/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.display

import android.content.Context
import android.content.SharedPreferences

/**
 * The display screen's custom card, as the presenter last set it up: which bird, and the line to
 * show in place of the catalog's description.
 *
 * SharedPreferences, for the same reason
 * [com.meta.pixelandtexel.birdspotter.data.demo.DemoSettingsStore] is: a custom card is presenter
 * configuration, not something the app *made*. `journal.db` stores outings and `catalog.db` is a
 * read-only seed — and staying out of the seed is the point: the catalog's own description is
 * untouched, and taking the custom card down is a clear, not a database migration.
 *
 * **Written on the keystroke, not on the way out.** A card is typically set up before the demo and
 * shown during it, with an app restart anywhere in between; a message that only survived a graceful
 * exit is a message that appears to forget at random.
 */
class DisplaySettingsStore(private val prefs: SharedPreferences) {

  /**
   * The chosen bird's catalog slug, or null when none has been chosen. A slug is stable by contract
   * — see [com.meta.pixelandtexel.birdspotter.data.catalog.Species] — so a stored one stays good
   * across a seed bump; one that still fails to resolve reads as unchosen.
   */
  var customBirdId: String?
    get() = prefs.getString(KEY_CUSTOM_BIRD_ID, null)?.ifEmpty { null }
    set(value) {
      prefs.edit().putString(KEY_CUSTOM_BIRD_ID, value.orEmpty()).apply()
    }

  /** The presenter's line. Blank means none has been written. */
  var customMessage: String
    get() = prefs.getString(KEY_CUSTOM_MESSAGE, null).orEmpty()
    set(value) {
      prefs.edit().putString(KEY_CUSTOM_MESSAGE, value).apply()
    }

  companion object {
    private const val KEY_CUSTOM_BIRD_ID = "display.customBirdId"
    private const val KEY_CUSTOM_MESSAGE = "display.customMessage"

    /** Opens the store over the app's preferences. */
    fun open(context: Context) = DisplaySettingsStore(
        context.getSharedPreferences("display_settings", Context.MODE_PRIVATE),
    )
  }
}
