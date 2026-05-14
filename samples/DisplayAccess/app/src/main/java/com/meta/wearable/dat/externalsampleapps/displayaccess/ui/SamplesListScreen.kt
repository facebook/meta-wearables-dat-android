/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.displayaccess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.meta.wearable.dat.externalsampleapps.displayaccess.R
import com.meta.wearable.dat.externalsampleapps.displayaccess.SampleApp

private val SampleCardColor = Color(0xFFFCFCFD)
private val SampleCardDividerColor = Color(0xFFDDE4F1)
private val SampleIconBackgroundColor = Color(0xFF751C19)
private val SampleButtonStartColor = Color(0xFF597FF6)
private val SampleButtonEndColor = Color(0xFF2A50BA)
private val SampleButtonDisabledStartColor = Color(0xFFD4DAE4)
private val SampleButtonDisabledEndColor = Color(0xFFC5CDD8)
private val SampleButtonDisabledTextColor = Color(0xFFF7F8FA)

@Composable
fun SamplesListScreen(
    isTryItEnabled: Boolean,
    onSampleSelected: (SampleApp) -> Unit,
    modifier: Modifier = Modifier,
) {
  val sample = SampleApp.CAR_MAINTENANCE
  val buttonBrush =
      Brush.horizontalGradient(
          colors =
              if (isTryItEnabled) {
                listOf(SampleButtonStartColor, SampleButtonEndColor)
              } else {
                listOf(SampleButtonDisabledStartColor, SampleButtonDisabledEndColor)
              },
      )

  Box(
      modifier = modifier.fillMaxSize(),
  ) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 28.dp)) {
      Spacer(modifier = Modifier.height(24.dp))

      Column(
          modifier =
              Modifier.fillMaxWidth()
                  .weight(1f)
                  .clip(RoundedCornerShape(30.dp))
                  .background(SampleCardColor)
                  .padding(horizontal = 32.dp, vertical = 40.dp),
          verticalArrangement = Arrangement.Top,
          horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Box(
            modifier =
                Modifier.size(76.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(SampleIconBackgroundColor),
            contentAlignment = Alignment.Center,
        ) {
          Icon(
              imageVector = sample.icon,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(34.dp),
          )
        }

        Spacer(modifier = Modifier.height(36.dp))

        Text(
            text = stringResource(sample.titleRes),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(sample.descriptionRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.weight(1f))

        HorizontalDivider(color = SampleCardDividerColor)

        Spacer(modifier = Modifier.height(36.dp))

        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .clip(CircleShape)
                    .background(brush = buttonBrush)
                    .clickable(enabled = isTryItEnabled) { onSampleSelected(sample) }
                    .padding(vertical = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
          Text(
              text = stringResource(R.string.try_it_button),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
              color = if (isTryItEnabled) Color.White else SampleButtonDisabledTextColor,
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SamplePlaceholderScreen(
    sample: SampleApp,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxSize()) {
    TopAppBar(
        title = { Text(stringResource(sample.titleRes), fontWeight = FontWeight.Bold) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.White,
                titleContentColor = Color(0xFF1A1A1A),
            ),
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Icon(
          imageVector = sample.icon,
          contentDescription = null,
          tint = Color(0xFF666666),
          modifier = Modifier.size(64.dp),
      )
      Spacer(modifier = Modifier.height(16.dp))
      Text(
          text = stringResource(sample.titleRes),
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
          text = "Coming soon",
          style = MaterialTheme.typography.bodyMedium,
          color = Color(0xFF999999),
      )
    }
  }
}
