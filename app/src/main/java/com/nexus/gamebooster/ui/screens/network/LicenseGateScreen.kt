package com.nexus.gamebooster.ui.screens.network

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.gamebooster.R
import com.nexus.gamebooster.network.NexusApiClient
import com.nexus.gamebooster.ui.components.NgbButton
import com.nexus.gamebooster.ui.components.NgbLogoChip
import com.nexus.gamebooster.ui.components.NgbNeonBackdrop
import com.nexus.gamebooster.ui.components.NgbNeonSpinner
import com.nexus.gamebooster.ui.components.NgbSecondaryButton
import com.nexus.gamebooster.ui.components.NgbStaggerIn
import com.nexus.gamebooster.ui.motion.LocalNgbMotionEnabled
import com.nexus.gamebooster.ui.theme.NgbChamferShape
import com.nexus.gamebooster.ui.theme.NgbPrimary
import com.nexus.gamebooster.ui.theme.NgbSecondary
import com.nexus.gamebooster.ui.theme.NgbTertiary
import kotlinx.coroutines.launch

/**
 * License gate — "NEON ARENA v2" look (Phase 11.5, from the user's neon-theme
 * mockup): star-field backdrop, gradient logo chip + wordmark, and the key
 * entry inside the mockup's violet card (.card) with a kit CTA. The production
 * API base is built into the app ([NexusApiClient.DEFAULT_BASE_URL]) and is
 * intentionally not shown or editable in the UI — the user only types their
 * access key.
 *
 * While the key is being checked the CTA swaps to the neon spinner
 * ([NgbNeonSpinner]) and a live status chip — the fancy loading for opening
 * this screen. After a successful activation the session token persists in
 * private prefs and every later start re-syncs silently [NexusApiClient.sync];
 * the key is only asked for again if the server revokes the session (401/403).
 */
@Composable
fun LicenseGateScreen(
    showRetry: Boolean = false,
    // v2.2.0 account-bound licenses: typed expiry message from the Worker,
    // shown as a dismissible dialog on top of the gate.
    expiryNotice: String = "",
    onExpiryNoticeDismiss: () -> Unit = {},
    onActivated: () -> Unit,
    onRetry: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var messageIsSuccess by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    // v2.2.0: this installation's account number ("User-XXXXX"), issued once and
    // bound to the device on the server.
    var accountCode by remember { mutableStateOf(NexusApiClient.accountCode(context)) }

    // v2.2.0: mid-session / re-login expiry notice (typed per key type by the Worker).
    if (expiryNotice.isNotBlank()) {
        AlertDialog(
            onDismissRequest = onExpiryNoticeDismiss,
            title = { Text(stringResource(R.string.license_expired_title)) },
            text = { Text(expiryNotice) },
            confirmButton = {
                TextButton(onClick = onExpiryNoticeDismiss) { Text(stringResource(R.string.license_expired_dismiss)) }
            },
        )
    }

    Surface(color = androidx.compose.ui.graphics.Color.Transparent) {
        Box(Modifier.fillMaxSize()) {
            NgbNeonBackdrop(Modifier.fillMaxSize())
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Brand block — gradient chip + gradient wordmark (mockup top bar).
                NgbStaggerIn(0) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        NgbLogoChip(chipSize = 64.dp)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "NEXUS GAME BOOSTER",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                brush = Brush.linearGradient(listOf(NgbPrimary, NgbTertiary)),
                                fontWeight = FontWeight.ExtraBold,
                            ),
                        )
                        Text(
                            stringResource(R.string.license_subtitle),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (showRetry) {
                    Spacer(Modifier.height(16.dp))
                    NgbStaggerIn(1) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))

                // Key card — v1.2.1 re-skin: the kit `.panel` (chamfered
                // gradient ring + dark card fill; was the rounded violet card).
                NgbStaggerIn(2) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(NgbChamferShape(18.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(NgbPrimary.copy(alpha = 0.75f), NgbTertiary.copy(alpha = 0.18f), NgbPrimary.copy(alpha = 0.40f)),
                                    start = androidx.compose.ui.geometry.Offset.Zero,
                                    end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                                ),
                            )
                            .padding(1.dp)
                            .clip(NgbChamferShape(17.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.97f))
                            .background(
                                Brush.verticalGradient(listOf(NgbSecondary.copy(alpha = 0.06f), NgbTertiary.copy(alpha = 0.03f))),
                            )
                            .padding(15.dp),
                    ) {
                        OutlinedTextField(
                            value = key,
                            onValueChange = { key = it },
                            label = { Text(stringResource(R.string.license_key_label)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NgbTertiary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(14.dp))
                        // v2.2.0 account-bound licenses: show this installation's
                        // account number once issued; otherwise offer the one-tap
                        // issuance. The server binds the account to the device and
                        // private keys are issued against it.
                        if (accountCode.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.account_yours, accountCode),
                                color = NgbTertiary,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        } else {
                            Spacer(Modifier.height(8.dp))
                            NgbSecondaryButton(
                                onClick = {
                                    if (busy) return@NgbSecondaryButton
                                    busy = true
                                    message = context.getString(R.string.account_issue_progress)
                                    messageIsError = false
                                    messageIsSuccess = false
                                    scope.launch {
                                        try {
                                            accountCode = NexusApiClient.issueAccountCode(context)
                                            message = context.getString(R.string.account_issue_success)
                                            messageIsSuccess = true
                                            messageIsError = false
                                        } catch (e: Exception) {
                                            message = e.message ?: context.getString(R.string.account_issue_failed)
                                            messageIsError = true
                                            messageIsSuccess = false
                                        } finally {
                                            busy = false
                                        }
                                    }
                                },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.account_issue_gate)) }
                        }
                        NgbButton(
                            onClick = {
                                if (busy) return@NgbButton
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
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            enabled = !busy && key.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (busy) {
                                NgbNeonSpinner(spinnerSize = 18.dp)
                                Spacer(Modifier.size(10.dp))
                                Text(stringResource(R.string.license_checking))
                            } else {
                                Text(stringResource(R.string.license_action))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                NgbStaggerIn(3) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        // Status line with the mockup's blinking status dot while checking.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (busy) NgbStatusDot()
                            if (busy) Spacer(Modifier.size(6.dp))
                            Text(
                                message ?: stringResource(R.string.license_initial_message),
                                color = when {
                                    messageIsError -> MaterialTheme.colorScheme.error
                                    messageIsSuccess -> NgbPrimary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.license_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (showRetry) {
                            Spacer(Modifier.height(14.dp))
                            NgbSecondaryButton(
                                onClick = { onRetry?.invoke() },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.license_retry_action), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The mockup's .status-dot: 8 dp blinking green dot. Static full opacity when
 * motion is disabled.
 */
@Composable
private fun NgbStatusDot() {
    val motionEnabled = LocalNgbMotionEnabled.current
    if (motionEnabled) {
        val transition = rememberInfiniteTransition(label = "ngbDot")
        val alpha by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(
                tween(750),
                RepeatMode.Reverse,
            ),
            label = "dotAlpha",
        )
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(NgbPrimary.copy(alpha = alpha)),
        )
    } else {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(NgbPrimary),
        )
    }
}
