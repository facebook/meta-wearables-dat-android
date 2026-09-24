/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.demo

import android.content.Context
import android.content.SharedPreferences
import com.meta.pixelandtexel.birdspotter.domain.BirdLog
import com.meta.pixelandtexel.birdspotter.domain.DemoPreset
import com.meta.pixelandtexel.birdspotter.domain.LogCategory
import kotlinx.serialization.encodeToString

/**
 * The Demo Director's saved state: the presets the operator has, and which one — if any — is armed.
 *
 * SharedPreferences, not a database table: a preset is operator configuration the way the database
 * design's "deliberately absent" list says (`journal.db` stores what the user *made*, `catalog.db`
 * is a read-only seed, and a demo preset is neither). Losing one costs a few taps, which is exactly
 * the durability preferences promise.
 *
 * **The shipped preset is a template, not a fixture.** [shipped] is decoded from the bundled
 * `presets/full-flow.json` and is never itself the thing that plays; on first run it is *copied*
 * into the stored list, where it becomes an ordinary preset the operator can rename, edit and
 * delete like any other. That copy is what the Director reads.
 *
 * The cost of seeding — improving the shipped file no longer reaches a device that has already run
 * the app — is paid for by [resetToShipped]: [isShippedPresent] goes false the moment the stored
 * copy is edited or deleted, which is what surfaces the button that puts the pristine version back.
 *
 * Arming is explicit from the first launch: seeding arms the copy, and an empty stored id means the
 * deliberate "none" state in which the app never identifies.
 */
class DemoSettingsStore(
    private val prefs: SharedPreferences,
    /** The bundled template, or null when the shipped file is missing or unreadable. */
    val shipped: DemoPreset?,
) {

  init {
    seedIfNeeded()
  }

  /**
   * Every preset the operator has, in saved order.
   *
   * Empty is a legal answer — deleting the last one is allowed, and nothing re-seeds behind the
   * operator's back. Only a *never seeded* store fills itself.
   */
  fun presets(): List<DemoPreset> {
    val stored = prefs.getString(KEY_PRESETS, null) ?: return emptyList()
    return runCatching { DemoPreset.wire.decodeFromString<List<DemoPreset>>(stored) }
        .getOrElse { error ->
          // A blob this build cannot read is not worth crashing the panel over —
          // unknown *fields* are already ignored; this is for a mangled write.
          BirdLog.warning(LogCategory.DEMO) {
            "stored demo presets are unreadable; treating as none — $error"
          }
          emptyList()
        }
  }

  fun savePresets(presets: List<DemoPreset>) {
    prefs.edit().putString(KEY_PRESETS, DemoPreset.wire.encodeToString(presets)).apply()
  }

  /**
   * The preset the Director should read from, or null when identification is off — disarmed, or
   * armed at an id that no longer resolves.
   */
  fun armedPreset(): DemoPreset? {
    val id = prefs.getString(KEY_ARMED, null).orEmpty()
    if (id.isEmpty()) return null
    return presets().firstOrNull { it.id == id }
  }

  /** Arm the preset with [id], or pass null to disarm — the app then never identifies. */
  fun armPreset(id: String?) {
    prefs.edit().putString(KEY_ARMED, id.orEmpty()).apply()
  }

  /**
   * True while some stored preset is still byte-for-byte the shipped one.
   *
   * Value equality rather than an id check, deliberately: editing the seeded copy is as much a
   * departure from the shipped script as deleting it, and both are things the operator may want to
   * undo. False is what surfaces "Reset to starter".
   */
  val isShippedPresent: Boolean
    get() = shipped?.let { template -> presets().any { it == template } } == true

  /**
   * Put the pristine shipped preset back: replacing the stored one that carries its id if it is
   * still there, appending it if it was deleted.
   *
   * Arming is left alone. Restoring a script is not the same as choosing to run it, and silently
   * re-arming would change what the next session plays without being asked.
   */
  fun resetToShipped() {
    val template = shipped ?: return
    val current = presets()
    savePresets(
        if (current.any { it.id == template.id }) {
          current.map { if (it.id == template.id) template else it }
        } else {
          current + template
        },
    )
  }

  /**
   * First run: copy the template in and arm it, so the demo works out of the box before anyone
   * opens the panel.
   *
   * Keyed on the presets key being *absent*, which is the only honest "never seeded" signal — an
   * operator who deletes every preset leaves an empty list behind, and that must stay empty.
   */
  private fun seedIfNeeded() {
    if (prefs.contains(KEY_PRESETS)) return
    val template = shipped ?: return
    savePresets(listOf(template))
    armPreset(template.id)
  }

  companion object {
    private const val KEY_PRESETS = "demo.presets"
    private const val KEY_ARMED = "demo.armedPresetId"
    private const val SHIPPED_ASSET = "presets/full-flow.json"

    /**
     * Opens the store over the app's preferences, decoding the shipped template as it goes. A
     * template that will not decode is a staging bug the parity tests exist to catch before it
     * ships; at runtime it degrades to "nothing to seed" with a log line, the same shape the
     * catalog takes when `catalog.db` is missing.
     */
    fun open(context: Context): DemoSettingsStore {
      val shipped = runCatching {
        context.assets.open(SHIPPED_ASSET).use { it.readBytes().decodeToString() }
      }
          .mapCatching(DemoPreset::decode)
          .getOrElse { error ->
            BirdLog.error(LogCategory.DEMO, error) { "could not load the shipped preset" }
            null
          }
      return DemoSettingsStore(
          prefs = context.getSharedPreferences("demo_settings", Context.MODE_PRIVATE),
          shipped = shipped,
      )
    }
  }
}
