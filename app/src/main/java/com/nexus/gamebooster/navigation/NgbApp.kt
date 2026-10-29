package com.nexus.gamebooster.navigation

import android.provider.Settings
import android.view.View
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
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
import com.nexus.gamebooster.ui.arcade.ArcadeScanSweep
import com.nexus.gamebooster.ui.arcade.NgbArcadeColors
import com.nexus.gamebooster.ui.components.NgbNeonBackdrop
import com.nexus.gamebooster.ui.components.NgbNeonLoader
import com.nexus.gamebooster.ui.components.RocketSplashOverlay
import com.nexus.gamebooster.ui.motion.LocalNgbMotionEnabled
import com.nexus.gamebooster.ui.screens.home.HomeScreen
import com.nexus.gamebooster.ui.screens.library.LibraryScreen
import com.nexus.gamebooster.ui.screens.monitor.MonitorScreen
import com.nexus.gamebooster.ui.screens.network.PingGoodScreen
import com.nexus.gamebooster.ui.screens.network.LicenseGateScreen
import com.nexus.gamebooster.network.NexusApiClient
import com.nexus.gamebooster.ui.screens.profiles.ProfilesScreen
import com.nexus.gamebooster.ui.screens.settings.SettingsScreen
import com.nexus.gamebooster.ui.theme.NgbChamferShape
import com.nexus.gamebooster.ui.theme.NexusBoosterTheme
import com.nexus.gamebooster.ui.theme.neonGlow

/**
 * App shell. Persisted settings drive everything global, so changes apply immediately:
 *  - LANGUAGE: [withAppLocale] builds a localized context (SYSTEM = untouched device
 *    context) provided as LocalContext/LocalConfiguration — this is what makes
 *    stringResource actually switch language — and [AppLocale.isRtl] decides the
 *    layout direction (SYSTEM follows the device RTL setting).
 *  - THEME: NexusBoosterTheme (two dark variants only).
 *  - MOTION: [LocalNgbMotionEnabled] combines the in-app "Reduce motion" setting with
 *    the system's animator duration scale.
 * Below that: the v1.2.2 ARCADE bottom navigation (theme-only port) (sliding indicator + icon pop)
 * and a NavHost with restrained, direction-neutral transitions, plus the
 * route-change neon scan sweep from the redesign reference.
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
    Box(Modifier.fillMaxSize()) {
        when (gateState) {
            // Session re-sync loader (rings + rotating arc — kept from Phase 11.5).
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
        // Rocket Edition (v1.2.0): the 3D launch splash rides on TOP of whatever
        // gate state is active during boot and fades out after ~2.6 s. Purely
        // cosmetic — the gate check above already runs underneath from frame one.
        RocketSplashOverlay()
    }
}

@Composable
private fun NgbMainContent() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val motionEnabled = LocalNgbMotionEnabled.current

    // The reference's `.scan.go` — a neon band sweeps the screen on every route change.
    var scanSweepKey by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(currentRoute) { scanSweepKey++ }

    Scaffold(
        // The arcade floor/backdrop lives behind the shell (NgbRoot gate 2), so the
        // scaffold stays transparent and screens draw over the arcade room.
        containerColor = Color.Transparent,
        bottomBar = {
            ArcadeNavBar(currentRoute = currentRoute) { destination ->
                navController.navigate(destination.route) {
                    launchSingleTop = true
                    restoreState = true
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            NavHost(
                navController = navController,
                startDestination = NgbDestination.Home.route,
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
            // Route-change scan sweep (the reference's `.scan` layer, above content).
            ArcadeScanSweep(
                sweepKey = scanSweepKey,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The v1.2.1 arcade bottom navigation — the reference's `.nav`: six equal
 * columns over the near-black gradient with a neon top hairline, a sliding
 * chamfered indicator box behind the active item, and the selected icon's
 * pop (lift −3 dp, scale 1.15, glow). RTL-aware: the indicator order flips
 * with the layout direction, exactly like a mirrored design board.
 */
@Composable
private fun ArcadeNavBar(currentRoute: String?, onSelect: (NgbDestination) -> Unit) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val entries = NgbDestination.entries
    val selectedIndex = entries.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)

    // Sliding indicator (the reference's `.ind`, 450 ms slight overshoot).
    val indicatorPos by animateFloatAsState(
        targetValue = (if (rtl) entries.size - 1 - selectedIndex else selectedIndex).toFloat(),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "navIndicator",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xEB04110A), Color(0xFF020805)))),
    ) {
        // Top neon hairline (the reference's `.nav::before`).
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .drawBehind {
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                NgbArcadeColors.Neon.copy(alpha = 0.6f),
                                Color.Transparent,
                            ),
                        ),
                    )
                },
        )
        var rowWidthPx by remember { mutableStateOf(0f) }
        val density = LocalDensity.current
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .onGloballyPositioned { rowWidthPx = it.size.width.toFloat() },
        ) {
            if (rowWidthPx > 0f) {
                val columnWidth = rowWidthPx / entries.size
                val indicatorWidth = with(density) { 56.dp.toPx() }
                val x = indicatorPos * columnWidth + (columnWidth - indicatorWidth) / 2f
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .size(width = 56.dp, height = 44.dp)
                        .graphicsLayer { translationX = x }
                        .clip(NgbChamferShape(8.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    NgbArcadeColors.Neon.copy(alpha = 0.20f),
                                    NgbArcadeColors.Neon.copy(alpha = 0.04f),
                                ),
                            ),
                        )
                        .drawBehind {
                            // Bottom neon border of the indicator box.
                            drawRect(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.92f to Color.Transparent,
                                    1f to NgbArcadeColors.Neon,
                                ),
                            )
                        },
                )
            }
            Row(Modifier.fillMaxSize()) {
                entries.forEach { destination ->
                    val selected = currentRoute == destination.route
                    val pop by animateFloatAsState(
                        targetValue = if (selected) 1f else 0f,
                        animationSpec = spring(dampingRatio = 0.55f, stiffness = 640f),
                        label = "navPop",
                    )
                    val tint = if (selected) NgbArcadeColors.Neon else NgbArcadeColors.Muted
                    val iconGlow = if (selected && pop > 0.5f) {
                        Modifier.neonGlow(
                            glowColor = NgbArcadeColors.Neon,
                            shape = NgbChamferShape(4.dp),
                            blurRadius = 6.dp,
                            glowAlpha = 0.8f,
                        )
                    } else {
                        Modifier
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSelect(destination) },
                            ),
                    ) {
                        Icon(
                            imageVector = if (selected) destination.filledIcon else destination.outlinedIcon,
                            contentDescription = destination.name,
                            tint = tint,
                            modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer {
                                    translationY = -3.dp.toPx() * pop
                                    scaleX = 1f + 0.15f * pop
                                    scaleY = 1f + 0.15f * pop
                                }
                                .then(iconGlow),
                        )
                        Spacer(Modifier.size(2.dp))
                        Text(
                            text = stringResource(destination.labelRes),
                            color = tint,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }
        }
    }
}
