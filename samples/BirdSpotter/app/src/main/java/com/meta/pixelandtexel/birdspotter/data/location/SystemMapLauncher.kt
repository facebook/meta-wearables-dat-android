/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.location

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.meta.pixelandtexel.birdspotter.domain.MapLauncher
import com.meta.pixelandtexel.birdspotter.domain.SightingLocation

/**
 * The [MapLauncher] the app ships: a `geo:` `Intent` the user's chosen maps app answers.
 *
 * The mirror is [MapLauncher], not this class — the `Intent` and the `geo:` URI are precisely the
 * platform plumbing the architecture note keeps idiomatic.
 *
 * `geo:lat,lng?q=lat,lng(Label)` centres the map on the point *and* drops a labelled pin; the bare
 * `geo:lat,lng` only centres, and some apps then show nothing at the spot. The label is URL-encoded
 * because a place or species name carries spaces. Launched from the application context — the tap
 * comes from a Composable with no Activity to hand — so it needs [Intent.FLAG_ACTIVITY_NEW_TASK],
 * the same flag [com.meta.pixelandtexel.birdspotter.data.permissions.SystemPermissionsController]
 * uses to reach Settings. A device with no maps app at all is a no-op, not a crash.
 */
class SystemMapLauncher(context: Context) : MapLauncher {

  private val appContext = context.applicationContext

  override fun open(location: SightingLocation) {
    val (latitude, longitude) = location.coordinate
    val label = Uri.encode(location.title)
    val uri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($label)")
    val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
      appContext.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
      // No maps app installed — nothing to open, and a demo phone without one is no reason
      // to take the sighting page down.
    }
  }
}
