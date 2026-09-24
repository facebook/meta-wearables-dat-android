/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.mockdevice

import com.meta.pixelandtexel.birdspotter.domain.MockDeviceInfo
import com.meta.pixelandtexel.birdspotter.domain.MockGlassesModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The mock panel's one piece of policy: **which pair the controls are aimed at** as pairs come and
 * go.
 *
 * The kit holds up to a handful of simulated pairs and reports the list whole, so every change is a
 * fresh list rather than an event, and the panel has to decide where its controls point from the
 * list alone. The rule under test: stay on the pair being driven for as long as it exists, fall to
 * the latest pair when it does not, and point at nothing when there is nothing.
 *
 * Scenario names are fixed by the testing-parity rule.
 */
class MockDeviceViewModelTest {

  @Test
  fun pairingSelectsTheNewPair() {
    // Nothing was selected; the first pair to arrive is the one the controls take.
    assertEquals(rayBan.id, selectedDeviceId(listOf(rayBan), current = null))
  }

  @Test
  fun aNewPairDoesNotStealTheSelection() {
    // Somebody is driving the Ray-Ban when a second pair arrives. The controls stay put: a
    // slider mid-drag must not switch glasses under the thumb.
    assertEquals(rayBan.id, selectedDeviceId(listOf(rayBan, display), current = rayBan.id))
  }

  @Test
  fun unpairingTheSelectedPairFallsBackToTheLatest() {
    // The pair being driven is unpaired. The latest remaining one is the closest thing to
    // where the controls were.
    assertEquals(display.id, selectedDeviceId(listOf(rayBan, display), current = "gone"))
  }

  @Test
  fun noPairsMeansNoSelection() {
    // The kit holds nothing — disabled, or every pair unpaired. Nothing to aim at.
    assertNull(selectedDeviceId(emptyList(), current = rayBan.id))
  }

  private val rayBan = MockDeviceInfo(id = "mock-1", model = MockGlassesModel.RAY_BAN_META)
  private val display = MockDeviceInfo(id = "mock-2", model = MockGlassesModel.META_RAY_BAN_DISPLAY)
}
