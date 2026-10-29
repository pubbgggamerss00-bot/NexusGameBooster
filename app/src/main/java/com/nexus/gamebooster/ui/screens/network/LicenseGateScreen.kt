package com.nexus.gamebooster.ui.screens.network

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.gamebooster.R
import com.nexus.gamebooster.network.NexusApiClient
import com.nexus.gamebooster.ui.arcade.ArcadeBurst
import com.nexus.gamebooster.ui.arcade.ArcadeButton
import com.nexus.gamebooster.ui.arcade.ArcadeField
import com.nexus.gamebooster.ui.arcade.ArcadePanel
import com.nexus.gamebooster.ui.arcade.ArcadePanelVariant
import com.nexus.gamebooster.ui.arcade.ArcadeStatusDot
import com.nexus.gamebooster.ui.arcade.ArcadeTint
import com.nexus.gamebooster.ui.arcade.NgbArcadeColors
import com.nexus.gamebooster.ui.components.NgbNeonBackdrop
import com.nexus.gamebooster.ui.components.NgbNeonSpinner
import com.nexus.gamebooster.ui.components.NgbStaggerIn
import com.nexus.gamebooster.ui.motion.LocalNgbMotionEnabled
import com.nexus.gamebooster.ui.theme.NgbTagFont
import com.nexus.gamebooster.ui.theme.neonGlow
import kotlinx.coroutines.launch

/**
 * License gate — v1.2.1 "ARCADE HUD" restyle (the reference's renderLicense):
 * a centered column with the floating rocket (110 dp, ±8 dp / 4 s float,
 * motion-gated, neon glow), the Orbitron "NEXUS BOOSTER" wordmark ("BOOSTER"
 * in neon) and the key entry inside a chamfered [ArcadePanel] — password HUD
 * field, status message line and a full-width 3D-lip verify button. On a
 * successful activation the kit's [ArcadeBurst] particle celebration fires
 * once. The retry state (server unreachable, saved session kept) renders as
 * a WARN panel with a retry button.
 *
 * The production API base is built into the app ([NexusApiClient.DEFAULT_BASE_URL])
 * and is intentionally not shown or editable in the UI — the user only types
 * their access key. After a successful activation the session token persists in
 * private prefs and every later start re-syncs silently [NexusApiClient.sync];
 * the key is only asked for again if the server revokes the session (401/403).
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
    // Celebration burst: bumped on every REAL successful activation (0 = idle no-op).
    var burstKey by remember { mutableIntStateOf(0) }

    Surface(color = Color.Transparent) {
        Box(Modifier.fillMaxSize()) {
            NgbNeonBackdrop(Modifier.fillMaxSize())
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Brand block — floating rocket + Orbitron wordmark (reference brand row).
                NgbStaggerIn(0) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val motion = LocalNgbMotionEnabled.current
                        val rocketY: State<Float> = if (motion) {
                            rememberInfiniteTransition(label = "rocketFloat").animateFloat(
                                initialValue = 8f,
                                targetValue = -8f,
                                animationSpec = infiniteRepeatable(tween(4000), RepeatMode.Reverse),
                                label = "rocketY",
                            )
                        } else {
                            remember { mutableStateOf(0f) }
                        }
                        Image(
                            painter = painterResource(R.drawable.rocket_splash),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(110.dp)
                                .neonGlow(
                                    glowColor = NgbArcadeColors.Neon,
                                    shape = CircleShape,
                                    blurRadius = 20.dp,
                                    glowAlpha = 0.6f,
                                )
                                .graphicsLayer { translationY = rocketY.value.dp.toPx() },
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onBackground)) { append("NEXUS ") }
                                withStyle(SpanStyle(color = NgbArcadeColors.Neon)) { append("BOOSTER") }
                            },
                            fontFamily = NgbTagFont,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            letterSpacing = 0.9.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.license_subtitle),
                            fontSize = 13.sp,
                            color = NgbArcadeColors.Muted,
                        )
                    }
                }
                if (showRetry) {
                    Spacer(Modifier.height(16.dp))
                    NgbStaggerIn(1) {
                        ArcadePanel(variant = ArcadePanelVariant.WARN) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(
                                    stringResource(R.string.license_retry_title),
                                    color = NgbArcadeColors.Warn,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.license_retry_body),
                                    fontSize = 13.sp,
                                    color = NgbArcadeColors.Muted,
                                )
                                Spacer(Modifier.height(12.dp))
                                ArcadeButton(
                                    onClick = { onRetry?.invoke() },
                                    enabled = !busy,
                                    tint = ArcadeTint.NEON,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.license_retry_action))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))

                // Key card — the reference's license panel: label, password HUD field,
                // status line and the full-width verify button.
                NgbStaggerIn(2) {
                    ArcadePanel {
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                stringResource(R.string.license_key_label),
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                            )
                            Spacer(Modifier.height(10.dp))
                            ArcadeField(
                                value = key,
                                onValueChange = { key = it },
                                hint = stringResource(R.string.license_key_label),
                                isPassword = true,
                                maxLength = 24,
                            )
                            Spacer(Modifier.height(12.dp))
                            // Status line with the arcade status dot while checking.
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (busy) ArcadeStatusDot(NgbArcadeColors.Neon)
                                if (busy) Spacer(Modifier.size(6.dp))
                                Text(
                                    message ?: stringResource(R.string.license_initial_message),
                                    color = when {
                                        messageIsError -> NgbArcadeColors.Danger
                                        messageIsSuccess -> NgbArcadeColors.Neon
                                        else -> NgbArcadeColors.Muted
                                    },
                                    fontSize = 13.sp,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.license_hint),
                                fontSize = 12.sp,
                                color = NgbArcadeColors.Muted,
                            )
                            Spacer(Modifier.height(14.dp))
                            ArcadeButton(
                                onClick = {
                                    if (busy) return@ArcadeButton
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
                                            burstKey++
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
                                tint = ArcadeTint.NEON,
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
                }
            }
            // The kit's celebration burst, fired once on successful activation.
            ArcadeBurst(
                burstKey = burstKey,
                tint = NgbArcadeColors.Neon,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
