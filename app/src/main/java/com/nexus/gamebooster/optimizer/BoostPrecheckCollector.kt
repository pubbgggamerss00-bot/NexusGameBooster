package com.nexus.gamebooster.optimizer

import com.nexus.gamebooster.data.ActiveProfileStore
import com.nexus.gamebooster.games.GameRepository
import com.nexus.gamebooster.monitoring.MetricAvailability
import com.nexus.gamebooster.monitoring.MonitoringRepository
import com.nexus.gamebooster.monitoring.UnavailableReasons
import com.nexus.gamebooster.profiles.ProfileIds
import com.nexus.gamebooster.profiles.ProfileRepository
import kotlinx.coroutines.flow.first

/**
 * Collects the boost run's pre-checks from the EXISTING repositories (no new reads, no
 * duplicated device code):
 *
 *  - [MonitoringRepository.refresh()] — real one-shot battery (level + charging),
 *    memory, thermal status snapshot;
 *  - [ActiveProfileStore]/[ProfileRepository] — the normalized active profile id
 *    (never unavailable: normalization guarantees a preset id);
 *  - [GameRepository] — the boost's target game name: the request's game first, then
 *    the first library entry.
 *
 * HONESTY: an unavailable metric becomes a [BoostCheck] with a stable
 * [UnavailableReasons] key — NEVER a zero. The boost is never blocked solely because
 * optional pre-check info is unavailable; actions decide from what IS available.
 *
 * Only interfaces are injected, so the construction logic is directly unit-testable
 * with fakes (no Android, no DataStore).
 */
class BoostPrecheckCollector(
    private val monitoringRepository: MonitoringRepository,
    private val profileRepository: ProfileRepository,
    private val activeProfileStore: ActiveProfileStore,
    private val gameRepository: GameRepository,
) {

    /** Runs the real reads and builds the pre-check list, in stable display order. */
    suspend fun collect(request: BoostRequest): List<BoostCheck> {
        val snapshot = monitoringRepository.refresh()

        val battery = snapshot.battery
        val level = battery.levelPercent
        val batteryLevelCheck = if (level != null) {
            BoostCheck(
                labelKey = BoostCheckKeys.BATTERY_LEVEL,
                valueText = level.toString(),
                capturedAtMillis = System.currentTimeMillis(),
                levelPercent = level,
            )
        } else {
            BoostCheck(
                labelKey = BoostCheckKeys.BATTERY_LEVEL,
                availabilityReason = unavailableReason(battery.availability),
                capturedAtMillis = System.currentTimeMillis(),
            )
        }

        val charging = battery.charging
        val chargingCheck = if (charging != null) {
            BoostCheck(
                labelKey = BoostCheckKeys.BATTERY_CHARGING,
                valueText = if (charging) BoostCheckValues.CHARGING_YES else BoostCheckValues.CHARGING_NO,
                capturedAtMillis = System.currentTimeMillis(),
                charging = charging,
            )
        } else {
            BoostCheck(
                labelKey = BoostCheckKeys.BATTERY_CHARGING,
                availabilityReason = unavailableReason(battery.availability),
                capturedAtMillis = System.currentTimeMillis(),
            )
        }

        val availableMb = snapshot.memory.availableMb
        val memoryCheck = if (availableMb != null) {
            BoostCheck(
                labelKey = BoostCheckKeys.MEMORY_AVAILABLE,
                // Raw number with the unit attached by the UI (localized "MB" label).
                valueText = availableMb.toString(),
                capturedAtMillis = System.currentTimeMillis(),
            )
        } else {
            BoostCheck(
                labelKey = BoostCheckKeys.MEMORY_AVAILABLE,
                availabilityReason = unavailableReason(snapshot.memory.availability),
                capturedAtMillis = System.currentTimeMillis(),
            )
        }

        val statusLabel = snapshot.thermal.statusLabel
        val thermalCheck = if (statusLabel != null) {
            BoostCheck(
                labelKey = BoostCheckKeys.THERMAL_STATUS,
                valueText = statusLabel, // machine key; localized by the UI
                capturedAtMillis = System.currentTimeMillis(),
            )
        } else {
            BoostCheck(
                labelKey = BoostCheckKeys.THERMAL_STATUS,
                availabilityReason = unavailableReason(snapshot.thermal.availability),
                capturedAtMillis = System.currentTimeMillis(),
            )
        }

        // Normalization guarantees a preset id — the profile check is never unavailable.
        val profileId = ProfileIds.normalize(activeProfileStore.activeProfileId.first())
        val profileCheck = BoostCheck(
            labelKey = BoostCheckKeys.PROFILE,
            valueText = profileId,
            capturedAtMillis = System.currentTimeMillis(),
        )

        val gameName = resolveGameName(request)
        val gameCheck = if (gameName != null) {
            BoostCheck(
                labelKey = BoostCheckKeys.GAME,
                valueText = gameName,
                capturedAtMillis = System.currentTimeMillis(),
            )
        } else {
            BoostCheck(
                labelKey = BoostCheckKeys.GAME,
                availabilityReason = UnavailableReasons.NO_GAME_SELECTED,
                capturedAtMillis = System.currentTimeMillis(),
            )
        }

        return listOf(
            batteryLevelCheck,
            chargingCheck,
            memoryCheck,
            thermalCheck,
            profileCheck,
            gameCheck,
        )
    }

    /**
     * Request's game first ([BoostRequest.gameName], or [BoostRequest.gameId] looked up
     * in the live library), then the first library entry as the fallback; null when the
     * library is empty → honest `no_game_selected` check.
     */
    private suspend fun resolveGameName(request: BoostRequest): String? {
        val games = gameRepository.games.first()
        request.gameName?.let { return it }
        request.gameId?.let { id -> games.firstOrNull { it.id == id }?.name?.let { return it } }
        return games.firstOrNull()?.name
    }

    private fun unavailableReason(availability: MetricAvailability): String =
        (availability as? MetricAvailability.Unavailable)?.reason ?: UnavailableReasons.NO_READING
}
