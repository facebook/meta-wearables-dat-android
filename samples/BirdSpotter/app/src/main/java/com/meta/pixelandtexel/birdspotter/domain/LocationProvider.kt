/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.domain

/**
 * A WGS84 point — latitude and longitude in degrees.
 *
 * It lives beside the interface that produces it, the way [IdentifyQuery] sits with
 * [BirdCatalogRepository]. The offline-map proposal projects the same `Coordinate` for its region
 * polygons, so the name is chosen to serve both when that lands.
 */
data class Coordinate(
    val latitude: Double,
    val longitude: Double,
)

/**
 * The phone's GPS, as one honest question: *where are we, right now?*
 *
 * One shot, not a `Flow` — a sighting is stamped once, at the moment it is logged, so the wizard
 * asks for a single fix rather than subscribing to movement. The interface is the mirrored surface;
 * the engine behind it — `LocationManager` — is location plumbing the architecture note keeps
 * idiomatic, the same call it makes for [AudioClipPlayer]'s engine.
 *
 * `null` is the whole failure surface: authorization not granted, location services off, or no fix
 * before the attempt gives up. Gating the Identify tab on the permission narrows the first of those
 * and removes none of the others. An outing whose fix never arrived simply carries no coordinates —
 * [com.meta.pixelandtexel.birdspotter.data.journal.Outing.latitude] and
 * [longitude][com.meta.pixelandtexel.birdspotter.data.journal.Outing.longitude] are nullable for
 * exactly this — so a caller treats `null` as "no stamp", never as an error to handle.
 */
interface LocationProvider {
  /** The current position, or `null` if it cannot be obtained (denied, disabled, or timed out). */
  suspend fun currentCoordinate(): Coordinate?
}
