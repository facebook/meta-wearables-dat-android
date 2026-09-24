/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.navigation

import kotlinx.serialization.Serializable

/**
 * Every address in the app.
 *
 * One list, mirrored case-for-case, which is what lets a feature that navigates from outside the UI
 * — "Hey Meta, what bird is that?" landing the user in Identify — be the same one-line call rather
 * than a mechanism per platform.
 *
 * The `…Graph` entries are containers rather than screens. Each one owns a tab's back stack: push
 * Settings inside the Journal tab, switch to Explore and back, and Settings is still there.
 * Navigating to a graph lands on its start destination.
 */
@Serializable
sealed interface Route {
  @Serializable data object ExploreGraph : Route

  @Serializable data object Explore : Route

  @Serializable data object IdentifyGraph : Route

  @Serializable data object Identify : Route

  /**
   * The step-by-step identification wizard, pushed from [Identify].
   *
   * One route for the whole five-question flow: the stations share their answers and system back
   * walks them, which is a screen's internal state, not five addresses.
   */
  @Serializable data object IdentifyWizard : Route

  @Serializable data object JournalGraph : Route

  @Serializable data object Journal : Route

  @Serializable data object Settings : Route

  /**
   * Settings → Meta AI Glasses: where the link stands — registration, the device, camera access —
   * and the way back out of it. Offered only once registration has happened; before that, Settings
   * shows the set-up card instead.
   */
  @Serializable data object GlassesSettings : Route

  /**
   * Settings → Meta AI Glasses → Test ASR: a session opened to find out whether this pair
   * transcribes, and what it hears when it does.
   */
  @Serializable data object SpeechTest : Route

  /**
   * Settings → Meta AI Glasses → Camera: one photograph at chosen settings, and what the crossing
   * cost.
   */
  @Serializable data object GlassesCamera : Route

  /**
   * Settings → Meta AI Glasses → Display Screen: a session opened to put any bird's card on the
   * glasses' panel by hand — it stays up until it is cleared, replaced, or the screen is left.
   */
  @Serializable data object GlassesDisplay : Route

  /** Settings → Diagnostics: how the app's own event log is set, and the runs it has recorded. */
  @Serializable data object Diagnostics : Route

  /**
   * One recorded run's page: its lines, filtered by category and level. The [name] is the log
   * file's own name, which carries the run's start time — see
   * [com.meta.pixelandtexel.birdspotter.data.diagnostics.DiagnosticsLogStore].
   */
  @Serializable data class DiagnosticsFile(val name: String) : Route

  /** Settings → Demo Director: the preset picker that scripts what the app "identifies". */
  @Serializable data object DemoDirector : Route

  /**
   * One preset's page — its three sections as cards, and the duplicate/rename/delete actions. The
   * [presetId] is the preset's stored id; the shipped one's ships in its JSON.
   */
  @Serializable data class DemoDirectorPreset(val presetId: String) : Route

  /**
   * One STT-input row's editor. A null [questionId] opens a new row rather than an existing one —
   * the same address adds and amends, because the editor hands back a whole row either way and the
   * save is an upsert.
   */
  @Serializable
  data class DemoDirectorQuestion(val presetId: String, val questionId: String? = null) : Route

  /**
   * One photo row's editor. A null [photoId] adds. There is no address for what happens past the
   * end of the list: that is fixed, not authored.
   */
  @Serializable
  data class DemoDirectorPhoto(val presetId: String, val photoId: String? = null) : Route

  /** One ambient-input row's editor. A null [callId] adds. */
  @Serializable
  data class DemoDirectorAmbient(val presetId: String, val callId: String? = null) : Route

  /**
   * One species' page in the field guide.
   *
   * Registered in all three tab graphs rather than one — see [birdDestinations]. The [speciesId] is
   * the catalog slug (`northern-cardinal`), which is a stable id by contract (see
   * [com.meta.pixelandtexel.birdspotter.data.catalog.Species]), so an address stays good across a
   * seed bump.
   */
  @Serializable data class BirdDetail(val speciesId: String) : Route

  /**
   * One journal entry's page — an outing and everything it confirmed — pushed from a Journal row.
   * The same surface the live flow's post-stop review will land on.
   *
   * Registered in the Journal graph alone — entries are opened only from the Journal — unlike
   * [BirdDetail], which every tab reaches. The [outingId] is the root row's UUID in `journal.db`.
   */
  @Serializable data class OutingDetail(val outingId: String) : Route
}

/**
 * The bottom bar's three destinations, in bar order.
 *
 * Kept apart from [Route] because a tab is chrome — it needs a label — where a route is only an
 * address.
 *
 * The glyph is deliberately *not* here. It belongs to `BirdSpotterTheme.glyphs`, which is the one
 * place any icon is named, and the bar picks it up there. Holding a drawable id on the enum would
 * put a second source of icon truth in the app, which is how the bar came to draw a different
 * picture from the rest of the app in the first place.
 */
enum class Tab(
    val label: String,
    val graph: Route,
    /**
     * The [graph]'s start destination — where tapping the already-selected tab returns to.
     *
     * Stated rather than looked up. `findStartDestination()` would give the same answer, but only
     * for a graph already on the back stack, and the tab bar has to know where a tab goes whether
     * or not the user has been there yet.
     */
    val start: Route,
) {
  EXPLORE("Explore", Route.ExploreGraph, Route.Explore),
  IDENTIFY("Identify", Route.IdentifyGraph, Route.Identify),
  JOURNAL("Journal", Route.JournalGraph, Route.Journal),
}
