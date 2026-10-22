package com.nexus.gamebooster.games

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Outcome of a launch attempt (or of a launchability RESOLVE). There is no fake
 * "Success with progress" — either the platform accepted startActivity, or the package
 * was only verified without starting it, or we honestly report why nothing happened.
 */
sealed interface GameLaunchResult {
    /** The system accepted startActivity — the target app is opening. */
    data object Launched : GameLaunchResult

    /**
     * Phase 5 (`resolve` only): the package HAS a launchable activity, but nothing was
     * started. Never returned by [GameLauncher.launch] — it does not imply a launch.
     */
    data object Ready : GameLaunchResult

    /** The package exists but exposes no MAIN/LAUNCHER activity we can start. */
    data object NoLaunchIntent : GameLaunchResult

    /** Nothing is installed under that package anymore (or it vanished mid-launch). */
    data object NotFound : GameLaunchResult

    /** The platform refused; [reason] is a short technical tag (exception class name). */
    data class Error(val reason: String) : GameLaunchResult
}

/**
 * Contract for launching a game/app by package name — and (Phase 5) for verifying
 * launchability WITHOUT launching. Kept separate from [InstalledAppSource] so
 * repositories and ViewModels can be tested with fakes that never touch the real
 * activity manager.
 */
interface GameLauncher {
    suspend fun launch(packageName: String): GameLaunchResult

    /**
     * Resolves whether [packageName] currently has a launchable activity — WITHOUT
     * starting anything. The result never implies a launch ([Ready] = "ready to
     * launch", used by the boost engine's PrepareGameLaunchAction).
     */
    suspend fun resolve(packageName: String): GameLaunchResult
}

/**
 * Real launcher: resolves the package's MAIN/LAUNCHER intent via PackageManager and
 * starts it with FLAG_ACTIVITY_NEW_TASK (required because we start from a non-activity
 * context). Every failure mode maps to an honest [GameLaunchResult].
 */
class AndroidGameLauncher(private val context: Context) : GameLauncher {

    override suspend fun launch(packageName: String): GameLaunchResult =
        withContext(Dispatchers.Main) {
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                    ?: return@withContext GameLaunchResult.NoLaunchIntent
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                GameLaunchResult.Launched
            } catch (_: ActivityNotFoundException) {
                GameLaunchResult.NotFound
            } catch (e: SecurityException) {
                GameLaunchResult.Error(e.javaClass.simpleName)
            } catch (e: Exception) {
                GameLaunchResult.Error(e.javaClass.simpleName)
            }
        }

    /**
     * Real resolve (Phase 5): queries PackageManager for the package's MAIN/LAUNCHER
     * activities — the same resolution [launch] performs — but NEVER calls startActivity.
     *
     * Visibility note (honest): under the manifest's `<queries>` MAIN/LAUNCHER filter,
     * a package that is installed AND launchable resolves; an empty result means the
     * package is not installed (or not visible) — for a launcher-filtered app these two
     * are indistinguishable without extra permissions, so the honest mapping is
     * [NotFound]. An installed-but-visible package always has a MAIN/LAUNCHER activity,
     * so [NoLaunchIntent] is only ever produced by other [GameLauncher] implementations.
     */
    override suspend fun resolve(packageName: String): GameLaunchResult =
        withContext(Dispatchers.Default) {
            try {
                val query = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setPackage(packageName)
                val activities = context.packageManager.queryIntentActivities(query, 0)
                if (activities.isEmpty()) {
                    GameLaunchResult.NotFound
                } else {
                    GameLaunchResult.Ready
                }
            } catch (e: SecurityException) {
                GameLaunchResult.Error(e.javaClass.simpleName)
            } catch (e: Exception) {
                GameLaunchResult.Error(e.javaClass.simpleName)
            }
        }
}
