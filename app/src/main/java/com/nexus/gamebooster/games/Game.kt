package com.nexus.gamebooster.games

/**
 * A library entry.
 *
 * Two origins:
 *  - Installed entries ([isManual] = false): added from Discover / All apps. [id] IS
 *    the package name (stable across sessions), [installed]/[launchable]/[isDetectedGame]
 *    describe what the last real scan knew about it.
 *  - Manual entries ([isManual] = true): typed by the user for apps the detector may
 *    not classify as games. Persisted as `name|addedAtMillis` in DataStore; a
 *    user-typed [packageName] is session-only metadata (documented limitation).
 */
data class Game(
    val id: String,
    val name: String,
    val packageName: String?,
    val isManual: Boolean,
    val installed: Boolean = false,
    /** True only when the app itself declares ApplicationInfo.CATEGORY_GAME. */
    val isDetectedGame: Boolean = false,
    /** When the entry was added this session; 0 for entries restored from DataStore. */
    val addedAtMillis: Long = 0L,
    /** True when a launch intent was resolved for the package during the last scan. */
    val launchable: Boolean = false,
)

/**
 * Result of an installed-apps scan. Scanning is REAL since Phase 2 (launcher apps via
 * the manifest <queries> filter) — there is no "Unavailable" variant anymore.
 * [Failed.reason] is a stable machine key mapped to localized text by the UI layer.
 */
sealed interface GameScanResult {
    data class Success(val apps: List<InstalledApp>) : GameScanResult
    data class Failed(val reason: String) : GameScanResult
}
