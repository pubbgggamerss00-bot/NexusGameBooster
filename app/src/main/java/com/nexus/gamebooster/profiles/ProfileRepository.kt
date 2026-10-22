package com.nexus.gamebooster.profiles

import com.nexus.gamebooster.R

/**
 * Read-side catalog of performance profiles.
 *
 * The presets are FIXED (there are exactly three). Selecting a profile persists a
 * preference via the data layer (`ActiveProfileStore` / DataStore key `active_profile`)
 * and changes ONLY NexusGameBooster's own monitoring behavior as described by each
 * profile's [PerformanceProfile.monitoringPolicy] — never anything system-wide.
 *
 * Kept as an interface so ViewModels and tests can run against the static
 * implementation or fakes without DataStore or Android types.
 */
interface ProfileRepository {

    /** The three fixed presets, in stable display order (Balanced, Performance, Battery Saver). */
    val profiles: List<PerformanceProfile>

    /** The default profile (Balanced) — used whenever no valid profile is persisted. */
    fun defaultProfile(): PerformanceProfile

    /** Exact lookup by preset id; the legacy "gaming" id has NO preset, so it returns null. */
    fun findById(id: String): PerformanceProfile?

    /**
     * Lookup that never fails: any raw persisted value (null, unknown, or the legacy
     * "gaming" id) falls back to [defaultProfile] via [ProfileIds.normalize].
     */
    fun findByIdOrFallback(id: String?): PerformanceProfile
}

/**
 * Static implementation of [ProfileRepository] — a fixed catalog, no I/O, no state.
 *
 * Policy values (Phase 4 design, locked):
 *  - BALANCED: in-app auto refresh every 15 s while Monitor is open, live battery on,
 *    no keep-screen-on.
 *  - PERFORMANCE: faster 5 s in-app auto refresh, live battery on, Monitor keeps this
 *    app's screen on (window flag).
 *  - BATTERY_SAVER: all in-app monitoring automation off (no auto refresh, no live
 *    battery stream); manual refresh always works on every profile.
 */
class StaticProfileRepository : ProfileRepository {

    private val notSupportedEffects: List<ProfileEffect> = listOf(
        ProfileEffect(EffectKind.NOT_SUPPORTED, R.string.ns_cpu_freq),
        ProfileEffect(EffectKind.NOT_SUPPORTED, R.string.ns_process_priority),
        ProfileEffect(EffectKind.NOT_SUPPORTED, R.string.ns_thermal_limits),
        ProfileEffect(EffectKind.NOT_SUPPORTED, R.string.ns_ram_boost),
    )

    override val profiles: List<PerformanceProfile> = listOf(
        PerformanceProfile(
            id = ProfileIds.BALANCED,
            titleRes = R.string.profile_balanced_title,
            descriptionRes = R.string.profile_balanced_description,
            monitoringPolicy = MonitoringPolicy(
                autoRefreshIntervalMillis = 15_000L,
                liveBatteryEvents = true,
                keepScreenOnWhileVisible = false,
                inAppMonitoringEnabled = true,
            ),
            appliedEffects = listOf(
                ProfileEffect(EffectKind.APPLIED_BY_APP, R.string.effect_balanced_refresh),
                ProfileEffect(EffectKind.APPLIED_BY_APP, R.string.effect_balanced_live),
            ),
            recommendations = listOf(
                ProfileEffect(EffectKind.REQUIRES_USER_ACTION, R.string.rec_game_mode),
                ProfileEffect(EffectKind.REQUIRES_USER_ACTION, R.string.rec_graphics),
                ProfileEffect(EffectKind.REQUIRES_USER_ACTION, R.string.rec_close_apps),
            ),
            notSupportedEffects = notSupportedEffects,
        ),
        PerformanceProfile(
            id = ProfileIds.PERFORMANCE,
            titleRes = R.string.profile_performance_title,
            descriptionRes = R.string.profile_performance_description,
            monitoringPolicy = MonitoringPolicy(
                autoRefreshIntervalMillis = 5_000L,
                liveBatteryEvents = true,
                keepScreenOnWhileVisible = true,
                inAppMonitoringEnabled = true,
            ),
            appliedEffects = listOf(
                ProfileEffect(EffectKind.APPLIED_BY_APP, R.string.effect_perf_refresh),
                ProfileEffect(EffectKind.APPLIED_BY_APP, R.string.effect_perf_live),
                ProfileEffect(EffectKind.APPLIED_BY_APP, R.string.effect_perf_screen_on),
            ),
            recommendations = listOf(
                ProfileEffect(EffectKind.REQUIRES_USER_ACTION, R.string.rec_game_mode),
                ProfileEffect(EffectKind.REQUIRES_USER_ACTION, R.string.rec_battery_optimization),
                ProfileEffect(EffectKind.REQUIRES_USER_ACTION, R.string.rec_graphics),
            ),
            notSupportedEffects = notSupportedEffects,
        ),
        PerformanceProfile(
            id = ProfileIds.BATTERY_SAVER,
            titleRes = R.string.profile_battery_title,
            descriptionRes = R.string.profile_battery_description,
            monitoringPolicy = MonitoringPolicy(
                autoRefreshIntervalMillis = null,
                liveBatteryEvents = false,
                keepScreenOnWhileVisible = false,
                inAppMonitoringEnabled = false,
            ),
            appliedEffects = listOf(
                ProfileEffect(EffectKind.APPLIED_BY_APP, R.string.effect_battery_manual),
                ProfileEffect(EffectKind.APPLIED_BY_APP, R.string.effect_battery_no_live),
            ),
            recommendations = listOf(
                ProfileEffect(EffectKind.REQUIRES_USER_ACTION, R.string.rec_system_saver),
                ProfileEffect(EffectKind.REQUIRES_USER_ACTION, R.string.rec_close_apps),
            ),
            notSupportedEffects = notSupportedEffects,
        ),
    )

    override fun defaultProfile(): PerformanceProfile = profiles.first()

    override fun findById(id: String): PerformanceProfile? = profiles.firstOrNull { it.id == id }

    override fun findByIdOrFallback(id: String?): PerformanceProfile =
        findById(ProfileIds.normalize(id)) ?: defaultProfile()
}
