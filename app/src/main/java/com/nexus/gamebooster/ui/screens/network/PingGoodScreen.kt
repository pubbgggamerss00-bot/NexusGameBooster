package com.nexus.gamebooster.ui.screens.network

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nexus.gamebooster.R
import com.nexus.gamebooster.network.DnsForwardingVpnService
import com.nexus.gamebooster.network.DnsResolver
import com.nexus.gamebooster.network.DnsResolverTester
import com.nexus.gamebooster.network.DnsTestResult
import com.nexus.gamebooster.network.NexusApiClient
import com.nexus.gamebooster.network.WireGuardController
import com.nexus.gamebooster.network.WireGuardProfile
import com.nexus.gamebooster.ui.arcade.ArcadeButton
import com.nexus.gamebooster.ui.arcade.ArcadeChip
import com.nexus.gamebooster.ui.arcade.ArcadeHead
import com.nexus.gamebooster.ui.arcade.ArcadeInkText
import com.nexus.gamebooster.ui.arcade.ArcadeKvRow
import com.nexus.gamebooster.ui.arcade.ArcadeOutlineButton
import com.nexus.gamebooster.ui.arcade.ArcadePanel
import com.nexus.gamebooster.ui.arcade.ArcadePanelVariant
import com.nexus.gamebooster.ui.arcade.ArcadeSectionLabel
import com.nexus.gamebooster.ui.arcade.ArcadeSegment
import com.nexus.gamebooster.ui.arcade.ArcadeSegmented
import com.nexus.gamebooster.ui.arcade.ArcadeTint
import com.nexus.gamebooster.ui.arcade.NgbArcadeColors
import com.nexus.gamebooster.ui.motion.LocalNgbMotionEnabled
import com.nexus.gamebooster.ui.theme.NgbHudFont
import com.nexus.gamebooster.ui.theme.neonGlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

// =============================================================================
// PING screen — the v1.2.1 "ARCADE HUD" restyle of the reference's PING view
// (renderPing + .power/.ring/.pbar/.kv/.panel, click cases vpn/dns/dnstest).
//
// Every behavior is preserved 1:1 from the previous version: WireGuard
// connect/disconnect (incl. the VpnService.prepare flow and the one-VPN
// conflict), profile import/select/delete, real DNS testing/selection, panel
// sync (5-minute loop + manual refresh), remote panel DNS entries and all
// error/loading messages. The fake reference numbers ("۲۴ میلی‌ثانیه",
// "Frankfurt-02") are NOT reproduced — the info rows show only REAL values.
// =============================================================================

@Composable
fun PingGoodScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    // Tunnel-only busy flags — the connect CTAs must show CONNECTING only for
    // their own operation, never for a background sync/import/test.
    var wgWorking by remember { mutableStateOf(false) }
    var dnsWorking by remember { mutableStateOf(false) }
    var profiles by remember { mutableStateOf(WireGuardController.listProfiles(context)) }
    var selectedProfileId by rememberSaveable { mutableStateOf(WireGuardController.selectedProfileId(context)) }
    var wgConnected by remember { mutableStateOf(WireGuardController.isConnected(context)) }
    var dnsConnected by remember { mutableStateOf(DnsForwardingVpnService.isRunning) }
    var results by remember { mutableStateOf(emptyList<DnsTestResult>()) }
    var hasTested by rememberSaveable { mutableStateOf(false) }
    var selectedDns by rememberSaveable { mutableStateOf("") }
    var remoteDns by remember { mutableStateOf(readRemoteDns(context)) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var pendingDns by remember { mutableStateOf("") }
    var profilePendingDelete by remember { mutableStateOf<WireGuardProfile?>(null) }

    fun startDnsTunnel(address: String) {
        val intent = Intent(context, DnsForwardingVpnService::class.java)
            .putExtra(DnsForwardingVpnService.EXTRA_RESOLVER, address)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ContextCompat.startForegroundService(context, intent)
            else context.startService(intent)
            scope.launch {
                delay(500)
                dnsConnected = DnsForwardingVpnService.isRunning
                dnsWorking = false
                message = if (dnsConnected) context.getString(R.string.dns_connected)
                else context.getString(R.string.dns_test_error, "Could not start the DNS tunnel")
            }
        } catch (e: Exception) {
            dnsConnected = false
            dnsWorking = false
            message = context.getString(R.string.dns_test_error, e.message ?: "Could not start the DNS tunnel")
        }
    }

    fun connectWireGuard() {
        if (dnsConnected || DnsForwardingVpnService.isRunning) {
            wgWorking = false
            message = context.getString(R.string.ping_vpn_conflict); return
        }
        wgWorking = true; message = context.getString(R.string.wg_working)
        scope.launch {
            val result = WireGuardController.connect(context, selectedProfileId)
            wgConnected = result.isSuccess
            message = if (result.isSuccess) context.getString(R.string.wg_connect_success)
            else context.getString(R.string.wg_connect_error, result.exceptionOrNull()?.message ?: "Unknown error")
            wgWorking = false
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val result = WireGuardController.importConfig(context, uri)
            if (result.isSuccess) {
                profiles = WireGuardController.listProfiles(context)
                selectedProfileId = result.getOrNull()?.id.orEmpty()
            }
            message = if (result.isSuccess) context.getString(R.string.wg_import_success)
            else context.getString(R.string.wg_import_error, result.exceptionOrNull()?.message ?: "Unknown error")
            busy = false
        }
    }
    val wgPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) connectWireGuard()
        else { wgWorking = false; message = context.getString(R.string.ping_vpn_conflict) }
    }
    val dnsPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && pendingDns.isNotBlank()) {
            startDnsTunnel(pendingDns)
        } else { dnsWorking = false; message = context.getString(R.string.ping_vpn_conflict) }
    }

    LaunchedEffect(Unit) {
        profiles = WireGuardController.listProfiles(context)
        selectedProfileId = WireGuardController.selectedProfileId(context)
        wgConnected = WireGuardController.isConnected(context)
        dnsConnected = DnsForwardingVpnService.isRunning
        remoteDns = readRemoteDns(context)
        // Poll every five minutes while this screen is active.
        while (true) {
            try {
                val data = NexusApiClient.sync(context)
                remoteDns = readDns(context, data)
                profiles = WireGuardController.listProfiles(context)
                selectedProfileId = WireGuardController.selectedProfileId(context)
                syncMessage = context.getString(R.string.sync_ok_summary, remoteDns.size, data.optJSONArray("wireguard")?.length() ?: 0)
            } catch (e: Exception) { syncMessage = e.message ?: context.getString(R.string.api_error_sync_failed) }
            delay(5 * 60 * 1000L)
        }
    }

    profilePendingDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { profilePendingDelete = null },
            title = { Text(stringResource(R.string.wg_delete_title)) },
            text = { Text(stringResource(R.string.wg_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    profilePendingDelete = null
                    scope.launch {
                        busy = true
                        val result = WireGuardController.deleteProfile(context, profile.id)
                        profiles = WireGuardController.listProfiles(context)
                        selectedProfileId = WireGuardController.selectedProfileId(context)
                        message = if (result.isSuccess) context.getString(R.string.wg_delete_success)
                            else context.getString(R.string.wg_delete_error, result.exceptionOrNull()?.message ?: "Unknown error")
                        busy = false
                    }
                }) { Text(stringResource(R.string.wg_delete_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { profilePendingDelete = null }) { Text(stringResource(R.string.wg_delete_cancel)) } }
        )
    }

    // WireGuard power-ring state machine — the reference's ST.vpn off/busy/on,
    // bound to the REAL tunnel flags. LOCKED (not tappable) only when no
    // profile exists, exactly like the previous CTA's LOCKED state.
    val wgLocked = !wgWorking && !wgConnected && selectedProfileId.isBlank()
    val wgStatusRes = when {
        wgConnected -> R.string.wg_connected
        wgWorking -> R.string.wg_working
        else -> R.string.wg_disconnected
    }
    val wgSubtitleRes: Int? = when {
        wgConnected -> R.string.wg_connect_success
        wgWorking -> null
        else -> R.string.ping_power_hint
    }

    // DNS connect CTA eligibility — identical logic to the previous kit states
    // (LOCKED when nothing usable is selected or WireGuard holds the VPN slot).
    val dnsEligible = selectedDns.isNotBlank() &&
        (remoteDns.any { it.address == selectedDns } || (hasTested && results.any { it.resolver.address == selectedDns && it.successful }))
    val dnsCtaEnabled = when {
        dnsWorking -> false
        dnsConnected -> true
        else -> dnsEligible && !wgConnected
    }

    val selectedProfileName = profiles.firstOrNull { it.id == selectedProfileId }?.name
    val bestDnsAddress = results.firstOrNull { it.successful }?.resolver?.address

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Reference `head('NETWORK', …)`.
        ArcadeHead(
            code = "NETWORK",
            title = stringResource(R.string.ping_title),
            subtitle = stringResource(R.string.ping_subtitle),
        )

        // Panel sync card — content-identical, restyled into an ArcadePanel.
        ArcadePanel(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ArcadeSectionLabel(stringResource(R.string.sync_panel_title))
                Text(
                    text = syncMessage ?: stringResource(R.string.sync_initial),
                    color = NgbArcadeColors.Muted,
                    fontSize = 12.5.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ArcadeChip(stringResource(R.string.sync_dns_count, remoteDns.size), tint = NgbArcadeColors.Azure)
                    ArcadeChip(stringResource(R.string.sync_config_count, profiles.size), tint = NgbArcadeColors.Lime)
                }
                ArcadeOutlineButton(
                    onClick = {
                        scope.launch {
                            busy = true
                            try {
                                val data = NexusApiClient.sync(context)
                                remoteDns = readDns(context, data)
                                profiles = WireGuardController.listProfiles(context)
                                selectedProfileId = WireGuardController.selectedProfileId(context)
                                syncMessage = context.getString(R.string.sync_manual_ok)
                            } catch (e: Exception) { syncMessage = e.message ?: context.getString(R.string.api_error_sync_failed) }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    tint = ArcadeTint.AZURE,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = ArcadeInkText)
                    else Text(stringResource(R.string.sync_manual_action), fontWeight = FontWeight.Bold)
                }
            }
        }

        // Reference `.seg.tabs` — WireGuard / DNS.
        ArcadeSegmented(
            options = listOf(
                ArcadeSegment(id = "wg", label = stringResource(R.string.ping_tab_wireguard)),
                ArcadeSegment(id = "dns", label = stringResource(R.string.ping_tab_dns)),
            ),
            selected = if (tab == 0) "wg" else "dns",
            onSelect = { next -> tab = if (next == "wg") 0 else 1 },
        )

        if (message.isNotBlank()) {
            ArcadePanel(variant = ArcadePanelVariant.FLAT, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = message,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 13.sp,
                )
            }
        }

        ArcadePane(visible = tab == 0) {
            // The `.panel` + `.power` hero: dashed spinning ring, radial face,
            // state-colored border/glow/icon. HOT fill while connected.
            ArcadePanel(
                variant = if (wgConnected) ArcadePanelVariant.HOT else ArcadePanelVariant.PLAIN,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    WgPowerRing(
                        connected = wgConnected,
                        busy = wgWorking,
                        locked = wgLocked,
                        actionLabel = stringResource(when {
                            wgWorking -> R.string.wg_working
                            wgConnected -> R.string.wg_disconnect
                            else -> R.string.wg_connect
                        }),
                    ) {
                        if (wgConnected) {
                            wgWorking = true
                            scope.launch {
                                val result = WireGuardController.disconnect(context)
                                wgConnected = false
                                wgWorking = false
                                message = if (result.isSuccess) context.getString(R.string.wg_disconnect_success)
                                else context.getString(R.string.wg_connect_error, result.exceptionOrNull()?.message ?: "Unknown error")
                            }
                        } else if (dnsConnected || DnsForwardingVpnService.isRunning) {
                            message = context.getString(R.string.ping_vpn_conflict)
                        } else {
                            val prepare = VpnService.prepare(context)
                            if (prepare != null) wgPermission.launch(prepare) else connectWireGuard()
                        }
                    }
                    Text(
                        text = stringResource(wgStatusRes),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    wgSubtitleRes?.let { subtitleRes ->
                        Text(
                            text = stringResource(subtitleRes),
                            fontSize = 12.sp,
                            color = NgbArcadeColors.Muted,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            // Info panel — real fields only (no invented server/ping values).
            ArcadePanel(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ArcadeSectionLabel(stringResource(R.string.wg_title))
                    Text(stringResource(R.string.wg_desc), color = NgbArcadeColors.Muted, fontSize = 13.sp)
                    ArcadeKvRow(stringResource(R.string.ping_kv_profile), selectedProfileName ?: "—")
                    ArcadeKvRow(stringResource(R.string.ping_kv_protocol), "WireGuard")
                    Text(
                        text = stringResource(if (profiles.isNotEmpty()) R.string.wg_profile_ready else R.string.wg_no_profile),
                        color = NgbArcadeColors.Muted,
                        fontSize = 12.sp,
                    )
                    profiles.forEach { profile ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable(enabled = !wgConnected) {
                                selectedProfileId = profile.id
                                WireGuardController.selectProfile(context, profile.id)
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selectedProfileId == profile.id, onClick = {
                                if (!wgConnected) {
                                    selectedProfileId = profile.id
                                    WireGuardController.selectProfile(context, profile.id)
                                }
                            }, enabled = !wgConnected)
                            Text(
                                text = profile.name,
                                modifier = Modifier.weight(1f),
                                fontWeight = if (selectedProfileId == profile.id) FontWeight.SemiBold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            IconButton(onClick = { profilePendingDelete = profile }, enabled = !busy && !wgConnected) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.wg_delete), tint = NgbArcadeColors.Danger)
                            }
                        }
                    }
                    ArcadeOutlineButton(
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                        enabled = !busy && !wgConnected,
                        tint = ArcadeTint.AZURE,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.wg_import), fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Reference `.panel.warn` — the one-VPN honesty card.
            ArcadePanel(variant = ArcadePanelVariant.WARN, modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = NgbArcadeColors.Warn, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.ping_vpn_conflict), color = NgbArcadeColors.Muted, fontSize = 13.sp)
                }
            }
        }

        ArcadePane(visible = tab == 1) {
            ArcadePanel(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        ArcadeSectionLabel(stringResource(R.string.dns_title), modifier = Modifier.weight(1f))
                        ArcadeChip(
                            text = stringResource(if (dnsConnected) R.string.dns_connected else R.string.dns_disconnected),
                            tint = if (dnsConnected) NgbArcadeColors.Neon else NgbArcadeColors.Warn,
                        )
                    }
                    Text(stringResource(R.string.dns_desc), color = NgbArcadeColors.Muted, fontSize = 13.sp)
                    // "تست همه" — the reference's dnstest button.
                    ArcadeButton(
                        onClick = {
                            busy = true; message = context.getString(R.string.dns_testing)
                            scope.launch {
                                results = DnsResolverTester.testAll()
                                hasTested = true
                                if (results.none { it.resolver.address == selectedDns && it.successful }) selectedDns = results.firstOrNull { it.successful }?.resolver?.address.orEmpty()
                                message = if (results.any { it.successful }) "" else context.getString(R.string.dns_test_error, "No DNS server responded")
                                busy = false
                            }
                        },
                        enabled = !busy && !dnsConnected,
                        tint = ArcadeTint.AZURE,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (busy && message == context.getString(R.string.dns_testing)) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = ArcadeInkText)
                        }
                        Text(stringResource(R.string.dns_test_all), fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (results.isEmpty()) {
                Text(stringResource(R.string.dns_select_hint), color = NgbArcadeColors.Muted, fontSize = 13.sp)
                DnsResolverTester.defaults.forEach { resolver ->
                    DnsArcadeRow(resolver.name, resolver.address, resolver.detail, null, false, selectedDns == resolver.address, false) { selectedDns = resolver.address }
                }
            } else {
                results.forEach { result ->
                    DnsArcadeRow(
                        name = result.resolver.name,
                        address = result.resolver.address,
                        detail = result.resolver.detail,
                        latencyMs = result.latencyMs,
                        tested = true,
                        selected = selectedDns == result.resolver.address,
                        best = result.successful && result.resolver.address == bestDnsAddress,
                    ) { selectedDns = result.resolver.address }
                }
            }

            if (remoteDns.isNotEmpty()) {
                ArcadeSectionLabel(stringResource(R.string.dns_remote_title))
                remoteDns.forEach { resolver ->
                    DnsArcadeRow(resolver.name, resolver.address, resolver.detail, null, false, selectedDns == resolver.address, false) { selectedDns = resolver.address }
                }
            }

            Text(stringResource(R.string.dns_note), color = NgbArcadeColors.Muted, fontSize = 12.sp)

            // Keep the real DNS tunnel CTA (the reference prototype has none).
            ArcadeButton(
                onClick = {
                    if (dnsConnected) {
                        dnsWorking = true
                        context.startService(Intent(context, DnsForwardingVpnService::class.java).setAction(DnsForwardingVpnService.ACTION_STOP))
                        message = context.getString(R.string.dns_testing)
                        scope.launch {
                            var attempts = 0
                            while (DnsForwardingVpnService.isRunning && attempts < 30) {
                                delay(100)
                                attempts++
                            }
                            dnsConnected = DnsForwardingVpnService.isRunning
                            dnsWorking = false
                            message = if (dnsConnected) context.getString(R.string.dns_test_error, context.getString(R.string.dns_stop_stuck))
                                else context.getString(R.string.dns_disconnected)
                        }
                    } else if (wgConnected) {
                        message = context.getString(R.string.ping_vpn_conflict)
                    } else {
                        dnsWorking = true
                        pendingDns = selectedDns
                        val prepare = VpnService.prepare(context)
                        if (prepare != null) dnsPermission.launch(prepare) else {
                            startDnsTunnel(selectedDns)
                        }
                    }
                },
                enabled = dnsCtaEnabled,
                tint = ArcadeTint.NEON,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (dnsWorking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = ArcadeInkText)
                Text(
                    text = stringResource(when {
                        dnsWorking -> R.string.wg_working
                        dnsConnected -> R.string.dns_disconnect
                        else -> R.string.dns_connect
                    }),
                    fontWeight = FontWeight.Bold,
                )
            }

            if (wgConnected) {
                ArcadePanel(variant = ArcadePanelVariant.WARN, modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = NgbArcadeColors.Warn, modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.ping_vpn_conflict), color = NgbArcadeColors.Muted, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/**
 * Tab pane transition (fade + slide, the previous screen's exact spec) —
 * rendered as a plain instant show/hide when Reduce motion is on.
 */
@Composable
private fun ArcadePane(visible: Boolean, content: @Composable () -> Unit) {
    if (LocalNgbMotionEnabled.current) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 12 },
            exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 24 },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        }
    } else if (visible) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

/**
 * The reference `.power`: a 132 dp area with a 2 dp dashed ring spinning
 * 14 s idle / 0.8 s busy (motion-gated: static ring when motion is off) and
 * a 96 dp radial-gradient face with a 3 dp state-colored border — Line when
 * off, Neon + strong glow when connected, Azure when connecting. Tap to
 * connect/disconnect (disabled while busy or locked, like the prototype).
 */
@Composable
private fun WgPowerRing(
    connected: Boolean,
    busy: Boolean,
    locked: Boolean,
    actionLabel: String,
    onToggle: () -> Unit,
) {
    val motion = LocalNgbMotionEnabled.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && motion && !locked && !busy) 0.92f else 1f,
        animationSpec = tween(120),
        label = "powerPressScale",
    )
    val ringColor = when {
        connected -> NgbArcadeColors.Neon
        busy -> NgbArcadeColors.Azure
        else -> NgbArcadeColors.Line
    }
    val iconTint = when {
        connected -> NgbArcadeColors.Neon
        busy -> NgbArcadeColors.Azure
        else -> NgbArcadeColors.Muted
    }
    val faceHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val faceLow = MaterialTheme.colorScheme.surfaceContainerLowest
    val spinMillis = if (busy) 800 else 14_000

    Box(modifier = Modifier.size(132.dp), contentAlignment = Alignment.Center) {
        val ringModifier = if (motion) {
            key(spinMillis) {
                val spin = rememberInfiniteTransition(label = "powerRingSpin")
                val angle by spin.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(tween(spinMillis, easing = LinearEasing), RepeatMode.Restart),
                    label = "powerRingAngle",
                )
                Modifier.fillMaxSize().graphicsLayer { rotationZ = angle }
            }
        } else {
            Modifier.fillMaxSize()
        }
        Box(
            modifier = ringModifier.drawBehind {
                drawCircle(
                    color = ringColor,
                    radius = size.minDimension / 2f - 1.dp.toPx(),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx())),
                    ),
                )
            },
        )
        val glow: Modifier = when {
            connected -> Modifier.neonGlow(NgbArcadeColors.Neon, CircleShape, blurRadius = 18.dp, glowAlpha = 0.55f)
            busy -> Modifier.neonGlow(NgbArcadeColors.Azure, CircleShape, blurRadius = 14.dp, glowAlpha = 0.30f)
            else -> Modifier
        }
        Box(
            modifier = Modifier
                .size(96.dp)
                .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                .then(glow)
                .clip(CircleShape)
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(faceHigh, faceLow),
                            center = Offset(size.width / 2f, size.height * 0.3f),
                            radius = size.maxDimension * 0.8f,
                        ),
                    )
                }
                .border(3.dp, ringColor, CircleShape)
                .semantics { contentDescription = actionLabel }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = !locked && !busy,
                    role = Role.Button,
                    onClick = onToggle,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, tint = iconTint, modifier = Modifier.size(40.dp))
        }
    }
}

/** One DNS resolver row — the reference's selected-hot `.panel` with the
 *  HUD name, muted HUD address, real latency value and `.pbar` signal bars. */
@Composable
private fun DnsArcadeRow(
    name: String,
    address: String,
    detail: String,
    latencyMs: Long?,
    tested: Boolean,
    selected: Boolean,
    best: Boolean,
    onSelect: () -> Unit,
) {
    ArcadePanel(
        variant = if (selected) ArcadePanelVariant.HOT else ArcadePanelVariant.PLAIN,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        contentPadding = 12.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = name,
                        fontFamily = NgbHudFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (best) ArcadeChip(text = stringResource(R.string.dns_best_chip), tint = NgbArcadeColors.Lime)
                }
                Text(address, fontFamily = NgbHudFont, fontSize = 13.sp, color = NgbArcadeColors.Muted)
                Text(detail, fontSize = 12.sp, color = NgbArcadeColors.Muted)
            }
            DnsLatencyCell(latencyMs, tested)
            DnsSignalBars(latencyMs, tested)
            ArcadeOutlineButton(
                onClick = onSelect,
                tint = if (selected) ArcadeTint.NEON else ArcadeTint.AZURE,
            ) {
                Text(
                    text = stringResource(if (selected) R.string.dns_row_active else R.string.dns_row_select),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** The right-hand latency value: real ms colored by the kit thresholds,
 *  "Failed / timeout" for failed tests, or an honest "—" when untested. */
@Composable
private fun DnsLatencyCell(latencyMs: Long?, tested: Boolean) {
    when {
        tested && latencyMs != null -> Text(
            text = stringResource(R.string.dns_latency, latencyMs.toInt()),
            fontFamily = NgbHudFont,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = latencyColor(latencyMs),
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 52.dp),
        )
        tested -> Text(
            text = stringResource(R.string.dns_failed),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = NgbArcadeColors.Danger,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 52.dp),
        )
        else -> Text(
            text = "—",
            fontFamily = NgbHudFont,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = NgbArcadeColors.Muted,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 52.dp),
        )
    }
}

/**
 * The reference `.pbar`: five 5 dp bars (6/10/14/18/22 dp tall, LTR like the
 * CSS `direction:ltr`), filled bottom-aligned from the REAL measured latency.
 * Untested/failed resolvers show ALL BARS GRAY — fixing the prototype bug
 * where untested rows rendered two red bars (its `bars(999)` default).
 */
@Composable
private fun DnsSignalBars(latencyMs: Long?, tested: Boolean) {
    val filled = if (tested && latencyMs != null) latencyBarCount(latencyMs) else 0
    val fillColor = if (tested && latencyMs != null) latencyColor(latencyMs) else null
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(5) { index ->
                Box(
                    Modifier
                        .width(5.dp)
                        .height(SIGNAL_BAR_HEIGHTS[index])
                        .background(if (fillColor != null && index < filled) fillColor else NgbArcadeColors.Line),
                )
            }
        }
    }
}

private val SIGNAL_BAR_HEIGHTS = listOf(6.dp, 10.dp, 14.dp, 18.dp, 22.dp)

/** Reference `bars(ms)` fill count: <30→5, <50→4, <70→3, else 2. */
private fun latencyBarCount(latencyMs: Long): Int = when {
    latencyMs < 30L -> 5
    latencyMs < 50L -> 4
    latencyMs < 70L -> 3
    else -> 2
}

/** Reference bar/latency color: <30 Neon, <50 Lime, <70 Warn, else Danger. */
private fun latencyColor(latencyMs: Long): Color = when {
    latencyMs < 30L -> NgbArcadeColors.Neon
    latencyMs < 50L -> NgbArcadeColors.Lime
    latencyMs < 70L -> NgbArcadeColors.Warn
    else -> NgbArcadeColors.Danger
}

private fun readRemoteDns(context: android.content.Context): List<DnsResolver> =
    NexusApiClient.cachedSync(context)?.let { readDns(context, it) }.orEmpty()

private fun readDns(context: android.content.Context, data: JSONObject): List<DnsResolver> {
    val array = data.optJSONArray("dns") ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val address = item.optString("address")
        if (address.isBlank()) null else DnsResolver(
            item.optString("name", context.getString(R.string.dns_remote_default_name)), address,
            listOf(item.optString("category"), item.optString("description")).filter { it.isNotBlank() }.joinToString(" · ")
        )
    }
}

// =============================================================================
// NEW_STRINGS — new user-facing keys introduced by this restyle (not added to
// resources here; add to app/src/main/res/values/strings.xml + values-fa):
// ping_power_hint=Tap the ring to connect or disconnect the tunnel=برای اتصال یا قطع تونل روی حلقه بزنید
// ping_kv_profile=Profile=پروفایل
// ping_kv_protocol=Protocol=پروتکل
// dns_best_chip=Best=بهترین
// dns_row_select=Select=انتخاب
// dns_row_active=Active=فعال
// =============================================================================
