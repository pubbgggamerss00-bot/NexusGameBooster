package com.nexus.gamebooster.optimizer.actions

import com.nexus.gamebooster.monitoring.UnavailableReasons
import com.nexus.gamebooster.optimizer.BoostAction
import com.nexus.gamebooster.optimizer.BoostActionIds
import com.nexus.gamebooster.optimizer.BoostActionOutcome
import com.nexus.gamebooster.optimizer.BoostSession
import com.nexus.gamebooster.shizuku.ShizukuPrivilege

/**
 * Aggressive auto-run: ask the system to reclaim caches (pm trim-caches).
 * Complements ForceStopBackgroundAction; does not kill processes itself.
 */
class FreeMemoryAction(
    private val shizuku: ShizukuPrivilege,
) : BoostAction {

    override val id: String = BoostActionIds.FREE_MEMORY

    override suspend fun execute(session: BoostSession): BoostActionOutcome {
        if (!shizuku.isReady) {
            return BoostActionOutcome.Unsupported(UnavailableReasons.SHIZUKU_REQUIRED)
        }

        // 0 = let the system free as much reclaimable cache as it is willing to
        val result = shizuku.trimCaches(0L)
        return if (result.isSuccess) {
            BoostActionOutcome.Applied()
        } else {
            BoostActionOutcome.Failed(
                reasonKey = UnavailableReasons.API_ERROR,
                message = result.stderr.ifBlank { "trim-caches failed (code ${result.exitCode})" },
            )
        }
    }
}
