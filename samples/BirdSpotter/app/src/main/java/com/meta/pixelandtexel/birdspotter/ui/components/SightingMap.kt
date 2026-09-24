/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.meta.pixelandtexel.birdspotter.R
import com.meta.pixelandtexel.birdspotter.domain.Coordinate
import com.meta.pixelandtexel.birdspotter.domain.SightingLocation
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme
import com.meta.pixelandtexel.birdspotter.ui.theme.glyph

/**
 * How close the map sits to its pin, named by what you'd see rather than by a raw zoom number.
 *
 * The unit is the divergence: map engines disagree about what a zoom number means — Google counts
 * in power-of-two steps, others frame by a span in metres — so a raw "15" doesn't travel. The role
 * is what the app names and what a caller asks for; [googleZoom] is the engine's own number.
 */
enum class SightingMapZoom(val googleZoom: Float) {
  NEIGHBORHOOD(15f),
  CITY(12f),
  REGION(9f),
}

/**
 * A sighting's location as a small map with one pin.
 *
 * Non-interactive by default — Google's *lite mode*, a single static bitmap. That is the right
 * default for a record inside a scrolling page: it draws far cheaper than a live `MapView`, and a
 * live map here would fight the page's own scroll for every drag. A tap doesn't pan it; it calls
 * [onTap], which the sighting page routes to the platform maps app. Pass [interactive] only when a
 * screen genuinely wants pan and zoom in place.
 *
 * The tiles are pulled toward one quiet look. Google's cartography is fully styleable, so
 * `res/raw/map_style_light.json` / `map_style_dark.json` reshape them toward a muted standard
 * rendering with points of interest excluded. The JSONs are authored from
 * [BirdSpotterColors][com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterColors]: cool-paper
 * ground, verdigris-leaning water and parks, labels at the text tokens, POI badges and transit off
 * so the one pin is the only mark. JSON styling rather than a cloud map ID, deliberately — it is
 * the only styling lite mode supports, it needs no Google-side console state, and the files live in
 * the repo where a palette change can review them. Lite mode also ignores the SDK's own dark colour
 * scheme, so dark is the same move the app makes: swap files with the theme. Tiles can only be
 * nudged, though, where the marker is drawn outright — so the brand rides [SightingPin], not the
 * cartography.
 *
 * A `@Preview` has no Google Play services behind it to render tiles, so it shows a labelled
 * placeholder instead — the same `LocalInspectionMode` signal [CatalogPhoto] reads. The live map
 * also renders blank until a `MAPS_API_KEY` is supplied (see SETUP.md); the sighting page keeps the
 * coordinate and the Open-in-Maps row beneath it either way, so the screen stays useful.
 */
@Composable
fun SightingMap(
    location: SightingLocation,
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    zoom: SightingMapZoom = SightingMapZoom.NEIGHBORHOOD,
    onTap: (() -> Unit)? = null,
) {
  if (LocalInspectionMode.current) {
    SightingMapPlaceholder(location, modifier)
    return
  }

  val target = LatLng(location.coordinate.latitude, location.coordinate.longitude)
  val cameraPositionState = rememberCameraPositionState {
    position = CameraPosition.fromLatLngZoom(target, zoom.googleZoom)
  }

  val context = LocalContext.current
  // The theme's own default signal: the palette and the cartography swap together.
  val dark = isSystemInDarkTheme()
  val colors = BirdSpotterTheme.colors

  Box(modifier) {
    GoogleMap(
        modifier = Modifier.matchParentSize(),
        googleMapOptionsFactory = { GoogleMapOptions().liteMode(!interactive) },
        cameraPositionState = cameraPositionState,
        properties =
            MapProperties(
                mapType = MapType.NORMAL,
                mapStyleOptions =
                    MapStyleOptions.loadRawResourceStyle(
                        context,
                        if (dark) R.raw.map_style_dark else R.raw.map_style_light,
                    ),
            ),
        uiSettings =
            MapUiSettings(
                compassEnabled = false,
                mapToolbarEnabled = false,
                zoomControlsEnabled = false,
                scrollGesturesEnabled = interactive,
                zoomGesturesEnabled = interactive,
                rotationGesturesEnabled = interactive,
                tiltGesturesEnabled = interactive,
            ),
    ) {
      MarkerComposable(
          // Keys: re-rasterise the pin when its tokens change with the palette.
          colors.gilt,
          colors.paperRaised,
          colors.ink,
          state = rememberMarkerState(position = target),
          // The tail's tip sits on the coordinate, not the pin's centre.
          anchor = Offset(0.5f, 1f),
          title = location.title,
          snippet = location.subtitle,
      ) {
        SightingPin()
      }
    }

    // A lite-mode map is a static tile that doesn't reliably report taps, and the whole tile
    // should be the affordance anyway — so a transparent layer over it carries [onTap]. Only
    // when non-interactive; a live map keeps its own gestures.
    if (!interactive && onTap != null) {
      Box(Modifier.matchParentSize().clickable(onClick = onTap))
    }
  }
}

/**
 * The pin's drawing, in numbers `SightingPin` on both platforms copies exactly. Not spacing: these
 * are the strokes of one drawing, and no more come from `BirdSpotterTheme.space` than the glyphs'
 * paths do.
 */
private object SightingPinMetrics {
  val BadgeDiameter = 34.dp
  val RingWidth = 2.dp
  val GlyphSize = 18.dp
  val TailWidth = 12.dp
  val TailHeight = 7.dp

  /** How far the tail tucks up under the badge, so the join can never open into a seam. */
  val TailOverlap = 3.dp
}

/**
 * The sighting marker both platforms draw stroke for stroke: the swallow on a brass badge, ringed
 * in raised paper, over a short tail that puts a point on the coordinate.
 *
 * The badge is `gilt` because the pin is the map's one accent — the palette's own rule — and the
 * palette already keeps gilt legible on its mode's ground, so the same tokens carry both schemes:
 * brass reads dark on the light map and light on the dark one. The engine's stock teardrop is the
 * one mark on this screen the app hasn't drawn, which is why it is replaced rather than tinted.
 */
@Composable
private fun SightingPin() {
  val colors = BirdSpotterTheme.colors
  Box(
      modifier =
          Modifier.size(
              width = SightingPinMetrics.BadgeDiameter,
              height = SightingPinMetrics.BadgeDiameter + SightingPinMetrics.TailHeight,
          ),
      contentAlignment = Alignment.TopCenter,
  ) {
    // The tail first, so it sits behind the badge; its tip is the marker's anchor.
    Canvas(Modifier.matchParentSize()) {
      val top = (SightingPinMetrics.BadgeDiameter - SightingPinMetrics.TailOverlap).toPx()
      val halfWidth = SightingPinMetrics.TailWidth.toPx() / 2f
      val tail =
          Path().apply {
            moveTo(size.width / 2f - halfWidth, top)
            lineTo(size.width / 2f + halfWidth, top)
            lineTo(size.width / 2f, size.height)
            close()
          }
      drawPath(tail, colors.gilt)
    }
    Box(
        modifier =
            Modifier.size(SightingPinMetrics.BadgeDiameter)
                .background(colors.gilt, CircleShape)
                .border(SightingPinMetrics.RingWidth, colors.paperRaised, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
      Icon(
          painter = glyph(BirdSpotterTheme.glyphs.identify),
          contentDescription = null,
          tint = colors.ink,
          modifier = Modifier.size(SightingPinMetrics.GlyphSize),
      )
    }
  }
}

/**
 * What a preview (or a build with no Maps key) shows where the tiles would be: a muted card
 * carrying the pin's label, so the layout still reads.
 */
@Composable
private fun SightingMapPlaceholder(location: SightingLocation, modifier: Modifier = Modifier) {
  Box(
      modifier = modifier.background(BirdSpotterTheme.colors.giltWash),
      contentAlignment = Alignment.Center,
  ) {
    Text(
        text = location.title,
        style = BirdSpotterTheme.type.label,
        color = BirdSpotterTheme.colors.textFaint,
    )
  }
}

@Preview
@Composable
private fun SightingMapPreview() {
  BirdSpotterTheme {
    SightingMap(
        location =
            SightingLocation(
                coordinate = Coordinate(39.1031, -84.5120),
                title = "American Robin",
                subtitle = "Eden Park",
            ),
        modifier = Modifier.fillMaxWidth().height(180.dp),
    )
  }
}
