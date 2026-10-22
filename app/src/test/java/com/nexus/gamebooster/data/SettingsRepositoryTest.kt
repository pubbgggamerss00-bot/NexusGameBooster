package com.nexus.gamebooster.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nexus.gamebooster.optimizer.BoostHistoryStore
import com.nexus.gamebooster.optimizer.BoostStatus
import com.nexus.gamebooster.profiles.ProfileIds
import com.nexus.gamebooster.testing.testReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * REAL file-backed DataStore tests for [SettingsRepository] (Phase 4/5 contract +
 * Phase 7 language/theme/reduce-motion extension).
 *
 * Pattern (same as the Phase 6 suite): a [androidx.datastore.preferences.core.PreferenceDataStoreFactory]
 * DataStore over a `TemporaryFolder` file, running on an [UnconfinedTestDispatcher] scope
 * so every read/write completes deterministically inside `runTest` — real files, real
 * JSON serialization, no Android types, no mocks.
 *
 * DataStore contract honored: exactly ONE instance per file per test — each test gets a
 * fresh folder/file and the scope is cancelled in [tearDown] BEFORE the next instance
 * (with its own new file) is ever created.
 *
 * These tests ARE executed for real via Gradle (`:app:testDebugUnitTest`) in this
 * sandbox — see worklog Task 14-d for the green run.
 */
class SettingsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repo: SettingsRepository

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
        val file = File(tmp.newFolder(), "settings_test.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        repo = SettingsRepository(dataStore)
    }

    @After
    fun tearDown() {
        // Cancel the DataStore scope FIRST: a second DataStore over the same file must
        // never overlap the previous one (the Phase 6 two-instance contract).
        scope.cancel()
    }

    // ------------------------------------------------------- active profile (Phase 4)

    @Test
    fun activeProfileId_roundTripAndDefault() = runTest {
        assertEquals(ProfileIds.BALANCED, repo.activeProfileId.first()) // nothing persisted yet
        repo.setActiveProfile(ProfileIds.PERFORMANCE)
        assertEquals(ProfileIds.PERFORMANCE, repo.activeProfileId.first())
        repo.setActiveProfile(ProfileIds.BATTERY_SAVER)
        assertEquals(ProfileIds.BATTERY_SAVER, repo.activeProfileId.first())
    }

    @Test
    fun activeProfileId_legacyGamingNormalizedToBalancedOnRead_storedBytesUntouched() = runTest {
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("active_profile")] = "gaming"
        }
        // Every read path normalizes: both the dedicated flow and the settings flow.
        assertEquals(ProfileIds.BALANCED, repo.activeProfileId.first())
        assertEquals(ProfileIds.BALANCED, repo.settings.first().activeProfileId)
        // The migration happens on READ — the stored raw value is left as-is.
        assertEquals(
            "gaming",
            dataStore.data.first()[stringPreferencesKey("active_profile")],
        )
    }

    // ---------------------------------------------------- game library (Phase 2/3)

    @Test
    fun selectedAndManualGames_roundTrip() = runTest {
        repo.setSelectedGamePackages(setOf("com.game.one", "com.game.two"))
        repo.setManualGameEntries(setOf("My|Racer|1700000000000"))
        assertEquals(setOf("com.game.one", "com.game.two"), repo.selectedGamePackages.first())
        assertEquals(setOf("My|Racer|1700000000000"), repo.manualGameEntries.first())
        val settings = repo.settings.first()
        assertEquals(setOf("com.game.one", "com.game.two"), settings.selectedGamePackages)
        assertEquals(setOf("My|Racer|1700000000000"), settings.manualGames)
    }

    // --------------------------------------------------------- settings flow defaults

    @Test
    fun settings_defaultsWhenNothingPersisted_phase7Values() = runTest {
        val settings = repo.settings.first()
        assertEquals(Language.SYSTEM, settings.language) // Phase 7: SYSTEM is the default
        assertFalse(settings.reduceMotion)
        assertEquals(ProfileIds.BALANCED, settings.activeProfileId)
        assertEquals(ThemeMode.DARK_NAVY, settings.theme)
        assertTrue(settings.selectedGamePackages.isEmpty())
        assertTrue(settings.manualGames.isEmpty())
    }

    @Test
    fun settings_flowReflectsActiveProfileWrites() = runTest {
        repo.setActiveProfile(ProfileIds.BATTERY_SAVER)
        assertEquals(ProfileIds.BATTERY_SAVER, repo.settings.first().activeProfileId)
        // The dedicated ActiveProfileStore view agrees with the settings view.
        assertEquals(ProfileIds.BATTERY_SAVER, repo.activeProfileId.first())
    }

    // ------------------------------------------------------------ Phase 7: language

    @Test
    fun setLanguage_roundTripForAllThreeValues() = runTest {
        for (language in listOf(Language.ENGLISH, Language.PERSIAN, Language.SYSTEM)) {
            repo.setLanguage(language)
            assertEquals(language, repo.settings.first().language)
        }
    }

    @Test
    fun setLanguage_removedKeyFallsBackToSystem() = runTest {
        repo.setLanguage(Language.PERSIAN)
        assertEquals(Language.PERSIAN, repo.settings.first().language)
        dataStore.edit { prefs -> prefs.remove(stringPreferencesKey("language")) }
        // "no stored key" means SYSTEM (follow the device), never an EN/FA guess.
        assertEquals(Language.SYSTEM, repo.settings.first().language)
    }

    @Test
    fun setLanguage_unknownStoredCodeFallsBackToSystem() = runTest {
        dataStore.edit { prefs -> prefs[stringPreferencesKey("language")] = "klingon" }
        assertEquals(Language.SYSTEM, repo.settings.first().language)
    }

    // ------------------------------------------------------------- Phase 7: theme

    @Test
    fun setTheme_roundTripDarkNavyAndAmoledBlack() = runTest {
        assertEquals(ThemeMode.DARK_NAVY, repo.settings.first().theme)
        repo.setTheme(ThemeMode.AMOLED_BLACK)
        assertEquals(ThemeMode.AMOLED_BLACK, repo.settings.first().theme)
        repo.setTheme(ThemeMode.DARK_NAVY)
        assertEquals(ThemeMode.DARK_NAVY, repo.settings.first().theme)
    }

    @Test
    fun setTheme_unknownStoredValueFallsBackToDarkNavy() = runTest {
        dataStore.edit { prefs -> prefs[stringPreferencesKey("theme")] = "light_pink" }
        assertEquals(ThemeMode.DARK_NAVY, repo.settings.first().theme)
    }

    // ----------------------------------------------------- Phase 7: reduce motion

    @Test
    fun setReduceMotion_roundTrip() = runTest {
        assertFalse(repo.settings.first().reduceMotion)
        repo.setReduceMotion(true)
        assertTrue(repo.settings.first().reduceMotion)
        repo.setReduceMotion(false)
        assertFalse(repo.settings.first().reduceMotion)
    }

    // ------------------------------------------------------------- seams + reset

    @Test
    fun implementsAllThreePersistenceSeams() {
        // ServiceLocator wires ONE instance as all three stores — the contract the VMs
        // and the boost engine rely on.
        assertTrue(repo is SelectedGamesStore)
        assertTrue(repo is ActiveProfileStore)
        assertTrue(repo is BoostHistoryStore)
    }

    @Test
    fun clear_wipesEveryKeyIncludingBoostHistory() = runTest {
        repo.setLanguage(Language.PERSIAN)
        repo.setReduceMotion(true)
        repo.setTheme(ThemeMode.AMOLED_BLACK)
        repo.setActiveProfile(ProfileIds.PERFORMANCE)
        repo.setSelectedGamePackages(setOf("com.game.one"))
        repo.addBoostReport(testReport(id = "r1", completedAtMillis = 10))
        repo.clear()
        val settings = repo.settings.first()
        assertEquals(Language.SYSTEM, settings.language)
        assertFalse(settings.reduceMotion)
        assertEquals(ThemeMode.DARK_NAVY, settings.theme)
        assertEquals(ProfileIds.BALANCED, settings.activeProfileId)
        assertTrue(repo.selectedGamePackages.first().isEmpty())
        assertTrue(repo.boostHistory.first().isEmpty())
    }

    // ------------------------------------------------------------ BoostHistoryStore

    @Test
    fun boostHistory_addsAndReturnsNewestFirst_andClears() = runTest {
        assertTrue(repo.boostHistory.first().isEmpty())
        repo.addBoostReport(testReport(id = "old", completedAtMillis = 100))
        repo.addBoostReport(testReport(id = "newest", completedAtMillis = 300))
        repo.addBoostReport(testReport(id = "middle", completedAtMillis = 200))
        assertEquals(
            listOf("newest", "middle", "old"),
            repo.boostHistory.first().map { it.id },
        )
        repo.clearBoostHistory()
        assertTrue(repo.boostHistory.first().isEmpty())
    }

    @Test
    fun boostHistory_upsertReplacesSameId() = runTest {
        repo.addBoostReport(testReport(id = "same", completedAtMillis = 100, status = BoostStatus.COMPLETED))
        repo.addBoostReport(
            testReport(id = "same", completedAtMillis = 500, status = BoostStatus.PARTIAL_SUCCESS),
        )
        val history = repo.boostHistory.first()
        assertEquals(1, history.size)
        assertEquals(BoostStatus.PARTIAL_SUCCESS, history.single().status)
        assertEquals(500L, history.single().completedAtMillis)
    }
}
