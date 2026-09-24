/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.diagnostics

import android.os.Handler
import android.os.Looper
import com.meta.pixelandtexel.birdspotter.domain.BirdLog
import com.meta.pixelandtexel.birdspotter.domain.LogCategory
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Says who is holding the main thread when the screen stops moving, and prints their stack.
 *
 * **A stall is easy to see and very hard to attribute.** A frozen screen looks identical whatever
 * caused it, and the usual way of finding out — read the code and reason about which call blocks —
 * is exactly the method that has already been wrong twice about this one. This asks the phone
 * instead: a heartbeat is posted to the main looper on a fixed beat, and when one comes back late
 * the main thread's own stack is written to the log. The top frames of that stack are the answer,
 * with no theory in between.
 *
 * Debug builds only, and started from [com.meta.pixelandtexel.birdspotter.AppContainer]. It costs
 * one sleeping thread and a message on the main queue every [BeatMillis], which is nothing next to
 * what it catches.
 */
object MainThreadWatchdog {

  private val running = AtomicBoolean(false)

  /**
   * Starts watching, once per process.
   *
   * [stallMillis] is what counts as a stall rather than a slow frame: three frames at 60 Hz is
   * about 50 ms and is ordinary, while a quarter of a second is long enough that a person sees the
   * screen stop. Reporting the ordinary case would bury the real one in noise.
   */
  fun install(stallMillis: Long = 250) {
    if (!running.compareAndSet(false, true)) return

    val main = Handler(Looper.getMainLooper())
    val mainThread = Looper.getMainLooper().thread

    thread(isDaemon = true, name = "birdspotter-watchdog") {
      while (true) {
        // Set before the beat is posted and cleared by the beat itself, so a beat that
        // never runs is exactly the case this reports.
        val answered = AtomicBoolean(false)
        main.post { answered.set(true) }

        val startedAt = System.nanoTime()
        Thread.sleep(stallMillis)

        if (!answered.get()) {
          // Sampled while the thread is still stuck, so the frames below are the ones
          // doing the blocking rather than whatever ran after it let go.
          val stack = mainThread.stackTrace
          val heldFor = (System.nanoTime() - startedAt) / 1_000_000
          BirdLog.warning(LogCategory.APP) {
            buildString {
              append("main thread blocked ${heldFor}ms and counting — top frames:")
              stack.take(FramesReported).forEach { frame -> append("\n    at $frame") }
            }
          }
          // Wait out the rest of the stall rather than reporting the same one every
          // beat: one stall should read as one line, not as a wall of them.
          while (!answered.get()) Thread.sleep(stallMillis)
        }

        Thread.sleep(BeatMillis)
      }
    }
  }

  /** How often the heartbeat goes out once the main thread is keeping up. */
  private const val BeatMillis = 200L

  /**
   * Deep enough to cross the framework frames a stall is usually buried under and reach the app's
   * own, and short of dumping a whole thread into a log line.
   */
  private const val FramesReported = 24
}
