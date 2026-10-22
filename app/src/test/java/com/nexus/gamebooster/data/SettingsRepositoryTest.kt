package com.nexus.gamebooster.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nexus.gamebooster.optimizer.BoostHistoryStore
import com.nexus.gamebooster.optimizer.BoostStatus
import com.nexus.gamebooster.profiles.ProfileIds
import com.nexus.gamebooster.testing.testReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
 * Recreated after the sandbox environment wipe (Task 16-b, from the Task 14-d spec).
 * IS executed for real via Gradle: `./gradlew :app:testDebugUnitTest`.
 *
 * REAL file-backed DataStore tests for [SettingsRepository] (no Android context):
 * each test gets its own unique file via [PreferenceDataStoreFactory] on an
 * [UnconfinedTestDispatcher] scope over a [TemporaryFolder].
 *
 * TWO-INSTANCE CONTRACT (Task 14-d lesson): a DataStore file path may only ever have
 * ONE live DataStore instance — every test creates a fresh file, and the owning scope
 * is cancelled in @After BEFORE the next test creates the next file. No sleeps
 * anywhere: the unconfined scope makes every read/write deterministic.
 */
class SettingsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var currentDataStore: DataStore<Preferences>
    private var fileCounter = 0

    @Before
    fun setUp() {
        dataStoreScope = CoroutineScope(testDispatcher + Job())
    }

    @After
    fun tearDown() {
        // Release the DataStore before the next test creates its file.
        dataStoreScope.cancel()
    }

    private fun newRepository(): SettingsRepository {
        val dir = tmp.newFolder()
        val file = File(dir, "nexus_settings_${fileCounter++}.preferences_pb")
        currentDataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { file }
        return SettingsRepository(currentDataStore)
    }

    // ------------------------------------------------------------------ profile

    @Test
    fun activeProfileId_roundTripsThroughTheStore() = runTest(testDispatcher) {
        val repo = newRepository()
        repo.setActiveProfile(ProfileIds.PERFORMANCE)
        assertEquals(ProfileIds.PERFORMANCE, repo.activeProfileId.first())
        assertEquals(
            ProfileIds.PERFORMANCE,
            repo.settings.first().activeProfileId,
        )
    }

    @Test
    fun legacyGaming_normalizedOnRead_storedBytesUntouched() = runTest(testDispatcher) {
        val dir = tmp.newFolder()
        val file = File(dir, "nexus_settings_legacy_${fileCounter++}.preferences_pb")
        currentDataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { file }
        val repo = SettingsRepository(currentDataStore)

        repo.setActiveProfile(ProfileIds.LEGACY_GAMING)
        // Every read path normalizes the legacy id to Balanced…
        assertEquals(ProfileIds.BALANCED, repo.activeProfileId.first())
        assertEquals(ProfileIds.BALANCED, repo.settings.first().activeProfileId)
        // …while the STORED bytes keep the raw legacy value untouched.
        val raw = file.readBytes().toString(Charsets.ISO_8859_1)
        assertTrue("gaming" in raw)
        assertFalse("balanced" in raw)
    }

    // -------------------------------------------------------------- game library

    @Test
    fun selectedGamePackages_roundTrip() = runTest(testDispatcher) {
        val repo = newRepository()
        val packages = setOf("com.example.game.one", "com.example.game.two")
        repo.setSelectedGamePackages(packages)
        assertEquals(packages, repo.selectedGamePackages.first())
        assertEquals(packages, repo.settings.first().selectedGamePackages)
    }

    @Test
    fun manualGameEntries_roundTrip_includingPipeInName() = runTest(testDispatcher) {
        val repo = newRepository()
        val entries = setOf("Cool|Game|1720000000000", "Plain|1720000000001")
        repo.setManualGameEntries(entries)
        assertEquals(entries, repo.manualGameEntries.first())
        assertEquals(entries, repo.settings.first().manualGames)
    }

    // ------------------------------------------------------------------ defaults

    @Test
    fun defaults_systemLanguage_darkNavy_noReduceMotion_emptyLibrary() = runTest(testDispatcher) {
        val repo = newRepository()
        assertEquals(AppSettings(), repo.settings.first())
        assertEquals(ProfileIds.BALANCED, repo.activeProfileId.first())
        assertTrue(repo.selectedGamePackages.first().isEmpty())
        assertTrue(repo.manualGameEntries.first().isEmpty())
        assertTrue(repo.boostHistory.first().isEmpty())
    }

    // ------------------------------------------------------------------ language

    @Test
    fun setLanguage_roundTripsEnglishPersianAndSystem() = runTest(testDispatcher) {
        val repo = newRepository()
        repo.setLanguage(Language.ENGLISH)
        assertEquals(Language.ENGLISH, repo.settings.first().language)
        repo.setLanguage(Language.PERSIAN)
        assertEquals(Language.PERSIAN, repo.settings.first().language)
        repo.setLanguage(Language.SYSTEM)
        assertEquals(Language.SYSTEM, repo.settings.first().language)
    }

    @Test
    fun removedLanguageKey_fallsBackToSystem() = runTest(testDispatcher) {
        val repo = newRepository()
        repo.setLanguage(Language.PERSIAN)
        assertEquals(Language.PERSIAN, repo.settings.first().language)
        // Removing the key (as clear()/an old build would) falls back to SYSTEM.
        editRaw { it.remove(stringPreferencesKey("language")) }
        assertEquals(Language.SYSTEM, repo.settings.first().language)
    }

    @Test
    fun unknownLanguageCode_fallsBackToSystem() = runTest(testDispatcher) {
        val repo = newRepository()
        editRaw { it[stringPreferencesKey("language")] = "xx" }
        assertEquals(Language.SYSTEM, repo.settings.first().language)
    }

    // --------------------------------------------------------------------- theme

    @Test
    fun theme_roundTrips_andUnknownValueFallsBackToDarkNavy() = runTest(testDispatcher) {
        val repo = newRepository()
        repo.setTheme(ThemeMode.AMOLED_BLACK)
        assertEquals(ThemeMode.AMOLED_BLACK, repo.settings.first().theme)
        editRaw { it[stringPreferencesKey("theme")] = "old_phase2_theme" }
        assertEquals(ThemeMode.DARK_NAVY, repo.settings.first().theme)
    }

    @Test
    fun reduceMotion_roundTripsBothWays() = runTest(testDispatcher) {
        val repo = newRepository()
        repo.setReduceMotion(true)
        assertTrue(repo.settings.first().reduceMotion)
        repo.setReduceMotion(false)
        assertFalse(repo.settings.first().reduceMotion)
    }

    // --------------------------------------------------------------- store roles

    @Test
    fun implementsSelectedGamesActiveProfileAndBoostHistoryStores() = runTest(testDispatcher) {
        val repo = newRepository()
        // Interface checks (the ServiceLocator wires these seams through the interfaces).
        assertTrue(repo is SelectedGamesStore)
        assertTrue(repo is ActiveProfileStore)
        assertTrue(repo is BoostHistoryStore)

        val games: SelectedGamesStore = repo
        val profiles: ActiveProfileStore = repo
        val history: BoostHistoryStore = repo

        games.setSelectedGamePackages(setOf("com.example.game"))
        profiles.setActiveProfile(ProfileIds.PERFORMANCE)
        history.addBoostReport(testReport("r1", BoostStatus.COMPLETED, completedAtMillis = 1_000))

        assertEquals(setOf("com.example.game"), games.selectedGamePackages.first())
        assertEquals(ProfileIds.PERFORMANCE, profiles.activeProfileId.first())
        assertEquals(listOf("r1"), history.boostHistory.first().map { it.id })
    }

    // --------------------------------------------------------------------- clear

    @Test
    fun clear_wipesEverySettingIncludingBoostHistory() = runTest(testDispatcher) {
        val repo = newRepository()
        repo.setLanguage(Language.PERSIAN)
        repo.setTheme(ThemeMode.AMOLED_BLACK)
        repo.setReduceMotion(true)
        repo.setActiveProfile(ProfileIds.PERFORMANCE)
        repo.setSelectedGamePackages(setOf("com.example.game"))
        repo.setManualGameEntries(setOf("Manual|1000"))
        repo.addBoostReport(testReport("r1", BoostStatus.COMPLETED, completedAtMillis = 1_000))

        repo.clear()

        assertEquals(AppSettings(), repo.settings.first())
        assertEquals(ProfileIds.BALANCED, repo.activeProfileId.first())
        assertTrue(repo.selectedGamePackages.first().isEmpty())
        assertTrue(repo.manualGameEntries.first().isEmpty())
        assertTrue(repo.boostHistory.first().isEmpty())
    }

    // -------------------------------------------------------------- boost history

    @Test
    fun boostHistory_newestFirstByCompletedAt() = runTest(testDispatcher) {
        val repo = newRepository()
        repo.addBoostReport(testReport("r1", BoostStatus.COMPLETED, completedAtMillis = 1_000))
        repo.addBoostReport(testReport("r2", BoostStatus.PARTIAL_SUCCESS, completedAtMillis = 2_000))
        repo.addBoostReport(testReport("r3", BoostStatus.FAILED, completedAtMillis = 3_000))
        assertEquals(listOf("r3", "r2", "r1"), repo.boostHistory.first().map { it.id })
    }

    @Test
    fun boostHistory_capsAtTwenty_andClearWorks() = runTest(testDispatcher) {
        val repo = newRepository()
        for (i in 1..22) {
            repo.addBoostReport(testReport("r$i", BoostStatus.COMPLETED, completedAtMillis = i * 1_000L))
        }
        val history = repo.boostHistory.first()
        assertEquals(SettingsRepository.MAX_BOOST_HISTORY, history.size)
        assertEquals(20, history.size)
        assertEquals("r22", history.first().id)
        assertEquals("r3", history.last().id)
        assertFalse(history.any { it.id == "r1" || it.id == "r2" })

        repo.clearBoostHistory()
        assertTrue(repo.boostHistory.first().isEmpty())
    }

    @Test
    fun boostHistory_upsertReplacesSameId() = runTest(testDispatcher) {
        val repo = newRepository()
        repo.addBoostReport(testReport("r1", BoostStatus.PARTIAL_SUCCESS, completedAtMillis = 1_000))
        repo.addBoostReport(testReport("r1", BoostStatus.COMPLETED, completedAtMillis = 2_000))
        val history = repo.boostHistory.first()
        assertEquals(1, history.size)
        assertEquals(BoostStatus.COMPLETED, history.single().status)
        assertEquals(2_000L, history.single().completedAtMillis)
    }

    /** Raw-edit helper: writes the CURRENT test's file directly, bypassing typed setters. */
    private suspend fun editRaw(
        transform: (androidx.datastore.preferences.core.MutablePreferences) -> Unit,
    ) {
        currentDataStore.edit(transform)
    }
}
