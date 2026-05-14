/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.displayaccess.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme =
    lightColorScheme(
        primary = Color(0xFF2147A8),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFF3F6FF),
        onPrimaryContainer = Color(0xFF142B63),
        secondary = Color(0xFF6B7280),
        onSecondary = Color.White,
        surface = Color.White,
        onSurface = Color(0xFF111827),
        onSurfaceVariant = Color(0xFF667085),
        outline = Color(0xFFD9E1F2),
        background = Color(0xFFF4F7FC),
        onBackground = Color(0xFF111827),
    )

@Composable
fun DisplayAccessTheme(content: @Composable () -> Unit) {
  MaterialTheme(
      colorScheme = LightColorScheme,
      content = content,
  )
}
