package com.nexus.gamebooster.monitoring

import kotlinx.coroutines.flow.Flow

/**
 * One-shot result of reading everything this app can genuinely observe.
 *
 * [headroom] is the PowerManager thermal-headroom abstraction (API 30+, may be
 * "not reported" on many devices — see [ThermalHeadroomSnapshot]). [timestampMillis]
 * is the real wall-clock time (`System.currentTimeMillis()`) taken when the reads
 * finished; it is the honest "last updated" timestamp shown in the UI.
 */
data class MonitoringSnapshot(
    val battery: BatterySnapshot,
    val thermal: ThermalSnapshot,
    val headroom: ThermalHeadroomSnapshot,
    val memory: MemorySnapshot,
    val cpu: CpuSnapshot,
    val timestampMillis: Long,
)

interface MonitoringRepository {

    /**
     * One-shot read of all available metrics. No polling, no background loops —
     * screens call this explicitly (button or screen entry).
     */
    suspend fun refresh(): MonitoringSnapshot

    /**
     * Cold Flow of live battery snapshots (implemented by SystemMonitoringRepository
     * over `SystemReads.batteryUpdates`, a runtime-registered broadcast receiver).
     *
     * LIFECYCLE CONTRACT — for foreground, lifecycle-aware observation ONLY:
     * this Flow registers a system broadcast receiver for as long as it is being
     * collected and unregisters it when collection is cancelled (callbackFlow +
     * awaitClose). It must therefore be collected ONLY from a foreground-aware
     * scope (e.g. a ViewModel whose state the UI reads through
     * `collectAsStateWithLifecycle`, or `repeatOnLifecycle(STARTED)`), never from
     * `GlobalScope`, never from a Service, and never for background collection.
     * There is no manifest-declared receiver, no polling, and no wake lock.
     */
    fun batteryUpdates(): Flow<BatterySnapshot>
}
