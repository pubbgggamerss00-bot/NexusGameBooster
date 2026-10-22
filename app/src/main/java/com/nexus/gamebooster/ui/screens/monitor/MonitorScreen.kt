package com.nexus.gamebooster.ui.screens.monitor

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.DeviceThermostat
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Warning
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexus.gamebooster.R
import com.nexus.gamebooster.core.ServiceLocator
import com.nexus.gamebooster.core.SessionMonitorMode
import com.nexus.gamebooster.core.VmFactory
import com.nexus.gamebooster.core.unavailableReasonLabel
import com.nexus.gamebooster.monitoring.BatterySnapshot
import com.nexus.gamebooster.monitoring.CpuSnapshot
import com.nexus.gamebooster.monitoring.DeviceFacts
import com.nexus.gamebooster.monitoring.MemorySnapshot
import com.nexus.gamebooster.monitoring.MetricAvailability
import com.nexus.gamebooster.monitoring.ThermalHeadroomSnapshot
import com.nexus.gamebooster.monitoring.ThermalSnapshot
import com.nexus.gamebooster.monitoring.UnavailableReasons
import com.nexus.gamebooster.monitoring.batteryRows
import com.nexus.gamebooster.monitoring.deviceRows
import com.nexus.gamebooster.monitoring.memoryRows
import com.nexus.gamebooster.monitoring.thermalRows
import com.nexus.gamebooster.profiles.PerformanceProfile
import com.nexus.gamebooster.ui.components.MapperMetricRow
import com.nexus.gamebooster.ui.components.MetricRow
import com.nexus.gamebooster.ui.components.NgbButton
import com.nexus.gamebooster.ui.components.NgbOutlinedButton
import com.nexus.gamebooster.ui.components.NgbScreenContainer
import com.nexus.gamebooster.ui.components.SectionCard
import com.nexus.gamebooster.ui.components.StatusChip
import com.nexus.gamebooster.ui.components.StatusChipVariant
import com.nexus.gamebooster.ui.components.chipVariantForReason
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay

/**
 * Phase 4 Monitor dashboard. Every card renders REAL reads through the shared
 * mappers (`monitoring/MonitoringMappers.kt` + `MapperMetricRow`):
 *  - Battery: live (runtime-registered receiver, updates only while collected and
 *    only while the active profile's policy allows the live stream).
 *  - Thermal status (Android 10+) + thermal headroom (Android 11+, only when the
 *    device actually reports it — the PowerManager abstraction, never a °C value).
 *  - Memory: Available / Total / derived "In use (calculated)" / this app's heap.
 *  - Device: real Build/Settings facts.  - Data freshness + manual refresh.
 * The active profile drives THIS APP's monitoring behavior only (policy chips under
 * the header): auto-refresh cadence while this screen is lifecycle-active, live
 * battery stream on/off, and a keep-screen-on WINDOW flag (never a wake lock).
 * CPU load, GPU and FPS are NOT shown — there is no permission-free API; the
 * explanation card states this honestly. No charts, no fake real-time animation.
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.monitor_headline),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            // Prominent manual refresh. This screen uses no animations at all, so the
            // reduce-motion preference is respected by construction; while a read is
            // running the button is disabled and the honest "Reading device…" note shows.
            NgbButton(onClick = vm::refresh, enabled = !state.isRefreshing) {
                Text(stringResource(R.string.refresh))
            }
        }
        Text(
            text = stringResource(R.string.monitor_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        // Honest policy chips from the LOADED profile. While the profile has not loaded
        // yet, a neutral "loading policy" state is shown — never a guess.
        PolicyChipsRow(profile = state.profile)
        if (state.sessionMode == SessionMonitorMode.EFFICIENT) {
            Spacer(Modifier.height(6.dp))
            // Honest session-tuning chip (Phase 5): the boost engine put THIS app's own
            // monitoring into the power-efficient session mode after a real low-battery
            // read. Nothing system-wide changed; manual refresh still works.
            StatusChip(
                text = stringResource(R.string.monitor_session_efficient_chip),
                variant = StatusChipVariant.OK,
                modifier = Modifier.fillMaxWidth(),
            )
        }
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

        // Landscape / tablet (≥ 600 dp): the two main sections — live battery status and
        // device info — sit side by side; narrower screens keep the single column.
        // Plain BoxWithConstraints on width, no WindowSizeClass dependency.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val twoColumns = maxWidth >= 600.dp
            Column {
                if (twoColumns) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        BatteryCard(snapshot = state.battery, modifier = Modifier.weight(1f))
                        DeviceCard(facts = state.deviceFacts, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(16.dp))
                    ThermalCard(status = state.thermal, headroom = state.headroom)
                    Spacer(Modifier.height(16.dp))
                    MemoryCard(snapshot = state.memory)
                    Spacer(Modifier.height(16.dp))
                    CpuCard(snapshot = state.cpu)
                    Spacer(Modifier.height(16.dp))
                    FreshnessCard(lastUpdatedMillis = state.lastUpdatedMillis)
                } else {
                    BatteryCard(snapshot = state.battery)
                    Spacer(Modifier.height(16.dp))
                    ThermalCard(status = state.thermal, headroom = state.headroom)
                    Spacer(Modifier.height(16.dp))
                    MemoryCard(snapshot = state.memory)
                    Spacer(Modifier.height(16.dp))
                    CpuCard(snapshot = state.cpu)
                    Spacer(Modifier.height(16.dp))
                    DeviceCard(facts = state.deviceFacts)
                    Spacer(Modifier.height(16.dp))
                    FreshnessCard(lastUpdatedMillis = state.lastUpdatedMillis)
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        SectionCard(
            title = stringResource(R.string.monitor_session),
            icon = Icons.Outlined.Schedule,
        ) {
            MetricRow(
                label = stringResource(R.string.monitor_session),
                icon = Icons.Outlined.Schedule,
                value = uptimeText,
                supporting = stringResource(R.string.monitor_session_note),
            )
        }
        Spacer(Modifier.height(16.dp))

        SectionCard(
            title = stringResource(R.string.monitor_explanation_title),
            icon = Icons.Outlined.Info,
        ) {
            Text(
                text = stringResource(R.string.monitor_explanation_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Honest policy chips for the LOADED profile: what this app itself will do while the
 * Monitor screen is open. Null profile → neutral "loading policy" state (the policy is
 * not yet known, so nothing is claimed). The muted neutral chip variant is used for
 * "auto refresh off" purely as a calm visual, not as an error.
 */
@Composable
private fun PolicyChipsRow(profile: PerformanceProfile?) {
    if (profile == null) {
        Text(
            text = stringResource(R.string.monitor_policy_loading),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val policy = profile.monitoringPolicy
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        val intervalMillis = policy.autoRefreshIntervalMillis
        if (policy.inAppMonitoringEnabled && intervalMillis != null) {
            StatusChip(
                text = stringResource(
                    R.string.monitor_auto_refresh_every,
                    (intervalMillis / 1000L).toInt(),
                ),
                variant = StatusChipVariant.OK,
            )
        } else {
            StatusChip(
                text = stringResource(R.string.monitor_auto_refresh_off),
                variant = StatusChipVariant.UNSUPPORTED,
            )
        }
        if (policy.keepScreenOnWhileVisible) {
            StatusChip(
                text = stringResource(R.string.monitor_keep_screen_on_chip),
                variant = StatusChipVariant.OK,
            )
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

@Composable
private fun BatteryCard(snapshot: BatterySnapshot?, modifier: Modifier = Modifier) {
    SectionCard(
        title = stringResource(R.string.status_battery),
        icon = Icons.Outlined.BatteryStd,
        modifier = modifier,
    ) {
        if (snapshot == null) {
            RefreshingNote()
        } else {
            batteryRows(snapshot).forEach { row ->
                MapperMetricRow(row = row, icon = Icons.Outlined.BatteryStd)
            }
            Text(
                text = stringResource(R.string.monitor_battery_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ThermalCard(
    status: ThermalSnapshot?,
    headroom: ThermalHeadroomSnapshot?,
) {
    SectionCard(
        title = stringResource(R.string.status_thermal),
        icon = Icons.Outlined.DeviceThermostat,
    ) {
        if (status == null) {
            RefreshingNote()
        } else {
            // Status row (with the honest "Requires Android 10 or newer" muted chip on
            // old devices) + headroom row only when the platform reports it. The
            // headroom unit hint ("the platform's headroom abstraction, not a
            // temperature") is attached by MapperMetricRow.
            thermalRows(status = status, headroom = headroom).forEach { row ->
                MapperMetricRow(row = row, icon = Icons.Outlined.DeviceThermostat)
            }
            Text(
                text = stringResource(R.string.monitor_thermal_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MemoryCard(snapshot: MemorySnapshot?) {
    SectionCard(
        title = stringResource(R.string.status_memory),
        icon = Icons.Outlined.Memory,
    ) {
        if (snapshot == null) {
            RefreshingNote()
        } else {
            // Available / Total / derived "In use (calculated)" / "This app (heap)".
            // The strict Phase 2 distinction is kept: no usage % is derived from availMem.
            memoryRows(snapshot).forEach { row ->
                MapperMetricRow(row = row, icon = Icons.Outlined.Memory)
            }
            Text(
                text = stringResource(R.string.monitor_memory_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CpuCard(snapshot: CpuSnapshot?) {
    SectionCard(
        title = stringResource(R.string.status_cpu),
        icon = Icons.Outlined.DeveloperBoard,
    ) {
        val cores = snapshot?.coreCount
        if (cores != null) {
            MetricRow(
                label = stringResource(R.string.status_cpu),
                icon = Icons.Outlined.DeveloperBoard,
                value = stringResource(R.string.cpu_cores_value, cores),
                supporting = stringResource(R.string.monitor_cpu_note),
            )
        } else {
            val reason = snapshot?.availability?.let { availabilityReason(it) }
                ?: UnavailableReasons.NO_READING
            MetricRow(
                label = stringResource(R.string.status_cpu),
                icon = Icons.Outlined.DeveloperBoard,
                statusVariant = chipVariantForReason(reason),
                statusText = if (snapshot == null) {
                    stringResource(R.string.reason_no_reading)
                } else {
                    unavailableReasonLabel(reason)
                },
                supporting = stringResource(R.string.monitor_cpu_note),
            )
        }
        // CPU load is NOT sampled and will not be: no permission-free API exists.
        // The explanation card at the bottom states this instead of a fake value.
    }
}

@Composable
private fun DeviceCard(facts: DeviceFacts?, modifier: Modifier = Modifier) {
    SectionCard(
        title = stringResource(R.string.home_device_info),
        icon = Icons.Outlined.Smartphone,
        modifier = modifier,
    ) {
        if (facts == null) {
            RefreshingNote()
        } else {
            deviceRows(facts).forEach { row ->
                MapperMetricRow(row = row, icon = Icons.Outlined.Smartphone)
            }
        }
    }
}

/** Real "last updated" timestamp of the last successful one-shot refresh. */
@Composable
private fun FreshnessCard(lastUpdatedMillis: Long?) {
    SectionCard(
        title = stringResource(R.string.monitor_last_updated_title),
        icon = Icons.Outlined.Schedule,
    ) {
        val value = if (lastUpdatedMillis == null) {
            stringResource(R.string.monitor_never_refreshed)
        } else {
            remember(lastUpdatedMillis) {
                DateFormat.getDateTimeInstance().format(Date(lastUpdatedMillis))
            }
        }
        MetricRow(
            label = stringResource(R.string.monitor_last_updated),
            icon = Icons.Outlined.Schedule,
            value = value,
        )
    }
}

@Composable
private fun ErrorCard(reason: String, onRetry: () -> Unit) {
    SectionCard(
        title = stringResource(R.string.monitor_error_title),
        icon = Icons.Outlined.Warning,
    ) {
        Text(
            text = unavailableReasonLabel(reason),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        NgbOutlinedButton(onClick = onRetry) {
            Text(stringResource(R.string.action_retry))
        }
    }
}

@Composable
private fun RefreshingNote() {
    Text(
        text = stringResource(R.string.refreshing),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
