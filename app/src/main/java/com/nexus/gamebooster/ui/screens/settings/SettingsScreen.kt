package com.nexus.gamebooster.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.nexus.gamebooster.BuildConfig
import com.nexus.gamebooster.R
import com.nexus.gamebooster.core.ServiceLocator
import com.nexus.gamebooster.core.VmFactory
import com.nexus.gamebooster.data.DeviceChangeRecord
import com.nexus.gamebooster.data.DeviceChangeTypes
import com.nexus.gamebooster.data.Language
import com.nexus.gamebooster.data.ThemeMode
import com.nexus.gamebooster.optimizer.actions.DeviceRestoreExecutor.ItemStatus
import com.nexus.gamebooster.optimizer.actions.DeviceRestoreExecutor.Reasons
import com.nexus.gamebooster.optimizer.RootGameModeController
import com.nexus.gamebooster.shizuku.ShizukuPrivilege
import com.nexus.gamebooster.ui.arcade.ArcadeButton
import com.nexus.gamebooster.ui.arcade.ArcadeChip
import com.nexus.gamebooster.ui.arcade.ArcadeField
import com.nexus.gamebooster.ui.arcade.ArcadeHead
import com.nexus.gamebooster.ui.arcade.ArcadeKvRow
import com.nexus.gamebooster.ui.arcade.ArcadeOutlineButton
import com.nexus.gamebooster.ui.arcade.ArcadePanel
import com.nexus.gamebooster.ui.arcade.ArcadePanelVariant
import com.nexus.gamebooster.ui.arcade.ArcadeSegment
import com.nexus.gamebooster.ui.arcade.ArcadeSegmented
import com.nexus.gamebooster.ui.arcade.ArcadeSwitch
import com.nexus.gamebooster.ui.arcade.ArcadeTint
import com.nexus.gamebooster.ui.arcade.NgbArcadeColors
import com.nexus.gamebooster.ui.components.NgbScreenContainer
import com.nexus.gamebooster.ui.theme.NgbBodyFont
import com.nexus.gamebooster.update.UpdateTools

/**
 * Settings — v1.2.1 "ARCADE HUD" restyle (the reference's renderSettings):
 * chamfered [ArcadePanel] groups, [ArcadeSegmented] language/theme selectors,
 * an [ArcadeSwitch] for reduce motion, HUD [ArcadeField] inputs, 3D-lip
 * [ArcadeButton]s and dashed [ArcadeKvRow]s. All behavior (language/theme/
 * reduce-motion persistence, root game mode, restore ledger, update check,
 * tickets, reset confirmation) is unchanged.
 */
@Composable
fun SettingsScreen() {
    val vm: SettingsViewModel = viewModel(factory = VmFactory { ServiceLocator.settingsViewModel() })
    val state by vm.uiState.collectAsStateWithLifecycle()
    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    var showRestoreDialog by rememberSaveable { mutableStateOf(false) }
    var showForgetDialog by rememberSaveable { mutableStateOf(false) }
    var rootPackage by rememberSaveable { mutableStateOf("") }
    var rootStatus by rememberSaveable { mutableStateOf<String?>(null) }
    var rootBusy by rememberSaveable { mutableStateOf(false) }
    val rootController = remember { RootGameModeController() }
    val scope = rememberCoroutineScope()

    NgbScreenContainer {
        // Returns from the system "Install unknown apps" page — resume the update
        // flow exactly once, based on the REAL switch state.
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            vm.onScreenResumed()
        }
        ArcadeHead(code = "SETTINGS", title = stringResource(R.string.settings_headline))
        Spacer(Modifier.height(16.dp))

        LanguageGroupPanel(
            state = state,
            onLanguage = vm::setLanguage,
        )
        Spacer(Modifier.height(12.dp))

        AppearanceGroupPanel(
            state = state,
            onTheme = vm::setTheme,
            onReduceMotion = vm::setReduceMotion,
        )
        Spacer(Modifier.height(12.dp))

        RootGameModePanel(
            rootPackage = rootPackage,
            onPackageChange = { rootPackage = it.trim() },
            rootStatus = rootStatus,
            rootBusy = rootBusy,
            onCheckRoot = {
                rootBusy = true
                scope.launch {
                    val result = rootController.checkRoot()
                    rootStatus = result.message
                    rootBusy = false
                }
            },
            onPerformance = {
                rootBusy = true
                scope.launch {
                    val result = rootController.setPerformanceMode(rootPackage, true)
                    rootStatus = result.message
                    rootBusy = false
                }
            },
            onStandard = {
                rootBusy = true
                scope.launch {
                    val result = rootController.setPerformanceMode(rootPackage, false)
                    rootStatus = result.message
                    rootBusy = false
                }
            },
        )
        Spacer(Modifier.height(12.dp))

        RestoreDeviceChangesCard(
            state = state,
            onActivateShizuku = vm::activateShizukuForRestore,
            onRestoreClick = { showRestoreDialog = true },
            onForgetClick = { showForgetDialog = true },
        )
        Spacer(Modifier.height(12.dp))

        AppUpdatesCard(
            state = state,
            onCheck = vm::checkForUpdates,
            onDownload = vm::downloadAndInstallUpdate,
            onAllowInstall = vm::grantInstallPermission,
        )
        Spacer(Modifier.height(12.dp))

        // Phase 12 (1.0.0): support tickets — every entry point re-verifies the
        // typed key against the active app session via the API (server-owned
        // check); see [TicketSupportSection] for the dialog flow.
        ArcadePanel {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.ticket_section_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(10.dp))
                TicketSupportSection()
            }
        }
        Spacer(Modifier.height(12.dp))

        ArcadePanel {
            Column(Modifier.fillMaxWidth()) {
                ArcadeKvRow(
                    label = stringResource(R.string.settings_version),
                    value = BuildConfig.VERSION_NAME,
                )
                Spacer(Modifier.height(10.dp))
                ArcadeKvRow(
                    label = stringResource(R.string.settings_app_id),
                    value = BuildConfig.APPLICATION_ID,
                )
                Spacer(Modifier.height(10.dp))
                ArcadeKvRow(
                    label = stringResource(R.string.settings_permissions),
                    value = stringResource(R.string.settings_permissions_value),
                    valueFont = NgbBodyFont,
                )
                // Shown only once a real scan has completed — the count is then a fact,
                // not an estimate.
                state.visibleAppCount?.let { count ->
                    Spacer(Modifier.height(10.dp))
                    ArcadeKvRow(
                        label = stringResource(R.string.settings_visible_apps),
                        value = stringResource(R.string.settings_visible_apps_value, count),
                        valueFont = NgbBodyFont,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        ArcadeButton(
            onClick = { showResetDialog = true },
            tint = ArcadeTint.DANGER,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_reset))
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.settings_reset_title)) },
            text = { Text(stringResource(R.string.settings_reset_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    vm.resetAll()
                }) {
                    Text(stringResource(R.string.settings_reset_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text(stringResource(R.string.settings_restore_confirm_title)) },
            text = { Text(stringResource(R.string.settings_restore_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreDialog = false
                    vm.restoreDeviceChanges()
                }) {
                    Text(stringResource(R.string.settings_restore_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showForgetDialog) {
        AlertDialog(
            onDismissRequest = { showForgetDialog = false },
            title = { Text(stringResource(R.string.settings_restore_forget_title)) },
            text = { Text(stringResource(R.string.settings_restore_forget_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showForgetDialog = false
                    vm.forgetDeviceRecords()
                }) {
                    Text(stringResource(R.string.settings_restore_forget))
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgetDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    // Honest result of the last restore run (or the honest "Shizuku required" report).
    state.lastRestoreReport?.let { report ->
        AlertDialog(
            onDismissRequest = vm::dismissRestoreReport,
            title = { Text(stringResource(R.string.settings_restore_result_title)) },
            text = {
                Column {
                    if (report.shizukuRequired) {
                        Text(
                            text = stringResource(R.string.settings_restore_result_shizuku_required),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        Text(
                            text = stringResource(
                                R.string.settings_restore_result_summary,
                                report.restoredCount,
                                report.skippedCount,
                                report.failedCount,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        val nonRestored = report.results.filter { it.status !is ItemStatus.Restored }
                        if (nonRestored.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Column(
                                modifier = Modifier
                                    .heightIn(max = 220.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                nonRestored.forEach { item ->
                                    val label = restoreRecordTitle(item.record)
                                    RestoreStatusText(item.status, labelPrefix = label)
                                    Spacer(Modifier.height(4.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = vm::dismissRestoreReport) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    }

    // One-shot hint after the Activate button (reuses the honest Home messages).
    state.shizukuHintRes?.let { hintRes ->
        AlertDialog(
            onDismissRequest = vm::dismissShizukuHint,
            title = { Text(stringResource(R.string.shizuku_card_title)) },
            text = { Text(stringResource(hintRes)) },
            confirmButton = {
                TextButton(onClick = vm::dismissShizukuHint) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    }
}

/**
 * Language group (the reference's first settings panel): the section title, one
 * [ArcadeSegmented] for فارسی / English / System (ids "fa"/"en"/"sys") and the
 * honest note. Selection state comes from the REAL persisted language.
 */
@Composable
private fun LanguageGroupPanel(
    state: SettingsUiState,
    onLanguage: (Language) -> Unit,
) {
    ArcadePanel {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.settings_section_language),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(10.dp))
            ArcadeSegmented(
                options = listOf(
                    ArcadeSegment(id = "fa", label = stringResource(R.string.settings_language_fa)),
                    ArcadeSegment(id = "en", label = stringResource(R.string.settings_language_en)),
                    ArcadeSegment(id = "sys", label = stringResource(R.string.settings_language_system)),
                ),
                selected = when (state.language) {
                    Language.PERSIAN -> "fa"
                    Language.ENGLISH -> "en"
                    Language.SYSTEM -> "sys"
                },
                onSelect = { id ->
                    onLanguage(
                        when (id) {
                            "fa" -> Language.PERSIAN
                            "en" -> Language.ENGLISH
                            else -> Language.SYSTEM
                        },
                    )
                },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_language_note),
                style = MaterialTheme.typography.labelSmall,
                color = NgbArcadeColors.Muted,
            )
        }
    }
}

/**
 * Appearance group: the navy/AMOLED theme segmented control (ids "navy"/"amoled")
 * and the reduce-motion row. The WHOLE row stays toggleable (Role.Switch) so
 * TalkBack treats it as one control; the [ArcadeSwitch] handles taps on itself.
 */
@Composable
private fun AppearanceGroupPanel(
    state: SettingsUiState,
    onTheme: (ThemeMode) -> Unit,
    onReduceMotion: (Boolean) -> Unit,
) {
    ArcadePanel {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.settings_section_appearance),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(10.dp))
            ArcadeSegmented(
                options = listOf(
                    ArcadeSegment(id = "navy", label = stringResource(R.string.settings_theme_dark_navy)),
                    ArcadeSegment(id = "amoled", label = stringResource(R.string.settings_theme_amoled)),
                ),
                selected = when (state.theme) {
                    ThemeMode.DARK_NAVY -> "navy"
                    ThemeMode.AMOLED_BLACK -> "amoled"
                },
                onSelect = { id ->
                    onTheme(
                        if (id == "amoled") ThemeMode.AMOLED_BLACK else ThemeMode.DARK_NAVY,
                    )
                },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_theme_note),
                style = MaterialTheme.typography.labelSmall,
                color = NgbArcadeColors.Muted,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(
                        value = state.reduceMotion,
                        role = Role.Switch,
                        onValueChange = onReduceMotion,
                    )
                    .padding(vertical = 8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_reduce_motion),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(R.string.settings_reduce_motion_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = NgbArcadeColors.Muted,
                    )
                }
                Spacer(Modifier.width(12.dp))
                ArcadeSwitch(checked = state.reduceMotion, onCheckedChange = onReduceMotion)
            }
        }
    }
}

/**
 * Optional root game mode (the app's existing card, arcade-skinned): the HUD
 * package field plus the check/performance/standard actions as arcade buttons.
 * Root permission is still requested only when a button is tapped, and the
 * honest status line still reports the REAL controller result.
 */
@Composable
private fun RootGameModePanel(
    rootPackage: String,
    onPackageChange: (String) -> Unit,
    rootStatus: String?,
    rootBusy: Boolean,
    onCheckRoot: () -> Unit,
    onPerformance: () -> Unit,
    onStandard: () -> Unit,
) {
    ArcadePanel(variant = ArcadePanelVariant.FLAT) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.root_game_mode_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.root_game_mode_note),
                style = MaterialTheme.typography.bodySmall,
                color = NgbArcadeColors.Muted,
            )
            Spacer(Modifier.height(12.dp))
            ArcadeField(
                value = rootPackage,
                onValueChange = onPackageChange,
                hint = "com.example.game",
                isSearch = true,
            )
            Spacer(Modifier.height(10.dp))
            ArcadeButton(
                onClick = onCheckRoot,
                enabled = !rootBusy,
                tint = ArcadeTint.NEON,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.root_check_button))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                ArcadeButton(
                    onClick = onPerformance,
                    enabled = !rootBusy,
                    tint = ArcadeTint.AZURE,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.root_performance_button))
                }
                Spacer(Modifier.width(8.dp))
                ArcadeButton(
                    onClick = onStandard,
                    enabled = !rootBusy,
                    tint = ArcadeTint.LIME,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.root_standard_button))
                }
            }
            Spacer(Modifier.height(8.dp))
            if (rootBusy) CircularProgressIndicator(modifier = Modifier.size(20.dp))
            Text(
                text = rootStatus ?: stringResource(R.string.root_game_mode_idle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

/**
 * Phase 10 card: every device change the app recorded at boost time, with a one-tap
 * honest restore. Empty ledger → an honest "nothing recorded" state; Shizuku not
 * ready → honest status + the same one-tap Activate ladder as Home.
 */
@Composable
private fun RestoreDeviceChangesCard(
    state: SettingsUiState,
    onActivateShizuku: () -> Unit,
    onRestoreClick: () -> Unit,
    onForgetClick: () -> Unit,
) {
    ArcadePanel {
        Column(Modifier.fillMaxWidth()) {
            // Shizuku precondition row — the restore runs over the same privileged bridge.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.settings_restore_shizuku_status),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = when (state.shizukuStatus) {
                        ShizukuPrivilege.Status.READY ->
                            stringResource(R.string.shizuku_status_ready)
                        ShizukuPrivilege.Status.PERMISSION_REQUIRED ->
                            stringResource(R.string.shizuku_status_permission_required)
                        ShizukuPrivilege.Status.NOT_RUNNING ->
                            stringResource(R.string.shizuku_status_not_running)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (state.shizukuStatus == ShizukuPrivilege.Status.READY) {
                        NgbArcadeColors.Neon
                    } else {
                        NgbArcadeColors.Muted
                    },
                )
            }
            if (state.shizukuStatus != ShizukuPrivilege.Status.READY) {
                Spacer(Modifier.height(8.dp))
                ArcadeOutlineButton(onClick = onActivateShizuku, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.shizuku_activate))
                }
            }
            Spacer(Modifier.height(12.dp))

            if (state.deviceChanges.isEmpty()) {
                // Honest empty state: no ledger entries means the app recorded nothing.
                Text(
                    text = stringResource(R.string.settings_restore_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = NgbArcadeColors.Muted,
                )
            } else {
                Text(
                    text = stringResource(R.string.settings_restore_count_value, state.deviceChanges.size),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(4.dp))
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    state.deviceChanges.forEach { record ->
                        RestoreRecordRow(
                            record = record,
                            status = state.lastStatuses[record.id],
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                ArcadeOutlineButton(
                    onClick = onRestoreClick,
                    enabled = !state.restoreBusy,
                    tint = ArcadeTint.LIME,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.restoreBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.settings_restore_button))
                }
                TextButton(onClick = onForgetClick, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_restore_forget))
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_restore_note),
                style = MaterialTheme.typography.labelSmall,
                color = NgbArcadeColors.Muted,
            )
            Text(
                text = stringResource(R.string.settings_restore_reset_note),
                style = MaterialTheme.typography.labelSmall,
                color = NgbArcadeColors.Muted,
            )
        }
    }
}

@Composable
private fun RestoreRecordRow(record: DeviceChangeRecord, status: ItemStatus?) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = restoreRecordTitle(record),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = restoreRecordSubtitle(record),
            style = MaterialTheme.typography.labelSmall,
            color = NgbArcadeColors.Muted,
        )
        status?.let { RestoreStatusText(it, labelPrefix = null) }
    }
}

/** Localized one-line status ("Restored" / "Skipped: …" / "Failed: <raw stderr>"). */
@Composable
private fun RestoreStatusText(status: ItemStatus, labelPrefix: String?) {
    val (text, color) = when (status) {
        is ItemStatus.Restored ->
            stringResource(R.string.settings_restore_status_restored) to
                NgbArcadeColors.Neon

        is ItemStatus.Skipped ->
            stringResource(
                R.string.settings_restore_status_skipped,
                restoreReasonLabel(status.reasonKey),
            ) to NgbArcadeColors.Muted

        is ItemStatus.Failed ->
            stringResource(R.string.settings_restore_status_failed, status.message) to
                NgbArcadeColors.Danger
    }
    val line = if (labelPrefix != null) "$labelPrefix — $text" else text
    Text(
        text = line,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 2,
    )
}

@Composable
private fun restoreReasonLabel(reasonKey: String): String = when (reasonKey) {
    Reasons.UNKNOWN_ORIGINAL -> stringResource(R.string.settings_restore_reason_unknown_original)
    Reasons.NO_CHANGE_MADE -> stringResource(R.string.settings_restore_reason_no_change_made)
    Reasons.SHIZUKU_REQUIRED -> stringResource(R.string.settings_restore_reason_shizuku_required)
    else -> reasonKey
}

@Composable
private fun restoreRecordTitle(record: DeviceChangeRecord): String = when (record.type) {
    DeviceChangeTypes.ANIMATION_SCALES -> stringResource(R.string.settings_restore_item_anim)
    DeviceChangeTypes.IDLE_WHITELIST ->
        stringResource(R.string.settings_restore_item_whitelist, record.packageName.orEmpty())
    DeviceChangeTypes.STANDBY_BUCKET ->
        stringResource(R.string.settings_restore_item_bucket, record.packageName.orEmpty())
    DeviceChangeTypes.ART_COMPILE ->
        stringResource(R.string.settings_restore_item_compile, record.packageName.orEmpty())
    else -> record.type
}

@Composable
private fun restoreRecordSubtitle(record: DeviceChangeRecord): String = when (record.type) {
    DeviceChangeTypes.ANIMATION_SCALES -> stringResource(
        R.string.settings_restore_item_anim_subtitle,
        record.windowScale ?: stringResource(R.string.settings_restore_item_unknown_value),
        record.transitionScale ?: stringResource(R.string.settings_restore_item_unknown_value),
        record.animatorScale ?: stringResource(R.string.settings_restore_item_unknown_value),
    )

    DeviceChangeTypes.IDLE_WHITELIST ->
        stringResource(R.string.settings_restore_item_whitelist_subtitle)

    DeviceChangeTypes.STANDBY_BUCKET -> stringResource(
        R.string.settings_restore_item_bucket_subtitle,
        record.originalBucket?.let { localizedBucketName(it) }
            ?: stringResource(R.string.settings_restore_item_unknown_value),
    )

    DeviceChangeTypes.ART_COMPILE ->
        stringResource(R.string.settings_restore_item_compile_subtitle)

    else -> record.createdAtMillis.toString()
}

/** Localizes the known bucket names; unknown raw values are shown as-is (honest). */
@Composable
private fun localizedBucketName(name: String): String = when (name) {
    "active" -> stringResource(R.string.settings_restore_bucket_active)
    "working_set" -> stringResource(R.string.settings_restore_bucket_working_set)
    "frequent" -> stringResource(R.string.settings_restore_bucket_frequent)
    "rare" -> stringResource(R.string.settings_restore_bucket_rare)
    "restricted" -> stringResource(R.string.settings_restore_bucket_restricted)
    "exempted" -> stringResource(R.string.settings_restore_bucket_exempted)
    else -> name
}

/**
 * Phase 11 card: check the app's own GitHub releases for a newer version and, when
 * one exists, download + start the install entirely in-app. Every state comes from
 * a real network/system call — Idle means "not checked yet", never "up to date".
 */
@Composable
private fun AppUpdatesCard(
    state: SettingsUiState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onAllowInstall: () -> Unit,
) {
    ArcadePanel {
        Column(Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.settings_update_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                ArcadeChip(
                    text = stringResource(R.string.settings_version) + " " + BuildConfig.VERSION_NAME,
                    tint = NgbArcadeColors.Lime,
                )
            }
            Spacer(Modifier.height(12.dp))

            val checking = state.update is UpdateCheckUi.Checking
            val downloading = state.update is UpdateCheckUi.Downloading
            ArcadeButton(
                onClick = onCheck,
                enabled = !checking && !downloading,
                tint = ArcadeTint.AZURE,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = if (checking) {
                        stringResource(R.string.settings_update_checking)
                    } else {
                        stringResource(R.string.settings_update_check)
                    },
                )
            }

            when (val update = state.update) {
                is UpdateCheckUi.UpToDate -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = NgbArcadeColors.Neon,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.settings_update_uptodate, update.remoteVersion),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                is UpdateCheckUi.Available -> {
                    Text(
                        text = stringResource(R.string.settings_update_available, update.versionName),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (update.whatsNew != null) {
                        Text(
                            text = stringResource(R.string.settings_update_whatsnew),
                            style = MaterialTheme.typography.labelMedium,
                            color = NgbArcadeColors.Muted,
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Text(
                                text = update.whatsNew,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    } else {
                        // The release ships no friendly notes file — say so, never invent.
                        Text(
                            text = stringResource(R.string.settings_update_notes_missing),
                            style = MaterialTheme.typography.bodySmall,
                            color = NgbArcadeColors.Muted,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    ArcadeOutlineButton(
                        onClick = onDownload,
                        enabled = !downloading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.settings_update_download))
                    }
                }

                is UpdateCheckUi.Downloading -> {
                    val total = update.totalBytes
                    if (total > 0) {
                        LinearProgressIndicator(
                            progress = {
                                (update.downloadedBytes.toFloat() / total).coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = stringResource(
                                R.string.settings_update_downloading,
                                UpdateTools.formatMb(update.downloadedBytes),
                                UpdateTools.formatMb(total),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = NgbArcadeColors.Muted,
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = stringResource(
                                R.string.settings_update_downloaded,
                                UpdateTools.formatMb(update.downloadedBytes),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = NgbArcadeColors.Muted,
                        )
                    }
                }

                is UpdateCheckUi.Failed -> Text(
                    text = stringResource(update.errorRes, *update.args.toTypedArray()),
                    style = MaterialTheme.typography.bodySmall,
                    color = NgbArcadeColors.Danger,
                )

                UpdateCheckUi.Idle, UpdateCheckUi.Checking -> Unit
            }

            if (state.updateNeedsInstallPermission) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_update_install_permission_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = NgbArcadeColors.Danger,
                )
                ArcadeOutlineButton(onClick = onAllowInstall, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_update_allow))
                }
            }

            state.updateHintRes?.let { hintRes ->
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = NgbArcadeColors.Neon,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(hintRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_update_note),
                style = MaterialTheme.typography.labelSmall,
                color = NgbArcadeColors.Muted,
            )
        }
    }
}
