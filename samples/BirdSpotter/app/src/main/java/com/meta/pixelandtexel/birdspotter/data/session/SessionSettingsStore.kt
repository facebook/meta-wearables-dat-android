/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.session

import android.content.Context
import android.content.SharedPreferences
import com.meta.pixelandtexel.birdspotter.domain.StripReading

/**
 * What the real-time screen remembers between sessions.
 *
 * SharedPreferences, for the same reason
 * [com.meta.pixelandtexel.birdspotter.data.demo.DemoSettingsStore] is: this is how somebody likes
 * their instrument set, not something the app *made*. `journal.db` stores outings and `catalog.db`
 * is a read-only seed; a preference about how a picture is drawn is neither, and losing one costs a
 * tap.
 *
 * **Written on the tap, not on the way out.** A session can end by being saved, discarded,
 * backgrounded or killed, and a preference that only survived one of those four is a preference
 * that appears to forget at random.
 *
 * One key today, and it is still a store rather than a loose preferences call in a composable: the
 * screen has exactly one place to ask, the key is spelled once, and the unknown-value case is
 * answered here instead of at every call site.
 */
class SessionSettingsStore(private val prefs: SharedPreferences) {

  /**
   * How the watcher last had the strip drawn.
   *
   * **The sonogram is the answer to anything unreadable**, including a first run and a value
   * written by some future build this one does not know: it is the reading the screen was born with
   * and the one the rest of the feature is written around.
   */
  var stripReading: StripReading
    get() {
      val stored = prefs.getString(KEY_STRIP_READING, null)
      return StripReading.entries.firstOrNull { it.name.lowercase() == stored }
          ?: StripReading.SONOGRAM
    }
    set(value) {
      prefs.edit().putString(KEY_STRIP_READING, value.name.lowercase()).apply()
    }

  companion object {
    private const val KEY_STRIP_READING = "session.stripReading"

    /** Opens the store over the app's preferences. */
    fun open(context: Context) = SessionSettingsStore(
        context.getSharedPreferences("session_settings", Context.MODE_PRIVATE),
    )
  }
}
