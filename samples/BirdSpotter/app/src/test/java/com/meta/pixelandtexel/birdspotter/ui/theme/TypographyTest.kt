/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.ui.theme

import android.graphics.Paint
import androidx.core.content.res.ResourcesCompat
import com.meta.pixelandtexel.birdspotter.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

/**
 * Native graphics mode so text measurement is real rather than stubbed — the tabular figure check
 * below is worthless against fake metrics.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TypographyTest {

  private val context = RuntimeEnvironment.getApplication()

  @Test
  fun `every bundled face loads`() {
    listOf(
        "libre_caslon_display" to R.font.libre_caslon_display,
        "libre_caslon_text_italic" to R.font.libre_caslon_text_italic,
        "public_sans" to R.font.public_sans,
        "cinzel" to R.font.cinzel,
    )
        .forEach { (name, id) ->
          assertNotNull("font resource $name failed to load", ResourcesCompat.getFont(context, id))
        }
  }

  /**
   * Public Sans replaced Libre Franklin specifically because Franklin ships no `tnum` and its
   * digits vary by 23.7% of an em, which makes a live-updating value on the Glasses screen jitter.
   * Verify the adopted face renders digits at a uniform advance.
   */
  @Test
  fun `data face has tabular figures`() {
    val paint =
        Paint().apply {
          typeface = ResourcesCompat.getFont(context, R.font.public_sans)
          textSize = 48f
          fontFeatureSettings = "tnum"
        }
    val widths = "0123456789".map { paint.measureText(it.toString()) }
    val spread = widths.max() - widths.min()
    assertTrue("digits are not tabular; spread was ${spread}px", spread < 0.01f)
  }

  /** The `data` role must actually request the feature — this is what the screens read. */
  @Test
  fun `data style requests tnum`() {
    assertEquals("tnum", BirdSpotterTypography().data.fontFeatureSettings)
  }

  /**
   * Cinzel is inscriptional caps for the wordmark and life-list numbering only. If it ever leaks
   * into a running-text role, that is a bug.
   */
  @Test
  fun `cinzel is confined to the plate role`() {
    val type = BirdSpotterTypography()
    listOf(type.body, type.caption, type.label, type.headline, type.display, type.title).forEach {
      assertTrue("Cinzel leaked into a text role", it.fontFamily != Cinzel)
    }
    assertEquals(Cinzel, type.plate.fontFamily)
  }
}
