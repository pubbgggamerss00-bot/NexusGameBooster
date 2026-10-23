package com.nexus.gamebooster.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nexus.gamebooster.optimizer.BoostHistoryStore
import com.nexus.gamebooster.optimizer.BoostReport
import com.nexus.gamebooster.profiles.ProfileIds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.IOException

/**
 * App language. SYSTEM is the default: the app then follows the DEVICE language
 * (showing Persian resources only on fa devices). Only English and Persian ship
 * as explicit choices; their codes are stable storage values.
 */
enum class Language(val code: String) {
    SYSTEM("system"),
    ENGLISH("en"),
    PERSIAN("fa");

    companion object {
        fun fromCode(code: String): Language =
            entries.firstOrNull { it.code == code } ?: SYSTEM
    }
}

/**
 * Persisted theme choice. Phase 2 ships two dark variants:
 *  - DARK_NAVY: the original #070B14 palette.
 *  - AMOLED_BLACK: pure #000000 background, same neon-green primary.
 * There is still no light theme and no dynamic color.
 */
enum class ThemeMode(val storageValue: String) {
    DARK_NAVY("dark_navy"),
    AMOLED_BLACK("amoled_black");

    companion object {
        fun fromStorageValue(value: String?): ThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: DARK_NAVY
    }
}

/**
 * Persisted UI preferences.
 *
 * NOTE (honesty): `activeProfileId` stores which of the three fixed profiles the user
 * SELECTED as a preference. Nothing is applied to the system; the profile only steers
 * NexusGameBooster's own in-app monitoring behavior (see `profiles/MonitoringPolicy`).
 *
 * `selectedGamePackages` / `manualGames` are the RAW persisted forms of the game
 * library (package names and `name|addedAtMillis` entries); parsing into [Game]
 * objects stays in `games/GameRepository`, which reads them through [SelectedGamesStore].
 */
data class AppSettings(
    val language: Language = Language.SYSTEM,
    val reduceMotion: Boolean = false,
    val activeProfileId: String = ProfileIds.BALANCED,
    val theme: ThemeMode = ThemeMode.DARK_NAVY,
    val selectedGamePackages: Set<String> = emptySet(),
    val manualGames: Set<String> = emptySet(),
)

/**
 * Persistence seam for the game library. Kept as an interface so `GameRepository`
 * can be exercised with fakes (no DataStore, no Android types needed by callers).
 */
interface SelectedGamesStore {
    /** Package names the user added from the Discover / All-apps browse. */
    val selectedGamePackages: Flow<Set<String>>

    /**
     * Raw manual entries in `name|addedAtMillis` form. Parsing (including names that
     * themselves contain `|`) stays in the games layer.
     */
    val manualGameEntries: Flow<Set<String>>

    suspend fun setSelectedGamePackages(packages: Set<String>)
    suspend fun setManualGameEntries(entries: Set<String>)
}

/**
 * Persistence seam for the active performance profile. Kept as an interface (mirroring
 * [SelectedGamesStore]) so `ProfilesViewModel` and `MonitorViewModel` can be exercised
 * with fakes — no DataStore and no Android types needed by their callers. Implementations
 * MUST emit values that are already normalized to a preset id (see [ProfileIds.normalize]):
 * legacy Phase 1 `"gaming"` values migrate to `"balanced"` on read.
 */
interface ActiveProfileStore {
    /** Normalized active profile id; defaults to [ProfileIds.BALANCED] when nothing is persisted. */
    val activeProfileId: Flow<String>

    /** Persists the selection. Throws on I/O failure — callers wrap in runCatching. */
    suspend fun setActiveProfile(profileId: String)
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nexus_settings")

private object PreferencesKeys {
    val LANGUAGE = stringPreferencesKey("language")
    val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
    val ACTIVE_PROFILE = stringPreferencesKey("active_profile")
    val THEME = stringPreferencesKey("theme")
    val SELECTED_GAMES = stringSetPreferencesKey("selected_games")
    val MANUAL_GAMES = stringSetPreferencesKey("manual_games")
    val BOOST_HISTORY = stringSetPreferencesKey("boost_history")
    val DEVICE_CHANGES = stringSetPreferencesKey("device_changes")
}

/**
 * DataStore-backed settings store.
 *
 * The [DataStore] is constructor-injected (no Context field inside this class) — this is
 * what makes real JVM unit tests with a file-backed DataStore possible; production code
 * obtains instances through [create].
 *
 * Phase 5: also implements [BoostHistoryStore] — boost reports persist as one JSON
 * string per report inside the `boost_history` string set, capped at the
 * [MAX_BOOST_HISTORY] newest reports, corrupt entries skipped on every read.
 */
class SettingsRepository(private val dataStore: DataStore<Preferences>) :
    SelectedGamesStore, ActiveProfileStore, BoostHistoryStore, DeviceChangeStore {

    companion object {
        /** Production entry point; keeps the property-delegate DataStore wiring in one place. */
        fun create(context: Context): SettingsRepository = SettingsRepository(context.dataStore)

        /** Boost history cap: the [BoostReport] newest list is kept; older ones are dropped. */
        const val MAX_BOOST_HISTORY = 20

        /** Device-change ledger cap (per-package entries only; far below this in practice). */
        const val MAX_DEVICE_CHANGES = 50
    }

    /** Tolerant JSON mapper: unknown future fields are ignored, sealed outcomes decode via @SerialName. */
    private val json = Json { ignoreUnknownKeys = true }

    private fun Flow<Preferences>.guardCorruption(): Flow<Preferences> = catch { error ->
        // Defensive: a corrupt/unreadable preferences file must not crash the app.
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    val settings: Flow<AppSettings> = dataStore.data
        .guardCorruption()
        .map { prefs ->
            AppSettings(
                language = Language.fromCode(prefs[PreferencesKeys.LANGUAGE] ?: Language.SYSTEM.code),
                reduceMotion = prefs[PreferencesKeys.REDUCE_MOTION] ?: false,
                // Normalized on read: legacy Phase 1 "gaming" (and any unknown value)
                // migrates to BALANCED without touching the stored bytes.
                activeProfileId = ProfileIds.normalize(prefs[PreferencesKeys.ACTIVE_PROFILE]),
                theme = ThemeMode.fromStorageValue(prefs[PreferencesKeys.THEME]),
                selectedGamePackages = prefs[PreferencesKeys.SELECTED_GAMES] ?: emptySet(),
                manualGames = prefs[PreferencesKeys.MANUAL_GAMES] ?: emptySet(),
            )
        }

    override val selectedGamePackages: Flow<Set<String>> = dataStore.data
        .guardCorruption()
        .map { prefs -> prefs[PreferencesKeys.SELECTED_GAMES] ?: emptySet() }

    override val manualGameEntries: Flow<Set<String>> = dataStore.data
        .guardCorruption()
        .map { prefs -> prefs[PreferencesKeys.MANUAL_GAMES] ?: emptySet() }

    // Normalized on read (same migration as `settings` above).
    override val activeProfileId: Flow<String> = dataStore.data
        .guardCorruption()
        .map { prefs -> ProfileIds.normalize(prefs[PreferencesKeys.ACTIVE_PROFILE]) }

    suspend fun setLanguage(language: Language) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.LANGUAGE] = language.code
        }
    }

    suspend fun setReduceMotion(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.REDUCE_MOTION] = enabled
        }
    }

    override suspend fun setActiveProfile(profileId: String) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.ACTIVE_PROFILE] = profileId
        }
    }

    suspend fun setTheme(theme: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.THEME] = theme.storageValue
        }
    }

    override suspend fun setSelectedGamePackages(packages: Set<String>) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.SELECTED_GAMES] = packages
        }
    }

    override suspend fun setManualGameEntries(entries: Set<String>) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.MANUAL_GAMES] = entries
        }
    }

    // -------------------------------------------------------- BoostHistoryStore

    override val boostHistory: Flow<List<BoostReport>> = dataStore.data
        .guardCorruption()
        .map { prefs -> decodeBoostReports(prefs[PreferencesKeys.BOOST_HISTORY] ?: emptySet()) }

    override suspend fun addBoostReport(report: BoostReport) {
        dataStore.edit { prefs ->
            val decoded = decodeBoostReports(prefs[PreferencesKeys.BOOST_HISTORY] ?: emptySet())
            // Upsert: a report with the same id replaces its previous version (used by
            // BoostViewModel when user-explicit actions are appended to a live report).
            // Newest-first by completedAtMillis; cap at MAX_BOOST_HISTORY (drop oldest).
            val updated = (listOf(report) + decoded.filterNot { it.id == report.id })
                .sortedByDescending { it.completedAtMillis }
                .take(MAX_BOOST_HISTORY)
            prefs[PreferencesKeys.BOOST_HISTORY] = updated.map { json.encodeToString(it) }.toSet()
        }
    }

    override suspend fun clearBoostHistory() {
        dataStore.edit { prefs ->
            prefs.remove(PreferencesKeys.BOOST_HISTORY)
        }
    }

    /**
     * Decodes every entry with `runCatching` per entry: one corrupt/garbage string is
     * skipped silently — it never breaks the whole history and never becomes a fake report.
     */
    private fun decodeBoostReports(entries: Set<String>): List<BoostReport> =
        entries.mapNotNull { entry ->
            runCatching { json.decodeFromString<BoostReport>(entry) }.getOrNull()
        }

    // -------------------------------------------------------- DeviceChangeStore

    override val deviceChanges: Flow<List<DeviceChangeRecord>> = dataStore.data
        .guardCorruption()
        .map { prefs -> decodeDeviceChanges(prefs[PreferencesKeys.DEVICE_CHANGES] ?: emptySet()) }

    override suspend fun addDeviceChangeIfAbsent(record: DeviceChangeRecord) {
        dataStore.edit { prefs ->
            val decoded = decodeDeviceChanges(prefs[PreferencesKeys.DEVICE_CHANGES] ?: emptySet())
            // First recorded original wins: a later boost must never overwrite the
            // user's true pre-boost values with values the app itself had changed.
            if (decoded.any { it.upsertKey == record.upsertKey }) return@edit
            val updated = (listOf(record) + decoded).take(MAX_DEVICE_CHANGES)
            prefs[PreferencesKeys.DEVICE_CHANGES] = updated.map { json.encodeToString(it) }.toSet()
        }
    }

    override suspend fun removeDeviceChanges(ids: Set<String>) {
        if (ids.isEmpty()) return
        dataStore.edit { prefs ->
            val decoded = decodeDeviceChanges(prefs[PreferencesKeys.DEVICE_CHANGES] ?: emptySet())
            val remaining = decoded.filterNot { it.id in ids }
            if (remaining.isEmpty()) {
                prefs.remove(PreferencesKeys.DEVICE_CHANGES)
            } else {
                prefs[PreferencesKeys.DEVICE_CHANGES] =
                    remaining.map { json.encodeToString(it) }.toSet()
            }
        }
    }

    override suspend fun clearDeviceChanges() {
        dataStore.edit { prefs ->
            prefs.remove(PreferencesKeys.DEVICE_CHANGES)
        }
    }

    /** Same per-entry tolerance as the boost history: one bad line never breaks the ledger. */
    private fun decodeDeviceChanges(entries: Set<String>): List<DeviceChangeRecord> =
        entries.mapNotNull { entry ->
            runCatching { json.decodeFromString<DeviceChangeRecord>(entry) }.getOrNull()
        }

    /** Clears every key this app wrote, including theme, profile, the game keys and the boost history (Settings → "Reset app data"). */
    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
