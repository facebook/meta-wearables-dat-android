/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.diagnostics

import android.util.Log
import com.meta.pixelandtexel.birdspotter.domain.LogCategory
import com.meta.pixelandtexel.birdspotter.domain.LogEntry
import com.meta.pixelandtexel.birdspotter.domain.LogLevel
import com.meta.pixelandtexel.birdspotter.domain.LogSink

/**
 * Puts a line where a developer at a desk will see it — logcat.
 *
 * The [LogCategory] becomes the tag, so `adb logcat -s BirdSpotter.glasses` is a real filter rather
 * than a grep through prose. That is the same job the Diagnostics screen's chips do for someone
 * holding the phone, and the two agree because both read [LogEntry.category].
 *
 * **The tag is prefixed.** `BirdSpotter.` in front of every one keeps a bare `glasses` from
 * colliding with some library's, and lets `-s BirdSpotter.*` pull the whole app's output in one go.
 */
class ConsoleLogSink : LogSink {

  override fun write(entry: LogEntry) {
    val tag = tagFor(entry.category)
    when (entry.level) {
      LogLevel.DEBUG -> Log.d(tag, entry.message)
      LogLevel.INFO -> Log.i(tag, entry.message)
      LogLevel.WARNING -> Log.w(tag, entry.message)
      LogLevel.ERROR -> Log.e(tag, entry.message)
    }
  }

  private companion object {
    const val TagPrefix = "BirdSpotter."

    /**
     * Built once for the whole set rather than concatenated per line — this runs on the hot path,
     * and the set is nine strings.
     */
    val Tags: Map<LogCategory, String> = LogCategory.entries.associateWith { "$TagPrefix${it.id}" }

    fun tagFor(category: LogCategory): String = Tags[category] ?: "${TagPrefix}app"
  }
}
