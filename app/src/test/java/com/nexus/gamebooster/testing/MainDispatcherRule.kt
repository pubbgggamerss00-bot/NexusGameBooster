package com.nexus.gamebooster.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Recreated after the sandbox environment wipe (Task 16-b, from the Task 14-d spec).
 * IS executed for real via Gradle: `./gradlew :app:testDebugUnitTest`.
 *
 * Replaces [Dispatchers.Main] with a shared [UnconfinedTestDispatcher] for the duration
 * of each test so every `viewModelScope.launch` runs eagerly and deterministically on
 * the test scheduler.
 *
 * SHARED-SCHEDULER CONTRACT (hard-won Task 14-d lesson): every ViewModel test MUST run
 * its body with `runTest(mainDispatcherRule.testDispatcher) { ... }` — the SAME
 * dispatcher instance this rule installed as Main. Passing a different dispatcher would
 * put viewModelScope coroutines on a separate scheduler, break virtual-time control of
 * the Monitor auto-refresh ticker, and previously caused a 60-second test hang.
 */
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
