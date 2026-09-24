/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

@file:Suppress("UseOfRunBlocking", "UseOfRunBlockingInTest")

package com.meta.pixelandtexel.birdspotter.data.location

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * One reading carried over two instruments, as the instrument underneath comes and goes.
 *
 * The scenarios are the three things a watcher can actually observe: the reading follows the
 * request, it comes back on its own when the preferred instrument has nothing to give, and it stops
 * only when there is genuinely nowhere left to read from.
 *
 * **Each instrument is opened fresh and answers with a different number**, which is what makes
 * these assertions about *which* one is being read rather than about a value passing through. A
 * provider is a factory here exactly as it is in the app — the failover calls it again every time
 * it picks an instrument up — so counting the calls is how a test can tell the second opening of
 * the phone from the first.
 */
class FailoverAimTest {

  @Test
  fun askingForThePreferred_readsFromIt() = failoverTest {
    val phone = Instrument(270.0, 271.0)
    val glasses = Instrument(90.0)
    val readings = FailoverReadings("bearing", { glasses.open() }, { phone.open() })
    val heard = readings.collectInto(this)

    settle()
    assertEquals(listOf(270.0), heard)

    readings.usePreferred(true)
    settle()

    assertEquals(listOf(270.0, 90.0), heard)
  }

  /**
   * The regression this suite exists for.
   *
   * A pair that reports motion but no magnetic field answers the compass by finishing at once,
   * which is the failover's entire signal to hand back. What the watcher must see after that is the
   * phone's bearing *moving again* — not the last reading from before the switch, held for the rest
   * of the session while the log says the handback succeeded.
   *
   * The last reading is the whole test: it can only arrive down a reopened phone stream that is
   * still being read, which is the thing a handback has to leave behind.
   */
  @Test
  fun aPreferredThatEndsAtOnce_handsBackToAFallbackThatKeepsReading() = failoverTest {
    val phone = Instrument(12.0, 34.0)
    val readings = FailoverReadings("bearing", { emptyFlow() }, { phone.open() })
    val heard = readings.collectInto(this)

    settle()
    assertEquals(listOf(12.0), heard)

    // The glasses are asked for, and have no compass to offer.
    readings.usePreferred(true)
    settle()

    // The phone is picked back up, which a second opening of it is the proof of.
    assertEquals(listOf(12.0, 34.0), heard)

    // And it is still being *read* — the reading that the failover ending would swallow, and
    // the one a watcher turning on the spot is waiting for.
    phone.report(56.0)
    settle()

    assertEquals(listOf(12.0, 34.0, 56.0), heard)
  }

  @Test
  fun bothInstrumentsSilent_endsTheReading() = failoverTest {
    val readings = FailoverReadings("bearing", { emptyFlow() }, { emptyFlow() })
    val heard = readings.collectInto(this)

    readings.usePreferred(true)
    settle()

    assertEquals(emptyList<Double>(), heard)
  }
}

/**
 * One sensor, opened as many times as the failover picks it up.
 *
 * **It stays open after its reading**, which is what a working sensor does and what lets a test
 * tell *the failover ended this stream* from *this stream ran out of numbers*. A channel is what
 * buys that: the scripted reading is waiting in it before anything collects, and [report] puts a
 * further one down whichever stream is open right now.
 */
private class Instrument(vararg readings: Double) {

  private val scripted = ArrayDeque(readings.toList())
  private var live: Channel<Double>? = null

  /**
   * The next reading this instrument has never yet given out, or an empty stream once it has given
   * out all of them.
   */
  fun open(): Flow<Double> {
    val reading = scripted.removeFirstOrNull() ?: return emptyFlow()
    val channel = Channel<Double>(Channel.UNLIMITED)
    channel.trySend(reading)
    live = channel
    return channel.receiveAsFlow()
  }

  /** A further reading, down whichever stream this instrument is open as right now. */
  fun report(reading: Double) {
    live?.trySend(reading)
  }
}

/** Everything this reading has said so far, as it says it. */
private fun FailoverReadings.collectInto(scope: CoroutineScope): List<Double> {
  val heard = mutableListOf<Double>()
  scope.launch { stream().collect { heard.add(it) } }
  return heard
}

/**
 * One scenario, with the collector it leaves running cleaned up after it.
 *
 * **A reading that is still being read is the *point* of most of these**, so the collector below is
 * a coroutine that deliberately never finishes — and a scope that waits for its children would
 * therefore never return. Cancelling them is what ends the scenario, exactly as a screen going away
 * ends the real one.
 */
private fun failoverTest(scenario: suspend CoroutineScope.() -> Unit) = runBlocking {
  scenario()
  coroutineContext.cancelChildren()
}

/**
 * Lets the failover's own coroutines run to a standstill.
 *
 * A switch of instruments is several hops — the request lands, the old collector unwinds, the new
 * one attaches and drains what is waiting — and none of them are worth a real delay.
 */
private suspend fun settle() {
  repeat(20) { yield() }
}
