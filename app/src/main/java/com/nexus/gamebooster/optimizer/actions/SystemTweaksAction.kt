package com.nexus.gamebooster.optimizer.actions

import com.nexus.gamebooster.monitoring.UnavailableReasons
import com.nexus.gamebooster.optimizer.BoostAction
import com.nexus.gamebooster.optimizer.BoostActionIds
import com.nexus.gamebooster.optimizer.BoostActionOutcome
import com.nexus.gamebooster.optimizer.BoostSession
import com.nexus.gamebooster.shizuku.ShizukuPrivilege

/**
 * Aggressive auto-run: disable window / transition / animator scales for the session.
 * Reduces compositor work and perceived jank. Values are not restored automatically
 * in this phase (user can re-enable from Developer options or a future "restore" action).
 */
class SystemTweaksAction(
    private val shizuku: ShizukuPrivilege,
) : BoostAction {

    override val id: String = BoostActionIds.SYSTEM_TWEAKS

    override suspend fun execute(session: BoostSession): BoostActionOutcome {
        if (!shizuku.isReady) {
            return BoostActionOutcome.Unsupported(UnavailableReasons.SHIZUKU_REQUIRED)
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
}
