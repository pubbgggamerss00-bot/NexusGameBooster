package com.nexus.gamebooster.data

import android.os.Build

/**
 * Real device identity from `android.os.Build`. This is genuine, permission-free
 * information — nothing here is faked.
 */
data class DeviceIdentity(
    val model: String,
    val manufacturer: String,
    val androidVersion: String,
    val sdkInt: Int,
)

fun deviceIdentity(): DeviceIdentity = DeviceIdentity(
    model = Build.MODEL,
    manufacturer = Build.MANUFACTURER,
    androidVersion = Build.VERSION.RELEASE,
    sdkInt = Build.VERSION.SDK_INT,
)

fun deviceModel(): String = Build.MODEL

fun androidVersion(): String = Build.VERSION.RELEASE
