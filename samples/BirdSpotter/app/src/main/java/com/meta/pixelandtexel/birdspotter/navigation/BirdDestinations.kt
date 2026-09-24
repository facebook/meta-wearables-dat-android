/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.meta.pixelandtexel.birdspotter.features.birddetail.BirdDetailScreen

/**
 * The destinations any tab can reach, registered into whichever graph asks for them.
 *
 * **Called from every tab graph on purpose.** A tab owns its back stack, so a screen reachable from
 * every tab has to exist in every tab. Declaring [Route.BirdDetail] in one graph and navigating to
 * it from another does resolve — `findNode` walks up to the root — but the entry then belongs to a
 * graph the user is not in, and two things quietly break: the bottom bar's `hierarchy` check lights
 * up the wrong tab, and `saveState`/`restoreState` files the entry under a tab that never opened
 * it. Registering it in all three costs one line each and makes resolution find the copy in the
 * current tab first.
 *
 * A deep link would go on exactly one of these, not all three: three identical URI patterns resolve
 * by graph order, which is a coin flip. Put it on the Explore copy, so an address arriving from
 * outside the app lands in the tab that owns the field guide.
 */
fun NavGraphBuilder.birdDestinations(navController: NavHostController) {
  composable<Route.BirdDetail> { entry ->
    BirdDetailScreen(
        speciesId = entry.toRoute<Route.BirdDetail>().speciesId,
        onBack = { navController.popBackStack() },
    )
  }
}
