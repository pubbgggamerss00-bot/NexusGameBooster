package com.nexus.gamebooster.optimizer.actions

import android.content.ContextWrapper
import android.content.Intent
import com.nexus.gamebooster.core.SessionMonitorController
import com.nexus.gamebooster.core.SessionMonitorMode
import com.nexus.gamebooster.games.GameLaunchResult
import com.nexus.gamebooster.monitoring.UnavailableReasons
import com.nexus.gamebooster.optimizer.BoostActionOutcome
import com.nexus.gamebooster.optimizer.BoostDetailKeys
import com.nexus.gamebooster.profiles.ProfileIds
import com.nexus.gamebooster.profiles.StaticProfileRepository
import com.nexus.gamebooster.testing.FakeActiveProfileStore
import com.nexus.gamebooster.testing.FakeGameLauncher
import com.nexus.gamebooster.testing.batterySession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the boost actions' honest decision logic (Phase 5 contract).
 *
 * Pure-Kotlin actions are tested with the fakes from `testing/` against the REAL
 * interfaces, so any signature drift fails the compile instead of the test.
 *
 * JVM LIMIT (honest, measured): [OpenBatterySettingsAction] and
 * [OpenGameDetailsAction] chain `Intent(...).setData(...).addFlags(...)` BEFORE their
 * try-block. On the mockable android.jar those methods return default values
 * (`addFlags` → null), so the chain itself NPEs before `startActivity` is ever reached
 * — verified in the first real run of Task 14-d ("Cannot invoke Intent.addFlags(int)
 * because the return value of Intent.setData(android.net.Uri) is null"). That is mock
 * behavior, not a device bug (on-device the framework returns `this`), but it means
 * these two Context-dependent actions cannot be exercised past intent construction on
 * a plain JVM — no Robolectric is allowed in this suite. What IS tested on the JVM:
 * the no-package skip path (below, no intent is built), the ActivityNotFound →
 * Unsupported mapping's Consent boundary via the [BoostExplicitActions] seam in
 * BoostViewModelTest, and the launch verb ([LaunchGameAction]) end-to-end.
 *
 * These tests ARE executed for real via Gradle (`:app:testDebugUnitTest`) in this
 * sandbox — see worklog Task 14-d for the green run.
 */
class BoostActionsTest {

    // ------------------------------------------------------------ ApplyProfileAction

    @Test
    fun applyProfile_validStoredId_alreadyApplied_withoutWriting() = runTest {
        val store = FakeActiveProfileStore(ProfileIds.PERFORMANCE)
        val outcome = ApplyProfileAction(StaticProfileRepository(), store)
            .execute(batterySession(level = 87, charging = false))

        assertTrue(outcome is BoostActionOutcome.AlreadyApplied)
        assertEquals(null, store.written) // valid preset — nothing is written
    }

    @Test
    fun applyProfile_legacyGaming_normalizesAndRewrites_applied() = runTest {
        val store = FakeActiveProfileStore(ProfileIds.LEGACY_GAMING)
        val outcome = ApplyProfileAction(StaticProfileRepository(), store)
            .execute(batterySession(level = 87, charging = false))

        assertTrue(outcome is BoostActionOutcome.Applied)
        assertEquals(ProfileIds.BALANCED, store.written) // the same migration every read applies
        assertEquals(ProfileIds.BALANCED, store.activeProfileId.first())
    }

    @Test
    fun applyProfile_unknownId_normalizesAndRewrites_applied() = runTest {
        val store = FakeActiveProfileStore("turbo")
        val outcome = ApplyProfileAction(StaticProfileRepository(), store)
            .execute(batterySession(level = 87, charging = false))

        assertTrue(outcome is BoostActionOutcome.Applied)
        assertEquals(ProfileIds.BALANCED, store.written)
    }

    @Test
    fun applyProfile_storeWriteFails_failedWithApiError() = runTest {
        val store = FakeActiveProfileStore(ProfileIds.LEGACY_GAMING).apply { failWrites = true }
        val outcome = ApplyProfileAction(StaticProfileRepository(), store)
            .execute(batterySession(level = 87, charging = false))

        assertTrue(outcome is BoostActionOutcome.Failed)
        outcome as BoostActionOutcome.Failed
        assertEquals(UnavailableReasons.API_ERROR, outcome.reasonKey)
        assertEquals(null, store.written)
    }

    // ------------------------------------------------------- SessionMonitoringAction

    @Test
    fun sessionMonitoring_lowBatteryDischarging_setsEfficient_applied() = runTest {
        val controller = SessionMonitorController()
        val outcome = SessionMonitoringAction(controller)
            .execute(batterySession(level = 15, charging = false, profileId = ProfileIds.BALANCED))

        assertTrue(outcome is BoostActionOutcome.Applied)
        assertEquals(SessionMonitorMode.EFFICIENT, controller.mode.value)
    }

    @Test
    fun sessionMonitoring_boundaryLevel20_countsAsLow_applied() = runTest {
        val controller = SessionMonitorController()
        val outcome = SessionMonitoringAction(controller)
            .execute(batterySession(level = 20, charging = false, profileId = ProfileIds.BALANCED))

        assertTrue(outcome is BoostActionOutcome.Applied)
        assertEquals(SessionMonitorMode.EFFICIENT, controller.mode.value)
    }

    @Test
    fun sessionMonitoring_charging_skippedNotNeeded_controllerUntouched() = runTest {
        val controller = SessionMonitorController()
        val outcome = SessionMonitoringAction(controller)
            .execute(batterySession(level = 15, charging = true, profileId = ProfileIds.BALANCED))

        assertTrue(outcome is BoostActionOutcome.Skipped)
        outcome as BoostActionOutcome.Skipped
        assertEquals(UnavailableReasons.NOT_NEEDED, outcome.reasonKey)
        assertEquals(SessionMonitorMode.NORMAL, controller.mode.value)
    }

    @Test
    fun sessionMonitoring_healthyBattery_skippedBatteryHealthy() = runTest {
        val controller = SessionMonitorController()
        val outcome = SessionMonitoringAction(controller)
            .execute(batterySession(level = 80, charging = false, profileId = ProfileIds.BALANCED))

        assertTrue(outcome is BoostActionOutcome.Skipped)
        outcome as BoostActionOutcome.Skipped
        assertEquals(UnavailableReasons.BATTERY_HEALTHY, outcome.reasonKey)
        assertEquals(SessionMonitorMode.NORMAL, controller.mode.value)
    }

    @Test
    fun sessionMonitoring_batterySaverProfile_skippedAlreadyEfficient() = runTest {
        val controller = SessionMonitorController()
        val outcome = SessionMonitoringAction(controller)
            .execute(batterySession(level = 15, charging = false, profileId = ProfileIds.BATTERY_SAVER))

        assertTrue(outcome is BoostActionOutcome.Skipped)
        outcome as BoostActionOutcome.Skipped
        assertEquals(UnavailableReasons.ALREADY_EFFICIENT, outcome.reasonKey)
        assertEquals(SessionMonitorMode.NORMAL, controller.mode.value)
    }

    @Test
    fun sessionMonitoring_batteryUnavailable_skippedNeverGuessed() = runTest {
        val controller = SessionMonitorController()
        val outcome = SessionMonitoringAction(controller)
            .execute(batterySession(level = null, charging = null, profileId = ProfileIds.BALANCED))

        assertTrue(outcome is BoostActionOutcome.Skipped)
        outcome as BoostActionOutcome.Skipped
        assertEquals(UnavailableReasons.BATTERY_UNAVAILABLE, outcome.reasonKey)
        assertEquals(SessionMonitorMode.NORMAL, controller.mode.value)
    }

    // ------------------------------------------------------- PrepareGameLaunchAction

    @Test
    fun prepareLaunch_noGameSelected_skipped() = runTest {
        val launcher = FakeGameLauncher()
        val outcome = PrepareGameLaunchAction(launcher, packageName = null)
            .execute(batterySession(level = 87, charging = false))

        assertTrue(outcome is BoostActionOutcome.Skipped)
        outcome as BoostActionOutcome.Skipped
        assertEquals(UnavailableReasons.NO_GAME_SELECTED, outcome.reasonKey)
        assertEquals(0, launcher.resolveCalls.size) // nothing resolved without a package
    }

    @Test
    fun prepareLaunch_readyToLaunch_appliedWithDetail_neverLaunches() = runTest {
        val launcher = FakeGameLauncher(resolveResult = GameLaunchResult.Ready)
        val outcome = PrepareGameLaunchAction(launcher, packageName = "com.game.one")
            .execute(batterySession(level = 87, charging = false))

        assertTrue(outcome is BoostActionOutcome.Applied)
        outcome as BoostActionOutcome.Applied
        assertEquals(BoostDetailKeys.READY_TO_LAUNCH, outcome.detailKey)
        assertEquals(listOf("com.game.one"), launcher.resolveCalls)
        assertEquals(0, launcher.launchCalls.size) // prepare NEVER launches — consent stays with the user
    }

    @Test
    fun prepareLaunch_noLaunchIntent_unsupported() = runTest {
        val launcher = FakeGameLauncher(resolveResult = GameLaunchResult.NoLaunchIntent)
        val outcome = PrepareGameLaunchAction(launcher, packageName = "com.game.one")
            .execute(batterySession(level = 87, charging = false))

        assertTrue(outcome is BoostActionOutcome.Unsupported)
        outcome as BoostActionOutcome.Unsupported
        assertEquals(UnavailableReasons.NO_LAUNCH_INTENT, outcome.reasonKey)
    }

    @Test
    fun prepareLaunch_notFound_skipped() = runTest {
        val launcher = FakeGameLauncher(resolveResult = GameLaunchResult.NotFound)
        val outcome = PrepareGameLaunchAction(launcher, packageName = "com.game.one")
            .execute(batterySession(level = 87, charging = false))

        assertTrue(outcome is BoostActionOutcome.Skipped)
        outcome as BoostActionOutcome.Skipped
        assertEquals(UnavailableReasons.GAME_NOT_FOUND, outcome.reasonKey)
    }

    // --------------------------------------- Context-dependent USER-EXPLICIT actions

    /** Minimal Context whose startActivity is observed — nothing else is touched. */
    private open class StartActivityContext : ContextWrapper(null) {
        var startCalls = 0
            private set

        override fun startActivity(intent: Intent) {
            startCalls++
        }
    }

    @Test
    fun openGameDetails_noPackage_skipped_withoutBuildingOrStartingAnyIntent() = runTest {
        // The no-game-selected guard runs BEFORE any Intent is constructed, so this
        // decision IS JVM-testable even though the success/ActivityNotFound paths are
        // not (see the class KDoc for the measured mockable-jar limit).
        val context = StartActivityContext()
        val outcome = OpenGameDetailsAction(context, packageName = null)
            .execute(batterySession(level = 87, charging = false))

        assertTrue(outcome is BoostActionOutcome.Skipped)
        outcome as BoostActionOutcome.Skipped
        assertEquals(UnavailableReasons.NO_GAME_SELECTED, outcome.reasonKey)
        assertEquals(0, context.startCalls)
    }

    // ------------------------------------------------------------- LaunchGameAction

    @Test
    fun launchGame_launched_applied() = runTest {
        val launcher = FakeGameLauncher(launchResult = GameLaunchResult.Launched)
        val outcome = LaunchGameAction(launcher, packageName = "com.game.one")
            .execute(batterySession(level = 87, charging = false))

        assertTrue(outcome is BoostActionOutcome.Applied)
        assertEquals(listOf("com.game.one"), launcher.launchCalls)
        assertEquals(0, launcher.resolveCalls.size) // launch does not re-resolve
    }

    @Test
    fun launchGame_noLaunchIntent_unsupported() = runTest {
        val launcher = FakeGameLauncher(launchResult = GameLaunchResult.NoLaunchIntent)
        val outcome = LaunchGameAction(launcher, packageName = "com.game.one")
            .execute(batterySession(level = 87, charging = false))

        assertTrue(outcome is BoostActionOutcome.Unsupported)
        outcome as BoostActionOutcome.Unsupported
        assertEquals(UnavailableReasons.NO_LAUNCH_INTENT, outcome.reasonKey)
    }

    @Test
    fun launchGame_notFound_skipped() = runTest {
        val launcher = FakeGameLauncher(launchResult = GameLaunchResult.NotFound)
        val outcome = LaunchGameAction(launcher, packageName = "com.game.one")
            .execute(batterySession(level = 87, charging = false))

        assertTrue(outcome is BoostActionOutcome.Skipped)
        outcome as BoostActionOutcome.Skipped
        assertEquals(UnavailableReasons.GAME_NOT_FOUND, outcome.reasonKey)
    }

    @Test
    fun launchGame_error_failedWithReason() = runTest {
        val launcher = FakeGameLauncher(launchResult = GameLaunchResult.Error("SecurityException"))
        val outcome = LaunchGameAction(launcher, packageName = "com.game.one")
            .execute(batterySession(level = 87, charging = false))

        assertTrue(outcome is BoostActionOutcome.Failed)
        outcome as BoostActionOutcome.Failed
        assertEquals(UnavailableReasons.API_ERROR, outcome.reasonKey)
        assertEquals("SecurityException", outcome.message)
    }
}
