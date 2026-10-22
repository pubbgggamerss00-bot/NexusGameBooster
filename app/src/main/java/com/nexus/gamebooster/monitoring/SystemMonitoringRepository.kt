package com.nexus.gamebooster.monitoring

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * NOTE: `MonitoringSnapshot` and the `MonitoringRepository` interface live in
 * `MonitoringRepository.kt` (single declaration — Phase 2 briefly had duplicates in
 * this file, which would be a redeclaration compile error; consolidated in Phase 3).
 *
 * Thin facade over [SystemReads] (the single source of truth) so the Monitor screen
 * and the Home device-facts repository always agree.
 */
class SystemMonitoringRepository(
    private val appContext: Context,
) : MonitoringRepository {

    override suspend fun refresh(): MonitoringSnapshot = withContext(Dispatchers.Default) {
        val battery = SystemReads.readBattery(appContext)
        val thermal = SystemReads.readThermal(appContext)
        val headroom = SystemReads.readThermalHeadroom(appContext)
        val memory = SystemReads.readMemory(appContext)
        val cpu = SystemReads.readCpu()
        // Real wall-clock stamp taken when the reads have completed — the honest
        // "last updated" time for the UI.
        MonitoringSnapshot(
            battery = battery,
            thermal = thermal,
            headroom = headroom,
            memory = memory,
            cpu = cpu,
            timestampMillis = System.currentTimeMillis(),
        )
    }

    /**
     * Live battery Flow. The receiver inside is registered only while this Flow is
     * collected and unregistered on cancellation — collection belongs in a
     * foreground-aware scope (see the interface contract), never in a Service.
     */
    override fun batteryUpdates(): Flow<BatterySnapshot> = SystemReads.batteryUpdates(appContext)
}
