package com.nexus.gamebooster.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Phase 11 — installs an APK this app just downloaded, the honest Android way.
 *
 * Self-updating is a two-step system contract (no root, no Shizuku needed):
 *  1. The per-app "Install unknown apps" switch (REQUEST_INSTALL_PACKAGES) must be
 *     allowed once — [openInstallPermissionSettings] sends the user to exactly that
 *     one page for THIS app, [canRequestInstalls] reads the real state.
 *  2. The actual install always goes through Android's own package installer UI —
 *     a one-tap system confirmation that this app cannot and must not bypass.
 */
object ApkInstaller {

    /** Real state of the per-app "Install unknown apps" switch (API 26+). */
    fun canRequestInstalls(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** Opens the system page with ONLY this app's "Install unknown apps" switch. */
    fun openInstallPermissionSettings(context: Context): Result<Unit> = runCatching {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Hands the downloaded APK to the system installer. The installer dialog opens
     * over the app; the user confirms there (Android requirement). The app process
     * is restarted by the system when the update completes.
     */
    fun installApk(context: Context, apkFile: File): Result<Unit> = runCatching {
        require(apkFile.exists() && apkFile.length() > 0) { "downloaded APK is missing" }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.updatefiles",
            apkFile,
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_ACTIVITY_NEW_TASK,
            )
        context.startActivity(intent)
    }
}
