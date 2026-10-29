package com.nexus.gamebooster.ui.screens.home

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.DeviceThermostat
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexus.gamebooster.R
import com.nexus.gamebooster.core.ServiceLocator
import com.nexus.gamebooster.core.VmFactory
import com.nexus.gamebooster.core.availabilityLabel
import com.nexus.gamebooster.core.boostActionLabel
import com.nexus.gamebooster.core.boostCheckLabel
import com.nexus.gamebooster.core.pluggedLabel
import com.nexus.gamebooster.monitoring.BatterySnapshot
import com.nexus.gamebooster.monitoring.CpuSnapshot
import com.nexus.gamebooster.monitoring.DeviceFacts
import com.nexus.gamebooster.monitoring.MemorySnapshot
import com.nexus.gamebooster.monitoring.MetricRowKeys
import com.nexus.gamebooster.monitoring.ThermalHeadroomSnapshot
import com.nexus.gamebooster.monitoring.ThermalSnapshot
import com.nexus.gamebooster.monitoring.thermalRows
import com.nexus.gamebooster.optimizer.BoostActionOutcome
import com.nexus.gamebooster.optimizer.BoostReport
import com.nexus.gamebooster.optimizer.BoostStatus
import com.nexus.gamebooster.profiles.PerformanceProfile
import com.nexus.gamebooster.shizuku.ShizukuPrivilege
import com.nexus.gamebooster.ui.arcade.ArcadeButton
import com.nexus.gamebooster.ui.arcade.ArcadeChip
import com.nexus.gamebooster.ui.arcade.ArcadePanel
import com.nexus.gamebooster.ui.arcade.ArcadePanelVariant
import com.nexus.gamebooster.ui.arcade.ArcadeStatusDot
import com.nexus.gamebooster.ui.arcade.ArcadeTint
import com.nexus.gamebooster.ui.arcade.NgbArcadeColors
import com.nexus.gamebooster.ui.arcade.OrbitSpeed
import com.nexus.gamebooster.ui.components.BoostCard
import com.nexus.gamebooster.ui.components.BoostStepUi
import com.nexus.gamebooster.ui.components.MapperMetricRow
import com.nexus.gamebooster.ui.components.LicenseCountdownCard
import com.nexus.gamebooster.ui.components.MetricRow
import com.nexus.gamebooster.ui.components.NgbScreenContainer
import com.nexus.gamebooster.ui.components.SectionCard
import com.nexus.gamebooster.ui.components.StatusChipVariant
import com.nexus.gamebooster.ui.components.profileIcon
import com.nexus.gamebooster.ui.motion.LocalNgbMotionEnabled
import com.nexus.gamebooster.ui.motion.ngbPressScale
import com.nexus.gamebooster.ui.theme.NgbChamferShape
import com.nexus.gamebooster.ui.theme.NgbTagFont
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private const val BYTES_PER_GB = 1073741824.0 // 1024^3

/**
 * ARCADE HUD hero (reference `.panel.hot.hero`): the existing generated key art inside
 * a HOT chamfered panel at the reference's 158 dp height, floating ±8 dp over 5 s
 * (motion-gated), under the reference's scanline overlay and a bottom scrim. The
 * top-end badge chip and the Orbitron "NEXUS BOOSTER" brand line (a logo — decorative,
 * hardcoded like the reference, with the neon half glowing) finish the header. The
 * image stays decorative (contentDescription = null); the visible localized texts
 * carry the meaning for screen readers.
 */
@Composable
private fun NgbHero() {
    val motion = LocalNgbMotionEnabled.current
    val float = rememberInfiniteTransition(label = "heroFloat")
    val floatY by float.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            tween(5000, easing = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)),
            RepeatMode.Reverse,
        ),
        label = "heroFloatY",
    )
    ArcadePanel(
        modifier = Modifier
            .fillMaxWidth()
            .height(158.dp),
        variant = ArcadePanelVariant.HOT,
        cut = 20.dp,
        contentPadding = 0.dp,
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(158.dp)) {
            // The art layer (inset by the panel's 1.25 dp edge): floating image + scanlines + scrim.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(158.dp)
                    .clip(NgbChamferShape(17.dp)),
            ) {
                Image(
                    painter = painterResource(R.drawable.ngb_hero),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        // Slight overscale so the ±8 dp float never reveals panel edges.
                        .graphicsLayer {
                            translationY = (if (motion) floatY else 0f) * density
                            scaleX = 1.12f
                            scaleY = 1.12f
                        },
                )
                // Scanlines: 1 px dark line every 3 dp — the reference's multiply overlay.
                Canvas(modifier = Modifier.matchParentSize()) {
                    val lineH = 1.dp.toPx()
                    val gap = 3.dp.toPx()
                    var y = 0f
                    while (y < size.height) {
                        drawRect(
                            color = Color.Black.copy(alpha = 0.18f),
                            topLeft = Offset(0f, y),
                            size = Size(size.width, lineH),
                        )
                        y += gap
                    }
                }
                // Bottom scrim so the title/subtitle stay readable on both themes.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.45f to Color.Transparent,
                                1f to NgbArcadeColors.Ink.copy(alpha = 0.92f),
                            ),
                        ),
                )
            }
            ArcadeChip(
                text = stringResource(R.string.home_hero_badge),
                tint = NgbArcadeColors.Neon,
                leading = { ArcadeStatusDot(color = NgbArcadeColors.Neon) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
            ) {
                // The brand line is an LTR logo, not translatable copy.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row {
                        Text(
                            text = "NEXUS ",
                            fontFamily = NgbTagFont,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            letterSpacing = 0.7.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "BOOSTER",
                            fontFamily = NgbTagFont,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            letterSpacing = 0.7.sp,
                            color = NgbArcadeColors.Neon,
                            style = TextStyle(
                                shadow = Shadow(
                                    color = NgbArcadeColors.Neon.copy(alpha = 0.75f),
                                    offset = Offset.Zero,
                                    blurRadius = 14f,
                                ),
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.home_subtitle),
                    fontSize = 12.sp,
                    color = NgbArcadeColors.Muted,
                )
            }
        }
    }
}

@Composable
fun HomeScreen(onOpenLibrary: () -> Unit = {}) {
    val vm: HomeViewModel = viewModel(factory = VmFactory { ServiceLocator.homeViewModel() })
    val boostVm: BoostViewModel = viewModel(factory = VmFactory { ServiceLocator.boostViewModel() })
    val state by vm.uiState.collectAsStateWithLifecycle()
    val boostState by boostVm.uiState.collectAsStateWithLifecycle()
    val motionEnabled = LocalNgbMotionEnabled.current

    // Boost events → the shared honest activity log (started / finished / cancelled).
    LaunchedEffect(boostVm) {
        boostVm.logEvents.collect { event -> vm.onBoostLogEvent(event) }
    }

    // Re-check Shizuku when user returns from the Shizuku manager; auto-request permission if binder is up.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.refreshShizukuStatus()
                // If user just started Shizuku, ask for permission without another tap.
                if (state.shizukuStatus == ShizukuPrivilege.Status.PERMISSION_REQUIRED ||
                    ServiceLocator.shizukuPrivilege.status.value == ShizukuPrivilege.Status.PERMISSION_REQUIRED
                ) {
                    vm.requestShizukuPermission()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Clean Home (Phase 9): only hero art + Shizuku + Boost. Metrics / history / profiles → other tabs.
    val context = androidx.compose.ui.platform.LocalContext.current
    NgbScreenContainer {
        NgbHero()
        Spacer(Modifier.height(16.dp))
        LicenseCountdownCard()
        Spacer(Modifier.height(20.dp))
        ShizukuStatusCard(
            status = state.shizukuStatus,
            onActivate = { vm.activateShizuku(context) },
            onRequestPermission = { vm.activateShizuku(context) },
        )
        Spacer(Modifier.height(20.dp))
        val reactor = reactorLook(boostState)
        val boostInProgress = boostState.phase == BoostPhase.CHECKING ||
            boostState.phase == BoostPhase.RUNNING
        BoostCard(
            label = boostButtonLabel(boostState),
            tagline = reactor.tagline,
            accentColor = reactor.accent,
            orbitSpeed = reactor.speed,
            enabled = boostState.phase == BoostPhase.IDLE ||
                (boostState.phase == BoostPhase.FINISHED && !boostState.isRunning),
            inProgress = boostInProgress,
            steps = boostStepList(boostState),
            onCancel = if (boostInProgress) boostVm::cancelBoost else null,
            onBoostClick = {
                vm.onBoostPressed()
                boostVm.startBoost()
            },
        )
        Spacer(Modifier.height(8.dp))
    }

    // In-app Shizuku activate feedback (install / start / permission).
    state.shizukuMessageKey?.let { key ->
        ShizukuMessageDialog(
            messageKey = key,
            onDismiss = vm::dismissShizukuMessage,
            onOpenDownload = { vm.openShizukuDownload(context) },
            onOpenShizukuApp = { vm.openShizukuApp(context) },
        )
    }

    if (boostState.showResultDialog) {
        BoostResultDialog(
            state = boostState,
            profiles = state.profiles,
            onCancel = boostVm::cancelBoost,
            onRetry = boostVm::retry,
            onOpenBatterySettings = boostVm::openBatterySettings,
            onOpenGameDetails = boostVm::openGameDetails,
            onLaunchReadyGame = boostVm::launchReadyGame,
            onDismiss = boostVm::dismissDialog,
        )
    }
}

/**
 * Boost button label per phase — honest, never a percentage: "Boost" idle,
 * "Checking…"/"Boosting…" while the real run is active, and the real status name when
 * a run has finished.
 */
@Composable
private fun boostButtonLabel(state: BoostUiState): String = when (state.phase) {
    BoostPhase.IDLE -> stringResource(R.string.boost_button_label)
    BoostPhase.CHECKING -> stringResource(R.string.boost_checking)
    BoostPhase.RUNNING -> stringResource(R.string.boost_running)
    BoostPhase.FINISHED -> state.status?.let { stringResource(BoostViewModel.statusLabelRes(it)) }
        ?: stringResource(R.string.boost_button_label)
}

/**
 * Reactor look per boost phase (the reference's `.reactor` states): IDLE neon +
 * "TAP TO BOOST", CHECKING azure + "CHECKING" (fast orbit), RUNNING lime + "BOOSTING",
 * FINISHED neon + "READY". The taglines are the reference's decorative HUD codes
 * (brand-style English-only, like "NEXUS BOOSTER") — not translatable copy. The label
 * next to them stays the honest localized [boostButtonLabel].
 */
private fun reactorLook(state: BoostUiState): ReactorLook = when (state.phase) {
    BoostPhase.IDLE -> ReactorLook(NgbArcadeColors.Neon, OrbitSpeed.IDLE, "TAP TO BOOST")
    BoostPhase.CHECKING -> ReactorLook(NgbArcadeColors.Azure, OrbitSpeed.CHECKING, "CHECKING")
    BoostPhase.RUNNING -> ReactorLook(NgbArcadeColors.Lime, OrbitSpeed.RUNNING, "BOOSTING")
    BoostPhase.FINISHED -> ReactorLook(NgbArcadeColors.Neon, OrbitSpeed.IDLE, "READY")
}

/** Accent color + orbit speed + decorative HUD tagline for one reactor phase. */
private data class ReactorLook(val accent: Color, val speed: OrbitSpeed, val tagline: String)

/**
 * The reactor's step rows, mapped from REAL state only (the reference's fake
 * `.steps` list is never fabricated here):
 *  - while checking: one blinking active row (the honest "Checking…" state);
 *  - the captured pre-checks (each a done row — they exist when listed);
 *  - when finished: every recorded action outcome (done only when actually
 *    applied/already-applied; skipped/unsupported/failed stay muted — the report
 *    dialog carries the full typed outcome).
 */
@Composable
private fun boostStepList(state: BoostUiState): List<BoostStepUi> {
    val steps = mutableListOf<BoostStepUi>()
    if (state.phase == BoostPhase.CHECKING) {
        steps.add(
            BoostStepUi(label = stringResource(R.string.boost_checking), done = false, active = true),
        )
    }
    state.checks.forEach { check ->
        steps.add(BoostStepUi(label = boostCheckLabel(check.labelKey), done = true, active = false))
    }
    if (state.phase == BoostPhase.FINISHED) {
        state.report?.actions?.forEach { record ->
            val applied = record.outcome is BoostActionOutcome.Applied ||
                record.outcome is BoostActionOutcome.AlreadyApplied
            steps.add(
                BoostStepUi(label = boostActionLabel(record.actionId), done = applied, active = false),
            )
        }
    }
    return steps
}

/**
 * Phase 9 → ARCADE HUD: Shizuku status card — PLAIN/WARN/BAD panel with the kit's
 * pulsing status dot (neon/warn/danger) and one small 3D-lip action button when not
 * ready (danger to activate, lime to grant permission). READY stays quiet.
 */
@Composable
private fun ShizukuStatusCard(
    status: ShizukuPrivilege.Status,
    onActivate: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    val variant: ArcadePanelVariant
    val dotColor: Color
    val guideRes: Int
    val buttonRes: Int?
    val buttonTint: ArcadeTint?
    val buttonAction: (() -> Unit)?
    when (status) {
        ShizukuPrivilege.Status.READY -> {
            variant = ArcadePanelVariant.PLAIN
            dotColor = NgbArcadeColors.Neon
            guideRes = R.string.shizuku_status_ready
            buttonRes = null
            buttonTint = null
            buttonAction = null
        }
        ShizukuPrivilege.Status.PERMISSION_REQUIRED -> {
            variant = ArcadePanelVariant.WARN
            dotColor = NgbArcadeColors.Warn
            guideRes = R.string.shizuku_guide_permission
            buttonRes = R.string.shizuku_request_permission
            buttonTint = ArcadeTint.LIME
            buttonAction = onRequestPermission
        }
        else -> {
            variant = ArcadePanelVariant.BAD
            dotColor = NgbArcadeColors.Danger
            guideRes = R.string.shizuku_guide_inactive
            buttonRes = R.string.shizuku_activate
            buttonTint = ArcadeTint.DANGER
            buttonAction = onActivate
        }
    }
    ArcadePanel(variant = variant, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArcadeStatusDot(color = dotColor)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.shizuku_card_title),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(guideRes),
                    fontSize = 12.sp,
                    color = NgbArcadeColors.Muted,
                )
            }
            if (buttonAction != null && buttonTint != null && buttonRes != null) {
                ArcadeButton(onClick = buttonAction, tint = buttonTint, small = true) {
                    Text(
                        text = stringResource(buttonRes),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}


@Composable
private fun ShizukuMessageDialog(
    messageKey: String,
    onDismiss: () -> Unit,
    onOpenDownload: () -> Unit,
    onOpenShizukuApp: () -> Unit,
) {
    val body = when (messageKey) {
        HomeViewModel.MSG_NOT_INSTALLED -> stringResource(R.string.shizuku_msg_not_installed)
        HomeViewModel.MSG_NEED_START -> stringResource(R.string.shizuku_msg_need_start)
        HomeViewModel.MSG_PERMISSION -> stringResource(R.string.shizuku_msg_permission)
        HomeViewModel.MSG_READY -> stringResource(R.string.shizuku_msg_ready)
        else -> messageKey
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shizuku_card_title)) },
        text = { Text(body) },
        confirmButton = {
            when (messageKey) {
                HomeViewModel.MSG_NOT_INSTALLED -> {
                    TextButton(onClick = onOpenDownload) {
                        Text(stringResource(R.string.shizuku_msg_install_button))
                    }
                }
                HomeViewModel.MSG_NEED_START -> {
                    // Explicit user choice only — we do not auto-leave the app.
                    TextButton(onClick = onOpenShizukuApp) {
                        Text(stringResource(R.string.shizuku_msg_open_shizuku_button))
                    }
                }
                else -> {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_close))
                    }
                }
            }
        },
        dismissButton = {
            if (messageKey == HomeViewModel.MSG_NOT_INSTALLED ||
                messageKey == HomeViewModel.MSG_NEED_START
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_close))
                }
            }
        },
    )
}

@Composable
private fun DeviceCard(facts: DeviceFacts?) {
    SectionCard(
        title = stringResource(R.string.home_device_info),
        icon = Icons.Outlined.Smartphone,
    ) {
        if (facts != null) {
            MetricRow(
                label = stringResource(R.string.device_model),
                icon = Icons.Outlined.Smartphone,
                value = stringResource(R.string.device_model_value, facts.manufacturer, facts.model),
            )
            facts.deviceName?.let { name ->
                MetricRow(
                    label = stringResource(R.string.device_device_name),
                    icon = Icons.Outlined.Smartphone,
                    value = name,
                )
            }
            MetricRow(
                label = stringResource(R.string.device_android_version),
                icon = Icons.Outlined.Code,
                value = facts.androidVersion,
            )
            MetricRow(
                label = stringResource(R.string.device_sdk),
                icon = Icons.Outlined.Code,
                value = facts.sdkLevel.toString(),
            )
            facts.securityPatch?.let { patch ->
                MetricRow(
                    label = stringResource(R.string.device_security_patch),
                    icon = Icons.Outlined.Code,
                    value = patch,
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    state: HomeUiState,
    onRefresh: () -> Unit,
) {
    SectionCard(
        title = stringResource(R.string.home_status_title),
        icon = Icons.Outlined.Insights,
        trailing = {
            TextButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                Text(stringResource(R.string.refresh))
            }
        },
    ) {
        if (state.isRefreshing) {
            Text(
                text = stringResource(R.string.refreshing),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BatteryRow(snapshot = state.battery)
        ThermalRows(status = state.thermal, headroom = state.headroom)
        MemoryRows(snapshot = state.memory)
        CpuRow(snapshot = state.cpu)
    }
}

@Composable
private fun BatteryRow(snapshot: BatterySnapshot?) {
    val level = snapshot?.levelPercent
    if (level != null) {
        val chargingText = when (snapshot.charging) {
            true -> stringResource(R.string.battery_charging)
            false -> stringResource(R.string.battery_discharging)
            // Honest default when the platform gives a % but no status.
            null -> stringResource(R.string.battery_not_reported)
        }
        val pluggedText = pluggedLabel(snapshot.pluggedLabel).orEmpty()
        val supporting = listOf(chargingText, pluggedText)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        MetricRow(
            label = stringResource(R.string.status_battery),
            icon = Icons.Outlined.BatteryStd,
            value = stringResource(R.string.battery_level_value, level),
            supporting = supporting,
        )
    } else {
        MetricRow(
            label = stringResource(R.string.status_battery),
            icon = Icons.Outlined.BatteryStd,
            statusVariant = StatusChipVariant.UNAVAILABLE,
            statusText = if (snapshot == null) {
                stringResource(R.string.reason_no_reading)
            } else {
                availabilityLabel(snapshot.availability)
            },
        )
    }
}

/**
 * Thermal status via the SAME shared mappers the Monitor screen uses (no copied
 * logic): status row plus a headroom row — on Home the headroom is surfaced ONLY
 * when actually reported ("extra row ONLY when available"); the Monitor screen is
 * the place that also explains unsupported/not-reported states.
 */
@Composable
private fun ThermalRows(
    status: ThermalSnapshot?,
    headroom: ThermalHeadroomSnapshot?,
) {
    if (status == null) {
        MetricRow(
            label = stringResource(R.string.status_thermal),
            icon = Icons.Outlined.DeviceThermostat,
            statusVariant = StatusChipVariant.UNAVAILABLE,
            statusText = stringResource(R.string.reason_no_reading),
        )
    } else {
        thermalRows(status = status, headroom = headroom)
            .filter { row ->
                row.labelKey != MetricRowKeys.THERMAL_HEADROOM || row.rawValue != null
            }
            .forEach { row ->
                MapperMetricRow(row = row, icon = Icons.Outlined.DeviceThermostat)
            }
    }
}

/**
 * System memory must stay unambiguous: "Available" and "Total" are separate, labeled
 * lines — availMem is never shown as a usage percentage. "This app" is the JVM heap of
 * this process only, and is labeled as such.
 */
@Composable
private fun MemoryRows(snapshot: MemorySnapshot?) {
    val available = snapshot?.availableBytes
    val total = snapshot?.totalBytes
    if (available != null && total != null) {
        MetricRow(
            label = stringResource(R.string.memory_available),
            icon = Icons.Outlined.Memory,
            value = stringResource(R.string.memory_value_gb, formatGigabytes(available)),
        )
        MetricRow(
            label = stringResource(R.string.memory_total),
            icon = Icons.Outlined.Memory,
            value = stringResource(R.string.memory_value_gb, formatGigabytes(total)),
        )
        snapshot.appHeapUsedMb?.let { heapMb ->
            MetricRow(
                label = stringResource(R.string.memory_this_app),
                icon = Icons.Outlined.Memory,
                value = stringResource(R.string.memory_app_value_mb, heapMb),
            )
        }
    } else {
        MetricRow(
            label = stringResource(R.string.status_memory),
            icon = Icons.Outlined.Memory,
            statusVariant = StatusChipVariant.UNAVAILABLE,
            statusText = if (snapshot == null) {
                stringResource(R.string.reason_no_reading)
            } else {
                availabilityLabel(snapshot.availability)
            },
        )
    }
}

@Composable
private fun CpuRow(snapshot: CpuSnapshot?) {
    val cores = snapshot?.coreCount
    if (cores != null) {
        MetricRow(
            label = stringResource(R.string.status_cpu),
            icon = Icons.Outlined.DeveloperBoard,
            value = stringResource(R.string.cpu_cores_value, cores),
        )
    } else {
        MetricRow(
            label = stringResource(R.string.status_cpu),
            icon = Icons.Outlined.DeveloperBoard,
            statusVariant = StatusChipVariant.UNAVAILABLE,
            statusText = if (snapshot == null) {
                stringResource(R.string.reason_no_reading)
            } else {
                availabilityLabel(snapshot.availability)
            },
        )
    }
}

/**
 * Shortcut card into the Library tab. The count is the real, live library size.
 * The whole card is one press target with automatic reduced-motion-aware scale
 * feedback; the visible hint text doubles as the screen-reader click label.
 */
@Composable
private fun LibraryShortcutCard(gameCount: Int, onOpenLibrary: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    SectionCard(
        title = stringResource(R.string.home_library_title),
        icon = Icons.Outlined.SportsEsports,
        modifier = Modifier
            .ngbPressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClickLabel = stringResource(R.string.home_library_open_hint),
                onClick = onOpenLibrary,
            ),
    ) {
        Text(
            text = if (gameCount == 0) {
                stringResource(R.string.home_library_empty)
            } else {
                stringResource(R.string.home_library_count, gameCount)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.home_library_open_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Home quick-select for the three profiles (renders automatically from the repository
 * interface). Selection persists a preference only; a failing write shows one honest
 * localized error line — the previously persisted profile is still the active one.
 */
@Composable
private fun ProfileQuickCard(
    profiles: List<PerformanceProfile>,
    activeProfileId: String?,
    saveError: Boolean,
    onSelect: (String) -> Unit,
    motionEnabled: Boolean,
) {
    SectionCard(
        title = stringResource(R.string.home_profiles_title),
        icon = Icons.Outlined.Tune,
    ) {
        profiles.forEach { profile ->
            ProfileSelectRow(
                profile = profile,
                selected = activeProfileId == profile.id,
                onClick = { onSelect(profile.id) },
                motionEnabled = motionEnabled,
            )
        }
        if (saveError) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.profile_save_failed),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * One radio row of the quick-select group: proper [Role.RadioButton] semantics (the
 * merged row reads the profile name plus the radio's selected state), a 48 dp minimum
 * touch height, and the shared restrained press scale — all instantly off when the
 * reduce-motion preference disables [motionEnabled].
 */
@Composable
private fun ProfileSelectRow(
    profile: PerformanceProfile,
    selected: Boolean,
    onClick: () -> Unit,
    motionEnabled: Boolean,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .ngbPressScale(interactionSource, motionEnabled)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(vertical = 4.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Icon(
            imageVector = profileIcon(profile.id),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(profile.titleRes),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ActivityCard(entries: List<ActivityEntry>) {
    SectionCard(
        title = stringResource(R.string.recent_activity),
        icon = Icons.Outlined.History,
    ) {
        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.activity_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            entries.forEach { entry ->
                ActivityRow(entry = entry)
            }
        }
    }
}

@Composable
private fun ActivityRow(entry: ActivityEntry) {
    val timeText = remember(entry.timeMillis) {
        DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(Date(entry.timeMillis))
    }
    val argText = entry.messageArg ?: entry.messageArgRes?.let { stringResource(it) }
    val message = if (argText != null) {
        stringResource(entry.messageRes, argText)
    } else {
        stringResource(entry.messageRes)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = timeText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text = message, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Persisted boost history (Phase 5): the newest reports with their REAL status and
 * completion time; tapping reopens that report read-only in the result dialog. The
 * history is local-only (DataStore on this device) — stated honestly under the list.
 */
@Composable
private fun BoostHistoryCard(
    reports: List<BoostReport>,
    onOpenReport: (BoostReport) -> Unit,
    motionEnabled: Boolean,
) {
    SectionCard(
        title = stringResource(R.string.boost_history_title),
        icon = Icons.Outlined.History,
    ) {
        if (reports.isEmpty()) {
            Text(
                text = stringResource(R.string.boost_history_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            reports.forEach { report ->
                BoostHistoryRow(
                    report = report,
                    onOpen = { onOpenReport(report) },
                    motionEnabled = motionEnabled,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.boost_history_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One tappable history entry: opens the report read-only (announced via the explicit
 * click label), keeps the color dot + status TEXT pair (never color alone), respects
 * the 48 dp minimum target, and scales on press only when motion is enabled.
 */
@Composable
private fun BoostHistoryRow(
    report: BoostReport,
    onOpen: () -> Unit,
    motionEnabled: Boolean,
) {
    val timeText = remember(report.completedAtMillis) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(report.completedAtMillis))
    }
    val statusText = stringResource(BoostViewModel.statusLabelRes(report.status))
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .ngbPressScale(interactionSource, motionEnabled)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClickLabel = stringResource(R.string.a11y_open_report),
                onClick = onOpen,
            )
            .padding(vertical = 6.dp, horizontal = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(boostStatusColor(report.status)),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
            )
            val detail = listOfNotNull(report.gameName).joinToString(" · ")
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = timeText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun boostStatusColor(status: BoostStatus) = when (status) {
    BoostStatus.COMPLETED -> MaterialTheme.colorScheme.primary
    BoostStatus.PARTIAL_SUCCESS -> MaterialTheme.colorScheme.secondary
    BoostStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
    BoostStatus.FAILED -> MaterialTheme.colorScheme.error
}

/** Binary GB with one decimal — the same convention Android settings use for RAM. */
private fun formatGigabytes(bytes: Long): String =
    String.format(Locale.getDefault(), "%.1f", bytes / BYTES_PER_GB)
