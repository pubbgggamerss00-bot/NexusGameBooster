package com.nexus.gamebooster.core

import com.nexus.gamebooster.data.Language
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 7 pure tests for [AppLocale.isRtl] — the JVM-testable RTL decision of the app
 * shell (no Android types involved).
 *
 * These tests ARE executed for real via Gradle (`:app:testDebugUnitTest`) in this
 * sandbox — see worklog Task 14-d for the green run.
 */
class AppLocaleTest {

    @Test
    fun system_mirrorsTheDeviceDirectionExactly() {
        assertTrue(AppLocale.isRtl(Language.SYSTEM, systemLocaleIsRtl = true))
        assertFalse(AppLocale.isRtl(Language.SYSTEM, systemLocaleIsRtl = false))
    }

    @Test
    fun english_isNeverRtl_regardlessOfDevice() {
        assertFalse(AppLocale.isRtl(Language.ENGLISH, systemLocaleIsRtl = true))
        assertFalse(AppLocale.isRtl(Language.ENGLISH, systemLocaleIsRtl = false))
    }

    @Test
    fun persian_isAlwaysRtl_regardlessOfDevice() {
        assertTrue(AppLocale.isRtl(Language.PERSIAN, systemLocaleIsRtl = true))
        assertTrue(AppLocale.isRtl(Language.PERSIAN, systemLocaleIsRtl = false))
    }
}
