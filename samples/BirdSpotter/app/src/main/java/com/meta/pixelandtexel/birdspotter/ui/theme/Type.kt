/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.meta.pixelandtexel.birdspotter.R

/*
 * Typefaces — "The Subscriber's Plate".
 *
 * Libre Caslon Display  headline serif; the register of English natural-history publishing
 * Libre Caslon Text     italic only, for scientific names (Display has no italic)
 * Public Sans           UI and data. A fork of Libre Franklin for the US Web Design System.
 * Cinzel                inscriptional caps. Wordmark and life-list numbering ONLY.
 *
 * Why Public Sans and not Libre Franklin: Libre Franklin ships no `tnum` feature and its
 * digits vary by 23.7% of an em, so a live-updating value on the Glasses screen visibly
 * jitters. Public Sans is the same Franklin skeleton with working tabular figures.
 *
 * minSdk is 31, comfortably past the API 26 floor for FontVariation, so each family is a
 * single variable file.
 */

/**
 * Pulls one weight out of a variable font file.
 *
 * The `variationSettings` overload is `@RequiresApi(26)`; minSdk is 31, so it is always available
 * here. It is still marked experimental in Compose, hence the opt-in.
 */
@OptIn(ExperimentalTextApi::class)
private fun variableFont(
    resId: Int,
    weight: FontWeight,
    style: FontStyle = FontStyle.Normal,
) = Font(
    resId,
    weight = weight,
    style = style,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val CaslonDisplay = FontFamily(Font(R.font.libre_caslon_display, FontWeight.Normal))

val CaslonTextItalic = FontFamily(
    variableFont(R.font.libre_caslon_text_italic, FontWeight.Normal, FontStyle.Italic),
)

val PublicSans = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map {
      variableFont(R.font.public_sans, it)
    },
)

val Cinzel = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Bold).map { variableFont(R.font.cinzel, it) },
)

/**
 * The mirrored type scale — one role per line of the design, and the sizes that go with them.
 *
 * Roles, not Material slots: a species name is `display`, which is what lets Meta's docs show the
 * same design whichever snippet a reader is looking at.
 */
@Immutable
data class BirdSpotterTypography(
    /** Species name on a sighting card. */
    val display: TextStyle = TextStyle(
        fontFamily = CaslonDisplay,
        fontWeight = FontWeight.Normal,
        fontSize = 34.sp,
        lineHeight = 38.sp,
    ),
    /** Section and screen titles. */
    val title: TextStyle = TextStyle(
        fontFamily = CaslonDisplay,
        fontWeight = FontWeight.Normal,
        fontSize = 26.sp,
        lineHeight = 31.sp,
    ),
    /** Binomial name — italic serif, as the convention requires. */
    val scientific: TextStyle = TextStyle(
        fontFamily = CaslonTextItalic,
        fontStyle = FontStyle.Italic,
        fontSize = 16.sp,
        lineHeight = 21.sp,
    ),
    /** Card and group headings. */
    val headline: TextStyle = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    /** Running text. */
    val body: TextStyle = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.sp,
    ),
    /** Buttons, chips, field labels. */
    val label: TextStyle = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.3.sp,
    ),
    /** Timestamps, credits, secondary metadata. */
    val caption: TextStyle = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    /**
     * Live sensor values on the Glasses screen. Tabular figures are the whole point — without them
     * a ticking session timer shoves every digit beside it.
     */
    val data: TextStyle = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = "tnum",
    ),
    /**
     * Engraved-plate labels: the wordmark and life-list numbering ("SIGHTING NO. 0042"). Uppercase,
     * letterspaced, small. Cinzel has no lowercase worth reading — never use this for running text.
     */
    val plate: TextStyle = TextStyle(
        fontFamily = Cinzel,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 2.2.sp,
    ),
)

/**
 * Material 3 slots derived from the roles above, so stock Compose components (Button, TopAppBar, …)
 * inherit the brand rather than falling back to Roboto.
 */
internal fun materialTypographyFrom(type: BirdSpotterTypography) = Typography(
    displayLarge = type.display,
    displayMedium = type.display,
    displaySmall = type.title,
    headlineLarge = type.title,
    headlineMedium = type.title,
    headlineSmall = type.headline,
    titleLarge = type.headline,
    titleMedium = type.headline,
    titleSmall = type.label,
    bodyLarge = type.body,
    bodyMedium = type.body,
    bodySmall = type.caption,
    labelLarge = type.label,
    labelMedium = type.label,
    labelSmall = type.caption,
)
