package com.nexus.gamebooster.optimizer.actions

import com.nexus.gamebooster.monitoring.UnavailableReasons
import com.nexus.gamebooster.optimizer.BoostAction
import com.nexus.gamebooster.optimizer.BoostActionIds
import com.nexus.gamebooster.optimizer.BoostActionOutcome
import com.nexus.gamebooster.optimizer.BoostSession
import com.nexus.gamebooster.shizuku.ShizukuPrivilege

/**
 * Aggressive auto-run: protect the selected game from battery / Doze restrictions.
 *
 *  1. Add package to device-idle whitelist
 *  2. Set App Standby bucket to "active"
 *
 * Both steps need Shizuku. Partial success is still reported honestly.
 */
class ProtectGameAction(
    private val shizuku: ShizukuPrivilege,
    private val targetGamePackage: String?,
) : BoostAction {

    override val id: String = BoostActionIds.PROTECT_GAME

    override suspend fun execute(session: BoostSession): BoostActionOutcome {
        if (!shizuku.isReady) {
            return BoostActionOutcome.Unsupported(UnavailableReasons.SHIZUKU_REQUIRED)
        }
        val pkg = targetGamePackage
        if (pkg.isNullOrBlank()) {
            return BoostActionOutcome.Skipped(UnavailableReasons.NO_GAME_SELECTED)
        }

        val whitelist = shizuku.whitelistFromBatteryOptimization(pkg)
        val bucket = shizuku.setStandbyBucket(pkg, "active")

        return when {
            whitelist.isSuccess && bucket.isSuccess ->
                BoostActionOutcome.Applied()
            whitelist.isSuccess || bucket.isSuccess ->
                BoostActionOutcome.Applied(detailKey = BoostDetailKeysExtra.PARTIAL_PROTECT)
            else ->
                BoostActionOutcome.Failed(
                    reasonKey = UnavailableReasons.API_ERROR,
                    message = listOfNotNull(
                        whitelist.stderr.takeIf { it.isNotBlank() },
                        bucket.stderr.takeIf { it.isNotBlank() },
                    ).joinToString(" | ").ifBlank { "protect failed" },
                )
        }
    }
}
