/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.meta.pixelandtexel.birdspotter.R

/*
 * Glyphs — the app's icon set.
 *
 * Every icon in the app is named here and nowhere else. A screen that reaches for a raw
 * drawable — or for a Material icon — is a screen that will drift from its opposite number,
 * which is exactly how the two tab bars ended up drawing different pictures.
 *
 * The drawings ship as `res/drawable/ic_glyph_*`; see `licenses/icons/` for
 * provenance and terms.
 */

/**
 * The mirrored glyph set — every icon the app draws, named once by role and resolved from
 * `res/drawable/`.
 *
 * **Not Material icons, deliberately.** A platform icon set is licensed for that platform, so an
 * icon taken from one is a drawing this design system cannot own. Roles, not pictures: [selected]
 * is a checkmark today, and if it stops being one it stops being one everywhere at once.
 */
class BirdSpotterGlyphs {

  // --- Tab bar ---------------------------------------------------------------------
  //
  // Fill weight, all three. Identify is our own mark and the mark is a solid silhouette;
  // an outline binoculars beside it reads as two icon sets sharing one row.

  /** Binoculars — the field guide. */
  @get:DrawableRes val explore = R.drawable.ic_glyph_explore

  /** The BirdSpotter swallow. Says *bird*, which covers both the photo and the sound path. */
  @get:DrawableRes val identify = R.drawable.ic_glyph_identify

  /** An open book — the life list. */
  @get:DrawableRes val journal = R.drawable.ic_glyph_journal

  // --- Navigation ------------------------------------------------------------------

  /**
   * Back, in a screen that draws its own bar. A caret rather than Material's arrow — the shape that
   * reads as "back" without belonging to any one platform's chrome.
   */
  @get:DrawableRes val back = R.drawable.ic_glyph_back

  /** Dismiss a modal or a wizard. */
  @get:DrawableRes val close = R.drawable.ic_glyph_close

  /** The right-hand caret on a row that opens something. */
  @get:DrawableRes val disclosure = R.drawable.ic_glyph_disclosure

  // --- Controls --------------------------------------------------------------------

  /** A search field's leading mark. */
  @get:DrawableRes val search = R.drawable.ic_glyph_search

  /** Clear a search field. Filled, so it reads as a button rather than as decoration. */
  @get:DrawableRes val clearSearch = R.drawable.ic_glyph_clear_search

  /** A chosen option — wizard answers, filter chips. */
  @get:DrawableRes val selected = R.drawable.ic_glyph_selected

  /** Settings, as a toolbar control. */
  @get:DrawableRes val settings = R.drawable.ic_glyph_settings

  /**
   * The viewfinder's capture control — a ring around a disc, the shape every camera app has taught
   * people to reach for. Ours rather than Phosphor's: the set has no shutter, and the ring-and-disc
   * is a control's shape rather than a picture of one.
   */
  @get:DrawableRes val shutter = R.drawable.ic_glyph_shutter

  /**
   * The viewfinder's light, on. A bolt rather than a bulb, because a bolt is what a camera control
   * has meant since the flashgun.
   */
  @get:DrawableRes val flash = R.drawable.ic_glyph_flash

  /**
   * The same bolt, struck through: the light is off. A second drawing rather than the first one
   * dimmed — a control that is *off* and a control that is *unavailable* would otherwise be the
   * same picture at different opacities, and the panel shows both.
   */
  @get:DrawableRes val flashOff = R.drawable.ic_glyph_flash_off

  // --- Content ---------------------------------------------------------------------

  /** Supplementary detail about a result. */
  @get:DrawableRes val info = R.drawable.ic_glyph_info

  /** The affordance on canned Q&A. */
  @get:DrawableRes val help = R.drawable.ic_glyph_help

  /** A recording — the marker on a species' call. */
  @get:DrawableRes val call = R.drawable.ic_glyph_call

  /**
   * Play a bird's recording. Filled, so it reads as a button rather than as decoration — the same
   * reasoning as [clearSearch].
   */
  @get:DrawableRes val play = R.drawable.ic_glyph_play

  /**
   * Pause the recording. Filled, to match [play] so the transport control does not change weight as
   * it toggles.
   */
  @get:DrawableRes val pause = R.drawable.ic_glyph_pause

  /**
   * The emblem an empty Settings screen is built around. Filled, because at emblem size a
   * regular-weight gear reads as wiry.
   */
  @get:DrawableRes val settingsMark = R.drawable.ic_glyph_settings_mark

  /** Where something happened — the pin beside a session's fix, and a sighting's place. */
  @get:DrawableRes val location = R.drawable.ic_glyph_location

  /** Which way the watcher is facing. */
  @get:DrawableRes val compass = R.drawable.ic_glyph_compass

  /**
   * How high the watcher is aiming — the band the viewfinder is pointed at.
   *
   * An eye rather than an angle: the role is *where they are looking*, which on a phone is
   * whichever way it is tilted and on a pair of glasses will be the wearer's own gaze. The drawing
   * follows the role, so that day changes nothing here.
   */
  @get:DrawableRes val gaze = R.drawable.ic_glyph_gaze

  /**
   * The Meta AI glasses — the card that offers to link them, the settings screen that reports on
   * them, and wherever else the wearable itself needs naming.
   */
  @get:DrawableRes val glasses = R.drawable.ic_glyph_glasses

  /**
   * The same pair, on a head — for glasses that are **being worn**.
   *
   * **Two drawings because the wearer's own state is the thing worth showing.** A pair connected
   * and a pair connected *and on a face* are different facts about whether the session is really
   * seeing anything, and the don signal is the one reading that says which. The bare frames are the
   * resting state; this is the live one.
   *
   * Ours rather than Phosphor's: the set has no eyeglasses-on-a-head, and a role the design system
   * needs is a role it draws.
   */
  @get:DrawableRes val glassesWorn = R.drawable.ic_glyph_glasses_worn

  /**
   * The phone in the watcher's hand, wherever it is one of two devices the app could be using — the
   * session's source pill, most of all. Named [device] rather than `phone` because the role is
   * *this thing you are holding*, and the label beside it reads "On device" for the same reason:
   * what matters is that it is not the glasses.
   */
  @get:DrawableRes val device = R.drawable.ic_glyph_device

  // --- Size anchors ----------------------------------------------------------------
  //
  // The identify wizard's size scale: sparrow → robin → crow → goose, the ladder every
  // printed guide has used for the question.
  //
  // These are the one set here that is *not* fitted to its own box. All four are drawn
  // in a single shared 64x56 box, feet on a common ground line, at their true sizes
  // relative to each other — so a screen draws all four at one identical frame and the
  // proportions come out of the artwork. Fitting each to its own box, the way every
  // other glyph is fitted, would render a sparrow and a goose the same size and throw
  // away the only thing the row says.

  /** Sparrow — stop 1, the smallest anchor. */
  @get:DrawableRes val sizeSparrow = R.drawable.ic_glyph_size_sparrow

  /** Robin — stop 3. */
  @get:DrawableRes val sizeRobin = R.drawable.ic_glyph_size_robin

  /** Crow — stop 5. */
  @get:DrawableRes val sizeCrow = R.drawable.ic_glyph_size_crow

  /** Goose — stop 7, and the one that fills the shared box. */
  @get:DrawableRes val sizeGoose = R.drawable.ic_glyph_size_goose

  // --- The mark --------------------------------------------------------------------
  //
  // The badge, split into its two layers so the splash can turn
  // the rings around a still swallow. Both are registered to the badge's own box —
  // unlike [identify], which refits the swallow alone to the icon grid.

  /** The badge's rings — the swallow's surround. */
  @get:DrawableRes val markRings = R.drawable.ic_glyph_mark_rings

  /** The badge's swallow, at the rings' registration. */
  @get:DrawableRes val markBird = R.drawable.ic_glyph_mark_bird
}

/**
 * `Icon(glyph(BirdSpotterTheme.glyphs.search), contentDescription = …)` — the one way an icon
 * enters a composable.
 *
 * The drawables carry a solid fill, so `Icon` tints them the way it tints a Material vector.
 */
@Composable fun glyph(@DrawableRes id: Int): Painter = painterResource(id)
