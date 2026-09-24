/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.diagnostics

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.meta.pixelandtexel.birdspotter.domain.LogLevel

/**
 * How the diagnostic log is set: whether it writes to files at all, and how far down it records.
 *
 * SharedPreferences, for the same reason
 * [com.meta.pixelandtexel.birdspotter.data.demo.DemoSettingsStore] and
 * [com.meta.pixelandtexel.birdspotter.data.session.SessionSettingsStore] are — this is how somebody
 * has their instrument set, not something the app made.
 *
 * **Read at launch, before the first line.** The composition root asks for both values while it is
 * installing the sinks, so a raised floor is in force from the first line of the run rather than
 * from whenever Settings first opened.
 */
class DiagnosticsSettingsStore(private val prefs: SharedPreferences) {

  /**
   * Whether lines are written to disk. On by default — a log that has to be switched on before it
   * is useful is a log that was off during the run you needed it for.
   *
   * The logcat sink is not affected: turning this off stops the files, not the developer's own
   * console.
   */
  var isFileLoggingEnabled: Boolean
    get() = prefs.getBoolean(KeyFileLogging, true)
    set(value) = prefs.edit { putBoolean(KeyFileLogging, value) }

  /**
   * The floor lines have to clear. [LogLevel.DEBUG] unless somebody has raised it — see
   * [com.meta.pixelandtexel.birdspotter.domain.BirdLog.minimumLevel] for why the noisy default is
   * the right one here.
   */
  var minimumLevel: LogLevel
    get() = prefs.getString(KeyMinimumLevel, null)?.let(LogLevel::parse) ?: LogLevel.DEBUG
    set(value) = prefs.edit { putString(KeyMinimumLevel, value.id) }

  companion object {
    private const val KeyFileLogging = "diagnostics.fileLogging"
    private const val KeyMinimumLevel = "diagnostics.minimumLevel"

    /** Opens the store over the app's preferences. */
    fun open(context: Context) = DiagnosticsSettingsStore(
        context.getSharedPreferences("diagnostics_settings", Context.MODE_PRIVATE),
    )
  }
}
