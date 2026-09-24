/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter

import android.app.Application

/**
 * Owns the [AppContainer] for the life of the process.
 *
 * The Application object rather than the Activity, so a rotation does not reopen the database — and
 * so the eventual glasses session survives one too, which matters more: a DAT session dropped and
 * re-established on every configuration change would be a visible flicker in the demo.
 */
class BirdSpotterApplication : Application() {

  lateinit var container: AppContainer
    private set

  override fun onCreate() {
    super.onCreate()
    container = AppContainer(this)
  }
}
