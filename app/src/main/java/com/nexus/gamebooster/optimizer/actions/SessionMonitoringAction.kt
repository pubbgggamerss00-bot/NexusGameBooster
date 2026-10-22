package com.nexus.gamebooster.optimizer.actions

import com.nexus.gamebooster.core.SessionMonitorController
import com.nexus.gamebooster.monitoring.UnavailableReasons
import com.nexus.gamebooster.optimizer.BoostAction
import com.nexus.gamebooster.optimizer.BoostActionIds
import com.nexus.gamebooster.optimizer.BoostActionOutcome
import com.nexus.gamebooster.optimizer.BoostCheck
import com.nexus.gamebooster.optimizer.BoostCheckKeys
import com.nexus.gamebooster.optimizer.BoostSession
import com.nexus.gamebooster.profiles.ProfileIds

/**
 * Auto-run boost action #2: decides — ONLY from the session's REAL battery pre-checks —
 * whether this app should switch its own monitoring to the power-efficient session mode
 * ([SessionMonitorController.setEfficient]).
 *
 * The decision never parses display strings: it reads the typed [BoostCheck.levelPercent]
 * / [BoostCheck.charging] payloads the pre-check collector attached to the battery checks.
 *
 * Decision order (locked design):
 *  1. battery level or charging state unreadable → `Skipped(battery_unavailable)`
 *  2. currently charging → `Skipped(not_needed)` (no battery to save)
 *  3. active profile is Battery Saver → `Skipped(already_efficient)` (the user already
 *     chose the most conservative mode)
 *  4. battery level above [lowBatteryThresholdPercent] → `Skipped(battery_healthy)`
 *  5. otherwise → switch to the efficient session mode → `Applied`
 *
 * SCOPE (honesty): the "efficient mode" changes ONLY this app's own monitoring for the
 * current app session — no system setting, no other app, no battery saver toggle.
 */
class SessionMonitoringAction(
    private val sessionMonitor: SessionMonitorController,
    private val lowBatteryThresholdPercent: Int = DEFAULT_LOW_BATTERY_THRESHOLD,
) : BoostAction {

    override val id: String = BoostActionIds.SESSION_MONITORING

    override suspend fun execute(session: BoostSession): BoostActionOutcome {
        val levelCheck = session.checks.firstOrNull { it.labelKey == BoostCheckKeys.BATTERY_LEVEL }
        val chargingCheck = session.checks.firstOrNull { it.labelKey == BoostCheckKeys.BATTERY_CHARGING }

        // 1. Missing/unreadable battery state — honestly skipped, never guessed.
        val level = levelCheck?.levelPercent
        if (levelCheck == null || levelCheck.availabilityReason != null || level == null) {
            return BoostActionOutcome.Skipped(UnavailableReasons.BATTERY_UNAVAILABLE)
        }
        val charging = chargingCheck?.charging
        if (chargingCheck == null || chargingCheck.availabilityReason != null || charging == null) {
            return BoostActionOutcome.Skipped(UnavailableReasons.BATTERY_UNAVAILABLE)
        }

        // 2. Plugged in — saving battery would not help right now.
        if (charging) {
            return BoostActionOutcome.Skipped(UnavailableReasons.NOT_NEEDED)
        }

        // 3. Battery Saver profile: monitoring is already at its most conservative.
        if (session.profileId == ProfileIds.BATTERY_SAVER) {
            return BoostActionOutcome.Skipped(UnavailableReasons.ALREADY_EFFICIENT)
        }

        // 4. Battery healthy — the efficient session mode is unnecessary.
        if (level > lowBatteryThresholdPercent) {
            return BoostActionOutcome.Skipped(UnavailableReasons.BATTERY_HEALTHY)
        }

        // 5. Real low battery, on battery power: conserve power in THIS app's monitoring.
        sessionMonitor.setEfficient()
        return BoostActionOutcome.Applied()
    }

    companion object {
        const val DEFAULT_LOW_BATTERY_THRESHOLD = 20
    }
}
