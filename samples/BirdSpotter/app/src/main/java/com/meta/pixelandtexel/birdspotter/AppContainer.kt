/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter

import android.content.Context
import android.os.Build
import com.meta.pixelandtexel.birdspotter.data.audio.FailoverAudioSource
import com.meta.pixelandtexel.birdspotter.data.audio.GlassesMicrophoneSource
import com.meta.pixelandtexel.birdspotter.data.audio.PhoneMicrophoneSource
import com.meta.pixelandtexel.birdspotter.data.audio.SystemSpokenOutput
import com.meta.pixelandtexel.birdspotter.data.camera.CaptureScratchStore
import com.meta.pixelandtexel.birdspotter.data.camera.PhoneCameraPreviewSource
import com.meta.pixelandtexel.birdspotter.data.catalog.CatalogAssetStore
import com.meta.pixelandtexel.birdspotter.data.catalog.CatalogDatabase
import com.meta.pixelandtexel.birdspotter.data.catalog.LocalBirdCatalogRepository
import com.meta.pixelandtexel.birdspotter.data.dat.DatGlassesCameraRepository
import com.meta.pixelandtexel.birdspotter.data.dat.DatGlassesDisplayRepository
import com.meta.pixelandtexel.birdspotter.data.dat.DatGlassesInputRepository
import com.meta.pixelandtexel.birdspotter.data.dat.DatGlassesMotionRepository
import com.meta.pixelandtexel.birdspotter.data.dat.DatGlassesSessionRepository
import com.meta.pixelandtexel.birdspotter.data.dat.DatGlassesSpeechRepository
import com.meta.pixelandtexel.birdspotter.data.dat.DatGlassesVoiceRepository
import com.meta.pixelandtexel.birdspotter.data.dat.DatMockDeviceRepository
import com.meta.pixelandtexel.birdspotter.data.demo.DemoSettingsStore
import com.meta.pixelandtexel.birdspotter.data.demo.PresetDemoDirector
import com.meta.pixelandtexel.birdspotter.data.diagnostics.ConsoleLogSink
import com.meta.pixelandtexel.birdspotter.data.diagnostics.DiagnosticsLogStore
import com.meta.pixelandtexel.birdspotter.data.diagnostics.DiagnosticsSettingsStore
import com.meta.pixelandtexel.birdspotter.data.diagnostics.FileLogSink
import com.meta.pixelandtexel.birdspotter.data.diagnostics.MainThreadWatchdog
import com.meta.pixelandtexel.birdspotter.data.display.DisplaySettingsStore
import com.meta.pixelandtexel.birdspotter.data.journal.JournalDatabase
import com.meta.pixelandtexel.birdspotter.data.journal.LocalJournalRepository
import com.meta.pixelandtexel.birdspotter.data.location.FailoverGazeProvider
import com.meta.pixelandtexel.birdspotter.data.location.FailoverHeadingProvider
import com.meta.pixelandtexel.birdspotter.data.location.GlassesGazeProvider
import com.meta.pixelandtexel.birdspotter.data.location.GlassesHeadingProvider
import com.meta.pixelandtexel.birdspotter.data.location.SystemGazeProvider
import com.meta.pixelandtexel.birdspotter.data.location.SystemHeadingProvider
import com.meta.pixelandtexel.birdspotter.data.location.SystemLocationProvider
import com.meta.pixelandtexel.birdspotter.data.location.SystemMapLauncher
import com.meta.pixelandtexel.birdspotter.data.media.MediaFileStore
import com.meta.pixelandtexel.birdspotter.data.mockdevice.MockDeviceSettingsStore
import com.meta.pixelandtexel.birdspotter.data.permissions.SystemPermissionsController
import com.meta.pixelandtexel.birdspotter.data.session.SessionSettingsStore
import com.meta.pixelandtexel.birdspotter.domain.AudioCaptureSource
import com.meta.pixelandtexel.birdspotter.domain.BirdCatalogRepository
import com.meta.pixelandtexel.birdspotter.domain.BirdLog
import com.meta.pixelandtexel.birdspotter.domain.CameraPreviewSource
import com.meta.pixelandtexel.birdspotter.domain.DemoDirector
import com.meta.pixelandtexel.birdspotter.domain.GazeProvider
import com.meta.pixelandtexel.birdspotter.domain.GlassesCameraRepository
import com.meta.pixelandtexel.birdspotter.domain.GlassesDisplayRepository
import com.meta.pixelandtexel.birdspotter.domain.GlassesInputRepository
import com.meta.pixelandtexel.birdspotter.domain.GlassesMotionRepository
import com.meta.pixelandtexel.birdspotter.domain.GlassesSessionRepository
import com.meta.pixelandtexel.birdspotter.domain.GlassesSpeechRepository
import com.meta.pixelandtexel.birdspotter.domain.GlassesVoiceRepository
import com.meta.pixelandtexel.birdspotter.domain.HeadingProvider
import com.meta.pixelandtexel.birdspotter.domain.JournalRepository
import com.meta.pixelandtexel.birdspotter.domain.LocationProvider
import com.meta.pixelandtexel.birdspotter.domain.LogCategory
import com.meta.pixelandtexel.birdspotter.domain.LogSink
import com.meta.pixelandtexel.birdspotter.domain.MapLauncher
import com.meta.pixelandtexel.birdspotter.domain.MockDeviceRepository
import com.meta.pixelandtexel.birdspotter.domain.PermissionsController
import com.meta.pixelandtexel.birdspotter.domain.SessionDetector
import com.meta.pixelandtexel.birdspotter.domain.SpokenOutput
import com.meta.pixelandtexel.birdspotter.features.identify.RealtimeViewModel

/**
 * The composition root: every long-lived dependency, built once and handed down.
 *
 * No DI framework, deliberately. The object graph is a handful of repositories over two SQLite
 * files and (later) the DAT SDK, and the mirrored-architecture contract asks for init injection, so
 * anything Meta puts in a side-by-side snippet has to be constructed the plain way rather than
 * conjured by an annotation.
 *
 * Held by [BirdSpotterApplication], so it outlives configuration changes and the database is opened
 * exactly once per process.
 */
class AppContainer(context: Context) {

  private val applicationContext = context.applicationContext

  /**
   * The rolling diagnostic log's files — where the Diagnostics screen reads them from, and where
   * [FileLogSink] writes them. See [DiagnosticsLogStore].
   *
   * **Not `by lazy`, unlike everything below.** The log has to be up before anything else exists,
   * or the launch — the run people most often come asking about — is the one run with no lines in
   * it. See [installDiagnostics].
   */
  val diagnosticsLogStore: DiagnosticsLogStore = DiagnosticsLogStore.open(applicationContext)

  /**
   * Whether the log writes files, and how far down it records. Read before the first line of the
   * run — see [installDiagnostics].
   */
  val diagnosticsSettings: DiagnosticsSettingsStore =
      DiagnosticsSettingsStore.open(applicationContext)

  init {
    installDiagnostics()
  }

  /**
   * Opened lazily: [CatalogDatabase.open] touches the filesystem to check whether the bundled seed
   * supersedes the installed one, which has no business running on the main thread during
   * `Application.onCreate`. Room's own connection is lazy too, so nothing is read until the first
   * query.
   */
  private val catalogDatabase: CatalogDatabase by lazy {
    CatalogDatabase.open(applicationContext)
  }

  val birdCatalogRepository: BirdCatalogRepository by lazy {
    LocalBirdCatalogRepository(catalogDatabase.speciesStore())
  }

  /** Bundled photos and calls. Not a repository — it resolves bytes, not rows. */
  val catalogAssetStore: CatalogAssetStore by lazy {
    CatalogAssetStore.open(applicationContext)
  }

  private val journalDatabase: JournalDatabase by lazy {
    JournalDatabase.open(applicationContext)
  }

  /**
   * The captured-media directory, shared by the Journal store and the screens that draw captured
   * photos. Exposed like [catalogAssetStore] — it resolves files, not rows.
   */
  val mediaFileStore: MediaFileStore by lazy {
    MediaFileStore.open(applicationContext)
  }

  /**
   * Where a photograph taken only to be looked at lands. Nothing in the Journal's world touches it
   * — see [CaptureScratchStore].
   */
  val captureScratchStore: CaptureScratchStore by lazy {
    CaptureScratchStore.open(applicationContext)
  }

  val journalRepository: JournalRepository by lazy {
    LocalJournalRepository(
        store = journalDatabase.journalStore(),
        mediaFileStore = mediaFileStore,
    )
  }

  /**
   * OS permission status for the Identify tab's gates (camera, mic, location) and the trip back to
   * Settings. Requesting the prompt lives in the Identify screen — see [PermissionsController].
   */
  val permissionsController: PermissionsController by lazy {
    SystemPermissionsController(applicationContext)
  }

  /** Phone GPS for the sighting stamp — one fix per identify run. See [LocationProvider]. */
  val locationProvider: LocationProvider by lazy {
    SystemLocationProvider(applicationContext)
  }

  /**
   * Which way the watcher is facing, for as long as a session is watching it.
   *
   * Both aim readings work the way the microphone does: the head the wearer is actually aiming when
   * the session has it, the device in their hand when it does not, and nothing above here learns
   * that either one changed. See [FailoverReadings].
   */
  val headingProvider: HeadingProvider by lazy {
    FailoverHeadingProvider(
        preferred = GlassesHeadingProvider(glassesMotionRepository),
        fallback = SystemHeadingProvider(applicationContext),
    )
  }

  /**
   * How high the watcher is aiming, for as long as a session is watching it. Its own provider
   * rather than a second question put to [headingProvider] — see [GazeProvider].
   */
  val gazeProvider: GazeProvider by lazy {
    FailoverGazeProvider(
        preferred = GlassesGazeProvider(glassesMotionRepository),
        fallback = SystemGazeProvider(applicationContext),
    )
  }

  /**
   * The live viewfinder's frames — the phone's, and only the phone's: the glasses have no
   * viewfinder in this app, and their photographs arrive finished through [glassesCameraRepository]
   * instead. See [CameraPreviewSource].
   */
  val cameraPreviewSource: CameraPreviewSource by lazy {
    PhoneCameraPreviewSource(applicationContext)
  }

  /**
   * The real-time session's spine. Held open for the whole session, where the camera comes and goes
   * — see [AudioCaptureSource].
   */
  val audioCaptureSource: AudioCaptureSource by lazy {
    // One stream, two microphones underneath — the session asks for the glasses when it has
    // them and is handed the phone back when it does not, without the screen above learning
    // that failover exists. See [FailoverAudioSource]. The glasses' ears are the session's
    // camera stream, so they hang off the same link as the shutter.
    FailoverAudioSource(
        preferred = GlassesMicrophoneSource(datGlassesSessionRepository),
        fallback = PhoneMicrophoneSource(applicationContext),
    )
  }

  /**
   * The app's own voice — an identification said where the wearer will hear it, when there is an
   * ear to say it into. See [SpokenOutput].
   *
   * Lazy like everything else here, and that matters more than usual: constructing it binds to the
   * platform's speech service, which is work worth deferring until a session is actually opened
   * rather than doing during launch.
   */
  val spokenOutput: SpokenOutput by lazy {
    SystemSpokenOutput(applicationContext)
  }

  /**
   * The Demo Director's saved presets and armed id. Shared by the Director (which reads the armed
   * preset) and the settings screens (which author and arm them) — one store, so arming in Settings
   * is what the next session plays.
   */
  val demoSettingsStore: DemoSettingsStore by lazy {
    DemoSettingsStore.open(applicationContext)
  }

  /** What the real-time screen remembers between sessions — see [SessionSettingsStore]. */
  val sessionSettingsStore: SessionSettingsStore by lazy {
    SessionSettingsStore.open(applicationContext)
  }

  /** The display screen's custom card, kept between runs — see [DisplaySettingsStore]. */
  val displaySettingsStore: DisplaySettingsStore by lazy {
    DisplaySettingsStore.open(applicationContext)
  }

  /**
   * The Demo Director: scripts what the app "identifies", from the armed preset — see
   * [DemoDirector]. Exposed with its full surface so the settings panel and the photo/STT paths
   * reach the parts that answer on cue; the realtime session takes it as [sessionDetector].
   */
  val demoDirector: DemoDirector by lazy {
    PresetDemoDirector(
        store = demoSettingsStore,
        catalog = birdCatalogRepository,
    )
  }

  /**
   * What decides a bird was heard. The Director wearing its [SessionDetector] hat — scripted on
   * purpose, per the honest-fakes pillar.
   */
  val sessionDetector: SessionDetector by lazy { demoDirector }

  /** Hands a logged sighting's location off to the phone's maps app. See [MapLauncher]. */
  val mapLauncher: MapLauncher by lazy {
    SystemMapLauncher(applicationContext)
  }

  /**
   * The one object the live DAT session lives in — both public glasses repositories are hats on it.
   * DAT itself boots in its `init` (see [DatGlassesSessionRepository]), so a JVM test that never
   * touches glasses never loads the SDK; MainActivity warms this at launch so the registration
   * callback can never arrive first.
   */
  private val datGlassesSessionRepository: DatGlassesSessionRepository by lazy {
    DatGlassesSessionRepository(applicationContext)
  }

  /**
   * The app's standing with the glasses — registration, the device that would answer, and the
   * session a live run holds open. See [GlassesSessionRepository].
   */
  val glassesSessionRepository: GlassesSessionRepository by lazy {
    datGlassesSessionRepository
  }

  /**
   * The glasses camera: one photograph on demand, through the running session's camera. Shares
   * [datGlassesSessionRepository] because a capture is scoped to the session's own camera
   * capability — see [GlassesCameraRepository].
   */
  val glassesCameraRepository: GlassesCameraRepository by lazy {
    DatGlassesCameraRepository(datGlassesSessionRepository)
  }

  /**
   * The buttons on the glasses, for as long as a session is listening. Shares
   * [datGlassesSessionRepository] for the same reason the camera does — the capability rides the
   * session. See [GlassesInputRepository].
   */
  val glassesInputRepository: GlassesInputRepository by lazy {
    DatGlassesInputRepository(datGlassesSessionRepository)
  }

  /**
   * The display on the glasses — an identified bird's photographs, paged where the wearer is
   * already looking. Shares [datGlassesSessionRepository] because the capability rides the session,
   * and [catalogAssetStore] because the photographs are the catalog's own. See
   * [GlassesDisplayRepository].
   */
  val glassesDisplayRepository: GlassesDisplayRepository by lazy {
    DatGlassesDisplayRepository(datGlassesSessionRepository, catalogAssetStore)
  }

  /**
   * The glasses' motion sensors, for as long as something is listening. Shares
   * [datGlassesSessionRepository] for the same reason the camera and the buttons do — the
   * capability rides the session. See [GlassesMotionRepository].
   */
  private val glassesMotionRepository: GlassesMotionRepository by lazy {
    DatGlassesMotionRepository(datGlassesSessionRepository)
  }

  /**
   * What the wearer says, transcribed on the glasses themselves. Shares
   * [datGlassesSessionRepository] for the same reason every other sense does — the capability rides
   * the session.
   *
   * **No phone-side counterpart, and that is the design.** Every other sense here has a failover
   * onto the device in the watcher's hand; this one deliberately does not, because a phone
   * recogniser would have to take the microphone away from the ambient lane to work. See
   * [GlassesSpeechRepository].
   */
  val glassesSpeechRepository: GlassesSpeechRepository by lazy {
    DatGlassesSpeechRepository(datGlassesSessionRepository)
  }

  /**
   * What the wearer says to Meta AI about this app — "Hey Meta, open BirdSpotter" — and the
   * acknowledgement each invocation is owed. Takes [datGlassesSessionRepository] not for its
   * session but for its `init`: the channel must never be asked for before the SDK it rides on is
   * up. See [GlassesVoiceRepository].
   */
  private val datGlassesVoiceRepository: DatGlassesVoiceRepository by lazy {
    DatGlassesVoiceRepository(datGlassesSessionRepository)
  }

  val glassesVoiceRepository: GlassesVoiceRepository by lazy { datGlassesVoiceRepository }

  /**
   * How the mock is set: the switch, the model it fakes, where its button was pinned. Shared by the
   * repository (which restores it at launch) and the overlay (which moves the button) — one store,
   * so the pin survives the relaunch. See [MockDeviceSettingsStore].
   */
  val mockDeviceSettingsStore: MockDeviceSettingsStore by lazy {
    MockDeviceSettingsStore.open(applicationContext)
  }

  /**
   * Meta's Mock Device Kit, as the app drives it: the switch that stands simulated glasses in for
   * real ones, and the controls on the simulated pair. Takes [datGlassesSessionRepository] because
   * the flip has to end that repository's leases first — and because building it is what restores
   * the last choice, MainActivity warms it at launch right after the session repository. See
   * [MockDeviceRepository].
   */
  val mockDeviceRepository: MockDeviceRepository by lazy {
    DatMockDeviceRepository(
        applicationContext,
        datGlassesSessionRepository,
        datGlassesVoiceRepository,
        mockDeviceSettingsStore,
    )
  }

  /**
   * The one real-time session the app can hold, alive whether or not a screen is showing it. Here
   * rather than in the cover that draws it because a session started by a voice launch with the
   * phone locked in a pocket has no cover, and a cover that falls must not take a session in
   * progress down with it.
   *
   * Lazy like everything else, and it matters here: building it reaches the glasses repositories,
   * which boot DAT, so nothing may touch it before MainActivity has the Bluetooth grant answered.
   */
  val realtimeViewModel: RealtimeViewModel by lazy {
    RealtimeViewModel(
        audioSource = audioCaptureSource,
        previewSource = cameraPreviewSource,
        detector = sessionDetector,
        director = demoDirector,
        birdCatalog = birdCatalogRepository,
        journal = journalRepository,
        glassesSession = glassesSessionRepository,
        glassesCamera = glassesCameraRepository,
        glassesInput = glassesInputRepository,
        glassesSpeech = glassesSpeechRepository,
        glassesDisplay = glassesDisplayRepository,
        spokenOutput = spokenOutput,
        locationProvider = locationProvider,
        headingProvider = headingProvider,
        gazeProvider = gazeProvider,
    )
  }

  /**
   * Points [BirdLog] at its sinks, per [diagnosticsSettings], and opens a file for this run of the
   * app.
   *
   * **Also the Diagnostics screen's apply button.** Turning file logging on or off there calls this
   * again rather than reaching into [BirdLog] itself, so the rule for which sinks are installed is
   * written once. Re-running it starts another file, which is the right answer for a switch flipped
   * mid-session: the run before the change and the run after it are two different things to read.
   *
   * The logcat sink is unconditional. It costs nothing on a device nobody has `adb` attached to,
   * and switching off the *files* should not also blind the developer who is sitting in front of
   * Android Studio.
   */
  fun installDiagnostics() {
    BirdLog.minimumLevel = diagnosticsSettings.minimumLevel

    val sinks =
        buildList<LogSink> {
          add(ConsoleLogSink())
          if (diagnosticsSettings.isFileLoggingEnabled) {
            diagnosticsLogStore.beginNewRun()
            add(FileLogSink(diagnosticsLogStore))
          }
        }
    BirdLog.install(sinks)

    // Says who is holding the main thread whenever the screen stops moving — see
    // [MainThreadWatchdog]. Installed once and left running: it costs a sleeping thread, and
    // it only ever writes a line when something is already visibly wrong.
    MainThreadWatchdog.install()

    // The first line of every file, and the one that makes a shared-out log worth
    // reading: which build, on what, recording how much.
    val version =
        runCatching {
          applicationContext.packageManager
              .getPackageInfo(applicationContext.packageName, 0)
              .versionName
        }
            .getOrNull() ?: "?"
    BirdLog.info(LogCategory.APP) {
      "BirdSpotter $version · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) " +
          "on ${Build.MANUFACTURER} ${Build.MODEL} " +
          "· recording at ${BirdLog.minimumLevel.displayLabel} " +
          "· files ${if (diagnosticsSettings.isFileLoggingEnabled) "on" else "off"}"
    }
  }
}
