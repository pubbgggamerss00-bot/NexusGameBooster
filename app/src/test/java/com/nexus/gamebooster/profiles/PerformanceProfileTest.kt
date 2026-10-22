package com.nexus.gamebooster.profiles

import com.nexus.gamebooster.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Recreated after the sandbox environment wipe (Task 16-b, from the Task 14-d spec).
 * IS executed for real via Gradle: `./gradlew :app:testDebugUnitTest`.
 *
 * Tests the REAL catalog ([StaticProfileRepository]) and [ProfileIds] normalization:
 * the locked Phase 4 monitoring policies, the stable display order, honest
 * fallback lookups, EffectKind exclusivity per section, real R.string ids, and
 * immutability/copy semantics.
 */
class PerformanceProfileTest {

    private val repo = StaticProfileRepository()

    // ------------------------------------------------------------- ProfileIds

    @Test
    fun profileIds_isValid_trueOnlyForTheThreePresets() {
        assertTrue(ProfileIds.isValid(ProfileIds.BALANCED))
        assertTrue(ProfileIds.isValid(ProfileIds.PERFORMANCE))
        assertTrue(ProfileIds.isValid(ProfileIds.BATTERY_SAVER))
        assertFalse(ProfileIds.isValid(null))
        assertFalse(ProfileIds.isValid(ProfileIds.LEGACY_GAMING))
        assertFalse(ProfileIds.isValid("unknown_id"))
        assertFalse(ProfileIds.isValid(""))
    }

    @Test
    fun profileIds_normalize_passesPresetIdsThroughUnchanged() {
        assertEquals(ProfileIds.BALANCED, ProfileIds.normalize(ProfileIds.BALANCED))
        assertEquals(ProfileIds.PERFORMANCE, ProfileIds.normalize(ProfileIds.PERFORMANCE))
        assertEquals(ProfileIds.BATTERY_SAVER, ProfileIds.normalize(ProfileIds.BATTERY_SAVER))
    }

    @Test
    fun profileIds_normalize_mapsLegacyGamingAndAnythingUnknownToBalanced() {
        assertEquals(ProfileIds.BALANCED, ProfileIds.normalize(ProfileIds.LEGACY_GAMING))
        assertEquals(ProfileIds.BALANCED, ProfileIds.normalize(null))
        assertEquals(ProfileIds.BALANCED, ProfileIds.normalize("unknown_id"))
        assertEquals(ProfileIds.BALANCED, ProfileIds.normalize(""))
    }

    // --------------------------------------------------- locked preset policies

    @Test
    fun balancedPolicy_isFifteenSecondRefresh_liveAndNoKeepScreenOn() {
        val policy = repo.findById(ProfileIds.BALANCED)!!.monitoringPolicy
        assertEquals(15_000L, policy.autoRefreshIntervalMillis)
        assertTrue(policy.liveBatteryEvents)
        assertFalse(policy.keepScreenOnWhileVisible)
        assertTrue(policy.inAppMonitoringEnabled)
    }

    @Test
    fun performancePolicy_isFiveSecondRefresh_liveAndKeepsScreenOn() {
        val policy = repo.findById(ProfileIds.PERFORMANCE)!!.monitoringPolicy
        assertEquals(5_000L, policy.autoRefreshIntervalMillis)
        assertTrue(policy.liveBatteryEvents)
        assertTrue(policy.keepScreenOnWhileVisible)
        assertTrue(policy.inAppMonitoringEnabled)
    }

    @Test
    fun batterySaverPolicy_turnsEveryInAppAutomationOff() {
        val policy = repo.findById(ProfileIds.BATTERY_SAVER)!!.monitoringPolicy
        assertNull(policy.autoRefreshIntervalMillis)
        assertFalse(policy.liveBatteryEvents)
        assertFalse(policy.keepScreenOnWhileVisible)
        assertFalse(policy.inAppMonitoringEnabled)
    }

    // ------------------------------------------------------------------ catalog

    @Test
    fun catalog_orderIsBalancedPerformanceBatterySaver() {
        assertEquals(3, repo.profiles.size)
        assertEquals(
            listOf(ProfileIds.BALANCED, ProfileIds.PERFORMANCE, ProfileIds.BATTERY_SAVER),
            repo.profiles.map { it.id },
        )
    }

    @Test
    fun defaultProfile_isBalanced_theFirstCatalogEntry() {
        assertEquals(ProfileIds.BALANCED, repo.defaultProfile().id)
        assertSame(repo.profiles.first(), repo.defaultProfile())
    }

    @Test
    fun findById_exactLookup_onlyPresetIdsResolve() {
        assertEquals(ProfileIds.BALANCED, repo.findById(ProfileIds.BALANCED)?.id)
        assertEquals(ProfileIds.PERFORMANCE, repo.findById(ProfileIds.PERFORMANCE)?.id)
        assertEquals(ProfileIds.BATTERY_SAVER, repo.findById(ProfileIds.BATTERY_SAVER)?.id)
        // The legacy id has NO preset — exact lookup must not silently invent one.
        assertNull(repo.findById(ProfileIds.LEGACY_GAMING))
        assertNull(repo.findById("unknown_id"))
    }

    @Test
    fun findByIdOrFallback_neverFailsForAnyRawPersistedValue() {
        assertEquals(ProfileIds.PERFORMANCE, repo.findByIdOrFallback(ProfileIds.PERFORMANCE).id)
        assertEquals(ProfileIds.BATTERY_SAVER, repo.findByIdOrFallback(ProfileIds.BATTERY_SAVER).id)
        assertEquals(ProfileIds.BALANCED, repo.findByIdOrFallback(null).id)
        assertEquals(ProfileIds.BALANCED, repo.findByIdOrFallback(ProfileIds.LEGACY_GAMING).id)
        assertEquals(ProfileIds.BALANCED, repo.findByIdOrFallback("unknown_id").id)
        assertNotNull(repo.findByIdOrFallback(""))
    }

    // ------------------------------------------------------- honesty invariants

    @Test
    fun effectSections_areKindExclusive_forEveryProfile() {
        for (profile in repo.profiles) {
            assertTrue(profile.appliedEffects.isNotEmpty())
            assertTrue(profile.appliedEffects.all { it.kind == EffectKind.APPLIED_BY_APP })
            assertTrue(profile.recommendations.isNotEmpty())
            assertTrue(profile.recommendations.all { it.kind == EffectKind.REQUIRES_USER_ACTION })
            assertTrue(profile.notSupportedEffects.isNotEmpty())
            assertTrue(profile.notSupportedEffects.all { it.kind == EffectKind.NOT_SUPPORTED })
        }
    }

    @Test
    fun notSupportedRows_nameTheSameFourHonestClaimsForEveryProfile() {
        val expected = listOf(
            R.string.ns_cpu_freq,
            R.string.ns_process_priority,
            R.string.ns_thermal_limits,
            R.string.ns_ram_boost,
        )
        for (profile in repo.profiles) {
            assertEquals(expected, profile.notSupportedEffects.map { it.textRes })
        }
    }

    @Test
    fun titlesAndDescriptions_useRealStringResources() {
        assertEquals(R.string.profile_balanced_title, repo.findById(ProfileIds.BALANCED)!!.titleRes)
        assertEquals(R.string.profile_balanced_description, repo.findById(ProfileIds.BALANCED)!!.descriptionRes)
        assertEquals(R.string.profile_performance_title, repo.findById(ProfileIds.PERFORMANCE)!!.titleRes)
        assertEquals(R.string.profile_performance_description, repo.findById(ProfileIds.PERFORMANCE)!!.descriptionRes)
        assertEquals(R.string.profile_battery_title, repo.findById(ProfileIds.BATTERY_SAVER)!!.titleRes)
        assertEquals(R.string.profile_battery_description, repo.findById(ProfileIds.BATTERY_SAVER)!!.descriptionRes)
    }

    // ------------------------------------------------------------ immutability

    @Test
    fun copy_neverMutatesTheOriginal_andSectionsAreImmutableLists() {
        val original = repo.findById(ProfileIds.BALANCED)!!
        val modified = original.copy(id = "changed")
        assertEquals("balanced", original.id)
        assertEquals("changed", modified.id)
        assertEquals(original.monitoringPolicy, modified.monitoringPolicy)
        // Sections are built with listOf — mutation is rejected, never silently allowed.
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (original.appliedEffects as MutableList<ProfileEffect>).add(
                ProfileEffect(EffectKind.APPLIED_BY_APP, R.string.chip_ok),
            )
        }
        // The catalog is stable: repeated reads return the same fixed list instance.
        assertSame(repo.profiles, repo.profiles)
    }
}
