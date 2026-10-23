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

/**
 * Aggressive auto-run: force ART to compile the target game with speed-profile.
 * Can take several seconds on first run; improves subsequent cold starts / frame pacing.
 *
 * Phase 10: on success the package is recorded in the device-change ledger. The exact
 * prior compile state cannot be snapshotted — restore honestly uses `--reset` (back to
 * the system-managed state) and the UI says so.
 */
class CompileGameAction(
    private val shizuku: ShizukuPrivilege,
    private val targetGamePackage: String?,
    private val mode: String = "speed-profile",
    private val changeLedger: DeviceChangeStore? = null,
) : BoostAction {

    override val id: String = BoostActionIds.COMPILE_GAME

    override suspend fun execute(session: BoostSession): BoostActionOutcome {
        if (!shizuku.isReady) {
            return BoostActionOutcome.Unsupported(UnavailableReasons.SHIZUKU_REQUIRED)
        }
        val pkg = targetGamePackage
        if (pkg.isNullOrBlank()) {
            return BoostActionOutcome.Skipped(UnavailableReasons.NO_GAME_SELECTED)
        }

        val result = shizuku.compilePackage(pkg, mode)
        return if (result.isSuccess) {
            changeLedger?.addDeviceChangeIfAbsent(
                DeviceChangeRecord(
                    id = "art_" + pkg + "_" + System.currentTimeMillis(),
                    type = DeviceChangeTypes.ART_COMPILE,
                    packageName = pkg,
                    createdAtMillis = System.currentTimeMillis(),
                ),
            )
            BoostActionOutcome.Applied()
        } else {
            // Some OEMs restrict compile; report honestly instead of pretending.
            BoostActionOutcome.Failed(
                reasonKey = UnavailableReasons.API_ERROR,
                message = result.stderr.ifBlank { "compile failed (code ${result.exitCode})" },
            )
        }
    }
}
