package com.nexus.gamebooster.ui.screens.network

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nexus.gamebooster.R
import com.nexus.gamebooster.network.DnsForwardingVpnService
import com.nexus.gamebooster.network.DnsResolverTester
import com.nexus.gamebooster.network.DnsTestResult
import com.nexus.gamebooster.network.WireGuardController
import com.nexus.gamebooster.network.WireGuardProfile
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun PingGoodScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var profiles by remember { mutableStateOf(WireGuardController.listProfiles(context)) }
    var selectedProfileId by rememberSaveable { mutableStateOf(WireGuardController.selectedProfileId(context)) }
    var wgConnected by remember { mutableStateOf(WireGuardController.isConnected(context)) }
    var dnsConnected by remember { mutableStateOf(DnsForwardingVpnService.isRunning) }
    var results by remember { mutableStateOf(emptyList<DnsTestResult>()) }
    var hasTested by rememberSaveable { mutableStateOf(false) }
    var selectedDns by rememberSaveable { mutableStateOf("") }
    var pendingDns by remember { mutableStateOf("") }

    fun startDnsTunnel(address: String) {
        val intent = Intent(context, DnsForwardingVpnService::class.java)
            .putExtra(DnsForwardingVpnService.EXTRA_RESOLVER, address)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ContextCompat.startForegroundService(context, intent)
            else context.startService(intent)
            scope.launch {
                delay(500)
                dnsConnected = DnsForwardingVpnService.isRunning
                message = if (dnsConnected) context.getString(R.string.dns_connected)
                else context.getString(R.string.dns_test_error, "Could not start the DNS tunnel")
            }
        } catch (e: Exception) {
            dnsConnected = false
            message = context.getString(R.string.dns_test_error, e.message ?: "Could not start the DNS tunnel")
        }
    }

    fun connectWireGuard() {
        if (dnsConnected || DnsForwardingVpnService.isRunning) {
            message = context.getString(R.string.ping_vpn_conflict); return
        }
        busy = true; message = context.getString(R.string.wg_working)
        scope.launch {
            val result = WireGuardController.connect(context, selectedProfileId)
            wgConnected = result.isSuccess
            message = if (result.isSuccess) context.getString(R.string.wg_connect_success)
            else context.getString(R.string.wg_connect_error, result.exceptionOrNull()?.message ?: "Unknown error")
            busy = false
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
        else message = context.getString(R.string.ping_vpn_conflict)
    }
    val dnsPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && pendingDns.isNotBlank()) {
            startDnsTunnel(pendingDns)
        } else message = context.getString(R.string.ping_vpn_conflict)
    }

    LaunchedEffect(Unit) {
        profiles = WireGuardController.listProfiles(context)
        selectedProfileId = WireGuardController.selectedProfileId(context)
        wgConnected = WireGuardController.isConnected(context)
        dnsConnected = DnsForwardingVpnService.isRunning
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Filled.NetworkCheck, null, Modifier.padding(13.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ping_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.ping_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TabRow(selectedTabIndex = tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(R.string.ping_tab_wireguard)) }, icon = { Icon(Icons.Filled.Security, null) })
            Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.ping_tab_dns)) }, icon = { Icon(Icons.Filled.Speed, null) })
        }
        if (message.isNotBlank()) Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Text(message, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
        }

        if (tab == 0) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.Security, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.wg_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(stringResource(if (wgConnected) R.string.wg_connected else R.string.wg_disconnected), color = if (wgConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(stringResource(R.string.wg_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider()
                    Text(stringResource(if (profiles.isNotEmpty()) R.string.wg_profile_ready else R.string.wg_no_profile))
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
                            Text(profile.name, modifier = Modifier.weight(1f), fontWeight = if (selectedProfileId == profile.id) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, enabled = !busy && !wgConnected, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.wg_import)) }
                    Button(onClick = {
                        if (wgConnected) {
                            busy = true
                            scope.launch {
                                val result = WireGuardController.disconnect(context)
                                wgConnected = false
                                message = if (result.isSuccess) context.getString(R.string.wg_disconnect_success) else context.getString(R.string.wg_connect_error, result.exceptionOrNull()?.message ?: "Unknown error")
                                busy = false
                            }
                        } else if (dnsConnected || DnsForwardingVpnService.isRunning) message = context.getString(R.string.ping_vpn_conflict)
                        else {
                            val prepare = VpnService.prepare(context)
                            if (prepare != null) wgPermission.launch(prepare) else connectWireGuard()
                        }
                    }, enabled = !busy && (wgConnected || selectedProfileId.isNotBlank()), modifier = Modifier.fillMaxWidth()) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(if (wgConnected) R.string.wg_disconnect else R.string.wg_connect))
                    }
                    Text(stringResource(R.string.ping_vpn_conflict), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.Cloud, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.dns_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(stringResource(if (dnsConnected) R.string.dns_connected else R.string.dns_disconnected), color = if (dnsConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(stringResource(R.string.dns_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = {
                            busy = true; message = context.getString(R.string.dns_testing)
                            scope.launch {
                                results = DnsResolverTester.testAll()
                                hasTested = true
                                if (results.none { it.resolver.address == selectedDns && it.successful }) selectedDns = results.firstOrNull { it.successful }?.resolver?.address.orEmpty()
                                message = if (results.any { it.successful }) "" else context.getString(R.string.dns_test_error, "No DNS server responded")
                                busy = false
                            }
                        }, enabled = !busy && !dnsConnected, modifier = Modifier.fillMaxWidth()) {
                            if (busy && message == context.getString(R.string.dns_testing)) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.dns_test_all))
                        }
                    }
                }
                if (results.isEmpty()) {
                    Text(stringResource(R.string.dns_select_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DnsResolverTester.defaults.forEach { resolver -> DnsResolverCard(resolver.name, resolver.address, resolver.detail, null, selectedDns == resolver.address, false) { selectedDns = resolver.address } }
                } else results.forEachIndexed { index, result ->
                    DnsResolverCard(result.resolver.name, result.resolver.address, result.resolver.detail, result.latencyMs, selectedDns == result.resolver.address, result.successful, if (result.successful) index + 1 else null) { selectedDns = result.resolver.address }
                }
                Text(stringResource(R.string.dns_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = {
                    if (dnsConnected) {
                        context.stopService(Intent(context, DnsForwardingVpnService::class.java)); dnsConnected = false
                        message = context.getString(R.string.dns_disconnected)
                    } else if (wgConnected) message = context.getString(R.string.ping_vpn_conflict)
                    else {
                        pendingDns = selectedDns
                        val prepare = VpnService.prepare(context)
                        if (prepare != null) dnsPermission.launch(prepare) else {
                            startDnsTunnel(selectedDns)
                        }
                    }
                }, enabled = !busy && (dnsConnected || (hasTested && selectedDns.isNotBlank() && results.any { it.resolver.address == selectedDns && it.successful })) && (dnsConnected || !wgConnected), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (dnsConnected) R.string.dns_disconnect else R.string.dns_connect))
                }
                if (wgConnected) Text(stringResource(R.string.ping_vpn_conflict), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DnsResolverCard(name: String, address: String, detail: String, latency: Long?, selected: Boolean, success: Boolean, rank: Int? = null, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(if (selected) Icons.Filled.CheckCircle else Icons.Filled.Cloud, null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (rank != null && success) "$rank. $name" else name, fontWeight = FontWeight.SemiBold)
                Text(address, style = MaterialTheme.typography.bodyMedium)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (latency != null && success) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.dns_latency, latency.toInt()), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    if (selected) Text("✓", color = MaterialTheme.colorScheme.primary)
                }
            } else if (rank == null) Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else Text(stringResource(R.string.dns_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
        }
    }
}
