package com.nexus.gamebooster.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 7 pure tests for the [Language] enum (locale engine contract).
 *
 * These tests ARE executed for real via Gradle (`:app:testDebugUnitTest`) in this
 * sandbox — see worklog Task 14-d for the green run.
 */
class LanguageTest {

    @Test
    fun fromCode_resolvesEveryShippedLanguage() {
        assertEquals(Language.PERSIAN, Language.fromCode("fa"))
        assertEquals(Language.ENGLISH, Language.fromCode("en"))
        assertEquals(Language.SYSTEM, Language.fromCode("system"))
    }

    @Test
    fun fromCode_unknownOrNullabilityFallbacksToSystem() {
        // Unknown codes (also the persisted-read path for a missing/corrupt key) → SYSTEM.
        assertEquals(Language.SYSTEM, Language.fromCode("klingon"))
        assertEquals(Language.SYSTEM, Language.fromCode(""))
        assertEquals(Language.SYSTEM, Language.fromCode("FA")) // codes are lowercase, case-sensitive
    }

    @Test
    fun codesAreStableStorageValues_systemIsFirstAndDefault() {
        assertEquals("system", Language.SYSTEM.code)
        assertEquals("en", Language.ENGLISH.code)
        assertEquals("fa", Language.PERSIAN.code)
        // SYSTEM must stay the FIRST entry: AppSettings/SettingsUiState default to it and
        // "no stored key" on read must mean SYSTEM (the device language), never EN/FA.
        assertEquals(Language.SYSTEM, Language.entries.first())
        assertEquals(Language.SYSTEM, Language.values().first())
        assertTrue(Language.entries.size == 3)
    }
}
