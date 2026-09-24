/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.domain

/**
 * Hands a sighting's location off to the phone's own maps app.
 *
 * The one thing the in-app map deliberately doesn't do — routing, search, "where am I relative to
 * this" — is exactly what tapping through to the phone's own maps app gets for free rather than
 * rebuilding it. So this is the whole surface: one verb, [open].
 *
 * The interface is the mirrored surface. The engine behind it — a `geo:` `Intent` — is the platform
 * plumbing the architecture note keeps idiomatic, the same call it makes for [LocationProvider]'s
 * manager and [AudioClipPlayer]'s engine.
 */
interface MapLauncher {
  /**
   * Opens [location] in the maps app, its pin dropped on the point and labelled
   * [SightingLocation.title].
   */
  fun open(location: SightingLocation)
}
