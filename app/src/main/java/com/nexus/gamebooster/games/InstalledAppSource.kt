package com.nexus.gamebooster.games

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Outcome of querying the device for launchable apps.
 * [Failed.reason] is a stable machine key (see `monitoring.UnavailableReasons`),
 * never raw exception text — the UI maps it to localized text.
 */
sealed interface InstalledAppQueryResult {
    data class Success(val apps: List<InstalledApp>) : InstalledAppQueryResult
    data class Failed(val reason: String) : InstalledAppQueryResult
}

/**
 * Source of truth for "which apps on this device can be launched".
 *
 * Honesty contract: the real implementation sees ONLY apps with a MAIN/LAUNCHER
 * intent (the manifest <queries> filter), and marks as games ONLY apps that declare
 * `ApplicationInfo.CATEGORY_GAME`. It never guesses, and it never claims every
 * installed package is visible.
 */
interface InstalledAppSource {

    /** Queries launchable apps. Runs on Dispatchers.IO in the real implementation. */
    suspend fun queryLaunchableApps(): InstalledAppQueryResult

    /**
     * Loads the real app icon for a package, or null when unavailable (unknown
     * package, uninstalled, or the platform refused). Callers must render a neutral
     * placeholder for null — never a fabricated icon.
     */
    suspend fun loadIcon(packageName: String): ImageBitmap?
}
