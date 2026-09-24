/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.domain

import kotlinx.coroutines.flow.Flow

/**
 * Whatever is deciding that a bird was heard, or that the watcher said something.
 *
 * The app ships **no classifier and no speech model**, and this interface is where the absence is
 * kept honest: the implementation the app runs is the Demo Director — [DemoDirector], as
 * [com.meta.pixelandtexel.birdspotter.data.demo.PresetDemoDirector] — which plays the ambient calls
 * of the armed [DemoPreset] and is named so that nobody has to guess. A real classifier implements
 * this one member and the session above it does not change.
 *
 * Cold, per the architecture contract: collecting starts the preset's timeline, cancelling abandons
 * it. The findings carry no timestamp; the session stamps them against its own clock — see
 * [SessionFinding].
 */
interface SessionDetector {

  /** Findings as they are made, until the collector goes away. */
  fun findingStream(): Flow<SessionFinding>
}
