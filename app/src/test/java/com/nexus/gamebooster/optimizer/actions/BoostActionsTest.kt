package com.nexus.gamebooster.optimizer.actions

import android.content.ContextWrapper
import com.nexus.gamebooster.core.SessionMonitorController
import com.nexus.gamebooster.core.SessionMonitorMode
import com.nexus.gamebooster.games.GameLaunchResult
import com.nexus.gamebooster.monitoring.UnavailableReasons
import com.nexus.gamebooster.optimizer.BoostActionOutcome
import com.nexus.gamebooster.optimizer.BoostDetailKeys
import com.nexus.gamebooster.optimizer.BoostSession
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
 * Recreated after the sandbox environment wipe (Task 16-b, from the Task 14-d spec).
 * IS executed for real via Gradle: `./gradlew :app:testDebugUnitTest`.
 *
 * HONEST SCOPE (Task 14-d measured limit, deliberately kept): the Context-dependent
 * intent CHAINS of OpenBatterySettingsAction / OpenGameDetailsAction are NOT tested
 * directly — with `isReturnDefaultValues = true` the mockable android.jar returns null
 * from Intent.setData/addFlags, so the chain NPEs BEFORE startActivity ("Cannot invoke
 * Intent.addFlags(int) because the return value of Intent.setData(android.net.Uri) is
 * null"). That is mock behavior, not a main-source bug (on-device the framework returns
 * `this`); the honest consent seam is BoostExplicitActions (covered via
 * BoostViewModelTest). Do NOT re-add those four tests.
 *
 * Covered here: ApplyProfileAction (4), SessionMonitoringAction (6),
 * PrepareGameLaunchAction (4), OpenGameDetailsAction's null-package guard (1) — the
 * guard returns BEFORE any context/intent use, so a bare ContextWrapper suffices — and
 * LaunchGameAction (4). Total: the exact Phase 6 count of 19.
 */
class BoostActionsTest {

    // -------------------------------------------------------- ApplyProfileAction

    @Test
    fun applyProfile_validStoredId_alreadyApplied_withoutWriting() = runTest {
        val store = FakeActiveProfileStore(ProfileIds.PERFORMANCE)
        val action = ApplyProfileAction(StaticProfileRepository(), store)

        val outcome = action.execute(BoostSession(emptyList(), null, null))

        assertTrue(outcome is BoostActionOutcome.AlreadyApplied)
        assertEquals(0, store.writes)
        assertEquals(ProfileIds.PERFORMANCE, store.activeProfileId.first())
    }

    @Test
    fun applyProfile_legacyGaming_normalizedAndRewrittenAsBalanced() = runTest {
        val store = FakeActiveProfileStore(ProfileIds.LEGACY_GAMING)
        val action = ApplyProfileAction(StaticProfileRepository(), store)

        val outcome = action.execute(BoostSession(emptyList(), null, null))

        assertTrue(outcome is BoostActionOutcome.Applied)
        assertEquals(1, store.writes)
        assertEquals(ProfileIds.BALANCED, store.activeProfileId.first())
    }

    @Test
    fun applyProfile_unknownId_normalizedAndRewrittenAsBalanced() = runTest {
        val store = FakeActiveProfileStore("vendor_turbo_mode")
        val action = ApplyProfileAction(StaticProfileRepository(), store)

        val outcome = action.execute(BoostSession(emptyList(), null, null))

        assertTrue(outcome is BoostActionOutcome.Applied)
        assertEquals(ProfileIds.BALANCED, store.activeProfileId.first())
    }

    @Test
    fun applyProfile_storeError_failedWithApiError_andNothingWritten() = runTest {
        val store = FakeActiveProfileStore(ProfileIds.LEGACY_GAMING)
        store.failWrites = true
        val action = ApplyProfileAction(StaticProfileRepository(), store)

        val outcome = action.execute(BoostSession(emptyList(), null, null))

        val failed = outcome as BoostActionOutcome.Failed
        assertEquals(UnavailableReasons.API_ERROR, failed.reasonKey)
        assertEquals(ProfileIds.LEGACY_GAMING, store.activeProfileId.first())
    }

    // --------------------------------------------------- SessionMonitoringAction

    @Test
    fun sessionMonitoring_lowBatteryOnBatteryPower_applied_andEfficientModeSet() = runTest {
        val controller = SessionMonitorController()
        val action = SessionMonitoringAction(controller)

        val outcome = action.execute(batterySession(levelPercent = 15, charging = false, profileId = ProfileIds.BALANCED))

        assertTrue(outcome is BoostActionOutcome.Applied)
        assertEquals(SessionMonitorMode.EFFICIENT, controller.mode.value)
    }

    @Test
    fun sessionMonitoring_twentyPercent_isStillLowEnough_applied() = runTest {
        val controller = SessionMonitorController()
        val action = SessionMonitoringAction(controller)

        val outcome = action.execute(batterySession(levelPercent = 20, charging = false, profileId = ProfileIds.BALANCED))

        assertTrue(outcome is BoostActionOutcome.Applied)
        assertEquals(SessionMonitorMode.EFFICIENT, controller.mode.value)
    }

    @Test
    fun sessionMonitoring_charging_skippedAsNotNeeded_modeStaysNormal() = runTest {
        val controller = SessionMonitorController()
        val action = SessionMonitoringAction(controller)

        val outcome = action.execute(batterySession(levelPercent = 10, charging = true, profileId = ProfileIds.BALANCED))

        val skipped = outcome as BoostActionOutcome.Skipped
        assertEquals(UnavailableReasons.NOT_NEEDED, skipped.reasonKey)
        assertEquals(SessionMonitorMode.NORMAL, controller.mode.value)
    }

    @Test
    fun sessionMonitoring_batterySaverProfile_skippedAsAlreadyEfficient() = runTest {
        val controller = SessionMonitorController()
        val action = SessionMonitoringAction(controller)

        val outcome = action.execute(
            batterySession(levelPercent = 10, charging = false, profileId = ProfileIds.BATTERY_SAVER)
        )

        val skipped = outcome as BoostActionOutcome.Skipped
        assertEquals(UnavailableReasons.ALREADY_EFFICIENT, skipped.reasonKey)
        assertEquals(SessionMonitorMode.NORMAL, controller.mode.value)
    }

    @Test
    fun sessionMonitoring_healthyBattery_skippedAsBatteryHealthy() = runTest {
        val controller = SessionMonitorController()
        val action = SessionMonitoringAction(controller)

        val outcome = action.execute(batterySession(levelPercent = 80, charging = false, profileId = ProfileIds.BALANCED))

        val skipped = outcome as BoostActionOutcome.Skipped
        assertEquals(UnavailableReasons.BATTERY_HEALTHY, skipped.reasonKey)
        assertEquals(SessionMonitorMode.NORMAL, controller.mode.value)
    }

    @Test
    fun sessionMonitoring_unreadableBattery_skippedAsBatteryUnavailable() = runTest {
        val controller = SessionMonitorController()
        val action = SessionMonitoringAction(controller)

        // No typed payload at all — the honest "cannot decide" path, never a guess.
        val outcome = action.execute(batterySession(levelPercent = null, charging = null, profileId = ProfileIds.BALANCED))

        val skipped = outcome as BoostActionOutcome.Skipped
        assertEquals(UnavailableReasons.BATTERY_UNAVAILABLE, skipped.reasonKey)
        assertEquals(SessionMonitorMode.NORMAL, controller.mode.value)
    }

    // --------------------------------------------------- PrepareGameLaunchAction

    @Test
    fun prepareLaunch_nullPackage_skippedAsNoGameSelected_withoutResolving() = runTest {
        val launcher = FakeGameLauncher()
        val action = PrepareGameLaunchAction(launcher, packageName = null)

        val outcome = action.execute(BoostSession(emptyList(), null, null))

        val skipped = outcome as BoostActionOutcome.Skipped
        assertEquals(UnavailableReasons.NO_GAME_SELECTED, skipped.reasonKey)
        assertTrue(launcher.resolveCalls.isEmpty())
    }

    @Test
    fun prepareLaunch_ready_appliedWithReadyToLaunchDetail_resolveNotLaunch() = runTest {
        val launcher = FakeGameLauncher(
            resolveResults = mapOf("com.example.game" to GameLaunchResult.Ready),
        )
        val action = PrepareGameLaunchAction(launcher, packageName = "com.example.game")

        val outcome = action.execute(BoostSession(emptyList(), null, null))

        val applied = outcome as BoostActionOutcome.Applied
        assertEquals(BoostDetailKeys.READY_TO_LAUNCH, applied.detailKey)
        // The resolve verb ran; NOTHING was launched (launch stays a user-explicit tap).
        assertEquals(listOf("com.example.game"), launcher.resolveCalls)
        assertTrue(launcher.launchCalls.isEmpty())
    }

    @Test
    fun prepareLaunch_noLaunchIntent_unsupported() = runTest {
        val launcher = FakeGameLauncher(
            resolveResults = mapOf("com.example.game" to GameLaunchResult.NoLaunchIntent),
        )
        val action = PrepareGameLaunchAction(launcher, packageName = "com.example.game")

        val outcome = action.execute(BoostSession(emptyList(), null, null))

        val unsupported = outcome as BoostActionOutcome.Unsupported
        assertEquals(UnavailableReasons.NO_LAUNCH_INTENT, unsupported.reasonKey)
    }

    @Test
    fun prepareLaunch_notFound_skippedAsGameNotFound() = runTest {
        val launcher = FakeGameLauncher(
            resolveResults = mapOf("com.example.game" to GameLaunchResult.NotFound),
        )
        val action = PrepareGameLaunchAction(launcher, packageName = "com.example.game")

        val outcome = action.execute(BoostSession(emptyList(), null, null))

        val skipped = outcome as BoostActionOutcome.Skipped
        assertEquals(UnavailableReasons.GAME_NOT_FOUND, skipped.reasonKey)
    }

    // --------------------------------------------------- OpenGameDetailsAction
    // (null-package guard only — see the class KDoc for the honest intent-chain limit)

    @Test
    fun openGameDetails_nullPackage_skippedAsNoGameSelected_withoutBuildingIntent() = runTest {
        // The guard returns before ANY context/intent use, so a bare wrapper suffices
        // on the mockable android.jar.
        val action = OpenGameDetailsAction(ContextWrapper(null), packageName = null)

        val outcome = action.execute(BoostSession(emptyList(), null, null))

        val skipped = outcome as BoostActionOutcome.Skipped
        assertEquals(UnavailableReasons.NO_GAME_SELECTED, skipped.reasonKey)
    }

    // ------------------------------------------------------------ LaunchGameAction

    @Test
    fun launchGame_launched_applied() = runTest {
        val launcher = FakeGameLauncher(
            launchResults = mapOf("com.example.game" to GameLaunchResult.Launched),
        )
        val action = LaunchGameAction(launcher, packageName = "com.example.game")

        val outcome = action.execute(BoostSession(emptyList(), null, null))

        assertTrue(outcome is BoostActionOutcome.Applied)
        assertEquals(listOf("com.example.game"), launcher.launchCalls)
    }

    @Test
    fun launchGame_noLaunchIntent_unsupported() = runTest {
        val launcher = FakeGameLauncher(
            launchResults = mapOf("com.example.game" to GameLaunchResult.NoLaunchIntent),
        )
        val action = LaunchGameAction(launcher, packageName = "com.example.game")

        val outcome = action.execute(BoostSession(emptyList(), null, null))

        val unsupported = outcome as BoostActionOutcome.Unsupported
        assertEquals(UnavailableReasons.NO_LAUNCH_INTENT, unsupported.reasonKey)
    }

    @Test
    fun launchGame_notFound_skippedAsGameNotFound() = runTest {
        val launcher = FakeGameLauncher(
            launchResults = mapOf("com.example.game" to GameLaunchResult.NotFound),
        )
        val action = LaunchGameAction(launcher, packageName = "com.example.game")

        val outcome = action.execute(BoostSession(emptyList(), null, null))

        val skipped = outcome as BoostActionOutcome.Skipped
        assertEquals(UnavailableReasons.GAME_NOT_FOUND, skipped.reasonKey)
    }

    @Test
    fun launchGame_error_failedWithApiErrorAndTechnicalReason() = runTest {
        val launcher = FakeGameLauncher(
            launchResults = mapOf("com.example.game" to GameLaunchResult.Error("SecurityException")),
        )
        val action = LaunchGameAction(launcher, packageName = "com.example.game")

        val outcome = action.execute(BoostSession(emptyList(), null, null))

        val failed = outcome as BoostActionOutcome.Failed
        assertEquals(UnavailableReasons.API_ERROR, failed.reasonKey)
        assertEquals("SecurityException", failed.message)
    }
}
