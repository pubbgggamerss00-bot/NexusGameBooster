package com.nexus.gamebooster.core

import com.nexus.gamebooster.data.Language
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Recreated after the sandbox environment wipe (Task 16-b, from the Task 14-d spec).
 * IS executed for real via Gradle: `./gradlew :app:testDebugUnitTest`.
 *
 * The pure, JVM-testable RTL decision of the Phase 7 locale engine: SYSTEM mirrors the
 * device direction, the explicit choices are absolute.
 */
class AppLocaleTest {

    @Test
    fun system_mirrorsTheDeviceDirection() {
        assertTrue(AppLocale.isRtl(Language.SYSTEM, systemLocaleIsRtl = true))
        assertFalse(AppLocale.isRtl(Language.SYSTEM, systemLocaleIsRtl = false))
    }

    @Test
    fun english_isAlwaysLtr_regardlessOfTheDevice() {
        assertFalse(AppLocale.isRtl(Language.ENGLISH, systemLocaleIsRtl = true))
        assertFalse(AppLocale.isRtl(Language.ENGLISH, systemLocaleIsRtl = false))
    }

    @Test
    fun persian_isAlwaysRtl_regardlessOfTheDevice() {
        assertTrue(AppLocale.isRtl(Language.PERSIAN, systemLocaleIsRtl = true))
        assertTrue(AppLocale.isRtl(Language.PERSIAN, systemLocaleIsRtl = false))
    }
}
