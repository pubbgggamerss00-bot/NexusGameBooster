package com.nexus.gamebooster.ui.screens.profiles

import androidx.lifecycle.viewModelScope
import com.nexus.gamebooster.profiles.ProfileIds
import com.nexus.gamebooster.profiles.StaticProfileRepository
import com.nexus.gamebooster.testing.FakeActiveProfileStore
import com.nexus.gamebooster.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Recreated after the sandbox environment wipe (Task 16-b missed this class;
 * restored by Task 17 from the Task 14-d spec). IS executed for real via Gradle:
 * `./gradlew :app:testDebugUnitTest`.
 *
 * All tests run under VIRTUAL TIME with the shared Main dispatcher. The init
 * block collects the store flow forever, so cancelling `vm.viewModelScope` is
 * the LAST statement of every test body (the Task 14-d hang rule), and state is
 * always awaited with `uiState.first { ... }` — no sleeps anywhere.
 */
class ProfilesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var store: FakeActiveProfileStore

    @Before
    fun setUp() {
        store = FakeActiveProfileStore(ProfileIds.BALANCED)
    }

    private fun newViewModel(initialStore: FakeActiveProfileStore = store): ProfilesViewModel =
        ProfilesViewModel(
            profileRepository = StaticProfileRepository(),
            activeProfileStore = initialStore,
        )

    /** MUST be the last statement of every test body (the init collector runs forever). */
    private fun shutdownForTest(vm: ProfilesViewModel) {
        vm.viewModelScope.cancel()
    }

    // ------------------------------------------------------------- init / catalog

    @Test
    fun init_listsTheThreePresets_andLoadsThePersistedProfileClean() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = newViewModel()
        val state = vm.uiState.first { it.activeProfileId != null }

        assertEquals(
            listOf(ProfileIds.BALANCED, ProfileIds.PERFORMANCE, ProfileIds.BATTERY_SAVER),
            state.profiles.map { it.id },
        )
        assertEquals(ProfileIds.BALANCED, state.activeProfileId)
        assertFalse(state.saveError)
        assertFalse(state.isSaving)
        shutdownForTest(vm)
    }

    @Test
    fun legacyGamingId_normalizedToBalanced() = runTest(mainDispatcherRule.testDispatcher) {
        // The fake store deliberately emits the RAW legacy value: the VM must map it.
        val legacyStore = FakeActiveProfileStore(ProfileIds.LEGACY_GAMING)
        val vm = newViewModel(legacyStore)

        val state = vm.uiState.first { it.activeProfileId != null }
        assertEquals(ProfileIds.BALANCED, state.activeProfileId)
        shutdownForTest(vm)
    }

    // ------------------------------------------------------------------ selection

    @Test
    fun select_isOptimistic_thenSettlesAfterThePersistedWrite() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = newViewModel()
        vm.uiState.first { it.activeProfileId != null }

        // Main is UNCONFINED (the shared rule), so the launched persist runs eagerly.
        // Hold it on the one-shot gate: the synchronous optimistic update (selected id
        // + isSaving) stays visible while the write is still in flight.
        val gate = CompletableDeferred<Unit>()
        store.writeGate = gate
        vm.selectProfile(ProfileIds.PERFORMANCE)
        assertEquals(ProfileIds.PERFORMANCE, vm.uiState.value.activeProfileId)
        assertTrue(vm.uiState.value.isSaving)

        gate.complete(Unit)
        val state = vm.uiState.first { !it.isSaving }
        assertEquals(ProfileIds.PERFORMANCE, state.activeProfileId)
        assertFalse(state.saveError)
        assertEquals(1, store.writes)
        shutdownForTest(vm)
    }

    @Test
    fun invalidIds_ignored_noWriteNoStateChange() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = newViewModel()
        vm.uiState.first { it.activeProfileId != null }

        // The legacy "gaming" id and a plain unknown id have NO preset — both ignored.
        vm.selectProfile(ProfileIds.LEGACY_GAMING)
        vm.selectProfile("com.unknown.profile")

        val state = vm.uiState.value
        assertEquals(ProfileIds.BALANCED, state.activeProfileId)
        assertFalse(state.isSaving)
        assertFalse(state.saveError)
        assertEquals(0, store.writes)
        shutdownForTest(vm)
    }

    @Test
    fun persistFailure_saveErrorAndRevert_thenRetryWorks() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = newViewModel()
        vm.uiState.first { it.activeProfileId != null }

        store.failWrites = true
        vm.selectProfile(ProfileIds.PERFORMANCE)
        val failed = vm.uiState.first { it.saveError }
        // The previous profile stays visibly active — honest revert, never a blank.
        assertEquals(ProfileIds.BALANCED, failed.activeProfileId)
        assertFalse(failed.isSaving)
        assertEquals(1, store.writes)

        // Retry = select again with the store healthy: error clears and it settles.
        store.failWrites = false
        vm.selectProfile(ProfileIds.PERFORMANCE)
        val retried = vm.uiState.first { it.activeProfileId == ProfileIds.PERFORMANCE && !it.isSaving }
        assertFalse(retried.saveError)
        assertEquals(2, store.writes)
        shutdownForTest(vm)
    }

    @Test
    fun dismissSaveError_clearsTheFlag_onlyTheFlag() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = newViewModel()
        vm.uiState.first { it.activeProfileId != null }

        store.failWrites = true
        vm.selectProfile(ProfileIds.PERFORMANCE)
        vm.uiState.first { it.saveError }

        vm.dismissSaveError()

        assertFalse(vm.uiState.value.saveError)
        // Dismissing is NOT retrying: the reverted selection stays put.
        assertEquals(ProfileIds.BALANCED, vm.uiState.value.activeProfileId)
        shutdownForTest(vm)
    }

    @Test
    fun externalStoreWrite_settlesTheOptimisticState() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = newViewModel()
        vm.uiState.first { it.activeProfileId != null }

        // Hold THIS VM's own write on the one-shot gate, then write from "outside"
        // (e.g. the Home quick-select writing the same store): the collector must
        // settle the optimistic state even before the VM's own write finishes.
        val gate = CompletableDeferred<Unit>()
        store.writeGate = gate
        vm.selectProfile(ProfileIds.PERFORMANCE)
        runCurrent() // let the launched write consume the gate and suspend there

        store.setActiveProfile(ProfileIds.BATTERY_SAVER)
        val settled = vm.uiState.first { it.activeProfileId == ProfileIds.BATTERY_SAVER }
        assertFalse(settled.isSaving)
        assertFalse(settled.saveError)

        // Releasing the gate lets the VM's own (older) write land last: PERFORMANCE.
        gate.complete(Unit)
        val final = vm.uiState.first { it.activeProfileId == ProfileIds.PERFORMANCE && !it.isSaving }
        assertFalse(final.saveError)
        assertEquals(2, store.writes)
        shutdownForTest(vm)
    }
}
