package com.nexus.gamebooster.navigation

import android.provider.Settings
import android.view.View
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nexus.gamebooster.R
import com.nexus.gamebooster.core.AppLocale
import com.nexus.gamebooster.core.ServiceLocator
import com.nexus.gamebooster.core.withAppLocale
import com.nexus.gamebooster.data.AppSettings
import com.nexus.gamebooster.ui.components.NgbNeonBackdrop
import com.nexus.gamebooster.ui.components.NgbNeonLoader
import com.nexus.gamebooster.ui.motion.LocalNgbMotionEnabled
import com.nexus.gamebooster.ui.screens.home.HomeScreen
import com.nexus.gamebooster.ui.screens.library.LibraryScreen
import com.nexus.gamebooster.ui.screens.monitor.MonitorScreen
import com.nexus.gamebooster.ui.screens.network.PingGoodScreen
import com.nexus.gamebooster.ui.screens.network.LicenseGateScreen
import com.nexus.gamebooster.network.NexusApiClient
import com.nexus.gamebooster.ui.screens.profiles.ProfilesScreen
import com.nexus.gamebooster.ui.screens.settings.SettingsScreen
import com.nexus.gamebooster.ui.theme.NexusBoosterTheme

/**
 * App shell. Persisted settings drive everything global, so changes apply immediately:
 *  - LANGUAGE: [withAppLocale] builds a localized context (SYSTEM = untouched device
 *    context) provided as LocalContext/LocalConfiguration — this is what makes
 *    stringResource actually switch language — and [AppLocale.isRtl] decides the
 *    layout direction (SYSTEM follows the device RTL setting).
 *  - THEME: NexusBoosterTheme (two dark variants only).
 *  - MOTION: [LocalNgbMotionEnabled] combines the in-app "Reduce motion" setting with
 *    the system's animator duration scale.
 * Below that: bottom navigation + NavHost with restrained, direction-neutral
 * transitions (fade + tiny scale, no directional slides — RTL-safe by construction).
 */
@Composable
fun NgbApp() {
    val settings by ServiceLocator.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = AppSettings())

    val base = LocalContext.current
    val localized = remember(settings.language) { base.withAppLocale(settings.language) }
    val systemIsRtl = base.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
    val layoutDirection = if (AppLocale.isRtl(settings.language, systemIsRtl)) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }

    // Read once per composition, keyed on the base context: a global settings change
    // (developer options → animation scale) re-applies on the next process start,
    // which matches how the platform itself treats it.
    val systemAnimationsEnabled = remember(base) {
        Settings.Global.getFloat(
            base.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }
    val motionEnabled = !settings.reduceMotion && systemAnimationsEnabled

    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides localized.resources.configuration,
        LocalLayoutDirection provides layoutDirection,
        LocalNgbMotionEnabled provides motionEnabled,
    ) {
        NexusBoosterTheme(themeMode = settings.theme) {
            NgbRoot()
        }
    }
}

@Composable
private fun NgbRoot() {
    val context = LocalContext.current
    val sessionInvalid by NexusApiClient.sessionInvalid.collectAsStateWithLifecycle()
    // 0=checking, 1=login, 2=app, 3=retry (saved session kept, server unreachable)
    var gateState by remember { mutableIntStateOf(0) }
    var checkTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(sessionInvalid) {
        if (sessionInvalid) gateState = 1
    }
    LaunchedEffect(checkTick) {
        if (!NexusApiClient.hasSession(context)) {
            gateState = 1
        } else {
            gateState = 0
            try {
                NexusApiClient.sync(context)
                gateState = 2
            } catch (e: SecurityException) {
                // Auth failure: sync() already cleared the session and flagged it invalid.
                gateState = 1
            } catch (_: Exception) {
                // Server/network unreachable: keep the saved session and offer a retry.
                gateState = 3
            }
        }
    }
    when (gateState) {
        // App entry: the NEON ARENA v2 loader (rings + rotating arc + star field)
        // while the saved session is re-synced — replaces the plain spinner.
        0 -> NgbNeonLoader(
            statusText = stringResource(R.string.app_loading_subtitle),
            loadingContentDescription = stringResource(R.string.loader_content_description),
        )
        1 -> LicenseGateScreen(onActivated = { gateState = 2 })
        2 -> Box(Modifier.fillMaxSize()) {
            NgbNeonBackdrop(Modifier.fillMaxSize())
            NgbMainContent()
        }
        else -> LicenseGateScreen(showRetry = true, onActivated = { gateState = 2 }, onRetry = { checkTick++ })
    }
}

@Composable
private fun NgbMainContent() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val motionEnabled = LocalNgbMotionEnabled.current

    Scaffold(
        // The star-field backdrop lives behind the shell (NgbRoot gate 2), so the
        // scaffold stays transparent and screens draw over the neon sky.
        containerColor = Color.Transparent,
        bottomBar = {
            // Mockup .bottom-nav: near-black translucent bar, green hairline top edge.
            NavigationBar(containerColor = Color(0xEB080612)) {
                NgbDestination.entries.forEach { destination ->
                    val selected = currentRoute == destination.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                launchSingleTop = true
                                restoreState = true
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) destination.filledIcon else destination.outlinedIcon,
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NgbDestination.Home.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                if (motionEnabled) {
                    fadeIn(tween(200)) + scaleIn(initialScale = 0.98f, animationSpec = tween(200))
                } else {
                    EnterTransition.None
                }
            },
            exitTransition = {
                if (motionEnabled) fadeOut(tween(150)) else ExitTransition.None
            },
            popEnterTransition = {
                if (motionEnabled) {
                    fadeIn(tween(200)) + scaleIn(initialScale = 0.98f, animationSpec = tween(200))
                } else {
                    EnterTransition.None
                }
            },
            popExitTransition = {
                if (motionEnabled) fadeOut(tween(150)) else ExitTransition.None
            },
        ) {
            composable(NgbDestination.Home.route) {
                HomeScreen(
                    onOpenLibrary = {
                        navController.navigate(NgbDestination.Library.route) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(NgbDestination.Library.route) { LibraryScreen() }
            composable(NgbDestination.Monitor.route) { MonitorScreen() }
            composable(NgbDestination.Profiles.route) { ProfilesScreen() }
            composable(NgbDestination.Ping.route) { PingGoodScreen() }
            composable(NgbDestination.Settings.route) { SettingsScreen() }
        }
    }
}
