package com.nexus.gamebooster.monitoring

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

/**
 * THE single source of truth for every real, permission-free system read in this app.
 *
 * Both [SystemMonitoringRepository] (Monitor screen) and
 * [AndroidDeviceInfoRepository] (Home device facts) call into this object, so two
 * screens can never disagree about the same metric.
 *
 * Reads ONLY genuinely-available, standard, permission-free Android APIs:
 *  - Battery: ACTION_BATTERY_CHANGED sticky intent (level/scale %, status, plugged type),
 *             plus [batteryUpdates] for live updates via a runtime-registered receiver
 *  - Thermal: PowerManager.getCurrentThermalStatus() (API 29+; guarded for older devices)
 *             and PowerManager.getThermalHeadroom(0) (API 30+; guarded for older devices)
 *  - Memory:  ActivityManager.MemoryInfo (totalMem / availMem) + this app's Runtime heap
 *  - CPU:     Runtime.availableProcessors() (core count only)
 *
 * CPU load, GPU stats and FPS are intentionally NOT sampled — Android exposes no
 * permission-free API for them, and this app does not request privileged or
 * vendor-specific APIs. No root, no hidden APIs, no wake locks, no polling loops.
 */
internal object SystemReads {

    fun readBattery(appContext: Context): BatterySnapshot = try {
        parseBatteryIntent(
            appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)),
        )
    } catch (_: Exception) {
        BatterySnapshot(MetricAvailability.Unavailable(UnavailableReasons.API_ERROR))
    }

    /**
     * Single source of truth for turning an ACTION_BATTERY_CHANGED intent into a
     * [BatterySnapshot]. Used by BOTH the one-shot [readBattery] (sticky intent) and
     * the live [batteryUpdates] flow (broadcast deliveries) so the two paths can
     * never produce different values from the same input.
     */
    private fun parseBatteryIntent(intent: Intent?): BatterySnapshot {
        if (intent == null) {
            return BatterySnapshot(MetricAvailability.Unavailable(UnavailableReasons.NO_READING))
        }
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val percent = if (level >= 0 && scale > 0) (level * 100) / scale else null
        val charging = if (status != -1) {
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        } else {
            null
        }
        val pluggedLabel = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> PluggedLabels.AC
            BatteryManager.BATTERY_PLUGGED_USB -> PluggedLabels.USB
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> PluggedLabels.WIRELESS
            else -> null // 0 = on battery, -1 = not reported → null, not a guess.
        }
        return BatterySnapshot(
            availability = if (percent != null) {
                MetricAvailability.Available
            } else {
                MetricAvailability.Unavailable(UnavailableReasons.NO_READING)
            },
            levelPercent = percent,
            charging = charging,
            pluggedLabel = pluggedLabel,
        )
    }

    /**
     * Live battery updates. Cold Flow over a context-registered receiver for
     * ACTION_BATTERY_CHANGED (a protected system broadcast — no permission needed).
     *
     * Efficiency contract (honest, no hidden costs):
     *  - The receiver is registered at RUNTIME (RECEIVER_NOT_EXPORTED — the system can
     *    still deliver its own protected broadcasts; third-party apps cannot) and lives
     *    ONLY while this Flow is being collected: `awaitClose` unregisters it when the
     *    collecting coroutine is cancelled. Nothing is declared in the manifest.
     *  - No polling loops, no handlers, no wake locks — work happens only when the
     *    system actually broadcasts a battery change.
     *  - CONFLATED buffer: collectors observe only the latest snapshot, and an emission
     *    is never lost just because a collector was briefly suspended.
     */
    fun batteryUpdates(appContext: Context): Flow<BatterySnapshot> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(parseBatteryIntent(intent))
            }
        }
        try {
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        } catch (_: Exception) {
            // Registration failed (rare). Fall through to the one-shot sticky read below
            // so the collector still receives one real value; the unregister in
            // awaitClose is a harmless no-op (wrapped in runCatching) in that case.
        }
        // The sticky intent holds the CURRENT battery state — read it immediately so the
        // collector gets a value without waiting for the next actual battery change.
        // Duplicates with the receiver delivery are harmless (conflated buffer).
        try {
            trySend(
                parseBatteryIntent(
                    appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)),
                ),
            )
        } catch (_: Exception) {
            trySend(BatterySnapshot(MetricAvailability.Unavailable(UnavailableReasons.API_ERROR)))
        }
        awaitClose {
            runCatching { appContext.unregisterReceiver(receiver) }
        }
    }.buffer(Channel.CONFLATED)

    fun readThermal(appContext: Context): ThermalSnapshot = try {
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        when {
            powerManager == null ->
                ThermalSnapshot(MetricAvailability.Unavailable(UnavailableReasons.API_ERROR))

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                val status = powerManager.currentThermalStatus
                ThermalSnapshot(
                    availability = MetricAvailability.Available,
                    statusLabel = thermalLabel(status),
                    severity = status,
                )
            }

            else ->
                ThermalSnapshot(MetricAvailability.Unavailable(UnavailableReasons.REQUIRES_ANDROID_10))
        }
    } catch (_: Exception) {
        ThermalSnapshot(MetricAvailability.Unavailable(UnavailableReasons.API_ERROR))
    }

    /**
     * Real thermal headroom via `PowerManager.getThermalHeadroom(0)` — the CURRENT
     * headroom only (window 0; this app never requests a forecast).
     *
     * Availability semantics (all honest, nothing guessed):
     *  - PowerManager missing          → API_ERROR (temporary/environmental failure).
     *  - OS older than Android 11      → REQUIRES_ANDROID_11 (permanent on this device,
     *    deliberately distinguished from a temporary failure so the UI can say
     *    "unsupported" instead of "failed").
     *  - NaN or <= 0.0 result          → HEADROOM_NOT_REPORTED. Many devices return 0.0
     *    when the headroom abstraction is unsupported; that is "the device did not
     *    report it", NOT a genuine zero-second reading, so it is never displayed as 0.0.
     */
    fun readThermalHeadroom(appContext: Context): ThermalHeadroomSnapshot = try {
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        when {
            powerManager == null ->
                ThermalHeadroomSnapshot(MetricAvailability.Unavailable(UnavailableReasons.API_ERROR))

            Build.VERSION.SDK_INT < Build.VERSION_CODES.R ->
                ThermalHeadroomSnapshot(
                    MetricAvailability.Unavailable(UnavailableReasons.REQUIRES_ANDROID_11),
                )

            else -> {
                // API 30+ only (guarded above): current headroom, no forecasting.
                val headroom = powerManager.getThermalHeadroom(0)
                if (headroom.isNaN() || headroom <= 0f) {
                    ThermalHeadroomSnapshot(
                        MetricAvailability.Unavailable(UnavailableReasons.HEADROOM_NOT_REPORTED),
                    )
                } else {
                    ThermalHeadroomSnapshot(
                        availability = MetricAvailability.Available,
                        headroomSeconds = headroom,
                        forecastSeconds = 0,
                    )
                }
            }
        }
    } catch (_: Exception) {
        ThermalHeadroomSnapshot(MetricAvailability.Unavailable(UnavailableReasons.API_ERROR))
    }

    private fun thermalLabel(status: Int): String = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> ThermalLabels.NONE
        PowerManager.THERMAL_STATUS_LIGHT -> ThermalLabels.LIGHT
        PowerManager.THERMAL_STATUS_MODERATE -> ThermalLabels.MODERATE
        PowerManager.THERMAL_STATUS_SEVERE -> ThermalLabels.SEVERE
        PowerManager.THERMAL_STATUS_CRITICAL -> ThermalLabels.CRITICAL
        PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalLabels.EMERGENCY
        PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalLabels.SHUTDOWN
        else -> ThermalLabels.UNKNOWN
    }

    fun readMemory(appContext: Context): MemorySnapshot = try {
        val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (activityManager == null) {
            MemorySnapshot(MetricAvailability.Unavailable(UnavailableReasons.API_ERROR))
        } else {
            val info = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(info)
            val runtime = Runtime.getRuntime()
            MemorySnapshot(
                availability = MetricAvailability.Available,
                totalMb = info.totalMem / BYTES_PER_MB,
                availableMb = info.availMem / BYTES_PER_MB,
                availableBytes = info.availMem,
                totalBytes = info.totalMem,
                lowMemory = info.lowMemory,
                // "This app" heap (JVM only) — kept separate from the system numbers above.
                appHeapUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MB,
                appHeapAllocatedMb = runtime.totalMemory() / BYTES_PER_MB,
            )
        }
    } catch (_: Exception) {
        MemorySnapshot(MetricAvailability.Unavailable(UnavailableReasons.API_ERROR))
    }

    fun readCpu(): CpuSnapshot = try {
        CpuSnapshot(
            availability = MetricAvailability.Available,
            coreCount = Runtime.getRuntime().availableProcessors(),
        )
    } catch (_: Exception) {
        CpuSnapshot(MetricAvailability.Unavailable(UnavailableReasons.API_ERROR))
    }

    private const val BYTES_PER_MB: Long = 1024L * 1024L
}
