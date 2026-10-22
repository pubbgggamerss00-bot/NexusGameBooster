package com.nexus.gamebooster.games

import androidx.compose.ui.graphics.ImageBitmap
import com.nexus.gamebooster.data.SelectedGamesStore
import com.nexus.gamebooster.monitoring.UnavailableReasons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** Sort orders for library / discover lists. */
enum class GameSort { NAME_ASC, NAME_DESC, RECENTLY_ADDED }

/**
 * Real events emitted by this repository at the moment they happen. The Home screen's
 * activity log subscribes to these — only genuine user/device actions appear there.
 * The buffer replays recent events so a late subscriber (e.g. HomeViewModel) still
 * sees them; on process death everything here is gone, which is honest for a log.
 */
sealed interface GameLibraryEvent {
    data class ScanCompleted(val launchableCount: Int) : GameLibraryEvent
    data class GameAdded(val name: String) : GameLibraryEvent
    data class GameRemoved(val name: String) : GameLibraryEvent
    data class GameLaunched(val name: String) : GameLibraryEvent
    data class GameLaunchFailed(val name: String) : GameLibraryEvent
}

/**
 * The user's game library + the device's launchable-app catalog.
 *
 * Persistence model (dependency-free, no kotlinx.serialization):
 *  - Selected installed apps: package names in the `selected_games` string set.
 *  - Manual entries: `name|addedAtMillis` strings in the `manual_games` string set.
 *    (Names containing `|` are safe: the parser splits on the LAST `|`.)
 *  - Everything else (labels, icons, detected-game flags) is hydrated from real scans
 *    at runtime and never persisted as fake data.
 *
 * Known, documented limitation: addedAtMillis for installed entries is session-only
 * (a string set cannot carry timestamps), so RECENTLY_ADDED falls back to name order
 * for restored entries.
 */
class GameRepository(
    private val installedAppSource: InstalledAppSource,
    private val gameLauncher: GameLauncher,
    private val store: SelectedGamesStore,
) {

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _games = MutableStateFlow<List<Game>>(emptyList())
    val games: StateFlow<List<Game>> = _games.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    private val _lastScanResult = MutableStateFlow<GameScanResult?>(null)
    val lastScanResult: StateFlow<GameScanResult?> = _lastScanResult.asStateFlow()

    private val _events = MutableSharedFlow<GameLibraryEvent>(
        replay = 16,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<GameLibraryEvent> = _events.asSharedFlow()

    init {
        // Seed the library from DataStore once; real metadata arrives with the first scan.
        repoScope.launch {
            val packages = store.selectedGamePackages.first()
            val manuals = store.manualGameEntries.first()
            val restored = buildList {
                packages.forEach { pkg ->
                    val known = _installedApps.value.firstOrNull { it.packageName == pkg }
                    add(
                        Game(
                            id = pkg,
                            name = known?.label ?: pkg,
                            packageName = pkg,
                            isManual = false,
                            installed = known != null,
                            isDetectedGame = known?.isGame ?: false,
                            addedAtMillis = 0L,
                            launchable = known != null,
                        )
                    )
                }
                manuals.forEach { entry -> parseManualEntry(entry)?.let { add(it) } }
            }
            _games.value = restored
        }
    }

    // ---------------------------------------------------------------- scanning

    /**
     * Runs a real scan of launchable apps, caches the catalog, and hydrates existing
     * library entries with real metadata (label, launchable, detected-game).
     * Never auto-adds anything to the library — adding is always explicit.
     */
    suspend fun refreshInstalledApps(): GameScanResult {
        val result = when (val query = installedAppSource.queryLaunchableApps()) {
            is InstalledAppQueryResult.Success -> {
                _installedApps.value = query.apps
                hydrateFrom(query.apps)
                GameScanResult.Success(query.apps)
            }

            is InstalledAppQueryResult.Failed -> GameScanResult.Failed(query.reason)
        }
        _lastScanResult.value = result
        if (result is GameScanResult.Success) {
            _events.tryEmit(GameLibraryEvent.ScanCompleted(result.apps.size))
        }
        return result
    }

    /** Apps the device itself declares as games (Discover tab "Games" filter). */
    fun discoverableGames(): List<InstalledApp> = _installedApps.value.filter { it.isGame }

    /** Every launchable app (Discover tab "All apps" browse). */
    fun allLaunchableApps(): List<InstalledApp> = _installedApps.value

    /** Real icon lookup, delegated to the app source (null → caller shows a placeholder). */
    suspend fun loadIcon(packageName: String): ImageBitmap? =
        installedAppSource.loadIcon(packageName)

    private suspend fun hydrateFrom(apps: List<InstalledApp>) {
        _games.update { current ->
            current.map { game ->
                val app = game.packageName?.let { pkg -> apps.firstOrNull { it.packageName == pkg } }
                if (app == null) {
                    game
                } else {
                    game.copy(
                        // Manual entries keep the user's typed name; detected entries show the real label.
                        name = if (game.isManual) game.name else app.label,
                        installed = true,
                        launchable = true,
                        isDetectedGame = app.isGame,
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------ library edits

    /**
     * Adds an installed app to the library. Returns false (no-op) when the package is
     * already present. Persists the package set.
     */
    suspend fun addInstalledGame(packageName: String): Boolean {
        val trimmed = packageName.trim()
        if (trimmed.isEmpty()) return false
        if (_games.value.any { it.packageName == trimmed }) return false
        val app = _installedApps.value.firstOrNull { it.packageName == trimmed }
        val game = Game(
            id = trimmed,
            name = app?.label ?: trimmed,
            packageName = trimmed,
            isManual = false,
            installed = true,
            isDetectedGame = app?.isGame ?: false,
            addedAtMillis = System.currentTimeMillis(),
            launchable = true,
        )
        _games.update { it + game }
        persistSelectedPackages()
        _events.tryEmit(GameLibraryEvent.GameAdded(game.name))
        return true
    }

    /**
     * Adds a manual entry (for games the device does not declare as games, or that are
     * otherwise not discoverable). Duplicate names (case-insensitive) are rejected.
     * Returns false when nothing was added.
     */
    suspend fun addManualGame(name: String, packageName: String? = null): Boolean {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return false
        val trimmedPackage = packageName?.trim()?.takeIf { it.isNotEmpty() }
        if (_games.value.any { it.isManual && it.name.equals(trimmedName, ignoreCase = true) }) {
            return false
        }
        val addedAt = System.currentTimeMillis()
        val game = Game(
            id = manualId(trimmedName, addedAt),
            name = trimmedName,
            packageName = trimmedPackage,
            isManual = true,
            addedAtMillis = addedAt,
        )
        _games.update { it + game }
        persistManualEntries()
        _events.tryEmit(GameLibraryEvent.GameAdded(game.name))
        return true
    }

    /** Removes a library entry (library only — nothing is uninstalled) and persists the change. */
    suspend fun removeGame(id: String) {
        val target = _games.value.firstOrNull { it.id == id } ?: return
        _games.update { current -> current.filterNot { it.id == id } }
        if (target.isManual) {
            persistManualEntries()
        } else {
            persistSelectedPackages()
        }
        _events.tryEmit(GameLibraryEvent.GameRemoved(target.name))
    }

    /** Launches the app via the real [GameLauncher] and reports the honest outcome. */
    suspend fun launch(packageName: String): GameLaunchResult {
        val displayName = displayNameFor(packageName)
        val result = gameLauncher.launch(packageName)
        when (result) {
            is GameLaunchResult.Launched -> _events.tryEmit(GameLibraryEvent.GameLaunched(displayName))
            else -> _events.tryEmit(GameLibraryEvent.GameLaunchFailed(displayName))
        }
        return result
    }

    /** Used by Settings → "Reset app data": clears the library and persists empty sets. */
    suspend fun reset() {
        _games.value = emptyList()
        store.setSelectedGamePackages(emptySet())
        store.setManualGameEntries(emptySet())
    }

    // ------------------------------------------------------------- search & sort

    /** Case-insensitive search over label/name and package name. */
    fun search(games: List<Game>, query: String): List<Game> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return games
        return games.filter {
            it.name.contains(trimmed, ignoreCase = true) ||
                it.packageName?.contains(trimmed, ignoreCase = true) == true
        }
    }

    /** Case-insensitive search over app label and package name. */
    // @JvmName: the Game overload has the same erased JVM signature (List, String).
    @JvmName("searchInstalledApps")
    fun search(apps: List<InstalledApp>, query: String): List<InstalledApp> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return apps
        return apps.filter {
            it.label.contains(trimmed, ignoreCase = true) ||
                it.packageName.contains(trimmed, ignoreCase = true)
        }
    }

    /**
     * Sorts library entries. RECENTLY_ADDED uses addedAtMillis (newest first, name as
     * fallback); restored installed entries (addedAtMillis == 0) therefore sort last —
     * a documented limitation of a dependency-free persistence format.
     */
    fun sort(games: List<Game>, order: GameSort): List<Game> = when (order) {
        GameSort.NAME_ASC -> games.sortedBy { it.name.lowercase() }
        GameSort.NAME_DESC -> games.sortedByDescending { it.name.lowercase() }
        GameSort.RECENTLY_ADDED -> games.sortedWith(
            compareByDescending<Game> { it.addedAtMillis }.thenBy { it.name.lowercase() }
        )
    }

    /**
     * Sorts discoverable apps. Discover items have no add-timestamps, so
     * RECENTLY_ADDED falls back to label order (documented).
     */
    // @JvmName: the Game overload has the same erased JVM signature (List, GameSort).
    @JvmName("sortInstalledApps")
    fun sort(apps: List<InstalledApp>, order: GameSort): List<InstalledApp> = when (order) {
        GameSort.NAME_ASC, GameSort.RECENTLY_ADDED -> apps.sortedBy { it.label.lowercase() }
        GameSort.NAME_DESC -> apps.sortedByDescending { it.label.lowercase() }
    }

    // ----------------------------------------------------------------- internals

    private suspend fun persistSelectedPackages() {
        store.setSelectedGamePackages(
            _games.value.filterNot { it.isManual }.mapNotNull { it.packageName }.toSet()
        )
    }

    private suspend fun persistManualEntries() {
        store.setManualGameEntries(
            _games.value.filter { it.isManual }.map { "${it.name}|${it.addedAtMillis}" }.toSet()
        )
    }

    private fun displayNameFor(packageName: String): String =
        _games.value.firstOrNull { it.packageName == packageName }?.name
            ?: _installedApps.value.firstOrNull { it.packageName == packageName }?.label
            ?: packageName

    private companion object {
        /**
         * Stable id for a manual entry: java.lang.String.hashCode() is specified and
         * deterministic, so the id survives process death. Duplicate names are rejected
         * on add, so collisions within the same millisecond are not a practical risk.
         */
        fun manualId(name: String, addedAtMillis: Long): String =
            "manual_${addedAtMillis}_${name.hashCode()}"

        /**
         * Parses a persisted `name|addedAtMillis` entry. Splits on the LAST `|` so
         * names containing `|` survive; malformed entries are dropped (never guessed).
         */
        fun parseManualEntry(entry: String): Game? {
            val separator = entry.lastIndexOf('|')
            if (separator <= 0) return null
            val name = entry.substring(0, separator).trim()
            val millis = entry.substring(separator + 1).toLongOrNull() ?: 0L
            if (name.isEmpty()) return null
            return Game(
                id = manualId(name, millis),
                name = name,
                packageName = null,
                isManual = true,
                addedAtMillis = millis,
            )
        }
    }
}
