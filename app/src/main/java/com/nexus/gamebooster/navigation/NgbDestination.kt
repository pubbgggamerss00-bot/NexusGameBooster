package com.nexus.gamebooster.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import com.nexus.gamebooster.R

/**
 * Top-level destinations shown in the bottom navigation bar.
 * Each entry carries an outlined icon (unselected) and a filled icon (selected).
 */
enum class NgbDestination(
    @StringRes val labelRes: Int,
    val route: String,
    val outlinedIcon: ImageVector,
    val filledIcon: ImageVector,
) {
    Home(
        labelRes = R.string.nav_home,
        route = "home",
        outlinedIcon = Icons.Outlined.Home,
        filledIcon = Icons.Filled.Home,
    ),
    Library(
        labelRes = R.string.nav_library,
        route = "library",
        outlinedIcon = Icons.Outlined.SportsEsports,
        filledIcon = Icons.Filled.SportsEsports,
    ),
    Monitor(
        labelRes = R.string.nav_monitor,
        route = "monitor",
        outlinedIcon = Icons.Outlined.ShowChart,
        filledIcon = Icons.Filled.ShowChart,
    ),
    Profiles(
        labelRes = R.string.nav_profiles,
        route = "profiles",
        outlinedIcon = Icons.Outlined.Tune,
        filledIcon = Icons.Filled.Tune,
    ),
    Settings(
        labelRes = R.string.nav_settings,
        route = "settings",
        outlinedIcon = Icons.Outlined.Settings,
        filledIcon = Icons.Filled.Settings,
    ),
}
