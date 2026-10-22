package com.nexus.gamebooster.ui.screens.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.gamebooster.data.ActiveProfileStore
import com.nexus.gamebooster.profiles.PerformanceProfile
import com.nexus.gamebooster.profiles.ProfileIds
import com.nexus.gamebooster.profiles.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfilesUiState(
    val profiles: List<PerformanceProfile> = emptyList(),
    val activeProfileId: String? = null,
    /** True when the last persist attempt failed; the UI shows an inline error + retry. */
    val saveError: Boolean = false,
    /** True while a profile selection write is in flight (optimistic UI already applied). */
    val isSaving: Boolean = false,
)

/**
 * Profiles ViewModel (Phase 4).
 *
 * The three profiles are static ([ProfileRepository]); selecting one persists a
 * preference via [ActiveProfileStore] (DataStore key `active_profile`) and changes ONLY
 * NexusGameBooster's own monitoring behavior — nothing on the device is modified.
 *
 * Selection is optimistic: the chosen id shows immediately, then the write runs inside
 * `runCatching`. On failure the UI never crashes and never throws: `saveError` becomes
 * true and `activeProfileId` reverts to the last known persisted value, so the previous
 * profile stays visibly active. Retry = the user taps a profile again (or the error
 * card's Retry button); [dismissSaveError] just clears the message.
 */
class ProfilesViewModel(
    private val profileRepository: ProfileRepository,
    private val activeProfileStore: ActiveProfileStore,
) : ViewModel() {

    /** Last value observed from the persisted flow — the revert target after a failed write. */
    private var lastPersistedId: String? = null

    private val _uiState = MutableStateFlow(ProfilesUiState(profiles = profileRepository.profiles))
    val uiState: StateFlow<ProfilesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            activeProfileStore.activeProfileId.collect { persisted ->
                val normalized = ProfileIds.normalize(persisted)
                lastPersistedId = normalized
                _uiState.update { current ->
                    current.copy(
                        activeProfileId = normalized,
                        // The persisted value caught up with an optimistic selection (or
                        // arrived from elsewhere, e.g. the Home quick-select) → settled.
                        saveError = false,
                        isSaving = false,
                    )
                }
            }
        }
    }

    /**
     * Selects a profile. Invalid ids (unknown or the legacy "gaming" id — they have no
     * preset) are ignored, never persisted. Valid ids update the UI optimistically and
     * then persist; a failing write sets [ProfilesUiState.saveError] and reverts the
     * visible selection to the last known persisted value. NEVER throws.
     */
    fun selectProfile(profileId: String) {
        if (profileRepository.findById(profileId) == null) return
        _uiState.update {
            it.copy(activeProfileId = profileId, isSaving = true, saveError = false)
        }
        viewModelScope.launch {
            runCatching { activeProfileStore.setActiveProfile(profileId) }
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(saveError = false, isSaving = false)
                    }
                }
                .onFailure {
                    _uiState.update { state ->
                        state.copy(
                            saveError = true,
                            isSaving = false,
                            // Revert to the last known persisted value: the previous
                            // profile is still the one actually active.
                            activeProfileId = lastPersistedId,
                        )
                    }
                }
        }
    }

    /** Clears the inline save-error message without retrying. */
    fun dismissSaveError() {
        _uiState.update { it.copy(saveError = false) }
    }
}
