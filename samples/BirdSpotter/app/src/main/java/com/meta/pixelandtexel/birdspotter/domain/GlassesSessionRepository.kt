/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.domain

import kotlinx.coroutines.flow.Flow

/**
 * Where the app stands with Meta AI — the one fact Settings reads before offering anything about
 * glasses.
 *
 * Registration is a one-time handshake the Meta AI app owns: the app asks, Meta AI takes the
 * screen, and the answer comes back through the `birdspotter://` callback. This enum is only *where
 * that stands*, mirrored off the SDK's own states so nothing above the data layer imports DAT to
 * ask.
 */
enum class GlassesRegistrationState {
  /**
   * Meta AI is reachable and the app is not yet linked — the invitation state, and the one the
   * Settings card is built for.
   */
  AVAILABLE,

  /** The handshake is in flight; Meta AI has the screen and will call back. */
  REGISTERING,

  /** Linked. Sessions may start, and Settings trades the card for the glasses row. */
  REGISTERED,

  /** Nothing to talk to — no Meta AI app on this phone, or the platform said no. */
  UNAVAILABLE,
}

/**
 * A device session's life, exactly as the device drives it.
 *
 * The device decides — a doff, a fold, another app, "Hey Meta" — and the app only ever observes.
 * There is no `IDLE`: a session that has not been asked to start is a session nobody is collecting,
 * so the stream simply has not begun.
 */
enum class GlassesSessionState {
  STARTING,
  STARTED,
  PAUSED,
  STOPPING,
  STOPPED,
}

/**
 * Whether this pair and this app can actually talk to each other.
 *
 * **A paired, powered, perfectly working pair of glasses can still be unreachable**, because DAT
 * pins versions on both ends — a minimum Meta AI app build and a minimum firmware. When that is
 * what is wrong, the link simply never comes up, and reporting it as "not in range" sends someone
 * hunting for a Bluetooth problem they do not have. The SDK is willing to say which end is behind,
 * so the app says it too.
 */
enum class GlassesCompatibility {
  /** Not answered yet — the reading arrives a beat after the pair is listed. */
  UNKNOWN,

  /** Both ends are current. The only case that permits a session. */
  COMPATIBLE,

  /** The glasses' firmware is behind; updating happens in the Meta AI app. */
  DEVICE_UPDATE_REQUIRED,

  /** This app's DAT SDK is behind the glasses — ours to fix, not the wearer's. */
  SDK_UPDATE_REQUIRED,
}

/**
 * How hot the glasses are running, collapsed to the three answers a wearer can act on.
 *
 * The SDK grades heat on a finer ladder; the collapse lives in the data layer, and its boundaries
 * are a judgement call worth re-tuning once real glasses have been run warm. [ELEVATED] means
 * expect throttling; [CRITICAL] means the session is about to lose — the zone the session's own
 * errors fire from.
 */
enum class GlassesThermalLevel {
  NOMINAL,
  ELEVATED,
  CRITICAL,
}

/**
 * The pair of glasses Meta AI knows about, as far as a session cares: what to call it, whether it
 * can be reached right now, whether the two ends are speaking the same version of DAT — and the
 * wearer-facing readings off the same snapshot: wear, battery, charging, heat.
 *
 * **The readings are nullable, and `null` means unknown — never a value.** They arrive over the
 * live link, so an unreachable pair reads unknown across the board, and battery in particular must
 * never render unknown as 0%. Hinge state is deliberately not here: folding the glasses drops the
 * link, so [isAvailable] already carries that fact. The thermal *reading* rides here; acting on
 * heat stays the session's job.
 */
data class GlassesDeviceInfo(
    /**
     * What the wearer calls them — the name shown on the glasses settings screen. Never blank: the
     * SDK leaves `name` empty until the two ends have actually linked, which for an unreachable
     * pair is forever, so the data layer substitutes a generic label.
     */
    val name: String,
    /**
     * Whether the link is up: paired glasses that are folded or out of range are known but not
     * available, and a session asked for now would not start.
     */
    val isAvailable: Boolean,
    /** Whether a session is possible at all — see [GlassesCompatibility]. */
    val compatibility: GlassesCompatibility = GlassesCompatibility.UNKNOWN,
    /** Whether they are on a face — `null` until the link has said either way. */
    val isWorn: Boolean? = null,
    /**
     * Charge, 0–100 — `null` while unknown. A number here is a real reading: the data layer maps
     * the SDK's unknown back to `null` rather than letting it wear a percent.
     */
    val batteryLevel: Int? = null,
    /** Whether they are charging — `null` until the link has said either way. */
    val isCharging: Boolean? = null,
    /** How hot they are running — `null` until the link has said. */
    val thermal: GlassesThermalLevel? = null,
    /**
     * Whether this pair has a panel to draw on.
     *
     * **Not nullable, and that is the difference from the readings above.** Those arrive over the
     * live link and are unknown until it says; this is a fact about the model in the box, answered
     * from the device's own description without asking the glasses anything. Most pairs have no
     * display, so `false` is the honest default rather than a stand-in for silence.
     *
     * It is here because a *send* needs no permission from the screen — a gallery with nowhere to
     * land lands nowhere, quietly — but an **offer** does: a control that says *show this on the
     * glasses* must not be drawn on a pair that has no glass to show it on.
     */
    val hasDisplay: Boolean = false,
)

/**
 * A grant Meta AI holds on the app's behalf, rather than one the OS holds.
 *
 * **This is the whole list DAT offers — two sensors on the glasses, not the phone.** The phone's
 * own camera and microphone are [PermissionsController]'s business and are asked for in the OS
 * dialogs; these two are asked for in the Meta AI app, are read off a live link, and can be
 * answered differently for the same phone from one minute to the next.
 */
enum class GlassesPermission {
  /** The glasses' camera — what a live session photographs through. */
  CAMERA,

  /**
   * The glasses' microphones — what a live session listens through, carried on the camera stream,
   * and what the recogniser hears. **Without it the session still runs:** the stream is opened
   * without audio and the listening stays on the phone. It is given in another app, on a screen the
   * wearer may not visit twice, so having it already in hand is the difference between a demo that
   * hears through the glasses and one that quietly does not.
   */
  MICROPHONE,
}

/**
 * Where a [GlassesPermission] stands — and, in the third case, whether it can be known at all.
 *
 * **Deliberately not a [PermissionStatus].** DAT's own status enum has exactly two cases, `Granted`
 * and `Denied`; there is no not-determined, so a grant nobody has ever been asked for reads as
 * [DENIED] and `Allow` is the right offer. What DAT adds instead is a third answer the OS never
 * gives: **the question is unanswerable without a connected pair.** `checkPermissionStatus` fails
 * with `NO_DEVICE_WITH_CONNECTION` when none is, because the grant lives on the glasses and Meta AI
 * has to reach them to read it.
 *
 * Folding that failure into "not asked" is what put `Allow` on a screen that cannot ask — and made
 * the grant appear to arrive only when the app was restarted with the glasses on. [UNKNOWN] is the
 * honest third case, and it is the whole reason this enum exists beside [PermissionStatus] rather
 * than reusing it.
 */
enum class GlassesAccess {
  /** No connected pair to read the grant from. Nothing to offer but a link. */
  UNKNOWN,

  /** Meta AI says yes, on some pair that answered. Sessions may use the sensor. */
  GRANTED,

  /** Meta AI says no — including the never-asked case, which DAT cannot tell apart. */
  DENIED,
}

/**
 * What went wrong with the glasses, in the cases the app can act on.
 *
 * Named cases rather than a message string — parity rule 9.
 */
sealed class GlassesError(message: String) : Exception(message) {

  /** The app is not registered with Meta AI; Settings owns the way back. */
  data object NotRegistered : GlassesError("The app is not registered with Meta AI")

  /** No glasses answered — none paired, none in range, or the session fell over. */
  data object NotConnected : GlassesError("No glasses answered")

  /**
   * The glasses answered, and cannot host a session until their own DAT software is brought up to
   * date — through the Meta AI app, like every other update to them.
   *
   * **Its own case because it is the failure that lies.** A pair in this state is on, paired and
   * reporting battery, wear and heat over the link the whole time: those ride a channel the phone
   * opens on connect, and want nothing from the glasses beyond being connected. A session wants the
   * software. Reported as [NotConnected], this sends somebody hunting a Bluetooth fault that does
   * not exist, past a settings screen cheerfully showing 82% and Worn.
   */
  data object GlassesUpdateRequired :
      GlassesError("The glasses need their DAT software updated before a session can run")

  /** The photograph never finished its Bluetooth crossing. */
  data object TransferFailed : GlassesError("The photo did not arrive from the glasses")
}

/**
 * The app's standing with the glasses: registration, the device that would answer, and the session
 * a live run holds open.
 *
 * **The session is a cold stream** — parity rule 4. Collecting [sessionStream] creates the DAT
 * device session and starts it; cancelling the collection stops it. There is no `start()`/`stop()`
 * pair to keep balanced across two platforms and no session object to leak: the collector is the
 * lease.
 *
 * Raising the registration and permission flows is deliberately not here — the same un-mirrorable
 * piece [PermissionsController] names: Android can only raise either from an Activity, so each
 * platform's Settings screens own their own raise, and both watch the outcome land in these
 * streams. See the Platform notes.
 */
interface GlassesSessionRepository {

  /** Cold stream of where registration stands, current state first. */
  fun registrationStateStream(): Flow<GlassesRegistrationState>

  /** Cold stream of the glasses Meta AI knows about — the first pair, or `null` when none. */
  fun deviceInfoStream(): Flow<GlassesDeviceInfo?>

  /**
   * A device session, as a cold stream of its states. Collecting starts one; cancelling stops it;
   * [GlassesSessionState.STOPPED] is terminal and ends the stream. Fails with a [GlassesError] when
   * a session cannot be had at all.
   */
  fun sessionStream(): Flow<GlassesSessionState>

  /**
   * A device session opened for the display alone — the same lease as [sessionStream], carrying one
   * capability instead of five.
   *
   * The full session is an instrument rack: the camera lit and streaming, the sensors and the
   * recogniser riding behind it. A screen that only wants to draw on the panel has no business
   * lighting any of that — frames nobody reads and a recogniser nobody hears are battery and
   * bandwidth spent on nothing — so this lease attaches the display and stops there.
   * [GlassesSessionState.STARTED] here means *the panel can be drawn on*, or that this pair has
   * none and never will.
   *
   * The default is the full session, which every capability rides anyway; an implementation with a
   * lighter path overrides it.
   */
  fun displaySessionStream(): Flow<GlassesSessionState> = sessionStream()

  /**
   * Where one of Meta AI's grants stands. Given through the Meta AI app rather than the OS — the
   * reason this is not a [PermissionsController] case — and readable only over a live link, which
   * is the reason it answers [GlassesAccess] rather than [PermissionStatus]. Ask again when the
   * link changes; there is no stream to follow.
   */
  suspend fun access(permission: GlassesPermission): GlassesAccess
}
