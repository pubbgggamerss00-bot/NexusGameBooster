package com.nexus.gamebooster.optimizer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.nexus.gamebooster.data.SettingsRepository
import com.nexus.gamebooster.testing.testReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * REAL file-backed DataStore tests for the [BoostHistoryStore] implementation in
 * [SettingsRepository] (Phase 5 contract): newest-first ordering, the 20-report cap,
 * corrupt-JSON tolerance, clear, and upsert-by-id.
 *
 * The DataStore instance is the ONLY one alive per file: each test gets a fresh
 * TemporaryFolder file and the scope is cancelled in [tearDown] before anything else
 * touches disk (the Phase 6 two-instance contract — violating it hangs/crashes DataStore).
 *
 * These tests ARE executed for real via Gradle (`:app:testDebugUnitTest`) in this
 * sandbox — see worklog Task 14-d for the green run.
 */
class BoostHistoryStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: BoostHistoryStore

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
        val file = File(tmp.newFolder(), "boost_history_test.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        store = SettingsRepository(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun emptyByDefault() = runTest {
        assertTrue(store.boostHistory.first().isEmpty())
    }

    @Test
    fun add_retrievesNewestFirst() = runTest {
        store.addBoostReport(testReport(id = "old", completedAtMillis = 100))
        store.addBoostReport(testReport(id = "newest", completedAtMillis = 300))
        store.addBoostReport(testReport(id = "middle", completedAtMillis = 200))
        assertEquals(
            listOf("newest", "middle", "old"),
            store.boostHistory.first().map { it.id },
        )
    }

    @Test
    fun capAt20_dropsTheOldestReports() = runTest {
        for (i in 1..22) {
            store.addBoostReport(testReport(id = "r$i", completedAtMillis = i.toLong()))
        }
        val history = store.boostHistory.first()
        assertEquals(SettingsRepository.MAX_BOOST_HISTORY, history.size)
        // Newest-first: r22 … r3 survive, the two oldest (r1, r2) were dropped.
        assertEquals((22 downTo 3).map { "r$it" }, history.map { it.id })
    }

    @Test
    fun corruptJsonEntries_skippedOnRead_neverBreakTheHistory() = runTest {
        val report = testReport(id = "r1", completedAtMillis = 100)
        store.addBoostReport(report)
        val rawKey = stringSetPreferencesKey("boost_history")
        val raw = dataStore.data.first()[rawKey] ?: emptySet()
        assertEquals(1, raw.size)
        // Corrupt the stored set the way a broken write would: garbage + wrong-shape JSON.
        dataStore.edit { prefs ->
            prefs[rawKey] = raw + setOf("{not json", "}{", "{\"x\":1}")
        }
        // Only the one real report survives; garbage entries never crash or fake a report.
        assertEquals(listOf(report), store.boostHistory.first())
    }

    @Test
    fun clear_removesEveryReport() = runTest {
        store.addBoostReport(testReport(id = "r1", completedAtMillis = 1))
        store.addBoostReport(testReport(id = "r2", completedAtMillis = 2))
        assertEquals(2, store.boostHistory.first().size)
        store.clearBoostHistory()
        assertTrue(store.boostHistory.first().isEmpty())
    }

    @Test
    fun upsert_sameIdReplacesInsteadOfDuplicating() = runTest {
        store.addBoostReport(
            testReport(id = "same", completedAtMillis = 100, status = BoostStatus.COMPLETED),
        )
        store.addBoostReport(
            testReport(id = "other", completedAtMillis = 150, status = BoostStatus.PARTIAL_SUCCESS),
        )
        store.addBoostReport(
            testReport(id = "same", completedAtMillis = 500, status = BoostStatus.CANCELLED),
        )
        val history = store.boostHistory.first()
        assertEquals(2, history.size)
        // The re-added report replaced its previous version and sorted to the front.
        assertEquals("same", history.first().id)
        assertEquals(BoostStatus.CANCELLED, history.first().status)
        assertEquals(500L, history.first().completedAtMillis)
        assertEquals(listOf("other"), history.drop(1).map { it.id })
    }
}
