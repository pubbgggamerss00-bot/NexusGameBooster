package com.nexus.gamebooster.data

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Phase 10 — persistent ledger of device changes this app actually made.
 *
 * Every device-mutating boost action first READS the current value (animation scales,
 * standby bucket, idle-whitelist membership) and records it here BEFORE writing, so a
 * later restore can put back the user's REAL original value instead of a guess.
 *
 * Honest scope — what is recorded (and therefore restorable):
 *  - [DeviceChangeTypes.ANIMATION_SCALES]: window/transition/animator scales (one
 *    record for the whole device; the FIRST original values ever recorded are kept —
 *    later boosts never overwrite them with our own 0.0).
 *  - [DeviceChangeTypes.IDLE_WHITELIST]: the game was added to the battery-optimization
 *    whitelist. Only recorded when the package was NOT whitelisted before us
 *    ([wasWhitelisted] = false). `null` means the pre-state could not be read —
 *    restore honestly reports "unknown original" and leaves it alone.
 *  - [DeviceChangeTypes.STANDBY_BUCKET]: the game's standby bucket was pinned to
 *    "active". [originalBucket] carries the bucket name read before the change
 *    (null → unknown original → restore skips honestly).
 *  - [DeviceChangeTypes.ART_COMPILE]: the game was ART-compiled with a boost profile.
 *    The exact prior compile state cannot be snapshotted; restore uses
 *    `cmd package compile --reset` which returns the package to the system-managed
 *    state (documented honestly in the UI).
 *
 * NOT recorded on purpose (not restorable, transient by design):
 *  - force-stopped background apps (they restart when the user opens them),
 *  - trimmed system caches (the system refills them as needed).
 */
@Serializable
data class DeviceChangeRecord(
    val id: String,
    @SerialName("type") val type: String,
    /** Target package for per-package changes; null for device-wide (animation scales). */
    @SerialName("package") val packageName: String? = null,
    /** ANIMATION_SCALES: original scale values ("0", "1.0", …); null = unknown original. */
    @SerialName("windowScale") val windowScale: String? = null,
    @SerialName("transitionScale") val transitionScale: String? = null,
    @SerialName("animatorScale") val animatorScale: String? = null,
    /** IDLE_WHITELIST: false = we added it (restorable); true = already whitelisted; null = unknown. */
    @SerialName("wasWhitelisted") val wasWhitelisted: Boolean? = null,
    /** STANDBY_BUCKET: original bucket name (e.g. "rare"); null = unknown original. */
    @SerialName("originalBucket") val originalBucket: String? = null,
    @SerialName("createdAtMillis") val createdAtMillis: Long,
) {
    /**
     * Upsert key: at most ONE record per key is kept. Animation scales are device-wide
     * (package = null); the per-package types are keyed by their package. This is what
     * stops a second boost from replacing the user's true original values with values
     * the app itself had already changed.
     */
    val upsertKey: String get() = "$type:${packageName.orEmpty()}"
}

/** Stable string values persisted in [DeviceChangeRecord.type]. */
object DeviceChangeTypes {
    const val ANIMATION_SCALES = "animation_scales"
    const val IDLE_WHITELIST = "idle_whitelist"
    const val STANDBY_BUCKET = "standby_bucket"
    const val ART_COMPILE = "art_compile"
}

/**
 * Persistence seam for the device-change ledger (mirrors [SelectedGamesStore] /
 * [BoostHistoryStore] so tests can use fakes without DataStore or Android types).
 */
interface DeviceChangeStore {

    /** Newest-first decoded ledger; corrupt entries are skipped, never surfaced as fakes. */
    val deviceChanges: Flow<List<DeviceChangeRecord>>

    /**
     * Inserts [record] only when no record with the same [DeviceChangeRecord.upsertKey]
     * exists yet — the first recorded original always wins.
     */
    suspend fun addDeviceChangeIfAbsent(record: DeviceChangeRecord)

    /** Removes successfully restored records by id. */
    suspend fun removeDeviceChanges(ids: Set<String>)

    /** Forgets the whole ledger WITHOUT touching the device (user-explicit "forget"). */
    suspend fun clearDeviceChanges()
}
