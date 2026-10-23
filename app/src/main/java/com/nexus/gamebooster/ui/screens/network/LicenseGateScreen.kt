package com.nexus.gamebooster.ui.screens.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.nexus.gamebooster.R
import com.nexus.gamebooster.network.NexusApiClient
import kotlinx.coroutines.launch

/**
 * License gate. The production API base is built into the app
 * ([NexusApiClient.DEFAULT_BASE_URL]) and is intentionally not shown or
 * editable in the UI — the user only types their access key.
 *
 * After a successful activation the session token persists in private prefs
 * and every later start re-syncs silently [NexusApiClient.sync]; the key is
 * only asked for again if the server revokes the session (401/403).
 */
@Composable
fun LicenseGateScreen(showRetry: Boolean = false, onActivated: () -> Unit, onRetry: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var messageIsSuccess by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.Key, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(56.dp))
            Text("NEXUS GAME BOOSTER", style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.license_subtitle), style = MaterialTheme.typography.titleMedium)
            if (showRetry) {
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.license_retry_title),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.license_retry_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { onRetry?.invoke() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.license_retry_action))
                }
                Spacer(Modifier.height(16.dp))
            }
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text(stringResource(R.string.license_key_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                if (busy) return@Button
                busy = true
                messageIsError = false
                messageIsSuccess = false
                message = context.getString(R.string.license_checking)
                scope.launch {
                    try {
                        NexusApiClient.activate(context, NexusApiClient.DEFAULT_BASE_URL, key)
                        message = context.getString(R.string.license_success)
                        messageIsSuccess = true
                        messageIsError = false
                        onActivated()
                    } catch (e: Exception) {
                        message = e.message ?: context.getString(R.string.license_failed_default)
                        messageIsError = true
                        messageIsSuccess = false
                    } finally { busy = false }
                }
            }, enabled = !busy && key.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                if (busy) CircularProgressIndicator() else Text(stringResource(R.string.license_action))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                message ?: stringResource(R.string.license_initial_message),
                color = when {
                    messageIsError -> MaterialTheme.colorScheme.error
                    messageIsSuccess -> androidx.compose.ui.graphics.Color(0xFF2E7D32)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(stringResource(R.string.license_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
