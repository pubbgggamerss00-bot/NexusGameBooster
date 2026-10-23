package com.nexus.gamebooster.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 11 — pure logic behind the Settings update card: version-prefix comparison,
 * the releases/latest redirect-Location tag parser, the deterministic asset URLs and
 * size formatting. No Android types, no network — the HTTP layer itself is verified
 * by packaging plus real endpoint checks (curl), not by a fake server here.
 */
class UpdateToolsTest {

    // ------------------------------------------------------------ version compare

    @Test
    fun numericPrefix_extractsLeadingNumbers() {
        assertEquals(listOf(0, 10, 1), UpdateTools.numericPrefix("0.10.1-network-tools-fix"))
        assertEquals(listOf(0, 9, 4), UpdateTools.numericPrefix("0.9.4-boost-modes"))
        assertEquals(listOf(0, 11), UpdateTools.numericPrefix("0.11"))
        assertEquals(listOf(2), UpdateTools.numericPrefix("2-x"))
    }

    @Test
    fun numericPrefix_rejectsNonNumericVersions() {
        assertNull(UpdateTools.numericPrefix("abc"))
        assertNull(UpdateTools.numericPrefix(""))
        assertNull(UpdateTools.numericPrefix("-boost"))
    }

    @Test
    fun compare_samePrefix_isEqualRegardlessOfSuffix() {
        assertEquals(Integer.valueOf(0), UpdateTools.compareVersionNames("0.11.0-updates", "0.11.0-updates"))
        // Same numbers, different labels: NOT an update (never guesses an ordering).
        assertEquals(Integer.valueOf(0), UpdateTools.compareVersionNames("0.11.0-updates", "0.11.0-whatever"))
    }

    @Test
    fun compare_remoteNewer_isNegative_localNewer_isPositive() {
        // compareTo semantics: local vs remote — negative means the remote is newer.
        assertTrue(UpdateTools.compareVersionNames("0.10.1-x", "0.11.0-updates")!! < 0)
        assertTrue(UpdateTools.compareVersionNames("0.10.1-x", "0.10.2-x")!! < 0)
        assertTrue(UpdateTools.compareVersionNames("1.9.9", "2.0.0")!! < 0)
        assertTrue(UpdateTools.compareVersionNames("0.11.0-updates", "0.10.1-x")!! > 0)
    }

    @Test
    fun compare_missingComponents_areZeros() {
        assertEquals(Integer.valueOf(0), UpdateTools.compareVersionNames("0.11", "0.11.0"))
        assertTrue(UpdateTools.compareVersionNames("0.11", "0.11.1")!! < 0)
    }

    @Test
    fun compare_unparseableSide_returnsNull() {
        assertNull(UpdateTools.compareVersionNames("abc", "0.11.0"))
        assertNull(UpdateTools.compareVersionNames("0.11.0", "abc"))
    }

    // ------------------------------------------------------------ redirect tag parse

    @Test
    fun parseTagFromLocation_readsTheRealGithubShape() {
        val real = "https://github.com/pubbgggamerss00-bot/NexusGameBooster/releases/tag/v0.10.1-network-tools-fix"
        assertEquals("v0.10.1-network-tools-fix", UpdateTools.parseTagFromLocation(real))
    }

    @Test
    fun parseTagFromLocation_rejectsWrongShapes() {
        assertNull(UpdateTools.parseTagFromLocation(null))
        assertNull(UpdateTools.parseTagFromLocation("https://github.com/x/y/releases/latest"))
        assertNull(UpdateTools.parseTagFromLocation("https://github.com/x/y/releases/tag/not-a-vtag"))
        assertNull(UpdateTools.parseTagFromLocation("https://example.com/whatever"))
    }

    // ------------------------------------------------------------ asset URLs

    @Test
    fun assetUrls_matchThePipelineUploadNames() {
        val base = "https://github.com/pubbgggamerss00-bot/NexusGameBooster"
        assertEquals(
            "$base/releases/download/v0.11.0-updates/NexusGameBooster-v0.11.0-updates-release.apk",
            UpdateTools.apkAssetUrl(base, "v0.11.0-updates"),
        )
        assertEquals(
            "$base/releases/download/v0.11.0-updates/whatsnew-fa.txt",
            UpdateTools.whatsNewAssetUrl(base, "v0.11.0-updates", "fa"),
        )
    }

    // ------------------------------------------------------------ formatting

    @Test
    fun formatMb_isDotDecimalWithUnit() {
        assertEquals("12.3 MB", UpdateTools.formatMb(12345678L))
        assertEquals("0.0 MB", UpdateTools.formatMb(0L))
        assertEquals("15.7 MB", UpdateTools.formatMb(15747670L))
    }
}
