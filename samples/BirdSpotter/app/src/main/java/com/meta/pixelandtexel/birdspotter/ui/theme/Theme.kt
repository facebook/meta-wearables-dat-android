/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Both expose the same four token sets — `BirdSpotterTheme.colors`, `.type`, `.space` and `.glyphs`
 * — with identical member names.
 *
 * Note there is no glasses-display equivalent of this: the DAT display API takes three text presets
 * (heading / body / meta) and two colors, rendered in Meta's own design system. Everything here is
 * the phone app only.
 */
@Composable
fun BirdSpotterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
  val colors = if (darkTheme) BirdSpotterColors.dark else BirdSpotterColors.light
  val type = BirdSpotterTypography()
  val space = BirdSpotterSpacing()
  val glyphs = BirdSpotterGlyphs()

  CompositionLocalProvider(
      LocalBirdSpotterColors provides colors,
      LocalBirdSpotterTypography provides type,
      LocalBirdSpotterSpacing provides space,
      LocalBirdSpotterGlyphs provides glyphs,
  ) {
    MaterialTheme(
        colorScheme = colors.toMaterialColorScheme(darkTheme),
        typography = materialTypographyFrom(type),
        content = content,
    )
  }
}

/**
 * Token accessors: `BirdSpotterTheme.colors.gilt`, `BirdSpotterTheme.type.plate`,
 * `BirdSpotterTheme.space.gutter`, `BirdSpotterTheme.glyphs.search`.
 */
object BirdSpotterTheme {
  val colors: BirdSpotterColors
    @Composable @ReadOnlyComposable get() = LocalBirdSpotterColors.current

  val type: BirdSpotterTypography
    @Composable @ReadOnlyComposable get() = LocalBirdSpotterTypography.current

  val space: BirdSpotterSpacing
    @Composable @ReadOnlyComposable get() = LocalBirdSpotterSpacing.current

  val glyphs: BirdSpotterGlyphs
    @Composable @ReadOnlyComposable get() = LocalBirdSpotterGlyphs.current
}

private val LocalBirdSpotterColors = staticCompositionLocalOf { BirdSpotterColors.light }
private val LocalBirdSpotterTypography = staticCompositionLocalOf { BirdSpotterTypography() }
private val LocalBirdSpotterSpacing = staticCompositionLocalOf { BirdSpotterSpacing() }
private val LocalBirdSpotterGlyphs = staticCompositionLocalOf { BirdSpotterGlyphs() }

/**
 * Material 3 scheme derived from the palette.
 *
 * Every role is set deliberately. Material fills unspecified roles from its *baseline purple*, so a
 * partial scheme leaves lavender showing through in exactly the places you don't look —
 * NavigationBar's `surfaceContainer` and its `secondaryContainer` selection pill being the ones
 * that bit us.
 *
 * Dynamic color is not offered at all. Material You repaints the scheme from the user's wallpaper,
 * which would make the app look different on every demo device.
 *
 * This mapping is Android-only plumbing. The mirrored surface is `BirdSpotterColors`.
 */
private fun BirdSpotterColors.toMaterialColorScheme(darkTheme: Boolean): ColorScheme {
  // Container tints, mixed from the accents toward the ground.
  val ground = if (darkTheme) lacquer else paper
  val primaryContainer = lerp(gilt, ground, if (darkTheme) 0.74f else 0.84f)
  val secondaryContainer = lerp(verdigris, ground, if (darkTheme) 0.76f else 0.86f)
  // Blend the container foreground toward the *text* colour, not the surface — in dark
  // mode `paperRaised` is itself dark, so blending toward it makes selected states
  // dimmer than unselected ones.
  val contrastPole = if (darkTheme) textPrimary else ink
  val onPrimaryContainer = lerp(gilt, contrastPole, 0.55f)
  val onSecondaryContainer = lerp(verdigris, contrastPole, 0.55f)

  // Surface elevation ladder. Dark steps up toward the highlight, light steps down.
  val step = if (darkTheme) lacquerHigh else ink
  fun surfaceAt(amount: Float) = lerp(paperRaised, step, amount)

  return if (darkTheme) {
    darkColorScheme(
        primary = gilt,
        onPrimary = lacquer,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = BirdSpotterColors.light.gilt,
        secondary = verdigris,
        onSecondary = lacquer,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = textSecondary,
        onTertiary = lacquer,
        tertiaryContainer = lacquerRaised,
        onTertiaryContainer = textPrimary,
        background = paper,
        onBackground = textPrimary,
        surface = paper,
        onSurface = textPrimary,
        surfaceVariant = lacquerRaised,
        onSurfaceVariant = textSecondary,
        surfaceTint = gilt,
        inverseSurface = BirdSpotterColors.light.paper,
        inverseOnSurface = BirdSpotterColors.light.textPrimary,
        surfaceDim = paper,
        surfaceBright = surfaceAt(0.30f),
        surfaceContainerLowest = lerp(paper, lacquer, 0.5f),
        surfaceContainerLow = paperRaised,
        surfaceContainer = surfaceAt(0.16f),
        surfaceContainerHigh = surfaceAt(0.28f),
        surfaceContainerHighest = surfaceAt(0.40f),
        error = Color(0xFFE39B8B),
        onError = lacquer,
        errorContainer = Color(0xFF5C2A1E),
        onErrorContainer = Color(0xFFF6D6CD),
        outline = textFaint,
        outlineVariant = rule,
        scrim = Color(0xFF000000),
    )
  } else {
    lightColorScheme(
        primary = gilt,
        onPrimary = paperRaised,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = BirdSpotterColors.dark.gilt,
        secondary = verdigris,
        onSecondary = paperRaised,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = textSecondary,
        onTertiary = paperRaised,
        tertiaryContainer = lerp(textSecondary, paper, 0.85f),
        onTertiaryContainer = ink,
        background = paper,
        onBackground = textPrimary,
        surface = paper,
        onSurface = textPrimary,
        surfaceVariant = lerp(paper, ink, 0.07f),
        onSurfaceVariant = textSecondary,
        surfaceTint = gilt,
        inverseSurface = BirdSpotterColors.dark.paperRaised,
        inverseOnSurface = BirdSpotterColors.dark.textPrimary,
        surfaceDim = surfaceAt(0.10f),
        surfaceBright = paperRaised,
        surfaceContainerLowest = Color(0xFFF8FAF5),
        surfaceContainerLow = paperRaised,
        surfaceContainer = surfaceAt(0.035f),
        surfaceContainerHigh = surfaceAt(0.07f),
        surfaceContainerHighest = surfaceAt(0.11f),
        error = Color(0xFF8C2F1B),
        onError = paperRaised,
        errorContainer = Color(0xFFF3DAD3),
        onErrorContainer = Color(0xFF3D1109),
        outline = textFaint,
        outlineVariant = rule,
        scrim = Color(0xFF000000),
    )
  }
}
