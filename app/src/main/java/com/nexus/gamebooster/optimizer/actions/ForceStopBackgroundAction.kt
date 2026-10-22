package com.nexus.gamebooster.optimizer.actions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.nexus.gamebooster.monitoring.UnavailableReasons
import com.nexus.gamebooster.optimizer.BoostAction
import com.nexus.gamebooster.optimizer.BoostActionIds
import com.nexus.gamebooster.optimizer.BoostActionOutcome
import com.nexus.gamebooster.optimizer.BoostSession
import com.nexus.gamebooster.shizuku.ShizukuPrivilege

/**
 * Aggressive auto-run action: force-stop user-installed launchable apps that are
 * NOT the target game, NOT this booster, and NOT on the hard safe-list.
 *
 * Requires Shizuku READY. Without it → honest [Unsupported].
 *
 * Scope is intentionally aggressive (Phase 9): every non-safe launchable package
 * gets `am force-stop`. System packages and the protected set are skipped.
 */
class ForceStopBackgroundAction(
    private val context: Context,
    private val shizuku: ShizukuPrivilege,
    private val targetGamePackage: String?,
) : BoostAction {

    override val id: String = BoostActionIds.FORCE_STOP_BACKGROUND

    override suspend fun execute(session: BoostSession): BoostActionOutcome {
        if (!shizuku.isReady) {
            return BoostActionOutcome.Unsupported(UnavailableReasons.SHIZUKU_REQUIRED)
        }
        if (targetGamePackage.isNullOrBlank()) {
            return BoostActionOutcome.Skipped(UnavailableReasons.NO_GAME_SELECTED)
        }

        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launchables = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)

        val protected = buildSet {
            add(context.packageName)
            add(targetGamePackage)
            addAll(HARD_SAFE_PACKAGES)
        }

        var stopped = 0
        var failed = 0
        val seen = mutableSetOf<String>()

        for (ri in launchables) {
            val pkg = ri.activityInfo?.packageName ?: continue
            if (!seen.add(pkg)) continue
            if (pkg in protected) continue
            // Skip obvious system / privilege holders
            if (pkg.startsWith("com.android.") || pkg.startsWith("android.")) continue
            if (pkg.startsWith("com.google.android.")) continue
            if (pkg.contains("shizuku", ignoreCase = true)) continue

            val result = shizuku.forceStop(pkg)
            if (result.isSuccess) stopped++ else failed++
        }

        return when {
            stopped == 0 && failed == 0 ->
                BoostActionOutcome.AlreadyApplied(detailKey = BoostDetailKeysExtra.NONE_TO_STOP)
            stopped > 0 ->
                BoostActionOutcome.Applied(detailKey = "stopped_$stopped")
            else ->
                BoostActionOutcome.Failed(
                    reasonKey = UnavailableReasons.API_ERROR,
                    message = "force-stop failed for all candidates ($failed)",
                )
        }
    }

    companion object {
        /** Never force-stop these even if they have a launcher activity. */
        val HARD_SAFE_PACKAGES = setOf(
            "moe.shizuku.privileged.api",
            "com.android.settings",
            "com.android.systemui",
            "com.android.phone",
            "com.android.launcher",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.miui.home",
            "com.sec.android.app.launcher",
            "com.huawei.android.launcher",
            "com.oppo.launcher",
            "com.vivo.launcher",
        )
    }
}

/** Extra detail keys used by Phase-9 actions (localized in UiMessage). */
object BoostDetailKeysExtra {
    const val NONE_TO_STOP = "none_to_stop"
    const val PARTIAL_PROTECT = "partial_protect"
}
