package com.nexus.gamebooster.games

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.nexus.gamebooster.monitoring.UnavailableReasons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Real PackageManager-backed discovery.
 *
 * Visibility: because the manifest declares the MAIN/LAUNCHER <queries> intent (and
 * NOT QUERY_ALL_PACKAGES), `getInstalledApplications` only returns apps that are
 * launchable from a launcher — exactly what this feature needs.
 *
 * Game detection (HONEST): an app is a game ONLY if it declares
 * `ApplicationInfo.CATEGORY_GAME` (available since API 26 — our minSdk).
 * CATEGORY_UNDEFINED (the default) means not-a-game. The deprecated
 * `ApplicationInfo.FLAG_IS_GAME` is deliberately NOT used.
 *
 * Consequence, stated plainly: only apps that declare themselves as games appear in
 * the Discover "Games" filter — some games do not declare it. That is why the UI
 * offers "All apps", where the user can add any launchable app manually. This class
 * never claims every app is a game.
 */
class AndroidInstalledAppSource(private val appContext: Context) : InstalledAppSource {

    override suspend fun queryLaunchableApps(): InstalledAppQueryResult =
        withContext(Dispatchers.IO) {
            try {
                val packageManager = appContext.packageManager
                val installed: List<ApplicationInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getInstalledApplications(
                        PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
                    )
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                }

                val launchable = installed.mapNotNull { info ->
                    // Launchable iff the platform can produce a MAIN/LAUNCHER intent for it.
                    if (packageManager.getLaunchIntentForPackage(info.packageName) == null) {
                        null
                    } else {
                        InstalledApp(
                            packageName = info.packageName,
                            label = info.loadLabel(packageManager).toString().ifBlank { info.packageName },
                            isGame = info.category == ApplicationInfo.CATEGORY_GAME,
                            category = info.category,
                        )
                    }
                }.sortedBy { it.label.lowercase() }

                InstalledAppQueryResult.Success(launchable)
            } catch (e: Exception) {
                // Stable machine key for the UI; details go to logcat for the developer.
                Log.w(TAG, "Installed-app scan failed", e)
                InstalledAppQueryResult.Failed(UnavailableReasons.SCAN_FAILED)
            }
        }

    override suspend fun loadIcon(packageName: String): ImageBitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                appContext.packageManager.getApplicationIcon(packageName).toBitmap().asImageBitmap()
            }.getOrNull()
        }

    private companion object {
        const val TAG = "NgbGames"
    }
}
