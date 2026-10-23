package com.nexus.gamebooster.optimizer.actions

import com.nexus.gamebooster.monitoring.UnavailableReasons
import com.nexus.gamebooster.optimizer.BoostAction
import com.nexus.gamebooster.optimizer.BoostActionIds
import com.nexus.gamebooster.optimizer.BoostActionOutcome
import com.nexus.gamebooster.optimizer.BoostSession
import com.nexus.gamebooster.shizuku.ShizukuPrivilege

/**
 * Safety-first implementation (adopted from the 0.10.0 network-tools update).
 *
 * Force-stopping arbitrary launchable apps can interrupt calls, messaging,
 * accessibility services, alarms and other user-visible work. This action is
 * intentionally disabled until the app has a per-package review/consent flow.
 * It never issues force-stop commands automatically.
 *
 * Consequence for Phase 10: the "stopped_<n>" detail key and the "none_to_stop"
 * detail key are no longer produced by any action; [BoostDetailKeysExtra] and the
 * UiMessage mappings stay so existing persisted reports keep rendering, and the
 * PARTIAL_PROTECT key remains in active use by [ProtectGameAction].
 */
class ForceStopBackgroundAction(
    private val shizuku: ShizukuPrivilege,
    private val targetGamePackage: String?,
) : BoostAction {

    override val id: String = BoostActionIds.FORCE_STOP_BACKGROUND

    override suspend fun execute(session: BoostSession): BoostActionOutcome {
        // Keep constructor dependencies and target validation explicit; no packages
        // are stopped in this safe build.
        if (!shizuku.isReady) {
            return BoostActionOutcome.Unsupported(UnavailableReasons.SHIZUKU_REQUIRED)
        }
        if (targetGamePackage.isNullOrBlank()) {
            return BoostActionOutcome.Skipped(UnavailableReasons.NO_GAME_SELECTED)
        }
        return BoostActionOutcome.Skipped(UnavailableReasons.ACTION_DISABLED_FOR_SAFETY)
    }
}

/** Extra detail keys used by Phase-9 actions (localized in UiMessage). */
object BoostDetailKeysExtra {
    const val NONE_TO_STOP = "none_to_stop"
    const val PARTIAL_PROTECT = "partial_protect"
}
