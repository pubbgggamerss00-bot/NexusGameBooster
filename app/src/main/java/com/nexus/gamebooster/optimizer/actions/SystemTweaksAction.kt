package com.nexus.gamebooster.optimizer.actions

import com.nexus.gamebooster.data.DeviceChangeRecord
import com.nexus.gamebooster.data.DeviceChangeStore
import com.nexus.gamebooster.data.DeviceChangeTypes
import com.nexus.gamebooster.monitoring.UnavailableReasons
import com.nexus.gamebooster.optimizer.BoostAction
import com.nexus.gamebooster.optimizer.BoostActionIds
import com.nexus.gamebooster.optimizer.BoostActionOutcome
import com.nexus.gamebooster.optimizer.BoostSession
import com.nexus.gamebooster.shizuku.ShizukuPrivilege

/** Global settings keys this action flips to 0. */
private val ANIMATION_SCALE_KEYS = listOf(
    "window_animation_scale",
    "transition_animation_scale",
    "animator_duration_scale",
)

/**
 * Aggressive auto-run: disable window / transition / animator scales for the session.
 * Reduces compositor work and perceived jank.
 *
 * Phase 10: BEFORE writing 0, the current values are read once and persisted in the
 * device-change ledger ([DeviceChangeStore]) — but only the FIRST recorded originals
 * are ever kept (the store ignores later writes for the same key), so a second boost
 * can never replace the user's true original values with the app's own 0.0.
 */
class SystemTweaksAction(
    private val shizuku: ShizukuPrivilege,
    private val changeLedger: DeviceChangeStore? = null,
) : BoostAction {

    override val id: String = BoostActionIds.SYSTEM_TWEAKS

    override suspend fun execute(session: BoostSession): BoostActionOutcome {
        if (!shizuku.isReady) {
            return BoostActionOutcome.Unsupported(UnavailableReasons.SHIZUKU_REQUIRED)
        }

        // Read-before-write: snapshot the current scales so a later restore can put
        // back the REAL pre-boost values (null = read failed → restore falls back to
        // the factory default and says so).
        if (changeLedger != null) {
            val record = DeviceChangeRecord(
                id = "anim_" + System.currentTimeMillis(),
                type = DeviceChangeTypes.ANIMATION_SCALES,
                packageName = null,
                windowScale = readScale("window_animation_scale"),
                transitionScale = readScale("transition_animation_scale"),
                animatorScale = readScale("animator_duration_scale"),
                createdAtMillis = System.currentTimeMillis(),
            )
            changeLedger.addDeviceChangeIfAbsent(record)
        }

        val result = shizuku.setAnimationScales(0f)
        return if (result.isSuccess) {
            BoostActionOutcome.Applied()
        } else {
            BoostActionOutcome.Failed(
                reasonKey = UnavailableReasons.API_ERROR,
                message = result.stderr.ifBlank { "animation scale tweak failed" },
            )
        }
    }

    /** Reads one scale value; null when the read fails (recorded as unknown original). */
    private suspend fun readScale(key: String): String? {
        val result = shizuku.getGlobalSetting(key)
        val value = result.stdout.trim()
        return value.takeIf { result.isSuccess && value.isNotEmpty() && value != "null" }
    }
}
