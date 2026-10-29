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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexus.gamebooster.R
import com.nexus.gamebooster.core.ServiceLocator
import com.nexus.gamebooster.core.VmFactory
import com.nexus.gamebooster.core.uiMessageText
import com.nexus.gamebooster.games.Game
import com.nexus.gamebooster.games.GameSort
import com.nexus.gamebooster.games.InstalledApp
import com.nexus.gamebooster.ui.arcade.ArcadeButton
import com.nexus.gamebooster.ui.arcade.ArcadeChip
import com.nexus.gamebooster.ui.arcade.ArcadeField
import com.nexus.gamebooster.ui.arcade.ArcadeHead
import com.nexus.gamebooster.ui.arcade.ArcadeOutlineButton
import com.nexus.gamebooster.ui.arcade.ArcadePanel
import com.nexus.gamebooster.ui.arcade.ArcadePanelVariant
import com.nexus.gamebooster.ui.arcade.ArcadeSegment
import com.nexus.gamebooster.ui.arcade.ArcadeSegmented
import com.nexus.gamebooster.ui.arcade.ArcadeTint
import com.nexus.gamebooster.ui.arcade.NgbArcadeColors
import com.nexus.gamebooster.ui.components.NgbButton
import com.nexus.gamebooster.ui.components.NgbOutlinedButton
import com.nexus.gamebooster.ui.motion.LocalNgbMotionEnabled
import com.nexus.gamebooster.ui.motion.ngbPressScale
import com.nexus.gamebooster.ui.theme.NgbChamferShape
import com.nexus.gamebooster.ui.theme.NgbHudFont
import com.nexus.gamebooster.ui.theme.NgbTagFont


/**
 * LIBRARY screen — v1.2.1 "ARCADE HUD" restyle (the `renderLibrary` view of the
 * reference: `head` → `.seg.tabs` → `.field.srch` → `.panel`/`.gcard` rows →
 * full-width `.obtn`). Every screen behavior is unchanged: search + sort, the
 * my-games/discover tabs, the discover GAMES/ALL_APPS filter, manual add dialog,
 * remove confirm, details, launch, the real scan flow and all empty states.
 *
 * Only the presentation moved to the arcade kit: [ArcadeHead], [ArcadeSegmented],
 * [ArcadeField], [ArcadePanel] cards with the chamfered `.cover` tile, and the
 * 3D-lip / outlined arcade buttons. Data still comes only from [LibraryViewModel].
 */

/** Horizontal and vertical page padding — matches NgbScreenContainer's geometry. */
private val LibraryScreenPadding = 16.dp

/** Maximum content width on tablets / landscape — matches NgbScreenContainer. */
private val LibraryMaxContentWidth = 640.dp

/** Stable segment ids for the arcade segmented controls (UI-local only). */
private const val SEGMENT_MY_GAMES = "my_games"
private const val SEGMENT_DISCOVER = "discover"
private const val SEGMENT_GAMES = "games"
private const val SEGMENT_ALL_APPS = "all_apps"

/** The `.cover` tile geometry of the reference: 62 px with a 10 px chamfer. */
private val CoverTileSize = 62.dp

/**
 * Arcade family hues the cover gradients are seeded from (the task's palette:
 * Neon/Lime/Azure/Warn/Danger — one bright hue plus one hue mixed deep toward
 * the kit ink, so every tile reads "bright → dark" on the dark theme).
 */
private val CoverHues = listOf(
    NgbArcadeColors.Neon,
    NgbArcadeColors.Lime,
    NgbArcadeColors.Azure,
    NgbArcadeColors.Warn,
    NgbArcadeColors.Danger,
)

/**
 * The `.cover::after` hover shine sweep, frozen as a static diagonal band
 * (transparent 40% → white 25% at 50% → transparent 60%) — no animation.
 */
private val CoverShineBrush = Brush.linearGradient(
    0f to Color.Transparent,
    0.40f to Color.Transparent,
    0.50f to Color.White.copy(alpha = 0.25f),
    0.60f to Color.Transparent,
    1f to Color.Transparent,
)

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
                ArcadeHead(
                    code = "LIBRARY",
                    title = stringResource(R.string.library_headline),
                    subtitle = stringResource(R.string.library_subtitle),
                )
                Spacer(Modifier.height(12.dp))

                // The reference's `.seg.tabs` — same two tabs, same tab state.
                ArcadeSegmented(
                    options = listOf(
                        ArcadeSegment(id = SEGMENT_MY_GAMES, label = stringResource(R.string.library_tab_my_games)),
                        ArcadeSegment(id = SEGMENT_DISCOVER, label = stringResource(R.string.library_tab_discover)),
                    ),
                    selected = if (state.tab == LibraryTab.MY_GAMES) SEGMENT_MY_GAMES else SEGMENT_DISCOVER,
                    onSelect = { id ->
                        vm.selectTab(
                            if (id == SEGMENT_MY_GAMES) LibraryTab.MY_GAMES else LibraryTab.DISCOVER
                        )
                    },
                )
                Spacer(Modifier.height(10.dp))

                // `.field.srch` with the clear action beside it (appears only when
                // there is something to clear, exactly like the old trailing icon).
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ArcadeField(
                        value = state.searchQuery,
                        onValueChange = vm::onSearchQueryChange,
                        modifier = Modifier.weight(1f),
                        hint = stringResource(R.string.library_search_hint),
                        isSearch = true,
                    )
                    if (state.searchQuery.isNotEmpty()) {
                        ArcadeOutlineButton(
                            onClick = { vm.onSearchQueryChange("") },
                            tint = ArcadeTint.AZURE,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.library_search_clear),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))

                // Sort trigger (outlined, per the reference `.obtn`) + the real scan
                // refresh. Refresh is disabled while a scan is running.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SortMenu(sort = state.sort, onSelect = vm::setSort)
                    ArcadeOutlineButton(
                        onClick = vm::refresh,
                        enabled = !state.isRefreshing,
                        tint = ArcadeTint.LIME,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.refresh),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                if (state.isRefreshing) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = NgbArcadeColors.Neon,
                        trackColor = NgbArcadeColors.Line,
                    )
                }
                Spacer(Modifier.height(12.dp))

                when (state.tab) {
                    LibraryTab.MY_GAMES -> MyLibraryContent(vm = vm, state = state)
                    LibraryTab.DISCOVER -> DiscoverContent(vm = vm, state = state)
                }

                // The reference's full-width trailing `.obtn` — manual add.
                Spacer(Modifier.height(12.dp))
                ArcadeOutlineButton(
                    onClick = vm::showAddDialog,
                    modifier = Modifier.fillMaxWidth(),
                    tint = ArcadeTint.NEON,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.library_add_manual),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
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
        state.myGames.isEmpty() && state.searchQuery.isBlank() -> ArcadeEmptyPanel(
            icon = Icons.Outlined.SportsEsports,
            title = stringResource(R.string.library_empty_title),
            body = stringResource(R.string.my_games_empty_body),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ArcadeButton(onClick = { vm.selectTab(LibraryTab.DISCOVER) }) {
                    Text(
                        text = stringResource(R.string.library_tab_discover),
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                Spacer(Modifier.height(8.dp))
                ArcadeOutlineButton(onClick = vm::showAddDialog, tint = ArcadeTint.AZURE) {
                    Text(
                        text = stringResource(R.string.library_add_manual),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }

        state.myGames.isEmpty() -> ArcadeEmptyPanel(
            icon = Icons.Outlined.Search,
            title = stringResource(R.string.library_no_match_title),
            body = stringResource(R.string.library_no_match_body, state.searchQuery),
        )

        else -> LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
 * One library game card: an arcade `.panel` wrapping the reference's `.gcard`
 * (chamfered cover tile + HUD name + meta + actions). The row is a single press
 * target with the shared restrained press scale and an explicit "open details"
 * click label; the launch/remove buttons stay their own accessibility nodes (a
 * merged parent never swallows interactive descendants).
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
    ArcadePanel(
        modifier = Modifier.fillMaxWidth(),
        variant = ArcadePanelVariant.PLAIN,
        contentPadding = 10.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = CoverTileSize)
                .ngbPressScale(interactionSource, motionEnabled)
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClickLabel = stringResource(R.string.a11y_open_game_details),
                    onClick = onClick,
                ),
        ) {
            GameCover(vm = vm, packageName = game.packageName, name = game.name)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                GameNameText(name = game.name)
                GameMetaText(text = game.packageName ?: stringResource(R.string.details_package_none))
            }
            Spacer(Modifier.width(8.dp))
            // The reference stacks the row's actions vertically (flex-direction:column).
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Launch only makes sense with a real package behind the entry.
                if (game.packageName != null) {
                    ArcadeButton(
                        onClick = { vm.launch(game) },
                        small = true,
                        tint = ArcadeTint.NEON,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.library_launch),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
                ArcadeOutlineButton(onClick = onRemove, tint = ArcadeTint.DANGER) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.remove_confirm),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------- Discover

@Composable
private fun DiscoverContent(vm: LibraryViewModel, state: LibraryUiState) {
    Column {
        ArcadeSegmented(
            options = listOf(
                ArcadeSegment(id = SEGMENT_GAMES, label = stringResource(R.string.discover_filter_games)),
                ArcadeSegment(id = SEGMENT_ALL_APPS, label = stringResource(R.string.discover_filter_all_apps)),
            ),
            selected = if (state.discoverFilter == DiscoverFilter.GAMES) SEGMENT_GAMES else SEGMENT_ALL_APPS,
            onSelect = { id ->
                vm.setDiscoverFilter(
                    if (id == SEGMENT_GAMES) DiscoverFilter.GAMES else DiscoverFilter.ALL_APPS
                )
            },
        )
        Spacer(Modifier.height(8.dp))

        // The honesty note, restyled as the reference's `.panel.warn`.
        ArcadePanel(variant = ArcadePanelVariant.WARN, contentPadding = 12.dp) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = NgbArcadeColors.Warn,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.discover_caption),
                    fontSize = 12.sp,
                    color = NgbArcadeColors.Muted,
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        when {
            state.isRefreshing && !state.hasScannedOnce -> Text(
                text = stringResource(R.string.library_scanning),
                fontSize = 12.sp,
                color = NgbArcadeColors.Muted,
            )

            state.scanFailedReason != null && state.installedApps.isEmpty() -> ArcadeEmptyPanel(
                icon = Icons.Outlined.CloudOff,
                title = stringResource(R.string.library_scan_failed_title),
                body = stringResource(R.string.library_scan_failed_body),
            ) {
                ArcadeOutlineButton(onClick = vm::refresh, tint = ArcadeTint.NEON) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_retry),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }

            state.discoverApps.isEmpty() -> ArcadeEmptyPanel(
                icon = Icons.Outlined.Search,
                title = stringResource(R.string.discover_empty_title),
                body = stringResource(R.string.discover_empty_body),
            )

            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
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
    ArcadePanel(
        modifier = Modifier.fillMaxWidth(),
        variant = ArcadePanelVariant.PLAIN,
        contentPadding = 10.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = CoverTileSize),
        ) {
            GameCover(vm = vm, packageName = app.packageName, name = app.label)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                GameNameText(name = app.label)
                GameMetaText(text = app.packageName)
            }
            Spacer(Modifier.width(8.dp))
            if (added) {
                ArcadeChip(
                    text = stringResource(R.string.discover_added_cd),
                    tint = NgbArcadeColors.Lime,
                    leading = {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = NgbArcadeColors.Lime,
                            modifier = Modifier.size(13.dp),
                        )
                    },
                )
            } else {
                ArcadeButton(
                    onClick = { vm.addInstalled(app.packageName) },
                    small = true,
                    tint = ArcadeTint.AZURE,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.add_dialog_confirm),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SortMenu(sort: GameSort, onSelect: (GameSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ArcadeOutlineButton(onClick = { expanded = true }, tint = ArcadeTint.NEON) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Sort,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.sort_label),
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
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

// ------------------------------------------------------------------ cover tile

/**
 * Deterministic `.cover` gradient pair, seeded by the package name: one bright
 * arcade hue plus a second arcade hue mixed deep toward the kit ink. The same
 * game always paints the same tile (String.hashCode is stable); nothing random.
 */
private fun coverGradientColors(seedKey: String): Pair<Color, Color> {
    val seed = seedKey.hashCode()
    val firstIndex = Math.floorMod(seed, CoverHues.size)
    var secondIndex = Math.floorMod(seed / CoverHues.size + 2, CoverHues.size)
    if (secondIndex == firstIndex) {
        secondIndex = (firstIndex + 1) % CoverHues.size
    }
    val bright = CoverHues[firstIndex]
    val deep = lerp(CoverHues[secondIndex], NgbArcadeColors.Ink, 0.72f)
    return bright to deep
}

/**
 * The reference's `.cover`: a 62 dp tile clipped to the 10 dp chamfer, filled
 * with the deterministic diagonal gradient. A REAL app icon is shown when the
 * ViewModel's PackageManager cache has one; otherwise the tile falls back to
 * the game's first letter in Orbitron ExtraBold. The static shine band always
 * rides on top (the hover sweep, frozen — no animation).
 */
@Composable
private fun GameCover(vm: LibraryViewModel, packageName: String?, name: String) {
    var bitmap by remember(packageName) {
        mutableStateOf(if (packageName != null) vm.cachedIcon(packageName) else null)
    }
    if (packageName != null) {
        LaunchedEffect(packageName) {
            bitmap = vm.iconFor(packageName)
        }
    }
    val (hueBright, hueDeep) = coverGradientColors(seedKey = packageName ?: name)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(CoverTileSize)
            .clip(NgbChamferShape(10.dp))
            .background(Brush.linearGradient(listOf(hueBright, hueDeep))),
    ) {
        val loaded: ImageBitmap? = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = name.firstOrNull()?.uppercase().orEmpty(),
                fontFamily = NgbTagFont,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 24.sp,
                color = Color.White.copy(alpha = 0.92f),
            )
        }
        Box(modifier = Modifier.matchParentSize().background(CoverShineBrush))
    }
}

// -------------------------------------------------------------- shared row text

/**
 * Game title in the reference's `.gcard h3` style: HUD (Rajdhani) bold 17 sp,
 * forced LTR paragraph direction with start alignment (game names are Latin).
 */
@Composable
private fun GameNameText(name: String, modifier: Modifier = Modifier) {
    Text(
        text = name,
        modifier = modifier,
        style = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
        fontFamily = NgbHudFont,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        textAlign = TextAlign.Start,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Muted 12 sp meta line under the title (package / source info). */
@Composable
private fun GameMetaText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 12.sp,
        color = NgbArcadeColors.Muted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

// ----------------------------------------------------------------- empty state

/**
 * Empty / no-match state restyled as the reference's `.panel.flat`, centered:
 * icon, heavy title, muted body and an optional CTA slot.
 */
@Composable
private fun ArcadeEmptyPanel(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    ArcadePanel(
        modifier = modifier.fillMaxWidth(),
        variant = ArcadePanelVariant.FLAT,
        contentPadding = 18.dp,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = NgbArcadeColors.Neon,
                modifier = Modifier.size(30.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = body,
                fontSize = 12.sp,
                color = NgbArcadeColors.Muted,
                textAlign = TextAlign.Center,
            )
            if (action != null) {
                Spacer(Modifier.height(14.dp))
                action()
            }
        }
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
