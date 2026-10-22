package com.nexus.gamebooster.profiles

/**
 * Stable profile ids persisted in DataStore (`active_profile`).
 *
 * Phase 4 removed the legacy Phase 1 "Gaming" preset (`"gaming"`). The id is kept ONLY
 * as [LEGACY_GAMING] so values persisted by older builds can be migrated: every read
 * path runs raw values through [normalize], which maps anything unknown — null,
 * `"gaming"`, or any other string — to [BALANCED].
 */
object ProfileIds {
    const val BALANCED = "balanced"
    const val PERFORMANCE = "performance"
    const val BATTERY_SAVER = "battery_saver"

    /** Legacy Phase 1 id. Never a preset in Phase 4+; read paths normalize it to [BALANCED]. */
    const val LEGACY_GAMING = "gaming"

    private val PRESET_IDS = setOf(BALANCED, PERFORMANCE, BATTERY_SAVER)

    /** True only for the three shipped preset ids; false for null, legacy or unknown values. */
    fun isValid(id: String?): Boolean = id != null && id in PRESET_IDS

    /**
     * Normalizes a persisted raw value to a preset id: the three preset ids pass through
     * unchanged; everything else (null, unknown, legacy `"gaming"`) becomes [BALANCED].
     */
    fun normalize(raw: String?): String = if (raw != null && raw in PRESET_IDS) raw else BALANCED
}

/**
 * The three-way honesty taxonomy for every row shown under a profile.
 *
 *  - [APPLIED_BY_APP]: the effect happens directly inside NexusGameBooster the moment
 *    the profile is active (e.g. this app's own refresh cadence). This app CAN do these.
 *  - [REQUIRES_USER_ACTION]: a recommendation the app CANNOT perform itself; the user
 *    must change something on the device (vendor game mode, per-game graphics settings).
 *    A recommendation must never be labeled as applied.
 *  - [NOT_SUPPORTED]: a system-wide change normal Android apps are not allowed to make
 *    (CPU/GPU frequencies, process priorities, thermal limits). The UI states these
 *    openly instead of promising them.
 */
enum class EffectKind { APPLIED_BY_APP, REQUIRES_USER_ACTION, NOT_SUPPORTED }

/** One honest effect row of a profile: the kind and its localized text resource. */
data class ProfileEffect(
    val kind: EffectKind,
    val textRes: Int,
)

/**
 * What a profile changes about NexusGameBooster's OWN monitoring behavior.
 *
 * HONESTY CONTRACT: this policy changes ONLY this app's in-app behavior — how often the
 * Monitor screen refreshes, whether the live battery stream is subscribed, whether the
 * Monitor window keeps the screen on. It never changes anything system-wide: no CPU/GPU
 * frequencies, no process priorities, no thermal limits, no other apps.
 *
 *  - [autoRefreshIntervalMillis]: cadence of the in-app auto refresh while the Monitor
 *    screen is lifecycle-active; null means NO auto refresh (manual refresh only).
 *  - [liveBatteryEvents]: whether the live system battery broadcast stream is collected.
 *  - [keepScreenOnWhileVisible]: whether the Monitor window keeps the screen on while
 *    that screen is open (a window flag, not a wake lock).
 *  - [inAppMonitoringEnabled]: master switch — false means no auto refresh and no live
 *    battery stream at all; the manual one-shot refresh always stays available.
 */
data class MonitoringPolicy(
    val autoRefreshIntervalMillis: Long?,
    val liveBatteryEvents: Boolean,
    val keepScreenOnWhileVisible: Boolean,
    val inAppMonitoringEnabled: Boolean,
)

/**
 * One of the THREE fixed performance profiles (Balanced / Performance / Battery Saver).
 * Immutable and pure Kotlin — zero Android or Compose imports — so the model is directly
 * unit-testable on the JVM. Icons are deliberately NOT part of the model; the UI layer
 * maps profile ids to icons (`ui/components/ProfileIcons.kt`).
 *
 * [appliedEffects] describe exactly what this app itself does under this profile,
 * [recommendations] list actions only the user can take, [notSupportedEffects] name the
 * system-wide changes this app will never claim to make.
 */
data class PerformanceProfile(
    val id: String,
    val titleRes: Int,
    val descriptionRes: Int,
    val monitoringPolicy: MonitoringPolicy,
    val appliedEffects: List<ProfileEffect>,
    val recommendations: List<ProfileEffect>,
    val notSupportedEffects: List<ProfileEffect>,
)
