package com.nexus.gamebooster.shizuku

import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.BufferedReader

/**
 * Phase 9 — bridge to Shizuku (ADB-level / shell UID 2000 privileges without full root).
 *
 * Responsibilities:
 *  - Track binder alive + permission granted state
 *  - Request permission from the user (via Shizuku manager)
 *  - Execute shell commands under the elevated identity
 *  - Provide a single honest status for the UI
 *
 * Nothing here claims root. If Shizuku is not running or not authorized, every elevated
 * operation returns [ShellResult.Unavailable] instead of faking success.
 */
class ShizukuPrivilege {

    enum class Status {
        /** Shizuku app not installed or binder not present. */
        NOT_RUNNING,
        /** Binder is up but this app has not been granted the Shizuku permission yet. */
        PERMISSION_REQUIRED,
        /** Ready — elevated commands can run. */
        READY,
    }

    data class ShellResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    ) {
        val isSuccess: Boolean get() = exitCode == 0

        companion object {
            fun unavailable(reason: String) = ShellResult(
                exitCode = -1,
                stdout = "",
                stderr = reason,
            )
        }
    }

    private val _status = MutableStateFlow(Status.NOT_RUNNING)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val binderListener = Shizuku.OnBinderReceivedListener {
        refreshStatus()
    }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        _status.value = Status.NOT_RUNNING
    }
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE_PERMISSION) {
            refreshStatus()
        }
    }

    private var listenersAttached = false

    /** Call once from Application / ServiceLocator after Context is ready. */
    fun start() {
        if (listenersAttached) return
        listenersAttached = true
        Shizuku.addBinderReceivedListenerSticky(binderListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        refreshStatus()
    }

    fun stop() {
        if (!listenersAttached) return
        listenersAttached = false
        Shizuku.removeBinderReceivedListener(binderListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }

    fun refreshStatus() {
        _status.value = when {
            !Shizuku.pingBinder() -> Status.NOT_RUNNING
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED ->
                Status.PERMISSION_REQUIRED
            else -> Status.READY
        }
    }

    /** Triggers the Shizuku permission dialog (no-op if already granted or binder down). */
    fun requestPermission() {
        if (!Shizuku.pingBinder()) return
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            refreshStatus()
            return
        }
        Shizuku.requestPermission(REQUEST_CODE_PERMISSION)
    }

    val isReady: Boolean
        get() = _status.value == Status.READY

    /**
     * Runs a shell command as the Shizuku identity (shell / ADB level).
     * Returns [ShellResult.unavailable] when Shizuku is not ready — never pretends success.
     *
     * Uses the official `IShizukuService.newProcess` AIDL path: `Shizuku.newProcess`
     * is not public in API 13.1.5, and the service interface is the documented way
     * to spawn an elevated process. Stdout/stderr are drained concurrently so a
     * chatty stderr cannot deadlock a full pipe while stdout is being read.
     */
    suspend fun exec(vararg command: String): ShellResult = withContext(Dispatchers.IO) {
        if (!isReady) {
            return@withContext ShellResult.unavailable("Shizuku not ready: ${_status.value}")
        }
        try {
            val binder = Shizuku.getBinder()
                ?: return@withContext ShellResult.unavailable("Shizuku binder is null")
            val service = IShizukuService.Stub.asInterface(binder)
                ?: return@withContext ShellResult.unavailable("IShizukuService unavailable")
            val process = service.newProcess(command, null, null)

            val stderrText = async {
                ParcelFileDescriptor.AutoCloseInputStream(process.errorStream)
                    .bufferedReader().use(BufferedReader::readText)
            }
            val stdout = ParcelFileDescriptor.AutoCloseInputStream(process.inputStream)
                .bufferedReader().use(BufferedReader::readText)
            val stderr = stderrText.await()
            val code = process.waitFor()
            ShellResult(exitCode = code, stdout = stdout.trim(), stderr = stderr.trim())
        } catch (t: Throwable) {
            ShellResult.unavailable(t.message ?: t.javaClass.simpleName)
        }
    }

    /** Convenience: `sh -c "<script>"`. */
    suspend fun execSh(script: String): ShellResult = exec("sh", "-c", script)

    /**
     * Force-stop a single package. Honest result.
     */
    suspend fun forceStop(packageName: String): ShellResult =
        exec("am", "force-stop", packageName)

    /**
     * Put package into the device-idle whitelist (ignore battery optimizations).
     * Uses the classic dumpsys path that works on most AOSP / OEM builds under shell.
     */
    suspend fun whitelistFromBatteryOptimization(packageName: String): ShellResult =
        exec("dumpsys", "deviceidle", "whitelist", "+$packageName")

    /**
     * Set App Standby bucket (active / working_set / frequent / rare / restricted).
     */
    suspend fun setStandbyBucket(packageName: String, bucket: String): ShellResult =
        exec("am", "set-standby-bucket", packageName, bucket)

    /**
     * ART compile for better runtime performance of the target game.
     * [mode] typical values: "speed-profile", "speed", "quicken".
     */
    suspend fun compilePackage(packageName: String, mode: String = "speed-profile"): ShellResult =
        exec("cmd", "package", "compile", "-m", mode, "-f", packageName)

    /**
     * Trim system caches (frees reclaimable RAM). Size is a hint in bytes; 0 = aggressive.
     */
    suspend fun trimCaches(bytesHint: Long = 0L): ShellResult =
        exec("pm", "trim-caches", bytesHint.toString())

    /**
     * Temporarily disable window / transition / animator scales (0 = off).
     * Caller should restore previous values after the gaming session if desired.
     */
    suspend fun setAnimationScales(scale: Float): ShellResult = execSh(
        "settings put global window_animation_scale $scale; " +
            "settings put global transition_animation_scale $scale; " +
            "settings put global animator_duration_scale $scale"
    )

    companion object {
        private const val REQUEST_CODE_PERMISSION = 9001
    }
}
