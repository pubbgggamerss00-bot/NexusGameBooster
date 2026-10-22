package com.nexus.gamebooster.ui.screens.profiles

import com.nexus.gamebooster.profiles.ProfileIds
import com.nexus.gamebooster.profiles.StaticProfileRepository
import com.nexus.gamebooster.testing.FakeActiveProfileStore
import com.nexus.gamebooster.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * JVM tests for [ProfilesViewModel] (Phase 4 contract): optimistic selection +
 * persistence, honest save-error with rollback + working retry, legacy id normalization
 * on load, invalid ids ignored.
 *
 * SHARED-SCHEDULER CONTRACT: every test uses `runTest(mainDispatcherRule.testDispatcher)`
 * (see [MainDispatcherRule]) — the Phase 6 lesson about mismatched schedulers.
 *
 * These tests ARE executed for real via Gradle (`:app:testDebugUnitTest`) in this
 * sandbox — see worklog Task 14-d for the green run.
 */
class ProfilesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun vm(store: FakeActiveProfileStore) =
        ProfilesViewModel(StaticProfileRepository(), store)

    @Test
    fun init_listsTheThreePresets_andLoadsThePersistedProfile() = runTest(mainDispatcherRule.testDispatcher) {
        val store = FakeActiveProfileStore(ProfileIds.PERFORMANCE)
        val viewModel = vm(store)
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(
            listOf(ProfileIds.BALANCED, ProfileIds.PERFORMANCE, ProfileIds.BATTERY_SAVER),
            state.profiles.map { it.id },
        )
        assertEquals(ProfileIds.PERFORMANCE, state.activeProfileId)
        assertFalse(state.saveError)
        assertFalse(state.isSaving)
    }

    @Test
    fun init_legacyGamingId_isNormalizedToBalanced() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = vm(FakeActiveProfileStore(ProfileIds.LEGACY_GAMING))
        runCurrent()
        assertEquals(ProfileIds.BALANCED, viewModel.uiState.value.activeProfileId)
    }

    @Test
    fun selectProfile_updatesOptimistically_thenPersists() = runTest(mainDispatcherRule.testDispatcher) {
        val store = FakeActiveProfileStore(ProfileIds.BALANCED)
        val viewModel = vm(store)
        runCurrent()

        viewModel.selectProfile(ProfileIds.PERFORMANCE)
        // Optimistic: the chosen profile shows immediately, before any write completes.
        assertEquals(ProfileIds.PERFORMANCE, viewModel.uiState.value.activeProfileId)
        assertTrue(viewModel.uiState.value.isSaving)

        advanceUntilIdle()
        assertEquals(ProfileIds.PERFORMANCE, store.written)
        assertFalse(viewModel.uiState.value.isSaving)
        assertFalse(viewModel.uiState.value.saveError)
        assertEquals(ProfileIds.PERFORMANCE, viewModel.uiState.value.activeProfileId)
    }

    @Test
    fun selectProfile_invalidIdsAreIgnored_neverPersisted() = runTest(mainDispatcherRule.testDispatcher) {
        val store = FakeActiveProfileStore(ProfileIds.BALANCED)
        val viewModel = vm(store)
        runCurrent()

        viewModel.selectProfile(ProfileIds.LEGACY_GAMING) // no preset behind "gaming"
        viewModel.selectProfile("turbo")
        runCurrent()

        assertEquals(null, store.written)
        assertEquals(ProfileIds.BALANCED, viewModel.uiState.value.activeProfileId)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun persistFailure_setsSaveError_revertsToLastPersisted_thenRetryWorks() =
        runTest(mainDispatcherRule.testDispatcher) {
            val store = FakeActiveProfileStore(ProfileIds.BALANCED)
            val viewModel = vm(store)
            runCurrent()

            store.failWrites = true
            viewModel.selectProfile(ProfileIds.PERFORMANCE)
            assertEquals("optimistic selection shows first", ProfileIds.PERFORMANCE, viewModel.uiState.value.activeProfileId)
            runCurrent() // the write fails here

            assertTrue(viewModel.uiState.value.saveError)
            assertFalse(viewModel.uiState.value.isSaving)
            assertEquals("visible selection reverts to the still-active persisted profile",
                ProfileIds.BALANCED, viewModel.uiState.value.activeProfileId)
            assertEquals(null, store.written)

            // Retry = the user taps again (or the error card's Retry button) once the
            // cause is gone: the same selection now succeeds and clears the error.
            store.failWrites = false
            viewModel.selectProfile(ProfileIds.PERFORMANCE)
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.saveError)
            assertEquals(ProfileIds.PERFORMANCE, viewModel.uiState.value.activeProfileId)
            assertEquals(ProfileIds.PERFORMANCE, store.written)
        }

    @Test
    fun dismissSaveError_clearsTheFlagWithoutRetrying() = runTest(mainDispatcherRule.testDispatcher) {
        val store = FakeActiveProfileStore(ProfileIds.BALANCED).apply { failWrites = true }
        val viewModel = vm(store)
        runCurrent()

        viewModel.selectProfile(ProfileIds.PERFORMANCE)
        runCurrent()
        assertTrue(viewModel.uiState.value.saveError)

        viewModel.dismissSaveError()
        assertFalse(viewModel.uiState.value.saveError)
        assertEquals(ProfileIds.BALANCED, viewModel.uiState.value.activeProfileId)
    }

    @Test
    fun persistedValueFromElsewhere_settlesOptimisticState_andClearsErrors() =
        runTest(mainDispatcherRule.testDispatcher) {
            // Mirrors the Home quick-select writing through the SAME store: the Profiles
            // VM's collector treats the incoming persisted value as settled truth.
            val store = FakeActiveProfileStore(ProfileIds.BALANCED)
            val viewModel = vm(store)
            runCurrent()

            viewModel.selectProfile(ProfileIds.BATTERY_SAVER)
            advanceUntilIdle()
            assertEquals(ProfileIds.BATTERY_SAVER, viewModel.uiState.value.activeProfileId)

            // Another surface (Home) selects Performance through the same store.
            store.setActiveProfile(ProfileIds.PERFORMANCE)
            advanceUntilIdle()
            assertEquals(ProfileIds.PERFORMANCE, viewModel.uiState.value.activeProfileId)
            assertFalse(viewModel.uiState.value.saveError)
            assertFalse(viewModel.uiState.value.isSaving)
        }
}
