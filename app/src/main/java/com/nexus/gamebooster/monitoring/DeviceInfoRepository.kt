package com.nexus.gamebooster.monitoring

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.nexus.gamebooster.data.deviceIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Real, permission-free device facts. Every field comes from public platform APIs
 * (`android.os.Build`, `Settings.Global.DEVICE_NAME`) — nothing is inferred or invented.
 * `null` fields mean "the device did not report this", and the UI hides those rows
 * instead of guessing.
 */
data class DeviceFacts(
    val manufacturer: String,
    val model: String,
    /** User-facing device name from Settings.Global; null when not reported. */
    val deviceName: String?,
    val androidVersion: String,
    val sdkLevel: Int,
    /** Build.VERSION.SECURITY_PATCH (API 23+); null when blank (e.g. some emulators). */
    val securityPatch: String?,
)

/**
 * Real device info + snapshot-style battery/memory reads. Battery and memory go
 * through [SystemReads] so they are identical to what the Monitor screen shows.
 */
interface DeviceInfoRepository {
    fun deviceFacts(): DeviceFacts
    fun battery(): BatterySnapshot
    fun memory(): MemorySnapshot
    suspend fun refreshMemory(): MemorySnapshot
}

class AndroidDeviceInfoRepository(
    private val appContext: Context,
) : DeviceInfoRepository {

    override fun deviceFacts(): DeviceFacts {
        // Reuse the Phase 1 Build wrapper — no duplicated Build.MODEL / VERSION reads.
        val identity = deviceIdentity()
        val deviceName = runCatching {
            Settings.Global.getString(appContext.contentResolver, Settings.Global.DEVICE_NAME)
        }.getOrNull()
        val securityPatch = Build.VERSION.SECURITY_PATCH?.takeIf { it.isNotBlank() }
        return DeviceFacts(
            manufacturer = identity.manufacturer,
            model = identity.model,
            deviceName = deviceName,
            androidVersion = identity.androidVersion,
            sdkLevel = identity.sdkInt,
            securityPatch = securityPatch,
        )
    }

    override fun battery(): BatterySnapshot = SystemReads.readBattery(appContext)

    override fun memory(): MemorySnapshot = SystemReads.readMemory(appContext)

    override suspend fun refreshMemory(): MemorySnapshot = withContext(Dispatchers.IO) {
        SystemReads.readMemory(appContext)
    }
}
