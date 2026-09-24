/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

@file:Suppress("UseOfRunBlocking", "UseOfRunBlockingInTest")

package com.meta.pixelandtexel.birdspotter.features.settings

import com.meta.pixelandtexel.birdspotter.domain.GlassesAccess
import com.meta.pixelandtexel.birdspotter.domain.GlassesDeviceInfo
import com.meta.pixelandtexel.birdspotter.domain.GlassesPermission
import com.meta.pixelandtexel.birdspotter.domain.GlassesRegistrationState
import com.meta.pixelandtexel.birdspotter.domain.GlassesSessionRepository
import com.meta.pixelandtexel.birdspotter.domain.GlassesSessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The glasses settings screen's one piece of policy: **when a Meta AI grant is worth asking
 * about**.
 *
 * DAT reads a grant off a connected pair and cannot answer without one, so the reading is a
 * function of the link and has to follow it. The screen used to ask once, at construction, which
 * froze the row on whatever was true then — glasses connected afterwards changed nothing until the
 * app was restarted, and the unreadable state wore "Not asked" and an `Allow` button that could not
 * succeed.
 *
 * Scenario names are fixed by the testing-parity rule.
 */
class GlassesSettingsViewModelTest {

  @Test
  fun theLinkComingUpReReadsTheGrant() {
    // The reported bug, in one line: linked, nothing connected, then the glasses answer.
    val glasses = FakeGlassesSession(
        devices = listOf(unreachable, reachable),
        answers = listOf(GlassesAccess.UNKNOWN, GlassesAccess.GRANTED),
    )

    val readings = runBlocking {
      accessStream(glasses, GlassesPermission.CAMERA, flowOf(0)).toList()
    }

    assertEquals(listOf(GlassesAccess.UNKNOWN, GlassesAccess.GRANTED), readings)
    assertEquals(2, glasses.reads(GlassesPermission.CAMERA))
  }

  @Test
  fun theLinkDroppingMakesTheGrantUnreadableAgain() {
    // Folding them, or walking out of range. The grant did not change; our ability to
    // read it did, and the row must not go on claiming a reading it can no longer take.
    val glasses = FakeGlassesSession(
        devices = listOf(reachable, unreachable),
        answers = listOf(GlassesAccess.GRANTED, GlassesAccess.UNKNOWN),
    )

    val readings = runBlocking {
      accessStream(glasses, GlassesPermission.CAMERA, flowOf(0)).toList()
    }

    assertEquals(listOf(GlassesAccess.GRANTED, GlassesAccess.UNKNOWN), readings)
    assertEquals(2, glasses.reads(GlassesPermission.CAMERA))
  }

  @Test
  fun aLinkThatNeverChangesIsReadOnce() {
    // Meta AI re-lists the same pair for reasons of its own — a rename, a metadata
    // refresh. Only reachability decides whether the answer could differ.
    val glasses = FakeGlassesSession(
        devices = listOf(unreachable, unreachable, unreachable),
        answers = listOf(GlassesAccess.UNKNOWN),
    )

    val readings = runBlocking {
      accessStream(glasses, GlassesPermission.CAMERA, flowOf(0)).toList()
    }

    assertEquals(listOf(GlassesAccess.UNKNOWN), readings)
    assertEquals(1, glasses.reads(GlassesPermission.CAMERA))
  }

  @Test
  fun noPairAtAllIsUnreadableRatherThanDenied() {
    // Nothing listed is not the wearer saying no — it is nobody to ask. `Allow` here
    // would raise a Meta AI flow with no device behind it.
    val glasses = FakeGlassesSession(
        devices = listOf(null),
        answers = listOf(GlassesAccess.UNKNOWN),
    )

    val readings = runBlocking {
      accessStream(glasses, GlassesPermission.CAMERA, flowOf(0)).toList()
    }

    assertEquals(listOf(GlassesAccess.UNKNOWN), readings)
  }

  @Test
  fun eachGrantIsReadForItsOwnPermission() {
    // Two rows, two grants, and DAT answers them separately — a wearer who allowed the
    // camera and refused the microphone must see exactly that, not one answer twice.
    val glasses = FakeGlassesSession(
        devices = listOf(reachable),
        camera = listOf(GlassesAccess.GRANTED),
        microphone = listOf(GlassesAccess.DENIED),
    )

    val readings = runBlocking {
      val camera = accessStream(glasses, GlassesPermission.CAMERA, flowOf(0)).toList()
      val microphone = accessStream(glasses, GlassesPermission.MICROPHONE, flowOf(0)).toList()
      camera to microphone
    }

    assertEquals(listOf(GlassesAccess.GRANTED), readings.first)
    assertEquals(listOf(GlassesAccess.DENIED), readings.second)
    assertEquals(
        listOf(GlassesPermission.CAMERA, GlassesPermission.MICROPHONE),
        glasses.asked,
    )
  }

  @Test
  fun aRefreshReReadsEveryGrant() {
    // What the screen does when one of its grant flows returns. Meta AI shows the grants
    // together, so the flow raised for one of them is an opportunity to give the other —
    // which is why both rows hang off the one refresh counter, and re-reading only the
    // grant that was raised would leave the other row stale.
    val glasses = FakeGlassesSession(
        devices = listOf(reachable),
        answers = listOf(GlassesAccess.DENIED, GlassesAccess.GRANTED),
    )
    val refreshes = flowOf(0, 1)

    val readings = runBlocking {
      val camera = accessStream(glasses, GlassesPermission.CAMERA, refreshes).toList()
      val microphone = accessStream(glasses, GlassesPermission.MICROPHONE, refreshes).toList()
      camera to microphone
    }

    assertEquals(listOf(GlassesAccess.DENIED, GlassesAccess.GRANTED), readings.first)
    assertEquals(listOf(GlassesAccess.DENIED, GlassesAccess.GRANTED), readings.second)
    assertEquals(2, glasses.reads(GlassesPermission.CAMERA))
    assertEquals(2, glasses.reads(GlassesPermission.MICROPHONE))
  }

  private val reachable = GlassesDeviceInfo(name = "Ray-Ban Meta", isAvailable = true)
  private val unreachable = GlassesDeviceInfo(name = "Ray-Ban Meta", isAvailable = false)
}

/**
 * A pair Meta AI lists, and what DAT would say about each grant every time it is asked — `answers`
 * in the order the readings happen, the last one standing in for any read beyond it. Scripting the
 * answers rather than deriving them keeps the reading *count* visible, which is half of what these
 * scenarios are about.
 */
private class FakeGlassesSession(
    private val devices: List<GlassesDeviceInfo?>,
    private val answers: Map<GlassesPermission, List<GlassesAccess>>,
) : GlassesSessionRepository {

  constructor(
      devices: List<GlassesDeviceInfo?>,
      camera: List<GlassesAccess>,
      microphone: List<GlassesAccess>,
  ) : this(
      devices,
      mapOf(GlassesPermission.CAMERA to camera, GlassesPermission.MICROPHONE to microphone),
  )

  /**
   * The one script, for the scenarios that are about the link rather than about which grant is
   * which.
   */
  constructor(
      devices: List<GlassesDeviceInfo?>,
      answers: List<GlassesAccess>,
  ) : this(devices, GlassesPermission.entries.associateWith { answers })

  /**
   * Every grant asked about, in the order it was asked — which grant, and how many times, are the
   * two things these scenarios pin.
   */
  val asked = mutableListOf<GlassesPermission>()

  fun reads(permission: GlassesPermission): Int = asked.count { it == permission }

  override fun registrationStateStream(): Flow<GlassesRegistrationState> = emptyFlow()

  override fun deviceInfoStream(): Flow<GlassesDeviceInfo?> = devices.asFlow()

  override fun sessionStream(): Flow<GlassesSessionState> = emptyFlow()

  override suspend fun access(permission: GlassesPermission): GlassesAccess {
    val script = answers[permission] ?: listOf(GlassesAccess.UNKNOWN)
    val answer = script[minOf(reads(permission), script.lastIndex)]
    asked += permission
    return answer
  }
}
