/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Spacing — "The Subscriber's Plate".
 *
 * Every value is a multiple of 4. Eight roles, each with one job; if a gap on screen is not one
 * of these, it is a bug rather than a decision. One rule goes with them above all others: that
 * a column sets its rhythm with `Arrangement.spacedBy` *or* with `Spacer`s, never both.
 */

/**
 * The mirrored spacing scale — one role per gap the design names, and the numbers behind them.
 *
 * Roles, not sizes: the space under a section heading is [related], which is what lets Meta's docs
 * show the same design whichever snippet a reader is looking at. A screen that reaches for a
 * literal is a screen that will drift.
 */
@Immutable
data class BirdSpotterSpacing(

    /** 4 — two lines that are one thought: a species name and its binomial. */
    val tight: Dp = 4.dp,

    /** 8 — a mark and its label: an icon and the word beside it. */
    val snug: Dp = 8.dp,

    /** 12 — a heading and the thing it heads. */
    val related: Dp = 12.dp,

    /** 16 — two items in the same block, and the air around a rule. */
    val separate: Dp = 16.dp,

    /**
     * 20 — inside a card's rule. One step under the gutter on purpose, so a card's text sits
     * *within* the page margin instead of lining up with it.
     */
    val cardInset: Dp = 20.dp,

    /**
     * 24 — the page margin. Nothing but a deliberate full-bleed element gets closer to the screen
     * edge than this.
     */
    val gutter: Dp = 24.dp,

    /** 32 — between the sections of a page, and above the first one. */
    val section: Dp = 32.dp,

    /** 40 — the foot of a scrolling page, so the last line clears the navigation bar. */
    val page: Dp = 40.dp,
)
