package com.nexus.gamebooster.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexus.gamebooster.BuildConfig
import com.nexus.gamebooster.R
import com.nexus.gamebooster.core.ServiceLocator
import com.nexus.gamebooster.core.VmFactory
import com.nexus.gamebooster.data.Language
import com.nexus.gamebooster.data.ThemeMode
import com.nexus.gamebooster.ui.components.NgbOutlinedButton
import com.nexus.gamebooster.ui.components.NgbScreenContainer
import com.nexus.gamebooster.ui.components.SectionCard

@Composable
fun SettingsScreen() {
    val vm: SettingsViewModel = viewModel(factory = VmFactory { ServiceLocator.settingsViewModel() })
    val state by vm.uiState.collectAsStateWithLifecycle()
    var showResetDialog by rememberSaveable { mutableStateOf(false) }

    NgbScreenContainer {
        Text(
            text = stringResource(R.string.settings_headline),
            style = MaterialTheme.typography.headlineSmall,
        )
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
            .clip(RoundedCornerShape(12.dp))
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
