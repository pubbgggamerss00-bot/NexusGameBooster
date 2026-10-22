package com.nexus.gamebooster.games

/**
 * One launchable installed app as discovered by the PackageManager query.
 *
 * Pure Kotlin data (no Android types) so discovery logic can be unit-tested with
 * fakes. [category] is the raw `ApplicationInfo.category` value, kept for
 * diagnostics only — [isGame] is the single honest decision field:
 * it is true ONLY when the app itself declares `CATEGORY_GAME`.
 */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val isGame: Boolean,
    val category: Int,
)
