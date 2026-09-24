/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.dat

import android.content.Context
import android.graphics.Bitmap as AndroidBitmap
import android.os.SystemClock
import com.meta.pixelandtexel.birdspotter.domain.AudioCaptureError
import com.meta.pixelandtexel.birdspotter.domain.AudioChunk
import com.meta.pixelandtexel.birdspotter.domain.BirdLog
import com.meta.pixelandtexel.birdspotter.domain.CaptureQuality
import com.meta.pixelandtexel.birdspotter.domain.CaptureResolution
import com.meta.pixelandtexel.birdspotter.domain.CaptureSourceKind
import com.meta.pixelandtexel.birdspotter.domain.CapturedPhoto
import com.meta.pixelandtexel.birdspotter.domain.GlassesAccess
import com.meta.pixelandtexel.birdspotter.domain.GlassesCompatibility
import com.meta.pixelandtexel.birdspotter.domain.GlassesDeviceInfo
import com.meta.pixelandtexel.birdspotter.domain.GlassesError
import com.meta.pixelandtexel.birdspotter.domain.GlassesInputEvent
import com.meta.pixelandtexel.birdspotter.domain.GlassesMotionSample
import com.meta.pixelandtexel.birdspotter.domain.GlassesPermission
import com.meta.pixelandtexel.birdspotter.domain.GlassesRegistrationState
import com.meta.pixelandtexel.birdspotter.domain.GlassesSessionRepository
import com.meta.pixelandtexel.birdspotter.domain.GlassesSessionState
import com.meta.pixelandtexel.birdspotter.domain.GlassesSpeechState
import com.meta.pixelandtexel.birdspotter.domain.GlassesThermalLevel
import com.meta.pixelandtexel.birdspotter.domain.LogCategory
import com.meta.pixelandtexel.birdspotter.domain.PhotoFormat
import com.meta.pixelandtexel.birdspotter.domain.Transcription
import com.meta.pixelandtexel.birdspotter.domain.Vector3
import com.meta.wearable.dat.camera.Camera
import com.meta.wearable.dat.camera.addCamera
import com.meta.wearable.dat.camera.types.AudioCodec
import com.meta.wearable.dat.camera.types.AudioFrame
import com.meta.wearable.dat.camera.types.AudioSampleRate
import com.meta.wearable.dat.camera.types.CameraState
import com.meta.wearable.dat.camera.types.PhotoData
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.StreamState
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.SpecificDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.ChargingState
import com.meta.wearable.dat.core.types.Device
import com.meta.wearable.dat.core.types.DeviceCompatibility
import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.core.types.DeviceSessionError
import com.meta.wearable.dat.core.types.DonState
import com.meta.wearable.dat.core.types.LinkState
import com.meta.wearable.dat.core.types.Permission as DatPermission
import com.meta.wearable.dat.core.types.PermissionStatus as DatPermissionStatus
import com.meta.wearable.dat.core.types.RegistrationState
import com.meta.wearable.dat.core.types.ThermalLevel
import com.meta.wearable.dat.display.Display
import com.meta.wearable.dat.display.addDisplay
import com.meta.wearable.dat.display.removeDisplay
import com.meta.wearable.dat.display.types.DisplayState
import com.meta.wearable.dat.display.views.ContentScope
import com.meta.wearable.dat.inputs.Inputs
import com.meta.wearable.dat.inputs.addInputs
import com.meta.wearable.dat.inputs.removeInputs
import com.meta.wearable.dat.inputs.types.CapturePressType
import com.meta.wearable.dat.inputs.types.InputEvent
import com.meta.wearable.dat.inputs.types.InputSource
import com.meta.wearable.dat.inputs.types.InputsConfiguration
import com.meta.wearable.dat.inputs.types.InputsState
import com.meta.wearable.dat.mockdevice.MockDeviceKit
import com.meta.wearable.dat.motion.Motion
import com.meta.wearable.dat.motion.addMotion
import com.meta.wearable.dat.motion.removeMotion
import com.meta.wearable.dat.motion.types.MotionConfiguration
import com.meta.wearable.dat.motion.types.MotionSample
import com.meta.wearable.dat.motion.types.MotionSamplingRate
import com.meta.wearable.dat.motion.types.MotionSource
import com.meta.wearable.dat.motion.types.MotionState
import com.meta.wearable.dat.speech.Speech
import com.meta.wearable.dat.speech.addSpeech
import com.meta.wearable.dat.speech.removeSpeech
import com.meta.wearable.dat.speech.types.SpeechError
import com.meta.wearable.dat.speech.types.SpeechState
import com.meta.wearable.dat.speech.types.TranscriptionResult
import java.io.ByteArrayOutputStream
import java.nio.ByteOrder
import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The DAT-backed [GlassesSessionRepository] — the one class a live SDK session actually lives in.
 *
 * A session here is three SDK moves held to one cold-stream lease: create a session on a named
 * pair, start it, and — the moment the device reports started — attach the `Camera` capability and
 * start its video stream. The frames themselves are never looked at: resolution stays low and the
 * rate slow, because the stream's job is to hold the camera open for the stills that cross it — a
 * capture is asked of the running stream and travels the stream's own channel. What the stream does
 * carry that the app wants is the glasses' **microphone**, which rides it as audio — see
 * [audioChunksFromActiveSession]. [DatGlassesCameraRepository] reaches the shutter through
 * [captureThroughActiveCamera], which is here because the handles are here.
 */
class DatGlassesSessionRepository(context: Context) : GlassesSessionRepository {

  /** The mock kit, asked one question — see [sessionStream]. */
  private val mockKit = MockDeviceKit.getInstance(context.applicationContext)

  init {
    // The SDK boots here rather than in Application.onCreate, deliberately: glasses code
    // is its only client, Robolectric hosts BirdSpotterApplication for every JVM test
    // and has no business touching DAT, and MainActivity warms the container's lazy at
    // launch so the registration callback can never arrive first. Failure is logged and
    // lived with — BirdSpotter without glasses is still a working bird app, and every
    // stream here then reads unavailable. The configuration call lives in
    // birdspotterApp.init.
    Wearables.initialize(context.applicationContext).onFailure { error, _ ->
      BirdLog.error(LogCategory.GLASSES) { "DAT failed to initialize: ${error.description}" }
    }
  }

  /**
   * The running session's handles: written by the one [sessionStream] collector, read by the
   * shutter. The monitor is the whole concurrency story — nothing here suspends while holding it.
   */
  private val lock = Any()
  private var activeCamera: Camera? = null
  private var activeInputs: Inputs? = null
  private var activeMotion: Motion? = null
  private var activeSpeech: Speech? = null
  private var activeDisplay: Display? = null

  /**
   * The session whichever lease is open was created on — held for [endActiveSessions], which is the
   * one caller that needs to reach a running session from outside its own collector.
   */
  private var activeSession: DeviceSession? = null

  /**
   * The pair the session was opened on, kept so the display attach can ask whether this pair has
   * one — the capability is hardware most pairs do not carry.
   */
  private var activeDeviceId: DeviceIdentifier? = null

  /**
   * The job bringing a capability back after something else on the link put it down — see
   * [reviveMotion] and [reviveInputs]. One at a time each; null whenever nothing is waiting.
   */
  private var motionRevival: Job? = null
  private var inputsRevival: Job? = null
  private var speechRevival: Job? = null

  /**
   * The job asking the display to attach on the display-only lease, where no camera frames gate the
   * asking — see [attachDisplayWithPatience]. Null whenever nothing is asking.
   */
  private var displayPatience: Job? = null

  /**
   * The collectors watching the inputs capability, held as one job so a re-attach can put them down
   * with the capability they were watching — see [bringInputsBack].
   */
  private var inputsCollectors: Job? = null

  /**
   * Whether the button has ever been listening this run. INACTIVE is both the state the capability
   * is born in — replayed to every new collector — and the state it dies in, and only the second
   * one is worth acting on.
   */
  private var inputsHasActivated = false

  /**
   * Whether the camera has been up at all this session. Its `STOPPED` is both the state it is born
   * in and the state it dies in, and only the second one means anything — the state flow replays
   * whatever it currently holds to a new collector, so the first reading a session sees is the one
   * from before it started.
   */
  private var cameraHasStarted = false

  /**
   * Whether the recogniser has ever been listening this run. STOPPED is both the state the
   * capability is born in — replayed to every new collector — and the state it dies in, and only
   * the second one is worth going back for.
   */
  private var speechHasStarted = false

  /**
   * When [describeEvery] last wrote a line, on the sensor's own clock. Zero until the first sample,
   * so the first one always prints.
   */
  private var lastMotionLogMillis = 0L

  /**
   * The presses, fanned out to whoever is listening — see [attachInputsIfNeeded].
   *
   * **A `MutableSharedFlow`, and emphatically not a `StateFlow`.** A `StateFlow` conflates and
   * deduplicates by equality, and every shutter press is equal to every other one: two presses in a
   * row would reach collectors as one, which is precisely the second photograph the watcher meant
   * to take. The buffer absorbs a burst without pushing back on the capability's own stream.
   */
  private val inputEvents = MutableSharedFlow<GlassesInputEvent>(extraBufferCapacity = 8)

  /**
   * The readings, fanned out to whoever is listening — see [attachMotionIfNeeded].
   *
   * **A `MutableSharedFlow` for the same reason the presses are one.** A `StateFlow` drops a
   * reading equal to the one before it, and a wearer holding their head still produces exactly that
   * — so a collector arriving mid-stillness would wait for the wearer to move before it heard
   * anything at all. The buffer absorbs a burst without pushing back on the capability's own
   * stream; `DROP_OLDEST` because the newest attitude is the only one worth having, which is the
   * same bargain the phone's own sensors make with `conflate()`.
   */
  private val motionSamples = MutableSharedFlow<GlassesMotionSample?>(
      extraBufferCapacity = 8,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )

  /**
   * Which sources this run's motion feed has been heard from, so [describeOnce] says its piece once
   * each instead of five times a second. Cleared when the capability attaches — the question it
   * answers is about this pair on this run.
   */
  private val motionSourcesHeard = mutableSetOf<MotionSource>()

  /**
   * Whether this session's stream was opened with the glasses' microphone on it — decided once,
   * before the camera attaches, from Meta AI's microphone grant. See
   * [audioChunksFromActiveSession].
   */
  @Volatile private var streamCarriesAudio = false

  /** Whether [receive] has described this session's first audio buffer yet. */
  @Volatile private var audioArrivalLogged = false

  /**
   * The glasses' microphone, fanned out to whoever is listening — see [attachCameraIfNeeded].
   *
   * **A `MutableSharedFlow` for the reason the readings are one**, and `null` is the end of the
   * audio in the same way — see [finishAudioListeners]. The buffer is generous and the overflow
   * drops the oldest, because the stream's own collector must never be made to wait on a slow
   * listener: a stalled SDK collector is a stalled camera stream, shutter and all.
   */
  private val audioChunks = MutableSharedFlow<AudioChunk?>(
      extraBufferCapacity = 64,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )

  /**
   * What the wearer said, fanned out to whoever is listening — see [attachSpeechIfNeeded].
   *
   * **A `MutableSharedFlow`, and emphatically not a `StateFlow`.** A `StateFlow` conflates and
   * deduplicates by equality, and a transcript is a value that repeats: a partial that holds a
   * stable prefix while the speaker pauses, or the same short word said twice, is two results that
   * compare equal and would reach collectors as one. The buffer absorbs a burst without pushing
   * back on the capability's own stream.
   *
   * The channel this reads *from* has that flaw and nothing here can mend it — see
   * [attachSpeechIfNeeded]. What this guarantees is that the app does not add a second helping of
   * it below the one place the loss happens.
   */
  private val transcriptions = MutableSharedFlow<Transcription>(extraBufferCapacity = 16)

  /**
   * Where the recogniser is, held rather than announced: a screen that opens mid-session has to be
   * told what is already true, and a state that only ever reported *changes* would leave it looking
   * at IDLE while the glasses were listening. Reset with the session that owned it.
   */
  private val speechState = MutableStateFlow(GlassesSpeechState.IDLE)

  /**
   * The photograph currently crossing, when one is: the rendezvous both of its endings meet at —
   * the picture arriving, or the link going down under it. See [failPendingPhoto].
   */
  private var pendingPhoto: CompletableDeferred<PhotoData?>? = null

  override fun registrationStateStream(): Flow<GlassesRegistrationState> =
      Wearables.registrationState
          .map { state ->
            when (state) {
              RegistrationState.AVAILABLE -> GlassesRegistrationState.AVAILABLE
              RegistrationState.REGISTERING -> GlassesRegistrationState.REGISTERING
              RegistrationState.REGISTERED -> GlassesRegistrationState.REGISTERED
              else -> GlassesRegistrationState.UNAVAILABLE
            }
          }
          .onEach {
            // The first thing to check when the glasses will not answer, and the one reading
            // that is otherwise invisible — the Settings card shows it, but only while
            // somebody is looking at Settings.
            BirdLog.info(LogCategory.GLASSES) { "registration — $it" }
          }

  /**
   * The pair this app is about, followed live through the SDK's per-device metadata.
   *
   * **Every paired device is watched, not just the first one listed.** A developer's phone
   * routinely has several pairs registered — Display glasses, a Ray-Ban, an older pair — of which
   * at most one is connected at a time. [Wearables.devices] is a `Set`, so "the first" is not even
   * a stable question, let alone the right one; see [preferred].
   */
  override fun deviceInfoStream(): Flow<GlassesDeviceInfo?> = channelFlow {
    Wearables.devices.collectLatest { identifiers ->
      val readings = identifiers.mapNotNull { Wearables.devicesMetadata[it] }
      if (readings.isEmpty()) {
        send(null)
        return@collectLatest
      }
      // Combined rather than collected one at a time: which pair matters depends on
      // all of them, so every device's news has to arrive at the same place.
      combine(readings) { devices -> devices.toList() }
          .collect { devices ->
            val chosen = preferred(devices)
            // Logged as well as published: "not reachable" has several causes that look
            // identical on screen, and the roster is what tells them apart on a phone
            // nobody can attach a debugger to mid-demo. Filter logcat for `glasses reading`.
            BirdLog.info(LogCategory.GLASSES) {
              "glasses reading — paired: ${devices.size} " +
                  "[${devices.joinToString { "${it.name.ifBlank { "?" }}=${it.linkState}" }}]" +
                  " · chosen: \"${chosen.name}\", " +
                  "link: ${chosen.linkState}, " +
                  "compatibility: ${chosen.compatibility}, " +
                  "don: ${chosen.donState}, " +
                  "battery: ${chosen.batteryLevel.takeIf { it > 0 } ?: "?"}, " +
                  "thermal: ${chosen.thermalLevel}"
            }
            send(
                GlassesDeviceInfo(
                    // The SDK leaves the name empty until the pair has been described —
                    // which, if the two never link, is forever. The identifier is the
                    // SDK's own fallback, and it is right
                    // for a device *picker*; on a status row it is 32 characters of hex,
                    // so the generic label reads better and the identifier stays in the
                    // log line above, which is where it is any use.
                    name = chosen.name.ifBlank { "Meta glasses" },
                    isAvailable = chosen.linkState == LinkState.CONNECTED,
                    compatibility =
                        when (chosen.compatibility) {
                          DeviceCompatibility.COMPATIBLE -> GlassesCompatibility.COMPATIBLE
                          DeviceCompatibility.DEVICE_UPDATE_REQUIRED ->
                              GlassesCompatibility.DEVICE_UPDATE_REQUIRED
                          DeviceCompatibility.SDK_UPDATE_REQUIRED ->
                              GlassesCompatibility.SDK_UPDATE_REQUIRED
                          DeviceCompatibility.UNDEFINED -> GlassesCompatibility.UNKNOWN
                        },
                    // The SDK's `UNKNOWN` is genuinely no answer, not a doff.
                    isWorn =
                        when (chosen.donState) {
                          DonState.DONNED -> true
                          DonState.DOFFED -> false
                          DonState.UNKNOWN -> null
                        },
                    // Zero crosses as unknown, not as a reading — a control channel
                    // still warming up reports it, and an empty pair would not be
                    // connected. Mapping it out is what lets a number here be trusted.
                    batteryLevel = chosen.batteryLevel.takeIf { it > 0 },
                    isCharging =
                        when (chosen.chargingState) {
                          ChargingState.CHARGING -> true
                          ChargingState.NOT_CHARGING -> false
                          ChargingState.UNKNOWN -> null
                        },
                    // The SDK's eight grades, collapsed to the three a wearer can act
                    // on. **Where the lines fall is the judgement.** LIGHT sits with
                    // nominal because warm glasses are the normal state of glasses
                    // doing anything at all, and a panel that cries heat during
                    // ordinary use teaches the wearer to ignore it. MODERATE is where
                    // the SDK starts throttling, which is the first thing worth saying
                    // out loud. CRITICAL and up are the grades the session's own errors
                    // fire from, so the row and the session agree about when the run is
                    // in danger.
                    thermal =
                        when (chosen.thermalLevel) {
                          ThermalLevel.NONE,
                          ThermalLevel.LIGHT -> GlassesThermalLevel.NOMINAL
                          ThermalLevel.MODERATE,
                          ThermalLevel.SEVERE -> GlassesThermalLevel.ELEVATED
                          ThermalLevel.CRITICAL,
                          ThermalLevel.EMERGENCY,
                          ThermalLevel.SHUTDOWN,
                          -> GlassesThermalLevel.CRITICAL
                          ThermalLevel.UNKNOWN -> null
                        },
                    // Read off the device's own description rather than the link, so it
                    // is answered for a pair that is merely known — which is what lets a
                    // screen decide whether to offer a send before a session exists.
                    hasDisplay = chosen.isDisplayCapable(),
                ),
            )
          }
    }
  }

  /**
   * How good a candidate a pair is, lower being better — the **worn** connected pair first, then
   * any connected pair, then one merely vouched for, then anything else.
   *
   * Two pairs can be connected at once — a display pair and another — and the one on the wearer's
   * face is the one a launch is spoken from and a session should open on. Don state is the
   * tie-break that names it; without it the choice falls to list position, which answers a
   * different question. A fuller picker is still its own feature.
   */
  private fun rank(device: Device): Int =
      when {
        device.linkState == LinkState.CONNECTED && device.donState == DonState.DONNED -> 0
        device.linkState == LinkState.CONNECTED -> 1
        device.compatibility == DeviceCompatibility.COMPATIBLE -> 2
        else -> 3
      }

  /**
   * The ranking the session and the voice channel share, so both land on the same pair — the
   * session names it directly, the channel's auto-selector is handed it to rank by.
   */
  internal fun deviceRanking(): Comparator<Device> = Comparator { a, b -> rank(a) - rank(b) }

  /** Which of several paired pairs this app is about. */
  private fun preferred(devices: List<Device>): Device = devices.minBy(::rank)

  /**
   * The same choice, made synchronously against the SDK's current list — what a session is opened
   * on, so the pair the status screen names is the pair the session runs on.
   */
  private fun preferredIdentifier(): DeviceIdentifier? =
      Wearables.devices.value
          .mapNotNull { id -> Wearables.devicesMetadata[id]?.value?.let { id to it } }
          .minByOrNull { rank(it.second) }
          ?.first

  override fun sessionStream(): Flow<GlassesSessionState> = channelFlow {
    // **Named, not auto-chosen.** `AutoDeviceSelector` fills its active device from the
    // device stream asynchronously, so one built at the moment of the tap is empty and the
    // session fails NO_ELIGIBLE_DEVICE — the race an asynchronous selector always leaves
    // open. Naming the pair also closes a subtler gap: the status screen and the session
    // would otherwise be free to disagree about which glasses these are.
    val deviceId = preferredIdentifier()
    if (deviceId == null) {
      BirdLog.error(LogCategory.GLASSES) { "glasses session — no paired device to run it on" }
      throw GlassesError.NotConnected
    }
    synchronized(lock) { activeDeviceId = deviceId }
    // Back to nothing-has-asked-yet, so a screen watching across two runs is not still
    // reading the ending of the one before this.
    speechState.value = GlassesSpeechState.IDLE
    // **Asked before the camera attaches, because the answer is baked into the stream.**
    // Audio is part of the configuration the camera is added with, and a stream asked for
    // audio the wearer never allowed is a stream that may not come up at all — taking the
    // shutter with it. So the microphone is only put on the stream when the grant is known
    // to be there; without it the session runs exactly as it would have, and the listening
    // stays on the phone.
    //
    // **Never on the mock kit.** Its camera plays a file or the phone's own camera and has
    // no audio feed behind it, so a stream asked for audio there is at best silent and at
    // worst a code path the mock never expected to run. The phone's microphone is the honest
    // answer on a mock pair.
    if (mockKit.isEnabled) {
      streamCarriesAudio = false
      BirdLog.info(LogCategory.GLASSES) {
        "glasses session — mock pair; the stream carries no audio"
      }
    } else {
      streamCarriesAudio = access(GlassesPermission.MICROPHONE) == GlassesAccess.GRANTED
      if (!streamCarriesAudio) {
        BirdLog.warning(LogCategory.GLASSES) {
          "glasses session — no microphone grant; the stream carries no audio"
        }
      }
    }
    val created = Wearables.createSession(SpecificDeviceSelector(deviceId))
    val session =
        created.getOrNull()
            ?: run {
              // Named in the log, because the cases are the diagnosis: a session that will not
              // open reads the same on screen whether nothing was eligible, one was already
              // running, or the glasses want a DAT update.
              val error = created.errorOrNull()
              BirdLog.error(LogCategory.GLASSES) { "glasses session — ${error?.description}" }
              throw error.asGlassesError()
            }
    synchronized(lock) { activeSession = session }
    try {
      BirdLog.info(LogCategory.GLASSES) { "glasses session — starting on $deviceId" }
      session.start()

      // The session's own failures, which `start()` never reports: it returns the moment
      // the request is away, and everything the glasses have to say about whether they
      // can host the session arrives afterwards, over the link. Ending the run is left to
      // the state flow — a session that fails is a session that stops, and it will say so
      // — except for the one failure that is somebody's to fix, which this ends with the
      // reason attached.
      launch {
        session.errors.collect { error ->
          BirdLog.error(LogCategory.GLASSES) { "glasses session — ${error.description}" }
          if (error == DeviceSessionError.DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED) {
            close(GlassesError.GlassesUpdateRequired)
          }
        }
      }

      session.state
          .transformWhile { state ->
            emit(state)
            // STOPPED is terminal for the session even though the flow under it is
            // hot — a stopped session never restarts; the app makes a new one.
            state != DeviceSessionState.STOPPED
          }
          .collect { state ->
            // Every transition, because the shape of a stall is which state it
            // stopped in — a session parked on STARTING and one that reached STARTED
            // and never had a camera stream attach are different bugs that look
            // identical from the pill on screen.
            BirdLog.debug(LogCategory.GLASSES) { "glasses session — $state" }
            when (state) {
              DeviceSessionState.STARTING -> send(GlassesSessionState.STARTING)
              // **STARTED means the session exists, not that the glasses are
              // reachable.** The device is still arriving at this point — the camera
              // says so out loud, spending its first beats waiting for it — and a
              // capability started into that window fails with `DEVICE_DISCONNECTED`
              // and does not try again.
              //
              // So only the camera is attached here, because only the camera is
              // willing to wait. The sensors follow it, once its frames have proved
              // the device is really there — see [attachSensorsIfNeeded]. Announcing
              // STARTED waits for the frames, for the same reason.
              DeviceSessionState.STARTED -> attachCameraIfNeeded(session)
              DeviceSessionState.PAUSED -> send(GlassesSessionState.PAUSED)
              DeviceSessionState.STOPPING -> send(GlassesSessionState.STOPPING)
              DeviceSessionState.STOPPED -> send(GlassesSessionState.STOPPED)
              else -> Unit // IDLE — the state a session is born in, before start()
            }
          }
    } finally {
      // Runs for the natural stop and for a cancelled collector alike — the lease ends,
      // the hardware is let go. The crossing goes first, so the shutter hears about it
      // now rather than fifteen seconds from now.
      failPendingPhoto()
      // And so the aim stops waiting on a pair that has gone.
      finishMotionListeners()
      // And so the session's ears go back to the phone at once, rather than falling silent.
      finishAudioListeners()
      streamCarriesAudio = false
      audioArrivalLogged = false
      // And so nothing is still trying to bring a capability back on a session that has
      // ended.
      synchronized(lock) {
        motionRevival?.cancel()
        inputsRevival?.cancel()
        speechRevival?.cancel()
        motionRevival = null
        inputsRevival = null
        speechRevival = null
        inputsCollectors = null
        inputsHasActivated = false
        speechHasStarted = false
      }
      synchronized(lock) { activeCamera }?.stop()
      // The stream goes down with the camera, and the next session starts it again from
      // nothing.
      synchronized(lock) {
        activeCamera = null
        cameraHasStarted = false
      }
      // Removing is the only way to switch inputs off — the capability has no stop of
      // its own, having started itself the moment it attached. Its collector is a child
      // of this scope and ends with it either way.
      session.removeInputs()
      synchronized(lock) { activeInputs = null }
      // Motion does have a stop, and it is the one that puts the sensor down; removing the
      // capability then releases it with the session.
      synchronized(lock) { activeMotion }?.stop()
      session.removeMotion()
      synchronized(lock) { activeMotion = null }
      // The recogniser goes down the same way the sensor does, and for the same reason:
      // `stop()` is what stops it listening, and removing it releases the capability.
      synchronized(lock) { activeSpeech }?.stop()
      session.removeSpeech()
      synchronized(lock) { activeSpeech = null }
      // Never UNAVAILABLE here — that is a fact about the pair, and this is a session
      // ending. A screen still watching should read *it stopped*, not *these glasses
      // cannot do it*.
      speechState.value = GlassesSpeechState.STOPPED
      // The display goes dark with the session that was feeding it.
      synchronized(lock) { activeDisplay }?.stop()
      session.removeDisplay()
      synchronized(lock) {
        activeDisplay = null
        activeDeviceId = null
      }
      session.stop()
      synchronized(lock) { activeSession = null }
    }
  }

  /**
   * The display-only lease — [GlassesSessionRepository.displaySessionStream].
   *
   * The same three SDK moves as [sessionStream] — create on the named pair, start, attach — with
   * everything the display does not need left dark: no camera lit, no stream started, no sensors,
   * no recogniser. What the full lease loses with them is its proof that the device has really
   * arrived, which is why the attach below needs patience of its own.
   *
   * The teardown is the display's alone. The other capabilities were never attached, and asking a
   * session to remove what it never grew is a log line of failures pretending to be a cleanup.
   */
  override fun displaySessionStream(): Flow<GlassesSessionState> = channelFlow {
    val deviceId = preferredIdentifier()
    if (deviceId == null) {
      BirdLog.error(LogCategory.GLASSES) { "display session — no paired device to run it on" }
      throw GlassesError.NotConnected
    }
    synchronized(lock) { activeDeviceId = deviceId }
    val created = Wearables.createSession(SpecificDeviceSelector(deviceId))
    val session =
        created.getOrNull()
            ?: run {
              val error = created.errorOrNull()
              BirdLog.error(LogCategory.GLASSES) { "display session — ${error?.description}" }
              throw error.asGlassesError()
            }
    synchronized(lock) { activeSession = session }
    try {
      BirdLog.info(LogCategory.GLASSES) { "display session — starting on $deviceId" }
      session.start()

      // The session's own failures, which `start()` never reports — the same bargain
      // the full lease strikes: the state flow ends the run, except for the one
      // failure that is somebody's to fix.
      launch {
        session.errors.collect { error ->
          BirdLog.error(LogCategory.GLASSES) { "display session — ${error.description}" }
          if (error == DeviceSessionError.DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED) {
            close(GlassesError.GlassesUpdateRequired)
          }
        }
      }

      session.state
          .transformWhile { state ->
            emit(state)
            // STOPPED is terminal for the session even though the flow under it is
            // hot — a stopped session never restarts; the app makes a new one.
            state != DeviceSessionState.STOPPED
          }
          .collect { state ->
            BirdLog.debug(LogCategory.GLASSES) { "display session — $state" }
            when (state) {
              DeviceSessionState.STARTING -> send(GlassesSessionState.STARTING)
              // STARTED is not announced here — on this lease the word means
              // *the panel can be drawn on*, and only the attach knows when
              // that turned true. See [attachDisplayWithPatience].
              DeviceSessionState.STARTED -> attachDisplayWithPatience(session)
              DeviceSessionState.PAUSED -> send(GlassesSessionState.PAUSED)
              DeviceSessionState.STOPPING -> send(GlassesSessionState.STOPPING)
              DeviceSessionState.STOPPED -> send(GlassesSessionState.STOPPED)
              else -> Unit // IDLE — the state a session is born in, before start()
            }
          }
    } finally {
      // Runs for the natural stop and for a cancelled collector alike — the lease
      // ends, the hardware is let go.
      synchronized(lock) {
        displayPatience?.cancel()
        displayPatience = null
      }
      synchronized(lock) { activeDisplay }?.stop()
      session.removeDisplay()
      synchronized(lock) {
        activeDisplay = null
        activeDeviceId = null
      }
      session.stop()
      synchronized(lock) { activeSession = null }
    }
  }

  /**
   * Ends whatever lease is open — the full session or the display's — and waits for it to be gone.
   *
   * **For the mock kit's flip, and nothing else.** Swapping the SDK's providers under an open
   * session leaves that session pointing at a device that no longer exists, and nothing in the swap
   * tells it so; ending the lease first is what keeps the flip clean. Asking the session to stop is
   * enough — its own state flow delivers STOPPED, the collector finishes, and the ordinary teardown
   * runs — but that lands a beat later, which is why this waits for the handle to clear rather than
   * returning on the ask.
   *
   * A no-op with nothing open, and bounded either way: a session that will not stop inside a second
   * is a session the flip goes ahead without.
   */
  suspend fun endActiveSessions() {
    synchronized(lock) { activeSession }?.stop()
    repeat(EndActiveSessionsPolls) {
      if (synchronized(lock) { activeSession == null }) return
      delay(EndActiveSessionsPollMillis)
    }
    BirdLog.error(LogCategory.GLASSES) {
      "glasses session — did not end in time for the mock kit's flip"
    }
  }

  /**
   * Attaches the camera capability once per session — a pause and resume must not stack a second
   * one on the first — and lets the stream's own state announce when the glasses are usable.
   *
   * **The video stream is the ignition, and then it stays lit.** A [Camera] does not power itself:
   * nothing brings the sensor up except the stream asking for it — and the capture rides the
   * running stream, a still asked of it crossing the stream's own channel, so putting the frames
   * down would take the shutter with them. The stream therefore runs for the life of the session.
   *
   * It is configured as small as the SDK sells, because nothing ever draws these frames — the
   * wearer's own eyes are the viewfinder — and every one of them is bandwidth the next photograph
   * would rather have. The audio is asked for at 16 kHz mono, the narrowest the stream offers and
   * exactly the one format the app records in.
   */
  private fun ProducerScope<GlassesSessionState>.attachCameraIfNeeded(session: DeviceSession) {
    if (synchronized(lock) { activeCamera != null }) return

    val carriesAudio = streamCarriesAudio
    session
        .addCamera(
            StreamConfiguration(
                audioCodec =
                    if (carriesAudio) AudioCodec.PCM(AudioSampleRate.RATE_16000, 1) else null,
                videoQuality = VideoQuality.LOW,
                frameRate = 7,
                // **Compressed, not raw, and the reason is the pocket.** The SDK pauses a raw
                // stream the moment the app leaves the foreground and keeps a compressed one
                // flowing. This stream is the glasses' microphone, so a raw stream would be a
                // session that goes deaf when the phone is locked. Nothing here ever decodes a
                // frame, so the codec costs the app nothing either way.
                compressVideo = true,
            ),
        )
        .onSuccess { camera ->
          synchronized(lock) { activeCamera = camera }
          if (carriesAudio) {
            // Collected exactly once, and handed on without waiting — see [audioChunks].
            launch { camera.stream.audioStream.collect(::receive) }
          }
          launch {
            // The stream is the session's pulse: its `start()` is what activates the
            // camera, and its STREAMING is what makes every promise on screen true at
            // once. A stop here is either ours or the glasses going away, and the
            // camera's own state says the second one.
            camera.stream.state.collect { streamState ->
              BirdLog.debug(LogCategory.GLASSES) { "camera stream — $streamState" }
              // A frame means the device is provably *there* — the thing the other
              // capabilities needed somebody to establish for them — and the first
              // moment a press can actually produce a photograph, which is the only
              // claim the pill on screen is allowed to make.
              if (streamState == StreamState.STREAMING) {
                attachSensorsIfNeeded(session)
                send(GlassesSessionState.STARTED)
              }
            }
          }
          launch {
            camera.state.collect { cameraState ->
              // The hardware coming up, which is a different beat from the session's own
              // and arrives after it. A session that says started and then sits here is
              // the classic "it claims the glasses but the shutter does nothing" report.
              BirdLog.debug(LogCategory.GLASSES) { "camera — $cameraState" }
              when (cameraState) {
                CameraState.STARTED -> synchronized(lock) { cameraHasStarted = true }
                // **Only a camera that was up and went down means the glasses are
                // gone** — see [cameraHasStarted]. The session may well still be up;
                // the *camera* is not, and this app has nothing to do with a pair of
                // glasses it cannot photograph through.
                CameraState.STOPPED,
                CameraState.STOPPING -> {
                  if (synchronized(lock) { cameraHasStarted }) {
                    // Including a photograph it was in the middle of taking.
                    failPendingPhoto()
                    send(GlassesSessionState.STOPPED)
                  }
                }
                else -> Unit // STARTING — still the linking beat
              }
            }
          }
          camera.stream.start().onFailure { error, _ ->
            BirdLog.error(LogCategory.GLASSES) {
              "the camera stream would not start: ${error.description}"
            }
          }
        }
        .onFailure { error, _ ->
          // No capability, no photographs — which is not a session worth claiming.
          BirdLog.error(LogCategory.GLASSES) {
            "glasses session — the camera capability would not attach: ${error.description}"
          }
          close(GlassesError.NotConnected)
        }
  }

  /**
   * The glasses' microphone, for
   * [com.meta.pixelandtexel.birdspotter.data.audio.GlassesMicrophoneSource] — one collector per
   * caller, fed by the single collection in [attachCameraIfNeeded].
   *
   * **Fails at once when there is nothing to hear.** No session, or a session whose stream was
   * opened without audio, is [AudioCaptureError.Unavailable] straight away, which is the answer the
   * failover needs to keep the phone. Unlike the readings, a collection here is only meaningful
   * against a session that is already up — the failover asks for the glasses only once one is.
   *
   * A session that ends under a collector fails it with [AudioCaptureError.Interrupted], so the
   * failover hears a lost microphone rather than watching a silent one.
   */
  fun audioChunksFromActiveSession(): Flow<AudioChunk> = flow {
    if (synchronized(lock) { activeCamera == null } || !streamCarriesAudio) {
      throw AudioCaptureError.Unavailable
    }
    var ended = false
    audioChunks
        .transformWhile { chunk ->
          chunk?.let { emit(it) }
          // `null` is the end of the audio, not a buffer — see [finishAudioListeners].
          ended = chunk == null
          !ended
        }
        .collect { emit(it) }
    if (ended) throw AudioCaptureError.Interrupted
  }

  /**
   * One buffer off the stream, as the app's one format, handed to every collector.
   *
   * The stream decodes to 16-bit PCM in the platform's own byte order, mono at the rate it was
   * asked for, so this is a rescale to −1..1 and nothing more. The buffer is read through a
   * duplicate, so its position — which the SDK may still care about — is left where it was.
   */
  private fun receive(frame: AudioFrame) {
    // Once per session, the buffer as it actually arrived. The layout above is read off the
    // SDK's decoder rather than documented, and a first buffer that turns out to be stereo, or
    // handed over already read to its end, is a strip of noise or a stream of nothing — this
    // is the line that tells those apart from a quiet room.
    if (!audioArrivalLogged) {
      audioArrivalLogged = true
      val buffer = frame.buffer
      BirdLog.info(LogCategory.GLASSES) {
        "camera stream — audio arriving: ${buffer.remaining()} bytes " +
            "(position ${buffer.position()}, limit ${buffer.limit()}, " +
            "capacity ${buffer.capacity()}, ${buffer.order()})"
      }
    }
    val pcm = frame.buffer.duplicate().order(ByteOrder.nativeOrder()).asShortBuffer()
    if (!pcm.hasRemaining()) return
    val samples = FloatArray(pcm.remaining()) { pcm.get() / Short.MAX_VALUE.toFloat() }
    audioChunks.tryEmit(AudioChunk(samples, source = CaptureSourceKind.GLASSES))
  }

  /** Ends every microphone collection — the session is gone, or its audio is. */
  private fun finishAudioListeners() {
    if (audioChunks.subscriptionCount.value == 0) return
    BirdLog.info(LogCategory.GLASSES) { "microphone — no audio to give; ending subscriptions" }
    audioChunks.tryEmit(null)
  }

  /**
   * Attaches the capabilities that will not wait for the glasses to arrive.
   *
   * **The camera is the only one with patience.** Its stream waits for the device and says so;
   * [Motion] and [Inputs] have no equivalent, so asked for in that same window they fail with
   * `DEVICE_DISCONNECTED` — once, quietly, with no retry of their own. That is a sensor that goes
   * starting → stopping → stopped in the space of three log lines and never speaks again, taking
   * the wearer's own bearing with it.
   *
   * So they are attached off the back of the frames instead: by the time a frame arrives, the
   * device is not merely sessioned but *there*. The display rides the same beat for the same
   * reason. All of them are idempotent, so a repeated STREAMING costs nothing.
   */
  private fun CoroutineScope.attachSensorsIfNeeded(session: DeviceSession) {
    attachInputsIfNeeded(session)
    attachMotionIfNeeded(session)
    attachSpeechIfNeeded(session)
    attachDisplayIfNeeded(session)
  }

  /**
   * The presses, for [DatGlassesInputRepository] — fed by the single collector in
   * [attachInputsIfNeeded].
   *
   * Collecting before a session exists is deliberately allowed: the flow outlives any one session,
   * so a collector that starts while the glasses are still linking is the same collector that hears
   * the first press once they are up. Nothing here starts the capability — the session does that
   * when it reaches STARTED.
   */
  fun inputEventsFromActiveSession(): Flow<GlassesInputEvent> = inputEvents.asSharedFlow()

  /**
   * Attaches the inputs capability once per session, and listens to everything it will send.
   *
   * **Subscribing *is* consuming, and the band is left out for that reason.** `sources` is not a
   * filter this app applies to a stream it would have received anyway — it is a request sent to the
   * glasses, which then route those surfaces here instead of to whatever was handling them. Asked
   * for all five, this took the Neural Band's swipes, and the swipe the wearer makes on the band is
   * how the card on the display is scrolled. A card that draws three photographs and a description
   * below the fold, on a surface whose renderer scrolls it natively, cannot also be a card whose
   * scroll gesture this app is quietly eating.
   *
   * So the band is not asked for, and neither is its drag. What is left is the temple and the
   * buttons: the surfaces this app has something to *do* with, and none of which scroll anything.
   *
   * **`consumeBack` is unaffected by that narrowing** — it rides the attach request as its own
   * field rather than being implied by a source, so back stays this app's while the band stays the
   * wearer's.
   *
   * **A narrow subscription hides one failure, and it has bitten here before.** Asked for
   * `CAPTURE_BUTTON` alone, the capability once reported ACTIVE and then delivered nothing at all
   * through a whole session of pressing the temple — and a subscription that narrow cannot tell
   * *the button sends nothing* apart from *the button sends something this app did not ask for*,
   * which want opposite fixes. Keeping the temple and both buttons is what keeps that distinction
   * visible; if the shutter ever goes quiet again, widening the set is the first thing to try.
   *
   * **`consumeBack` is on, and back belongs to this app.** Left off, back keeps its system meaning,
   * and that meaning is *leave the running experience*: a wearer who swipes back mid-run takes the
   * session down with them — camera, sensors, display and all — and the cause is invisible from the
   * phone, which sees only a session that ended for no reason anyone can name. Consuming the
   * gesture is the only way to stop that.
   *
   * **The cost of consuming it is that back now owes the wearer an answer,** and the reason that
   * debt is payable is scope: this capability lives exactly as long as a glasses-backed run does,
   * so the one screen that can be on the glasses is also the screen that answers back. There is no
   * stretch of the app where the gesture is quietly dead. What it answers with is decided above the
   * data layer — see [receive].
   *
   * A capability that will not attach is logged and lived with. The shutter on screen still works,
   * and a session with no button is worth a great deal more than no session.
   */
  private fun CoroutineScope.attachInputsIfNeeded(session: DeviceSession) {
    if (synchronized(lock) { activeInputs != null }) return

    val scope = this
    session
        .addInputs(
            InputsConfiguration(
                sources =
                    setOf(
                        InputSource.CAPTOUCH,
                        InputSource.CAPTURE_BUTTON,
                        InputSource.ACTION_BUTTON,
                    ),
                consumeBack = true,
            ),
        )
        .onSuccess { inputs ->
          synchronized(lock) {
            activeInputs = inputs
            inputsHasActivated = false
          }
          // Lazy, and started below once the job is held: the state flow replays the moment it
          // is collected, so a collector running before it has been recorded is one a re-attach
          // would leave behind, watching a capability nothing else remembers.
          val collectors =
              launch(start = CoroutineStart.LAZY) {
                // **The only two things that say whether the button is listening.** The capability
                // activates itself on attach and reports nothing back through this call, so a pair
                // whose presses never arrive is otherwise indistinguishable from a wearer who
                // never pressed — which is exactly the silence that hid the sensor coming up dead.
                launch {
                  inputs.state.collect { state ->
                    BirdLog.debug(LogCategory.GLASSES) { "inputs — $state" }
                    when (state) {
                      InputsState.ACTIVE -> synchronized(lock) { inputsHasActivated = true }
                      // **Only a button that was listening and then went quiet is one to
                      // attach again.** INACTIVE is also the state the capability is born
                      // in, replayed to this collector the moment it starts, and acting on
                      // that one would tear down a capability still on its way up.
                      InputsState.INACTIVE ->
                          if (synchronized(lock) { inputsHasActivated }) {
                            scope.reviveInputs(session)
                          }
                      else -> Unit // ACTIVATING, DEACTIVATING — the linking beats
                    }
                  }
                }
                launch {
                  // Nullable, and it starts that way: this capability holds its *last* error
                  // rather than announcing each one, so the opening `null` means nothing has
                  // gone wrong yet and is not worth a line.
                  inputs.errors.collect { error ->
                    error?.let {
                      BirdLog.error(LogCategory.GLASSES) { "inputs — ${it.description}" }
                    }
                  }
                }
                launch {
                  inputs.events.collect(::receive)
                  // **A stream that ends is not a button nobody pressed.** Both look like
                  // silence from here, and only one of them is worth chasing.
                  BirdLog.info(LogCategory.GLASSES) { "inputs — the event stream ended" }
                }
              }
          synchronized(lock) { inputsCollectors = collectors }
          collectors.start()
        }
        .onFailure { error, _ ->
          BirdLog.error(LogCategory.GLASSES) {
            "glasses session — inputs would not attach: ${error.description}"
          }
        }
  }

  /**
   * Attaches the button again after it has gone quiet under a running session.
   *
   * **A photograph is what takes it away.** A still crossing the link is heavy enough to put down
   * the other capabilities the session is holding, and the button comes back from that the way it
   * goes down: it does not. Left alone, the first photograph of a run is the last press of it — the
   * temple stops answering and the only thing that still fires the shutter is the control on the
   * phone, which is precisely the half of the demo the glasses are there for.
   *
   * **Unlike the sensor, this capability cannot simply be started again** — it has no start of its
   * own, and one that has ended holds its place until it is removed, so the dead one goes before a
   * live one can take the slot. That is destructive enough to be worth being sure about, which is
   * what [inputsHasActivated] is for.
   */
  private fun CoroutineScope.reviveInputs(session: DeviceSession) {
    val scope = this
    synchronized(lock) {
      if (inputsRevival?.isActive == true || activeInputs == null) return
      // Launched on the scope the capability was attached on, not on this one: the
      // collectors it will start have to outlive the job that starts them.
      inputsRevival = launch { scope.bringInputsBack(session) }
    }
  }

  private suspend fun CoroutineScope.bringInputsBack(session: DeviceSession) {
    waitOutCrossing()
    val collectors =
        synchronized(lock) {
          if (activeInputs == null) return
          inputsCollectors.also {
            activeInputs = null
            inputsCollectors = null
            inputsHasActivated = false
          }
        }
    BirdLog.info(LogCategory.GLASSES) { "inputs — the button went quiet; attaching it again" }
    // The old collectors go down with the capability they were watching. Left running, the
    // dead one's own INACTIVE arrives against the live one and takes it down again.
    collectors?.cancel()
    session.removeInputs()
    attachInputsIfNeeded(session)
  }

  /**
   * The readings, for [DatGlassesMotionRepository] — one collector per caller, fed by the single
   * collection in [attachMotionIfNeeded].
   *
   * Collecting before a session exists is allowed for the same reason a press listener may: the
   * registry outlives any one session, so a compass that collects while the glasses are still
   * linking is the one that reads the first sample once they are up.
   */
  fun motionSamplesFromActiveSession(): Flow<GlassesMotionSample> =
      motionSamples.transformWhile { sample ->
        sample?.let { emit(it) }
        // `null` is the end of the readings, not a reading — see [finishMotionListeners].
        sample != null
      }

  /**
   * Attaches the motion capability once per session and starts it.
   *
   * **The slowest rate the SDK offers, and it is still faster than the screen.** Both readings this
   * feeds are words — one of eight compass points, one of five strata — and neither turns over for
   * a degree of wobble. 5 Hz is a reading every 200 ms on a link that is also carrying a video
   * stream and owes it the bandwidth; the rates above it would buy a smoother number that nothing
   * on screen is drawing.
   *
   * **The capability must be started, unlike inputs.** `addMotion` attaches the sensor and leaves
   * it stopped; without the `start()` the samples flow stays silent forever, which looks exactly
   * like a pair with no IMU.
   *
   * A capability that will not attach is logged and lived with. The phone's own compass and
   * attitude are still there behind the failover, and a session with a bearing from the wrong
   * device is worth more than a session with no bearing.
   */
  private fun CoroutineScope.attachMotionIfNeeded(session: DeviceSession) {
    if (synchronized(lock) { activeMotion != null }) return

    val scope = this
    session
        .addMotion(
            MotionConfiguration(samplingRate = MotionSamplingRate.HZ_5),
        )
        .onSuccess { motion ->
          synchronized(lock) {
            activeMotion = motion
            motionSourcesHeard.clear()
            lastMotionLogMillis = 0L
          }
          launch { motion.samples.collect(::receive) }
          // **The only two things that say whether the sensor came up.** `start()` returns
          // nothing and is silently ignored unless the capability is stopped, so a run where
          // the chips never move is otherwise indistinguishable from a run where they were
          // never asked to. STARTED here and no sample below is a different bug from neither.
          launch {
            motion.state.collect { state ->
              BirdLog.debug(LogCategory.GLASSES) { "motion — $state" }
              // A sensor that stops without being asked to is one something else on the
              // link took away, and it is brought back rather than mourned — see
              // [reviveMotion].
              if (state == MotionState.STOPPED) scope.reviveMotion()
            }
          }
          launch {
            motion.errors.collect { error ->
              if (error != null) {
                BirdLog.error(LogCategory.GLASSES) { "motion — ${error.description}" }
              }
            }
          }
          motion.start()
        }
        .onFailure { error, _ ->
          BirdLog.error(LogCategory.GLASSES) {
            "glasses session — motion would not attach: ${error.description}"
          }
          // Nothing is coming, so nobody should be left waiting for it.
          finishMotionListeners()
        }
  }

  /**
   * Starts the sensor again after something else on the link has put it down.
   *
   * **A photograph is what puts it down.** A still crossing the link is heavy enough to stop the
   * capabilities the session is holding alongside it: the sensor goes STOPPING → STOPPED in the
   * middle of a capture and stays there, because nothing in the SDK brings it back. Left alone, the
   * first photograph of a run is the last reading of it — the compass and the stratum freeze on
   * whatever the wearer happened to be looking at while every log line still says the glasses have
   * the aim.
   *
   * So a stop this app did not ask for is read as an interruption rather than an ending. `start()`
   * is only honoured on a capability that is already STOPPED, which is what makes this safe to
   * attempt more than once: a sensor that came back on its own ignores it.
   *
   * **The collectors are held across the gap on purpose.** Ending them hands the aim to the phone
   * (see [finishMotionListeners]), and a reading that crosses to the phone and back for every
   * photograph is worse than one that holds still for a second — the failover exists for an
   * instrument that is gone, not for one that is busy. They are only ended once the sensor has
   * refused to come back at all, which is the point at which the glasses really do have nothing to
   * give.
   */
  private fun CoroutineScope.reviveMotion() {
    synchronized(lock) {
      if (motionRevival?.isActive == true || activeMotion == null) return
      motionRevival = launch { bringMotionBack() }
    }
  }

  private suspend fun bringMotionBack() {
    repeat(SensorRevivalAttempts) { attempt ->
      waitOutCrossing()
      // No session left to have a sensor on.
      val motion = synchronized(lock) { activeMotion } ?: return
      // Up again already: either it never really went down, or a stop arrived late about a
      // capability that has since been started.
      if (motion.state.value != MotionState.STOPPED) return
      BirdLog.info(LogCategory.GLASSES) {
        "motion — stopped without being asked to; starting it again " +
            "(${attempt + 1} of $SensorRevivalAttempts)"
      }
      motion.start()
      // `start()` answers on the state flow and nowhere else, so the only way to know
      // whether it took is to look again a beat later.
      delay(SensorRevivalDelayMillis)
      if (motion.state.value != MotionState.STOPPED) {
        BirdLog.info(LogCategory.GLASSES) { "motion — back up" }
        return
      }
    }
    BirdLog.error(LogCategory.GLASSES) {
      "motion — would not start again; the aim goes back to the phone"
    }
    finishMotionListeners()
  }

  /**
   * What the wearer said, for [DatGlassesSpeechRepository] — one collector per caller, fed by the
   * single collection in [attachSpeechIfNeeded].
   *
   * Collecting before a session exists is allowed for the same reason a press listener may: the
   * flow outlives any one session, so a screen that collects while the glasses are still linking is
   * the one that hears the first thing said once they are up.
   */
  fun transcriptionsFromActiveSession(): Flow<Transcription> = transcriptions.asSharedFlow()

  /** Where the recogniser is, for [DatGlassesSpeechRepository]. */
  fun speechStateFromActiveSession(): Flow<GlassesSpeechState> = speechState.asStateFlow()

  /**
   * Attaches the speech capability once per session and starts it listening.
   *
   * **Nothing but text crosses the link.** The recognition runs on the glasses' own engine, so this
   * costs the session no audio bandwidth — the words arrive already written, where the microphone's
   * audio has to cross the camera stream as samples. That is the reason the app transcribes here
   * rather than on the phone.
   *
   * **The capability must be started, like the sensor and unlike the button.** `addSpeech` attaches
   * a recogniser and leaves it stopped; without the `start()` the transcript flow stays silent
   * forever, which looks exactly like a wearer who never said anything.
   *
   * **A pair that cannot do this says so once, and the reading is terminal.** `UNAVAILABLE` is
   * hardware, not a link that will come back, and a screen told it can stop offering something
   * these glasses will never do. Every other failure is logged and lived with: a session that
   * cannot hear the wearer is still a session that photographs and identifies.
   */
  private fun CoroutineScope.attachSpeechIfNeeded(session: DeviceSession) {
    if (synchronized(lock) { activeSpeech != null }) return

    val scope = this
    speechState.value = GlassesSpeechState.STARTING
    session
        .addSpeech()
        .onSuccess { speech ->
          synchronized(lock) {
            activeSpeech = speech
            speechHasStarted = false
          }
          // **Deduplicated by the SDK before this line, and nothing here can undo it.** The
          // capability publishes results on a `StateFlow`, which drops a value equal to the one
          // before it — so a partial that repeats a stable prefix, or the same short word said
          // twice, arrives once. Reported rather than worked around: the loss happens above this
          // app, the matching downstream runs on finals, and a workaround that invented an
          // emission would be inventing something the wearer did not say.
          launch { speech.transcriptions.collect(::receive) }
          launch {
            speech.state.collect { state ->
              BirdLog.debug(LogCategory.GLASSES) { "speech — $state" }
              when (state) {
                SpeechState.STARTED -> {
                  synchronized(lock) { speechHasStarted = true }
                  speechState.value = GlassesSpeechState.LISTENING
                }
                // **Only a recogniser that was listening and then went quiet is one to
                // start again.** STOPPED is also the state the capability is born in,
                // replayed to this collector the moment it starts, and acting on that one
                // would chase a recogniser still on its way up.
                SpeechState.STOPPED ->
                    if (synchronized(lock) { speechHasStarted }) {
                      speechState.value = GlassesSpeechState.STOPPED
                      scope.reviveSpeech()
                    }
                else -> Unit // STARTING, STOPPING — the linking beats
              }
            }
          }
          launch {
            // Nullable, and it starts that way: this capability holds its *last* error rather
            // than announcing each one, so the opening `null` means nothing has gone wrong yet
            // and is not worth a line.
            speech.errors.collect { error ->
              if (error == null) return@collect
              BirdLog.error(LogCategory.GLASSES) { "speech — ${error.description}" }
              // The one error that is a fact about the glasses rather than about this
              // moment. Everything else is a link, a state, or a start that can be tried
              // again, and none of those is worth telling a screen to give up over.
              if (error == SpeechError.UNAVAILABLE) {
                speechState.value = GlassesSpeechState.UNAVAILABLE
              }
            }
          }
          speech.start().onFailure { error, _ ->
            BirdLog.error(LogCategory.GLASSES) {
              "speech — would not start: ${error.description}"
            }
          }
        }
        .onFailure { error, _ ->
          BirdLog.error(LogCategory.GLASSES) {
            "glasses session — speech would not attach: ${error.description}"
          }
          speechState.value = GlassesSpeechState.UNAVAILABLE
        }
  }

  /**
   * Starts the recogniser again after something else on the link has put it down.
   *
   * **A photograph is what puts it down**, the same crossing that stops the sensor — see
   * [reviveMotion], which this mirrors beat for beat. Left alone, the first photograph of a run
   * would be the last thing the wearer could say to the app.
   *
   * `start()` is refused on a capability that is not STOPPED, and the refusal is a returned error
   * rather than a thrown one, which is what makes this safe to attempt more than once.
   */
  private fun CoroutineScope.reviveSpeech() {
    synchronized(lock) {
      if (speechRevival?.isActive == true || activeSpeech == null) return
      speechRevival = launch { bringSpeechBack() }
    }
  }

  private suspend fun bringSpeechBack() {
    repeat(SensorRevivalAttempts) { attempt ->
      waitOutCrossing()
      // No session left to have a recogniser on.
      val speech = synchronized(lock) { activeSpeech } ?: return
      // Up again already: either it never really went down, or a stop arrived late about a
      // capability that has since been started.
      if (speech.state.value != SpeechState.STOPPED) return
      BirdLog.info(LogCategory.GLASSES) {
        "speech — stopped without being asked to; starting it again " +
            "(${attempt + 1} of $SensorRevivalAttempts)"
      }
      speech.start()
      // `start()` answers on the state flow as well, and that is the answer that says
      // whether it took — the returned one only says the ask was accepted.
      delay(SensorRevivalDelayMillis)
      if (speech.state.value != SpeechState.STOPPED) {
        BirdLog.info(LogCategory.GLASSES) { "speech — back up" }
        return
      }
    }
    BirdLog.error(LogCategory.GLASSES) {
      "speech — would not start again; nothing more will be heard this session"
    }
    speechState.value = GlassesSpeechState.STOPPED
  }

  /**
   * One transcript off the capability, in the app's own terms.
   *
   * The opening `null` is the flow's starting value rather than something the wearer said, and
   * silence at the end of a session arrives the same way — neither is a transcript.
   *
   * **A confidence the recogniser will not give is `null`, not a number.** The SDK spends `-1.0` on
   * *no answer*, which is a value that survives every comparison a caller might make of it and
   * reads as total disbelief in what was heard.
   */
  private fun receive(result: TranscriptionResult?) {
    val heard = result ?: return
    BirdLog.debug(LogCategory.GLASSES) {
      "speech — \"${heard.text}\"" +
          (if (heard.isFinal) " (final" else " (partial") +
          (if (heard.confidence < 0f) ")" else ", ${heard.confidence.toTwoDecimals()})")
    }
    if (
        !transcriptions.tryEmit(
            Transcription(
                text = heard.text,
                isFinal = heard.isFinal,
                confidence = heard.confidence.takeIf { it >= 0f },
            ),
        )
    ) {
      BirdLog.warning(LogCategory.GLASSES) {
        "speech — a transcript was dropped; nothing was reading fast enough"
      }
    }
  }

  /**
   * Attaches the display capability once per session — and only to a pair that has one.
   *
   * **Most pairs do not, and that is a fact about the hardware rather than a failure.** The gate is
   * the device's own answer, read at attach time; a pair without a display simply never grows the
   * capability, and every send through [sendThroughActiveDisplay] answers `NotConnected` — the same
   * quiet a caller gets from a session that has ended.
   *
   * A capability that will not attach is logged and lived with, the sensors' bargain again: the
   * identification still lands on the timeline, and a session that cannot decorate the wearer's
   * view is worth a great deal more than no session.
   */
  private fun CoroutineScope.attachDisplayIfNeeded(session: DeviceSession) {
    if (synchronized(lock) { activeDisplay != null }) return

    val deviceId = synchronized(lock) { activeDeviceId }
    val device = deviceId?.let { Wearables.devicesMetadata[it]?.value }
    if (device == null || !device.isDisplayCapable()) {
      BirdLog.info(LogCategory.GLASSES) { "glasses session — this pair has no display" }
      return
    }

    session
        .addDisplay()
        .onSuccess { display ->
          synchronized(lock) { activeDisplay = display }
          // **The only thing that says whether the panel came up.** Attaching reports
          // nothing back, so a display that never draws is otherwise indistinguishable
          // from one never asked to.
          launch {
            display.state.collect { state ->
              BirdLog.debug(LogCategory.GLASSES) { "display — $state" }
            }
          }
        }
        .onFailure { error, _ ->
          BirdLog.error(LogCategory.GLASSES) {
            "glasses session — display would not attach: ${error.description}"
          }
        }
  }

  /**
   * Attaches the display capability with retries, and announces STARTED when it lands — for the
   * lease with no camera frames to wait behind.
   *
   * [attachDisplayIfNeeded] gets to ask exactly once because the frames have already proved the
   * device is there. On the display-only lease nothing has, and a capability asked for while the
   * device is still arriving fails once and quietly — so this one asks again on the sensors' own
   * revival beat, until the capability answers or the run ends and takes the job with it.
   *
   * A pair with no display announces STARTED on the spot: there is nothing to wait for, the
   * settings screen's own reading says why a send will land nowhere, and holding the announcement
   * would dress a hardware fact up as a connection problem.
   */
  private fun ProducerScope<GlassesSessionState>.attachDisplayWithPatience(session: DeviceSession) {
    // A pause and resume lands here again with the capability already up — only the
    // announcement is owed.
    if (synchronized(lock) { activeDisplay != null }) {
      launch { send(GlassesSessionState.STARTED) }
      return
    }
    if (synchronized(lock) { displayPatience?.isActive == true }) return

    val patience = launch {
      val deviceId = synchronized(lock) { activeDeviceId }
      val device = deviceId?.let { Wearables.devicesMetadata[it]?.value }
      if (device == null || !device.isDisplayCapable()) {
        BirdLog.info(LogCategory.GLASSES) { "display session — this pair has no display" }
        send(GlassesSessionState.STARTED)
        return@launch
      }
      while (true) {
        var attached: Display? = null
        session
            .addDisplay()
            .onSuccess { attached = it }
            .onFailure { error, _ ->
              BirdLog.debug(LogCategory.GLASSES) {
                "display session — the display would not attach, asking again: " + error.description
              }
            }
        val display = attached
        if (display != null) {
          synchronized(lock) { activeDisplay = display }
          // **The only thing that says whether the panel came up.** Attaching
          // reports nothing back, so a display that never draws is otherwise
          // indistinguishable from one never asked to.
          launch {
            display.state.collect { state ->
              BirdLog.debug(LogCategory.GLASSES) { "display — $state" }
            }
          }
          send(GlassesSessionState.STARTED)
          return@launch
        }
        delay(SensorRevivalDelayMillis)
      }
    }
    synchronized(lock) { displayPatience = patience }
  }

  /**
   * One view onto the running display, for [DatGlassesDisplayRepository] — the whole screen at
   * once, because that is the only unit the display takes.
   *
   * The send waits out the capability's own warm-up rather than racing it: content asked of a
   * display that has not reached STARTED is refused, and a gallery is worth the beat it takes the
   * panel to come up. A display that never does is answered the way every dead capability here is —
   * `NotConnected`, with the diagnosis on the log.
   */
  suspend fun sendThroughActiveDisplay(content: ContentScope.() -> Unit) {
    val display =
        synchronized(lock) { activeDisplay }
            ?: run {
              // Which leg failed matters here: no capability on the session means the attach
              // never happened — the answer is further up the log, at session start.
              BirdLog.debug(LogCategory.GLASSES) {
                "display — asked to draw, but no display capability is attached to this session"
              }
              throw GlassesError.NotConnected
            }
    val started =
        withTimeoutOrNull(DisplayStartTimeoutMillis) {
          display.state.first { it == DisplayState.STARTED }
        }
    if (started == null) {
      BirdLog.warning(LogCategory.GLASSES) {
        "display — asked to draw, but the panel would not start"
      }
      throw GlassesError.NotConnected
    }
    // **Off the caller's thread, because the send is not a request — it is the transfer.**
    // Building the tree serialises three 600 px photographs and handing it over pushes them
    // across the link, and the caller is a view model on the main thread: run inline, a card
    // going up is the whole app stopping until it lands.
    withContext(Dispatchers.IO) {
      display.sendContent(content).onFailure { error, _ ->
        BirdLog.error(LogCategory.GLASSES) { "display — send failed: ${error.description}" }
        throw GlassesError.NotConnected
      }
    }
  }

  /**
   * Blanks the running display, if there is one. Quiet on purpose: the callers are teardowns, and a
   * display that is already gone is a display that is already blank.
   */
  suspend fun clearActiveDisplay() {
    val display = synchronized(lock) { activeDisplay } ?: return
    // The stop calls this from the main thread, on the frame that opens the review — see
    // [sendThroughActiveDisplay] for why nothing here may run there.
    withContext(Dispatchers.IO) { display.clearDisplay() }
  }

  /**
   * Waits until no photograph is crossing, and at least one beat besides.
   *
   * **A capability asked for anything mid-transfer is asked in the one window it cannot answer** —
   * the channel that would carry the request is the channel carrying the picture. The beat on the
   * end is for the crossing's own teardown, which lands a moment after the image does and would
   * otherwise put back down whatever this had just brought up.
   */
  private suspend fun waitOutCrossing() {
    do {
      delay(SensorRevivalDelayMillis)
    } while (synchronized(lock) { pendingPhoto != null })
  }

  /**
   * One sample off the capability, in the app's own terms — or dropped, when it is not the glasses
   * talking.
   *
   * **The source filter is the whole reason this is not a `map`.** One motion feed can carry the
   * glasses *and* a Neural Band, which are two rigid bodies moving independently; a consumer that
   * averaged them would produce a bearing describing neither, and it would do it silently.
   * `UNKNOWN` is dropped with them: a sample that will not say what it is attached to cannot be
   * trusted to be attached to a head.
   *
   * A sample with no accelerometer is dropped too. Both readings this app takes — elevation and a
   * tilt-compensated bearing — start from where down is, so a sample that cannot say has nothing to
   * give either of them.
   */
  private fun receive(sample: MotionSample) {
    describeOnce(sample)
    describeEvery(sample)
    if (sample.source != MotionSource.GLASSES) return
    val acceleration = sample.accelerometer ?: return
    motionSamples.tryEmit(
        GlassesMotionSample(
            acceleration =
                Vector3(
                    acceleration.x.toDouble(),
                    acceleration.y.toDouble(),
                    acceleration.z.toDouble(),
                ),
            magneticField =
                sample.magnetometer?.let {
                  Vector3(it.x.toDouble(), it.y.toDouble(), it.z.toDouble())
                },
        ),
    )
  }

  /**
   * Says out loud, once per source per run, what the motion feed is actually delivering.
   *
   * **Three of this path's ways of failing are silent by construction, and all three look identical
   * on screen.** A sample from anything but the glasses is dropped above; an accelerometer the
   * device omits arrives as nothing, and a sample with no down is dropped above too; a pair with no
   * magnetometer ends the bearing and hands the compass back to the phone. In every one of them the
   * chips simply keep the last reading they had — which is also exactly what a wearer sees when the
   * sensor never started in the first place. One line per source is what tells them apart.
   *
   * **`UNKNOWN` is the one to watch for**, because it is the SDK's *default* when a device omits
   * the field rather than a claim that something unidentified is moving. A pair whose firmware
   * leaves it unset has every sample dropped by a filter written to keep a wrist out of a bearing
   * about a head.
   *
   * The vector is logged rather than its length, because the axis convention in `GlassesAim` is
   * still a guess and a still head reading roughly `9.8` on one component is the measurement that
   * settles it.
   */
  private fun describeOnce(sample: MotionSample) {
    if (!synchronized(lock) { motionSourcesHeard.add(sample.source) }) return

    BirdLog.info(LogCategory.GLASSES) {
      "motion — first sample from ${sample.source}: accel " +
          (sample.accelerometer?.let {
            "(${it.x.toOneDecimal()}, ${it.y.toOneDecimal()}, ${it.z.toOneDecimal()}) m/s²"
          } ?: "none — no elevation or bearing from this pair") +
          ", magnetometer " +
          (if (sample.magnetometer == null) "none — no bearing from this pair" else "present") +
          // The fused attitude, which would make both derivations arithmetic instead of a
          // guess about which way the axes point. Reported because nothing else says
          // whether this pair sends one.
          ", orientation " +
          (if (sample.orientation == null) "none" else "present") +
          if (sample.source == MotionSource.GLASSES) "" else " · dropped, not the glasses"
    }
  }

  /**
   * Ends every motion subscription, because the glasses have no readings to give.
   *
   * **Silence and *there is nothing here* are the same thing to a collector, and they must not
   * be.** Whoever is waiting on these samples treats the flow completing as *the glasses cannot
   * answer this, ask the phone* — that is the whole of how the aim falls back (see
   * `FailoverReadings`). A capability that never attached, or that went down under a running
   * session, would otherwise leave the flow open and mute: the failover keeps waiting on a pair
   * that is never going to speak, the phone's own sensors are never asked again, and the reading on
   * screen freezes on whatever it last had while every log line says the glasses have it.
   *
   * **This is for a sensor that is gone, not one that is busy.** A stop that came from something
   * else on the link is answered by [reviveMotion] instead, and only reaches here once the sensor
   * has refused to come back — the failover is a change of instrument, and making one for every
   * photograph would be worse than the freeze it avoids.
   *
   * A `SharedFlow` has no completion of its own, so the end is a `null` the public flow stops on.
   * Unlike the presses, these subscriptions are not a lease worth keeping — a collector whose flow
   * completes can start another the moment there is a session to start it against, and the aim does
   * exactly that.
   */
  private fun finishMotionListeners() {
    if (motionSamples.subscriptionCount.value == 0) return
    BirdLog.info(LogCategory.GLASSES) { "motion — no readings to give; ending subscriptions" }
    motionSamples.tryEmit(null)
  }

  /**
   * Every sample, every field, for as long as the sensor runs.
   *
   * **The whole reading, not the part a derivation happens to use.** [receive] keeps two vectors
   * out of six fields and throws the rest away before anything can see it, which makes every
   * question about the sensor unanswerable without a rebuild — and the questions keep coming,
   * because the frame these readings are in was never documented. The gyroscope in particular has
   * never once been logged, and it is the field that says whether a wandering elevation is the
   * wearer's head moving or the sensor lying about it.
   *
   * **One line every three seconds, timed off the sensor's own clock.** Every sample was five lines
   * a second, which buried every other glasses line in the file and made the log worth less than
   * the thing it was recording. Three seconds is the cadence of a wearer moving their head, not of
   * an IMU, and it is what these readings are actually read at.
   *
   * The interval is measured on `timestampNs` rather than a count, so it stays three seconds if the
   * sampling rate changes, and rather than the wall clock, because that is the clock the samples
   * are stamped on and a correction mid-session must not open a gap in the log.
   */
  private fun describeEvery(sample: MotionSample) {
    val millis = sample.timestampNs / 1_000_000
    synchronized(lock) {
      if (millis - lastMotionLogMillis < MotionLogIntervalMillis) return
      lastMotionLogMillis = millis
    }

    val accel =
        sample.accelerometer?.let {
          "(${it.x.toOneDecimal()}, ${it.y.toOneDecimal()}, ${it.z.toOneDecimal()}) m/s²"
        } ?: "none"
    val gyro =
        sample.gyroscope?.let {
          "(${it.x.toTwoDecimals()}, ${it.y.toTwoDecimals()}, ${it.z.toTwoDecimals()}) rad/s"
        } ?: "none"
    val field =
        sample.magnetometer?.let {
          "(${it.x.toOneDecimal()}, ${it.y.toOneDecimal()}, ${it.z.toOneDecimal()}) µT"
        } ?: "none"
    val attitude =
        sample.orientation?.let {
          "(${it.x.toTwoDecimals()}, ${it.y.toTwoDecimals()}, " +
              "${it.z.toTwoDecimals()}, ${it.w.toTwoDecimals()})"
        } ?: "none"
    BirdLog.debug(LogCategory.GLASSES) {
      "motion — ${sample.source} accel $accel · gyro $gyro · mag $field · orientation $attitude · t ${sample.timestampNs / 1_000_000} ms"
    }
  }

  /**
   * One event off the capability, translated into something the app has a meaning for — or, far
   * more often, deliberately dropped.
   *
   * **Everything is logged, including what is ignored.** Two questions about this hardware are
   * still open and only a real pair of glasses can answer them: how a fast double press is
   * decomposed — two short presses, one double, or all three — and whether a short press also
   * leaves a photograph in the wearer's own camera roll. Both are read straight off these lines,
   * which is why the ignored press types say so out loud instead of vanishing.
   */
  private fun receive(event: InputEvent) {
    when (event) {
      is InputEvent.Capture -> {
        if (
            event.pressType != CapturePressType.SHORT_PRESS ||
                event.source != InputSource.CAPTURE_BUTTON
        ) {
          BirdLog.debug(LogCategory.GLASSES) {
            "input — ignored capture ${event.pressType} from ${event.source}"
          }
          return
        }
        BirdLog.info(LogCategory.GLASSES) { "input — capture button, short press" }
        publish(GlassesInputEvent.SHUTTER)
      }

      // **Unfiltered by source, unlike the shutter.** A back is the same request from the
      // wearer whatever sent it, and answering only one surface would make the gesture work
      // or not work depending on what they happen to have on. In practice the temple is the
      // only one that can send it, since the band is not subscribed — but that is a fact
      // about the attach request, not something this handler should re-decide. The source is
      // logged instead, because which surfaces actually send it is a fact about this
      // hardware nobody has read off a real pair yet.
      is InputEvent.Back -> {
        BirdLog.info(LogCategory.GLASSES) { "input — back from ${event.source}" }
        publish(GlassesInputEvent.BACK)
      }

      else -> {
        // Nothing else has a meaning up there, so anything else arriving is worth a
        // line: it means the configuration is not the whole story about what this pair
        // sends.
        BirdLog.debug(LogCategory.GLASSES) { "input — ignored $event" }
      }
    }
  }

  /**
   * Hands a gesture to everyone listening. One with nobody listening is dropped, which is what
   * should happen to a temple pressed while no session is watching for it.
   */
  private fun publish(event: GlassesInputEvent) {
    if (!inputEvents.tryEmit(event)) {
      BirdLog.warning(LogCategory.GLASSES) {
        "input — $event was dropped; nothing was reading fast enough"
      }
    }
  }

  /**
   * One of Meta AI's grants, as far as it can be read right now.
   *
   * **A failure here is not a denial — it is usually "nothing is connected".** DAT reads the grant
   * off the glasses themselves, so with no pair on the link the check fails
   * `NO_DEVICE_WITH_CONNECTION` rather than answering; `NO_DEVICE` (none paired at all) and
   * `META_AI_NOT_INSTALLED` land the same way, and none of the three is the wearer saying no. Every
   * failure therefore becomes [GlassesAccess.UNKNOWN], and the specific error goes to the log,
   * where it is the diagnosis.
   *
   * The success side has only two answers to give: DAT's `PermissionStatus` is `Granted`/`Denied`
   * and carries no not-determined, so a grant never asked for arrives here as `Denied`.
   */
  override suspend fun access(permission: GlassesPermission): GlassesAccess {
    // The grants read identically, so every line has to say which one was asked about.
    val name = permission.logName
    return Wearables.checkPermissionStatus(permission.datPermission)
        .fold(
            onSuccess = { status ->
              val granted = status == DatPermissionStatus.Granted
              BirdLog.info(LogCategory.GLASSES) {
                "$name access — ${if (granted) "granted" else "denied"}"
              }
              if (granted) GlassesAccess.GRANTED else GlassesAccess.DENIED
            },
            onFailure = { error, _ ->
              // Still `info`, not `error`: with nothing on the link this fails every time
              // it is asked, and it is not a failure — see the doc above.
              BirdLog.info(LogCategory.GLASSES) {
                "$name access unreadable — ${error.description}"
              }
              GlassesAccess.UNKNOWN
            },
        )
  }

  /**
   * One photograph through the running stream, for [DatGlassesCameraRepository].
   *
   * The ask and the answer are one suspending call on the stream, but the wait is still on a
   * rendezvous rather than on the SDK: two things can end it besides the photograph — the link
   * going down under it, which ends it at once (see [failPendingPhoto]), and
   * [PhotoTransferTimeoutMillis], the backstop for a crossing that dies mid-air with the link still
   * up.
   */
  suspend fun captureThroughActiveCamera(
      format: PhotoFormat,
      resolution: CaptureResolution,
      quality: CaptureQuality,
  ): CapturedPhoto = coroutineScope {
    val stream =
        synchronized(lock) { activeCamera }?.stream
            ?: run {
              BirdLog.warning(LogCategory.GLASSES) {
                "capture — asked for, but no camera is attached"
              }
              throw GlassesError.NotConnected
            }
    // The stream has a readiness of its own, and asking anyway is worse than not asking:
    // the refusal comes back at once, which reads on the log as a photograph that crossed
    // in no time at all and arrived empty.
    if (stream.state.value != StreamState.STREAMING) {
      BirdLog.warning(LogCategory.GLASSES) {
        "capture — asked for, but the stream is not up"
      }
      throw GlassesError.NotConnected
    }
    // Stamped so the line below can say how long the crossing took. That number is the
    // single most useful thing in the file when a demo "felt slow": a photograph is
    // supposed to be about a second, and knowing it was nine is the whole diagnosis.
    // `elapsedRealtime`, not the wall clock, which a correction mid-crossing would move.
    val startedAt = SystemClock.elapsedRealtime()
    // The capability encodes what it encodes and takes no format argument. Named here so
    // the day a second format appears, this is the one line that learns about it.
    when (format) {
      PhotoFormat.JPEG -> Unit
    }
    // The stream's capture takes no size and no compression either — the capability that
    // does is the one whose stills need a file-transfer channel this build has nothing
    // behind (see [DatGlassesCameraRepository.honoursCaptureSettings]). Both are still
    // written to the log, because a photograph that came back the wrong size is otherwise
    // indistinguishable from one that came back at the size it was asked for.
    BirdLog.debug(LogCategory.GLASSES) {
      "capture — ${resolution.name}/${quality.name} asked for; this path honours neither"
    }

    // The wait is on the rendezvous rather than on the SDK call, so that the link going
    // down can end it — see [failPendingPhoto]. Whoever gets there first wins; the loser
    // is cancelled where it stands.
    val crossing = CompletableDeferred<PhotoData?>()
    synchronized(lock) { pendingPhoto = crossing }
    BirdLog.debug(LogCategory.GLASSES) { "capture — fired, waiting on the crossing" }
    val ask = launch {
      stream
          .capturePhoto()
          .fold(
              onSuccess = { data -> crossing.complete(data) },
              onFailure = { error, _ ->
                BirdLog.error(LogCategory.GLASSES) { "capture failed: ${error.description}" }
                crossing.complete(null)
              },
          )
    }

    val capture =
        try {
          withTimeoutOrNull(PhotoTransferTimeoutMillis) { crossing.await() }
        } finally {
          synchronized(lock) { pendingPhoto = null }
          ask.cancel()
        }
    val elapsedMs = SystemClock.elapsedRealtime() - startedAt
    if (capture == null) {
      // Every ending but the photograph lands here — the SDK's refusal, the timeout,
      // the link going down under the crossing — and the elapsed time is what tells
      // them apart without a second line.
      BirdLog.error(LogCategory.GLASSES) {
        "capture — nothing arrived after $elapsedMs ms"
      }
      throw GlassesError.TransferFailed
    }
    val bytes = capture.toImageBytes()
    BirdLog.info(LogCategory.GLASSES) {
      "capture — ${bytes.size} bytes crossed in $elapsedMs ms"
    }
    CapturedPhoto(imageData = bytes)
  }

  /**
   * An arrival as bytes the rest of the app can keep.
   *
   * The capability hands over one of two shapes. A decoded image has to be written back out to be
   * stored — JPEG, because it is the format every consumer downstream reads. An encoded arrival is
   * kept exactly as it crossed: the decoder downstream reads the container, and a re-encode would
   * trade fidelity for nothing.
   */
  private fun PhotoData.toImageBytes(): ByteArray =
      when (this) {
        is PhotoData.Bitmap ->
            ByteArrayOutputStream()
                .also { sink ->
                  bitmap.compress(AndroidBitmap.CompressFormat.JPEG, CaptureJpegQuality, sink)
                }
                .toByteArray()
        is PhotoData.HEIC ->
            data.asReadOnlyBuffer().let { buffer ->
              buffer.rewind()
              ByteArray(buffer.remaining()).also { buffer.get(it) }
            }
      }

  /**
   * Fails whatever photograph is mid-crossing, if any.
   *
   * **A crossing cannot outlive the stream it is crossing.** The picture arrives on a flow that
   * goes quiet with the capability, so a session ended under it — a long press on the temple, a
   * hinge, the watcher tapping the switch — left the shutter sitting on
   * [PhotoTransferTimeoutMillis]: fifteen seconds of an empty row on the log, and then a line about
   * glasses on a screen that had been back on the phone the whole time.
   *
   * Completing the rendezvous with nothing in it is what fails the capture. The SDK's own refusal
   * arrives the same way, delivered by the asking coroutine — one door for every ending, so the
   * shutter has one thing to wait on.
   *
   * The timeout stays, for the case it was written for — a crossing that dies mid-air with the link
   * still up, which nothing here can hear about.
   */
  private fun failPendingPhoto() {
    synchronized(lock) { pendingPhoto.also { pendingPhoto = null } }?.complete(null)
  }

  private companion object {
    /**
     * How long a photograph gets to cross before the shutter is failed. The published claim is
     * about a second; fifteen forgives a congested link without stranding the UI forever.
     */
    private const val PhotoTransferTimeoutMillis = 15_000L

    /** How often the full motion reading is written out — see [describeEvery]. */
    private const val MotionLogIntervalMillis = 3_000L

    /** How long a capability gets between tries at coming back — see [bringMotionBack]. */
    private const val SensorRevivalDelayMillis = 750L

    /**
     * How long a send waits for the display to reach STARTED before giving up — the panel's own
     * warm-up, plus the crossing-teardown beat the sensors also ride out.
     */
    private const val DisplayStartTimeoutMillis = 3_000L

    /** How many times the sensor is asked to start again before the aim goes back to the phone. */
    private const val SensorRevivalAttempts = 3

    /**
     * What a decoded arrival is written back out at — see [toImageBytes].
     *
     * The capture takes no size or compression request; the crossing has already happened by the
     * time this number is used, so it costs storage rather than link time. High, because feather
     * detail is what the catalog match is for and re-encoding is the only loss on this path the app
     * controls.
     */
    private const val CaptureJpegQuality = 90
  }
}

/**
 * The SDK's own case. Exhaustive on purpose: DAT's `Permission` is the reason the domain enum has
 * the two cases it has, and a third arriving in a preview release should stop the build here rather
 * than quietly go unaskable.
 *
 * Not private, because *reading* a grant and *asking* for one are split across two layers — the
 * settings screen owns the raise, for the reason
 * [com.meta.pixelandtexel.birdspotter.domain.PermissionsController] documents — and one translation
 * of the same two cases is enough.
 */
internal val GlassesPermission.datPermission: DatPermission
  get() =
      when (this) {
        GlassesPermission.CAMERA -> DatPermission.CAMERA
        GlassesPermission.MICROPHONE -> DatPermission.MICROPHONE
      }

/** What to call it in the log. */
private val GlassesPermission.logName: String
  get() =
      when (this) {
        GlassesPermission.CAMERA -> "camera"
        GlassesPermission.MICROPHONE -> "microphone"
      }

/**
 * What a session failure means to the app.
 *
 * **One case is singled out, and the rest deliberately are not.** The screen can offer exactly two
 * answers — go update your glasses, or try again — so the only distinction worth carrying up is the
 * one that changes which of those a wearer is told. Everything else (nothing eligible, a session
 * already running, heat, power) is a link that will not hold, which is what
 * [GlassesError.NotConnected] says; the specific case is in the log line beside every call to this,
 * which is where it is any use.
 */
private fun DeviceSessionError?.asGlassesError(): GlassesError =
    when (this) {
      DeviceSessionError.DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED ->
          GlassesError.GlassesUpdateRequired
      else -> GlassesError.NotConnected
    }

/**
 * A sensor reading at the precision a log line can use, without going through a formatter — the
 * decimal separator of whatever locale the phone is set to has no business in a diagnostic that
 * gets pasted into a bug report.
 */
private fun Float.toOneDecimal(): Double = (this * 10).roundToInt() / 10.0

/**
 * The same, for the readings a single decimal would flatten to zero — a gyroscope in rad/s spends
 * most of its life under `0.05`, and a column of `0.0` says nothing at all.
 */
private fun Float.toTwoDecimals(): Double = (this * 100).roundToInt() / 100.0

/**
 * How long [DatGlassesSessionRepository.endActiveSessions] waits for a lease to clear — twenty
 * looks, fifty milliseconds apart: a second, which a stopping session never needs.
 */
private const val EndActiveSessionsPolls = 20
private const val EndActiveSessionsPollMillis = 50L
