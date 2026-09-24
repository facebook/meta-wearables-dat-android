/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.dat

import com.meta.pixelandtexel.birdspotter.domain.BirdLog
import com.meta.pixelandtexel.birdspotter.domain.GlassesVoiceEvent
import com.meta.pixelandtexel.birdspotter.domain.GlassesVoiceRepository
import com.meta.pixelandtexel.birdspotter.domain.LogCategory
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.types.RegistrationState
import com.meta.wearable.dat.core.voiceinvocations.startVoiceInvocationsStream
import com.meta.wearable.dat.core.voiceinvocations.types.actions.LaunchApp
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The DAT-backed [GlassesVoiceRepository] — where a "Hey Meta" launch is answered.
 *
 * **The answer is the contract.** Meta AI holds every invocation open until the app replies through
 * its response handle, and announces the app as not responding when no reply comes — opening on
 * screen is not the answer, the reply is. So each invocation is answered here, the moment it
 * arrives: success for a launch, before anything upstream reacts to it, and failure for a spoken
 * action, because this app defines none and silence would read as a hang. What the domain sees
 * afterwards is only the events it can act on.
 *
 * The channel rides its own device selection rather than a running session — a launch is what
 * *asks* for a session, so it cannot wait for one. Auto-selection is right here for the same reason
 * it is wrong at the session: the roster of paired devices is usually still empty when the channel
 * opens, and a selection that fills itself in as devices arrive is the only kind that can catch an
 * invocation spoken moments later. It is ranked, not left to the default: two pairs can be
 * connected at once, and the launch is spoken from the one on the wearer's face — so the channel
 * listens on the same pair the session runs on, by sharing the session's ranking (`deviceRanking`).
 *
 * **It waits for registration, then keeps the channel up.** Meta AI routes an invocation only to a
 * registered app and holds the launch open until the app answers, so the open is held back until
 * registration lands — opening ahead of it is what leaves the channel erroring with the launch
 * still in Meta AI's hand and nothing left to catch it. A channel that then drops is reopened
 * rather than logged and left down: the drop arrives on the error stream, not by ending the
 * invocations, so a first drop that went unhandled would be permanent and the held launch lost for
 * the life of the process.
 */
class DatGlassesVoiceRepository(
    /**
     * The object DAT boots in — held so this repository cannot be built before the SDK it opens a
     * channel on is up (see [DatGlassesSessionRepository]'s `init` for why the boot lives there),
     * and read for the one thing the channel and the session must agree on: the device ranking that
     * names which pair a launch is answered from.
     */
    private val link: DatGlassesSessionRepository,
) : GlassesVoiceRepository {

  /**
   * What [closeChannel] pulls: the same end a drop on the error stream brings the lease to, asked
   * for on purpose. Buffered by one and dropping the oldest, because a lease can only be closed
   * once and a second ask before it has gone adds nothing.
   */
  private val closeAsks = MutableSharedFlow<Unit>(
      extraBufferCapacity = 1,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )

  /** Whether a lease is open right now — what [closeChannel] waits on. */
  private val isLeaseOpen = AtomicBoolean(false)

  /**
   * Set by [withChannelClosed] so the reopen that follows comes at the floor delay rather than
   * wherever the backoff had climbed to: a lease closed on purpose is not a channel failing.
   */
  private val closedOnPurpose = AtomicBoolean(false)

  /**
   * What holds the reopen back while [withChannelClosed]'s body runs — the collector's loop waits
   * on it before opening a fresh lease, so the new channel is opened on whatever the body left
   * behind rather than on what it was about to take away.
   */
  @Volatile private var reopenHold: CompletableDeferred<Unit>? = null

  /**
   * Runs [body] with the channel closed: the lease in flight is ended and waited for, the body
   * runs, and only then does the collector's loop open a fresh lease.
   *
   * **For the mock kit's flip, and nothing else.** The channel is a lease on a device, and swapping
   * the SDK's providers underneath it leaves it listening on a pair that no longer exists — the
   * same reason the session repository ends its leases before the flip. The body is the swap
   * itself, held inside the closed window so the reopen cannot land on the providers about to be
   * taken away. With no channel open the body simply runs; and the close is bounded: a channel that
   * will not go inside a second is one the flip goes ahead without.
   */
  suspend fun withChannelClosed(body: suspend () -> Unit) {
    if (!isLeaseOpen.get()) {
      body()
      return
    }
    val hold = CompletableDeferred<Unit>()
    reopenHold = hold
    closedOnPurpose.set(true)
    closeAsks.tryEmit(Unit)
    try {
      repeat(CloseChannelPolls) {
        if (!isLeaseOpen.get()) return@repeat
        delay(CloseChannelPollMillis)
      }
      if (isLeaseOpen.get()) {
        BirdLog.error(LogCategory.GLASSES) {
          "voice channel — did not close in time for the mock kit's flip"
        }
      }
      body()
    } finally {
      hold.complete(Unit)
      reopenHold = null
    }
  }

  override fun voiceEventStream(): Flow<GlassesVoiceEvent> = channelFlow {
    var reopenDelayMillis = ReopenDelayFloorMillis
    while (isActive) {
      // Meta AI routes an invocation only to a registered app, and holds the launch
      // open until the app answers — so waiting for registration here costs nothing
      // and is exactly what a slower phone needs. Opening the channel ahead of
      // registration is what drops it, with the launch still in Meta AI's hand and the
      // channel gone before it could be caught. Re-asked on every reopen, so a
      // registration that drops and returns is waited out afresh.
      Wearables.registrationState.first { it == RegistrationState.REGISTERED }

      val stream =
          Wearables.startVoiceInvocationsStream(
              AutoDeviceSelector(deviceRanking = link.deviceRanking()),
          )
      BirdLog.info(LogCategory.GLASSES) { "voice channel — open, waiting on Meta AI" }
      isLeaseOpen.set(true)
      try {
        coroutineScope {
          launch {
            stream.state.collect { state ->
              BirdLog.debug(LogCategory.GLASSES) { "voice channel — $state" }
            }
          }
          // The deliberate end — see [closeChannel] — on the same footing as a drop.
          launch {
            closeAsks.first()
            throw VoiceChannelDropped
          }
          launch {
            stream.invocations.collect { invocation ->
              when (invocation) {
                is LaunchApp -> {
                  BirdLog.info(LogCategory.GLASSES) { "voice launch — answering Meta AI" }
                  // Answered before it is emitted: Meta AI is waiting on
                  // this, and what the app does with the launch is not Meta
                  // AI's wait to sit through.
                  if (!invocation.responseHandle.sendSuccess(null)) {
                    BirdLog.warning(LogCategory.GLASSES) {
                      "voice launch — the answer did not deliver"
                    }
                  }
                  send(GlassesVoiceEvent.LAUNCH)
                }
              }
            }
          }
          // A healthy channel suspends here for the life of the lease. A drop is
          // reported on the error stream, not by ending the invocations, so the
          // first error is what ends the attempt and reopens below — without this
          // the drop is permanent and the launch already held is lost for the life
          // of the process.
          stream.errors.collect { error ->
            // The enum constant, not just its description: CHANNEL_NOT_CONNECTED,
            // UNKNOWN_MESSAGE_TYPE and INVALID_ACTION_MESSAGE_PROTO each name a
            // distinct fault — a link that never came up, a message the glasses
            // sent that the SDK could not parse — where CHANNEL_ERROR is the
            // unspecified catch-all and the description alone cannot tell them apart.
            BirdLog.error(LogCategory.GLASSES) {
              "voice channel — ${error.name}: ${error.description}"
            }
            throw VoiceChannelDropped
          }
        }
      } catch (_: VoiceChannelDropped) {
        // Reopen below. A dropped channel does not resume; the next lease is new.
      } finally {
        // The lease ends, the channel goes down with it — the collector is what
        // holds it open, per the cold-stream contract on the interface.
        stream.close()
        isLeaseOpen.set(false)
      }

      // Back off between reopens so a channel that keeps dropping does not spin. The
      // floor is short because the wearer is waiting on the launch; the ceiling keeps
      // a channel that never recovers cheap.
      if (closedOnPurpose.getAndSet(false)) {
        reopenDelayMillis = ReopenDelayFloorMillis
        // Held until whatever asked for the close is done — see [withChannelClosed]. A
        // hold already released returns at once.
        reopenHold?.await()
      }
      delay(reopenDelayMillis)
      reopenDelayMillis = (reopenDelayMillis * 2).coerceAtMost(ReopenDelayCeilingMillis)
    }
  }
}

/** Thrown to end a channel lease from inside the collectors that watch it, so the loop reopens. */
private object VoiceChannelDropped : CancellationException("voice channel dropped")

/** The shortest wait before reopening a dropped channel — the wearer is waiting on the launch. */
private const val ReopenDelayFloorMillis = 500L

/** The longest that wait grows to, so a channel that never recovers stays cheap. */
private const val ReopenDelayCeilingMillis = 30_000L

/**
 * How long [DatGlassesVoiceRepository.withChannelClosed] waits for the lease to clear — twenty
 * polls of fifty milliseconds, the same second the session repository gives its own leases.
 */
private const val CloseChannelPolls = 20
private const val CloseChannelPollMillis = 50L
