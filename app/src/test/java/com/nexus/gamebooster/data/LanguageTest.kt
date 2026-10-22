package com.nexus.gamebooster.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Recreated after the sandbox environment wipe (Task 16-b, from the Task 14-d spec).
 * IS executed for real via Gradle: `./gradlew :app:testDebugUnitTest`.
 *
 * The in-app Language enum: stable storage codes, SYSTEM as the first/default entry,
 * and a tolerant fromCode that never fails (Phase 7 locale engine contract).
 */
class LanguageTest {

    @Test
    fun fromCode_returnsTheMatchingLanguage() {
        assertEquals(Language.PERSIAN, Language.fromCode("fa"))
        assertEquals(Language.ENGLISH, Language.fromCode("en"))
        assertEquals(Language.SYSTEM, Language.fromCode("system"))
    }

    @Test
    fun fromCode_unknownEmptyOrWrongCase_fallsBackToSystem() {
        assertEquals(Language.SYSTEM, Language.fromCode("xx"))
        assertEquals(Language.SYSTEM, Language.fromCode(""))
        // Codes are exact lowercase matches — "FA" is an unknown value, not Persian.
        assertEquals(Language.SYSTEM, Language.fromCode("FA"))
        assertEquals(Language.SYSTEM, Language.fromCode("fr"))
    }

    @Test
    fun system_isFirstEntry_stableCodesAndTheDefaultSetting() {
        assertEquals(Language.SYSTEM, Language.entries.first())
        assertEquals("system", Language.SYSTEM.code)
        assertEquals("en", Language.ENGLISH.code)
        assertEquals("fa", Language.PERSIAN.code)
        // A fresh AppSettings follows the device language until the user chooses.
        assertEquals(Language.SYSTEM, AppSettings().language)
    }
}
