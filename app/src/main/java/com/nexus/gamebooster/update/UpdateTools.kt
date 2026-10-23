package com.nexus.gamebooster.update

import java.util.Locale

/**
 * Phase 11 — pure, JVM-testable logic behind Settings → "Check for updates".
 *
 * The app's versionName ("0.11.0-updates") and the GitHub release tag
 * ("v0.11.0-updates") share one numeric prefix; only that prefix is comparable.
 * Anything else (suffixes like "-restore"/"-network-tools-fix") is a label, not
 * a number, and is deliberately NOT parsed into an ordering — when the numeric
 * prefixes are equal the app reports "up to date" instead of guessing.
 */
object UpdateTools {

    /**
     * The latest release, resolved WITHOUT the api.github.com JSON (which is
     * rate-limited per shared IP — useless for CGNAT users). Everything comes from
     * plain github.com redirects and predictable asset URLs, so a "failed" field
     * means the network really failed, never a quota.
     */
    data class LatestRelease(
        /** Raw tag, e.g. "v0.11.0-updates". */
        val tagName: String,
        /** Tag minus the leading "v" — comparable against BuildConfig.VERSION_NAME. */
        val versionName: String,
        /** Direct github.com download URL of the release APK asset. */
        val apkUrl: String,
        /** Byte size from a HEAD request, or -1 when unknown (indeterminate UI). */
        val apkSizeBytes: Long,
        /** Friendly whatsnew text for the user's language, or null when absent. */
        val whatsNewText: String?,
    )

    /** Extracts the leading numeric prefix ("0.10.1-network-tools-fix" → [0, 10, 1]). */
    fun numericPrefix(version: String): List<Int>? {
        val match = Regex("^(\\d+(?:\\.\\d+)*).*").find(version.trim())
        return match?.groupValues?.get(1)?.split('.')?.map { it.toInt() }
    }

    /**
     * Compares two version names by their numeric prefixes (standard compareTo
     * semantics over [local]). Returns null when EITHER side has no numeric prefix
     * (honest "can't compare"), otherwise negative = remote newer, 0 = equal,
     * positive = local newer. Missing components are zeros ("0.11" ≡ "0.11.0").
     */
    fun compareVersionNames(local: String, remote: String): Int? {
        val left = numericPrefix(local) ?: return null
        val right = numericPrefix(remote) ?: return null
        for (index in 0 until maxOf(left.size, right.size)) {
            val a = left.getOrElse(index) { 0 }
            val b = right.getOrElse(index) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
    }

    /**
     * Extracts the release tag from the Location header GitHub sends when
     * "…/releases/latest" redirects (302) to "…/releases/tag/vX.Y.Z-label".
     * Returns null when the URL does not have that exact shape — the caller then
     * reports an honest failure instead of guessing a version.
     */
    fun parseTagFromLocation(location: String?): String? {
        if (location == null) return null
        val match = Regex("/releases/tag/(v[^/?#]+)").find(location) ?: return null
        return match.groupValues[1]
    }

    /** Deterministic release-APK asset URL (the pipeline always uploads this name). */
    fun apkAssetUrl(repoBaseUrl: String, tagName: String): String =
        "$repoBaseUrl/releases/download/$tagName/NexusGameBooster-$tagName-release.apk"

    /** Deterministic friendly-notes asset URL for one language. */
    fun whatsNewAssetUrl(repoBaseUrl: String, tagName: String, languageTag: String): String =
        "$repoBaseUrl/releases/download/$tagName/whatsnew-$languageTag.txt"

    /** Human-readable size for the download UI ("12.3 MB") — always dot-decimal. */
    fun formatMb(bytes: Long): String =
        String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
}
