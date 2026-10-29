package com.nexus.gamebooster.ui.screens.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexus.gamebooster.R
import com.nexus.gamebooster.core.ServiceLocator
import com.nexus.gamebooster.core.VmFactory
import com.nexus.gamebooster.core.uiMessageText
import com.nexus.gamebooster.games.Game
import com.nexus.gamebooster.games.GameSort
import com.nexus.gamebooster.games.InstalledApp
import com.nexus.gamebooster.ui.components.EmptyState
import com.nexus.gamebooster.ui.components.NgbPageHeader
import com.nexus.gamebooster.ui.components.NgbButton
import com.nexus.gamebooster.ui.components.NgbOutlinedButton
import com.nexus.gamebooster.ui.components.NgbSegButton
import com.nexus.gamebooster.ui.components.NgbSegRow
import com.nexus.gamebooster.ui.motion.LocalNgbMotionEnabled
import com.nexus.gamebooster.ui.motion.ngbPressScale
import com.nexus.gamebooster.ui.theme.NgbChamferShape

/** Horizontal and vertical page padding — matches NgbScreenContainer's geometry. */
private val LibraryScreenPadding = 16.dp

/** Maximum content width on tablets / landscape — matches NgbScreenContainer. */
private val LibraryMaxContentWidth = 640.dp

@Composable
fun LibraryScreen() {
    val vm: LibraryViewModel = viewModel(factory = VmFactory { ServiceLocator.libraryViewModel() })
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val pendingMessage = state.message
    // Resolve the localized text in composable context first — uiMessageText is
    // @Composable (stringResource) and cannot be called inside the effect lambda.
    val pendingText = pendingMessage?.let { uiMessageText(it) }
    LaunchedEffect(pendingMessage?.id, pendingText) {
        if (pendingMessage != null && pendingText != null) {
            snackbarHostState.showSnackbar(pendingText)
            vm.onMessageShown()
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Same centered max-640dp geometry as NgbScreenContainer, WITHOUT its vertical
        // scroll: this screen's content is a viewport-bounded LazyColumn (a lazily
        // composed list must never live inside a scrollable column), so the shell is
        // reproduced here locally to keep every screen visually consistent.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = LibraryScreenPadding, vertical = LibraryScreenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = LibraryMaxContentWidth),
            ) {
                NgbPageHeader(
                    title = stringResource(R.string.library_headline),
                    subtitle = stringResource(R.string.library_subtitle),
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = vm::onSearchQueryChange,
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.library_search_hint)) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Outlined.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { vm.onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.library_search_clear),
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    SortMenu(sort = state.sort, onSelect = vm::setSort)
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = vm::refresh, enabled = !state.isRefreshing) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.refresh),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    NgbOutlinedButton(onClick = vm::showAddDialog) {
                        Icon(imageVector = Icons.Outlined.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.library_add_manual))
                    }
                }

                if (state.isRefreshing) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(8.dp))

                // v1.2.1 re-skin: the mockup `.seg.tabs` — ink2 well + the neon
                // gradient on the active tab (was a Material TabRow).
                NgbSegRow(Modifier.fillMaxWidth()) {
                    NgbSegButton(
                        label = stringResource(R.string.library_tab_my_games),
                        selected = state.tab == LibraryTab.MY_GAMES,
                        onClick = { vm.selectTab(LibraryTab.MY_GAMES) },
                        modifier = Modifier.weight(1f),
                        role = Role.Tab,
                    )
                    NgbSegButton(
                        label = stringResource(R.string.library_tab_discover),
                        selected = state.tab == LibraryTab.DISCOVER,
                        onClick = { vm.selectTab(LibraryTab.DISCOVER) },
                        modifier = Modifier.weight(1f),
                        role = Role.Tab,
                    )
                }
                Spacer(Modifier.height(12.dp))

                when (state.tab) {
                    LibraryTab.MY_GAMES -> MyLibraryContent(vm = vm, state = state)
                    LibraryTab.DISCOVER -> DiscoverContent(vm = vm, state = state)
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (state.addDialogVisible) {
        AddGameDialog(onConfirm = vm::confirmAddManual, onDismiss = vm::dismissAddDialog)
    }

    state.removeTarget?.let { game ->
        AlertDialog(
            onDismissRequest = vm::dismissRemove,
            title = { Text(stringResource(R.string.remove_dialog_title)) },
            text = { Text(stringResource(R.string.remove_dialog_body, game.name)) },
            confirmButton = {
                // Destructive-restraint pattern (matches Settings): the destructive
                // confirm stays outlined, never a filled primary CTA.
                NgbOutlinedButton(onClick = vm::confirmRemove) {
                    Text(stringResource(R.string.remove_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissRemove) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    state.detailsTarget?.let { game ->
        GameDetailsDialog(game = game, onDismiss = vm::dismissDetails)
    }
}

// ------------------------------------------------------------------ My Library

@Composable
private fun MyLibraryContent(vm: LibraryViewModel, state: LibraryUiState) {
    when {
        state.myGames.isEmpty() && state.searchQuery.isBlank() -> EmptyState(
            icon = Icons.Outlined.SportsEsports,
            title = stringResource(R.string.library_empty_title),
            body = stringResource(R.string.my_games_empty_body),
            action = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    NgbButton(onClick = { vm.selectTab(LibraryTab.DISCOVER) }) {
                        Text(stringResource(R.string.library_tab_discover))
                    }
                    Spacer(Modifier.height(8.dp))
                    NgbOutlinedButton(onClick = vm::showAddDialog) {
                        Text(stringResource(R.string.library_add_manual))
                    }
                }
            },
        )

        state.myGames.isEmpty() -> EmptyState(
            icon = Icons.Outlined.Search,
            title = stringResource(R.string.library_no_match_title),
            body = stringResource(R.string.library_no_match_body, state.searchQuery),
        )

        else -> LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.myGames, key = { it.id }) { game ->
                MyGameRow(
                    game = game,
                    vm = vm,
                    onClick = { vm.showDetails(game) },
                    onRemove = { vm.requestRemove(game) },
                    motionEnabled = LocalNgbMotionEnabled.current,
                )
            }
        }
    }
}

/**
 * One library game card. The row is a single press target with the shared
 * restrained press scale and an explicit "open details" click label; the inner
 * launch/remove icon buttons stay their own accessibility nodes (a merged parent
 * never swallows interactive descendants).
 */
@Composable
private fun MyGameRow(
    game: Game,
    vm: LibraryViewModel,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    motionEnabled: Boolean,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        shape = NgbChamferShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(NgbChamferShape(12.dp))
                .ngbPressScale(interactionSource, motionEnabled)
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClickLabel = stringResource(R.string.a11y_open_game_details),
                    onClick = onClick,
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            GameAppIcon(vm = vm, packageName = game.packageName)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(text = game.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = game.packageName ?: stringResource(R.string.details_package_none),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Launch only makes sense with a real package behind the entry.
            if (game.packageName != null) {
                IconButton(onClick = { vm.launch(game) }) {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = stringResource(R.string.library_launch),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.remove_confirm),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// -------------------------------------------------------------------- Discover

@Composable
private fun DiscoverContent(vm: LibraryViewModel, state: LibraryUiState) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterPill(
                label = stringResource(R.string.discover_filter_games),
                selected = state.discoverFilter == DiscoverFilter.GAMES,
                onClick = { vm.setDiscoverFilter(DiscoverFilter.GAMES) },
            )
            FilterPill(
                label = stringResource(R.string.discover_filter_all_apps),
                selected = state.discoverFilter == DiscoverFilter.ALL_APPS,
                onClick = { vm.setDiscoverFilter(DiscoverFilter.ALL_APPS) },
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.discover_caption),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        when {
            state.isRefreshing && !state.hasScannedOnce -> Text(
                text = stringResource(R.string.library_scanning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.scanFailedReason != null && state.installedApps.isEmpty() -> EmptyState(
                icon = Icons.Outlined.CloudOff,
                title = stringResource(R.string.library_scan_failed_title),
                body = stringResource(R.string.library_scan_failed_body),
                action = {
                    NgbOutlinedButton(onClick = vm::refresh) {
                        Text(stringResource(R.string.action_retry))
                    }
                },
            )

            state.discoverApps.isEmpty() -> EmptyState(
                icon = Icons.Outlined.Search,
                title = stringResource(R.string.discover_empty_title),
                body = stringResource(R.string.discover_empty_body),
            )

            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.discoverApps, key = { it.packageName }) { app ->
                    DiscoverRow(
                        app = app,
                        added = app.packageName in state.selectedPackages,
                        vm = vm,
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoverRow(app: InstalledApp, added: Boolean, vm: LibraryViewModel) {
    Card(
        shape = NgbChamferShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            GameAppIcon(vm = vm, packageName = app.packageName)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(text = app.label, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { vm.addInstalled(app.packageName) }, enabled = !added) {
                Icon(
                    imageVector = if (added) Icons.Outlined.Check else Icons.Outlined.Add,
                    contentDescription = stringResource(
                        if (added) R.string.discover_added_cd else R.string.discover_add_cd
                    ),
                    tint = if (added) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/**
 * Text-only filter pill built from stable foundation APIs (no experimental chip APIs).
 * Exposed as a radio in a two-option group so TalkBack announces the selected state.
 * v1.2.1: delegates to the shared kit `.seg` button ([NgbSegButton]).
 */
@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    NgbSegButton(label = label, selected = selected, onClick = onClick, role = Role.RadioButton)
}

@Composable
private fun SortMenu(sort: GameSort, onSelect: (GameSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Sort,
                contentDescription = stringResource(R.string.sort_label),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sort_name_asc)) },
                onClick = {
                    expanded = false
                    onSelect(GameSort.NAME_ASC)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sort_name_desc)) },
                onClick = {
                    expanded = false
                    onSelect(GameSort.NAME_DESC)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sort_recently_added)) },
                onClick = {
                    expanded = false
                    onSelect(GameSort.RECENTLY_ADDED)
                },
            )
        }
    }
}

// ----------------------------------------------------------------------- icons

/**
 * Real app icon with an honest placeholder: the bitmap is loaded asynchronously from
 * PackageManager via the ViewModel cache; null (unknown package / load failure) renders
 * a neutral tinted circle — never a fabricated icon.
 */
@Composable
private fun GameAppIcon(vm: LibraryViewModel, packageName: String?, size: Int = 40) {
    if (packageName == null) {
        IconPlaceholder(size = size)
        return
    }
    var bitmap by remember(packageName) { mutableStateOf(vm.cachedIcon(packageName)) }
    LaunchedEffect(packageName) {
        bitmap = vm.iconFor(packageName)
    }
    val loaded: ImageBitmap? = bitmap
    if (loaded != null) {
        Image(
            bitmap = loaded,
            contentDescription = null,
            modifier = Modifier
                .size(size.dp)
                .clip(NgbChamferShape(8.dp)),
        )
    } else {
        IconPlaceholder(size = size)
    }
}

@Composable
private fun IconPlaceholder(size: Int) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size.dp)
            .clip(NgbChamferShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Icon(
            imageVector = Icons.Outlined.SportsEsports,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size((size / 2).dp),
        )
    }
}

// --------------------------------------------------------------------- dialogs

@Composable
private fun AddGameDialog(
    onConfirm: (name: String, packageName: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var packageName by rememberSaveable { mutableStateOf("") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val nameError = attempted && name.isBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_dialog_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.add_dialog_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.add_dialog_name_label)) },
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text(stringResource(R.string.add_dialog_name_error)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.add_dialog_package_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            NgbButton(onClick = {
                if (name.isBlank()) {
                    attempted = true
                } else {
                    onConfirm(name, packageName.trim().takeIf { it.isNotEmpty() })
                }
            }) {
                Text(stringResource(R.string.add_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun GameDetailsDialog(game: Game, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.details_title)) },
        text = {
            Column {
                DetailRow(
                    label = stringResource(R.string.details_name),
                    value = game.name,
                )
                DetailRow(
                    label = stringResource(R.string.details_package),
                    value = game.packageName ?: stringResource(R.string.details_package_none),
                )
                DetailRow(
                    label = stringResource(R.string.details_source),
                    value = stringResource(
                        if (game.isManual) R.string.details_source_manual else R.string.details_source_installed
                    ),
                )
                DetailRow(
                    label = stringResource(R.string.details_launchable),
                    value = stringResource(
                        if (game.launchable) R.string.details_launchable_yes else R.string.details_launchable_unknown
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
        )
    }
}
