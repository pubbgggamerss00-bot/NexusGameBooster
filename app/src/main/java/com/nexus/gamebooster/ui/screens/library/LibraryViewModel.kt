package com.nexus.gamebooster.ui.screens.library

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.gamebooster.R
import com.nexus.gamebooster.core.UiMessage
import com.nexus.gamebooster.games.Game
import com.nexus.gamebooster.games.GameLaunchResult
import com.nexus.gamebooster.games.GameRepository
import com.nexus.gamebooster.games.GameScanResult
import com.nexus.gamebooster.games.GameSort
import com.nexus.gamebooster.games.InstalledApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Which tab is visible in the Library screen. */
enum class LibraryTab { MY_GAMES, DISCOVER }

/** Discover sub-filter: device-declared games, or every launchable app. */
enum class DiscoverFilter { GAMES, ALL_APPS }

data class LibraryUiState(
    val tab: LibraryTab = LibraryTab.MY_GAMES,
    val discoverFilter: DiscoverFilter = DiscoverFilter.GAMES,
    val searchQuery: String = "",
    val sort: GameSort = GameSort.NAME_ASC,
    // Raw sources (kept here so derived views below recompute consistently).
    val allGames: List<Game> = emptyList(),
    val installedApps: List<InstalledApp> = emptyList(),
    val isRefreshing: Boolean = false,
    val hasScannedOnce: Boolean = false,
    /** Stable reason key of the last failed scan, or null. Drives the Discover error state. */
    val scanFailedReason: String? = null,
    /** Derived: user's library after search + sort. */
    val myGames: List<Game> = emptyList(),
    /** Derived: discover list after filter + search + sort. */
    val discoverApps: List<InstalledApp> = emptyList(),
    /** Packages present in the library (drives the +/✓ state in Discover). */
    val selectedPackages: Set<String> = emptySet(),
    // Dialog / message state
    val addDialogVisible: Boolean = false,
    val removeTarget: Game? = null,
    val detailsTarget: Game? = null,
    val message: UiMessage? = null,
)

/**
 * Library ViewModel (Phase 2). The repository does the real work (PackageManager
 * scan, DataStore persistence, launching); this class only orchestrates UI state:
 * search/sort/tab/filter, dialogs, icon cache and snackbar messages.
 *
 * The scan runs once on init and again via [refresh]. Real app icons are loaded
 * lazily through [iconFor] and cached (including null results) per package.
 */
class LibraryViewModel(
    private val gameRepository: GameRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val messageId = AtomicLong(0L)
    private val iconCache = ConcurrentHashMap<String, ImageBitmap?>()

    init {
        viewModelScope.launch {
            gameRepository.games.collect { games ->
                updateState { it.copy(allGames = games) }
            }
        }
        viewModelScope.launch {
            gameRepository.installedApps.collect { apps ->
                updateState { it.copy(installedApps = apps) }
            }
        }
        refresh()
    }

    // ------------------------------------------------------------------ actions

    /** Real rescan of launchable apps (pull-to-refresh replacement: refresh button). */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { recompute(it.copy(isRefreshing = true)) }
            when (val result = gameRepository.refreshInstalledApps()) {
                is GameScanResult.Success -> _uiState.update {
                    recompute(
                        it.copy(isRefreshing = false, hasScannedOnce = true, scanFailedReason = null)
                    )
                }

                is GameScanResult.Failed -> _uiState.update {
                    recompute(
                        it.copy(
                            isRefreshing = false,
                            hasScannedOnce = true,
                            scanFailedReason = result.reason,
                        )
                    )
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        updateState { it.copy(searchQuery = query) }
    }

    fun setSort(sort: GameSort) {
        updateState { it.copy(sort = sort) }
    }

    fun selectTab(tab: LibraryTab) {
        updateState { it.copy(tab = tab) }
    }

    fun setDiscoverFilter(filter: DiscoverFilter) {
        updateState { it.copy(discoverFilter = filter) }
    }

    /** Launch via the repository; every outcome maps to an honest snackbar message. */
    fun launch(game: Game) {
        val packageName = game.packageName ?: return
        viewModelScope.launch {
            val result = gameRepository.launch(packageName)
            showMessage(
                when (result) {
                    is GameLaunchResult.Launched -> UiMessage(nextMessageId(), R.string.library_launching)
                    // launch() never returns Ready (the contract is resolve-only), but an
                    // alternative GameLauncher may; the honest message says nothing started.
                    is GameLaunchResult.Ready -> UiMessage(nextMessageId(), R.string.library_launch_ready)
                    is GameLaunchResult.NoLaunchIntent -> UiMessage(nextMessageId(), R.string.library_launch_no_intent)
                    is GameLaunchResult.NotFound -> UiMessage(nextMessageId(), R.string.library_launch_not_found)
                    is GameLaunchResult.Error -> UiMessage(
                        nextMessageId(), R.string.library_launch_error, result.reason
                    )
                }
            )
        }
    }

    // ------------------------------------------------------------ discover add

    fun addInstalled(packageName: String) {
        viewModelScope.launch {
            gameRepository.addInstalledGame(packageName)
        }
    }

    // --------------------------------------------------------- manual add dialog

    fun showAddDialog() {
        updateState { it.copy(addDialogVisible = true) }
    }

    fun dismissAddDialog() {
        updateState { it.copy(addDialogVisible = false) }
    }

    /** Repository rejects blanks/duplicates; a duplicate surfaces an honest message. */
    fun confirmAddManual(name: String, packageName: String?) {
        viewModelScope.launch {
            val added = gameRepository.addManualGame(name, packageName)
            if (!added) {
                showMessage(UiMessage(nextMessageId(), R.string.library_already_added))
            }
            _uiState.update { recompute(it.copy(addDialogVisible = false)) }
        }
    }

    // -------------------------------------------------------- remove & details

    fun requestRemove(game: Game) {
        updateState { it.copy(removeTarget = game) }
    }

    fun dismissRemove() {
        updateState { it.copy(removeTarget = null) }
    }

    fun confirmRemove() {
        val target = _uiState.value.removeTarget ?: return
        viewModelScope.launch {
            gameRepository.removeGame(target.id)
        }
        updateState { recompute(it.copy(removeTarget = null)) }
    }

    fun showDetails(game: Game) {
        updateState { it.copy(detailsTarget = game) }
    }

    fun dismissDetails() {
        updateState { it.copy(detailsTarget = null) }
    }

    // ----------------------------------------------------------------- messages

    fun onMessageShown() {
        updateState { it.copy(message = null) }
    }

    // -------------------------------------------------------------------- icons

    /** Cached real icon, or null while loading / when unavailable (placeholder shown). */
    fun cachedIcon(packageName: String): ImageBitmap? = iconCache[packageName]

    /** Loads (and caches) the real app icon off the main thread. */
    suspend fun iconFor(packageName: String): ImageBitmap? {
        if (iconCache.containsKey(packageName)) return iconCache[packageName]
        val icon = gameRepository.loadIcon(packageName)
        iconCache[packageName] = icon
        return icon
    }

    // ----------------------------------------------------------------- internals

    private fun nextMessageId(): Long = messageId.incrementAndGet()

    private fun showMessage(message: UiMessage) {
        updateState { it.copy(message = message) }
    }

    /**
     * Raw sources live in the state alongside the derived views; every mutation goes
     * through [updateState] so the derived lists are always consistent.
     */
    private fun updateState(transform: (LibraryUiState) -> LibraryUiState) {
        _uiState.update { recompute(transform(it)) }
    }

    private fun recompute(state: LibraryUiState): LibraryUiState {
        val myGames = gameRepository.sort(
            gameRepository.search(state.allGames, state.searchQuery),
            state.sort,
        )
        val discoverSource = when (state.discoverFilter) {
            DiscoverFilter.GAMES -> state.installedApps.filter { it.isGame }
            DiscoverFilter.ALL_APPS -> state.installedApps
        }
        val discoverApps = gameRepository.sort(
            gameRepository.search(discoverSource, state.searchQuery),
            state.sort,
        )
        return state.copy(
            myGames = myGames,
            discoverApps = discoverApps,
            selectedPackages = state.allGames.mapNotNull { it.packageName }.toSet(),
        )
    }
}
