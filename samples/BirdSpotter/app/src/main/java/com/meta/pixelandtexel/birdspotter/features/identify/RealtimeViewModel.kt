/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.identify

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meta.pixelandtexel.birdspotter.data.audio.FailoverAudioSource
import com.meta.pixelandtexel.birdspotter.data.audio.WavCodec
import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesWithMedia
import com.meta.pixelandtexel.birdspotter.data.journal.CaptureLocation
import com.meta.pixelandtexel.birdspotter.data.journal.CaptureSource
import com.meta.pixelandtexel.birdspotter.data.journal.GazeContext
import com.meta.pixelandtexel.birdspotter.data.journal.MomentContext
import com.meta.pixelandtexel.birdspotter.data.journal.OutingDraft
import com.meta.pixelandtexel.birdspotter.data.journal.OutingKind
import com.meta.pixelandtexel.birdspotter.data.journal.OutingMediaType
import com.meta.pixelandtexel.birdspotter.data.journal.PendingEvent
import com.meta.pixelandtexel.birdspotter.data.journal.PendingMedia
import com.meta.pixelandtexel.birdspotter.data.journal.PendingSighting
import com.meta.pixelandtexel.birdspotter.data.location.FailoverGazeProvider
import com.meta.pixelandtexel.birdspotter.data.location.FailoverHeadingProvider
import com.meta.pixelandtexel.birdspotter.data.session.MonotonicSessionClock
import com.meta.pixelandtexel.birdspotter.domain.AudioCaptureError
import com.meta.pixelandtexel.birdspotter.domain.AudioCaptureSource
import com.meta.pixelandtexel.birdspotter.domain.AudioChunk
import com.meta.pixelandtexel.birdspotter.domain.BirdCatalogRepository
import com.meta.pixelandtexel.birdspotter.domain.BirdLog
import com.meta.pixelandtexel.birdspotter.domain.CameraControl
import com.meta.pixelandtexel.birdspotter.domain.CameraPreviewSource
import com.meta.pixelandtexel.birdspotter.domain.CameraViewfinder
import com.meta.pixelandtexel.birdspotter.domain.CaptureSourceKind
import com.meta.pixelandtexel.birdspotter.domain.Coordinate
import com.meta.pixelandtexel.birdspotter.domain.DemoDirector
import com.meta.pixelandtexel.birdspotter.domain.DemoPhotoResponse
import com.meta.pixelandtexel.birdspotter.domain.DemoQuestion
import com.meta.pixelandtexel.birdspotter.domain.DemoResult
import com.meta.pixelandtexel.birdspotter.domain.GazeProvider
import com.meta.pixelandtexel.birdspotter.domain.GlassesAccess
import com.meta.pixelandtexel.birdspotter.domain.GlassesCameraRepository
import com.meta.pixelandtexel.birdspotter.domain.GlassesDisplayRepository
import com.meta.pixelandtexel.birdspotter.domain.GlassesError
import com.meta.pixelandtexel.birdspotter.domain.GlassesInputEvent
import com.meta.pixelandtexel.birdspotter.domain.GlassesInputRepository
import com.meta.pixelandtexel.birdspotter.domain.GlassesPermission
import com.meta.pixelandtexel.birdspotter.domain.GlassesRegistrationState
import com.meta.pixelandtexel.birdspotter.domain.GlassesSessionRepository
import com.meta.pixelandtexel.birdspotter.domain.GlassesSessionState
import com.meta.pixelandtexel.birdspotter.domain.GlassesSpeechRepository
import com.meta.pixelandtexel.birdspotter.domain.HeadingProvider
import com.meta.pixelandtexel.birdspotter.domain.JournalError
import com.meta.pixelandtexel.birdspotter.domain.JournalRepository
import com.meta.pixelandtexel.birdspotter.domain.LocationProvider
import com.meta.pixelandtexel.birdspotter.domain.LogCategory
import com.meta.pixelandtexel.birdspotter.domain.PhotoFormat
import com.meta.pixelandtexel.birdspotter.domain.PhotoIdentification
import com.meta.pixelandtexel.birdspotter.domain.PreviewFrame
import com.meta.pixelandtexel.birdspotter.domain.RealtimeSession
import com.meta.pixelandtexel.birdspotter.domain.SessionClock
import com.meta.pixelandtexel.birdspotter.domain.SessionDetector
import com.meta.pixelandtexel.birdspotter.domain.SessionEvent
import com.meta.pixelandtexel.birdspotter.domain.SessionFinding
import com.meta.pixelandtexel.birdspotter.domain.SessionRecorder
import com.meta.pixelandtexel.birdspotter.domain.SonogramAnalyzer
import com.meta.pixelandtexel.birdspotter.domain.SonogramBuffer
import com.meta.pixelandtexel.birdspotter.domain.SpokenOutput
import com.meta.pixelandtexel.birdspotter.domain.TimelineTickMillis
import com.meta.pixelandtexel.birdspotter.domain.compassPoint
import com.meta.pixelandtexel.birdspotter.domain.gazeBand
import com.meta.pixelandtexel.birdspotter.domain.heardAloud
import com.meta.pixelandtexel.birdspotter.domain.placement
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Where a session is: waiting for the microphone, listening, or stopped. */
enum class SessionStatus {
  OPENING,
  LISTENING,
  FAILED,
}

/**
 * One line the app said out loud, kept so it can be recognised coming back — see
 * [RealtimeViewModel.soundsLikeSomethingJustSaid].
 */
private data class SpokenLine(val words: List<String>, val at: Double)

/**
 * Which device the session is running on, as the source pill says it.
 *
 * **One enum rather than four booleans read at the pill.** The screen used to assemble the pill's
 * word out of `isSourceLinking` and `sourceKind` inline, which left the two genuinely different
 * kinds of *not the glasses yet* — reaching for a pair, and a pair that has gone quiet — spelled
 * identically as the phone. They are separate states here because they want separate drawings: one
 * is working, the other is waiting on the wearer.
 *
 * The honesty rule is unchanged and lives in [RealtimeUiState.sourceState]: [GLASSES] is claimed
 * only once audio has actually arrived from a pair, never on the promise of one.
 */
enum class SourceState {

  /** The phone's own microphone and camera — the session's ground state. */
  ON_DEVICE,

  /** A pair has been asked for and has not answered yet. The one state that works visibly. */
  LINKING,

  /** The glasses have the session: their audio is what the strip is being drawn from. */
  GLASSES,

  /**
   * A glasses run is still in flight, but the device is not giving us anything — a doff, a temple
   * tap, a hinge. Not a failure and not the phone: the run is there to be resumed by the wearer, or
   * hung up on with the pill.
   */
  GLASSES_PAUSED,

  /** A scripted feed, running on the device. */
  SIMULATED,
}

/**
 * How long into the session something landed, as a stopwatch reads: `0:14`, `1:07`.
 *
 * Minutes and seconds and nothing else. A session is minutes long by design — a demo that needs an
 * hours column has stopped being a demo — and a leading `00:` on every screenshot is noise.
 *
 * Named for what it labels, a stamp on a log row, rather than for the [SessionClock] it is read
 * off. There is no clock drawn on this screen; there are stamps against the things that happened.
 */
fun sessionStamp(seconds: Double): String {
  val whole = seconds.toInt().coerceAtLeast(0)
  return "${whole / 60}:${(whole % 60).toString().padStart(2, '0')}"
}

/**
 * How much of the session the strip shows at once. Eight seconds is about 500 columns — wide enough
 * that a whole phrase of song fits, narrow enough that a single call is more than a tick.
 */
const val TimelineWindowSeconds = 8.0

/**
 * How long the light is on before the frame is taken.
 *
 * Long enough for auto-exposure and auto-white-balance to answer the torch — under that, a lit
 * photo is the unlit one with a hotspot in it — and short enough that the shutter still feels like
 * a shutter. There is no capture API here to hand a flash mode to; this is what a flash *is* when
 * the photograph is a viewfinder frame.
 */
const val FlashSettleMillis = 320L

/** What kept a stopped session out of the journal. */
enum class SessionSaveError {
  NO_LOCATION_FIX,
  WRITE_FAILED,
}

/**
 * A stopped session, being decided about.
 *
 * The stop answers *is the microphone still open*; this answers *is this worth keeping*, and they
 * are two different questions asked a few seconds apart — see the confirmation contract. Nothing
 * here has touched the journal yet: the events are still the session's, the notes are still
 * unsaved, and Discard walks away from all of it.
 *
 * [droppedBirds] holds the stamps of detections the watcher has dropped — a saved outing's
 * sightings are the birds a watcher *confirmed*, not the ones a detector offered, and dropping a
 * bad one here is the whole reason a confirmation exists rather than an autosave.
 */
data class SessionReview(
    /** The stop-clock reading, which becomes the outing's `durationMs` at save. */
    val durationSeconds: Double,
    val notes: String = "",
    val droppedBirds: Set<Double> = emptySet(),
    val isSaving: Boolean = false,
    val saveError: SessionSaveError? = null,
    /** Set once the journal has the outing — what tells the screen the cover can fall. */
    val savedOutingId: String? = null,
) {

  /** The line under the save controls, or `null` while there is nothing wrong. */
  val saveErrorMessage: String?
    get() =
        when (saveError) {
          null -> null
          SessionSaveError.NO_LOCATION_FIX ->
              "Couldn't get your location, and an outing needs one. Try again in a moment."
          SessionSaveError.WRITE_FAILED ->
              "Couldn't save this outing. Nothing was written — try again."
        }
}

/**
 * What the real-time screen renders around the strip.
 *
 * The strip's own data is **not** here: columns arrive 62 times a second and this changes perhaps
 * twice a run, so folding them together would rebuild the header, the chips and the controls on
 * every column. The buffer lives on the view model and the strip reads it directly, told that the
 * window has moved by a plain elapsed reading.
 */
data class RealtimeUiState(
    val sourceKind: CaptureSourceKind = CaptureSourceKind.PHONE,
    val status: SessionStatus = SessionStatus.OPENING,
    val failure: AudioCaptureError? = null,
    /**
     * Whether a run is in flight — from the moment [RealtimeViewModel.start] opens one until the
     * microphone stream ends, by a stop, a failure or a cancel. What anything that has to hold the
     * process open for the session reads.
     */
    val isRunning: Boolean = false,
    val session: RealtimeSession = RealtimeSession(startedAt = 0L),
    val isCameraOpen: Boolean = false,
    /**
     * What the camera behind this session can be asked to do — see [CameraControl]. Read off the
     * source once, because it describes hardware rather than anything that changes mid-run, and
     * carried here so the panel builds its controls from a capability instead of from a `kind`.
     */
    val cameraControls: Set<CameraControl> = emptySet(),
    /**
     * Whether the next photo will be taken with the light on.
     *
     * **Armed, not lit.** This is a flash the way a camera means one — a setting that decides what
     * happens when the shutter fires — rather than a torch the watcher has to remember to put out.
     * It survives the panel closing, because arming a flash costs nothing while the camera is shut
     * and a photographer who set it once should not have to set it again.
     */
    val isFlashOn: Boolean = false,
    /**
     * Whether the pill is also a switch: the app is registered with Meta AI and a pair is
     * reachable. The session may still fail to start — this is the invitation, not the promise.
     */
    val isGlassesAvailable: Boolean = false,
    /**
     * The crossing: from asking for the glasses until their audio actually arrives.
     *
     * **It ends at the ears, not at the session.** It used to be cleared when the DAT session
     * reported `STARTED`, which is one beat too early — the glasses' audio still has to come up
     * after that, and the pill only flips on a chunk. In the gap the state was *not linking, not
     * glasses*, so the pill fell back to `On device` for a fraction of a second between `Linking`
     * and `Glasses`: phone, glasses, phone, glasses. Ending it on arrival closes the gap by
     * construction rather than by papering over it in [sourceState].
     *
     * Arrival semantics either way, which is the rule the audio failover contract sets: never claim
     * a source on the promise of one. Saying *still getting there* for the whole crossing is not a
     * claim.
     */
    val isSourceLinking: Boolean = false,
    /**
     * A capture is in flight. The shutter holds still rather than firing a second one into it.
     *
     * **One flag for both devices, because it guards one mistake.** A photograph mid-crossing from
     * the glasses must not queue a second ask onto a Bluetooth link already carrying one; a
     * photograph mid-[FlashSettleMillis] on the phone must not start a second settle that fights
     * the first for the torch. Two names for *the shutter is busy* would be two chances to check
     * the wrong one.
     *
     * **The guard is the whole capture rather than a fixed window.** The client spec asks for a 500
     * ms debounce; holding until the capture actually finishes is stricter than that and needs no
     * number — a BTC crossing can take well over half a second, and a timer that expired mid-flight
     * would re-arm the shutter exactly when pressing it is worst.
     *
     * It says nothing about *where the picture is* — that is the timeline's job now, on the photo's
     * own row. See [RealtimeViewModel.captureThroughGlasses].
     */
    val isCapturing: Boolean = false,
    /**
     * Whether the glasses can actually be photographed through right now: a DAT session whose
     * camera stream has reached `STREAMING`.
     *
     * Kept apart from [sourceKind] because the two answer different questions and can honestly
     * disagree. The pill says whose *ears* these are, which follows the audio that arrived; this
     * says whose *eyes* the shutter will use. During the beat after a pause they differ, and the
     * screen is right both times.
     */
    val isGlassesSessionLive: Boolean = false,
    /**
     * Whether a glasses run is in flight at all — from the tap that asked for one until it ends,
     * across every pause and resume in between.
     *
     * What makes the pill tappable while the session is *paused*: the run is still there to hang up
     * on, even though nothing is live and the glasses may be out of reach.
     */
    val isGlassesRequested: Boolean = false,
    /**
     * What the reachable pair has left, 0–100 — or `null` for every way of not knowing: no pair in
     * range, or a link that has not said yet.
     *
     * **A glance, not the settings screen's row.** That row is a status panel a watcher goes
     * looking for; this is the one number that changes what they do next, on the screen they are
     * already on. A session runs the glasses' camera, microphone and sensors at once, so *how much
     * longer can I do this* is a question the session itself should answer.
     *
     * Tri-state to `null` the whole way down, and the data layer has already mapped the link's `0`
     * — its unknown-or-empty — to nothing, so a number that reaches here is a real reading. See
     * [GlassesDeviceInfo.batteryLevel].
     */
    val glassesBattery: Int? = null,

    /**
     * Whether the pair is on a face, when the pair says. **A connected pair and a *worn* pair are
     * different facts about the session**, and only the second one means the camera is pointing
     * where the wearer is looking — so the switch draws them differently. `null` while nothing is
     * reachable, and while a reachable pair has not reported yet.
     */
    val isGlassesWorn: Boolean? = null,
    /**
     * Whether a reachable pair has a panel to draw on — what makes an identified bird on the
     * timeline a **thing to press**.
     *
     * **The automatic send is deliberately not gated on this, and this is deliberately not gated on
     * a running session.** An identification pushes itself up the moment it lands and asks nobody
     * first, because a send with nowhere to land lands nowhere and waiting on an answer would make
     * every identification pause for a question whose answer changes nothing (see
     * [GlassesDisplayRepository]). An *offer* is the other way round: a row that invites a tap it
     * cannot honour is the screen inventing a control, which is exactly what [canToggleSource]
     * refuses to do for the source switch.
     *
     * Gated on the pair being reachable, like the charge and the wear beside it — a display on a
     * pair that has left the room is a panel nothing can reach.
     */
    val isDisplayAvailable: Boolean = false,
    /**
     * The bird whose card is open on the phone, or `null` — the usual state.
     *
     * **The fallback half of the log's tap, and only the fallback.** A pair with glass gets the
     * card where the wearer is already looking and this stays null; everything else gets it here,
     * laid out the way the glasses would have laid it out. It is deliberately *not* set when the
     * card went up on the display: two copies of one card, one of them on a phone the watcher put
     * in their pocket, is the app answering a question twice.
     *
     * Session state rather than screen state because the row that opened it is session state: the
     * same stop that ends the run is what should take the card down with it.
     */
    val cardOnPhone: SpeciesWithMedia? = null,
    /**
     * One line about the source, when something needs saying — a missing camera grant, a crossing
     * that failed. Cleared on the next toggle; `null` is the usual, quiet state.
     */
    val sourceNotice: String? = null,
    /**
     * Where the session is happening: the one fix taken as it opened, or `null` while that is still
     * being asked for — or for good, if it never came.
     *
     * Asked for the instant the session starts rather than when a sighting is finally logged,
     * because *where* is a fact about the moment the watcher started watching, and by the time they
     * have a name for the bird they may be a field away. [LocationProvider] answers `null` for
     * every way of not knowing, so there is no error to carry beside this.
     */
    val coordinate: Coordinate? = null,
    /**
     * Which way the watcher is facing, in degrees clockwise from north — or `null` before the
     * compass has said, or on a phone that has none.
     */
    val heading: Double? = null,
    /**
     * How high the watcher is aiming, in degrees above the horizon — or `null` before the phone has
     * said, or on one that cannot.
     *
     * Kept beside [heading] rather than folded into it because the two arrive from different
     * instruments at different moments — see [GazeProvider]. Together they are a full aim: which
     * way on the ground, and how far up from it.
     */
    val elevation: Double? = null,
    /** The stopped session being decided about, or `null` while one is still running. */
    val review: SessionReview? = null,
) {

  /** Whether the session is over and the confirmation is what the cover shows. */
  val isReviewing: Boolean
    get() = review != null

  /**
   * Which device the pill is answering for — see [SourceState].
   *
   * **The order of the branches is the honesty contract.** [SourceState.GLASSES] is reached only
   * through [sourceKind], which follows the chunks that actually arrived, so a run that has been
   * asked for and has not delivered audio can never draw the pill as the glasses'. A run in flight
   * with nothing live is the pause; everything else falls back to the device in the hand.
   */
  val sourceState: SourceState
    get() =
        when {
          isSourceLinking -> SourceState.LINKING
          sourceKind == CaptureSourceKind.GLASSES -> SourceState.GLASSES
          isGlassesRequested && !isGlassesSessionLive -> SourceState.GLASSES_PAUSED
          sourceKind == CaptureSourceKind.SIMULATED -> SourceState.SIMULATED
          else -> SourceState.ON_DEVICE
        }

  /**
   * What the source pill reads.
   *
   * **`On device` rather than `Phone`.** The pill's question is *whose ears and eyes are these*,
   * and the useful half of the answer is that they are not the glasses' — said of the thing the
   * watcher is holding, which is also what the phone glyph beside it draws. `Phone` was a hardware
   * name where the sentence wanted a place.
   *
   * The ears stay the phone's either way — the SDK ships no audio stream of its own; what the
   * toggle moves today is the camera the shutter fires, so the pill answers for the session's
   * device.
   */
  val sourceLabel: String
    get() =
        when (sourceState) {
          SourceState.ON_DEVICE -> "On device"
          SourceState.LINKING -> "Linking"
          SourceState.GLASSES -> "Glasses"
          SourceState.GLASSES_PAUSED -> "Waiting"
          SourceState.SIMULATED -> "Simulated"
        }

  /**
   * Whether the source control is a **switch** rather than a label: a registered pair in reach, or
   * a run to hang up on.
   *
   * This is also what decides which of the two drawings the header carries. With nothing to switch
   * to there is one device in the story, and a two-sided control offering a side that does not
   * exist would be the screen inventing an option — so it stays the single pill it always was. The
   * moment a pair is reachable the control becomes a segmented chip with both sides on it, because
   * *now* there is a choice, and a choice should look like one before it is made.
   */
  val canToggleSource: Boolean
    get() = isGlassesAvailable || isGlassesRequested

  /**
   * Whether the switch's glasses side is the chosen one.
   *
   * True through the whole crossing, not just once it lands: a tap moves the selection at once and
   * the *ink* is what stays honest about how far along it is — see [glassesSourceLabel]. A switch
   * whose selection waited for the ears would sit under the watcher's thumb doing nothing visible
   * for as long as the glasses' audio takes, which is exactly how a control gets pressed twice.
   *
   * **And it stays true through a pause, which is the case worth arguing.** When the glasses drop
   * out the phone picks the recording back up, so it is tempting to throw the switch back to the
   * left — and that would be wrong twice over. The run is still in flight and still assigned to the
   * glasses, waiting on the wearer; and because only the *unlit* side takes a tap, a selection on
   * the left would make the glasses side the live one — where a tap calls [toggleSource], which
   * while paused **hangs the run up**. A control labelled `Glasses` that ends the glasses session.
   * The selection says where the session is assigned; [isDeviceCarrying] says who is doing the work
   * meanwhile.
   */
  /**
   * Whether a card the watcher asked for should go up on the glasses rather than open here.
   *
   * **Routed on the session, not on the pill and not on the pairing** — the same rule the shutter
   * follows (see [capturePhoto]). A pair can be reachable, and even have glass in it, while the run
   * is deliberately on the phone: that is what the switch's *On device* side means, and a card that
   * flew to a pair sitting on the table would be the app answering somewhere nobody is looking. So
   * a run on the phone gets the phone's card even with a Display pair in the room, and only a live
   * glasses session sends it up.
   */
  val cardGoesToGlasses: Boolean
    get() = isDisplayAvailable && isGlassesSessionLive

  val isGlassesSelected: Boolean
    get() =
        when (sourceState) {
          SourceState.LINKING,
          SourceState.GLASSES,
          SourceState.GLASSES_PAUSED -> true
          // **A live run keeps the selection even when the phone has the ears.** The pill reads
          // the microphone, and the microphone is not the whole session: with a run in flight
          // the shutter still fires through the glasses and the button on the temple still
          // works, so a switch thrown back to the phone would say the session had moved when
          // only its ears had. See [withoutGlassesEars].
          SourceState.ON_DEVICE,
          SourceState.SIMULATED -> isGlassesRequested && isGlassesSessionLive
        }

  /**
   * Whether the phone is doing the recording while the switch is thrown somewhere else — the one
   * state where the selected side and the working side are different devices.
   *
   * It is what stops a paused run reading as a stopped session. The glasses side says the session
   * is waiting on them; this lifts the phone side out of the idle ink to say *and this is carrying
   * it in the meantime*, which is the sentence the notice underneath spells out in words. Without
   * it the switch shows one lit side that is not listening and one dim side that is, next to a
   * sonogram visibly still scrolling.
   */
  val isDeviceCarrying: Boolean
    get() =
        when (sourceState) {
          SourceState.GLASSES_PAUSED -> true
          // A run whose ears went back to the phone, which is the same sentence as the pause —
          // the switch is on the glasses, and this is what is doing the listening meanwhile.
          // Not `LINKING`, where the handover has closed one microphone and not yet opened the
          // other and nothing is carrying anything.
          SourceState.ON_DEVICE -> isGlassesSelected
          SourceState.LINKING,
          SourceState.GLASSES,
          SourceState.SIMULATED -> false
        }

  /**
   * The switch's left side: the device in the hand, named for what it actually is. A simulated feed
   * is still *on the device*, and saying so is worth more than the symmetry.
   */
  val deviceSourceLabel: String
    get() = if (sourceKind == CaptureSourceKind.SIMULATED) "Simulated" else "On device"

  /**
   * The switch's right side. **It carries the live state, and the left side never does** — every
   * word this control has to say beyond the two device names is about the glasses: reaching for
   * them, or having lost them. The phone is only ever the phone.
   *
   * **`Waiting`, not `Paused`.** The SDK's state is `PAUSED` and the notice underneath still says
   * the glasses paused the session, because that is what happened to *them*. On a chip with no
   * subject the word attaches to the nearest noun the watcher has in mind, which is the session —
   * and the session did not pause: the strip is still scrolling and the phone is still recording.
   * `Waiting` can only be read about the thing it is printed on, and it is the truth: the switch is
   * thrown to the glasses and waiting for the wearer to pick them back up.
   *
   * **The charge rides here, on the word it is about.** It used to hang off the trailing edge of
   * the bar as a plate of its own, which put a number about the glasses at the far side of the
   * header from the control naming them — two readings about one device, in two places, and the
   * bar's only other occupant. On the side that already says *Glasses*, under the glyph that
   * already means them, it cannot be read as the phone's.
   *
   * **Only on the settled word.** `Linking` and `Waiting` are about reaching for a pair, and a
   * charge printed beside either is a fact about a device the chip is in the middle of saying it
   * does not have yet. It appears when the state does, and the number's arrival is not something
   * the watcher has to be told about twice.
   */
  val glassesSourceLabel: String
    get() =
        when (sourceState) {
          SourceState.LINKING -> "Linking"
          SourceState.GLASSES_PAUSED -> "Waiting"
          else -> glassesBattery?.let { "Glasses $it%" } ?: "Glasses"
        }

  /** True once the fix has landed — what the location dot is lit by. */
  val isLocated: Boolean
    get() = coordinate != null

  /** The bearing as one of eight compass points, or `null` while there is nothing to read. */
  val headingPoint: String?
    get() = heading?.let(::compassPoint)

  /**
   * The elevation as one of five strata, or `null` while there is nothing to read. The same type
   * the journal stores against a moment, so the chip and the entry agree by construction.
   */
  val elevationBand: GazeContext?
    get() = elevation?.let(::gazeBand)

  /**
   * What the log says while there is nothing on it yet.
   *
   * **The half-second before the first sample is real, and it is not listening.** `AudioRecord`
   * warms up the way any capture path does, and it is not something an app can hurry. It happens
   * off the main thread so nothing is frozen, but a screen that said `LISTENING` over a microphone
   * that had not opened yet would be the one dishonest line on it. So it says what it is doing
   * instead, and the word changes the moment the first sample lands.
   */
  val emptyLogLabel: String
    get() = if (status == SessionStatus.LISTENING) "Listening" else "Opening the microphone"

  /** The line shown in place of the strip, or `null` while there is nothing wrong. */
  val failureMessage: String?
    get() =
        when (failure) {
          null -> null
          AudioCaptureError.AccessDenied ->
              "Microphone access is off. Turn it back on in Settings to start a session."
          AudioCaptureError.Unavailable -> "There's no microphone to listen with."
          AudioCaptureError.Interrupted ->
              "Another app took the microphone. Close the session and start it again."
        }

  /** The first samples have landed and the session is under way. */
  fun listening(): RealtimeUiState = copy(status = SessionStatus.LISTENING, failure = null)

  /**
   * A chunk of audio has actually arrived, from [source] — **the only place the pill's claim
   * becomes true.**
   *
   * A tap on the pill is a request; the DAT session reporting `STARTED` is that request being
   * accepted; this is the ears arriving, which is the one thing the pill is allowed to be read off.
   * It is also what closes [isSourceLinking]: the crossing is over when there is audio to show for
   * it, and not before — see that field for the flicker this shape exists to prevent.
   *
   * The phone's own chunks close nothing, deliberately. While a glasses run is in flight the phone
   * is still recording underneath — the failover never closes a working microphone on the promise
   * of a better one — so a phone chunk mid-crossing means *the crossing has not happened yet*,
   * which is the state we are already in.
   */
  fun hearing(source: CaptureSourceKind): RealtimeUiState {
    var next = if (sourceKind == source) this else copy(sourceKind = source)
    if (source == CaptureSourceKind.GLASSES && next.isSourceLinking) {
      next = next.copy(isSourceLinking = false)
    }
    return if (next.status != SessionStatus.LISTENING) next.listening() else next
  }

  /**
   * The glasses' ears were asked for and could not be had — the phone keeps the microphone, and the
   * run goes on without it.
   *
   * **`Linking` is a promise, and this is the only thing that can withdraw it.** The word is closed
   * by glasses audio arriving ([hearing]) and by the session ending ([applying], [ending]) — and a
   * microphone that never opens is neither of those: the session is up, the shutter works, the
   * temple button works, and the one thing that was asked for is not coming. Without this the pill
   * sits on `Linking` for the whole run, reaching for a pair it has already stopped reaching for.
   *
   * **The run is not ended over it.** A glasses session with the phone's ears is most of what the
   * glasses were for — the photograph, the button, the wearer's own aim — so this changes what the
   * screen *says* and nothing about what the session *is*. The notice carries the half that
   * survived, because a watcher told only that the microphone failed will reasonably conclude the
   * glasses are done.
   *
   * A no-op when no run is in flight: the signal outlives the request by a beat, and a sentence
   * about glasses on a screen that has gone back to the phone is worse than no sentence.
   */
  fun withoutGlassesEars(): RealtimeUiState =
      if (!isGlassesRequested) {
        this
      } else {
        copy(
            isSourceLinking = false,
            sourceNotice =
                "The glasses' microphone didn't answer — the phone is listening. " +
                    "Photos and the button still come from the glasses.",
        )
      }

  /**
   * The spine gave up, which ends the session — a screen that promises it is listening and is not
   * would be the one dishonest thing here. Anything that is not one of ours is reported as
   * [AudioCaptureError.Unavailable], for the same reason the camera does it.
   */
  fun failed(error: Throwable): RealtimeUiState = copy(
      status = SessionStatus.FAILED,
      failure = error as? AudioCaptureError ?: AudioCaptureError.Unavailable,
  )

  /**
   * What the glasses session's own state does to the screen.
   *
   * **This decides what the glasses are *asked* to do; it does not move the pill.** The pill
   * follows [AudioChunk.source] — audio that actually arrived — so that a session which starts but
   * whose microphone never opens cannot leave the screen claiming the glasses. What this does own
   * is [isGlassesSessionLive], which is what the shutter routes on: `STARTED` here means the camera
   * stream reached `STREAMING`, so a photograph can really be taken.
   *
   * The pause is narrated rather than diagnosed: a transition arrives carrying no reason, so the
   * notice says what happened and not why. A later `STARTED` clears it — the device resumed, and
   * there is nothing left to explain.
   */
  fun applying(sessionState: GlassesSessionState): RealtimeUiState =
      when (sessionState) {
        // **`STARTED` opens the crossing rather than closing it.** The session being up is when
        // the glasses' ears are *asked* for — see the `usePreferred` call beside this — and the
        // first buffer of audio takes a beat to arrive. Holding the linking state across that beat
        // is what
        // stops the pill dropping back to the phone between `Linking` and `Glasses`. A resume
        // after a pause goes through here too, and wants exactly the same beat; a session that
        // is already being heard through does not, hence the guard.
        GlassesSessionState.STARTED ->
            copy(
                isGlassesSessionLive = true,
                isSourceLinking = sourceKind != CaptureSourceKind.GLASSES,
                sourceNotice = null,
            )
        GlassesSessionState.PAUSED ->
            copy(
                isGlassesSessionLive = false,
                isSourceLinking = false,
                sourceNotice =
                    "The glasses paused the session — the phone has it until they " +
                        "resume. Tap the side of the glasses to pick it back up.",
            )
        GlassesSessionState.STOPPING,
        GlassesSessionState.STOPPED ->
            copy(
                isGlassesSessionLive = false,
                isSourceLinking = false,
            )
        // Already the linking beat the toggle set up.
        GlassesSessionState.STARTING -> this
      }

  /**
   * What the screen is left with when a glasses run ends, however it ended: the switch back on the
   * phone, and at most one line about why.
   *
   * **One writer for that line, because there used to be two.** A photograph still crossing when
   * the run ended failed on its own and wrote *the photo didn't make it from the glasses*, while
   * the run's own teardown wrote its parting line over the top — and whichever won, the row the
   * picture would have landed on had already left the timeline. The teardown now waits for the
   * crossing to settle and composes the sentence here, so exactly one lands.
   *
   * **The crossing's line wins, because it is the one with a consequence on screen.** A watcher who
   * just saw a row disappear is owed that sentence more than they are owed *the glasses didn't
   * answer* — which the switch, already back on the phone, has said in its own way.
   *
   * It says *the session ended* rather than naming who ended it: a long press on the temple and a
   * tap on the switch arrive here identically, and what is worth saying is why the photograph is
   * not there.
   */
  fun ending(crossingLost: Boolean, parting: String?): RealtimeUiState = copy(
      isGlassesRequested = false,
      isGlassesSessionLive = false,
      isSourceLinking = false,
      isCapturing = false,
      sourceNotice =
          if (crossingLost) {
            "The session ended before the photo arrived."
          } else {
            parting
          },
  )
}

/**
 * Drives the real-time session: holds the microphone open for as long as the screen is up, turns
 * what it hears into sonogram columns, and collects everything that lands on the timeline.
 *
 * **The session's clock is its own thing** — see [SessionClock]. Elapsed seconds are a monotonic
 * count from the moment the session opened, and the sonogram is aligned to it rather than being it,
 * so a microphone that stops does not stop time. Every event is stamped here, at the moment it
 * arrives, rather than by whatever produced it: a detector's wall clock starts when the detector
 * does, and the session's starts when the session does.
 *
 * It also asks, once, *where*. The fix runs beside the microphone rather than in front of it — see
 * [stampLocation].
 *
 * [observeSession] and [observeCamera] are suspend functions the screen calls, not something the
 * constructor starts. The session's life is the screen's life and the camera's life is the camera
 * mode's — two cold streams, two cancellations, no `stop()` to forget on either platform.
 */
class RealtimeViewModel(
    private val audioSource: AudioCaptureSource,
    private val previewSource: CameraPreviewSource,
    private val detector: SessionDetector,
    /**
     * The Director's cue-answering half. Kept beside [detector] rather than folded into it — the
     * detector seam is the one a real classifier implements without the session changing, and the
     * cues (a photo's scripted response, a tapped question) are the demo's own surface. In the app
     * the two are one object; in a test they need not be.
     */
    private val director: DemoDirector,
    /**
     * For the one thing a cue-landed answer needs that the preset deliberately does not carry: the
     * bird's name. The catalog is the one source of bird names, and a row whose id it cannot
     * resolve lands nothing — the same silence the Director keeps.
     */
    private val birdCatalog: BirdCatalogRepository,
    /** Where Save writes the outing. Nothing here touches it until the watcher says so. */
    private val journal: JournalRepository,
    private val glassesSession: GlassesSessionRepository,
    private val glassesCamera: GlassesCameraRepository,
    private val glassesInput: GlassesInputRepository,
    /**
     * What the watcher says out loud, for as long as this run has the glasses to hear it with.
     *
     * **The one sense with no failover**, and the one feature a phone-only run simply does not
     * have: recognition happens on the glasses, and there is no second instrument to fall back on
     * (see [GlassesSpeechRepository]). A run on the phone collects this and hears nothing, which is
     * the honest shape of the bargain rather than an error to report.
     */
    private val glassesSpeech: GlassesSpeechRepository,
    /**
     * Where an identification goes when the wearer has somewhere to see it — the bird's
     * photographs, paged on the glasses themselves. Fire-and-forget from here: a run with no
     * display simply shows nothing, and the timeline never learns either way.
     */
    private val glassesDisplay: GlassesDisplayRepository,
    /**
     * Where an identification goes when the wearer has somewhere to *hear* it — the row's own
     * authored line, said into the ear the session is already talking to. Fire-and-forget on the
     * same terms as [glassesDisplay], and quiet on the same terms too: a run with nothing to speak
     * into says nothing, and the timeline never learns either way. See [SpokenOutput].
     */
    private val spokenOutput: SpokenOutput,
    private val locationProvider: LocationProvider,
    private val headingProvider: HeadingProvider,
    private val gazeProvider: GazeProvider,
    private val clock: SessionClock = MonotonicSessionClock(),
) : ViewModel() {

  /**
   * The same object as [audioSource] when the app is wired for real, kept separately so the session
   * can ask for the glasses' ears without the rest of the code learning that failover exists. Null
   * in previews and tests, where the microphone never changes.
   */
  private val audioFailover = audioSource as? FailoverAudioSource

  /**
   * The aim readings' failovers, on the same terms as [audioFailover] and for the same reason: the
   * session asks for the wearer's own head without anything above learning that there are two
   * instruments behind each of these. Null wherever the providers do not change.
   */
  private val headingFailover = headingProvider as? FailoverHeadingProvider
  private val gazeFailover = gazeProvider as? FailoverGazeProvider

  /**
   * The session second past which a transcript can be trusted to be the wearer rather than the
   * app's own voice coming back — see [stopListening]. Zero until the app first speaks, which is
   * right: nothing has been said yet, so nothing can be echoing.
   */
  private var deafUntil = 0.0

  /**
   * What the app has said lately, in the words it said them, newest last — see
   * [soundsLikeSomethingJustSaid]. Bounded because only the last few lines can still be in the air,
   * and stamped because a phrase stops being suspicious once enough time has passed for the wearer
   * to have chosen it themselves.
   */
  private val recentlySaid = ArrayDeque<SpokenLine>()

  private val analyzer = SonogramAnalyzer()

  /**
   * What the session heard, kept for Save — the strip's twin with a longer memory: same chunks,
   * same clock, same gap rule, but segments for the journal instead of pixels for the screen. See
   * [SessionRecorder].
   */
  private val recorder = SessionRecorder()

  /** The session's strip. A stable reference the timeline reads directly. */
  val sonogram = SonogramBuffer()

  private val _uiState = MutableStateFlow(
      RealtimeUiState(
          sourceKind = audioSource.kind,
          cameraControls = previewSource.controls,
      ),
  )
  val uiState: StateFlow<RealtimeUiState> = _uiState.asStateFlow()

  /**
   * How far into the session the strip is drawing — the window's right edge, and the one thing here
   * that changes at frame rate. Kept apart from [uiState] so that it redraws the strip and nothing
   * else.
   *
   * Published from the clock on a tick rather than from arriving audio. During a gap there is no
   * audio to publish from, and a strip that stopped scrolling because the microphone dropped is
   * precisely the bug this replaced.
   */
  private val _elapsed = MutableStateFlow(0.0)
  val elapsed: StateFlow<Double> = _elapsed.asStateFlow()

  private val _frame = MutableStateFlow<PreviewFrame?>(null)
  val frame: StateFlow<PreviewFrame?> = _frame.asStateFlow()

  /**
   * The newest frame, held for the shutter rather than shown.
   *
   * A plain field and not state, deliberately: while the surface is drawing the picture, frames
   * arrive only so a press of the shutter has something to take, and publishing each one into
   * [frame] recomposed the whole panel thirty times a second to draw nothing. So [frame] carries
   * exactly three things — the first frame (which enables the shutter and covers the moment before
   * the surface arrives), every frame when there is no surface (previews, simulated feeds), and the
   * last frame at close (the still the panel dismisses over). This carries all the rest.
   */
  private var latestFrame: PreviewFrame? = null

  /**
   * The hardware-path picture, when the source has one — see [CameraViewfinder]. The panel draws
   * this when it is here and falls back to [frame] when it is not, which is what keeps Compose
   * previews and simulated feeds drawing without a camera.
   */
  private val _viewfinder = MutableStateFlow<CameraViewfinder?>(null)
  val viewfinder: StateFlow<CameraViewfinder?> = _viewfinder.asStateFlow()

  /**
   * How far the viewfinder is magnified. Apart from [uiState] for the same reason [elapsed] is: a
   * pinch moves it sixty times a second, and folding that into the state would rebuild the header,
   * the bearing row and every row of the log along with it.
   */
  private val _zoom = MutableStateFlow(1f)
  val zoom: StateFlow<Float> = _zoom.asStateFlow()

  /**
   * Whether the watcher has asked for the glasses. A flag rather than a job handle, so the
   * session's own child ([observeGlasses]) is the only thing that ever runs one: toggled off,
   * `collectLatest` cancels the run mid-flight; toggled on again, a fresh one starts. Dies with the
   * session either way.
   */
  private val wantGlasses = MutableStateFlow(false)

  /**
   * A standing ask from [startOnGlasses] that no session has honoured yet.
   *
   * The ask can arrive before the session opens — a voice launch lands while the cover is still
   * rising — and [observeSession]'s reset would silently clear a bare [wantGlasses]. So the ask is
   * latched here and the reset *consumes* it instead: whichever of the two runs first, the session
   * opens reaching for the glasses. Spent the moment a run starts, so it never outlives the launch
   * that made it.
   */
  private var startOnGlassesRequested = false

  /**
   * How many photos this session has taken — the index the Director's photo section is keyed on.
   * The caller owns the count by contract ([DemoDirector.responseToPhotoAt]), and it resets when a
   * session starts, which is also the whole of how a fumbled take is recovered.
   */
  private var photoIndex = 0

  /**
   * The photograph currently crossing from the glasses, when one is.
   *
   * Held for one reason: so the run's teardown can **wait** for it rather than race it — see
   * [RealtimeUiState.ending]. Nothing cancels it, because a crossing is ended by the link going
   * down under it, not by dropping the handle; and it is launched in [viewModelScope] rather than
   * the session's scope so that the wait cannot deadlock on its own child.
   */
  private var captureJob: Job? = null

  /**
   * The run itself — [observeSession], held here rather than by whichever screen is showing it.
   * **The session belongs to the app, not to the cover**: a run started by a voice launch with the
   * phone locked in a pocket has no screen to hold it, and a cover that closes must not take a
   * session in progress down with it. [start] opens it; [stopSession] cancels it. Nothing else
   * does.
   */
  private var runJob: Job? = null

  /**
   * Opens a run, or joins the one in flight.
   *
   * **Idempotent while a run is in flight**, which is what lets the cover call it on every
   * appearance and the shell call it on every voice launch without either knowing about the other:
   * a launch that lands before the cover is up starts the run, and the cover then shows the session
   * already going; a launch that lands mid-run only adds the ask for the glasses. A fresh run
   * clears whatever review the last one left behind.
   *
   * [onGlasses] is the voice launch's request — the wearer spoke from the glasses, so the session
   * should arrive already reaching for them (see [startOnGlasses]). Latched straight onto the fresh
   * run rather than routed through [startOnGlasses], whose guards are about a session that is
   * already open.
   */
  fun start(onGlasses: Boolean = false) {
    if (runJob?.isActive == true && !_uiState.value.isReviewing) {
      if (onGlasses) startOnGlasses()
      return
    }
    runJob?.cancel()
    _uiState.update { it.copy(review = null) }
    startOnGlassesRequested = onGlasses
    runJob = viewModelScope.launch { observeSession() }
  }

  /**
   * Runs the session: the microphone, and whatever is reporting findings alongside it.
   *
   * Re-entrant — a second visit is a new session, on a clean strip, rather than the tail of the
   * last one. The findings are a child of the audio: when the spine stops, for any reason, the
   * script stops with it.
   *
   * Reachable to the tests, which drive a run directly; the app goes through [start], which owns
   * the job this runs in.
   */
  suspend fun observeSession() = coroutineScope {
    analyzer.reset()
    sonogram.reset()
    recorder.reset()
    // The app's own voice belongs to the run that said it. The clock restarts here, so a line
    // carried across would read as having been said moments from now, for ever.
    recentlySaid.clear()
    deafUntil = 0.0
    clock.start()
    _elapsed.value = 0.0
    _frame.value = null
    latestFrame = null
    _viewfinder.value = null
    _zoom.value = 1f
    // The reset consumes a standing ask rather than clearing it — the one way a request
    // made before the session opened survives into it. See [startOnGlassesRequested].
    wantGlasses.value = startOnGlassesRequested
    photoIndex = 0
    _uiState.value =
        RealtimeUiState(
            sourceKind = audioSource.kind,
            isRunning = true,
            session = RealtimeSession(startedAt = System.currentTimeMillis()),
            cameraControls = previewSource.controls,
        )

    BirdLog.info(LogCategory.SESSION) { "session started — listening on ${audioSource.kind}" }

    try {
      runLanes()
    } finally {
      _uiState.update { it.copy(isRunning = false) }
    }
  }

  /** The lanes of one run, for as long as the microphone stream lasts — see [observeSession]. */
  private suspend fun runLanes() = coroutineScope {
    val findings = launch {
      detector.findingStream().collect { finding ->
        record(finding.at(clock.elapsed))
        if (finding is SessionFinding.Bird) {
          showOnGlassesDisplay(finding.speciesId)
          // The one lane whose words are composed rather than authored, unless the
          // finding brought its own — see [heardAloud]. Hands-free is the whole premise
          // of the ambient path, and a watcher with their eyes on a hedge is exactly the
          // person who should not have to look at a phone to find out what just called.
          announce(
              finding.spokenLine ?: heardAloud(finding.commonName, finding.confidence),
          )
        }
      }
    }
    val fix = launch { stampLocation() }
    val bearing = launch { observeHeading() }
    val aim = launch { observeGaze() }
    val ticking = launch { observeClock() }
    val glasses = launch { observeGlasses() }

    audioSource
        .audioStream()
        .catch { error ->
          BirdLog.error(LogCategory.SESSION, error) {
            "session ended on a failed microphone"
          }
          _uiState.update { it.failed(error) }
        }
        .collect { chunk ->
          place(chunk)
          // **The pill follows the audio that arrived**, not the audio that was asked for —
          // see [AudioChunk.source] and [RealtimeUiState.hearing].
          _uiState.update { state -> state.hearing(chunk.source) }
        }

    findings.cancel()
    fix.cancel()
    bearing.cancel()
    aim.cancel()
    ticking.cancel()
    glasses.cancel()
  }

  /**
   * Draws a chunk onto the strip at the column the session was at when it was heard.
   *
   * Only a real gap moves the write head — see [placement] — and when one does, the silence lands
   * on the strip at the second it actually happened rather than being closed up as though the
   * session had been shorter.
   */
  private fun place(chunk: AudioChunk) {
    // **The write head moves first, and it moves even while the app is talking.** Everything
    // below this line is skipped for the length of an announcement; this is not, because the
    // head is what says where *now* is. Left where it was, the strip's newest column would be
    // the last one before the app started speaking — so the live trace would go on drawing
    // that column's shape, holding a picture of the last bird for as long as the sentence
    // lasts. Advancing writes cleared columns instead: the trace falls flat, which is what
    // the wearer's own microphone actually had in it.
    sonogram.advance(placement(clockColumn = clock.column, written = sonogram.count))
    // **The session stops listening while the app is talking.** A spoken line comes back down
    // whichever microphone is open — the glasses' speakers sit beside their microphones, and a
    // phone speaker sits inches from a phone microphone — so analysing through an announcement
    // would put the app's own voice on the strip and record it into the outing. Dropping the
    // chunks leaves a hole exactly as long as the line, which is what the announcement was: not
    // birdsong. See [SpokenOutput].
    if (spokenOutput.isSpeaking) return
    analyzer.analyze(chunk).forEach { sonogram.append(it) }
    // The recorder hears everything the strip draws — but nothing after the stop: a chunk
    // still crossing when the review opens belongs to the closing microphone, not the
    // outing, whose duration the stop already read off the clock.
    if (!_uiState.value.isReviewing) recorder.record(chunk, clock.elapsed)
  }

  /**
   * Tells the strip that now has moved, about once a frame.
   *
   * A redraw rather than a measurement: nothing is stamped from this, and what it publishes is the
   * clock's own reading taken fresh. It exists because during a gap in the audio there is nothing
   * else to say the window has scrolled.
   */
  private suspend fun observeClock() {
    while (currentCoroutineContext().isActive) {
      _elapsed.value = clock.elapsed
      delay(TimelineTickMillis)
    }
  }

  /**
   * Runs the camera, for as long as the camera mode is open. Called from the screen alongside
   * [observeSession], never instead of it — a session does not stop listening to take a picture.
   */
  suspend fun observeCamera() = coroutineScope {
    // The picture, riding beside the frames. A child rather than a sibling call from the
    // screen, so the two cannot outlive each other — and cancelled with this scope when the
    // mode closes.
    val picture = launch {
      previewSource.viewfinderStream().collect { _viewfinder.value = it }
    }

    previewSource
        .previewStream()
        // A camera that will not open closes the mode rather than ending the session. The
        // viewfinder is the optional half; losing it is not losing the run.
        .catch { closeCamera() }
        .collect {
          latestFrame = it
          // Published only while the UI is actually drawing frames — see [latestFrame].
          if (_viewfinder.value == null || _frame.value == null) _frame.value = it
        }

    picture.cancel()
  }

  fun openCamera() = _uiState.update { it.copy(isCameraOpen = true) }

  fun closeCamera() {
    _uiState.update { it.copy(isCameraOpen = false) }

    // The light is never left on — it is only ever on for the moment a photo is being taken,
    // and if a capture is cut short this is what puts it out.
    previewSource.setTorch(false)

    // A viewfinder that reopened at 5× would look broken. The *flash* setting is not reset with
    // it: that is a preference, not a running light.
    if (_zoom.value != 1f) {
      _zoom.value = 1f
      previewSource.setZoom(1f)
    }

    // The last picture, stamped into state *before* the surface goes: the panel falls back to
    // drawing [frame] the instant [viewfinder] empties, and without this it would fall back to
    // whatever the state last carried — the stale first frame, from a camera whose surface has
    // been the picture ever since. With it, the panel dismisses over the still it just showed.
    latestFrame?.let { _frame.value = it }

    // The surface itself dies with the camera — CameraX cancels the request the moment the
    // stream unbinds, so keeping the handle would be keeping a dead one.
    _viewfinder.value = null
  }

  /** Arm the flash, or disarm it. Nothing lights up until the shutter fires. */
  fun toggleFlash() {
    _uiState.update { it.copy(isFlashOn = !it.isFlashOn) }
  }

  /** Magnify, clamped to what this camera will actually do. */
  fun setZoom(factor: Float) {
    val clamped = factor.coerceIn(previewSource.zoomRange)
    _zoom.value = clamped
    previewSource.setZoom(clamped)
  }

  /**
   * Magnify by a step — what a pinch that reports *change* rather than *total* hands over.
   *
   * The multiplication happens here rather than at the gesture, and that is not a style preference:
   * `detectTransformGestures` fires many times between frames, and a screen computing `zoom * step`
   * from its own collected copy of [zoom] would multiply every one of them against whatever the
   * last recomposition saw. The zoom then advances once per redraw instead of once per event —
   * which is exactly what a pinch moving in steps looks like, and was.
   */
  fun zoomBy(step: Float) {
    setZoom(_zoom.value * step)
  }

  /**
   * The pill's tap. Phone → glasses is a request — the session must *arrive* before the pill flips;
   * glasses → phone is immediate — hanging up needs nobody's permission. The phone's camera panel
   * closes on the way over: the panel is the phone's viewfinder, and the glasses have none.
   */
  fun toggleSource() {
    // **Hanging up is the pill's only power over a running session.** A paused session is the
    // device's to resume — the SDK is explicit that an app must not restart one — so while
    // paused this ends the run rather than pretending to revive it. Tapping the side of the
    // glasses is what picks it back up.
    if (wantGlasses.value || _uiState.value.sourceKind == CaptureSourceKind.GLASSES) {
      wantGlasses.value = false
      return
    }
    if (!_uiState.value.isGlassesAvailable) return
    if (_uiState.value.isCameraOpen) closeCamera()
    _uiState.update { it.copy(sourceNotice = null) }
    wantGlasses.value = true
  }

  /**
   * Reaches for the glasses without a tap on the pill — the voice launch's half of [toggleSource].
   * "Hey Meta, open BirdSpotter" is spoken *from* the glasses, so the session it opens should
   * arrive already asking for them.
   *
   * **It only ever asks; it never hangs up.** A repeat — the launch replayed, the wearer asking
   * again mid-run — finds the ask already made and changes nothing, which is the difference from
   * the pill: a control under a thumb needs an off, a spoken open does not.
   *
   * **And it deliberately skips the pill's [RealtimeUiState.isGlassesAvailable] gate.** A voice
   * launch lands moments after the app does, before the registration and device streams have said
   * anything, and a request gated on them would lose the race it exists to win. [runGlassesSession]
   * holds the honest gates — the grant, and a pair that answers — and its notices explain a launch
   * the glasses could not carry.
   *
   * Safe to call before the session opens: the ask is latched and the opening reset consumes it —
   * see [startOnGlassesRequested].
   */
  fun startOnGlasses() {
    if (wantGlasses.value || _uiState.value.sourceKind == CaptureSourceKind.GLASSES) return
    if (_uiState.value.isCameraOpen) closeCamera()
    _uiState.update { it.copy(sourceNotice = null) }
    startOnGlassesRequested = true
    wantGlasses.value = true
  }

  /**
   * Puts a photograph onto the timeline, at this moment in the session — the shutter's whole job,
   * on either device.
   *
   * On the phone that is the frame currently on screen, and nothing happens without one: a shutter
   * pressed before the camera has answered should do nothing rather than drop a hole in the
   * timeline. On the glasses it is [captureThroughGlasses] — an ask and an arrival, no frame of
   * ours involved.
   */
  fun capturePhoto() {
    // **One press at a time, on either device** — see [RealtimeUiState.isCapturing]. The
    // guard is here rather than in the two paths below so that neither can forget it, and so
    // a shutter pressed twice is one photograph on both of them.
    if (_uiState.value.isCapturing) return

    // Routed on the *session*, not the pill: the eyes and the ears can honestly differ for a
    // beat, and a photograph should go through the glasses whenever they can take one.
    if (_uiState.value.isGlassesSessionLive) {
      captureThroughGlasses()
      return
    }
    if (latestFrame == null) return
    if (!_uiState.value.isFlashOn) {
      commitCapture()
      return
    }
    viewModelScope.launch { captureLit() }
  }

  /**
   * The shutter, when the session is riding the glasses: ask, and the photograph arrives a moment
   * later.
   *
   * **The row is stamped at the press; only the picture waits.** The Bluetooth crossing is a real
   * beat — about a second, sometimes several — and the feature brief is explicit that it should be
   * designed rather than hidden. It used to be shown as the shutter dimming and nothing else, which
   * left the log perfectly still for the whole crossing: the one moment the watcher most wants an
   * answer about, and the screen's answer was to grey out the control they just pressed. A dimmed
   * button reads as *broken*, not as *working*.
   *
   * So the capture lands on the timeline immediately, as a row with no picture in it yet, and the
   * crossing is drawn where the crossing is happening — on the photograph's own row, in the same
   * place its identification will appear a moment later. The press has a visible consequence at the
   * instant it happens, which is the whole of what was missing.
   *
   * Stamped at the press rather than at the arrival for the same reason, and it is the more honest
   * number besides: the photograph is of the second the shutter fired, not of the second Bluetooth
   * finished. It also means the row never moves — a row inserted at press time and re-stamped on
   * arrival would jump down the log past anything the microphone heard in between.
   *
   * The Director is asked only once the picture is real — see [askDirector].
   */
  private fun captureThroughGlasses() {
    _uiState.update { it.copy(isCapturing = true) }

    // The index is claimed now, because the row that carries it exists now. The Director's
    // photo section is keyed on it either way, and claiming it at the press is what lets the
    // arrival find its way back to this row.
    val index = photoIndex
    photoIndex += 1

    // The moment rides only a phone photo — the aim must come from the same device as the
    // capture, and the glasses report no gaze to aim by.
    record(
        SessionEvent.Photo(
            at = clock.elapsed,
            index = index,
            image = null,
            source = CaptureSource.GLASSES,
            gazeContext = null,
            bearingDeg = null,
            identification = null,
        ),
    )

    captureJob = viewModelScope.launch {
      try {
        val photo = glassesCamera.capturePhoto(PhotoFormat.JPEG)
        val image = uprightImage(photo.imageData)
        if (image != null) {
          landPhoto(image, index)
        } else {
          discardPhoto(index)
          _uiState.update { it.copy(sourceNotice = "The photo arrived unreadable.") }
        }
      } catch (error: GlassesError) {
        discardPhoto(index)
        _uiState.update {
          it.copy(sourceNotice = "The photo didn't make it from the glasses.")
        }
      } finally {
        _uiState.update { it.copy(isCapturing = false) }
      }
    }
  }

  /**
   * The picture, arriving into the row that has been waiting for it — and only then the question of
   * what is in it.
   *
   * **The Director is asked here rather than at the press**, so that a scripted answer with a short
   * delay cannot overtake the photograph it is about. A response composed during the crossing would
   * land on a row that is still an empty tile, which reads as the app naming a bird in a picture
   * nobody has seen.
   */
  private fun landPhoto(image: Bitmap, index: Int) {
    // A stopped timeline takes nothing more, not even a picture it was already carrying a row
    // for — the review shows what the session held when it ended, and [stopSession] has
    // already taken the empty row away.
    if (_uiState.value.isReviewing) return
    _uiState.update { it.copy(session = it.session.resolvingPhoto(index, image)) }
    askDirector(index)
  }

  /**
   * A crossing that failed, taking its row with it. See [RealtimeSession.discardingPhoto] for why
   * the row leaves rather than staying.
   */
  private fun discardPhoto(index: Int) {
    if (_uiState.value.isReviewing) return
    _uiState.update { it.copy(session = it.session.discardingPhoto(index)) }
  }

  /**
   * The glasses' bytes, decoded into the upright bitmap the rest of the app assumes.
   *
   * **`ImageDecoder`, never `BitmapFactory`.** A photograph off the glasses is captured in sensor
   * order with an EXIF `Orientation` tag describing the turn, and the two decoders disagree about
   * whose job that tag is: `ImageDecoder` applies it, `BitmapFactory` hands back the untouched
   * buffer. Nothing downstream turns a bitmap, so decoding with the wrong one is a photograph shown
   * on its side.
   *
   * The capability hands over the encoded bytes as they crossed, any EXIF untouched — unless the
   * turn rides the capture metadata beside the image instead, which is the open question the
   * session log measures on every crossing. Either way nothing upstream has applied it, so the
   * decode here is where the photograph comes upright.
   *
   * A software allocator because the bitmap outlives the decode — it is drawn on the timeline and
   * re-encoded into the journal at Save, and a hardware bitmap's pixels are not ours to read.
   */
  private fun uprightImage(data: ByteArray): Bitmap? =
      try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(data))) { decoder, _, _
          ->
          decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
      } catch (error: IOException) {
        null
      }

  /**
   * A photo taken with the light on.
   *
   * **The wait is the whole thing.** Lighting the torch and grabbing the very next frame gets a
   * picture of the scene as it was a moment *before* the light — auto-exposure and auto-white
   * balance both need a beat to answer it, and without one the flash shows up as a brighter version
   * of the same underexposed frame. [FlashSettleMillis] is that beat.
   *
   * The panel stays open across it on purpose: what the watcher sees is the light coming on, the
   * picture brightening, and *then* the panel leaving — which is the sequence that says a flash
   * fired rather than one that says the app hesitated.
   *
   * The settle is held under [RealtimeUiState.isCapturing] for the same reason the crossing is: two
   * presses inside it would light the torch twice, race the two settles against each other for when
   * to put it out, and land two photographs of one moment.
   */
  private suspend fun captureLit() {
    _uiState.update { it.copy(isCapturing = true) }
    try {
      previewSource.setTorch(true)
      delay(FlashSettleMillis)
      commitCapture()
      previewSource.setTorch(false)
    } finally {
      _uiState.update { it.copy(isCapturing = false) }
    }
  }

  private fun commitCapture() {
    val image = latestFrame?.image ?: return
    recordPhoto(image)
    closeCamera()
  }

  /**
   * A photograph landing on the timeline whole — the phone's shutter, which has its frame already
   * and so has no crossing to wait through.
   *
   * The glasses' two halves are [captureThroughGlasses] and [landPhoto]; both end at [askDirector],
   * which is the one place a capture becomes a cue.
   *
   * The moment rides the photo, and only a phone photo has one: the aim must come from the same
   * device as the capture, and the glasses report no gaze to aim by — a phone hanging in a lowered
   * hand knows nothing about where the wearer was looking. Which is why this takes no `source`: it
   * is the phone's, and the aim it stamps is unconditional because of it.
   */
  private fun recordPhoto(image: Bitmap) {
    val state = _uiState.value

    // The Nth capture gets the Nth response — see [DemoDirector.responseToPhotoAt]. The
    // count advances whether or not anything is armed: arming a preset mid-session should
    // not make the next photo replay a row an unarmed capture already spent.
    val index = photoIndex
    photoIndex += 1

    record(
        SessionEvent.Photo(
            at = clock.elapsed,
            index = index,
            image = image,
            source = CaptureSource.PHONE,
            gazeContext = state.elevationBand,
            bearingDeg = state.heading,
            identification = null,
        ),
    )
    askDirector(index)
  }

  /**
   * What the Director makes of the [index]th capture, asked at the moment the photograph is real —
   * the cue its photo section answers to.
   *
   * The wait is only shown when something is genuinely on its way. A capture with no scripted
   * answer is a photograph and nothing more, and a row that spun forever beside one would be the
   * screen promising an answer nobody is composing.
   */
  private fun askDirector(index: Int) {
    val response = director.responseToPhotoAt(index) ?: return
    resolvePhoto(index, PhotoIdentification.Pending)
    val sessionKey = _uiState.value.session.startedAt
    viewModelScope.launch { deliverPhotoResponse(response, sessionKey, index) }
  }

  /**
   * Asks the GPS once, for where the session is being run.
   *
   * Alongside the microphone rather than before it: a first fix can take seconds and a session that
   * would not start listening until the sky had been found would miss the bird it was opened for.
   * Nothing downstream waits on this — a session with no fix is a session that simply carries no
   * coordinates.
   */
  private suspend fun stampLocation() {
    val fix = locationProvider.currentCoordinate()
    _uiState.update { it.copy(coordinate = fix) }
  }

  /**
   * Follows the compass for as long as the session runs.
   *
   * A stream rather than the single fix [stampLocation] takes, because a heading is only true while
   * the watcher is standing that way. Nothing is recorded onto the timeline from it: this says
   * where they are looking *now*, and a bearing from four minutes ago is not a fact worth keeping.
   */
  private suspend fun observeHeading() {
    headingProvider.headingStream().collect { bearing ->
      _uiState.update { it.copy(heading = bearing) }
    }
  }

  /**
   * Follows the tilt for as long as the session runs, the way [observeHeading] follows the turn.
   *
   * Live for the same reason, and recorded for the same reason — which is to say not at all. Where
   * someone was aiming is a fact about the second they were aiming there; a stratum from four
   * minutes ago says nothing about the bird now in front of them.
   */
  private suspend fun observeGaze() {
    gazeProvider.gazeStream().collect { elevation ->
      _uiState.update { it.copy(elevation = elevation) }
    }
  }

  /**
   * The glasses side of the session: watches whether a pair could take it, and runs the DAT session
   * while the watcher wants one. A child of [observeSession], so leaving the screen is what hangs
   * up — the same lifetime every other sense here has.
   *
   * The return type is spelled out because the body ends on a collect that never completes, which
   * makes the inferred type `Nothing` — true, and useless to every caller here.
   */
  internal suspend fun observeGlasses(): Unit = coroutineScope {
    launch {
      combine(
          glassesSession.registrationStateStream(),
          glassesSession.deviceInfoStream(),
      ) { registration, device ->
        val reachable =
            registration == GlassesRegistrationState.REGISTERED && device?.isAvailable == true
        // **Every reading is gated on the pair being reachable**, rather than read straight
        // off the snapshot, so the glance goes quiet with the pair instead of leaving the
        // last thing it saw on screen. A charge from before the glasses went out of range
        // is the one reading worth nothing; *worn* read off the same snapshot is a claim
        // about somebody's face from before they left the room; and a display nothing can
        // reach must stop offering the send it was offering a moment ago.
        //
        // Which is why the snapshot itself is what crosses, gated once: four readings
        // carrying the same condition separately is four chances to forget it.
        reachable to device?.takeIf { reachable }
      }
          .collect { (available, reading) ->
            _uiState.update {
              it.copy(
                  isGlassesAvailable = available,
                  glassesBattery = reading?.batteryLevel,
                  isGlassesWorn = reading?.isWorn,
                  isDisplayAvailable = reading?.hasDisplay == true,
              )
            }
          }
    }
    wantGlasses.collectLatest { wanted ->
      if (wanted) {
        // The run beginning is what spends a standing ask — however it was made,
        // and before it can leak into a session the wearer did not speak for.
        startOnGlassesRequested = false
        runGlassesSession()
      }
    }
  }

  /**
   * One glasses session, from toggle to hang-up.
   *
   * Each state the device reports is applied by [RealtimeUiState.applying], which holds the rule
   * about what the pill may claim. Every way out — toggle back, doff, hinge, failure — funnels
   * through the `finally`, which puts the session back on the phone and leaves behind at most one
   * line about why.
   */
  private suspend fun runGlassesSession() {
    // The pill goes to `Linking` now, before anything is asked of the pair, because the
    // first thing asked of it may be waiting — see below.
    _uiState.update {
      it.copy(isGlassesRequested = true, isSourceLinking = true, sourceNotice = null)
    }

    // **The pair is waited for before it is asked anything.** A voice launch lands
    // moments after the app does, while the pair is still listed as disconnected; its
    // link comes up a few seconds later, and until it does the grant below is unreadable
    // and a session cannot be opened on it. Asked at once, every launch from a cold start
    // ended on the phone with a notice about glasses that were on the wearer's face. So
    // the run holds on `Linking` until the snapshot says the pair is reachable, and a pair
    // that never arrives is the same answer as one that does not respond.
    if (!awaitGlassesReachable()) {
      giveUpReaching(
          "Your glasses aren't answering — unfold them and check they're in range.",
      )
      return
    }

    // Two ways to fail this gate, and they send the wearer to different places. A
    // denial is a trip to Settings; an *unreadable* grant means DAT could not reach a
    // pair to ask, and Settings has nothing to offer — the glasses do.
    when (glassesSession.access(GlassesPermission.CAMERA)) {
      GlassesAccess.GRANTED -> Unit
      GlassesAccess.DENIED -> {
        giveUpReaching("Allow camera access in Settings → Meta AI Glasses first.")
        return
      }
      GlassesAccess.UNKNOWN -> {
        giveUpReaching(
            "Your glasses aren't answering — unfold them and check they're in range.",
        )
        return
      }
    }

    // The shutter on the temple, for as long as this run holds the glasses. Its own child
    // rather than a branch of the collector below, because the session stream speaks only
    // when the session's *state* changes — and a press is not a state.
    val inputJob = viewModelScope.launch { routeGlassesInput() }

    // And the answer to the one thing this run asks for that nothing else reports on: whether
    // the glasses' microphone was actually to be had. Scoped to the run, so a loss arriving
    // after the hang-up finds nobody listening.
    val earsJob = viewModelScope.launch { watchGlassesEars() }

    // And the watcher's own voice, on the same terms as the shutter: its own child, because
    // an utterance is not a session state either, and scoped to the run because a question
    // asked of a session that has ended has nobody left to answer it.
    val questionsJob = viewModelScope.launch { listenForQuestions() }

    // What is left on screen once this is over. Assigned in the `finally` rather than in
    // the `catch`, so a session that paused and then stopped does not keep explaining the
    // pause it already recovered from — and so hanging up deliberately says nothing at all.
    var parting: String? = null
    try {
      // docs:glasses-session-state:begin
      glassesSession.sessionStream().collect { state ->
        _uiState.update { it.applying(state) }
        // The ears follow the session: asked for on STARTED, handed back on any pause or
        // stop. The wrapper only *offers* the glasses — the pill does not move until a
        // chunk actually arrives from them.
        audioFailover?.usePreferred(_uiState.value.isGlassesSessionLive)
        // The senses move together: a session live enough to photograph through is one
        // whose wearer's head is the thing aimed at the bird, so the compass and the
        // attitude cross with the ears rather than on a rule of their own.
        headingFailover?.usePreferred(_uiState.value.isGlassesSessionLive)
        gazeFailover?.usePreferred(_uiState.value.isGlassesSessionLive)
      }
      // docs:glasses-session-state:end
    } catch (error: CancellationException) {
      throw error
    } catch (error: Exception) {
      // **The line that says which failure this was.** [partingFor] flattens everything
      // but the stale device into one sentence, which is right for a watcher and useless
      // to whoever reads the log afterwards: a link that dropped, a capability that
      // refused, and a capability that threw on the way in all arrive here looking
      // identical. The frames go with it at debug, because the failures worth chasing are
      // the ones this app did not throw itself and so has no line of its own for.
      BirdLog.error(LogCategory.GLASSES, error) { "glasses session — ended in failure" }
      BirdLog.debug(LogCategory.GLASSES) { error.stackTraceToString() }
      parting = partingFor(error)
    } finally {
      inputJob.cancel()
      earsJob.cancel()
      questionsJob.cancel()
      wantGlasses.value = false
      audioFailover?.usePreferred(false)
      headingFailover?.usePreferred(false)
      gazeFailover?.usePreferred(false)

      // A photograph still crossing when the run ends dies with it, and the two of them must
      // not both speak — see [RealtimeUiState.ending]. The flag is read *before* the wait,
      // because the capture clears it on its way out.
      //
      // NonCancellable because the usual way here is a cancelled run — the watcher tapping
      // the switch — and a `join` in a cancelled coroutine would throw past the reset,
      // leaving the screen claiming a session that has ended. The wait is short by
      // construction: `DatGlassesSessionRepository` fails a crossing the moment its stream
      // goes down, so the only thing still on the transfer timeout is a photograph crossing
      // a link that is up — which is not a run that is ending.
      val crossingLost = _uiState.value.isCapturing
      if (crossingLost) withContext(NonCancellable) { captureJob?.join() }
      _uiState.update { it.ending(crossingLost = crossingLost, parting = parting) }
    }
  }

  /**
   * Holds until the pair is reachable — registered, and its link up — or until
   * [GlassesArrivalPatienceMillis] runs out. Answers whether it is. Read off the snapshot the
   * session already keeps (see [observeGlasses]), so the answer is the one the pill's own switch is
   * gated on.
   */
  private suspend fun awaitGlassesReachable(): Boolean =
      withTimeoutOrNull(GlassesArrivalPatienceMillis) {
        _uiState.first { it.isGlassesAvailable }
      } != null

  /**
   * Takes the reach for the glasses back before a run ever opened, leaving [parting] on screen. A
   * cancelled reach never gets here: cancellation throws past it, and the reset in the run's
   * `finally` says nothing.
   */
  private fun giveUpReaching(parting: String) {
    _uiState.update {
      it.copy(isGlassesRequested = false, isSourceLinking = false, sourceNotice = parting)
    }
    wantGlasses.value = false
  }

  /**
   * The landing half of the STT lane: the preset's line, after its composing delay, with the linked
   * bird — when the row carries one — riding alongside it as a real identification. One event,
   * because they are one moment of the app speaking; the bird has **no confidence**, deliberately:
   * the app did not guess, the watcher described it.
   *
   * Fired by [listenForQuestions] when the glasses hear an utterance that matches an authored
   * prompt. There is deliberately no tap-to-fire stand-in: a scripted exchange the presenter
   * triggered by touch would fake the *input* too, and the Director only ever fakes answers.
   *
   * [heard] is what the watcher actually said, when anything did — which is what the journal keeps
   * rather than the authored prompt that happened to match it. Null where nothing was heard, and
   * then the prompt stands in.
   *
   * Internal so the mirrored tests can pin the landing — and the QA exchange it becomes at Save —
   * without a dispatcher under `viewModelScope`, the same seam `deliverPhotoResponse` keeps.
   */
  internal suspend fun deliverAnswer(
      question: DemoQuestion,
      sessionKey: Long,
      heard: String? = null,
  ) {
    delay(question.delayMillis.toLong())
    if (!isSameRun(sessionKey)) return
    // A linked id the catalog cannot resolve falls back to a words-only reply — the
    // same silence the Director keeps, and better than a card naming nobody.
    val name = question.speciesId?.let { commonName(it) }
    record(
        SessionEvent.Answer(
            at = clock.elapsed,
            text = question.answer,
            question = heard ?: question.prompts.firstOrNull(),
            speciesId = if (name != null) question.speciesId else null,
            commonName = name,
        ),
    )
    if (name != null) {
      showOnGlassesDisplay(question.speciesId)
    }
    // The answer is the one line the app says or shows, so the exchange the watcher started
    // out loud is finished out loud — there is no second field to author for a question, and
    // a reply that only appeared on a phone in a pocket would not be a reply.
    announce(question.answer)
  }

  /**
   * The scripted response to a photo, after its authored delay — landing **on the photo it
   * answers**, at [photoIndex], rather than as a row of its own further down the log.
   *
   * A species lands as the bird itself; *no identification* lands as the row's caption — the one
   * answer where the words are the whole of what the app has to say; an ambiguity lands as the
   * question it is, for the STT section to settle. A species the catalog cannot resolve resolves to
   * **nothing** — the same silence the Director's ambient path keeps, except that here the silence
   * has to be delivered: the photo is sitting on the log with its dots running, and clearing them
   * is how it stops waiting for a row that is never coming.
   */
  internal suspend fun deliverPhotoResponse(
      response: DemoPhotoResponse,
      sessionKey: Long,
      photoIndex: Int,
  ) {
    delay(response.delayMillis.toLong())
    if (!isSameRun(sessionKey)) return
    val identification =
        when (val result = response.result) {
          is DemoResult.Species ->
              commonName(result.speciesId)?.let { name ->
                PhotoIdentification.Bird(
                    speciesId = result.speciesId,
                    commonName = name,
                    confidence = result.confidence.toFloat(),
                )
              }

          is DemoResult.Ambiguous -> {
            val names = result.candidateIds.mapNotNull { commonName(it) }
            if (names.size == result.candidateIds.size && names.isNotEmpty()) {
              PhotoIdentification.Words(names.joinToString(" or ") + "?")
            } else {
              null
            }
          }

          DemoResult.NoIdentification ->
              response.caption.takeIf { it.isNotBlank() }?.let(PhotoIdentification::Words)
        }
    resolvePhoto(photoIndex, identification)
    // Only a named bird goes up: an ambiguity is a question, and a question on a
    // surface with no way to answer it is just a bird the app refuses to commit to.
    if (identification is PhotoIdentification.Bird) {
      showOnGlassesDisplay(identification.speciesId)
    }
    // The spoken line is the row's, not the result's: it is said whatever the capture came
    // back with, including nothing. "Not enough to go on" is worth hearing by a watcher who
    // is still holding the shutter, and the row that runs off the end of the script authors
    // no line at all — which is how it stays silent.
    announce(response.spokenLine)
  }

  /**
   * Fills a waiting photo's answer in, or clears its wait. The session's own guard again: a
   * timeline that has stopped takes nothing more, not even the resolution of something it was
   * already carrying — the review shows what the session held when it ended.
   */
  private fun resolvePhoto(photoIndex: Int, identification: PhotoIdentification?) =
      _uiState.update { state ->
        if (state.isReviewing) {
          state
        } else {
          state.copy(session = state.session.resolvingPhoto(photoIndex, identification))
        }
      }

  /**
   * Whether a delayed cue still belongs to the session on screen: same run, still recording. A
   * response composed across a stop, or across a fresh session, lands nowhere — a stopped timeline
   * takes nothing more.
   */
  private fun isSameRun(sessionKey: Long): Boolean {
    val state = _uiState.value
    return !state.isReviewing && state.session.startedAt == sessionKey
  }

  private suspend fun commonName(speciesId: String): String? = runCatching {
    birdCatalog.findById(speciesId)
  }
      .getOrNull()
      ?.species
      ?.commonName

  /**
   * The identified bird, put where the wearer is already looking — its catalog photographs, paged
   * on the glasses. Every identification lands here, whatever asked the question: the microphone, a
   * photograph, the wearer's own description.
   *
   * Fire-and-forget, deliberately. The timeline is the record and it is already written; the
   * display is a courtesy, and neither a pair with no display nor a slug the catalog cannot resolve
   * is worth holding anything up for — both simply show nothing.
   */
  private fun showOnGlassesDisplay(speciesId: String) {
    viewModelScope.launch {
      val bird = runCatching { birdCatalog.findById(speciesId) }.getOrNull()
      if (bird == null) {
        // The one silent way a push could end before it began — worth a line, because
        // from the screen it is indistinguishable from a send that fell off the link.
        BirdLog.warning(LogCategory.GLASSES) {
          "display — $speciesId is not in the catalog, so there is no card to send"
        }
        return@launch
      }
      // docs:display-gallery:begin
      glassesDisplay.showGallery(bird)
      // docs:display-gallery:end
    }
  }

  /**
   * A bird already on the timeline, put back on the display — the log's own tap.
   *
   * **What it is for is the second look.** A card is replaced by the next identification and
   * cleared by the stop, and neither of those asks the wearer whether they were finished reading.
   * Three birds in a minute is three cards, of which the wearer saw the last one; the timeline is
   * the record of the other two, and this is what makes that record reach the glass again. It sends
   * the same card the arrival sent — the identification is not re-made, it is re-shown.
   *
   * **A control is a promise, so this one is never refused.** [showOnGlassesDisplay] fires at every
   * identification and asks nobody, because a send with nowhere to land costs nothing — an
   * automatic push may land nowhere and the wearer is none the wiser. A press is different: the
   * watcher asked, and *nothing happened* is the one answer a row must not give. So where there is
   * glass the card goes up there, and where there is not it opens on the phone in the same layout —
   * see [cardOnPhone]. Most pairs have no display and most demos have no pair; a tap that only
   * worked on the best hardware in the room would be a control that works when the demo is already
   * going well.
   *
   * [RealtimeUiState.cardGoesToGlasses] is re-read here rather than trusted from the draw, so a
   * pair that left the room between the two lands its card on the phone.
   */
  fun showCard(speciesId: String) {
    val state = _uiState.value
    if (state.cardGoesToGlasses) {
      BirdLog.debug(LogCategory.GLASSES) { "card — tap for $speciesId, routed to the glasses" }
      showOnGlassesDisplay(speciesId)
      return
    }
    // The gates behind the route, spelled out: which one said no is the whole diagnosis
    // when a card the watcher expected on the glass opens down here instead.
    BirdLog.debug(LogCategory.GLASSES) {
      "card — tap for $speciesId, routed to the phone" +
          " (display available: ${state.isDisplayAvailable}," +
          " glasses session live: ${state.isGlassesSessionLive})"
    }
    // No glass to draw on — so the card is drawn here instead, in the layout it would have
    // had up there. A row that only answered on some pairs would be a control that works
    // when the demo is going well.
    viewModelScope.launch {
      val bird = runCatching { birdCatalog.findById(speciesId) }.getOrNull() ?: return@launch
      _uiState.update { it.copy(cardOnPhone = bird) }
    }
  }

  /** Puts the phone's card away. The glasses' card has no equivalent — see [cardOnPhone]. */
  fun dismissCard() = _uiState.update { it.copy(cardOnPhone = null) }

  /**
   * The row's own words, said where the wearer will hear them — the display's twin for the ear, and
   * the other half of *the phone stamps the identification and the glasses answer*.
   *
   * **A line is authored, never composed.** What is said is what the preset's row was given to say,
   * so an operator who wants the app to speak writes the sentence and an operator who does not
   * leaves the field empty — the same bargain the Director makes everywhere else. There is no
   * "speak: on/off" switch, because *whether* is not the app's decision either: a line is always
   * handed over and lands nowhere when there is no ear in reach.
   *
   * Fire-and-forget for the same reason the display is. The timeline is the record and it is
   * already written; this is a courtesy, and a session must not wait on one.
   */
  private fun announce(words: String?) {
    val line = words?.takeIf { it.isNotBlank() } ?: return
    // **Deaf from the moment the line is handed over**, not from the moment the synthesiser
    // gets around to it: the gap between the two is a window the app can hear itself through,
    // and one utterance through it is enough to start the loop over. See [stopListening].
    stopListening(line)
    // docs:spoken-line:begin
    viewModelScope.launch {
      spokenOutput.speak(line)
      // And again on the way out, because the transcript of what was just said has not
      // arrived yet — the recogniser is still waiting for the silence that ends it. The
      // line itself is already remembered; only the window is pushed back.
      stopListening()
    }
    // docs:spoken-line:end
  }

  /**
   * Shuts the question lane for as long as the app's own voice could still come back through it.
   *
   * **The app talks to the wearer through the same pair of glasses it listens to them with**, and
   * the on-device recogniser cannot tell the two voices apart. Left alone that is not a glitch but
   * a *loop*: a line goes out, comes back as an utterance, matches nothing, and earns the "didn't
   * catch that" line — which goes out, comes back, and matches nothing. It sustains itself
   * indefinitely and it fills the log while it does.
   *
   * Two things close the window, because neither is enough alone. [SpokenOutput.isSpeaking] covers
   * the line while it is in the air, and this stamp covers the tail after it: the recogniser does
   * not deliver a final until the utterance has been quiet for a beat, so the echo of a line lands
   * *after* the line has finished playing.
   *
   * The cost is that a wearer who answers the instant the app stops talking is not heard, which is
   * the right side of the trade: a question can be asked again, and a loop cannot be talked over.
   */
  private fun stopListening(line: String? = null) {
    deafUntil = clock.elapsed + EchoTailSeconds
    val words = line?.let(::spokenWords) ?: return
    if (words.isEmpty()) return
    recentlySaid.addLast(SpokenLine(words, clock.elapsed))
    while (recentlySaid.size > RecentlySaidLimit) recentlySaid.removeFirst()
  }

  /**
   * Whether a transcript arriving now is the app's own voice rather than the wearer's.
   *
   * **Three questions, because the window alone was not enough.** The first run with the guard
   * still looped: the app answered, the tail expired, and the echo arrived a second later — a
   * recogniser holds an utterance open until it has heard silence and only then delivers, so how
   * late an echo lands is a property of the room rather than a number this app can pick.
   *
   * The third question is the one that does not depend on timing at all: **the app knows exactly
   * what it just said.** A transcript that is mostly the words of a line the app spoke moments ago
   * is that line coming back, whenever it happens to arrive — which is how *"That's likely a green
   * day."* is recognised as *"That's likely a Green Jay."* despite the recogniser having misheard a
   * word of it.
   */
  private fun isHearingItself(transcript: String): Boolean =
      spokenOutput.isSpeaking ||
          clock.elapsed < deafUntil ||
          soundsLikeSomethingJustSaid(transcript)

  /**
   * Whether these words are mostly a line the app has just spoken.
   *
   * **Most of the words, not all of them, because an echo is misheard on its way back.** The
   * recogniser is listening to a synthesiser through a Bluetooth microphone, and it gets a word
   * wrong — *jay* for *day* — which is exactly enough to defeat comparing the two strings. Counting
   * how much of what was heard was also in what was said survives that.
   *
   * **The line is drawn at length, and that is the honest cost of this check.** An utterance made
   * only of words the app just said is genuinely ambiguous — the wearer picking the bird's name
   * back up is the same string as the echo of it. Under [EchoMinimumWords] is let through and
   * answered; a longer verbatim repeat is taken for the echo it usually is. That trades a rare lost
   * question for a loop, which is the right way round.
   */
  private fun soundsLikeSomethingJustSaid(transcript: String): Boolean {
    val heard = spokenWords(transcript)
    if (heard.size < EchoMinimumWords) return false
    val now = clock.elapsed
    return recentlySaid.any { said ->
      now - said.at in 0.0..EchoMemorySeconds &&
          heard.count { it in said.words }.toDouble() / heard.size >= EchoWordOverlap
    }
  }

  /**
   * A line as the words it is made of, punctuation and casing dropped — so a transcript and the
   * sentence it echoes are comparable word for word.
   */
  private fun spokenWords(line: String): List<String> =
      line.lowercase().replace(Regex("[^a-z0-9 ]"), " ").split(" ").filter { it.isNotEmpty() }

  /**
   * The wearer's hands on the glasses, routed to the same places the controls on screen go.
   *
   * **It calls [capturePhoto] rather than reaching for the glasses path directly**, and that is the
   * whole design: a press and a tap become the same act, so the choice of which device photographs
   * — and the rule that only one photograph may be in flight — are decided once, for both.
   *
   * **Which is also where the debounce comes from.** Two presses in quick succession are one
   * photograph, because the second arrives while [RealtimeUiState.isCapturing] is still set and is
   * turned away there. That guard is a better answer than a timer: it lasts exactly as long as the
   * crossing it protects, where a fixed window is either too short to catch a fumbled double press
   * or long enough to eat a second photograph the watcher genuinely meant to take. Nothing else
   * here needs to know about press types — a hold and a double press never reach the domain (see
   * [GlassesInputEvent]).
   *
   * Every press lands on the main dispatcher, the same as a tap, so the guard is read and set in
   * one place with no interleaving to reason about.
   *
   * **Back lands on [stopSession], which is the whole reason the gesture is taken from the system
   * at all.** Left alone it would end the run by ending the app on the glasses, which is a stop
   * with no review, no saved outing and nothing on screen to explain itself. Answering it here
   * makes the wearer's swipe the same act as the vermilion stop they would otherwise have reached
   * for the phone to press. A swipe once the review is already up changes nothing — [stopSession]
   * turns the second one away, the way it turns away a second tap.
   */
  internal suspend fun routeGlassesInput() {
    // docs:input-events:begin
    glassesInput.inputEventStream().collect { event ->
      when (event) {
        GlassesInputEvent.SHUTTER -> capturePhoto()
        GlassesInputEvent.BACK -> stopSession()
      }
    }
    // docs:input-events:end
  }

  /**
   * Listens for the glasses' microphone turning out not to be available, and takes the screen's
   * promise back when it does — see [RealtimeUiState.withoutGlassesEars].
   *
   * **Nothing else can tell.** The failover reopens the phone and the chunks keep coming, which is
   * exactly what it is for and exactly why the loss is invisible from the stream: the pill's
   * `Linking` is closed by glasses audio arriving, and that is the one thing that is never going to
   * happen now.
   *
   * Internal so the mirrored tests can drive it without standing a session up.
   */
  internal suspend fun watchGlassesEars() {
    audioFailover?.preferredLost()?.collect {
      _uiState.update { it.withoutGlassesEars() }
    }
  }

  /**
   * The watcher's own voice, routed to the Director — the third way a session gets an answer,
   * beside the ambient lane and the shutter.
   *
   * **The utterance is the question.** The recogniser says when somebody has finished speaking, and
   * that boundary is what the app treats as an ask: no button frames it, no wake word opens it. A
   * run with the glasses is listening for the whole of its length, which is the shape the hardware
   * actually offers — recognition happens up there, so nothing on the phone is being borrowed to do
   * it.
   *
   * **Partials are carried past.** The same sentence lands several times as it develops, and
   * matching on one of those would fire an answer to half a question — "is that a green" before the
   * jay was said.
   *
   * Internal so the mirrored tests can drive it without standing a session up.
   */
  internal suspend fun listenForQuestions() {
    glassesSpeech.transcriptionStream().collect { heard ->
      if (heard.isFinal) answerAloud(heard.text)
    }
  }

  /**
   * One finished utterance, answered.
   *
   * The words land on the log first and immediately — before the Director has been asked, and
   * whatever it says. **That is the honest half of the exchange**: what the glasses heard is a fact
   * about the run, and a session that only ever showed the app's replies would be hiding the input
   * those replies were made of. It is also the feedback that makes the demo readable — the sentence
   * appears as it is said, and the answer arrives on its authored beat after it.
   *
   * Then one of two things. A match lands the authored answer through [deliverAnswer]. A miss lands
   * the preset's own "didn't catch that" line, which is the row every preset carries for exactly
   * this and is the reason a miss is not silence: the app heard something, could not place it, and
   * says so rather than leaving the watcher wondering whether it was listening.
   *
   * **A run with nothing armed says nothing at all.** There is no preset, so there is no line to
   * say — the words still land on the log, which is all a session with identification switched off
   * has ever done.
   *
   * Internal on the same terms as [deliverAnswer], and for the same reason.
   */
  internal suspend fun answerAloud(transcript: String) {
    val words = transcript.trim()
    if (words.isEmpty()) return
    // **Before anything is written down**, because an echo is not something the wearer said
    // and has no business on the log, let alone in front of the Director. See
    // [stopListening] for what this is protecting against.
    if (isHearingItself(words)) {
      BirdLog.debug(LogCategory.GLASSES) { "speech — \"$words\" was the app's own voice" }
      return
    }
    val sessionKey = _uiState.value.session.startedAt
    if (!isSameRun(sessionKey)) return
    record(SessionEvent.Speech(at = clock.elapsed, text = words))

    val question = director.answerTo(words)
    if (question != null) {
      deliverAnswer(question, sessionKey, heard = words)
      return
    }
    // **Blank is the off switch, and it is the only one.** A run listening for its whole
    // length hears the presenter talking to the room as well as to the app, and every one of
    // those sentences is a miss — so an operator who does not want the app answering them
    // clears the preset's line and gets silence, with the words still on the log. There is no
    // separate toggle for the same reason there is no speak-on/off: the authored field
    // already says whether there is anything to say.
    val line = director.armed?.unmatchedQuestion?.takeIf { it.isNotBlank() } ?: return
    // A beat, so the miss reads as the app having listened rather than as a reflex. Fixed
    // rather than authored: the preset gives the words, and how long *not* understanding
    // takes is not a thing anybody should be tuning per row.
    delay(UnmatchedDelayMillis)
    if (!isSameRun(sessionKey)) return
    record(SessionEvent.Answer(at = clock.elapsed, text = line, question = words))
    announce(line)
  }

  // ── Stopping, and the confirmation ─────────────────────────────────────

  /**
   * The vermilion stop: ends the recording and lands on the review. Writes nothing — the journal
   * hears nothing until the watcher saves there.
   *
   * Setting the review says the session is over; cancelling the run at the end is what actually
   * closes the microphone. Reachable from the screen, from the glasses (a swipe back) and from the
   * notification a pocketed session is stopped from — the run is the app's, so every stop is this
   * one. The clock is read here, once — the reading Save stores as the duration.
   */
  fun stopSession() {
    if (_uiState.value.isReviewing) return
    if (_uiState.value.isCameraOpen) closeCamera()

    // A photograph still crossing when the stop came never arrives: [landPhoto] is guarded on
    // the review the way every other late cue is, so its row would sit on the confirmation as
    // an empty tile for good. The picture is not coming, so neither is the row — dropped here
    // rather than left for Save to skip, because what the watcher is deciding about should be
    // what the session actually holds.
    _uiState.update { state ->
      val crossing =
          state.session.events.filterIsInstance<SessionEvent.Photo>().filter { it.image == null }
      val settled =
          crossing.fold(state.session) { session, photo ->
            session.discardingPhoto(photo.index)
          }
      state.copy(
          session = settled,
          review = SessionReview(durationSeconds = clock.elapsed),
          // The phone's card goes down with the glasses' one, and for the same reason.
          cardOnPhone = null,
      )
    }
    // After the update, not inside it: `update` re-runs its block on a CAS retry, and a
    // log line written from in there would appear twice for one stop.
    BirdLog.info(LogCategory.SESSION) {
      "session stopped after ${clock.elapsed.toInt()}s — " +
          "${_uiState.value.session.events.size} events on the timeline"
    }

    // The gallery goes down with the recording — a session under review is not a bird
    // in front of the wearer. A line still being said, or queued behind one, goes with it
    // for the same reason: the run it was answering is over.
    spokenOutput.silence()
    viewModelScope.launch { glassesDisplay.clear() }

    // Last, so everything above sees the run as it was: cancelling is what actually closes
    // the microphone, and [observeSession] tears the rest down on its way out.
    runJob?.cancel()
    runJob = null
  }

  fun setReviewNotes(notes: String) = updateReview { it.copy(notes = notes) }

  /**
   * Keep a detection in the journal, or put it back out. The toggle answers per event — the same
   * robin heard twice is two rows here — and what Save writes as sightings is the kept birds, one
   * per species.
   */
  fun toggleBirdKept(at: Double) = updateReview { review ->
    val dropped =
        if (at in review.droppedBirds) {
          review.droppedBirds - at
        } else {
          review.droppedBirds + at
        }
    review.copy(droppedBirds = dropped)
  }

  fun saveOuting() {
    viewModelScope.launch { performSave() }
  }

  /**
   * Save: the whole session as one `LIVE` [OutingDraft], written in a single call.
   *
   * The location rule is the wizard's: the fix was asked for as the session opened; if it never
   * landed, ask once more and then decline rather than logging a walk nowhere. Declining leaves the
   * buttons live and says why.
   *
   * Internal for the same dispatcher reason as the cue deliveries.
   */
  internal suspend fun performSave() {
    val state = _uiState.value
    val review = state.review ?: return
    if (review.isSaving || review.savedOutingId != null) return
    updateReview { it.copy(isSaving = true, saveError = null) }
    try {
      val coordinate =
          state.coordinate
              ?: locationProvider.currentCoordinate()
              ?: throw JournalError.NoLocationFix
      val outingId =
          journal.saveOuting(
              draft(
                  session = state.session,
                  review = review,
                  location = CaptureLocation(coordinate.latitude, coordinate.longitude),
              ),
          )
      BirdLog.info(LogCategory.JOURNAL) { "outing saved — $outingId" }
      updateReview { it.copy(isSaving = false, savedOutingId = outingId) }
    } catch (error: CancellationException) {
      throw error
    } catch (error: Exception) {
      val reason =
          if (error is JournalError.NoLocationFix) {
            BirdLog.warning(LogCategory.JOURNAL) { "outing not saved — no location fix" }
            SessionSaveError.NO_LOCATION_FIX
          } else {
            BirdLog.error(LogCategory.JOURNAL, error) { "outing could not be written" }
            SessionSaveError.WRITE_FAILED
          }
      updateReview { it.copy(isSaving = false, saveError = reason) }
    }
  }

  /**
   * The session's events as the journal's shapes, in one pass over the timeline:
   *
   * - a photo becomes a media row carrying its own moment — and, when the app named a bird in it, a
   *   `DETECTION` event **pointing at that media row**: `seen` rather than `heard`, because the
   *   photo is the evidence and the journal has a column for saying so;
   * - a bird the microphone found becomes a `heard` `DETECTION`, kept or dropped alike, because the
   *   timeline records what happened and the sightings record what the watcher confirmed;
   * - an answer that answered a question becomes the `QA` exchange, whole;
   * - a kept bird becomes a sighting, one per species — confirming its detection where it was one,
   *   and confirming nothing where the watcher's own words reached it, exactly as the wizard's
   *   sightings do.
   *
   * What writes nothing: the watcher's speech (its exchange is written from the answer that paired
   * it) and a caption the app declined with (the journal has no row for an answer that names
   * nobody, by design) — a photo's words-only identification included.
   */
  private fun draft(
      session: RealtimeSession,
      review: SessionReview,
      location: CaptureLocation,
  ): OutingDraft {
    val media = mutableListOf<PendingMedia>()
    val events = mutableListOf<PendingEvent>()
    val detectionByStamp = mutableMapOf<Double, PendingEvent>()

    for (event in session.events) when (event) {
      is SessionEvent.Photo -> {
        val photo = pendingPhoto(event)
        if (photo != null) {
          media += photo
          val named = event.identification as? PhotoIdentification.Bird
          if (named != null) {
            val seen =
                PendingEvent.seen(
                    speciesId = named.speciesId,
                    confidence = named.confidence.toDouble(),
                    inPhoto = photo,
                )
            events += seen
            detectionByStamp[event.at] = seen
          }
        }
      }

      is SessionEvent.Bird -> {
        val heard =
            PendingEvent.heard(
                speciesId = event.speciesId,
                confidence = event.confidence.toDouble(),
                offsetMs = (event.at * 1000).toLong(),
            )
        events += heard
        detectionByStamp[event.at] = heard
      }

      is SessionEvent.Answer ->
          event.question?.let { question ->
            events +=
                PendingEvent.exchange(
                    question = question,
                    answer = event.text,
                    offsetMs = (event.at * 1000).toLong(),
                )
          }

      is SessionEvent.Speech -> Unit
    }

    // What the session heard, one media row per unbroken stretch — the edit decision
    // list the journal plays back and draws. Encoded at Save, like the photos: a
    // discarded session should never have paid for a file.
    for (segment in recorder.segments()) {
      if (segment.samples.isEmpty()) continue
      media +=
          PendingMedia(
              type = OutingMediaType.AUDIO,
              // The journal's enum has no SIMULATED: nothing simulated reaches the audio
              // spine today, and if Mock Device Kit audio ever does, the schema grows the
              // honest value rather than this mapping quietly lying.
              source =
                  if (segment.source == CaptureSourceKind.GLASSES) {
                    CaptureSource.GLASSES
                  } else {
                    CaptureSource.PHONE
                  },
              bytes = WavCodec.encode(segment.samples),
              fileExtension = "wav",
              offsetMs = segment.offsetMs,
              durationMs = segment.durationMs,
          )
    }

    // What can enter the life list: a detection the watcher kept, a bird an answer surfaced
    // that they kept, or a bird the app named in a photo they kept. One sighting per species,
    // first mention wins.
    val sightings =
        session.events
            .mapNotNull { event ->
              if (event.at in review.droppedBirds) return@mapNotNull null
              when (event) {
                is SessionEvent.Bird ->
                    PendingSighting(event.speciesId, confirming = detectionByStamp[event.at])
                is SessionEvent.Answer ->
                    event.speciesId?.let { PendingSighting(it, confirming = null) }
                is SessionEvent.Photo ->
                    (event.identification as? PhotoIdentification.Bird)?.let { named ->
                      PendingSighting(named.speciesId, confirming = detectionByStamp[event.at])
                    }
                else -> null
              }
            }
            .distinctBy { it.speciesId }

    return OutingDraft(
        kind = OutingKind.LIVE,
        startedAt = session.startedAt,
        durationMs = (review.durationSeconds * 1000).toLong(),
        location = location,
        notes = review.notes.trim().ifEmpty { null },
        media = media,
        events = events,
        sightings = sightings,
        // The strip the watcher just watched, kept rather than measured again. Every column
        // of it was computed once already, as the audio arrived — the journal page used to
        // throw that away and run the whole walk back through the analyzer on open.
        sonogram = sonogram.encoded(),
    )
  }

  /**
   * The photo as the journal will hold it. Encoded at save rather than at capture — a discarded
   * session should never have paid for compression — and to JPEG, the format every capture on this
   * screen already is underneath.
   */
  private fun pendingPhoto(photo: SessionEvent.Photo): PendingMedia? {
    // A row whose picture never crossed writes nothing. [stopSession] already takes those
    // away, so this is the belt to that braces — but a media row with no bytes is not a thing
    // the journal should ever be asked to hold.
    val image = photo.image ?: return null
    val stream = ByteArrayOutputStream()
    image.compress(Bitmap.CompressFormat.JPEG, PhotoJpegQuality, stream)
    return PendingMedia(
        type = OutingMediaType.PHOTO,
        source = photo.source,
        bytes = stream.toByteArray(),
        fileExtension = "jpg",
        offsetMs = (photo.at * 1000).toLong(),
        width = image.width,
        height = image.height,
        moment =
            MomentContext(
                gazeContext = photo.gazeContext,
                bearingDeg = photo.bearingDeg,
            ),
    )
  }

  private fun updateReview(transform: (SessionReview) -> SessionReview) = _uiState.update { state ->
    state.review?.let { state.copy(review = transform(it)) } ?: state
  }

  private fun record(event: SessionEvent) = _uiState.update { state ->
    // A stopped timeline takes nothing more: a photograph still crossing from the
    // glasses, or a cue composed across the stop, lands nowhere.
    if (state.isReviewing) state else state.copy(session = state.session.adding(event))
  }

  companion object {

    /**
     * What the photos are re-encoded at for the journal. High, because the capture is the frame the
     * watcher already judged on screen; the cost is paid once, at Save.
     */
    private const val PhotoJpegQuality = 90

    /**
     * How long a run reaching for the glasses waits for the pair to become reachable before it
     * gives up — see [awaitGlassesReachable]. Long enough to cover a cold start's link coming up
     * behind a voice launch, short enough that a pair left in a drawer is answered within the
     * demo's patience.
     */
    private const val GlassesArrivalPatienceMillis = 20_000L

    /**
     * How long the "didn't catch that" line waits before it lands — see [answerAloud]. Long enough
     * to read as the app having considered the question, short enough that nobody wonders whether
     * it heard at all.
     */
    private const val UnmatchedDelayMillis = 600L

    /**
     * How long after the app stops talking the question lane stays shut — see [stopListening].
     *
     * **Measured against the recogniser's endpoint, not against the audio.** A final does not
     * arrive when a line stops playing; it arrives once the recogniser has heard enough silence to
     * call the utterance over, which is a beat later again. Two seconds covers that with room to
     * spare, and costs a wearer who answers instantly one repeat.
     */
    private const val EchoTailSeconds = 4.0

    /**
     * How many words an utterance needs before it can be dismissed as an echo — see
     * [soundsLikeSomethingJustSaid].
     */
    private const val EchoMinimumWords = 3

    /** How much of an utterance has to be the app's own words for it to be its own voice. */
    private const val EchoWordOverlap = 0.6

    /**
     * How long a spoken line stays suspicious. Generous against the recogniser's own lateness, and
     * short enough that a phrase the wearer chooses for themselves a while later is heard.
     */
    private const val EchoMemorySeconds = 20.0

    /** How many spoken lines are kept — only the last few can still be in the air. */
    private const val RecentlySaidLimit = 4

    /**
     * The one line a failed session leaves on screen.
     *
     * **The version mismatch gets its own sentence because it is the failure that lies.** From the
     * outside it is indistinguishable from a dead link — and it is not one: the glasses are on, in
     * range, and the settings screen two taps away is showing their battery and whether they are
     * being worn. Told "the glasses didn't answer", somebody goes looking for a Bluetooth fault
     * that does not exist, and every reading in the app quietly disagrees with them while they
     * look. Naming the update turns a half-hour into a minute.
     *
     * Internal so the mirrored tests can pin both answers without standing a session up.
     */
    internal fun partingFor(error: Exception): String =
        when (error) {
          is GlassesError.GlassesUpdateRequired ->
              "Your glasses need a Meta update before they can run a session — " +
                  "open the Meta AI app."
          else -> "The glasses didn't answer. Try the pill again."
        }
  }
}
