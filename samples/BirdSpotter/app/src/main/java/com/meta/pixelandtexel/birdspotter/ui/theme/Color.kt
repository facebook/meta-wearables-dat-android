/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * BirdSpotter palette — the "specimen cabinet" direction: museum-lacquer ground, cool paper
 * (deliberately not warm cream), aged brass as the single accent.
 *
 * Provisional until the full visual-direction pass; the token names are the stable part.
 */
data class BirdSpotterColors(
    val ink: Color,
    val paper: Color,
    val paperRaised: Color,
    val lacquer: Color,
    val lacquerRaised: Color,
    val lacquerHigh: Color,
    val gilt: Color,
    val verdigris: Color,
    /**
     * The cabinet's red, and the palette's only one.
     *
     * A pigment name beside [gilt] and [verdigris] rather than a signal red, and muted for the same
     * reason those are: a system-alert red would be the one colour on screen not drawn from the
     * cabinet, and it would shout on a page whose loudest mark until now was a gold plate.
     *
     * It is the ink of **stopping and undoing**, and it is spent on exactly two controls: the one
     * that ends a running session (`StopControl`) and a destructive `ActionButton`. Both are the
     * same sentence — *this ends something* — which is why they share one red rather than
     * introducing a second. A second red is how a palette starts meaning nothing.
     *
     * It is never a status, never a chip, and never an error message's colour.
     */
    val vermilion: Color,
    /**
     * A surface, despite the name — [gilt] laid over the ground thinly enough to tint it rather
     * than colour it. What a control sits in when it wants to belong to the page's warm marks (the
     * eyebrow, the section plates) without competing with the one card.
     *
     * Carried at low alpha rather than baked to a hex so it composites over [paper] and [lacquer]
     * alike, the way [rule] does. Dark takes a little more of it: a warm wash registers less
     * against the lacquer ground than against paper.
     */
    val giltWash: Color,
    /**
     * [giltWash]'s green counterpart, and what a box you type in is filled with.
     *
     * The same wash at the same alpha, laid in [verdigris] instead of [gilt], because a field is
     * not a label: gilt is the ink the app *names* things in, and a page whose search box, section
     * plates and named birds are all one warm colour gives a watcher nothing to aim at. Verdigris
     * is what the app asks you to *act* in — the buttons, the claim, Save — and a field is the
     * quietest thing on that list, so it takes the same pigment at a whisper.
     */
    val verdigrisWash: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textFaint: Color,
    val rule: Color,
) {
  companion object {
    val light = BirdSpotterColors(
        ink = Color(0xFF10171A),
        paper = Color(0xFFE9ECE6),
        paperRaised = Color(0xFFF3F5F0),
        lacquer = Color(0xFF10171A),
        lacquerRaised = Color(0xFF1A2429),
        lacquerHigh = Color(0xFF26333A),
        gilt = Color(0xFF8A6A28),
        verdigris = Color(0xFF456A60),
        vermilion = Color(0xFF9C3A28),
        giltWash = Color(0x248A6A28),
        verdigrisWash = Color(0x24456A60),
        textPrimary = Color(0xFF10171A),
        textSecondary = Color(0xFF56635F),
        textFaint = Color(0xFF7C8985),
        rule = Color(0x2910171A),
    )

    val dark = BirdSpotterColors(
        ink = Color(0xFF0D1315),
        paper = Color(0xFF0D1315),
        paperRaised = Color(0xFF151E21),
        lacquer = Color(0xFF10171A),
        lacquerRaised = Color(0xFF1A2429),
        lacquerHigh = Color(0xFF26333A),
        gilt = Color(0xFFC6A45E),
        verdigris = Color(0xFF6E9A8E),
        vermilion = Color(0xFFC25C46),
        giltWash = Color(0x29C6A45E),
        verdigrisWash = Color(0x296E9A8E),
        textPrimary = Color(0xFFE9ECE6),
        textSecondary = Color(0xFF98A6A2),
        textFaint = Color(0xFF71807B),
        rule = Color(0x26E9ECE6),
    )
  }
}
