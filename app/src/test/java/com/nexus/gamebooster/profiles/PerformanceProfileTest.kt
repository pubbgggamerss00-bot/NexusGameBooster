package com.nexus.gamebooster.profiles

import com.nexus.gamebooster.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the profile model + catalog (Phase 4 contract, re-created for the
 * Phase 7 tree after the sandbox wipe).
 *
 * These tests ARE executed for real via Gradle (`:app:testDebugUnitTest`) in this
 * sandbox — no stale "NOT EXECUTED" claim: see worklog Task 14-d for the green run.
 */
class PerformanceProfileTest {

    // ------------------------------------------------------------ ProfileIds.isValid

    @Test
    fun isValid_trueOnlyForTheThreePresetIds() {
        assertTrue(ProfileIds.isValid(ProfileIds.BALANCED))
        assertTrue(ProfileIds.isValid(ProfileIds.PERFORMANCE))
        assertTrue(ProfileIds.isValid(ProfileIds.BATTERY_SAVER))
    }

    @Test
    fun isValid_falseForNullLegacyAndUnknownValues() {
        assertFalse(ProfileIds.isValid(null))
        assertFalse(ProfileIds.isValid(ProfileIds.LEGACY_GAMING))
        assertFalse(ProfileIds.isValid("turbo"))
        assertFalse(ProfileIds.isValid(""))
    }

    // --------------------------------------------------------- ProfileIds.normalize

    @Test
    fun normalize_presetIdsPassThroughUnchanged() {
        assertEquals(ProfileIds.BALANCED, ProfileIds.normalize(ProfileIds.BALANCED))
        assertEquals(ProfileIds.PERFORMANCE, ProfileIds.normalize(ProfileIds.PERFORMANCE))
        assertEquals(ProfileIds.BATTERY_SAVER, ProfileIds.normalize(ProfileIds.BATTERY_SAVER))
    }

    @Test
    fun normalize_nullBecomesBalanced() {
        assertEquals(ProfileIds.BALANCED, ProfileIds.normalize(null))
    }

    @Test
    fun normalize_legacyGamingMigratesToBalanced() {
        assertEquals(ProfileIds.BALANCED, ProfileIds.normalize("gaming"))
        assertEquals(ProfileIds.BALANCED, ProfileIds.normalize(ProfileIds.LEGACY_GAMING))
    }

    @Test
    fun normalize_unknownValueBecomesBalanced() {
        assertEquals(ProfileIds.BALANCED, ProfileIds.normalize("turbo"))
        assertEquals(ProfileIds.BALANCED, ProfileIds.normalize(""))
    }

    // ------------------------------------------------------- catalog shape + policies

    @Test
    fun catalog_hasExactlyThreePresetsInStableOrder() {
        val repo = StaticProfileRepository()
        assertEquals(
            listOf(ProfileIds.BALANCED, ProfileIds.PERFORMANCE, ProfileIds.BATTERY_SAVER),
            repo.profiles.map { it.id },
        )
        assertEquals(3, repo.profiles.size)
    }

    @Test
    fun balancedPolicy_refresh15s_liveOn_noKeepScreenOn_inAppOn() {
        val policy = StaticProfileRepository()
            .profiles
            .first { it.id == ProfileIds.BALANCED }
            .monitoringPolicy
        assertEquals(15_000L, policy.autoRefreshIntervalMillis)
        assertTrue(policy.liveBatteryEvents)
        assertFalse(policy.keepScreenOnWhileVisible)
        assertTrue(policy.inAppMonitoringEnabled)
    }

    @Test
    fun performancePolicy_refresh5s_liveOn_keepScreenOn_inAppOn() {
        val policy = StaticProfileRepository()
            .profiles
            .first { it.id == ProfileIds.PERFORMANCE }
            .monitoringPolicy
        assertEquals(5_000L, policy.autoRefreshIntervalMillis)
        assertTrue(policy.liveBatteryEvents)
        assertTrue(policy.keepScreenOnWhileVisible)
        assertTrue(policy.inAppMonitoringEnabled)
    }

    @Test
    fun batterySaverPolicy_noAutoRefresh_liveOff_noKeepScreenOn_inAppOff() {
        val policy = StaticProfileRepository()
            .profiles
            .first { it.id == ProfileIds.BATTERY_SAVER }
            .monitoringPolicy
        assertNull(policy.autoRefreshIntervalMillis)
        assertFalse(policy.liveBatteryEvents)
        assertFalse(policy.keepScreenOnWhileVisible)
        assertFalse(policy.inAppMonitoringEnabled)
    }

    @Test
    fun fallbackLookups_legacyAndUnknownLandOnBalanced_defaultIsBalanced() {
        val repo = StaticProfileRepository()
        assertEquals(ProfileIds.BALANCED, repo.defaultProfile().id)
        assertNull(repo.findById(ProfileIds.LEGACY_GAMING)) // no preset behind the legacy id
        assertEquals(ProfileIds.BALANCED, repo.findByIdOrFallback("gaming").id)
        assertEquals(ProfileIds.BALANCED, repo.findByIdOrFallback(null).id)
        assertEquals(ProfileIds.PERFORMANCE, repo.findByIdOrFallback(ProfileIds.PERFORMANCE).id)
    }

    // ------------------------------------------------------- EffectKind semantics

    @Test
    fun effectKinds_eachProfileSectionUsesTheHonestKindExclusively() {
        val repo = StaticProfileRepository()
        for (profile in repo.profiles) {
            assertTrue(
                "appliedEffects must all be APPLIED_BY_APP for ${profile.id}",
                profile.appliedEffects.isNotEmpty() &&
                    profile.appliedEffects.all { it.kind == EffectKind.APPLIED_BY_APP },
            )
            assertTrue(
                "recommendations must all be REQUIRES_USER_ACTION for ${profile.id}",
                profile.recommendations.isNotEmpty() &&
                    profile.recommendations.all { it.kind == EffectKind.REQUIRES_USER_ACTION },
            )
            assertTrue(
                "notSupportedEffects must all be NOT_SUPPORTED for ${profile.id}",
                profile.notSupportedEffects.isNotEmpty() &&
                    profile.notSupportedEffects.all { it.kind == EffectKind.NOT_SUPPORTED },
            )
        }
    }

    @Test
    fun effectRows_carryRealStringResources_notPlaceholders() {
        val repo = StaticProfileRepository()
        for (profile in repo.profiles) {
            (profile.appliedEffects + profile.recommendations + profile.notSupportedEffects)
                .forEach { effect ->
                    assertTrue("textRes must be a real resource id", effect.textRes != 0)
                }
        }
        // The "not supported" rows exist on every profile — the honesty contract.
        val anyProfile = repo.profiles.first()
        assertEquals(
            setOf(R.string.ns_cpu_freq, R.string.ns_process_priority),
            anyProfile.notSupportedEffects.take(2).map { it.textRes }.toSet(),
        )
    }

    // -------------------------------------------------------------- immutability

    @Test
    fun models_areImmutableValueObjects_copyPreservesEquality() {
        val repo = StaticProfileRepository()
        val balanced = repo.profiles.first()
        val copy = balanced.copy()
        assertEquals(balanced, copy)
        // Copying with a different policy yields an unequal instance (value semantics).
        val withOtherPolicy = balanced.copy(
            monitoringPolicy = balanced.monitoringPolicy.copy(autoRefreshIntervalMillis = null),
        )
        assertEquals(null, withOtherPolicy.monitoringPolicy.autoRefreshIntervalMillis)
        assertTrue(balanced != withOtherPolicy)
        // The lists the catalog hands out are read-only List references.
        assertSame(balanced.appliedEffects, copy.appliedEffects)
        // MonitoringPolicy itself is a value object.
        assertEquals(
            balanced.monitoringPolicy,
            balanced.monitoringPolicy.copy(),
        )
    }
}
