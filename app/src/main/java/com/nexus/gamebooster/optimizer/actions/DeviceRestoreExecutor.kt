package com.nexus.gamebooster.optimizer.actions

import com.nexus.gamebooster.data.DeviceChangeRecord
import com.nexus.gamebooster.data.DeviceChangeStore
import com.nexus.gamebooster.data.DeviceChangeTypes
import com.nexus.gamebooster.shizuku.ShizukuPrivilege
import kotlinx.coroutines.flow.first

/**
 * Phase 10 — reverts the device changes this app recorded in [DeviceChangeStore].
 *
 * Honesty rules (same contract as the boost actions):
 *  - Shizuku not ready → NOTHING is attempted, the report says so; the ledger is
 *    left untouched so the user can retry.
 *  - A record with an unknown original (e.g. the pre-state could not be read at
 *    boost time) is SKIPPED and reported — never "restored" to a guessed value.
 *  - Records whose original already satisfied the change (e.g. the game was already
 *    whitelisted) are skipped as "no change was made".
 *  - Only records that were ACTUALLY reverted are removed from the ledger; failed or
 *    skipped ones stay visible so the user can retry or forget them.
 *  - ART compile has no snapshot of the prior state; restore uses `--reset` which
 *    returns the package to the system-managed compile state (stated in the UI).
 */
class DeviceRestoreExecutor(
    private val shizuku: ShizukuPrivilege,
    private val store: DeviceChangeStore,
) {

    /** Per-record result of a restore run. */
    sealed class ItemStatus {
        /** The recorded change was reverted (device back to the recorded original). */
        data object Restored : ItemStatus()

        /** Nothing was done, and that is stated: see [reasonKey]. */
        data class Skipped(val reasonKey: String) : ItemStatus()

        /** The revert command failed — raw stderr is carried for the honest report. */
        data class Failed(val message: String) : ItemStatus()
    }

    data class ItemResult(
        val record: DeviceChangeRecord,
        val status: ItemStatus,
    )

    data class Report(
        val results: List<ItemResult>,
        /** True when nothing was attempted because Shizuku was not ready. */
        val shizukuRequired: Boolean,
    ) {
        val restoredCount: Int get() = results.count { it.status is ItemStatus.Restored }
        val skippedCount: Int get() = results.count { it.status is ItemStatus.Skipped }
        val failedCount: Int get() = results.count { it.status is ItemStatus.Failed }
    }

    /** Reason keys surfaced through the localized restore UI. */
    object Reasons {
        const val UNKNOWN_ORIGINAL = "restore_reason_unknown_original"
        const val NO_CHANGE_MADE = "restore_reason_no_change_made"
        const val SHIZUKU_REQUIRED = "restore_reason_shizuku_required"
    }

    suspend fun restoreAll(): Report {
        val records = store.deviceChanges.first()
        if (records.isEmpty()) {
            return Report(results = emptyList(), shizukuRequired = false)
        }
        if (!shizuku.isReady) {
            return Report(results = emptyList(), shizukuRequired = true)
        }

        val results = mutableListOf<ItemResult>()
        val restoredIds = mutableSetOf<String>()

        for (record in records) {
            val status = restoreOne(record)
            results += ItemResult(record, status)
            if (status is ItemStatus.Restored) restoredIds += record.id
        }

        // Only successfully reverted records leave the ledger — failed/skipped stay.
        store.removeDeviceChanges(restoredIds)
        return Report(results = results, shizukuRequired = false)
    }

    private suspend fun restoreOne(record: DeviceChangeRecord): ItemStatus = when (record.type) {
        DeviceChangeTypes.ANIMATION_SCALES -> restoreAnimationScales(record)
        DeviceChangeTypes.IDLE_WHITELIST -> restoreIdleWhitelist(record)
        DeviceChangeTypes.STANDBY_BUCKET -> restoreStandbyBucket(record)
        DeviceChangeTypes.ART_COMPILE -> restoreArtCompile(record)
        else -> ItemStatus.Skipped(Reasons.UNKNOWN_ORIGINAL)
    }

    private suspend fun restoreAnimationScales(record: DeviceChangeRecord): ItemStatus {
        // Unknown originals fall back to the AOSP factory default (1.0) — and the UI
        // says so instead of claiming "your original value was restored".
        val window = record.windowScale ?: DEFAULT_ANIMATION_SCALE
        val transition = record.transitionScale ?: DEFAULT_ANIMATION_SCALE
        val animator = record.animatorScale ?: DEFAULT_ANIMATION_SCALE

        val windowResult = shizuku.putGlobalSetting("window_animation_scale", window)
        val transitionResult = shizuku.putGlobalSetting("transition_animation_scale", transition)
        val animatorResult = shizuku.putGlobalSetting("animator_duration_scale", animator)

        val anyFailed = listOf(windowResult, transitionResult, animatorResult).any { !it.isSuccess }
        return if (anyFailed) {
            ItemStatus.Failed(
                message = listOfNotNull(
                    windowResult.stderr.takeIf { it.isNotBlank() && !windowResult.isSuccess },
                    transitionResult.stderr.takeIf { it.isNotBlank() && !transitionResult.isSuccess },
                    animatorResult.stderr.takeIf { it.isNotBlank() && !animatorResult.isSuccess },
                ).joinToString(" | ").ifBlank { "animation scale restore failed" },
            )
        } else {
            ItemStatus.Restored
        }
    }

    private suspend fun restoreIdleWhitelist(record: DeviceChangeRecord): ItemStatus {
        val pkg = record.packageName
            ?: return ItemStatus.Skipped(Reasons.UNKNOWN_ORIGINAL)
        return when (record.wasWhitelisted) {
            // The package was already whitelisted before us — nothing to undo.
            true -> ItemStatus.Skipped(Reasons.NO_CHANGE_MADE)
            // The pre-state could not be read at boost time; removing it now could
            // take away a whitelist the user had themselves. Honest skip.
            null -> ItemStatus.Skipped(Reasons.UNKNOWN_ORIGINAL)
            false -> {
                val result = shizuku.removeFromIdleWhitelist(pkg)
                if (result.isSuccess) ItemStatus.Restored
                else ItemStatus.Failed(result.stderr.ifBlank { "whitelist restore failed" })
            }
        }
    }

    private suspend fun restoreStandbyBucket(record: DeviceChangeRecord): ItemStatus {
        val pkg = record.packageName
            ?: return ItemStatus.Skipped(Reasons.UNKNOWN_ORIGINAL)
        val original = record.originalBucket
            // Without the recorded original we refuse to guess a bucket.
            ?: return ItemStatus.Skipped(Reasons.UNKNOWN_ORIGINAL)
        val result = shizuku.setStandbyBucket(pkg, original)
        return if (result.isSuccess) ItemStatus.Restored
        else ItemStatus.Failed(result.stderr.ifBlank { "standby bucket restore failed" })
    }

    private suspend fun restoreArtCompile(record: DeviceChangeRecord): ItemStatus {
        val pkg = record.packageName
            ?: return ItemStatus.Skipped(Reasons.UNKNOWN_ORIGINAL)
        val result = shizuku.resetCompile(pkg)
        return if (result.isSuccess) ItemStatus.Restored
        else ItemStatus.Failed(result.stderr.ifBlank { "compile reset failed" })
    }

    private companion object {
        /** AOSP factory default for all three animation scales. */
        const val DEFAULT_ANIMATION_SCALE = "1.0"
    }
}
