package com.nexus.gamebooster.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit rule that swaps `Dispatchers.Main` for a single [TestDispatcher] so ViewModel
 * code (`viewModelScope` uses `Dispatchers.Main.immediate`) runs under the SAME virtual
 * time scheduler the test body uses.
 *
 * SHARED-SCHEDULER CONTRACT (hard-won in Phase 6, task 13): every ViewModel test MUST
 * start its body with `runTest(mainDispatcherRule.testDispatcher) { ... }` — NOT a bare
 * `runTest { }`. A bare `runTest` creates a SECOND scheduler; the VM's coroutines then
 * live on a different clock than the test body, which produced a flaky 60-second hang
 * in Phase 6 before this rule was introduced.
 *
 * These tests are executed for real via Gradle (`:app:testDebugUnitTest`) in this
 * sandbox — see worklog Task 14-d.
 */
class MainDispatcherRule(
    /** The dispatcher tests MUST pass to `runTest(...)` so both sides share one scheduler. */
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
