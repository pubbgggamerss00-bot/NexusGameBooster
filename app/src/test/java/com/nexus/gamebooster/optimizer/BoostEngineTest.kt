package com.nexus.gamebooster.optimizer

import com.nexus.gamebooster.monitoring.UnavailableReasons
import com.nexus.gamebooster.testing.RecordingAction
import com.nexus.gamebooster.testing.stubAction
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Recreated after the sandbox environment wipe (Task 16-b, from the Task 14-d spec).
 * IS executed for real via Gradle: `./gradlew :app:testDebugUnitTest`.
 *
 * [DefaultBoostEngine] contracts: the honest status mapping (AlreadyApplied counts as
 * success, Failed outranks Skipped), empty action list, a crashing action recorded as
 * Failed(api_error) without aborting the run, execution-order records, and the exact
 * cancellation contract — CancellationException rethrown untouched (never recorded as
 * Failed) and ensureActive BETWEEN actions verified with a genuinely cancelled job.
 */
class BoostEngineTest {

    // ------------------------------------------------------------ status mapping

    @Test
    fun allActionsApplied_reportsCompleted() = runTest {
        val engine = DefaultBoostEngine(
            listOf(
                stubAction("a1", BoostActionOutcome.Applied()),
                stubAction("a2", BoostActionOutcome.Applied()),
            )
        )
        val session = BoostSession(emptyList(), null, null)
        assertEquals(BoostStatus.COMPLETED, engine.boost(session, BoostRequest()))
        assertEquals(2, session.actions.size)
    }

    @Test
    fun alreadyApplied_countsAsSuccess_reportsCompleted() = runTest {
        val engine = DefaultBoostEngine(
            listOf(
                stubAction("a1", BoostActionOutcome.AlreadyApplied()),
                stubAction("a2", BoostActionOutcome.Applied()),
            )
        )
        val session = BoostSession(emptyList(), null, null)
        assertEquals(BoostStatus.COMPLETED, engine.boost(session, BoostRequest()))
        assertTrue(session.actions[0].outcome is BoostActionOutcome.AlreadyApplied)
    }

    @Test
    fun skippedOutcome_reportsPartialSuccess() = runTest {
        val engine = DefaultBoostEngine(
            listOf(
                stubAction("a1", BoostActionOutcome.Applied()),
                stubAction("a2", BoostActionOutcome.Skipped(UnavailableReasons.BATTERY_HEALTHY)),
            )
        )
        val session = BoostSession(emptyList(), null, null)
        assertEquals(BoostStatus.PARTIAL_SUCCESS, engine.boost(session, BoostRequest()))
    }

    @Test
    fun unsupportedOutcome_reportsPartialSuccess() = runTest {
        val engine = DefaultBoostEngine(
            listOf(
                stubAction("a1", BoostActionOutcome.Unsupported(UnavailableReasons.NO_LAUNCH_INTENT)),
            )
        )
        val session = BoostSession(emptyList(), null, null)
        assertEquals(BoostStatus.PARTIAL_SUCCESS, engine.boost(session, BoostRequest()))
    }

    @Test
    fun failedOutcome_reportsFailed() = runTest {
        val engine = DefaultBoostEngine(
            listOf(
                stubAction("a1", BoostActionOutcome.Failed(UnavailableReasons.API_ERROR, message = "boom")),
            )
        )
        val session = BoostSession(emptyList(), null, null)
        assertEquals(BoostStatus.FAILED, engine.boost(session, BoostRequest()))
    }

    @Test
    fun failed_outranks_skipped() = runTest {
        val engine = DefaultBoostEngine(
            listOf(
                stubAction("a1", BoostActionOutcome.Skipped(UnavailableReasons.NOT_NEEDED)),
                stubAction("a2", BoostActionOutcome.Failed(UnavailableReasons.API_ERROR)),
            )
        )
        val session = BoostSession(emptyList(), null, null)
        assertEquals(BoostStatus.FAILED, engine.boost(session, BoostRequest()))
        assertEquals(2, session.actions.size)
    }

    @Test
    fun emptyActionList_reportsCompleted() = runTest {
        val engine = DefaultBoostEngine(emptyList())
        val session = BoostSession(emptyList(), null, null)
        assertEquals(BoostStatus.COMPLETED, engine.boost(session, BoostRequest()))
        assertTrue(session.actions.isEmpty())
    }

    // ------------------------------------------------------------ failure safety

    @Test
    fun crashingAction_recordedAsFailed_andRunContinues() = runTest {
        val crasher = stubAction("crasher", BoostActionOutcome.Applied()) {
            throw IllegalStateException("disk on fire")
        }
        val after = RecordingAction("after")
        val engine = DefaultBoostEngine(listOf(crasher, after))
        val session = BoostSession(emptyList(), null, null)

        assertEquals(BoostStatus.FAILED, engine.boost(session, BoostRequest()))
        assertTrue(after.executed)
        val failedRecord = session.actions.first { it.actionId == "crasher" }
        val failed = failedRecord.outcome as BoostActionOutcome.Failed
        assertEquals(UnavailableReasons.API_ERROR, failed.reasonKey)
        assertEquals("disk on fire", failed.message)
        assertEquals("after", session.actions.last().actionId)
    }

    // -------------------------------------------------------------- cancellation

    @Test
    fun cancellationException_rethrownUntouched_neverRecordedAsFailed() = runTest {
        val canceller = stubAction("cancel_me", BoostActionOutcome.Applied()) {
            throw CancellationException("user cancelled the boost")
        }
        val after = RecordingAction("after")
        val engine = DefaultBoostEngine(listOf(canceller, after))
        val session = BoostSession(emptyList(), null, null)

        var caught: Throwable? = null
        try {
            engine.boost(session, BoostRequest())
        } catch (t: Throwable) {
            caught = t
        }
        assertTrue(caught is CancellationException)
        // Not recorded at all — a cancelled run must not claim a Failed outcome.
        assertTrue(session.actions.isEmpty())
        assertFalse(after.executed)
    }

    // ------------------------------------------------------------ record ordering

    @Test
    fun executionOrder_isRecordedInListOrder() = runTest {
        val a1 = RecordingAction("a1")
        val a2 = RecordingAction("a2")
        val a3 = RecordingAction("a3")
        val engine = DefaultBoostEngine(listOf(a1, a2, a3))
        val session = BoostSession(emptyList(), null, null)

        assertEquals(BoostStatus.COMPLETED, engine.boost(session, BoostRequest()))
        assertEquals(listOf("a1", "a2", "a3"), session.actions.map { it.actionId })
        assertTrue(a1.executed && a2.executed && a3.executed)
    }

    @Test
    fun ensureActiveBetweenActions_genuinelyCancelledJobStopsBeforeNextAction() = runTest {
        val after = RecordingAction("after")
        // The canceller cancels the job the ENGINE runs on — resolved inside a real
        // suspend context so it is the child job below, never the enclosing TestScope
        // (a non-suspend lambda would capture the test's context and cancel THAT).
        val canceller = object : BoostAction {
            override val id: String = "canceller"
            override suspend fun execute(session: BoostSession): BoostActionOutcome {
                currentCoroutineContext().cancel()
                return BoostActionOutcome.Applied()
            }
        }
        val engine = DefaultBoostEngine(listOf(canceller, after))
        val session = BoostSession(emptyList(), null, null)

        // Run the engine on a CHILD job so cancelling it does not cancel the test body.
        val deferred = async(start = CoroutineStart.UNDISPATCHED) {
            engine.boost(session, BoostRequest())
        }
        deferred.join()

        assertTrue(deferred.isCancelled)
        assertFalse(after.executed)
        // The canceller's own outcome was recorded (record happens after its execute);
        // the NEXT action never ran because ensureActive sits between actions.
        assertEquals(listOf("canceller"), session.actions.map { it.actionId })
    }
}
