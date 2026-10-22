package com.nexus.gamebooster.monitoring

/**
 * Whether a metric could genuinely be read from the device.
 *
 * Invariant: a snapshot with [Available] always carries real values. Whenever a value
 * would be null, availability is [Unavailable] with a stable machine-readable reason
 * (see [UnavailableReasons]).
 */
sealed interface MetricAvailability {
    data object Available : MetricAvailability
    data class Unavailable(val reason: String) : MetricAvailability
}

/**
 * Stable reason keys for [MetricAvailability.Unavailable]. Kept as strings (not an enum)
 * so later phases can add reasons without breaking this model; the UI maps known keys
 * to localized text.
 */
object UnavailableReasons {
    const val PHASE_3 = "phase_3"
    const val API_ERROR = "api_error"
    const val NO_READING = "no_reading"
    const val REQUIRES_ANDROID_10 = "requires_android_10"
    const val REQUIRES_ANDROID_11 = "requires_android_11"
    const val HEADROOM_NOT_REPORTED = "headroom_not_reported"
    const val SCAN_FAILED = "scan_failed"

    /**
     * Phase 4: persisting the active-profile choice failed (DataStore I/O error).
     * The previous selection stays active; the UI surfaces a retry affordance.
     */
    const val PROFILE_SAVE_FAILED = "profile_save_failed"

    /*
     * Phase 5 (Safe Boost Engine) reason keys. They cover both metric availability
     * AND typed boost-action outcomes ([Skipped] / [Unsupported] / [Failed] carry one
     * of these stable keys; the UI maps each to localized text in `core/UiMessage`).
     */

    /** A boost action had nothing to do because its target is already in the wanted state. */
    const val ALREADY_APPLIED = "already_applied"

    /** A boost action was deliberately skipped because it would not help right now. */
    const val NOT_NEEDED = "not_needed"

    /** The session-monitoring action skipped: the battery read as healthy (above threshold). */
    const val BATTERY_HEALTHY = "battery_healthy"

    /** The session-monitoring action skipped: the battery state could not be read. */
    const val BATTERY_UNAVAILABLE = "battery_unavailable"

    /** The session-monitoring action skipped: the active profile is already Battery Saver. */
    const val ALREADY_EFFICIENT = "already_efficient"

    /** The game-launch preparation skipped: no game with a package is selected in the library. */
    const val NO_GAME_SELECTED = "no_game_selected"

    /** The game-launch preparation found no package under the requested game id. */
    const val GAME_NOT_FOUND = "game_not_found"

    /** The launchable intent for the game could not be resolved (nothing to launch/open). */
    const val NO_LAUNCH_INTENT = "no_launch_intent"

    /** The target settings screen does not exist on this device (ActivityNotFound). */
    const val NO_SETTINGS_SCREEN = "no_settings_screen"
}

/** Stable keys for thermal status labels, localized by the UI layer. */
object ThermalLabels {
    const val NONE = "none"
    const val LIGHT = "light"
    const val MODERATE = "moderate"
    const val SEVERE = "severe"
    const val CRITICAL = "critical"
    const val EMERGENCY = "emergency"
    const val SHUTDOWN = "shutdown"
    const val UNKNOWN = "unknown"
}

/**
 * Stable keys for the battery charging state, localized by the UI layer.
 * [NOT_REPORTED] means the platform broadcast did not carry a usable status —
 * it is an honest "not reported", never a guess.
 */
object ChargingLabels {
    const val CHARGING = "charging"
    const val DISCHARGING = "discharging"
    const val NOT_REPORTED = "not_reported"
}

/**
 * Stable keys for the power source the battery is plugged into, localized by the UI
 * layer. Null on the snapshot means "not reported by the platform".
 */
object PluggedLabels {
    const val AC = "ac"
    const val USB = "usb"
    const val WIRELESS = "wireless"
}

/** Where a metric came from. Standard system APIs only. */
enum class MetricSource { SYSTEM_API }

/**
 * Real battery data from the ACTION_BATTERY_CHANGED sticky intent. No permission needed.
 * [pluggedLabel] is a stable [PluggedLabels] key (or null when the platform does not
 * report a power source).
 */
data class BatterySnapshot(
    val availability: MetricAvailability,
    val levelPercent: Int? = null,
    val charging: Boolean? = null,
    val pluggedLabel: String? = null,
    val source: MetricSource = MetricSource.SYSTEM_API,
)

/**
 * Real thermal status from PowerManager.getCurrentThermalStatus() (API 29+).
 * [statusLabel] is a stable [ThermalLabels] key; [severity] is the raw platform int.
 */
data class ThermalSnapshot(
    val availability: MetricAvailability,
    val statusLabel: String? = null,
    val severity: Int? = null,
)

/**
 * Real thermal headroom from `PowerManager.getThermalHeadroom(0)` (API 30+).
 *
 * IMPORTANT — what this value is: headroom is the PowerManager ABSTRACTION for
 * "how much sustained work the device can still take before throttling". It is a
 * platform-defined quantity, NOT a temperature and never a °C value.
 *
 * [headroomSeconds] is null whenever the device/OS did not report it (many devices
 * return 0.0 or NaN when the abstraction is unsupported — the app treats that as
 * "not reported", never as a real 0.0 reading, and never guesses a substitute).
 * [forecastSeconds] is the forecast window requested from the platform; this app
 * only ever requests the current headroom (0 — no forecasting).
 */
data class ThermalHeadroomSnapshot(
    val availability: MetricAvailability,
    val headroomSeconds: Float? = null,
    val forecastSeconds: Int = 0,
)

/**
 * Real memory numbers from ActivityManager.MemoryInfo plus this app's JVM heap.
 * The bytes fields are the raw system values; the UI must keep "Available" and
 * "Total" clearly labeled and never derive a fake "usage %".
 */
data class MemorySnapshot(
    val availability: MetricAvailability,
    val totalMb: Long? = null,
    val availableMb: Long? = null,
    val availableBytes: Long? = null,
    val totalBytes: Long? = null,
    val lowMemory: Boolean? = null,
    /** This process's used JVM heap — clearly labeled "This app" in the UI, never system memory. */
    val appHeapUsedMb: Long? = null,
    /** This process's allocated JVM heap. */
    val appHeapAllocatedMb: Long? = null,
)

/**
 * CPU snapshot. [coreCount] is genuinely read from the runtime.
 * NOTE: CPU load is intentionally NOT sampled, permanently — Android exposes no
 * permission-free API for it. The UI explains this honestly instead of showing a
 * planned date or an invented value.
 */
data class CpuSnapshot(
    val availability: MetricAvailability,
    val coreCount: Int? = null,
)
