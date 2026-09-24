/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.meta.pixelandtexel.birdspotter.data.session.startRealtimeSessionService
import com.meta.pixelandtexel.birdspotter.domain.BirdLog
import com.meta.pixelandtexel.birdspotter.domain.GlassesVoiceEvent
import com.meta.pixelandtexel.birdspotter.domain.LogCategory
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme
import com.meta.wearable.dat.core.voiceinvocations.isVoiceInvocationsIntent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * The app's one activity. Everything above it is Compose — [BirdSpotterApp] holds the shell and the
 * navigation graph.
 */
@SuppressLint("InvalidFragmentVersionForActivityResult")
class MainActivity : ComponentActivity() {

  /**
   * The Bluetooth prompt, answered either way by booting the glasses (see [startGlasses]). A
   * refusal is a working app without glasses, not a reason to hold the SDK back.
   */
  private val bluetoothRequest = registerForActivityResult(
      ActivityResultContracts.RequestPermission(),
  ) {
    startGlasses()
  }

  /**
   * The notifications prompt, asked once the glasses are up. A refusal costs only the pocketed
   * session's notification — the session itself, and the service holding it, run either way.
   */
  private val notificationsRequest = registerForActivityResult(
      ActivityResultContracts.RequestPermission(),
  ) {}

  /**
   * Completed the moment [startGlasses] has run — the gate [voiceEvents] waits behind.
   *
   * Collecting the voice channel is what would otherwise boot DAT, and the whole point of
   * [onCreate]'s ordering is that nothing boots it before the Bluetooth grant has been answered. A
   * deferred rather than a flag, so the waiting flow suspends instead of polling.
   */
  private val glassesStarted = CompletableDeferred<Unit>()

  /**
   * [glassesStarted], as state the shell can read: what gates the parts of the shell that would
   * otherwise reach the SDK before the Bluetooth grant has been answered.
   */
  private var isGlassesReady by mutableStateOf(false)

  /**
   * Voice-triggered launches, which arrive as an intent rather than on the invocations channel.
   * "Hey Meta, open BirdSpotter" brings the activity up marked as Meta AI's own, and
   * [isVoiceInvocationsIntent] is what reads that mark — a separate arrival from the channel, which
   * carries spoken actions, so it has its own path to the shell. Conflated because only the latest
   * matters: the cover a launch raises is the same cover however many piled up behind a cold start.
   */
  private val launches = Channel<GlassesVoiceEvent>(Channel.CONFLATED)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // A plain launch rides the intent, not the channel — so it is read here. Only a
    // genuine cold start consults it: a configuration-change recreate carries the same
    // launching intent, and replaying it would raise the cover again over a shell the
    // wearer has already moved on from — the cover keeps its own state across the recreate.
    // A launch spoken while the app is already up comes through [onNewIntent].
    if (savedInstanceState == null && isVoiceInvocationsIntent(intent)) {
      onVoiceLaunch()
    }

    // The run holding the process open. A run beginning is what starts the service, from
    // here because raising a system flow is bound to whatever is on screen; the service
    // stops itself when the run ends. The over-keyguard leave a voice launch takes is
    // given back at the same moment — it was for that session, not for the app.
    lifecycleScope.launch {
      glassesStarted.await()
      val container = (applicationContext as BirdSpotterApplication).container
      container.realtimeViewModel.uiState
          .map { it.isRunning }
          .distinctUntilChanged()
          .collect { isRunning ->
            if (isRunning) {
              startRealtimeSessionService()
            } else {
              setShowWhenLocked(false)
              setTurnScreenOn(false)
            }
          }
    }

    // The Bluetooth grant comes first, and the order is the whole point.
    //
    // Booting DAT is what asks the platform which glasses exist, and the question is
    // asked over the bonded-device list — which throws without BLUETOOTH_CONNECT. The SDK
    // catches that, reads it as "no glasses on this phone", and settles on an
    // unregisterable state for the life of the process. Declaring the permission in the
    // manifest does none of this work on 31+: nothing is granted until it is asked for,
    // and until this prompt existed nothing ever asked.
    //
    // The reading it produces is indistinguishable from having no Meta AI app at all,
    // which is why the Settings card checks the grant itself before blaming Meta AI —
    // see setupNotice in SettingsScreen.
    if (isBluetoothGranted()) {
      startGlasses()
    } else {
      bluetoothRequest.launch(Manifest.permission.BLUETOOTH_CONNECT)
    }

    enableEdgeToEdge()
    setContent {
      BirdSpotterTheme {
        BirdSpotterApp(voiceEvents = voiceEvents, isGlassesReady = isGlassesReady)
      }
    }
  }

  /**
   * A launch spoken while the app is already up. The activity is `singleTask`, so it arrives here
   * rather than stacking a second copy; [setIntent] keeps [getIntent] honest for anything that
   * reads the intent later.
   */
  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    if (isVoiceInvocationsIntent(intent)) {
      onVoiceLaunch()
    }
  }

  /**
   * "Hey Meta, open BirdSpotter", however it arrived.
   *
   * **The session is started here, not left to the shell.** The launch may land with the phone
   * locked in a pocket, where nothing on screen composes and a run that waited on the cover would
   * never begin — so the app's own session is started directly, reaching for the glasses, and the
   * shell is told separately so it can raise the cover if there is a screen to raise it on. Both
   * are idempotent, so the two paths cost nothing together.
   *
   * The activity is also let over the keyguard for this launch — a visible activity is what lets
   * the platform grant a microphone service to an app it would otherwise refuse from the background
   * — and the leave is given back when the run ends (see [onCreate]).
   */
  private fun onVoiceLaunch() {
    setShowWhenLocked(true)
    setTurnScreenOn(true)
    lifecycleScope.launch {
      glassesStarted.await()
      BirdLog.info(LogCategory.SESSION) { "voice launch — starting the session on the glasses" }
      (applicationContext as BirdSpotterApplication)
          .container
          .realtimeViewModel
          .start(onGlasses = true)
    }
    launches.trySend(GlassesVoiceEvent.LAUNCH)
  }

  /**
   * The two ways a launch reaches the shell, as one stream: the intent path ([launches], where a
   * plain "Hey Meta, open BirdSpotter" lands) and the invocations channel (spoken actions, plus a
   * launch on the devices that deliver one there). Merged so the shell has a single thing to
   * collect and raises the cover on whichever arrives.
   *
   * The channel half is held back until the SDK is up — collecting it is what would otherwise boot
   * DAT, and [onCreate]'s ordering puts the Bluetooth grant first (see [glassesStarted]). The
   * intent half has no such gate: an intent is just an intent, and a launch should raise the cover
   * whether or not the glasses ever answered.
   *
   * **The session is started here, on both halves, before the shell hears.** The intent half starts
   * it in [onVoiceLaunch] — with no screen at all, if the phone is locked — and the channel half
   * starts it as the launch passes through, because the channel only ever delivers to a shell that
   * is up. Either way the shell is left with the half that needs a screen: raising the cover.
   *
   * A `by lazy` so the merged stream is one stable instance the shell collects once, rather than a
   * fresh one per recomposition.
   */
  private val voiceEvents: Flow<GlassesVoiceEvent> by lazy {
    merge(
        launches.receiveAsFlow(),
        flow {
          glassesStarted.await()
          val container = (applicationContext as BirdSpotterApplication).container
          emitAll(
              container.glassesVoiceRepository.voiceEventStream().onEach { event ->
                if (event == GlassesVoiceEvent.LAUNCH) {
                  container.realtimeViewModel.start(onGlasses = true)
                }
              },
          )
        },
    )
  }

  /**
   * Wakes the glasses repository, which is what boots DAT (see
   * [com.meta.pixelandtexel.birdspotter.data.dat.DatGlassesSessionRepository]) — at launch, from
   * the activity, so the `birdspotter://` registration callback can never arrive before the SDK is
   * listening for it. Deliberately not in [BirdSpotterApplication.onCreate]: Robolectric hosts that
   * class for every JVM test.
   *
   * Idempotent, because both paths through [onCreate] end here and a cold start that is already
   * granted takes only the first: the container holds it behind a lazy.
   */
  private fun startGlasses() {
    val container = (applicationContext as BirdSpotterApplication).container
    container.glassesSessionRepository
    // Second, and at launch for the same reason: building it is what puts the simulated
    // glasses back if they were on when the app last closed, and that has to happen before
    // any screen asks the SDK which glasses exist.
    container.mockDeviceRepository
    glassesStarted.complete(Unit)
    isGlassesReady = true

    // Then the notification prompt, for the pocketed session's one surface — after the
    // Bluetooth one, so the two do not stack.
    if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
    ) {
      notificationsRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  private fun isBluetoothGranted(): Boolean =
      ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
          PackageManager.PERMISSION_GRANTED
}
