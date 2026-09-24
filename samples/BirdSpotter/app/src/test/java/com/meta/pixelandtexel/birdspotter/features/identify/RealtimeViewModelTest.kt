/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

@file:Suppress("UseOfRunBlocking", "UseOfRunBlockingInTest")

package com.meta.pixelandtexel.birdspotter.features.identify

import android.graphics.Bitmap
import android.os.Looper
import com.meta.pixelandtexel.birdspotter.data.audio.WavCodec
import com.meta.pixelandtexel.birdspotter.data.catalog.Species
import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesGroup
import com.meta.pixelandtexel.birdspotter.data.catalog.SpeciesWithMedia
import com.meta.pixelandtexel.birdspotter.data.journal.CaptureSource
import com.meta.pixelandtexel.birdspotter.data.journal.GazeContext
import com.meta.pixelandtexel.birdspotter.data.journal.OutingDraft
import com.meta.pixelandtexel.birdspotter.data.journal.OutingEventType
import com.meta.pixelandtexel.birdspotter.data.journal.OutingKind
import com.meta.pixelandtexel.birdspotter.data.journal.OutingMediaType
import com.meta.pixelandtexel.birdspotter.data.journal.OutingWithChildren
import com.meta.pixelandtexel.birdspotter.domain.AudioCaptureError
import com.meta.pixelandtexel.birdspotter.domain.AudioCaptureSource
import com.meta.pixelandtexel.birdspotter.domain.AudioChunk
import com.meta.pixelandtexel.birdspotter.domain.BirdCatalogRepository
import com.meta.pixelandtexel.birdspotter.domain.CameraPreviewSource
import com.meta.pixelandtexel.birdspotter.domain.CaptureQuality
import com.meta.pixelandtexel.birdspotter.domain.CaptureResolution
import com.meta.pixelandtexel.birdspotter.domain.CaptureSourceKind
import com.meta.pixelandtexel.birdspotter.domain.CapturedPhoto
import com.meta.pixelandtexel.birdspotter.domain.Coordinate
import com.meta.pixelandtexel.birdspotter.domain.DemoDirector
import com.meta.pixelandtexel.birdspotter.domain.DemoPhotoResponse
import com.meta.pixelandtexel.birdspotter.domain.DemoPreset
import com.meta.pixelandtexel.birdspotter.domain.DemoQuestion
import com.meta.pixelandtexel.birdspotter.domain.DemoResult
import com.meta.pixelandtexel.birdspotter.domain.GazeProvider
import com.meta.pixelandtexel.birdspotter.domain.GlassesAccess
import com.meta.pixelandtexel.birdspotter.domain.GlassesCameraRepository
import com.meta.pixelandtexel.birdspotter.domain.GlassesDeviceInfo
import com.meta.pixelandtexel.birdspotter.domain.GlassesDisplayRepository
import com.meta.pixelandtexel.birdspotter.domain.GlassesError
import com.meta.pixelandtexel.birdspotter.domain.GlassesInputEvent
import com.meta.pixelandtexel.birdspotter.domain.GlassesInputRepository
import com.meta.pixelandtexel.birdspotter.domain.GlassesPermission
import com.meta.pixelandtexel.birdspotter.domain.GlassesRegistrationState
import com.meta.pixelandtexel.birdspotter.domain.GlassesSessionRepository
import com.meta.pixelandtexel.birdspotter.domain.GlassesSessionState
import com.meta.pixelandtexel.birdspotter.domain.GlassesSpeechRepository
import com.meta.pixelandtexel.birdspotter.domain.GlassesSpeechState
import com.meta.pixelandtexel.birdspotter.domain.HeadingProvider
import com.meta.pixelandtexel.birdspotter.domain.IdentifyQuery
import com.meta.pixelandtexel.birdspotter.domain.JournalRepository
import com.meta.pixelandtexel.birdspotter.domain.LocationProvider
import com.meta.pixelandtexel.birdspotter.domain.PhotoFormat
import com.meta.pixelandtexel.birdspotter.domain.PhotoIdentification
import com.meta.pixelandtexel.birdspotter.domain.PreviewFrame
import com.meta.pixelandtexel.birdspotter.domain.RealtimeSession
import com.meta.pixelandtexel.birdspotter.domain.SessionClock
import com.meta.pixelandtexel.birdspotter.domain.SessionDetector
import com.meta.pixelandtexel.birdspotter.domain.SessionEvent
import com.meta.pixelandtexel.birdspotter.domain.SessionFinding
import com.meta.pixelandtexel.birdspotter.domain.SpokenOutput
import com.meta.pixelandtexel.birdspotter.domain.Transcription
import com.meta.pixelandtexel.birdspotter.domain.compassPoint
import com.meta.pixelandtexel.birdspotter.domain.gazeBand
import com.meta.pixelandtexel.birdspotter.domain.placement
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The real-time session's policy: what it calls its source, what it tells the user when the
 * microphone will not open, how the clock reads, and how events land on the timeline in order.
 *
 * Most of it is pure — `RealtimeUiState`, `RealtimeSession` and `sessionStamp` need no view model
 * and no microphone, the same shape `ExploreViewModelTest` takes. What needs the runner is the
 * photo lane: a capture is a `Bitmap`, and a bitmap is the one thing on this screen the JVM has no
 * answer for.
 *
 * Scenario names are fixed by the testing-parity rule.
 */
@RunWith(RobolectricTestRunner::class)
class RealtimeViewModelTest {

  // ── The source pill ────────────────────────────────────────────────────

  @Test
  fun sourceLabel_namesTheDeviceInTheHand() {
    // Not "Phone": the pill's question is whose ears and eyes these are, and the useful
    // half of the answer is that they are not the glasses'.
    val state = RealtimeUiState(sourceKind = CaptureSourceKind.PHONE)

    assertEquals(SourceState.ON_DEVICE, state.sourceState)
    assertEquals("On device", state.sourceLabel)
  }

  @Test
  fun sourceLabel_namesTheGlasses() {
    val state = RealtimeUiState(sourceKind = CaptureSourceKind.GLASSES)

    assertEquals(SourceState.GLASSES, state.sourceState)
    assertEquals("Glasses", state.sourceLabel)
  }

  @Test
  fun sourceLabel_namesASimulatedFeed() {
    val state = RealtimeUiState(sourceKind = CaptureSourceKind.SIMULATED)

    assertEquals(SourceState.SIMULATED, state.sourceState)
    assertEquals("Simulated", state.sourceLabel)
  }

  @Test
  fun sourceLabel_readsLinkingWhileTheGlassesArrive() {
    val state = RealtimeUiState(isSourceLinking = true)

    assertEquals(SourceState.LINKING, state.sourceState)
    assertEquals("Linking", state.sourceLabel)
  }

  @Test
  fun sourceState_readsPausedWhileARunIsInFlightWithNothingLive() {
    // A doff or a temple tap: the run is still there to be resumed or hung up on, which is
    // neither the glasses answering nor the session being back on the phone.
    val state = RealtimeUiState(isGlassesRequested = true, isGlassesSessionLive = false)

    assertEquals(SourceState.GLASSES_PAUSED, state.sourceState)
    // `Waiting`, not `Paused` — see [RealtimeUiState.glassesSourceLabel]. The session did not
    // pause; the glasses did, and the phone is still recording.
    assertEquals("Waiting", state.sourceLabel)
  }

  @Test
  fun canToggleSource_needsRegisteredGlassesInReach() {
    // Also what decides the control's shape: a label with nothing to switch to, a two-sided
    // switch the moment there is.
    assertTrue(!RealtimeUiState().canToggleSource)
    assertTrue(RealtimeUiState(isGlassesAvailable = true).canToggleSource)
  }

  @Test
  fun theSwitchNamesBothSidesAtOnce() {
    val state = RealtimeUiState(isGlassesAvailable = true)

    assertEquals("On device", state.deviceSourceLabel)
    assertEquals("Glasses", state.glassesSourceLabel)
    assertTrue(!state.isGlassesSelected)
  }

  @Test
  fun theSwitchSaysASimulatedFeedIsStillOnTheDevice() {
    // The honesty line outranks the symmetry: a scripted feed is running on the thing in
    // the hand, and the left side is where that gets said.
    val state = RealtimeUiState(
        sourceKind = CaptureSourceKind.SIMULATED,
        isGlassesAvailable = true,
    )

    assertEquals("Simulated", state.deviceSourceLabel)
    assertTrue(!state.isGlassesSelected)
  }

  @Test
  fun theSwitchThrowsOnTheTapRatherThanOnTheEars() {
    // The crossing is the *ink's* business — a selection that waited for the audio would sit
    // under the thumb doing nothing, which is how a control gets pressed twice.
    val linking = RealtimeUiState(isGlassesRequested = true, isSourceLinking = true)

    assertTrue(linking.isGlassesSelected)
    assertEquals("Linking", linking.glassesSourceLabel)
    assertEquals("On device", linking.deviceSourceLabel)
  }

  @Test
  fun theGlassesSideCarriesEveryStateTheSessionCanBeIn() {
    // Every word this control has beyond the two device names is about the glasses; the
    // phone is only ever the phone.
    val paused = RealtimeUiState(isGlassesRequested = true, isGlassesSessionLive = false)

    assertEquals("Waiting", paused.glassesSourceLabel)
    assertTrue(paused.isGlassesSelected)
    assertEquals("On device", paused.deviceSourceLabel)
  }

  @Test
  fun aPausedRunStaysSelectedOnTheGlassesWithThePhoneCarryingIt() {
    // The switch says where the session is *assigned*; the carrying flag says who is doing
    // the work. Throwing the selection back to the phone would make the glasses side the
    // tappable one — and that tap hangs the run up.
    val paused =
        RealtimeUiState(isGlassesRequested = true)
            .applying(GlassesSessionState.STARTED)
            .hearing(CaptureSourceKind.GLASSES)
            .applying(GlassesSessionState.PAUSED)
            .hearing(CaptureSourceKind.PHONE)

    assertEquals(SourceState.GLASSES_PAUSED, paused.sourceState)
    assertTrue(paused.isGlassesSelected)
    assertTrue(paused.isDeviceCarrying)
  }

  @Test
  fun onlyAPausedRunHasThePhoneCarryingSomeoneElsesSession() {
    // Every other state has the lit side and the working side on the same device.
    assertTrue(!RealtimeUiState().isDeviceCarrying)
    assertTrue(!RealtimeUiState(isSourceLinking = true).isDeviceCarrying)
    assertTrue(!RealtimeUiState(sourceKind = CaptureSourceKind.GLASSES).isDeviceCarrying)
  }

  // ── A run whose microphone the glasses could not give ──────────────────

  @Test
  fun withoutGlassesEars_endsTheCrossingItCanNoLongerHonour() {
    // `Linking` is closed by glasses audio arriving — which, once the microphone has been
    // refused, is never. Left alone the pill promises the whole run a crossing that has
    // already been abandoned.
    val state =
        RealtimeUiState(isGlassesRequested = true, isSourceLinking = true)
            .applying(GlassesSessionState.STARTED)
            .withoutGlassesEars()

    assertTrue(!state.isSourceLinking)
    assertEquals(SourceState.ON_DEVICE, state.sourceState)
  }

  @Test
  fun withoutGlassesEars_saysWhichHalfOfTheGlassesSurvived() {
    val state =
        RealtimeUiState(isGlassesRequested = true, isSourceLinking = true)
            .applying(GlassesSessionState.STARTED)
            .withoutGlassesEars()

    // Told only that the microphone failed, a watcher reasonably concludes the glasses are
    // done — and the shutter and the button on the temple are still theirs.
    val notice = state.sourceNotice
    assertTrue(notice != null && notice.contains("Photos"))
  }

  @Test
  fun withoutGlassesEars_leavesTheSessionItselfAlone() {
    // The ears are not the session: a run with the phone's microphone still photographs
    // through the glasses, so nothing here may end it.
    val state =
        RealtimeUiState(isGlassesRequested = true, isSourceLinking = true)
            .applying(GlassesSessionState.STARTED)
            .withoutGlassesEars()

    assertTrue(state.isGlassesSessionLive)
    assertTrue(state.isGlassesRequested)
  }

  @Test
  fun aRunWithoutGlassesEarsStaysSelectedOnTheGlassesWithThePhoneCarryingIt() {
    // The same sentence the pause tells: the switch says where the session is assigned, the
    // carrying flag says who is listening meanwhile.
    val state =
        RealtimeUiState(isGlassesRequested = true, isSourceLinking = true)
            .applying(GlassesSessionState.STARTED)
            .withoutGlassesEars()
            .hearing(CaptureSourceKind.PHONE)

    assertTrue(state.isGlassesSelected)
    assertTrue(state.isDeviceCarrying)
  }

  @Test
  fun withoutGlassesEars_withNoRunInFlight_saysNothing() {
    // The signal outlives the request by a beat, and a sentence about glasses on a screen
    // that has gone back to the phone is worse than no sentence.
    val state = RealtimeUiState().withoutGlassesEars()

    assertEquals(null, state.sourceNotice)
  }

  @Test
  fun toggleSource_withoutGlassesInReach_staysOnPhone() {
    val viewModel = viewModel(audio = FakeAudioSource())

    viewModel.toggleSource()

    assertEquals(CaptureSourceKind.PHONE, viewModel.uiState.value.sourceKind)
  }

  // ── What the session's state does to the pill ──────────────────────────

  @Test
  fun applying_started_makesTheGlassesTheOnesToPhotographThrough() {
    val state = RealtimeUiState(isSourceLinking = true).applying(GlassesSessionState.STARTED)

    assertTrue(state.isGlassesSessionLive)
  }

  @Test
  fun applying_started_holdsTheLinkingBeatUntilTheEarsArrive() {
    // The session being up is the ears being *asked* for; the glasses' audio arrives a beat
    // later. Clearing the crossing here dropped the pill back to `On device` for a frame
    // between `Linking` and `Glasses`.
    val state =
        RealtimeUiState(isGlassesRequested = true, isSourceLinking = true)
            .applying(GlassesSessionState.STARTED)

    assertTrue(state.isSourceLinking)
    assertEquals(SourceState.LINKING, state.sourceState)
  }

  @Test
  fun hearing_theGlasses_endsTheCrossing() {
    val state =
        RealtimeUiState(isGlassesRequested = true, isSourceLinking = true)
            .applying(GlassesSessionState.STARTED)
            .hearing(CaptureSourceKind.GLASSES)

    assertTrue(!state.isSourceLinking)
    assertEquals(SourceState.GLASSES, state.sourceState)
  }

  @Test
  fun hearing_thePhoneMidCrossing_leavesTheCrossingOpen() {
    // The phone keeps recording underneath while the glasses warm up — the failover never
    // closes a working microphone on the promise of a better one. Its chunks say nothing
    // about whether the crossing has happened.
    val state =
        RealtimeUiState(isGlassesRequested = true, isSourceLinking = true)
            .applying(GlassesSessionState.STARTED)
            .hearing(CaptureSourceKind.PHONE)

    assertTrue(state.isSourceLinking)
    assertEquals(SourceState.LINKING, state.sourceState)
  }

  @Test
  fun theCrossingNeverShowsThePhoneOnTheWayToTheGlasses() {
    // The whole tap-to-glasses sequence, in order: nothing in it may read `On device`.
    val seen = mutableListOf<SourceState>()
    var state = RealtimeUiState(sourceKind = CaptureSourceKind.PHONE)
    seen += state.sourceState

    state = state.copy(isGlassesRequested = true, isSourceLinking = true)
    seen += state.sourceState

    state = state.applying(GlassesSessionState.STARTED)
    seen += state.sourceState

    state = state.hearing(CaptureSourceKind.GLASSES)
    seen += state.sourceState

    assertEquals(
        listOf(
            SourceState.ON_DEVICE,
            SourceState.LINKING,
            SourceState.LINKING,
            SourceState.GLASSES,
        ),
        seen,
    )
  }

  @Test
  fun aResumeAfterAPauseCrossesBackThroughLinking() {
    // A doff and a pick-up: `Paused`, then the same crossing beat again rather than a
    // flash of `On device` on the way back.
    val paused =
        RealtimeUiState(isGlassesRequested = true, isSourceLinking = true)
            .applying(GlassesSessionState.STARTED)
            .hearing(CaptureSourceKind.GLASSES)
            .applying(GlassesSessionState.PAUSED)
            .hearing(CaptureSourceKind.PHONE)
    assertEquals(SourceState.GLASSES_PAUSED, paused.sourceState)

    val resuming = paused.applying(GlassesSessionState.STARTED)
    assertEquals(SourceState.LINKING, resuming.sourceState)

    assertEquals(SourceState.GLASSES, resuming.hearing(CaptureSourceKind.GLASSES).sourceState)
  }

  @Test
  fun applying_started_doesNotMoveThePillOnItsOwn() {
    // The pill follows audio that actually arrived — a session whose microphone never
    // opens must not leave the screen claiming the glasses.
    val state = RealtimeUiState().applying(GlassesSessionState.STARTED)

    // The claim is what must not move — the pill may say it is still getting there, and
    // does, but it may not say `Glasses` until a chunk has come out of them.
    assertEquals(CaptureSourceKind.PHONE, state.sourceKind)
    assertTrue(state.sourceState != SourceState.GLASSES)
  }

  @Test
  fun aChunkFromTheGlassesIsWhatMovesThePill() {
    val state = RealtimeUiState().copy(sourceKind = CaptureSourceKind.GLASSES)

    assertEquals("Glasses", state.sourceLabel)
  }

  @Test
  fun applying_starting_staysOnTheLinkingBeat() {
    val state = RealtimeUiState(isSourceLinking = true).applying(GlassesSessionState.STARTING)

    assertTrue(!state.isGlassesSessionLive)
    assertTrue(state.isSourceLinking)
    assertEquals("Linking", state.sourceLabel)
  }

  @Test
  fun applying_paused_handsThePhotographBackToThePhone() {
    // A doff, or a tap on the temple. The shutter must stop routing to glasses that are
    // no longer running the session.
    val state =
        RealtimeUiState().applying(GlassesSessionState.STARTED).applying(GlassesSessionState.PAUSED)

    assertTrue(!state.isGlassesSessionLive)
    assertTrue(state.sourceNotice != null)
  }

  @Test
  fun applying_startedAfterPaused_clearsTheNotice() {
    val state =
        RealtimeUiState()
            .applying(GlassesSessionState.STARTED)
            .applying(GlassesSessionState.PAUSED)
            .applying(GlassesSessionState.STARTED)

    assertTrue(state.isGlassesSessionLive)
    assertNull(state.sourceNotice)
  }

  @Test
  fun applying_stopping_handsThePhotographBackQuietly() {
    val state =
        RealtimeUiState()
            .applying(GlassesSessionState.STARTED)
            .applying(GlassesSessionState.STOPPING)

    assertTrue(!state.isGlassesSessionLive)
    assertNull(state.sourceNotice)
  }

  @Test
  fun ending_midCrossing_saysTheSessionEndedRatherThanBlamingThePhoto() {
    // A long press on the temple ends the session under a photograph that is still crossing.
    // The row it would have landed on leaves the timeline, and the capture and the run's own
    // teardown both used to reach for the notice — so which sentence survived was a race, and
    // one of the two outcomes explained nothing at all.
    val state = RealtimeUiState(
        isGlassesRequested = true,
        isGlassesSessionLive = true,
        isCapturing = true,
    )

    val ended =
        state.ending(
            crossingLost = true,
            parting = "The glasses didn't answer. Try the pill again.",
        )

    assertEquals("The session ended before the photo arrived.", ended.sourceNotice)
    // And the switch is a switch again, with the phone on it.
    assertEquals(SourceState.ON_DEVICE, ended.sourceState)
    assertTrue(!ended.isCapturing)
    assertTrue(!ended.isGlassesRequested)
  }

  @Test
  fun ending_withNothingCrossing_carriesThePartingLine() {
    val state = RealtimeUiState(isGlassesRequested = true, isSourceLinking = true)

    val ended = state.ending(crossingLost = false, parting = "The glasses didn't answer.")

    assertEquals("The glasses didn't answer.", ended.sourceNotice)
    assertTrue(!ended.isSourceLinking)
  }

  @Test
  fun parting_forGlassesBehindOnTheirUpdate_namesTheUpdateRatherThanTheLink() {
    // The failure that lies: the glasses are on, in range, and the settings screen is
    // showing their battery — none of which needs anything from them but a connection,
    // where a session needs their software. "Didn't answer" sends somebody hunting a
    // Bluetooth fault that is not there.
    val line = RealtimeViewModel.partingFor(GlassesError.GlassesUpdateRequired)

    assertTrue(line.contains("Meta AI app"))
    assertTrue(!line.contains("didn't answer"))
  }

  @Test
  fun parting_forEveryOtherFailure_staysTheOneAnswerTheScreenCanGive() {
    // Nothing eligible, heat, power, a session already running — all of them are a link
    // that will not hold, and none of them is a sentence a wearer can act on differently.
    assertEquals(
        "The glasses didn't answer. Try the pill again.",
        RealtimeViewModel.partingFor(GlassesError.NotConnected),
    )
    assertEquals(
        "The glasses didn't answer. Try the pill again.",
        RealtimeViewModel.partingFor(GlassesError.TransferFailed),
    )
  }

  @Test
  fun ending_afterADeliberateHangUp_saysNothing() {
    // Hanging up is not a failure, and a run that ended with nothing in flight has nothing to
    // explain — including a pause it already recovered from.
    val state = RealtimeUiState(
        isGlassesRequested = true,
        sourceNotice = "The glasses paused the session — the phone has it until they resume.",
    )

    assertNull(state.ending(crossingLost = false, parting = null).sourceNotice)
  }

  @Test
  fun observeGlasses_aReachablePair_putsItsChargeOnTheGlance() = runBlocking {
    // The reading the session screen owes a wearer running the glasses' camera, microphone
    // and sensors at once: how much longer they can keep doing it.
    val glasses = FakeReachableGlassesSession()
    val viewModel = viewModel(audio = FakeAudioSource(), glassesSession = glasses)

    val observing = launch { viewModel.observeGlasses() }

    glasses.report(GlassesDeviceInfo(name = "Ray-Ban", isAvailable = true, batteryLevel = 82))

    // The collector starts inside that child coroutine, so nothing has landed when the job is
    // launched. Yield until it has, bounded, so a glance that never fills fails the assertion
    // below rather than hanging the suite.
    var spins = 0
    while (viewModel.uiState.value.glassesBattery == null && spins++ < 200) yield()
    observing.cancel()

    assertEquals(82, viewModel.uiState.value.glassesBattery)
  }

  @Test
  fun observeGlasses_aPairThatGoesOutOfReach_takesItsChargeWithIt() = runBlocking {
    // **The one rule this glance has.** A charge from before the glasses left the room is the
    // reading worth nothing, and a number that stays on screen after the link drops is a
    // sentence about a pair that is not there — so the glance goes quiet with the pair rather
    // than keeping the last thing it heard.
    val glasses = FakeReachableGlassesSession()
    val viewModel = viewModel(audio = FakeAudioSource(), glassesSession = glasses)

    val observing = launch { viewModel.observeGlasses() }

    glasses.report(GlassesDeviceInfo(name = "Ray-Ban", isAvailable = true, batteryLevel = 82))
    var spins = 0
    while (viewModel.uiState.value.glassesBattery == null && spins++ < 200) yield()

    // Same charge, same pair — only the link has gone.
    glasses.report(GlassesDeviceInfo(name = "Ray-Ban", isAvailable = false, batteryLevel = 82))
    while (viewModel.uiState.value.glassesBattery != null && spins++ < 400) yield()
    observing.cancel()

    assertNull(viewModel.uiState.value.glassesBattery)
    assertTrue(!viewModel.uiState.value.isGlassesAvailable)
  }

  @Test
  fun aPausedSessionCanStillBeHungUpOn() {
    // The device owns resume; the pill's only power over a paused session is to end it —
    // so it stays tappable even with the glasses out of reach.
    val state =
        RealtimeUiState(isGlassesAvailable = false, isGlassesRequested = true)
            .applying(GlassesSessionState.PAUSED)

    assertTrue(state.canToggleSource)
  }

  @Test
  fun startOnGlasses_beforeTheSessionOpens_reachesForTheGlassesTheMomentItDoes() = runBlocking {
    // The voice launch lands while the cover is still rising, so the ask comes before
    // the session's own opening reset — and must survive it. A launch spoken from the
    // glasses that opened a session on the phone would be the app ignoring the words
    // that started it.
    val viewModel = viewModel(
        audio = FakeAudioSource(openForMillis = 500),
        glassesSession = FakeGrantedGlassesSession(),
    )

    viewModel.startOnGlasses()
    val session = launch { viewModel.observeSession() }

    var spins = 0
    while (!viewModel.uiState.value.isGlassesRequested && spins++ < 400) yield()
    session.cancel()

    assertTrue(viewModel.uiState.value.isGlassesRequested)
  }

  @Test
  fun startOnGlasses_waitsForThePairToArrive() = runBlocking {
    // A voice launch lands while the pair is still listed as out of reach — its link comes
    // up a beat later. The run must hold on Linking rather than refuse, and go on to the
    // gates the moment the pair is reachable.
    val glasses = FakeReachableGlassesSession()
    val viewModel = viewModel(
        audio = FakeAudioSource(openForMillis = 5_000),
        glassesSession = glasses,
    )

    viewModel.startOnGlasses()
    val session = launch { viewModel.observeSession() }
    var spins = 0
    while (!viewModel.uiState.value.isSourceLinking && spins++ < 400) yield()

    // Still reaching, not refused.
    assertNull(viewModel.uiState.value.sourceNotice)
    assertEquals(SourceState.LINKING, viewModel.uiState.value.sourceState)

    // The pair arrives; this fake then refuses the grant, which is how the test sees that
    // the run moved on past the wait.
    glasses.report(GlassesDeviceInfo(name = "Ray-Ban", isAvailable = true))
    var waited = 0L
    while (viewModel.uiState.value.sourceNotice == null && waited < 2_000) {
      delay(10)
      waited += 10
    }
    session.cancel()

    assertTrue(viewModel.uiState.value.sourceNotice?.contains("aren't answering") == true)
    assertTrue(!viewModel.uiState.value.isSourceLinking)
  }

  @Test
  fun startOnGlasses_withTheCameraOpen_closesThePanelOnTheWayOver() {
    // The panel is the phone's viewfinder and the glasses have none — the same trade
    // the pill makes on the way over.
    val viewModel = viewModel(audio = FakeAudioSource())

    viewModel.openCamera()
    viewModel.startOnGlasses()

    assertTrue(!viewModel.uiState.value.isCameraOpen)
  }

  @Test
  fun startOnGlasses_whileAGlassesRunIsInFlight_leavesTheRunAlone() {
    // A spoken open only ever asks; a repeat finds the ask already made and changes
    // nothing — the difference from the pill, whose second tap is a hang-up. The camera
    // left open is the proof: a fresh ask would have closed it on the way over.
    val viewModel = viewModel(audio = FakeAudioSource())

    viewModel.startOnGlasses()
    viewModel.openCamera()
    viewModel.startOnGlasses()

    assertTrue(viewModel.uiState.value.isCameraOpen)
  }

  // ── Status ─────────────────────────────────────────────────────────────

  @Test
  fun initially_isOpening() {
    val state = RealtimeUiState()

    assertEquals(SessionStatus.OPENING, state.status)
    assertNull(state.failure)
    assertNull(state.failureMessage)
  }

  @Test
  fun listening_marksTheSessionListening() {
    val state = RealtimeUiState().listening()

    assertEquals(SessionStatus.LISTENING, state.status)
    assertNull(state.failure)
  }

  // ── Where the session is ───────────────────────────────────────────────

  @Test
  fun initially_hasNoFix() {
    assertTrue(!RealtimeUiState().isLocated)
  }

  @Test
  fun aFixMakesTheSessionLocated() {
    val state = RealtimeUiState(coordinate = Coordinate(39.142, -84.506))

    assertTrue(state.isLocated)
  }

  // ── Which way it is facing ─────────────────────────────────────────────

  @Test
  fun compassPoint_namesTheEightPoints() {
    assertEquals("N", compassPoint(0.0))
    assertEquals("NE", compassPoint(45.0))
    assertEquals("E", compassPoint(90.0))
    assertEquals("SE", compassPoint(135.0))
    assertEquals("S", compassPoint(180.0))
    assertEquals("SW", compassPoint(225.0))
    assertEquals("W", compassPoint(270.0))
    assertEquals("NW", compassPoint(315.0))
  }

  @Test
  fun compassPoint_takesTheNearestPoint() {
    // Each point owns the 45° centred on it, so the boundary between two sits at 22.5.
    assertEquals("N", compassPoint(22.4))
    assertEquals("NE", compassPoint(22.6))
    assertEquals("N", compassPoint(337.6))
  }

  @Test
  fun compassPoint_wrapsPastAFullTurn() {
    // A magnetometer reports whatever it last computed; wrapping is this function's job.
    assertEquals("N", compassPoint(360.0))
    assertEquals("NE", compassPoint(405.0))
    assertEquals("NW", compassPoint(-45.0))
  }

  @Test
  fun initially_hasNoBearing() {
    assertNull(RealtimeUiState().headingPoint)
  }

  @Test
  fun aHeadingReadsAsACompassPoint() {
    assertEquals("SW", RealtimeUiState(heading = 210.0).headingPoint)
  }

  // ── How high it is aiming ─────────────────────────────

  @Test
  fun gazeBand_namesTheFiveStrata() {
    assertEquals(GazeContext.OVERHEAD, gazeBand(80.0))
    assertEquals(GazeContext.CANOPY, gazeBand(40.0))
    assertEquals(GazeContext.HORIZON, gazeBand(0.0))
    assertEquals(GazeContext.UNDERSTORY, gazeBand(-30.0))
    assertEquals(GazeContext.GROUND, gazeBand(-80.0))
  }

  @Test
  fun gazeBand_isSymmetricAboutLevel() {
    // **The thresholds are the geometry, not one device's grip.** Looking 30° up and 30° down
    // are the same distance from level whatever is doing the looking, so the bands either side
    // of `Horizon` are mirror images. An instrument that reads below where it is actually
    // aimed — a phone tipped back to be read — corrects itself before it gets here, which is
    // why that correction is not visible in these numbers.
    assertEquals(GazeContext.HORIZON, gazeBand(-17.0))
    assertEquals(GazeContext.UNDERSTORY, gazeBand(-18.0))
    assertEquals(GazeContext.HORIZON, gazeBand(17.0))
    assertEquals(GazeContext.CANOPY, gazeBand(18.0))

    // Mirrored elevations land in mirrored strata, which is the property the name claims.
    assertEquals(GazeContext.CANOPY, gazeBand(30.0))
    assertEquals(GazeContext.UNDERSTORY, gazeBand(-30.0))
    assertEquals(GazeContext.OVERHEAD, gazeBand(60.0))
    assertEquals(GazeContext.GROUND, gazeBand(-60.0))
  }

  @Test
  fun gazeBand_survivesPastThePoles() {
    // A fused sensor reading arrives as whatever it last computed; clamping is not the caller's
    // job.
    assertEquals(GazeContext.OVERHEAD, gazeBand(120.0))
    assertEquals(GazeContext.GROUND, gazeBand(-120.0))
  }

  @Test
  fun initially_hasNoBand() {
    assertNull(RealtimeUiState().elevationBand)
  }

  @Test
  fun anElevationReadsAsAStratum() {
    assertEquals(GazeContext.CANOPY, RealtimeUiState(elevation = 30.0).elevationBand)
  }

  // ── Failure ────────────────────────────────────────────────────────────

  @Test
  fun failed_withAccessDenied_pointsAtSettings() {
    val state = RealtimeUiState().failed(AudioCaptureError.AccessDenied)

    assertEquals(SessionStatus.FAILED, state.status)
    assertEquals(
        "Microphone access is off. Turn it back on in Settings to start a session.",
        state.failureMessage,
    )
  }

  @Test
  fun failed_withInterrupted_saysAnotherAppTookTheMicrophone() {
    val state = RealtimeUiState().failed(AudioCaptureError.Interrupted)

    assertEquals(
        "Another app took the microphone. Close the session and start it again.",
        state.failureMessage,
    )
  }

  @Test
  fun failed_withAnUnknownError_reportsUnavailable() {
    val state = RealtimeUiState().failed(IllegalStateException("something else entirely"))

    assertEquals(AudioCaptureError.Unavailable, state.failure)
    assertEquals("There's no microphone to listen with.", state.failureMessage)
  }

  // ── The clock ──────────────────────────────────────────────────────────

  @Test
  fun sessionStamp_readsAsAStopwatch() {
    assertEquals("0:00", sessionStamp(0.0))
    assertEquals("0:09", sessionStamp(9.8))
    assertEquals("1:07", sessionStamp(67.0))
    assertEquals("12:00", sessionStamp(720.0))
  }

  @Test
  fun sessionStamp_neverGoesBackwardsPastZero() {
    // The strip clamps its window rather than the clock, so a negative can reach here.
    assertEquals("0:00", sessionStamp(-3.0))
  }

  // ── The timeline ───────────────────────────────────────────────────────

  @Test
  fun adding_keepsEventsInTimeOrder() {
    val session =
        RealtimeSession(startedAt = 0L)
            .adding(SessionEvent.Speech(at = 9.0, text = "green with a yellow belly"))
            .adding(
                SessionEvent.Bird(
                    at = 4.0,
                    speciesId = "american-robin",
                    commonName = "American Robin",
                    confidence = 0.87f,
                ),
            )

    // A photo is stamped when the shutter fires and a detection when the detector speaks;
    // nothing guarantees they arrive in the order they happened.
    assertEquals(listOf(4.0, 9.0), session.events.map { it.at })
  }

  @Test
  fun eventsBetween_takesTheWindowInclusive() {
    val session =
        RealtimeSession(startedAt = 0L)
            .adding(SessionEvent.Speech(at = 2.0, text = "before"))
            .adding(SessionEvent.Speech(at = 4.0, text = "on the edge"))
            .adding(SessionEvent.Speech(at = 9.0, text = "after"))

    val visible = session.eventsBetween(from = 4.0, to = 8.0)

    assertEquals(listOf("on the edge"), visible.map { (it as SessionEvent.Speech).text })
  }

  @Test
  fun finding_at_stampsItIntoAnEvent() {
    val finding = SessionFinding.Bird("green-jay", "Green Jay", 0.91f)

    val event = finding.at(18.5)

    assertEquals(SessionEvent.Bird(18.5, "green-jay", "Green Jay", 0.91f), event)
  }

  @Test
  fun finding_at_stampsAnAnswer() {
    val finding = SessionFinding.Answer("Green Jay or Blue Jay?")

    val event = finding.at(9.0)

    assertEquals(SessionEvent.Answer(9.0, "Green Jay or Blue Jay?"), event)
  }

  @Test
  fun resolvingPhoto_fillsInTheAnswerWithoutMovingTheRow() {
    // Found by capture index rather than by stamp: the stamp is an identity the collision
    // nudge is free to move, and the index is the number the answer was asked for under.
    val session =
        RealtimeSession(startedAt = 0L)
            .adding(photoEvent(at = 4.0, index = 0))
            .adding(SessionEvent.Speech(at = 6.0, text = "after"))
            .adding(photoEvent(at = 9.0, index = 1))

    val resolved =
        session.resolvingPhoto(
            index = 1,
            identification = PhotoIdentification.Bird("green-jay", "Green Jay", 0.92f),
        )

    assertEquals(listOf(4.0, 6.0, 9.0), resolved.events.map { it.at })
    assertEquals(
        PhotoIdentification.Pending,
        (resolved.events[0] as SessionEvent.Photo).identification,
    )
    assertEquals(
        PhotoIdentification.Bird("green-jay", "Green Jay", 0.92f),
        (resolved.events[2] as SessionEvent.Photo).identification,
    )
  }

  @Test
  fun resolvingPhoto_forACaptureThatIsGone_changesNothing() {
    val session = RealtimeSession(startedAt = 0L).adding(photoEvent(at = 4.0, index = 0))

    val resolved = session.resolvingPhoto(index = 7, identification = null)

    assertEquals(
        PhotoIdentification.Pending,
        (resolved.events[0] as SessionEvent.Photo).identification,
    )
  }

  @Test
  fun resolvingPhoto_fillsThePictureIntoTheRowThatWasWaitingForIt() {
    // The glasses' crossing: the row is stamped at the press with no picture in it, and
    // the photograph drops into that row rather than arriving as one of its own.
    val session =
        RealtimeSession(startedAt = 0L)
            .adding(crossingPhotoEvent(at = 4.0, index = 0))
            .adding(SessionEvent.Speech(at = 6.0, text = "after"))

    val landed = session.resolvingPhoto(index = 0, image = bitmap())

    assertEquals(listOf(4.0, 6.0), landed.events.map { it.at })
    assertNotNull((landed.events[0] as SessionEvent.Photo).image)
  }

  @Test
  fun discardingPhoto_takesAwayTheRowOfACrossingThatFailed() {
    // A photograph that never arrived is not a thing that happened during the session.
    val session =
        RealtimeSession(startedAt = 0L)
            .adding(photoEvent(at = 2.0, index = 0))
            .adding(crossingPhotoEvent(at = 4.0, index = 1))
            .adding(SessionEvent.Speech(at = 6.0, text = "after"))

    val dropped = session.discardingPhoto(index = 1)

    assertEquals(listOf(2.0, 6.0), dropped.events.map { it.at })
  }

  @Test
  fun discardingPhoto_forACaptureThatIsGone_changesNothing() {
    val session = RealtimeSession(startedAt = 0L).adding(photoEvent(at = 4.0, index = 0))

    val dropped = session.discardingPhoto(index = 7)

    assertEquals(listOf(4.0), dropped.events.map { it.at })
  }

  @Test
  fun adding_nudgesAnExactStampCollisionForward() {
    // Stamps double as the log's row identities, so two events cannot share one — a
    // photo and its zero-delay scripted response land inside the same millisecond.
    val session =
        RealtimeSession(startedAt = 0L)
            .adding(SessionEvent.Speech(at = 4.0, text = "first"))
            .adding(SessionEvent.Speech(at = 4.0, text = "second"))

    assertEquals(listOf(4.0, 4.001), session.events.map { it.at })
  }

  // ── The view model ─────────────────────────────────────────────────────

  @Test
  fun viewModel_takesItsSourceKindFromTheAudioSource() {
    val viewModel = viewModel(audio = FakeAudioSource(kind = CaptureSourceKind.GLASSES))

    assertEquals(CaptureSourceKind.GLASSES, viewModel.uiState.value.sourceKind)
  }

  @Test
  fun observeSession_whenTheMicrophoneFails_reportsTheFailure() = runBlocking {
    val viewModel = viewModel(audio = FakeAudioSource(failure = AudioCaptureError.Interrupted))

    viewModel.observeSession()

    assertEquals(SessionStatus.FAILED, viewModel.uiState.value.status)
    assertEquals(AudioCaptureError.Interrupted, viewModel.uiState.value.failure)
    assertEquals(0, viewModel.sonogram.count)
  }

  @Test
  fun observeSession_stampsTheSessionWhereItStarted() = runBlocking {
    val here = Coordinate(39.142, -84.506)
    val viewModel = viewModel(audio = FakeAudioSource(openForMillis = 30), fix = here)

    viewModel.observeSession()

    assertEquals(here, viewModel.uiState.value.coordinate)
    assertTrue(viewModel.uiState.value.isLocated)
  }

  @Test
  fun observeSession_withNoFix_leavesTheSessionUnlocated() = runBlocking {
    val viewModel = viewModel(audio = FakeAudioSource(openForMillis = 30), fix = null)

    viewModel.observeSession()

    assertNull(viewModel.uiState.value.coordinate)
    assertTrue(!viewModel.uiState.value.isLocated)
  }

  @Test
  fun observeSession_followsTheCompass() = runBlocking {
    val viewModel = viewModel(audio = FakeAudioSource(openForMillis = 30), bearings = listOf(110.0))

    viewModel.observeSession()

    assertEquals(110.0, viewModel.uiState.value.heading!!, 0.0001)
    assertEquals("E", viewModel.uiState.value.headingPoint)
  }

  @Test
  fun observeSession_withNoCompass_leavesTheBearingUnread() = runBlocking {
    val viewModel = viewModel(audio = FakeAudioSource(openForMillis = 30), bearings = emptyList())

    viewModel.observeSession()

    assertNull(viewModel.uiState.value.headingPoint)
  }

  @Test
  fun observeSession_followsTheTilt() = runBlocking {
    val viewModel =
        viewModel(audio = FakeAudioSource(openForMillis = 30), elevations = listOf(40.0))

    viewModel.observeSession()

    assertEquals(40.0, viewModel.uiState.value.elevation!!, 0.0001)
    assertEquals(GazeContext.CANOPY, viewModel.uiState.value.elevationBand)
  }

  @Test
  fun observeSession_withNoTilt_leavesTheBandUnread() = runBlocking {
    val viewModel = viewModel(audio = FakeAudioSource(openForMillis = 30), elevations = emptyList())

    viewModel.observeSession()

    assertNull(viewModel.uiState.value.elevationBand)
  }

  // ── The session clock ──────────────────────────────────────────────────

  @Test
  fun placement_leavesOrdinaryJitterAlone() {
    // A microphone handing a buffer over a beat late is not a gap, and closing up a column or
    // two would cost more than it bought.
    assertEquals(495, placement(clockColumn = 500, written = 495))
    assertEquals(484, placement(clockColumn = 500, written = 484))
  }

  @Test
  fun placement_skipsForwardAfterAGap() {
    // Far enough behind and the microphone genuinely stopped: the silence belongs on the strip
    // at the second it happened.
    assertEquals(500, placement(clockColumn = 500, written = 100))
  }

  @Test
  fun placement_neverMovesTheWriteHeadBack() {
    // Audio running ahead of the clock is the harmless direction, and a written column is a
    // column that happened.
    assertEquals(500, placement(clockColumn = 400, written = 500))
  }

  @Test
  fun observeSession_startsTheClockOver() = runBlocking {
    val clock = FakeSessionClock()
    clock.advance(40.0)
    val viewModel = viewModel(audio = FakeAudioSource(openForMillis = 30), clock = clock)

    viewModel.observeSession()

    // A second visit is a new session, not the tail of the last one.
    assertEquals(1, clock.starts)
  }

  @Test
  fun observeSession_withNoAudioAtAll_stillMovesTheTimeline() = runBlocking {
    // The regression the clock exists for. While elapsed was `columns / 62.5`, a session that
    // heard nothing had a strip frozen at second zero — and so did one whose microphone
    // dropped for four seconds mid-run, which is what failover will do routinely.
    val clock = FakeSessionClock()
    clock.advance(12.5)
    val viewModel = viewModel(audio = FakeAudioSource(openForMillis = 60), clock = clock)

    viewModel.observeSession()

    assertEquals(0, viewModel.sonogram.count)
    assertEquals(12.5, viewModel.elapsed.value, 0.0001)
  }

  @Test
  fun observeSession_stampsAFindingFromTheClock() = runBlocking {
    // Not from the sonogram: with no audio the column count is zero, and a bird stamped at
    // 0:00 four seconds into a dropout is the timeline lying about when it heard something.
    val clock = FakeSessionClock()
    clock.advance(18.5)
    val viewModel = viewModel(
        audio = FakeAudioSource(openForMillis = 30),
        findings = listOf(SessionFinding.Bird("green-jay", "Green Jay", 0.91f)),
        clock = clock,
    )

    viewModel.observeSession()

    assertEquals(listOf(18.5), viewModel.uiState.value.session.events.map { it.at })
  }

  // The armed preset used to be read into the ui state for a line under the header, and
  // both went: what the Director is playing is read on its own page. The cues it fires are
  // covered by the lanes below — a preset that is armed proves it by answering.

  // ── A photo, and the answer that lands on it ───────────────────────────

  @Test
  fun capturePhoto_landsThePhotoWaitingForItsAnswer() = runBlocking {
    // The wait is set at the shutter, not when the answer arrives: the row has to be
    // showing that something is coming from the moment the picture is on the log.
    val viewModel = viewModel(
        audio = FakeAudioSource(),
        armed = WaitingPhotoPreset,
        frames = listOf(frame()),
    )
    viewModel.observeCamera()

    viewModel.capturePhoto()

    val photo = viewModel.uiState.value.session.events.single() as SessionEvent.Photo
    assertEquals(0, photo.index)
    assertEquals(PhotoIdentification.Pending, photo.identification)
  }

  @Test
  fun capturePhoto_pressedAgainDuringTheFlashSettle_takesOnePhotograph() = runBlocking {
    // The armed flash holds the panel open across [FlashSettleMillis] so the light is seen
    // coming on, which used to leave the shutter live across those milliseconds: two presses
    // lit the torch twice, raced for when to put it out, and landed two photographs of one
    // moment. The guard is the capture itself, not a timer.
    val camera = FakeCameraSource(listOf(frame()))
    val viewModel = viewModel(audio = FakeAudioSource(), camera = camera)
    viewModel.observeCamera()
    viewModel.toggleFlash()

    viewModel.capturePhoto()
    assertTrue(viewModel.uiState.value.isCapturing)

    // The second press, inside the settle, is dropped rather than queued: the light is asked
    // for once, and only the one settle is running to put it out again.
    viewModel.capturePhoto()

    assertEquals(1, camera.torchOns)
  }

  @Test
  fun routeGlassesInput_aPressOnTheGlasses_takesTheSamePhotographTheShutterDoes() = runBlocking {
    // The button on the temple and the button on screen are one act: the press is routed
    // through [RealtimeViewModel.capturePhoto] rather than at the glasses directly, so the
    // choice of which device photographs — and the one-at-a-time guard — are decided once,
    // for both.
    val input = FakeGlassesInput()
    val viewModel = viewModel(
        audio = FakeAudioSource(),
        frames = listOf(frame()),
        glassesInput = input,
    )
    viewModel.observeCamera()

    val routing = launch { viewModel.routeGlassesInput() }

    // The collector starts inside that child coroutine, so it has not subscribed yet when
    // the job is launched — and a press with nobody listening is dropped by design. Yield
    // until it has, bounded, so a press that never routes fails the assertion below rather
    // than hanging the suite.
    var spins = 0
    while (!input.isListening && spins++ < 100) yield()

    input.press()

    while (viewModel.uiState.value.session.events.isEmpty() && spins++ < 200) yield()
    routing.cancel()

    val photo = viewModel.uiState.value.session.events.single() as SessionEvent.Photo
    assertEquals(0, photo.index)
    // The phone took it, because no glasses session is running — which is the routing under
    // test. A press is a request for *a* photograph, not for a photograph from the glasses.
    assertEquals(CaptureSource.PHONE, photo.source)
  }

  @Test
  fun routeGlassesInput_aBackOnTheGlasses_endsTheRunTheStopOnScreenWouldHaveEnded() = runBlocking {
    // Back is taken out of the system's hands so that a swipe stops ending the run by ending
    // the app on the glasses — a stop with no review and nothing on screen to explain it.
    // Taking it leaves the gesture owing the wearer an answer, and the answer is the stop
    // they would otherwise have picked the phone up to press.
    val clock = FakeSessionClock()
    clock.advance(42.0)
    val input = FakeGlassesInput()
    val viewModel = viewModel(audio = FakeAudioSource(), clock = clock, glassesInput = input)

    val routing = launch { viewModel.routeGlassesInput() }

    // The collector starts inside that child coroutine, so it has not subscribed yet when the
    // job is launched — and a gesture with nobody listening is dropped by design. Yield until
    // it has, bounded, so a swipe that never routes fails the assertion below rather than
    // hanging the suite.
    var spins = 0
    while (!input.isListening && spins++ < 100) yield()

    input.swipeBack()

    while (!viewModel.uiState.value.isReviewing && spins++ < 200) yield()
    routing.cancel()

    assertTrue(viewModel.uiState.value.isReviewing)
    // The same landing the vermilion stop makes, clock reading and all — not a shortcut that
    // happens to leave the screen looking similar.
    assertEquals(42.0, viewModel.uiState.value.review!!.durationSeconds, 0.0001)
  }

  @Test
  fun capturePhoto_withNothingScripted_leavesThePhotoWaitingForNothing() = runBlocking {
    // Nothing armed, nothing composing. A row that spun forever beside this photograph
    // would be the screen promising an answer nobody is writing.
    val viewModel = viewModel(audio = FakeAudioSource(), frames = listOf(frame()))
    viewModel.observeCamera()

    viewModel.capturePhoto()

    val photo = viewModel.uiState.value.session.events.single() as SessionEvent.Photo
    assertNull(photo.identification)
  }

  @Test
  fun deliverPhotoResponse_landsTheScriptedBirdOnItsPhoto() = runBlocking {
    val clock = FakeSessionClock()
    clock.advance(10.0)
    val viewModel = viewModel(audio = FakeAudioSource(), frames = listOf(frame()), clock = clock)
    viewModel.observeCamera()
    viewModel.capturePhoto()

    viewModel.deliverPhotoResponse(
        DemoPhotoResponse(
            id = "photo-1",
            result = DemoResult.Species("green-jay", 0.92),
            caption = "Green Jay",
            delayMillis = 0,
        ),
        sessionKey = viewModel.uiState.value.session.startedAt,
        photoIndex = 0,
    )

    // One row, not two: the answer is on the capture that produced it.
    val photo = viewModel.uiState.value.session.events.single() as SessionEvent.Photo
    assertEquals(10.0, photo.at, 0.0001)
    assertEquals(
        PhotoIdentification.Bird("green-jay", "Green Jay", 0.92f),
        photo.identification,
    )
  }

  @Test
  fun deliverPhotoResponse_pastTheEnd_answersOnThePhotoWithTheFixedCaption() = runBlocking {
    val viewModel = viewModel(audio = FakeAudioSource(), frames = listOf(frame()))
    viewModel.observeCamera()
    viewModel.capturePhoto()

    viewModel.deliverPhotoResponse(
        DemoPhotoResponse.pastTheEnd,
        sessionKey = viewModel.uiState.value.session.startedAt,
        photoIndex = 0,
    )

    val photo = viewModel.uiState.value.session.events.single() as SessionEvent.Photo
    assertEquals(
        PhotoIdentification.Words(DemoPhotoResponse.pastTheEnd.caption),
        photo.identification,
    )
  }

  @Test
  fun deliverPhotoResponse_dropsABirdTheCatalogCannotName() = runBlocking {
    // The same silence the Director's ambient path keeps — except that here it has to be
    // delivered: the photo is on the log with its dots running, and this is what stops them.
    val viewModel = viewModel(
        audio = FakeAudioSource(),
        armed = WaitingPhotoPreset,
        frames = listOf(frame()),
    )
    viewModel.observeCamera()
    viewModel.capturePhoto()

    viewModel.deliverPhotoResponse(
        DemoPhotoResponse(
            id = "ghost",
            result = DemoResult.Species("no-such-bird", 0.9),
            caption = "",
            delayMillis = 0,
        ),
        sessionKey = viewModel.uiState.value.session.startedAt,
        photoIndex = 0,
    )

    val photo = viewModel.uiState.value.session.events.single() as SessionEvent.Photo
    assertNull(photo.identification)
  }

  @Test
  fun deliverPhotoResponse_afterTheStop_landsNothing() = runBlocking {
    // A response composed across the stop lands nowhere: the review shows what the
    // session held when it ended, not what a delay was still carrying.
    val viewModel = viewModel(
        audio = FakeAudioSource(),
        armed = WaitingPhotoPreset,
        frames = listOf(frame()),
    )
    viewModel.observeCamera()
    viewModel.capturePhoto()
    val sessionKey = viewModel.uiState.value.session.startedAt
    viewModel.stopSession()

    viewModel.deliverPhotoResponse(
        DemoPhotoResponse.pastTheEnd,
        sessionKey = sessionKey,
        photoIndex = 0,
    )

    val photo = viewModel.uiState.value.session.events.single() as SessionEvent.Photo
    assertEquals(PhotoIdentification.Pending, photo.identification)
  }

  @Test
  fun deliverAnswer_landsTheAnswerWithItsBird() = runBlocking {
    val clock = FakeSessionClock()
    clock.advance(12.0)
    val viewModel = viewModel(audio = FakeAudioSource(), clock = clock)

    viewModel.deliverAnswer(GreenJayQuestion, sessionKey = 0L)

    val answer = viewModel.uiState.value.session.events.single() as SessionEvent.Answer
    assertEquals("That's likely a Green Jay.", answer.text)
    assertEquals("it's green with a yellow belly", answer.question)
    assertEquals("green-jay", answer.speciesId)
    assertEquals("Green Jay", answer.commonName)
  }

  // ── The watcher's own voice ────────────────────────────────────────────

  @Test
  fun answerAloud_whenTheUtteranceMatchesAPrompt_landsTheAnswer() = runBlocking {
    // The whole exchange: the glasses hear a description, the words land on the log, and the
    // authored reply follows them.
    val clock = FakeSessionClock()
    clock.advance(12.0)
    val viewModel = viewModel(audio = FakeAudioSource(), armed = GreenJayPreset, clock = clock)

    viewModel.answerAloud("It's green with a yellow belly")

    val events = viewModel.uiState.value.session.events
    assertEquals("It's green with a yellow belly", (events.first() as SessionEvent.Speech).text)
    val answer = events.last() as SessionEvent.Answer
    assertEquals("That's likely a Green Jay.", answer.text)
    // The watcher's own words, not the authored prompt that happened to match them.
    assertEquals("It's green with a yellow belly", answer.question)
    assertEquals("green-jay", answer.speciesId)
  }

  @Test
  fun answerAloud_whenNothingMatches_landsTheUnmatchedLine() = runBlocking {
    // The other half of the bargain. A miss is not silence: the app heard something, could
    // not place it, and says the preset's own line about it.
    val viewModel = viewModel(audio = FakeAudioSource(), armed = GreenJayPreset)

    viewModel.answerAloud("what a lovely afternoon")

    val events = viewModel.uiState.value.session.events
    assertEquals("what a lovely afternoon", (events.first() as SessionEvent.Speech).text)
    val answer = events.last() as SessionEvent.Answer
    assertEquals("Sorry — didn't catch that.", answer.text)
    assertEquals("what a lovely afternoon", answer.question)
    assertNull(answer.speciesId)
  }

  @Test
  fun answerAloud_whenTheUnmatchedLineIsBlank_saysNothing() = runBlocking {
    // The operator's off switch. A run listens for its whole length and hears the presenter
    // talking to the room too — clearing the line is how those sentences stop being answered,
    // and the words still land on the log.
    val quiet = GreenJayPreset.copy(unmatchedQuestion = "   ")
    val viewModel = viewModel(audio = FakeAudioSource(), armed = quiet)

    viewModel.answerAloud("what a lovely afternoon")

    val events = viewModel.uiState.value.session.events
    assertEquals("what a lovely afternoon", (events.single() as SessionEvent.Speech).text)
  }

  @Test
  fun answerAloud_whenNothingIsArmed_logsTheWordsAndSaysNothing() = runBlocking {
    // Identification switched off is a legal, intended state — the session listens and the
    // app never speaks. There is no preset, so there is no "didn't catch that" line either.
    val viewModel = viewModel(audio = FakeAudioSource())

    viewModel.answerAloud("it's green with a yellow belly")

    val events = viewModel.uiState.value.session.events
    assertEquals("it's green with a yellow belly", (events.single() as SessionEvent.Speech).text)
  }

  @Test
  fun answerAloud_whileTheAppIsTalking_landsNothing() = runBlocking {
    // **The loop this exists to stop.** The app answers through the glasses speaker, the
    // glasses microphone hears it, and the recogniser cannot tell the two voices apart — so
    // an unguarded lane earns its own "didn't catch that" line an answer at a time, for ever.
    val voice = FakeSpokenOutput(isSpeaking = true)
    val viewModel = viewModel(
        audio = FakeAudioSource(),
        armed = GreenJayPreset,
        spokenOutput = voice,
    )

    viewModel.answerAloud("Sorry, didn't catch that.")

    assertTrue(viewModel.uiState.value.session.events.isEmpty())
    assertTrue(voice.said.isEmpty())
  }

  @Test
  fun answerAloud_inTheBeatAfterTheAppStopsTalking_landsNothing() = runBlocking {
    // The tail, which is the half a plain `isSpeaking` check misses: the recogniser holds an
    // utterance open until it has heard silence, so the echo of a line lands *after* the line
    // has finished playing and the voice already reads as quiet.
    val voice = FakeSpokenOutput()
    val viewModel = viewModel(
        audio = FakeAudioSource(),
        armed = GreenJayPreset,
        spokenOutput = voice,
    )

    // What every answered question does on its way out.
    viewModel.deliverAnswer(GreenJayQuestion, sessionKey = 0L)
    val afterTheAnswer = viewModel.uiState.value.session.events.size
    viewModel.answerAloud("That's likely a Green Jay.")

    assertEquals(afterTheAnswer, viewModel.uiState.value.session.events.size)
  }

  @Test
  fun answerAloud_onceTheEchoHasPassed_isHeardAgain() = runBlocking {
    // And the lane opens again, or the guard would be a mute switch rather than a window.
    val clock = FakeSessionClock()
    val voice = FakeSpokenOutput()
    val viewModel = viewModel(
        audio = FakeAudioSource(),
        armed = GreenJayPreset,
        clock = clock,
        spokenOutput = voice,
    )

    viewModel.deliverAnswer(GreenJayQuestion, sessionKey = 0L)
    clock.advance(5.0)
    viewModel.answerAloud("green bird yellow belly")

    val heard =
        viewModel.uiState.value.session.events.filterIsInstance<SessionEvent.Speech>().map {
          it.text
        }
    assertEquals(listOf("green bird yellow belly"), heard)
  }

  @Test
  fun answerAloud_anEchoArrivingLate_isStillRecognisedAsTheAppsOwnVoice() = runBlocking {
    // **The loop that survived the timing window.** The app answered, the tail expired, and
    // the echo landed five seconds later — a recogniser holds an utterance open until it has
    // heard silence, so how late an echo arrives is not something a number can cover.
    // Recorded exactly as the glasses misheard it: "Green Jay" came back as "green day".
    val clock = FakeSessionClock()
    val viewModel = viewModel(
        audio = FakeAudioSource(),
        armed = GreenJayPreset,
        clock = clock,
    )

    viewModel.deliverAnswer(GreenJayQuestion, sessionKey = 0L)
    val afterTheAnswer = viewModel.uiState.value.session.events.size
    clock.advance(5.0)
    viewModel.answerAloud("That's likely a green day.")

    assertEquals(afterTheAnswer, viewModel.uiState.value.session.events.size)
  }

  @Test
  fun answerAloud_aTwoWordRepeatOfTheAppsWords_isHeard() = runBlocking {
    // **Where the check stops, and it is a real cost.** An utterance made only of words the
    // app just said is genuinely ambiguous — the wearer picking the bird's name back up is
    // the same string as the echo of it — so the line is drawn at length. Under three words
    // is let through and answered; a longer verbatim repeat is taken for the echo it usually
    // is. That trades a rare lost question for a loop, which is the right way round.
    val clock = FakeSessionClock()
    val viewModel = viewModel(
        audio = FakeAudioSource(),
        armed = GreenJayPreset,
        clock = clock,
    )

    viewModel.deliverAnswer(GreenJayQuestion, sessionKey = 0L)
    clock.advance(5.0)
    viewModel.answerAloud("green jay")

    val heard =
        viewModel.uiState.value.session.events.filterIsInstance<SessionEvent.Speech>().map {
          it.text
        }
    assertEquals(listOf("green jay"), heard)
  }

  @Test
  fun answerAloud_longAfterTheAppSpoke_isHeardAgain() = runBlocking {
    // A phrase stops being suspicious once enough time has passed for the wearer to have
    // chosen it themselves, or the guard would be a permanent ban on the app's own vocabulary.
    val clock = FakeSessionClock()
    val viewModel = viewModel(
        audio = FakeAudioSource(),
        armed = GreenJayPreset,
        clock = clock,
    )

    viewModel.deliverAnswer(GreenJayQuestion, sessionKey = 0L)
    clock.advance(30.0)
    viewModel.answerAloud("That's likely a green day.")

    val heard =
        viewModel.uiState.value.session.events.filterIsInstance<SessionEvent.Speech>().map {
          it.text
        }
    assertEquals(listOf("That's likely a green day."), heard)
  }

  @Test
  fun answerAloud_whenNothingWasSaid_landsNothing() = runBlocking {
    // A recogniser that commits to an empty utterance is not the watcher asking anything,
    // and answering it would put "didn't catch that" on the log for silence.
    val viewModel = viewModel(audio = FakeAudioSource(), armed = GreenJayPreset)

    viewModel.answerAloud("   ")

    assertTrue(viewModel.uiState.value.session.events.isEmpty())
  }

  @Test
  fun listenForQuestions_aPartialUtterance_isNotAnswered() = runBlocking {
    // The same sentence lands several times as it develops, and matching on one of those
    // would fire an answer to half a question.
    val speech = FakeGlassesSpeech()
    val viewModel = viewModel(
        audio = FakeAudioSource(),
        armed = GreenJayPreset,
        glassesSpeech = speech,
    )

    val listening = launch { viewModel.listenForQuestions() }
    while (!speech.isListening) yield()
    speech.say("It's green with a yellow belly", isFinal = false)
    yield()

    assertTrue(viewModel.uiState.value.session.events.isEmpty())
    listening.cancel()
  }

  @Test
  fun listenForQuestions_aFinalUtterance_reachesTheDirector() = runBlocking {
    // The lane end to end: the recogniser commits, and the exchange lands.
    val speech = FakeGlassesSpeech()
    val viewModel = viewModel(
        audio = FakeAudioSource(),
        armed = GreenJayPreset,
        glassesSpeech = speech,
    )

    val listening = launch { viewModel.listenForQuestions() }
    while (!speech.isListening) yield()
    speech.say("It's green with a yellow belly")
    while (viewModel.uiState.value.session.events.size < 2) yield()

    val answer = viewModel.uiState.value.session.events.last() as SessionEvent.Answer
    assertEquals("That's likely a Green Jay.", answer.text)
    listening.cancel()
  }

  // ── The display on the glasses ─────────────────────────────────────────

  @Test
  fun observeSession_aBirdFinding_putsItsGalleryOnTheDisplay() = runBlocking {
    val display = FakeGlassesDisplay()
    val viewModel = viewModel(
        audio = FakeAudioSource(openForMillis = 30),
        findings = listOf(SessionFinding.Bird("green-jay", "Green Jay", 0.91f)),
        glassesDisplay = display,
    )

    viewModel.observeSession()

    yield()
    assertEquals(listOf("green-jay"), display.shown)
  }

  @Test
  fun deliverPhotoResponse_putsTheScriptedBirdsGalleryOnTheDisplay() = runBlocking {
    val display = FakeGlassesDisplay()
    val viewModel = viewModel(
        audio = FakeAudioSource(),
        frames = listOf(frame()),
        glassesDisplay = display,
    )
    viewModel.observeCamera()
    viewModel.capturePhoto()

    viewModel.deliverPhotoResponse(
        DemoPhotoResponse(
            id = "photo-1",
            result = DemoResult.Species("green-jay", 0.92),
            caption = "Green Jay",
            delayMillis = 0,
        ),
        sessionKey = viewModel.uiState.value.session.startedAt,
        photoIndex = 0,
    )

    yield()
    assertEquals(listOf("green-jay"), display.shown)
  }

  @Test
  fun deliverPhotoResponse_wordsLeaveTheDisplayAlone() = runBlocking {
    // Words are the whole of what the app has to say — there is no bird to page through,
    // and a gallery for nobody would replace whatever the wearer was already shown.
    val display = FakeGlassesDisplay()
    val viewModel = viewModel(
        audio = FakeAudioSource(),
        frames = listOf(frame()),
        glassesDisplay = display,
    )
    viewModel.observeCamera()
    viewModel.capturePhoto()

    viewModel.deliverPhotoResponse(
        DemoPhotoResponse.pastTheEnd,
        sessionKey = viewModel.uiState.value.session.startedAt,
        photoIndex = 0,
    )

    yield()
    assertTrue(display.shown.isEmpty())
  }

  @Test
  fun deliverAnswer_putsTheBirdsGalleryOnTheDisplay() = runBlocking {
    val display = FakeGlassesDisplay()
    val viewModel = viewModel(audio = FakeAudioSource(), glassesDisplay = display)

    viewModel.deliverAnswer(GreenJayQuestion, sessionKey = 0L)

    yield()
    assertEquals(listOf("green-jay"), display.shown)
  }

  @Test
  fun stopSession_takesTheGalleryDown() = runBlocking {
    val display = FakeGlassesDisplay()
    val viewModel = viewModel(audio = FakeAudioSource(), glassesDisplay = display)

    viewModel.stopSession()

    yield()
    assertEquals(1, display.clearCount)
  }

  @Test
  fun observeGlasses_aPairWithAPanel_offersTheSend() = runBlocking {
    // The offer is the whole of what this flag is for — a row on the log becomes pressable
    // only once there is glass for the press to reach.
    val glasses = FakeReachableGlassesSession()
    val viewModel = viewModel(audio = FakeAudioSource(), glassesSession = glasses)

    val observing = launch { viewModel.observeGlasses() }

    glasses.report(
        GlassesDeviceInfo(name = "Ray-Ban Display", isAvailable = true, hasDisplay = true),
    )
    var spins = 0
    while (!viewModel.uiState.value.isDisplayAvailable && spins++ < 200) yield()
    observing.cancel()

    assertTrue(viewModel.uiState.value.isDisplayAvailable)
  }

  @Test
  fun observeGlasses_aPanelThatGoesOutOfReach_withdrawsTheSend() = runBlocking {
    // The same rule the charge keeps: a display on a pair that has left the room is a panel
    // nothing can reach, and a row still inviting a tap would be promising a send that dies
    // in the repository.
    val glasses = FakeReachableGlassesSession()
    val viewModel = viewModel(audio = FakeAudioSource(), glassesSession = glasses)

    val observing = launch { viewModel.observeGlasses() }

    glasses.report(
        GlassesDeviceInfo(name = "Ray-Ban Display", isAvailable = true, hasDisplay = true),
    )
    var spins = 0
    while (!viewModel.uiState.value.isDisplayAvailable && spins++ < 200) yield()

    // Same pair, same panel — only the link has gone.
    glasses.report(
        GlassesDeviceInfo(name = "Ray-Ban Display", isAvailable = false, hasDisplay = true),
    )
    while (viewModel.uiState.value.isDisplayAvailable && spins++ < 400) yield()
    observing.cancel()

    assertTrue(!viewModel.uiState.value.isDisplayAvailable)
  }

  @Test
  fun cardGoesToGlasses_needsALiveGlassesSessionAndNotJustAPair() {
    // The bug this exists to stop: a Display pair sitting on the table while the run is
    // deliberately on the phone, and every card flying to a panel nobody is looking through.
    val onDevice = RealtimeUiState(isDisplayAvailable = true, isGlassesSessionLive = false)
    val onGlasses = RealtimeUiState(isDisplayAvailable = true, isGlassesSessionLive = true)
    val noPanel = RealtimeUiState(isDisplayAvailable = false, isGlassesSessionLive = true)

    assertTrue(!onDevice.cardGoesToGlasses)
    assertTrue(onGlasses.cardGoesToGlasses)
    assertTrue(!noPanel.cardGoesToGlasses)
  }

  @Test
  fun showCard_withAPanelInReachButTheRunOnThePhone_opensTheCardOnThePhone() = runBlocking {
    val display = FakeGlassesDisplay()
    val glasses = FakeReachableGlassesSession()
    val viewModel = viewModel(
        audio = FakeAudioSource(),
        glassesSession = glasses,
        glassesDisplay = display,
    )

    val observing = launch { viewModel.observeGlasses() }
    glasses.report(
        GlassesDeviceInfo(name = "Ray-Ban Display", isAvailable = true, hasDisplay = true),
    )
    var spins = 0
    while (!viewModel.uiState.value.isDisplayAvailable && spins++ < 200) yield()
    observing.cancel()

    viewModel.showCard("green-jay")

    while (viewModel.uiState.value.cardOnPhone == null && spins++ < 400) yield()
    assertEquals("green-jay", viewModel.uiState.value.cardOnPhone?.species?.id)
    assertTrue(display.shown.isEmpty())
  }

  @Test
  fun showCard_withNoPanel_opensTheCardOnThePhone() = runBlocking {
    // **A press is never refused**, and the difference from the automatic push matters: that
    // one is allowed to land nowhere, because nobody asked for it. This one was asked for, so
    // where there is no glass the card opens here instead of nothing happening.
    val display = FakeGlassesDisplay()
    val viewModel = viewModel(audio = FakeAudioSource(), glassesDisplay = display)

    viewModel.showCard("green-jay")

    var spins = 0
    while (viewModel.uiState.value.cardOnPhone == null && spins++ < 200) yield()
    assertEquals("green-jay", viewModel.uiState.value.cardOnPhone?.species?.id)
    assertTrue(display.shown.isEmpty())
  }

  @Test
  fun dismissCard_putsThePhonesCardAway() = runBlocking {
    val viewModel = viewModel(audio = FakeAudioSource())
    viewModel.showCard("green-jay")
    var spins = 0
    while (viewModel.uiState.value.cardOnPhone == null && spins++ < 200) yield()

    viewModel.dismissCard()

    assertNull(viewModel.uiState.value.cardOnPhone)
  }

  // ── The app's own voice ────────────────────────────────────────────────

  @Test
  fun observeSession_aConfidentBirdFinding_saysItPlainly() = runBlocking {
    val voice = FakeSpokenOutput()
    val viewModel = viewModel(
        audio = FakeAudioSource(openForMillis = 30),
        findings = listOf(SessionFinding.Bird("green-jay", "Green Jay", 0.92f)),
        spokenOutput = voice,
    )

    viewModel.observeSession()

    yield()
    assertEquals(listOf("I just heard a Green Jay."), voice.said)
  }

  @Test
  fun observeSession_anUnsureBirdFinding_hedges() = runBlocking {
    // The number stays on the timeline where it can be looked at; the ear gets the
    // confidence as grammar.
    val voice = FakeSpokenOutput()
    val viewModel = viewModel(
        audio = FakeAudioSource(openForMillis = 30),
        findings = listOf(SessionFinding.Bird("green-jay", "Green Jay", 0.61f)),
        spokenOutput = voice,
    )

    viewModel.observeSession()

    yield()
    assertEquals(listOf("I think I heard a Green Jay."), voice.said)
  }

  @Test
  fun observeSession_aBirdFindingWithItsOwnLine_saysThatInstead() = runBlocking {
    val voice = FakeSpokenOutput()
    val viewModel = viewModel(
        audio = FakeAudioSource(openForMillis = 30),
        findings =
            listOf(
                SessionFinding.Bird(
                    "green-jay",
                    "Green Jay",
                    0.92f,
                    spokenLine = "Hear that? Green Jays hold this whole thicket.",
                ),
            ),
        spokenOutput = voice,
    )

    viewModel.observeSession()

    yield()
    assertEquals(listOf("Hear that? Green Jays hold this whole thicket."), voice.said)
  }

  @Test
  fun observeSession_aFindingThatIsNotABird_saysNothing() = runBlocking {
    val voice = FakeSpokenOutput()
    val viewModel = viewModel(
        audio = FakeAudioSource(openForMillis = 30),
        findings = listOf(SessionFinding.Speech("it's green with a yellow belly")),
        spokenOutput = voice,
    )

    viewModel.observeSession()

    yield()
    assertTrue(voice.said.isEmpty())
  }

  @Test
  fun deliverPhotoResponse_speaksTheRowsLine() = runBlocking {
    val voice = FakeSpokenOutput()
    val viewModel = viewModel(
        audio = FakeAudioSource(),
        frames = listOf(frame()),
        spokenOutput = voice,
    )
    viewModel.observeCamera()
    viewModel.capturePhoto()

    viewModel.deliverPhotoResponse(
        DemoPhotoResponse(
            id = "photo-1",
            result = DemoResult.Species("green-jay", 0.92),
            caption = "Green Jay",
            spokenLine = "Green Jay, 92 percent.",
            delayMillis = 0,
        ),
        sessionKey = viewModel.uiState.value.session.startedAt,
        photoIndex = 0,
    )

    yield()
    assertEquals(listOf("Green Jay, 92 percent."), voice.said)
  }

  @Test
  fun deliverPhotoResponse_aRowWithNoLineSaysNothing() = runBlocking {
    // The absence of a line is the whole of the "speak: on/off" switch the Director
    // deliberately does not have — a row is silent because nobody wrote it anything to say.
    val voice = FakeSpokenOutput()
    val viewModel = viewModel(
        audio = FakeAudioSource(),
        frames = listOf(frame()),
        spokenOutput = voice,
    )
    viewModel.observeCamera()
    viewModel.capturePhoto()

    viewModel.deliverPhotoResponse(
        DemoPhotoResponse.pastTheEnd,
        sessionKey = viewModel.uiState.value.session.startedAt,
        photoIndex = 0,
    )

    yield()
    assertTrue(voice.said.isEmpty())
  }

  @Test
  fun deliverAnswer_speaksTheAnswer() = runBlocking {
    val voice = FakeSpokenOutput()
    val viewModel = viewModel(audio = FakeAudioSource(), spokenOutput = voice)

    viewModel.deliverAnswer(GreenJayQuestion, sessionKey = 0L)

    yield()
    assertEquals(listOf(GreenJayQuestion.answer), voice.said)
  }

  @Test
  fun observeSession_whileTheAppIsSpeaking_theStripTakesNoAudio() = runBlocking {
    // The line comes back down the open microphone, and the app's own voice is not birdsong.
    val voice = FakeSpokenOutput(isSpeaking = true)
    val viewModel = viewModel(
        audio =
            FakeAudioSource(
                openForMillis = 30,
                chunks = listOf(AudioChunk(FloatArray(2048) { 0.5f })),
            ),
        spokenOutput = voice,
    )

    viewModel.observeSession()

    assertEquals(0, viewModel.sonogram.count)
  }

  @Test
  fun observeSession_onceTheLineIsFinished_theStripDrawsAgain() = runBlocking {
    val voice = FakeSpokenOutput()
    val viewModel = viewModel(
        audio =
            FakeAudioSource(
                openForMillis = 30,
                chunks = listOf(AudioChunk(FloatArray(2048) { 0.5f })),
            ),
        spokenOutput = voice,
    )

    viewModel.observeSession()

    assertTrue(viewModel.sonogram.count > 0)
  }

  @Test
  fun stopSession_cutsTheLineShort() = runBlocking {
    val voice = FakeSpokenOutput()
    val viewModel = viewModel(audio = FakeAudioSource(), spokenOutput = voice)

    viewModel.stopSession()

    assertEquals(1, voice.silences)
  }

  // ── Stopping, and the review ───────────────────────────────────────────

  @Test
  fun stopSession_landsOnTheReviewWithTheClockReading() {
    val clock = FakeSessionClock()
    clock.advance(42.0)
    val viewModel = viewModel(audio = FakeAudioSource(), clock = clock)

    viewModel.stopSession()

    val review = viewModel.uiState.value.review
    assertEquals(42.0, review!!.durationSeconds, 0.0001)
    assertTrue(viewModel.uiState.value.isReviewing)
  }

  @Test
  fun stopSession_closesTheCameraOnTheWayOut() {
    val viewModel = viewModel(audio = FakeAudioSource())
    viewModel.openCamera()

    viewModel.stopSession()

    assertTrue(!viewModel.uiState.value.isCameraOpen)
  }

  @Test
  fun toggleBirdKept_dropsABirdAndTakesItBack() {
    val viewModel = viewModel(audio = FakeAudioSource())
    viewModel.stopSession()

    viewModel.toggleBirdKept(14.0)
    assertEquals(setOf(14.0), viewModel.uiState.value.review!!.droppedBirds)

    viewModel.toggleBirdKept(14.0)
    assertTrue(viewModel.uiState.value.review!!.droppedBirds.isEmpty())
  }

  @Test
  fun start_clearsTheLastRunsReview() {
    // The view model outlives the cover; a fresh run must not open onto the review the last
    // one ended on.
    val viewModel = viewModel(audio = FakeAudioSource())
    viewModel.stopSession()

    viewModel.start()
    idleMainLooper()

    assertNull(viewModel.uiState.value.review)
  }

  // ── Saving ─────────────────────────────────────────────────────────────

  @Test
  fun performSave_writesTheSessionAsALiveOuting() = runBlocking {
    val clock = FakeSessionClock()
    clock.advance(8.0)
    val journal = FakeJournalRepository()
    val here = Coordinate(39.142, -84.506)
    val viewModel = viewModel(
        audio = FakeAudioSource(openForMillis = 30),
        fix = here,
        findings = listOf(SessionFinding.Bird("american-robin", "American Robin", 0.87f)),
        journal = journal,
        clock = clock,
    )

    viewModel.observeSession()
    clock.advance(4.0)
    viewModel.deliverAnswer(GreenJayQuestion, viewModel.uiState.value.session.startedAt)
    viewModel.stopSession()
    viewModel.setReviewNotes("A bright morning")
    viewModel.performSave()

    val draft = journal.saved!!
    assertEquals(OutingKind.LIVE, draft.kind)
    assertEquals(12_000L, draft.durationMs)
    assertEquals(here.latitude, draft.location.latitude, 0.0001)
    assertEquals(here.longitude, draft.location.longitude, 0.0001)
    assertEquals("A bright morning", draft.notes)

    // The robin heard on the clock is a detection; the exchange is kept whole.
    val detection = draft.events.first { it.type == OutingEventType.DETECTION }
    assertEquals("american-robin", detection.speciesId)
    assertEquals(8_000L, detection.offsetMs)
    assertEquals(0.87, detection.confidence!!, 0.0001)
    val exchange = draft.events.first { it.type == OutingEventType.QA }
    assertEquals("it's green with a yellow belly", exchange.question)
    assertEquals("That's likely a Green Jay.", exchange.answer)

    // Both birds enter the life list: the robin confirming its detection, the jay —
    // reached through the watcher's own words — confirming nothing, like the wizard's.
    assertEquals(
        setOf("american-robin", "green-jay"),
        draft.sightings.map { it.speciesId }.toSet(),
    )
    assertEquals(
        detection.id,
        draft.sightings.first { it.speciesId == "american-robin" }.confirming!!.id,
    )
    assertNull(draft.sightings.first { it.speciesId == "green-jay" }.confirming)

    assertEquals("outing-1", viewModel.uiState.value.review!!.savedOutingId)
  }

  @Test
  fun performSave_writesWhatTheSessionHeard() = runBlocking {
    val clock = FakeSessionClock()
    clock.advance(2.0)
    val journal = FakeJournalRepository()
    val viewModel = viewModel(
        audio =
            FakeAudioSource(
                openForMillis = 30,
                chunks = listOf(AudioChunk(FloatArray(2048) { 0.5f })),
            ),
        fix = Coordinate(39.142, -84.506),
        journal = journal,
        clock = clock,
    )

    viewModel.observeSession()
    viewModel.stopSession()
    viewModel.performSave()

    // One unbroken stretch on one microphone: one media row, pinned where the clock
    // stood when it opened, its length measured from the samples themselves.
    val audio = journal.saved!!.media.filter { it.type == OutingMediaType.AUDIO }
    assertEquals(1, audio.size)
    assertEquals("wav", audio[0].fileExtension)
    assertEquals(CaptureSource.PHONE, audio[0].source)
    assertEquals(2_000L, audio[0].offsetMs)
    assertEquals(128L, audio[0].durationMs)
    assertEquals(2048, WavCodec.decode(audio[0].bytes).size)
  }

  @Test
  fun performSave_writesAPhotosBirdAsSeenInThatPhoto() = runBlocking {
    // `seen`, not `heard`: the photo is the evidence, and the journal has a column that
    // points a detection at the media row it was found in.
    val journal = FakeJournalRepository()
    val viewModel = viewModel(
        audio = FakeAudioSource(openForMillis = 30),
        fix = Coordinate(39.142, -84.506),
        armed = JayPhotoPreset,
        frames = listOf(frame()),
        journal = journal,
    )

    viewModel.observeSession()
    viewModel.observeCamera()
    viewModel.capturePhoto()
    viewModel.deliverPhotoResponse(
        JayPhotoPreset.photoResponses[0],
        sessionKey = viewModel.uiState.value.session.startedAt,
        photoIndex = 0,
    )
    viewModel.stopSession()
    viewModel.performSave()

    val draft = journal.saved!!
    val photo = draft.media.single { it.type == OutingMediaType.PHOTO }
    val detection = draft.events.single { it.type == OutingEventType.DETECTION }
    assertEquals("green-jay", detection.speciesId)
    assertEquals(photo.id, detection.mediaId)
    assertNull(detection.offsetMs)

    // And it can enter the life list, confirming the detection it came from.
    val sighting = draft.sightings.single()
    assertEquals("green-jay", sighting.speciesId)
    assertEquals(detection.id, sighting.confirming!!.id)
  }

  @Test
  fun performSave_leavesADroppedBirdOutOfTheSightings() = runBlocking {
    val clock = FakeSessionClock()
    clock.advance(8.0)
    val journal = FakeJournalRepository()
    val viewModel = viewModel(
        audio = FakeAudioSource(openForMillis = 30),
        fix = Coordinate(39.142, -84.506),
        findings = listOf(SessionFinding.Bird("american-robin", "American Robin", 0.87f)),
        journal = journal,
        clock = clock,
    )

    viewModel.observeSession()
    viewModel.stopSession()
    viewModel.toggleBirdKept(8.0)
    viewModel.performSave()

    // The timeline keeps the detection — it happened — but nothing enters the life list.
    val draft = journal.saved!!
    assertEquals(1, draft.events.count { it.type == OutingEventType.DETECTION })
    assertTrue(draft.sightings.isEmpty())
  }

  @Test
  fun performSave_withoutAFix_asksOnceMoreThenDeclines() = runBlocking {
    val journal = FakeJournalRepository()
    val viewModel = viewModel(
        audio = FakeAudioSource(openForMillis = 30),
        fix = null,
        journal = journal,
    )

    viewModel.observeSession()
    viewModel.stopSession()
    viewModel.performSave()

    // Declining leaves the journal untouched and the buttons live, and says why.
    assertNull(journal.saved)
    val review = viewModel.uiState.value.review!!
    assertEquals(SessionSaveError.NO_LOCATION_FIX, review.saveError)
    assertTrue(!review.isSaving)
    assertNull(review.savedOutingId)
  }

  @Test
  fun performSave_whenTheWriteFails_saysSoAndStaysLive() = runBlocking {
    val journal = FakeJournalRepository(failWith = IllegalStateException("disk full"))
    val viewModel = viewModel(
        audio = FakeAudioSource(openForMillis = 30),
        fix = Coordinate(39.142, -84.506),
        journal = journal,
    )

    viewModel.observeSession()
    viewModel.stopSession()
    viewModel.performSave()

    val review = viewModel.uiState.value.review!!
    assertEquals(SessionSaveError.WRITE_FAILED, review.saveError)
    assertTrue(!review.isSaving)
  }

  // ── The run belongs to the app ────────────────────────────────────────

  @Test
  fun start_whileARunIsInFlight_doesNotStartAnother() {
    // The cover calls this on every appearance and the shell on every voice launch, and
    // neither knows about the other: the second call has to find the first run and join
    // it rather than open a second microphone under it.
    val audio = OpenCountingAudioSource()
    val viewModel = viewModel(audio)

    viewModel.start()
    idleMainLooper()
    viewModel.start()
    idleMainLooper()

    assertTrue(viewModel.uiState.value.isRunning)
    assertEquals(1, audio.opens)

    viewModel.stopSession()
    idleMainLooper()
  }

  @Test
  fun stopSession_endsTheRun() {
    // The stop is what closes the microphone. The run is the view model's own, so no
    // screen has to be showing — or be cancelled — for it to end.
    val audio = OpenCountingAudioSource()
    val viewModel = viewModel(audio)
    viewModel.start()
    idleMainLooper()

    viewModel.stopSession()
    idleMainLooper()

    assertFalse(viewModel.uiState.value.isRunning)
    assertTrue(viewModel.uiState.value.isReviewing)
    assertEquals(1, audio.closes)
  }

  /** Runs whatever the run posted to the main thread — a resumption, a cancellation. */
  private fun idleMainLooper() = shadowOf(Looper.getMainLooper()).idle()

  private fun viewModel(
      audio: AudioCaptureSource,
      fix: Coordinate? = null,
      bearings: List<Double> = emptyList(),
      elevations: List<Double> = emptyList(),
      findings: List<SessionFinding> = emptyList(),
      armed: DemoPreset? = null,
      frames: List<PreviewFrame> = emptyList(),
      journal: FakeJournalRepository = FakeJournalRepository(),
      clock: FakeSessionClock = FakeSessionClock(),
      camera: FakeCameraSource = FakeCameraSource(frames),
      glassesInput: GlassesInputRepository = FakeGlassesInput(),
      glassesSpeech: GlassesSpeechRepository = FakeGlassesSpeech(),
      glassesSession: GlassesSessionRepository = FakeGlassesSession(),
      glassesDisplay: GlassesDisplayRepository = FakeGlassesDisplay(),
      spokenOutput: SpokenOutput = FakeSpokenOutput(),
  ) = RealtimeViewModel(
      audioSource = audio,
      previewSource = camera,
      detector = FakeDetector(findings),
      director = FakeDirector(armed),
      birdCatalog = FakeCatalog,
      journal = journal,
      glassesSession = glassesSession,
      glassesCamera = FakeGlassesCamera(),
      glassesInput = glassesInput,
      glassesSpeech = glassesSpeech,
      glassesDisplay = glassesDisplay,
      spokenOutput = spokenOutput,
      locationProvider = FakeLocationProvider(fix),
      headingProvider = FakeHeadingProvider(bearings),
      gazeProvider = FakeGazeProvider(elevations),
      clock = clock,
  )

  private companion object {

    /** One pixel, which is all a capture has to be for a timeline to hold it. */
    fun bitmap(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    fun frame(): PreviewFrame = PreviewFrame(bitmap())

    /** A capture on the timeline, waiting — what the resolution tests resolve onto. */
    fun photoEvent(at: Double, index: Int): SessionEvent.Photo =
        SessionEvent.Photo(
            at = at,
            index = index,
            image = bitmap(),
            source = CaptureSource.PHONE,
            gazeContext = null,
            bearingDeg = null,
            identification = PhotoIdentification.Pending,
        )

    /**
     * A capture whose picture is still crossing from the glasses — a row with its space held open
     * and nothing in it yet.
     */
    fun crossingPhotoEvent(at: Double, index: Int): SessionEvent.Photo =
        SessionEvent.Photo(
            at = at,
            index = index,
            image = null,
            source = CaptureSource.GLASSES,
            gazeContext = null,
            bearingDeg = null,
            identification = null,
        )

    /** A preset whose first capture is answered with a named bird, at once. */
    val JayPhotoPreset = DemoPreset(
        id = "photo-jay",
        name = "Photo Jay",
        photoResponses =
            listOf(
                DemoPhotoResponse(
                    id = "photo-1",
                    result = DemoResult.Species("green-jay", 0.92),
                    caption = "Green Jay",
                    delayMillis = 0,
                ),
            ),
        unmatchedQuestion = "Sorry — didn't catch that.",
    )

    /**
     * The same capture, answered far too late for a test to see it land. What pins the *waiting*
     * half: with a zero delay the shutter's own delivery resolves the row before the assertion can
     * read it.
     */
    val WaitingPhotoPreset =
        JayPhotoPreset.copy(
            photoResponses = listOf(JayPhotoPreset.photoResponses[0].copy(delayMillis = 60_000)),
        )

    /** The starter's STT row, with no composing delay so a test lands it at once. */
    val GreenJayQuestion = DemoQuestion(
        id = "green-yellow-belly",
        prompts = listOf("it's green with a yellow belly", "green bird yellow belly"),
        answer = "That's likely a Green Jay.",
        speciesId = "green-jay",
        delayMillis = 0,
    )

    /** A preset with the starter's one STT row, and the line a miss comes back with. */
    val GreenJayPreset = DemoPreset(
        id = "green-jay-question",
        name = "Green Jay Question",
        questions = listOf(GreenJayQuestion),
        unmatchedQuestion = "Sorry — didn't catch that.",
    )
  }
}

/**
 * A microphone that only ever fails, or hears nothing at all.
 *
 * [openForMillis] holds the stream open without producing a sample. That is what the location tests
 * need: the fix runs beside the microphone and is cancelled when it stops, so a stream that ends
 * the instant it opens would race the GPS rather than test it.
 */
private class FakeAudioSource(
    override val kind: CaptureSourceKind = CaptureSourceKind.PHONE,
    private val failure: Throwable? = null,
    private val openForMillis: Long = 0,
    private val chunks: List<AudioChunk> = emptyList(),
) : AudioCaptureSource {
  override fun audioStream(): Flow<AudioChunk> = flow {
    failure?.let { throw it }
    chunks.forEach { emit(it) }
    if (openForMillis > 0) delay(openForMillis)
  }
}

/**
 * A microphone that opens, says nothing, and stays open until it is cancelled — counting the opens
 * and the closes, which is what a test of who owns the run reads.
 */
private class OpenCountingAudioSource : AudioCaptureSource {
  override val kind = CaptureSourceKind.PHONE
  private val opened = AtomicInteger()
  private val closed = AtomicInteger()

  val opens: Int
    get() = opened.get()

  val closes: Int
    get() = closed.get()

  override fun audioStream(): Flow<AudioChunk> = flow {
    opened.incrementAndGet()
    try {
      awaitCancellation()
    } finally {
      closed.incrementAndGet()
    }
  }
}

/** A GPS with one answer ready: a fix, or the `null` that stands for every way of not knowing. */
private class FakeLocationProvider(private val fix: Coordinate?) : LocationProvider {
  override suspend fun currentCoordinate(): Coordinate? = fix
}

/** A compass with a script. Empty is the phone that has none — a finished flow, not an error. */
private class FakeHeadingProvider(private val bearings: List<Double>) : HeadingProvider {
  override fun headingStream(): Flow<Double> = bearings.asFlow()
}

/** A tilt with a script. Empty is the phone that cannot answer — a finished flow, not an error. */
private class FakeGazeProvider(private val elevations: List<Double>) : GazeProvider {
  override fun gazeStream(): Flow<Double> = elevations.asFlow()
}

/**
 * A camera that never opens — the session does not need one — unless [frames] says otherwise, which
 * is how the photo lane gets something for the shutter to take.
 */
private class FakeCameraSource(private val frames: List<PreviewFrame> = emptyList()) :
    CameraPreviewSource {
  override val kind = CaptureSourceKind.PHONE

  /** How many times the light has been asked for — what a doubled capture shows up as. */
  var torchOns = 0
    private set

  override fun previewStream(): Flow<PreviewFrame> =
      if (frames.isEmpty()) emptyFlow() else frames.asFlow()

  override fun setTorch(isOn: Boolean) {
    if (isOn) torchOns += 1
  }
}

/** A detector with a script, or with nothing scripted at all. */
private class FakeDetector(private val findings: List<SessionFinding> = emptyList()) :
    SessionDetector {
  override fun findingStream(): Flow<SessionFinding> = findings.asFlow()
}

/**
 * A Director armed with exactly [armed], or with nothing. The cue methods keep the real contract —
 * the Nth row, the fixed past-the-end answer, null when nothing is armed — so the view model's
 * handling is tested against the shape it will actually be handed.
 */
private class FakeDirector(override val armed: DemoPreset? = null) : DemoDirector {
  override fun findingStream(): Flow<SessionFinding> = emptyFlow()

  override fun responseToPhotoAt(index: Int): DemoPhotoResponse? {
    val preset = armed ?: return null
    return preset.photoResponses.getOrNull(index) ?: DemoPhotoResponse.pastTheEnd
  }

  /**
   * Matched by plain containment on the lowercased transcript. The real normalise-and-contain
   * policy is `PresetDemoDirector`'s and has scenarios of its own; what a session needs from here
   * is only *matched* or *not*.
   */
  override fun answerTo(transcript: String): DemoQuestion? {
    val heard = transcript.lowercase()
    return armed?.questions?.firstOrNull { question ->
      question.prompts.any { heard.contains(it.lowercase()) }
    }
  }
}

/** Two birds and nothing else — what cue-landed answers resolve names against. */
private object FakeCatalog : BirdCatalogRepository {

  private val birds = mapOf(
      "american-robin" to "American Robin",
      "green-jay" to "Green Jay",
  )

  override suspend fun findById(speciesId: String): SpeciesWithMedia? {
    val commonName = birds[speciesId] ?: return null
    return SpeciesWithMedia(
        species =
            Species(
                id = speciesId,
                commonName = commonName,
                scientificName = "Testus $speciesId",
                familyName = "Testidae",
                browseOrder = 10,
                groupName = "Test Birds",
                sizeClass = 3,
                aboutText = "",
                habitatText = "",
            ),
    )
  }

  override suspend fun allSpecies(): List<Species> = emptyList()

  override suspend fun browseGroups(): List<SpeciesGroup> = emptyList()

  override suspend fun identifyCandidates(query: IdentifyQuery): List<SpeciesWithMedia> =
      emptyList()

  override suspend fun birdOfTheDay(epochDay: Long): SpeciesWithMedia? = null

  override suspend fun seedVersion(): Int? = null
}

/** A journal that remembers the one draft handed to it — or refuses, when told to fail. */
private class FakeJournalRepository(private val failWith: Exception? = null) : JournalRepository {
  var saved: OutingDraft? = null
    private set

  override fun journalStream(): Flow<List<OutingWithChildren>> = emptyFlow()

  override fun lifeListCountStream(): Flow<Int> = emptyFlow()

  override suspend fun findById(outingId: String): OutingWithChildren? = null

  override suspend fun saveOuting(draft: OutingDraft): String {
    failWith?.let { throw it }
    saved = draft
    return "outing-1"
  }

  override suspend fun updateNotes(outingId: String, notes: String?) = Unit

  override suspend fun delete(outingId: String) = Unit

  override suspend fun deleteAll() = Unit
}

/**
 * Glasses that are never there: no registration read, no pair listed, a session that refuses. The
 * default the session runs over when a test is not about the glasses.
 */
private class FakeGlassesSession : GlassesSessionRepository {
  override fun registrationStateStream(): Flow<GlassesRegistrationState> = emptyFlow()

  override fun deviceInfoStream(): Flow<GlassesDeviceInfo?> = emptyFlow()

  override fun sessionStream(): Flow<GlassesSessionState> = flow {
    throw GlassesError.NotConnected
  }

  override suspend fun access(permission: GlassesPermission): GlassesAccess = GlassesAccess.UNKNOWN
}

/**
 * Glasses that are registered, whose snapshots the test reports one at a time.
 *
 * Driven rather than scripted, for the same reason [FakeGlassesInput] is: a list emitted up front
 * is a list the observer may have drained before the test ever looks, which makes any assertion
 * about the state *between* two snapshots unwritable. Reporting one and waiting for it to land
 * makes each step observable.
 *
 * The snapshot is a [MutableStateFlow] rather than a shared flow because it is a standing fact and
 * not an event: a collector that arrives late is owed the current reading, not silence.
 */
private class FakeReachableGlassesSession : GlassesSessionRepository {
  private val devices = MutableStateFlow<GlassesDeviceInfo?>(null)

  override fun registrationStateStream(): Flow<GlassesRegistrationState> =
      flowOf(GlassesRegistrationState.REGISTERED)

  override fun deviceInfoStream(): Flow<GlassesDeviceInfo?> = devices

  /** Hand the screen one snapshot. `null` is a pair gone from the list entirely. */
  fun report(device: GlassesDeviceInfo?) {
    devices.value = device
  }

  override fun sessionStream(): Flow<GlassesSessionState> = flow {
    throw GlassesError.NotConnected
  }

  override suspend fun access(permission: GlassesPermission): GlassesAccess = GlassesAccess.UNKNOWN
}

/**
 * Glasses that will take a session: registered, in reach, the grant read granted, and a session
 * that opens and holds until the collector hangs up. What a test that starts a run on the glasses
 * drives it over.
 */
private class FakeGrantedGlassesSession : GlassesSessionRepository {
  override fun registrationStateStream(): Flow<GlassesRegistrationState> =
      flowOf(GlassesRegistrationState.REGISTERED)

  override fun deviceInfoStream(): Flow<GlassesDeviceInfo?> =
      flowOf(GlassesDeviceInfo(name = "Ray-Ban", isAvailable = true))

  override fun sessionStream(): Flow<GlassesSessionState> = flow {
    emit(GlassesSessionState.STARTING)
    awaitCancellation()
  }

  override suspend fun access(permission: GlassesPermission): GlassesAccess = GlassesAccess.GRANTED
}

/** The camera half of [FakeGlassesSession] — a shutter with nothing behind it. */
private class FakeGlassesCamera : GlassesCameraRepository {
  override val honoursCaptureSettings = true

  override suspend fun capturePhoto(
      format: PhotoFormat,
      resolution: CaptureResolution,
      quality: CaptureQuality,
  ): CapturedPhoto = throw GlassesError.NotConnected
}

/**
 * The buttons half of [FakeGlassesSession] — a pair of glasses whose temple the test uses. The flow
 * stays open between gestures, which is what a real one does.
 */
private class FakeGlassesInput : GlassesInputRepository {
  private val events = MutableSharedFlow<GlassesInputEvent>(extraBufferCapacity = 8)

  /**
   * Whether anything has collected yet — what a test waits on before pressing, since a press with
   * nobody listening is dropped by design.
   */
  val isListening: Boolean
    get() = events.subscriptionCount.value > 0

  override fun inputEventStream(): Flow<GlassesInputEvent> = events

  fun press() {
    check(events.tryEmit(GlassesInputEvent.SHUTTER)) { "the press was not delivered" }
  }

  fun swipeBack() {
    check(events.tryEmit(GlassesInputEvent.BACK)) { "the swipe was not delivered" }
  }
}

/**
 * A recogniser a test speaks through. Quiet until something says otherwise, which is what every
 * scenario that is not about speech needs from it.
 */
private class FakeGlassesSpeech : GlassesSpeechRepository {
  private val heard = MutableSharedFlow<Transcription>(extraBufferCapacity = 8)

  /**
   * Whether anything has collected yet — what a test waits on before speaking, since an utterance
   * with nobody listening is dropped by design.
   */
  val isListening: Boolean
    get() = heard.subscriptionCount.value > 0

  override fun transcriptionStream(): Flow<Transcription> = heard

  override fun speechStateStream(): Flow<GlassesSpeechState> = flowOf(GlassesSpeechState.LISTENING)

  fun say(text: String, isFinal: Boolean = true) {
    check(heard.tryEmit(Transcription(text, isFinal))) { "the utterance was not delivered" }
  }
}

/**
 * The display half of [FakeGlassesSession] — a wall that remembers every gallery that lands on it,
 * by the bird's id, and every time it was wiped.
 */
/**
 * A voice that remembers every line it was given rather than saying any of them, and can be told to
 * hold — [isSpeaking] is what a session reads while an announcement is in the air, and setting it
 * is how a test puts the app mid-sentence without one being said.
 */
private class FakeSpokenOutput(override var isSpeaking: Boolean = false) : SpokenOutput {
  val said = mutableListOf<String>()
  var silences = 0
    private set

  override suspend fun speak(words: String) {
    said += words
  }

  override fun silence() {
    silences++
  }
}

private class FakeGlassesDisplay : GlassesDisplayRepository {
  val shown = mutableListOf<String>()
  var clearCount = 0
    private set

  override suspend fun showGallery(bird: SpeciesWithMedia, message: String?) {
    shown += bird.species.id
  }

  override suspend fun clear() {
    clearCount++
  }
}

/**
 * A clock with the hands moved by hand. The session's own is monotonic and cannot be wound, which
 * is the point of it — a test that needs four seconds to pass should not take four seconds.
 *
 * [start] is **counted, not obeyed**: the hands stay where the test put them. Zeroing here would
 * make it impossible to say "this session is already eighteen seconds old" and then run it, which
 * is the only interesting thing to say to a clock.
 */
private class FakeSessionClock : SessionClock {
  override var elapsed: Double = 0.0
    private set

  var starts = 0
    private set

  override fun start() {
    starts++
  }

  fun advance(seconds: Double) {
    elapsed += seconds
  }
}
