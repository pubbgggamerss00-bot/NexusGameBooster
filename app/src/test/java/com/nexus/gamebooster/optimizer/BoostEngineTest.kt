package com.nexus.gamebooster.optimizer

import com.nexus.gamebooster.monitoring.UnavailableReasons
import com.nexus.gamebooster.testing.batterySession
import com.nexus.gamebooster.testing.recordingAction
import com.nexus.gamebooster.testing.stubAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.coroutineContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

/**
 * Tests for [DefaultBoostEngine]'s honest status mapping, failure containment and the
 * exact cancellation contract (ensureActive between actions; CancellationException is
 * rethrown and never recorded as a Failed outcome).
 *
 * These tests ARE executed for real via Gradle (`:app:testDebugUnitTest`) in this
 * sandbox — see worklog Task 14-d for the green run.
 */
class BoostEngineTest {

    private fun engine(vararg actions: BoostAction) = DefaultBoostEngine(actions.toList())

    private fun session() = batterySession(level = 87, charging = false, profileId = "balanced")

    // ------------------------------------------------------------ status mapping

    @Test
    fun everyActionApplied_returnsCompleted() = runTest {
        val status = engine(
            stubAction("a1"),
            stubAction("a2") { BoostActionOutcome.Applied() },
        ).boost(session(), BoostRequest())
        assertEquals(BoostStatus.COMPLETED, status)
    }

    @Test
    fun alreadyAppliedCountsAsSuccess_returnsCompleted() = runTest {
        val status = engine(
            stubAction("a1") { BoostActionOutcome.AlreadyApplied() },
            stubAction("a2") { BoostActionOutcome.Applied() },
        ).boost(session(), BoostRequest())
        assertEquals(BoostStatus.COMPLETED, status)
    }

    @Test
    fun anySkipped_returnsPartialSuccess() = runTest {
        val status = engine(
            stubAction("a1"),
            stubAction("a2") { BoostActionOutcome.Skipped(UnavailableReasons.NOT_NEEDED) },
        ).boost(session(), BoostRequest())
        assertEquals(BoostStatus.PARTIAL_SUCCESS, status)
    }

    @Test
    fun anyUnsupported_returnsPartialSuccess() = runTest {
        val status = engine(
            stubAction("a1"),
            stubAction("a2") { BoostActionOutcome.Unsupported(UnavailableReasons.NO_LAUNCH_INTENT) },
        ).boost(session(), BoostRequest())
        assertEquals(BoostStatus.PARTIAL_SUCCESS, status)
    }

    @Test
    fun anyFailed_returnsFailed() = runTest {
        val status = engine(
            stubAction("a1"),
            stubAction("a2") { BoostActionOutcome.Failed(UnavailableReasons.API_ERROR, "boom") },
        ).boost(session(), BoostRequest())
        assertEquals(BoostStatus.FAILED, status)
    }

    @Test
    fun failedOutranksSkipped_returnsFailed() = runTest {
        val status = engine(
            stubAction("a1") { BoostActionOutcome.Skipped(UnavailableReasons.NOT_NEEDED) },
            stubAction("a2") { BoostActionOutcome.Failed(UnavailableReasons.API_ERROR) },
        ).boost(session(), BoostRequest())
        assertEquals(BoostStatus.FAILED, status)
    }

    @Test
    fun emptyActionList_returnsCompletedAndRecordsNothing() = runTest {
        val session = session()
        val status = engine().boost(session, BoostRequest())
        assertEquals(BoostStatus.COMPLETED, status)
        assertTrue(session.actions.isEmpty())
    }

    // ------------------------------------------------- failure containment + order

    @Test
    fun actionCrash_recordedAsFailed_runContinuesWithNextAction() = runTest {
        val executed = mutableListOf<String>()
        val session = session()
        val status = engine(
            stubAction("crasher") { throw IllegalStateException("disk on fire") },
            recordingAction("survivor", executed),
        ).boost(session, BoostRequest())

        assertEquals(BoostStatus.FAILED, status)
        assertEquals(listOf("survivor"), executed) // one broken action does not abort the run
        val failed = session.actions.single { it.actionId == "crasher" }.outcome
            as BoostActionOutcome.Failed
        assertEquals(UnavailableReasons.API_ERROR, failed.reasonKey)
        assertEquals("disk on fire", failed.message)
        // The crasher is recorded exactly once, before the survivor.
        assertEquals(listOf("crasher", "survivor"), session.actions.map { it.actionId })
    }

    @Test
    fun outcomesAreRecordedInExecutionOrder_withAllActions() = runTest {
        val session = session()
        engine(
            stubAction("one") { BoostActionOutcome.AlreadyApplied() },
            stubAction("two") { BoostActionOutcome.Skipped(UnavailableReasons.NOT_NEEDED) },
            stubAction("three"),
        ).boost(session, BoostRequest())
        assertEquals(listOf("one", "two", "three"), session.actions.map { it.actionId })
        assertTrue(session.actions[0].outcome is BoostActionOutcome.AlreadyApplied)
        assertTrue(session.actions[1].outcome is BoostActionOutcome.Skipped)
        assertTrue(session.actions[2].outcome is BoostActionOutcome.Applied)
    }

    // ------------------------------------------------------ cancellation contract

    @Test
    fun cancellationFromAction_isRethrownUntouched_andNeverRecordedAsFailed() = runTest {
        val stop = CancellationException("user pressed cancel")
        val executed = mutableListOf<String>()
        val session = session()
        try {
            engine(
                recordingAction("first", executed),
                stubAction("second") { throw stop },
            ).boost(session, BoostRequest())
            fail("engine must rethrow the CancellationException")
        } catch (expected: CancellationException) {
            assertEquals("user pressed cancel", expected.message)
        }
        // The action that threw is NOT recorded as Failed — the run was cancelled.
        assertEquals(listOf("first"), executed)
        assertEquals(listOf("first"), session.actions.map { it.actionId })
    }

    @Test
    fun ensureActiveBetweenActions_cancelledJobStopsBeforeTheNextAction() = runTest {
        val executed = mutableListOf<String>()
        val runningJob = AtomicReference<Job>()
        val session = session()

        val canceller = stubAction("canceller") {
            executed += "canceller"
            // Simulates BoostViewModel.cancelBoost(): cancels the job running boost().
            runningJob.get().cancel()
            BoostActionOutcome.Applied()
        }
        val deferred = async {
            runningJob.set(coroutineContext[Job])
            engine(canceller, recordingAction("never-runs", executed))
                .boost(session, BoostRequest())
        }
        val thrown = runCatching { deferred.await() }.exceptionOrNull()
        assertTrue("expected a CancellationException, got: $thrown", thrown is CancellationException)
        assertEquals(listOf("canceller"), executed)
        // ensureActive ran between the actions: the outcome of "canceller" was recorded
        // honestly before the stop, and "never-runs" never executed.
        assertEquals(listOf("canceller"), session.actions.map { it.actionId })
    }
}
