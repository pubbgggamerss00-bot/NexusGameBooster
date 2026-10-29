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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
import com.nexus.gamebooster.ui.components.NgbButton
import com.nexus.gamebooster.ui.components.NgbOutlinedButton
import com.nexus.gamebooster.ui.components.NgbPageHeader
import com.nexus.gamebooster.ui.components.NgbScreenContainer
import com.nexus.gamebooster.ui.components.SectionCard
import com.nexus.gamebooster.ui.theme.NgbChamferShape
import com.nexus.gamebooster.update.UpdateTools

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
        NgbPageHeader(title = stringResource(R.string.settings_headline))
        Spacer(Modifier.height(16.dp))

        SectionCard(
            title = stringResource(R.string.settings_section_language),
            icon = Icons.Outlined.Language,
        ) {
            SelectRow(
                label = stringResource(R.string.settings_language_system),
                selected = state.language == Language.SYSTEM,
                onClick = { vm.setLanguage(Language.SYSTEM) },
            )
            SelectRow(
                label = stringResource(R.string.settings_language_en),
                selected = state.language == Language.ENGLISH,
                onClick = { vm.setLanguage(Language.ENGLISH) },
            )
            SelectRow(
                label = stringResource(R.string.settings_language_fa),
                selected = state.language == Language.PERSIAN,
                onClick = { vm.setLanguage(Language.PERSIAN) },
            )
            Text(
                text = stringResource(R.string.settings_language_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))

        SectionCard(
            title = stringResource(R.string.settings_section_appearance),
            icon = Icons.Outlined.Palette,
        ) {
            Text(
                text = stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.titleSmall,
            )
            SelectRow(
                label = stringResource(R.string.settings_theme_dark_navy),
                selected = state.theme == ThemeMode.DARK_NAVY,
                onClick = { vm.setTheme(ThemeMode.DARK_NAVY) },
            )
            SelectRow(
                label = stringResource(R.string.settings_theme_amoled),
                selected = state.theme == ThemeMode.AMOLED_BLACK,
                onClick = { vm.setTheme(ThemeMode.AMOLED_BLACK) },
            )
            Text(
                text = stringResource(R.string.settings_theme_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            // The WHOLE row toggles (Role.Switch); the Switch itself is display-only
            // (onCheckedChange = null) so TalkBack treats the row as one control.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(
                        value = state.reduceMotion,
                        role = Role.Switch,
                        onValueChange = vm::setReduceMotion,
                    )
                    .padding(vertical = 8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_reduce_motion),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.settings_reduce_motion_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.reduceMotion, onCheckedChange = null)
            }
        }
        Spacer(Modifier.height(16.dp))

        SectionCard(
            title = stringResource(R.string.root_game_mode_title),
            icon = Icons.Outlined.SettingsBackupRestore,
        ) {
            Text(
                text = stringResource(R.string.root_game_mode_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = rootPackage,
                onValueChange = { rootPackage = it.trim() },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.root_game_package_label)) },
                placeholder = { Text("com.example.game") },
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                NgbButton(
                    enabled = !rootBusy,
                    onClick = {
                        rootBusy = true
                        scope.launch {
                            val result = rootController.checkRoot()
                            rootStatus = result.message
                            rootBusy = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.root_check_button)) }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                NgbOutlinedButton(
                    onClick = {
                        rootBusy = true
                        scope.launch {
                            val result = rootController.setPerformanceMode(rootPackage, true)
                            rootStatus = result.message
                            rootBusy = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.root_performance_button)) }
                Spacer(Modifier.width(8.dp))
                NgbOutlinedButton(
                    onClick = {
                        rootBusy = true
                        scope.launch {
                            val result = rootController.setPerformanceMode(rootPackage, false)
                            rootStatus = result.message
                            rootBusy = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.root_standard_button)) }
            }
            Spacer(Modifier.height(8.dp))
            if (rootBusy) CircularProgressIndicator(modifier = Modifier.size(20.dp))
            Text(
                text = rootStatus ?: stringResource(R.string.root_game_mode_idle),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(16.dp))

        RestoreDeviceChangesCard(
            state = state,
            onActivateShizuku = vm::activateShizukuForRestore,
            onRestoreClick = { showRestoreDialog = true },
            onForgetClick = { showForgetDialog = true },
        )
        Spacer(Modifier.height(16.dp))

        AppUpdatesCard(
            state = state,
            onCheck = vm::checkForUpdates,
            onDownload = vm::downloadAndInstallUpdate,
            onAllowInstall = vm::grantInstallPermission,
        )
        Spacer(Modifier.height(16.dp))

        // Phase 12 (1.0.0): support tickets — every entry point re-verifies the
        // typed key against the active app session via the API (server-owned
        // check); see [TicketSupportSection] for the dialog flow.
        SectionCard(
            title = stringResource(R.string.ticket_section_title),
            icon = Icons.Outlined.ConfirmationNumber,
        ) {
            TicketSupportSection()
        }
        Spacer(Modifier.height(16.dp))

        SectionCard(
            title = stringResource(R.string.settings_section_about),
            icon = Icons.Outlined.Info,
        ) {
            AboutRow(
                label = stringResource(R.string.settings_version),
                value = BuildConfig.VERSION_NAME,
            )
            AboutRow(
                label = stringResource(R.string.settings_app_id),
                value = BuildConfig.APPLICATION_ID,
            )
            AboutRow(
                label = stringResource(R.string.settings_permissions),
                value = stringResource(R.string.settings_permissions_value),
            )
            // Shown only once a real scan has completed — the count is then a fact,
            // not an estimate.
            state.visibleAppCount?.let { count ->
                AboutRow(
                    label = stringResource(R.string.settings_visible_apps),
                    value = stringResource(R.string.settings_visible_apps_value, count),
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        // Outlined (not filled): destructive restraint for a reset action.
        NgbOutlinedButton(
            onClick = { showResetDialog = true },
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
    SectionCard(
        title = stringResource(R.string.settings_restore_section),
        icon = Icons.Outlined.SettingsBackupRestore,
    ) {
        // Shizuku precondition row — the restore runs over the same privileged bridge.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.settings_restore_shizuku_status),
                style = MaterialTheme.typography.bodyMedium,
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
                color = when (state.shizukuStatus) {
                    ShizukuPrivilege.Status.READY -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        if (state.shizukuStatus != ShizukuPrivilege.Status.READY) {
            Spacer(Modifier.height(8.dp))
            NgbOutlinedButton(onClick = onActivateShizuku, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.shizuku_activate))
            }
        }
        Spacer(Modifier.height(12.dp))

        if (state.deviceChanges.isEmpty()) {
            // Honest empty state: no ledger entries means the app recorded nothing.
            Text(
                text = stringResource(R.string.settings_restore_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = stringResource(R.string.settings_restore_count_value, state.deviceChanges.size),
                style = MaterialTheme.typography.titleSmall,
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

            NgbOutlinedButton(
                onClick = onRestoreClick,
                enabled = !state.restoreBusy,
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.settings_restore_reset_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RestoreRecordRow(record: DeviceChangeRecord, status: ItemStatus?) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = restoreRecordTitle(record),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = restoreRecordSubtitle(record),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                MaterialTheme.colorScheme.primary

        is ItemStatus.Skipped ->
            stringResource(
                R.string.settings_restore_status_skipped,
                restoreReasonLabel(status.reasonKey),
            ) to MaterialTheme.colorScheme.onSurfaceVariant

        is ItemStatus.Failed ->
            stringResource(R.string.settings_restore_status_failed, status.message) to
                MaterialTheme.colorScheme.error
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
    SectionCard(
        title = stringResource(R.string.settings_update_section),
        icon = Icons.Outlined.SystemUpdateAlt,
    ) {
        AboutRow(
            label = stringResource(R.string.settings_update_current),
            value = BuildConfig.VERSION_NAME,
        )

        val checking = state.update is UpdateCheckUi.Checking
        val downloading = state.update is UpdateCheckUi.Downloading
        NgbOutlinedButton(
            onClick = onCheck,
            enabled = !checking && !downloading,
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
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.settings_update_uptodate, update.remoteVersion),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            is UpdateCheckUi.Available -> {
                Text(
                    text = stringResource(R.string.settings_update_available, update.versionName),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (update.whatsNew != null) {
                    Text(
                        text = stringResource(R.string.settings_update_whatsnew),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        )
                    }
                } else {
                    // The release ships no friendly notes file — say so, never invent.
                    Text(
                        text = stringResource(R.string.settings_update_notes_missing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                NgbOutlinedButton(
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = stringResource(
                            R.string.settings_update_downloaded,
                            UpdateTools.formatMb(update.downloadedBytes),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            is UpdateCheckUi.Failed -> Text(
                text = stringResource(update.errorRes, *update.args.toTypedArray()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )

            UpdateCheckUi.Idle, UpdateCheckUi.Checking -> Unit
        }

        if (state.updateNeedsInstallPermission) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_update_install_permission_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            NgbOutlinedButton(onClick = onAllowInstall, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_update_allow))
            }
        }

        state.updateHintRes?.let { hintRes ->
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(hintRes),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.settings_update_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One radio row. Accessibility contract: the WHOLE row is selectable with
 * [Role.RadioButton] (min 48.dp touch target) and announces its selection state via
 * [stateDescription] so screen readers read "Selected"/"Not selected" explicitly.
 */
@Composable
private fun SelectRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val selectedText = stringResource(R.string.settings_a11y_selected)
    val notSelectedText = stringResource(R.string.settings_a11y_not_selected)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(NgbChamferShape(12.dp))
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(vertical = 4.dp)
            .semantics {
                stateDescription = if (selected) selectedText else notSelectedText
            },
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
    }
}
