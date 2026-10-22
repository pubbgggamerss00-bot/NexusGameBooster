package com.nexus.gamebooster.optimizer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.nexus.gamebooster.data.SettingsRepository
import com.nexus.gamebooster.testing.testReport
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Recreated after the sandbox environment wipe (Task 16-b, from the Task 14-d spec).
 * IS executed for real via Gradle: `./gradlew :app:testDebugUnitTest`.
 *
 * The REAL [BoostHistoryStore] implementation (SettingsRepository over a real
 * file-backed Preferences DataStore): empty default, newest-first order, the 20-report
 * cap, per-entry tolerant JSON decoding (corrupt entries skipped, valid ones survive),
 * clear, and upsert-by-id.
 *
 * TWO-INSTANCE CONTRACT: one DataStore per unique file per test; the owning scope is
 * cancelled in @After BEFORE the next file is created.
 */
class BoostHistoryStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var dataStoreScope: CoroutineScope
    private var fileCounter = 0

    @Before
    fun setUp() {
        dataStoreScope = CoroutineScope(testDispatcher + Job())
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
    }

    private fun newStore(): Pair<SettingsRepository, DataStore<Preferences>> {
        val dir = tmp.newFolder()
        val file = File(dir, "boost_history_${fileCounter++}.preferences_pb")
        val dataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(scope = dataStoreScope) { file }
        return SettingsRepository(dataStore) to dataStore
    }

    @Test
    fun emptyByDefault() = runTest(testDispatcher) {
        val (store, _) = newStore()
        assertTrue(store.boostHistory.first().isEmpty())
    }

    @Test
    fun newestFirstByCompletedAt() = runTest(testDispatcher) {
        val (store, _) = newStore()
        store.addBoostReport(testReport("r1", BoostStatus.COMPLETED, completedAtMillis = 1_000))
        store.addBoostReport(testReport("r2", BoostStatus.PARTIAL_SUCCESS, completedAtMillis = 2_000))
        store.addBoostReport(testReport("r3", BoostStatus.FAILED, completedAtMillis = 3_000))
        assertEquals(listOf("r3", "r2", "r1"), store.boostHistory.first().map { it.id })
    }

    @Test
    fun capsAtTwentyReports_oldestDropped() = runTest(testDispatcher) {
        val (store, _) = newStore()
        for (i in 1..22) {
            store.addBoostReport(testReport("r$i", BoostStatus.COMPLETED, completedAtMillis = i * 1_000L))
        }
        val history = store.boostHistory.first()
        assertEquals(20, history.size)
        assertEquals("r22", history.first().id)
        assertEquals("r3", history.last().id)
        assertFalse(history.any { it.id == "r1" || it.id == "r2" })
    }

    @Test
    fun corruptJsonEntriesSkipped_validReportSurvives() = runTest(testDispatcher) {
        val (store, dataStore) = newStore()
        val valid = testReport("valid", BoostStatus.COMPLETED, completedAtMillis = 5_000)
        val validJson = Json.encodeToString(valid)

        dataStore.edit { prefs ->
            prefs[stringSetPreferencesKey("boost_history")] = setOf(
                "{not json",
                "}{",
                "{\"x\":1}", // valid JSON, but not a BoostReport
                validJson,
            )
        }

        // Only the decodable report survives; the garbage never becomes a fake report.
        assertEquals(listOf(valid), store.boostHistory.first())

        // The next write re-persists the cleaned set — the garbage is gone for good.
        store.addBoostReport(testReport("after", BoostStatus.COMPLETED, completedAtMillis = 6_000))
        assertEquals(listOf("after", "valid"), store.boostHistory.first().map { it.id })
    }

    @Test
    fun clearRemovesEveryReport() = runTest(testDispatcher) {
        val (store, _) = newStore()
        store.addBoostReport(testReport("r1", BoostStatus.COMPLETED, completedAtMillis = 1_000))
        store.addBoostReport(testReport("r2", BoostStatus.COMPLETED, completedAtMillis = 2_000))
        store.clearBoostHistory()
        assertTrue(store.boostHistory.first().isEmpty())
    }

    @Test
    fun upsertByReplacesSameId() = runTest(testDispatcher) {
        val (store, _) = newStore()
        store.addBoostReport(testReport("r1", BoostStatus.PARTIAL_SUCCESS, completedAtMillis = 1_000))
        store.addBoostReport(testReport("r1", BoostStatus.COMPLETED, completedAtMillis = 2_000))
        val history = store.boostHistory.first()
        assertEquals(1, history.size)
        assertEquals(BoostStatus.COMPLETED, history.single().status)
        assertEquals(2_000L, history.single().completedAtMillis)
    }
}
