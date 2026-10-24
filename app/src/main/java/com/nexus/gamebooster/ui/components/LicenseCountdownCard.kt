package com.nexus.gamebooster.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.nexus.gamebooster.R
import com.nexus.gamebooster.network.NexusApiClient
import com.nexus.gamebooster.ui.theme.ngbNeonHalo
import com.nexus.gamebooster.ui.theme.NgbError
import com.nexus.gamebooster.ui.theme.NgbPrimary
import com.nexus.gamebooster.ui.theme.NgbSecondary
import com.nexus.gamebooster.ui.theme.NgbTertiary
import com.nexus.gamebooster.ui.theme.NgbTextMuted
import com.nexus.gamebooster.ui.theme.NgbOnBackground
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max

/** A live license countdown driven by the expiry timestamp returned by the API. */
@Composable
fun LicenseCountdownCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    // The API uses Unix epoch seconds; convert to milliseconds for wall-clock math.
    val createdAtMillis = NexusApiClient.licenseCreatedAt(context) * 1_000L
    val expiresAtMillis = NexusApiClient.licenseExpiresAt(context) * 1_000L
    val hasExpiry = expiresAtMillis > 0L
    val remainingMillis = if (hasExpiry) max(0L, expiresAtMillis - nowMillis) else 0L
    val expired = hasExpiry && remainingMillis <= 0L
    val warning = hasExpiry && remainingMillis <= 3L * 24L * 60L * 60L * 1_000L
    val accent = when {
        expired || warning -> NgbError
        else -> NgbPrimary
    }
    val totalMillis = if (createdAtMillis > 0L && expiresAtMillis > createdAtMillis) expiresAtMillis - createdAtMillis else remainingMillis.coerceAtLeast(1L)
    val progress = if (hasExpiry) (remainingMillis.toFloat() / totalMillis.toFloat()).coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(progress, animationSpec = tween(700, easing = FastOutSlowInEasing), label = "licenseRingProgress")
    val pulse = rememberInfiniteTransition(label = "licenseTimerPulse")
    val pulseScale by pulse.animateFloat(0.985f, 1.015f, animationSpec = infiniteRepeatable(tween(1_250, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "licenseTimerPulseScale")

    Card(
        modifier = modifier.fillMaxWidth().ngbNeonHalo(cornerRadius = 22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF140F26), Color(0xFF100C20), Color(0xFF1A1333))))
                .border(1.dp, Brush.sweepGradient(listOf(accent.copy(alpha = 0.85f), NgbTertiary, NgbSecondary, accent.copy(alpha = 0.85f))), RoundedCornerShape(22.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(116.dp).scale(pulseScale), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(108.dp)) {
                    val stroke = size.minDimension * 0.085f
                    drawArc(Color(0xFF322658), -90f, 360f, false, style = Stroke(stroke, cap = StrokeCap.Round))
                    if (hasExpiry) {
                        drawArc(
                            brush = Brush.sweepGradient(listOf(accent.copy(alpha = 0.45f), accent, NgbTertiary, accent)),
                            startAngle = -90f,
                            sweepAngle = 360f * animatedProgress,
                            useCenter = false,
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Filled.Bolt, contentDescription = null, tint = accent, modifier = Modifier.size(25.dp))
                    Text(if (!hasExpiry) "--" else if (expired) "00" else formatNumber(remainingMillis / 86_400_000L), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 28.sp)
                    Text(stringResource(R.string.license_countdown_days), color = NgbTextMuted, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.license_countdown_title), color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(if (hasExpiry) accent else NgbTextMuted))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = when {
                            !hasExpiry -> stringResource(R.string.license_countdown_unknown)
                            expired -> stringResource(R.string.license_countdown_expired)
                            warning -> stringResource(R.string.license_countdown_warning)
                            else -> stringResource(R.string.license_countdown_active)
                        },
                        color = if (hasExpiry) accent else NgbTextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (hasExpiry && !expired) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        TimeUnitBox(formatNumber((remainingMillis / 3_600_000L) % 24L), stringResource(R.string.license_countdown_hours), accent, Modifier.weight(1f))
                        TimeUnitBox(formatNumber((remainingMillis / 60_000L) % 60L), stringResource(R.string.license_countdown_minutes), accent, Modifier.weight(1f))
                        TimeUnitBox(formatNumber((remainingMillis / 1_000L) % 60L), stringResource(R.string.license_countdown_seconds), accent, Modifier.weight(1f))
                    }
                } else if (expired) {
                    Text("00 : 00 : 00", color = accent, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                } else {
                    Text("— : — : —", color = NgbOnBackground, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TimeUnitBox(value: String, label: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF0B0817).copy(alpha = 0.9f)).border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(10.dp)).padding(vertical = 7.dp, horizontal = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = accent, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, lineHeight = 21.sp)
        Text(label, color = NgbTextMuted, fontSize = 9.sp, textAlign = TextAlign.Center, maxLines = 1)
    }
}

private fun formatNumber(value: Long): String = NumberFormat.getIntegerInstance(Locale.getDefault()).format(value)
