/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.meta.pixelandtexel.birdspotter.BirdSpotterApplication
import com.meta.pixelandtexel.birdspotter.MainActivity
import com.meta.pixelandtexel.birdspotter.R
import com.meta.pixelandtexel.birdspotter.domain.BirdLog
import com.meta.pixelandtexel.birdspotter.domain.CaptureSourceKind
import com.meta.pixelandtexel.birdspotter.domain.LogCategory
import com.meta.pixelandtexel.birdspotter.domain.SessionEvent
import com.meta.pixelandtexel.birdspotter.features.identify.RealtimeUiState
import com.meta.pixelandtexel.birdspotter.features.identify.RealtimeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Holds the process — and the microphone — open for a real-time session that has no screen.
 *
 * **A lifecycle wrapper and nothing more.** The session is [RealtimeViewModel]'s, alive in the
 * app's own composition root whether or not a screen is showing it; what the platform will not
 * grant that object on its own is the right to keep recording once the phone is locked in a pocket.
 * A foreground service typed `microphone|connectedDevice` is how that right is asked for, and the
 * notification it must carry is the one surface a pocketed session has — the elapsed time, the word
 * *listening*, a count of what has landed, and the way to stop.
 *
 * Started by the activity when a run begins; stops itself when the run ends, so a session that
 * stops from the glasses or from the notification takes the service down with it.
 */
class RealtimeSessionService : Service() {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var watch: Job? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val viewModel = (application as BirdSpotterApplication).container.realtimeViewModel

    if (intent?.action == ActionStop) {
      // The stop from the notification is the vermilion stop on the screen: the run ends
      // onto its review, which the app lands on next time it is opened.
      viewModel.stopSession()
      stopSelf()
      return START_NOT_STICKY
    }

    val state = viewModel.uiState.value
    if (!state.isRunning) {
      stopSelf()
      return START_NOT_STICKY
    }

    // Promoted at once: a foreground service that has not shown its notification within a
    // few seconds of starting is an ANR, and the platform refuses a microphone type to an
    // app it cannot see — logged rather than thrown, because the session itself is still
    // running in the process for as long as the process lasts.
    try {
      startForeground(
          NotificationId,
          notification(state),
          ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
              ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
      )
    } catch (error: Exception) {
      BirdLog.error(LogCategory.SESSION, error) {
        "session service — the platform would not let it hold the microphone"
      }
      stopSelf()
      return START_NOT_STICKY
    }

    // Follow the run: the notification says what has landed, and the run ending is the
    // service ending. Reduced to what the notification draws so a session hearing sixty
    // chunks a second does not redraw it sixty times a second.
    if (watch == null) {
      watch = scope.launch {
        viewModel.uiState
            .map { NotificationReading(it) }
            .distinctUntilChanged()
            .collect { reading ->
              if (!reading.isRunning) {
                stopSelf()
                return@collect
              }
              notificationManager.notify(NotificationId, notification(viewModel.uiState.value))
            }
      }
    }
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    scope.cancel()
    super.onDestroy()
  }

  private val notificationManager: NotificationManager
    get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

  private fun notification(state: RealtimeUiState): Notification {
    notificationManager.createNotificationChannel(
        NotificationChannel(ChannelId, "Listening", NotificationManager.IMPORTANCE_LOW),
    )
    val open =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    val stop =
        PendingIntent.getService(
            this,
            1,
            Intent(this, RealtimeSessionService::class.java).setAction(ActionStop),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    return NotificationCompat.Builder(this, ChannelId)
        .setSmallIcon(R.drawable.ic_glyph_glasses)
        .setContentTitle("Listening")
        .setContentText(notificationLine(state))
        // The elapsed readout the screen refuses to draw, drawn here where there is no strip
        // to say *running* — and drawn by the platform, so it ticks without an update.
        .setWhen(state.session.startedAt)
        .setShowWhen(true)
        .setUsesChronometer(true)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(open)
        .addAction(0, "Stop", stop)
        .build()
  }

  private companion object {
    const val ChannelId = "session"
    const val NotificationId = 1
    const val ActionStop = "com.meta.pixelandtexel.birdspotter.action.STOP_SESSION"

    /** The one line under the title: whose ears, and what has landed. */
    fun notificationLine(state: RealtimeUiState): String {
      val ears =
          when (state.sourceKind) {
            CaptureSourceKind.GLASSES -> "On the glasses"
            CaptureSourceKind.SIMULATED -> "Simulated"
            CaptureSourceKind.PHONE -> "On the phone"
          }
      val birds = state.session.events.count { it is SessionEvent.Bird }
      return when (birds) {
        0 -> ears
        1 -> "$ears · 1 bird"
        else -> "$ears · $birds birds"
      }
    }

    /** What the notification is drawn from, so only a change in it redraws. */
    data class NotificationReading(
        val isRunning: Boolean,
        val sourceKind: CaptureSourceKind,
        val eventCount: Int,
    ) {
      constructor(
          state: RealtimeUiState,
      ) : this(
          isRunning = state.isRunning,
          sourceKind = state.sourceKind,
          eventCount = state.session.events.size,
      )
    }
  }
}

/**
 * Starts the service for a run that has begun — see [RealtimeSessionService].
 *
 * The platform refuses a start from an app it cannot see, and says so by throwing here rather than
 * in the service. Logged rather than propagated: the session is still running in the process for as
 * long as the process lasts, and a refused service is not a reason to end it.
 */
fun Context.startRealtimeSessionService() {
  try {
    ContextCompat.startForegroundService(this, Intent(this, RealtimeSessionService::class.java))
  } catch (error: Exception) {
    BirdLog.error(LogCategory.SESSION, error) {
      "session service — the platform would not let it start from here"
    }
  }
}
