/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.session

import android.os.SystemClock
import com.meta.pixelandtexel.birdspotter.domain.SessionClock

/**
 * The clock a real session runs on: the platform's monotonic elapsed time.
 *
 * [SystemClock.elapsedRealtime] rather than [SystemClock.uptimeMillis], and `ContinuousClock`
 * rather than `SuspendingClock` on the other side — the pair that keep counting while the phone is
 * asleep. A session recording with the screen off is still a session, and a minute that passed
 * while it did is a minute of the recording.
 *
 * **Never `System.currentTimeMillis()`.** A wall clock can be set backwards mid-session, by hand or
 * by the network, and a timeline that ran backwards would put an event before the one that caused
 * it.
 */
class MonotonicSessionClock : SessionClock {

  private var origin = SystemClock.elapsedRealtime()

  override val elapsed: Double
    get() = (SystemClock.elapsedRealtime() - origin) / 1000.0

  override fun start() {
    origin = SystemClock.elapsedRealtime()
  }
}
