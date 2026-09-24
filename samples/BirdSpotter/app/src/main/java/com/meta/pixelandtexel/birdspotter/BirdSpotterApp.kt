/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.meta.pixelandtexel.birdspotter.domain.GlassesVoiceEvent
import com.meta.pixelandtexel.birdspotter.features.explore.ExploreScreen
import com.meta.pixelandtexel.birdspotter.features.identify.IdentifyScreen
import com.meta.pixelandtexel.birdspotter.features.identify.IdentifyWizardScreen
import com.meta.pixelandtexel.birdspotter.features.identify.RealtimeScreen
import com.meta.pixelandtexel.birdspotter.features.journal.JournalScreen
import com.meta.pixelandtexel.birdspotter.features.journal.OutingDetailScreen
import com.meta.pixelandtexel.birdspotter.features.mockdevice.MockDeviceOverlay
import com.meta.pixelandtexel.birdspotter.features.settings.DemoAmbientEditorScreen
import com.meta.pixelandtexel.birdspotter.features.settings.DemoDirectorPresetScreen
import com.meta.pixelandtexel.birdspotter.features.settings.DemoDirectorScreen
import com.meta.pixelandtexel.birdspotter.features.settings.DemoPhotoEditorScreen
import com.meta.pixelandtexel.birdspotter.features.settings.DemoQuestionEditorScreen
import com.meta.pixelandtexel.birdspotter.features.settings.DiagnosticsFileScreen
import com.meta.pixelandtexel.birdspotter.features.settings.DiagnosticsScreen
import com.meta.pixelandtexel.birdspotter.features.settings.GlassesCameraScreen
import com.meta.pixelandtexel.birdspotter.features.settings.GlassesDisplayScreen
import com.meta.pixelandtexel.birdspotter.features.settings.GlassesSettingsScreen
import com.meta.pixelandtexel.birdspotter.features.settings.SettingsScreen
import com.meta.pixelandtexel.birdspotter.features.settings.SpeechTestScreen
import com.meta.pixelandtexel.birdspotter.features.splash.SplashMotion
import com.meta.pixelandtexel.birdspotter.features.splash.SplashScreen
import com.meta.pixelandtexel.birdspotter.navigation.Route
import com.meta.pixelandtexel.birdspotter.navigation.Tab
import com.meta.pixelandtexel.birdspotter.navigation.birdDestinations
import com.meta.pixelandtexel.birdspotter.ui.components.HairlineRule
import com.meta.pixelandtexel.birdspotter.ui.theme.BirdSpotterTheme
import com.meta.pixelandtexel.birdspotter.ui.theme.glyph
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The app shell: a bottom bar, and one navigation graph per tab beneath it.
 *
 * The [NavHost] hosts three nested graphs, one per tab, so each tab keeps its own back stack —
 * pushing Settings inside Journal, wandering off to Explore, and coming back leaves Settings on
 * screen.
 *
 * The shell deliberately owns no title bar. Each screen brings its own, which is why adding a
 * pushed screen never means editing this file's chrome.
 *
 * [birdDestinations] appears in all three graphs because a screen reachable from every tab has to
 * exist in every tab — see its own doc for what breaks otherwise.
 */
@Composable
fun BirdSpotterApp(
    voiceEvents: Flow<GlassesVoiceEvent> = emptyFlow(),
    isGlassesReady: Boolean = true,
    navController: NavHostController = rememberNavController(),
) {
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = backStackEntry?.destination

  // The launch moment, held by the shell because the shell is what it overlays.
  // Saveable so a rotation or a process restore doesn't replay it: a splash belongs
  // to a cold start.
  var isSplashFinished by rememberSaveable { mutableStateOf(false) }

  // The real-time cover's presented flag, held here rather than in `IdentifyScreen`
  // because the cover has to clear the bottom bar, and only the shell draws over it.
  // Saveable so a rotation doesn't tear the cover down.
  var isRealtimePresented by rememberSaveable { mutableStateOf(false) }

  // The voice launches, acted on for as long as the shell is up. Each one has already
  // been answered by the time it lands here (see GlassesVoiceRepository), and the session
  // it asked for has already been started by the activity — the launch may land with the
  // phone locked in a pocket, where nothing here composes. What is left to the shell is the
  // half that needs a screen: raising the cover over it.
  LaunchedEffect(voiceEvents) {
    voiceEvents.collect { event ->
      when (event) {
        GlassesVoiceEvent.LAUNCH -> isRealtimePresented = true
      }
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
          // The bar wears the app's palette rather than Material's: opaque
          // paperRaised parted from the page by a rule, textSecondary at rest, gilt
          // when selected. Material's defaults would read `surfaceContainer` — near
          // enough to `paper` that the bar dissolves into the screen — and tint the
          // selection with verdigris, which this design never does. The pill itself
          // stays: it's the platform's selection idiom, so it wears giltWash rather
          // than vanishing.
          Column {
            HairlineRule()
            NavigationBar(containerColor = BirdSpotterTheme.colors.paperRaised) {
              val colors = BirdSpotterTheme.colors
              val glyphs = BirdSpotterTheme.glyphs
              Tab.entries.forEach { tab ->
                // `hierarchy` walks a destination up through its parent graphs, so
                // Settings — three levels down inside the Journal graph — still lights
                // up the Journal tab.
                val isSelected =
                    currentDestination?.hierarchy?.any {
                      it.hasRoute(tab.graph::class)
                    } == true

                NavigationBarItem(
                    selected = isSelected,
                    onClick = { navController.switchTab(tab, isSelected) },
                    icon = {
                      Icon(
                          painter =
                              glyph(
                                  when (tab) {
                                    Tab.EXPLORE -> glyphs.explore
                                    Tab.IDENTIFY -> glyphs.identify
                                    Tab.JOURNAL -> glyphs.journal
                                  },
                              ),
                          contentDescription = tab.label,
                      )
                    },
                    label = { Text(tab.label) },
                    colors =
                        NavigationBarItemDefaults.colors(
                            selectedIconColor = colors.gilt,
                            selectedTextColor = colors.gilt,
                            indicatorColor = colors.giltWash,
                            unselectedIconColor = colors.textSecondary,
                            unselectedTextColor = colors.textSecondary,
                        ),
                )
              }
            }
          }
        },
        // Only the sides. The bottom is the nav bar's to handle, and the top belongs to
        // whichever screen is showing — reserving it here would leave a dead strip above
        // each screen's own app bar.
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
      NavHost(
          navController = navController,
          startDestination = Route.ExploreGraph,
          modifier = Modifier.padding(innerPadding),
      ) {
        navigation<Route.ExploreGraph>(startDestination = Route.Explore) {
          composable<Route.Explore> {
            ExploreScreen(
                onOpenBird = { navController.navigate(Route.BirdDetail(it)) },
            )
          }
          birdDestinations(navController)
        }

        navigation<Route.IdentifyGraph>(startDestination = Route.Identify) {
          composable<Route.Identify> {
            IdentifyScreen(
                onStartStepByStep = { navController.navigate(Route.IdentifyWizard) },
                onOpenRealtime = { isRealtimePresented = true },
            )
          }
          composable<Route.IdentifyWizard> {
            IdentifyWizardScreen(
                onClose = { navController.popBackStack() },
                onOpenBird = { navController.navigate(Route.BirdDetail(it)) },
            )
          }
          birdDestinations(navController)
        }

        navigation<Route.JournalGraph>(startDestination = Route.Journal) {
          composable<Route.Journal> {
            JournalScreen(
                onOpenSettings = { navController.navigate(Route.Settings) },
                onOpenEntry = { navController.navigate(Route.OutingDetail(it)) },
            )
          }
          composable<Route.Settings> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenDemoDirector = { navController.navigate(Route.DemoDirector) },
                onOpenGlasses = { navController.navigate(Route.GlassesSettings) },
                onOpenDiagnostics = { navController.navigate(Route.Diagnostics) },
            )
          }
          composable<Route.Diagnostics> {
            DiagnosticsScreen(
                onBack = { navController.popBackStack() },
                onOpenFile = { navController.navigate(Route.DiagnosticsFile(it)) },
            )
          }
          composable<Route.DiagnosticsFile> { entry ->
            DiagnosticsFileScreen(
                fileName = entry.toRoute<Route.DiagnosticsFile>().name,
                onBack = { navController.popBackStack() },
            )
          }
          composable<Route.GlassesSettings> {
            GlassesSettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenSpeechTest = { navController.navigate(Route.SpeechTest) },
                onOpenCameraScreen = { navController.navigate(Route.GlassesCamera) },
                onOpenDisplayScreen = { navController.navigate(Route.GlassesDisplay) },
            )
          }
          composable<Route.SpeechTest> {
            SpeechTestScreen(
                onBack = { navController.popBackStack() },
            )
          }
          composable<Route.GlassesCamera> {
            GlassesCameraScreen(
                onBack = { navController.popBackStack() },
            )
          }
          composable<Route.GlassesDisplay> {
            GlassesDisplayScreen(
                onBack = { navController.popBackStack() },
            )
          }
          composable<Route.DemoDirector> {
            DemoDirectorScreen(
                onBack = { navController.popBackStack() },
                onOpenPreset = { navController.navigate(Route.DemoDirectorPreset(it)) },
            )
          }
          composable<Route.DemoDirectorPreset> { entry ->
            val presetId = entry.toRoute<Route.DemoDirectorPreset>().presetId
            DemoDirectorPresetScreen(
                presetId = presetId,
                onBack = { navController.popBackStack() },
                onOpenQuestion = {
                  navController.navigate(Route.DemoDirectorQuestion(presetId, it))
                },
                onOpenPhoto = {
                  navController.navigate(Route.DemoDirectorPhoto(presetId, it))
                },
                onOpenAmbient = {
                  navController.navigate(Route.DemoDirectorAmbient(presetId, it))
                },
            )
          }
          composable<Route.DemoDirectorQuestion> { entry ->
            val route = entry.toRoute<Route.DemoDirectorQuestion>()
            DemoQuestionEditorScreen(
                presetId = route.presetId,
                questionId = route.questionId,
                onBack = { navController.popBackStack() },
            )
          }
          composable<Route.DemoDirectorPhoto> { entry ->
            val route = entry.toRoute<Route.DemoDirectorPhoto>()
            DemoPhotoEditorScreen(
                presetId = route.presetId,
                photoId = route.photoId,
                onBack = { navController.popBackStack() },
            )
          }
          composable<Route.DemoDirectorAmbient> { entry ->
            val route = entry.toRoute<Route.DemoDirectorAmbient>()
            DemoAmbientEditorScreen(
                presetId = route.presetId,
                callId = route.callId,
                onBack = { navController.popBackStack() },
            )
          }
          // Registered here rather than in `birdDestinations`, because an entry is
          // only ever opened from the Journal — see `Route.OutingDetail`.
          composable<Route.OutingDetail> { entry ->
            OutingDetailScreen(
                outingId = entry.toRoute<Route.OutingDetail>().outingId,
                onBack = { navController.popBackStack() },
                onOpenBird = { navController.navigate(Route.BirdDetail(it)) },
            )
          }
          birdDestinations(navController)
        }
      }
    }

    // The real-time cover: up from the bottom, over the shell so it takes the bottom
    // bar with it, and back down on close. It rides the same Box the splash overlays,
    // because a screen in the Scaffold's content can't reach past the bottom bar.
    // Slide only, no fade — an opaque cover reads as a sheet.
    AnimatedVisibility(
        visible = isRealtimePresented,
        enter =
            slideInVertically(
                animationSpec = tween(RealtimeCoverMillis, easing = FastOutSlowInEasing),
                initialOffsetY = { it },
            ),
        exit =
            slideOutVertically(
                animationSpec = tween(RealtimeCoverMillis, easing = FastOutSlowInEasing),
                targetOffsetY = { it },
            ),
    ) {
      RealtimeScreen(onClose = { isRealtimePresented = false })
    }

    // Over the shell, so the reveal is a dissolve onto a page that is already laid
    // out. The 0.42 / 0 / 0.58 / 1 crossfade curve is `SplashMotion`'s.
    AnimatedVisibility(
        visible = !isSplashFinished,
        enter = EnterTransition.None,
        exit =
            fadeOut(
                tween(
                    SplashMotion.crossfadeMillis,
                    easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f),
                ),
            ),
    ) {
      SplashScreen(onFinished = { isSplashFinished = true })
    }

    // The mock's floating button and panel, last so they sit over the cover and the
    // splash alike — the controls have to reach the session the cover holds. Empty, and
    // touch-transparent, whenever the kit is off. Not before the glasses are up: reaching
    // the kit is what boots the SDK, and the activity orders that after the Bluetooth
    // grant — see MainActivity.
    if (isGlassesReady) MockDeviceOverlay()
  }
}

/**
 * How long the real-time cover takes to rise or fall. Local rather than a shared `…Motion` holder
 * like the splash's: this is the platform's own sheet timing rather than a designed number, so it
 * is tuned here to read as a standard sheet.
 */
private const val RealtimeCoverMillis = 320

/**
 * Switch tabs the way a `TabView` does: the tab you leave keeps its stack, the tab you arrive at is
 * where you left it, and tapping the tab you are already in takes you back to its root.
 *
 * `saveState`/`restoreState` are the pair that does the first two. Without them a tab is rebuilt
 * from its start destination on every visit — three screens that reset when you look away.
 *
 * The third — [isSelected] — is a branch rather than a flag because the same `navigate` cannot
 * express it: `restoreState` would hand back the very stack `saveState` had just put away, so from
 * a pushed screen the tap did nothing at all.
 */
private fun NavHostController.switchTab(tab: Tab, isSelected: Boolean) {
  if (isSelected) {
    // To the start *destination*, not to the graph: popping to the graph itself would
    // take its start destination with it and leave a container with no screen under
    // it. Already at the root this finds nothing to pop and returns false, which is
    // the right answer: re-tapping the tab you are already on should do nothing.
    popBackStack(tab.start, inclusive = false)
    return
  }

  val start = graph.findStartDestination().id
  navigate(tab.graph) {
    // Tabs are peers, not a trail. Without this, hopping between tabs stacks them up
    // and back has to unwind every hop before it can leave the app.
    popUpTo(start) { saveState = true }
    launchSingleTop = true
    restoreState = true
  }
}

@Preview(showBackground = true)
@Composable
private fun BirdSpotterAppPreview() {
  BirdSpotterTheme {
    BirdSpotterApp()
  }
}
