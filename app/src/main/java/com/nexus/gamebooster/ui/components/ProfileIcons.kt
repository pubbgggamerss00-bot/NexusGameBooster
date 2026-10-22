package com.nexus.gamebooster.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import com.nexus.gamebooster.profiles.ProfileIds

/**
 * UI-layer icon map for profiles. Icons deliberately live HERE and not in the pure
 * domain model (`profiles/PerformanceProfile.kt` must stay free of Compose types so it
 * stays JVM-testable). Unknown or legacy ids fall back to the Balanced icon.
 */
fun profileIcon(profileId: String): ImageVector = when (profileId) {
    ProfileIds.PERFORMANCE -> Icons.Outlined.Speed
    ProfileIds.BATTERY_SAVER -> Icons.Outlined.BatterySaver
    else -> Icons.Outlined.Tune
}
