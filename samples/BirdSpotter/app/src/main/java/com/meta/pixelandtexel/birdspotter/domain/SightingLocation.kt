/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.domain

/**
 * A point worth showing on a map: a [Coordinate] and the words that label its pin.
 *
 * The map view and the [MapLauncher] both take one of these, so "the American Robin at Eden Park,
 * here" travels as a single value rather than a coordinate plus two loose strings the two callers
 * might word differently. [title] is the pin's name — the species, or "Sighting" when the bird was
 * never identified; [subtitle] is the finer line under it — the place name, absent when there is
 * none.
 *
 * The display strings live here, beside [Coordinate], rather than up in the UI layer because the
 * launcher needs the title too: the native Maps app labels its pin with the same word the in-app
 * map does. Built from a [com.meta.pixelandtexel.birdspotter.data.journal.Sighting] only when it
 * actually carries a fix — see `JournalEntry.location`.
 */
data class SightingLocation(
    val coordinate: Coordinate,
    val title: String,
    val subtitle: String? = null,
)
