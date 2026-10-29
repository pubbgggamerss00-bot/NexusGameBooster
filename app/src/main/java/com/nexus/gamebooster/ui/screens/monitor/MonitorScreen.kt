package com.nexus.gamebooster.ui.screens.monitor

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexus.gamebooster.R
import com.nexus.gamebooster.core.SessionMonitorMode
import com.nexus.gamebooster.core.ServiceLocator
import com.nexus.gamebooster.core.VmFactory
import com.nexus.gamebooster.core.chargingStateLabel
import com.nexus.gamebooster.core.metricRowLabel
import com.nexus.gamebooster.core.pluggedLabel
import com.nexus.gamebooster.core.thermalStatusLabel
import com.nexus.gamebooster.core.unavailableReasonLabel
import com.nexus.gamebooster.monitoring.BatterySnapshot
import com.nexus.gamebooster.monitoring.CpuSnapshot
import com.nexus.gamebooster.monitoring.DeviceFacts
import com.nexus.gamebooster.monitoring.MemorySnapshot
import com.nexus.gamebooster.monitoring.MetricAvailability
import com.nexus.gamebooster.monitoring.MetricRowKeys
import com.nexus.gamebooster.monitoring.MetricRowUi
import com.nexus.gamebooster.monitoring.ThermalHeadroomSnapshot
import com.nexus.gamebooster.monitoring.ThermalSnapshot
import com.nexus.gamebooster.monitoring.UnavailableReasons
import com.nexus.gamebooster.monitoring.batteryRows
import com.nexus.gamebooster.monitoring.deviceRows
import com.nexus.gamebooster.monitoring.memoryRows
import com.nexus.gamebooster.monitoring.thermalRows
import com.nexus.gamebooster.profiles.PerformanceProfile
import com.nexus.gamebooster.ui.arcade.ArcadeButton
import com.nexus.gamebooster.ui.arcade.ArcadeChip
import com.nexus.gamebooster.ui.arcade.ArcadeGauge
import com.nexus.gamebooster.ui.arcade.ArcadeHead
import com.nexus.gamebooster.ui.arcade.ArcadeKvRow
import com.nexus.gamebooster.ui.arcade.ArcadeOutlineButton
import com.nexus.gamebooster.ui.arcade.ArcadePanel
import com.nexus.gamebooster.ui.arcade.ArcadePanelVariant
import com.nexus.gamebooster.ui.arcade.ArcadeSectionLabel
import com.nexus.gamebooster.ui.arcade.ArcadeStatusDot
import com.nexus.gamebooster.ui.arcade.ArcadeTint
import com.nexus.gamebooster.ui.arcade.NgbArcadeColors
import com.nexus.gamebooster.ui.components.NgbScreenContainer
import com.nexus.gamebooster.ui.components.StatusChipVariant
import com.nexus.gamebooster.ui.components.chipVariantForReason
import com.nexus.gamebooster.ui.theme.NgbHudFont
import com.nexus.gamebooster.ui.theme.NgbTagFont
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * Phase 4 Monitor dashboard, restyled as the "ARCADE HUD" reference (v1.2.1):
 * chamfered [ArcadePanel]s, a LIVE badge row, ring gauges and dashed `.kv` rows.
 * Every card still renders REAL reads through the shared mappers
 * (`monitoring/MonitoringMappers.kt`) and the same core label resolvers:
 *  - Battery: live (runtime-registered receiver, updates only while collected and
 *    only while the active profile's policy allows the live stream).
 *  - Thermal status (Android 10+) + thermal headroom (Android 11+, only when the
 *    device actually reports it — the PowerManager abstraction, never a °C value).
 *  - Memory: Available / Total / derived "In use (calculated)" / this app's heap.
 *  - Device: real Build/Settings facts.  - Data freshness + manual refresh.
 *
 * HONEST GAUGE MAPPING (the reference shows RAM %, battery %, battery °C and CPU
 * cores gauges; this app only gauges what it really reads):
 *  - Battery % gauge (real [BatterySnapshot.levelPercent]).
 *  - Memory "In use (calculated)" % gauge — the SAME arithmetic the mappers already
 *    present as a derived row (total − available), labeled with the existing
 *    "(calculated)" string and the derived-value note. Never presented as measured.
 *  - NO battery-temperature gauge: this app never reads a temperature (the thermal
 *    API it uses is the status/headroom abstraction, not °C) — the reference's °C
 *    gauge is deliberately omitted instead of invented.
 *  - CPU cores stay a kv row (a count has no honest ring percentage).
 *  - NO sparkline: no sample history is kept anywhere in the ViewModel, so the
 *    single-reading presentation is kept (no fake history, no fake animation).
 *  - A metric that cannot be read keeps its honest unavailable presentation,
 *    restyled as a FLAT [ArcadePanel] carrying the existing reason string.
 *
 * The active profile drives THIS APP's monitoring behavior only (policy chips under
 * the header + the auto-refresh chip in the LIVE panel): auto-refresh cadence while
 * this screen is lifecycle-active, live battery stream on/off, and a keep-screen-on
 * WINDOW flag (never a wake lock). CPU load, GPU and FPS are NOT shown — there is no
 * permission-free API; the warning honesty card states this. No fake real-time
 * animation: the only motion is the kit's motion-gated gauge arc / LIVE dot.
 */
@Composable
fun MonitorScreen() {
    val vm: MonitorViewModel = viewModel(factory = VmFactory { ServiceLocator.monitorViewModel() })
    val state by vm.uiState.collectAsStateWithLifecycle()

    // Screen-active flag for the VM's auto-refresh ticker: the VM ticks ONLY while this
    // screen is at least lifecycle-STARTED. The observer is attached here and removed
    // on dispose, so the flag can never outlive the screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> vm.onMonitorScreenActive()
                Lifecycle.Event.ON_STOP -> vm.onMonitorScreenInactive()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Profile-driven keep-screen-on: a WINDOW flag on the Activity (not a PowerManager
    // wake lock — needs no permission) applied ONLY while this composable is composed
    // under a profile that enables it, and cleared the moment it leaves composition.
    if (state.profile?.monitoringPolicy?.keepScreenOnWhileVisible == true) {
        KeepScreenOnEffect()
    }

    // Real session uptime: wall-clock seconds since the app process started.
    // Ticker runs only while this screen is composed (LaunchedEffect cancels on leave).
    val sessionStartMillis = remember { ServiceLocator.sessionStartMillis }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val uptimeText = remember(nowMillis, sessionStartMillis) {
        formatUptime(nowMillis - sessionStartMillis)
    }

    NgbScreenContainer {
        // Arcade header (code tag + headline + subtitle) with the prominent manual
        // refresh beside it. While a read is running the button is disabled and the
        // honest "Reading device…" note shows.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            ArcadeHead(
                code = "MONITOR",
                title = stringResource(R.string.monitor_headline),
                subtitle = stringResource(R.string.monitor_subtitle),
                modifier = Modifier.weight(1f),
            )
            ArcadeButton(
                onClick = vm::refresh,
                enabled = !state.isRefreshing,
                small = true,
            ) {
                Text(
                    text = stringResource(R.string.refresh),
                    fontFamily = NgbHudFont,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        // Honest policy chips from the LOADED profile. While the profile has not loaded
        // yet, a neutral "loading policy" state is shown — never a guess. The
        // auto-refresh chip lives inside the LIVE panel; this row carries the
        // keep-screen-on chip and the session-tuning chip.
        PolicyRow(profile = state.profile, sessionMode = state.sessionMode)
        if (state.isRefreshing) {
            Text(
                text = stringResource(R.string.refreshing),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))

        state.errorReason?.let { reason ->
            ErrorCard(reason = reason, onRetry = vm::refresh)
            Spacer(Modifier.height(16.dp))
        }

        // Landscape / tablet (≥ 600 dp): the trend/thermal and cpu/device panels sit
        // side by side; narrower screens keep the single column. Plain
        // BoxWithConstraints on width, no WindowSizeClass dependency.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val twoColumns = maxWidth >= 600.dp
            Column {
                LivePanel(
                    battery = state.battery,
                    memory = state.memory,
                    profile = state.profile,
                )
                Spacer(Modifier.height(16.dp))
                if (twoColumns) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        MemoryTrendPanel(
                            memory = state.memory,
                            thermal = state.thermal,
                            modifier = Modifier.weight(1f),
                        )
                        ThermalPanel(
                            status = state.thermal,
                            headroom = state.headroom,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        CpuPanel(snapshot = state.cpu, modifier = Modifier.weight(1f))
                        DevicePanel(facts = state.deviceFacts, modifier = Modifier.weight(1f))
                    }
                } else {
                    MemoryTrendPanel(memory = state.memory, thermal = state.thermal)
                    Spacer(Modifier.height(16.dp))
                    ThermalPanel(status = state.thermal, headroom = state.headroom)
                    Spacer(Modifier.height(16.dp))
                    CpuPanel(snapshot = state.cpu)
                    Spacer(Modifier.height(16.dp))
                    DevicePanel(facts = state.deviceFacts)
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        SessionPanel(uptimeText = uptimeText, lastUpdatedMillis = state.lastUpdatedMillis)
        Spacer(Modifier.height(16.dp))

        HonestyCard()
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Honest policy chips for the LOADED profile: what this app itself will do while the
 * Monitor screen is open. Null profile → neutral "loading policy" text (the policy is
 * not yet known, so nothing is claimed and no chip pretends otherwise). The
 * auto-refresh chip is rendered inside the LIVE panel; this row keeps the
 * keep-screen-on chip (azure) and the Phase 5 efficient-session chip (lime).
 */
@Composable
private fun PolicyRow(profile: PerformanceProfile?, sessionMode: SessionMonitorMode) {
    if (profile == null) {
        Text(
            text = stringResource(R.string.monitor_policy_loading),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val policy = profile.monitoringPolicy
    val showKeepScreenOn = policy.keepScreenOnWhileVisible
    val efficient = sessionMode == SessionMonitorMode.EFFICIENT
    if (showKeepScreenOn || efficient) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (showKeepScreenOn) {
                ArcadeChip(
                    text = stringResource(R.string.monitor_keep_screen_on_chip),
                    tint = NgbArcadeColors.Azure,
                )
            }
            if (efficient) {
                // Honest session-tuning chip (Phase 5): the boost engine put THIS app's
                // own monitoring into the power-efficient session mode after a real
                // low-battery read. Nothing system-wide changed; manual refresh works.
                ArcadeChip(
                    text = stringResource(R.string.monitor_session_efficient_chip),
                    tint = NgbArcadeColors.Lime,
                )
            }
        }
    }
}

/**
 * Keeps the screen on while the Monitor screen is composed AND the active profile's
 * policy enables it. HONESTY + SAFETY: this is the standard window flag
 * (FLAG_KEEP_SCREEN_ON) set on the Activity's window — NOT a PowerManager wake lock,
 * so it needs no permission and no manifest change. The flag is cleared in onDispose,
 * i.e. the moment this composable leaves composition (or the profile changes), so the
 * screen-on effect can never outlive this screen. If no Activity window can be resolved
 * from the local context, nothing is done (no crash, no fallback).
 */
@Composable
private fun KeepScreenOnEffect() {
    val context = LocalContext.current
    DisposableEffect(context) {
        // Walk the ContextWrapper chain (e.g. ContextThemeWrapper) to find the Activity.
        var cursor: Context = context
        var window: Window? = null
        while (window == null && cursor is ContextWrapper) {
            if (cursor is Activity) {
                window = cursor.window
            }
            cursor = cursor.baseContext
        }
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

/**
 * The LIVE panel (the reference's first `.panel`): pulsing danger dot + Orbitron
 * "LIVE" badge, the profile's auto-refresh chip on the right, then the honest gauge
 * row (battery % / memory in-use %) with the real battery detail rows below.
 */
@Composable
private fun LivePanel(
    battery: BatterySnapshot?,
    memory: MemorySnapshot?,
    profile: PerformanceProfile?,
    modifier: Modifier = Modifier,
) {
    ArcadePanel(modifier = modifier.fillMaxWidth(), variant = ArcadePanelVariant.PLAIN) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                ArcadeStatusDot(color = NgbArcadeColors.Danger)
                Spacer(Modifier.width(6.dp))
                // Decorative HUD tag, hardcoded English exactly like the reference.
                Text(
                    text = "LIVE",
                    fontFamily = NgbTagFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    color = NgbArcadeColors.Danger,
                )
                Spacer(Modifier.weight(1f))
                AutoRefreshChip(profile = profile)
            }
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(modifier = Modifier.weight(1f)) { BatterySlot(snapshot = battery) }
                Box(modifier = Modifier.weight(1f)) { MemorySlot(snapshot = memory) }
            }
            // Battery details behind the gauge: charging state + power source when the
            // platform reported them (the level itself is the gauge above).
            if (battery?.levelPercent != null) {
                Spacer(Modifier.height(8.dp))
                batteryRows(battery)
                    .filter { it.labelKey != MetricRowKeys.BATTERY_LEVEL }
                    .forEach { row -> ArcadeKvRowFor(row = row) }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.monitor_battery_note),
                fontSize = 10.sp,
                color = NgbArcadeColors.Muted,
            )
        }
    }
}

/** The profile's auto-refresh cadence chip (azure). Null profile → loading text. */
@Composable
private fun AutoRefreshChip(profile: PerformanceProfile?) {
    val policy = profile?.monitoringPolicy
    if (policy == null) {
        Text(
            text = stringResource(R.string.monitor_policy_loading),
            fontSize = 11.sp,
            color = NgbArcadeColors.Muted,
        )
        return
    }
    val intervalMillis = policy.autoRefreshIntervalMillis
    ArcadeChip(
        text = if (policy.inAppMonitoringEnabled && intervalMillis != null) {
            stringResource(R.string.monitor_auto_refresh_every, (intervalMillis / 1000L).toInt())
        } else {
            stringResource(R.string.monitor_auto_refresh_off)
        },
        tint = NgbArcadeColors.Azure,
    )
}

/** Battery gauge slot: real level %, or the honest unavailable/refreshing square. */
@Composable
private fun BatterySlot(snapshot: BatterySnapshot?) {
    val level = snapshot?.levelPercent
    if (level != null) {
        ArcadeGauge(
            value = level.toFloat(),
            max = 100f,
            color = NgbArcadeColors.Lime,
            title = stringResource(R.string.status_battery),
            valueText = level.toString(),
            unitText = "%",
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        UnavailableSlot(
            title = stringResource(R.string.status_battery),
            note = if (snapshot == null) {
                stringResource(R.string.refreshing)
            } else {
                unavailableReasonLabel(availabilityReason(snapshot.availability))
            },
            noteColor = if (snapshot == null) {
                NgbArcadeColors.Muted
            } else {
                reasonValueColor(availabilityReason(snapshot.availability))
            },
        )
    }
}

/**
 * Memory gauge slot: "In use (calculated)" % — the same total − available arithmetic
 * the mappers already present as a derived row, labeled with the existing
 * "(calculated)" string. No measured usage percentage exists anywhere.
 */
@Composable
private fun MemorySlot(snapshot: MemorySnapshot?) {
    val total = snapshot?.totalMb
    val available = snapshot?.availableMb
    val usedPercent = if (total != null && available != null) {
        derivedUsedPercent(totalMb = total, availableMb = available)
    } else {
        null
    }
    if (usedPercent != null) {
        ArcadeGauge(
            value = usedPercent.toFloat(),
            max = 100f,
            color = NgbArcadeColors.Neon,
            title = stringResource(R.string.memory_in_use_calculated),
            valueText = usedPercent.toString(),
            unitText = "%",
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        UnavailableSlot(
            title = stringResource(R.string.status_memory),
            note = if (snapshot == null) {
                stringResource(R.string.refreshing)
            } else {
                unavailableReasonLabel(availabilityReason(snapshot.availability))
            },
            noteColor = if (snapshot == null) {
                NgbArcadeColors.Muted
            } else {
                reasonValueColor(availabilityReason(snapshot.availability))
            },
        )
    }
}

/**
 * The reference's gauge-shaped placeholder for a metric that could not be read: a
 * FLAT panel square (same footprint as the ring) carrying the existing honest reason
 * string, with the metric title below — mirroring [ArcadeGauge]'s structure.
 */
@Composable
private fun UnavailableSlot(
    title: String,
    note: String,
    noteColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArcadePanel(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            variant = ArcadePanelVariant.FLAT,
            contentPadding = 10.dp,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = note,
                    fontSize = 12.sp,
                    color = noteColor,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = title,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The "RAM trend" panel (reference: big HUD % + sparkline + thermal chip). No sample
 * history is kept by the ViewModel, so the sparkline is omitted — the single-reading
 * presentation stays: big HUD in-use % + the real memory kv rows + the thermal status
 * chip (warn) exactly where the reference puts it.
 */
@Composable
private fun MemoryTrendPanel(
    memory: MemorySnapshot?,
    thermal: ThermalSnapshot?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ArcadeSectionLabel(text = stringResource(R.string.status_memory))
        Spacer(Modifier.height(8.dp))
        ArcadePanel(modifier = Modifier.fillMaxWidth(), variant = ArcadePanelVariant.PLAIN) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val total = memory?.totalMb
                    val available = memory?.availableMb
                    val usedPercent = if (total != null && available != null) {
                        derivedUsedPercent(totalMb = total, availableMb = available)
                    } else {
                        null
                    }
                    when {
                        usedPercent != null -> Column {
                            Text(
                                text = stringResource(R.string.memory_in_use_calculated),
                                fontSize = 10.sp,
                                color = NgbArcadeColors.Muted,
                            )
                            Text(
                                text = "$usedPercent%",
                                fontFamily = NgbHudFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 26.sp,
                                color = NgbArcadeColors.Neon,
                            )
                        }

                        memory == null -> RefreshingNote()

                        else -> {
                            val reason = availabilityReason(memory.availability)
                            Text(
                                text = unavailableReasonLabel(reason),
                                fontSize = 13.sp,
                                color = reasonValueColor(reason),
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    ThermalChip(status = thermal)
                }
                Spacer(Modifier.height(12.dp))
                memory?.let { snapshot ->
                    memoryRows(snapshot).forEach { row -> ArcadeKvRowFor(row = row) }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.monitor_memory_note),
                    fontSize = 10.sp,
                    color = NgbArcadeColors.Muted,
                )
            }
        }
    }
}

/**
 * Thermal status chip (warn) in the trend-panel header row: the localized thermal_*
 * label when the platform reported a status; the honest unavailable/refreshing text
 * when it did not. Never a guessed state.
 */
@Composable
private fun ThermalChip(status: ThermalSnapshot?) {
    when {
        status == null -> Text(
            text = stringResource(R.string.refreshing),
            fontSize = 11.sp,
            color = NgbArcadeColors.Muted,
        )

        status.statusLabel != null -> ArcadeChip(
            text = thermalStatusLabel(status.statusLabel) ?: status.statusLabel,
            tint = NgbArcadeColors.Warn,
        )

        else -> {
            val reason = availabilityReason(status.availability)
            Text(
                text = unavailableReasonLabel(reason),
                fontSize = 11.sp,
                color = reasonValueColor(reason),
            )
        }
    }
}

/**
 * Thermal panel: the headroom kv row (only when the platform reported it — the
 * PowerManager abstraction, never a °C value) plus the honest status reason row on
 * devices where the status itself is unavailable (the label is already shown as the
 * warn chip in the trend panel, so it is not duplicated here).
 */
@Composable
private fun ThermalPanel(
    status: ThermalSnapshot?,
    headroom: ThermalHeadroomSnapshot?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ArcadeSectionLabel(text = stringResource(R.string.status_thermal))
        Spacer(Modifier.height(8.dp))
        ArcadePanel(modifier = Modifier.fillMaxWidth(), variant = ArcadePanelVariant.PLAIN) {
            Column {
                if (status == null) {
                    RefreshingNote()
                } else {
                    thermalRows(status = status, headroom = headroom)
                        .filter {
                            it.labelKey != MetricRowKeys.THERMAL_STATUS || status.statusLabel == null
                        }
                        .forEach { row -> ArcadeKvRowFor(row = row) }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.monitor_thermal_note),
                    fontSize = 10.sp,
                    color = NgbArcadeColors.Muted,
                )
            }
        }
    }
}

/**
 * CPU panel: the real core count (a count has no honest ring percentage, so it stays
 * a kv row — azure value) or the honest reason. CPU load is NOT sampled and will not
 * be: no permission-free API exists; the warning card at the bottom states this.
 */
@Composable
private fun CpuPanel(snapshot: CpuSnapshot?, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        ArcadeSectionLabel(text = stringResource(R.string.status_cpu))
        Spacer(Modifier.height(8.dp))
        ArcadePanel(modifier = Modifier.fillMaxWidth(), variant = ArcadePanelVariant.PLAIN) {
            Column {
                val cores = snapshot?.coreCount
                when {
                    cores != null -> ArcadeKvRow(
                        label = stringResource(R.string.status_cpu),
                        value = stringResource(R.string.cpu_cores_value, cores),
                        valueColor = NgbArcadeColors.Azure,
                    )

                    snapshot == null -> RefreshingNote()

                    else -> {
                        val reason = availabilityReason(snapshot.availability)
                        ArcadeKvRow(
                            label = stringResource(R.string.status_cpu),
                            value = unavailableReasonLabel(reason),
                            valueColor = reasonValueColor(reason),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.monitor_cpu_note),
                    fontSize = 10.sp,
                    color = NgbArcadeColors.Muted,
                )
            }
        }
    }
}

/** Device facts panel: real Build/Settings values as HUD kv rows. */
@Composable
private fun DevicePanel(facts: DeviceFacts?, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        ArcadeSectionLabel(text = stringResource(R.string.home_device_info))
        Spacer(Modifier.height(8.dp))
        ArcadePanel(modifier = Modifier.fillMaxWidth(), variant = ArcadePanelVariant.PLAIN) {
            Column {
                if (facts == null) {
                    RefreshingNote()
                } else {
                    deviceRows(facts).forEach { row -> ArcadeKvRowFor(row = row) }
                }
            }
        }
    }
}

/**
 * Session panel (reference: two `.kv` rows): real wall-clock session uptime and the
 * real "last updated" timestamp of the last successful refresh.
 */
@Composable
private fun SessionPanel(
    uptimeText: String,
    lastUpdatedMillis: Long?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ArcadeSectionLabel(text = stringResource(R.string.monitor_last_updated_title))
        Spacer(Modifier.height(8.dp))
        ArcadePanel(modifier = Modifier.fillMaxWidth(), variant = ArcadePanelVariant.PLAIN) {
            Column {
                ArcadeKvRow(
                    label = stringResource(R.string.monitor_session),
                    value = uptimeText,
                )
                val value = if (lastUpdatedMillis == null) {
                    stringResource(R.string.monitor_never_refreshed)
                } else {
                    remember(lastUpdatedMillis) {
                        DateFormat.getDateTimeInstance().format(Date(lastUpdatedMillis))
                    }
                }
                ArcadeKvRow(
                    label = stringResource(R.string.monitor_last_updated),
                    value = value,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.monitor_session_note),
                    fontSize = 10.sp,
                    color = NgbArcadeColors.Muted,
                )
            }
        }
    }
}

/** The honesty card (reference `.panel.warn`): why CPU load, GPU and FPS are absent. */
@Composable
private fun HonestyCard(modifier: Modifier = Modifier) {
    ArcadePanel(
        modifier = modifier.fillMaxWidth(),
        variant = ArcadePanelVariant.WARN,
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = NgbArcadeColors.Warn,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = stringResource(R.string.monitor_explanation_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.monitor_explanation_body),
                    fontSize = 12.sp,
                    color = NgbArcadeColors.Muted,
                )
            }
        }
    }
}

/** Refresh-failure card restyled as the reference's `.panel.bad` + outline retry. */
@Composable
private fun ErrorCard(reason: String, onRetry: () -> Unit) {
    ArcadePanel(modifier = Modifier.fillMaxWidth(), variant = ArcadePanelVariant.BAD) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = NgbArcadeColors.Danger,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.monitor_error_title),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = unavailableReasonLabel(reason),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            ArcadeOutlineButton(onClick = onRetry, tint = ArcadeTint.DANGER) {
                Text(text = stringResource(R.string.action_retry))
            }
        }
    }
}

/** Muted "Reading device…" note for panels whose snapshot has not arrived yet. */
@Composable
private fun RefreshingNote() {
    Text(
        text = stringResource(R.string.refreshing),
        fontSize = 12.sp,
        color = NgbArcadeColors.Muted,
    )
}

/**
 * One [MetricRowUi] rendered as the reference's dashed `.kv` row, resolved through
 * the SAME core resolvers [MapperMetricRow] uses (single source of localized text):
 * availability keys → the honest reason text (muted for permanent OS limits, warn for
 * transient failures — mirroring the old chip variants); raw values → locale-aware
 * headroom formatting plus its unit-hint footnote; machine-key values → localized
 * labels; derived rows → neon value plus the "calculated, not measured" footnote.
 */
@Composable
private fun ArcadeKvRowFor(row: MetricRowUi, modifier: Modifier = Modifier) {
    val label = metricRowLabel(row.labelKey)
    val availabilityKey = row.availabilityKey
    val rawValue = row.rawValue
    when {
        availabilityKey != null -> ArcadeKvRow(
            label = label,
            value = unavailableReasonLabel(availabilityKey),
            valueColor = reasonValueColor(availabilityKey),
            modifier = modifier,
        )

        rawValue != null -> Column(modifier = modifier.fillMaxWidth()) {
            ArcadeKvRow(
                label = label,
                value = stringResource(R.string.thermal_headroom_value, rawValue),
                valueColor = NgbArcadeColors.Warn,
            )
            KvFootnote(text = stringResource(R.string.thermal_headroom_unit_hint))
        }

        row.labelKey == MetricRowKeys.BATTERY_CHARGING_STATE -> ArcadeKvRow(
            label = label,
            value = chargingStateLabel(row.value),
            modifier = modifier,
        )

        row.labelKey == MetricRowKeys.BATTERY_POWER_SOURCE -> ArcadeKvRow(
            label = label,
            value = pluggedLabel(row.value) ?: row.value,
            modifier = modifier,
        )

        row.labelKey == MetricRowKeys.THERMAL_STATUS -> ArcadeKvRow(
            label = label,
            value = thermalStatusLabel(row.value) ?: row.value,
            valueColor = NgbArcadeColors.Warn,
            modifier = modifier,
        )

        row.derived -> Column(modifier = modifier.fillMaxWidth()) {
            ArcadeKvRow(
                label = label,
                value = row.value,
                valueColor = NgbArcadeColors.Neon,
            )
            KvFootnote(text = stringResource(R.string.derived_value_note))
        }

        else -> ArcadeKvRow(
            label = label,
            value = row.value,
            modifier = modifier,
        )
    }
}

/** Tiny muted footnote under a kv row (unit hints / calculated-value notes). */
@Composable
private fun KvFootnote(text: String) {
    Spacer(Modifier.height(4.dp))
    Text(
        text = text,
        fontSize = 10.sp,
        color = NgbArcadeColors.Muted,
    )
}

/** Reason text color: muted for permanent OS limits, warn for transient failures. */
private fun reasonValueColor(reason: String): Color =
    if (chipVariantForReason(reason) == StatusChipVariant.UNSUPPORTED) {
        NgbArcadeColors.Muted
    } else {
        NgbArcadeColors.Warn
    }

/**
 * The in-use memory percentage: arithmetic over the two REAL values the screen
 * already shows (total − available over total), rounded and clamped. Null when the
 * inputs are missing or nonsensical — never a guessed number.
 */
private fun derivedUsedPercent(totalMb: Long, availableMb: Long): Int? =
    if (totalMb > 0) {
        (((totalMb - availableMb) * 100f) / totalMb).roundToInt().coerceIn(0, 100)
    } else {
        null
    }

private fun availabilityReason(availability: MetricAvailability): String =
    (availability as? MetricAvailability.Unavailable)?.reason ?: UnavailableReasons.NO_READING

/** Plain wall-clock formatter: h:mm:ss or mm:ss. No fake precision. */
private fun formatUptime(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
