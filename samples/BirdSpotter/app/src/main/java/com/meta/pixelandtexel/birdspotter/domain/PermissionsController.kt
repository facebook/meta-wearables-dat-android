/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.domain

/**
 * A capability the app asks the OS for. Three of them gate the Identify tab: the phone [CAMERA] and
 * [MICROPHONE] behind the real-time flow, and [LOCATION] behind the questionnaire's where-stamp.
 */
enum class Permission {
  CAMERA,
  MICROPHONE,
  LOCATION,
}

/**
 * Where the user stands on a [Permission]. [NOT_DETERMINED] is "never asked"; [DENIED] covers both
 * a refusal and an OS restriction. Only [GRANTED] opens a gate.
 *
 * Android cannot always tell [NOT_DETERMINED] from [DENIED] without an Activity, and it does not
 * need to: the Identify tab treats everything that is not [GRANTED] as "needs enabling" and
 * escalates a tap to Settings only after a prompt changed nothing. See the Platform notes.
 */
enum class PermissionStatus {
  GRANTED,
  DENIED,
  NOT_DETERMINED,
}

/**
 * The OS permission surface the Identify tab reads to decide what it may offer.
 *
 * Two questions only — *what is the status?* and *take me to Settings to change it* — because those
 * are the parts that mirror.
 *
 * **Requesting the system prompt is deliberately not here.** It is the one piece the architecture
 * note calls un-mirrorable — the dialog can only be raised from an Activity result launcher — so
 * the Identify screen owns its own request path. See the Platform notes.
 */
interface PermissionsController {
  /** The current status of [permission], read fresh from the OS on each call. */
  fun status(permission: Permission): PermissionStatus

  /**
   * Opens this app's page in the system Settings app — the only way back from
   * [PermissionStatus.DENIED].
   */
  fun openAppSettings()
}
