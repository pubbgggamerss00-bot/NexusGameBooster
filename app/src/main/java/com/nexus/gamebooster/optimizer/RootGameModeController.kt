package com.nexus.gamebooster.optimizer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Optional root-only bridge for Android GameManager performance mode.
 * This deliberately exposes only two fixed commands (performance/standard), never an
 * arbitrary shell field. Root is requested only after the user taps a button.
 */
class RootGameModeController {
    data class Result(val success: Boolean, val message: String, val rootGranted: Boolean = false)

    suspend fun checkRoot(): Result = withContext(Dispatchers.IO) {
        runRootCommand("id") { code, output, error ->
            val granted = code == 0 && output.contains("uid=0")
            Result(granted, if (granted) "Root access confirmed" else error.ifBlank { output.ifBlank { "Root access unavailable or denied" } }, granted)
        }
    }

    suspend fun setPerformanceMode(packageName: String, enabled: Boolean): Result {
        val pkg = packageName.trim()
        if (!PACKAGE.matches(pkg)) return Result(false, "Enter a valid Android package name")
        val mode = if (enabled) "performance" else "standard"
        // package name is constrained to Android package characters before interpolation.
        return withContext(Dispatchers.IO) {
            runRootCommand("cmd game mode $mode $pkg") { code, output, error ->
                Result(code == 0, when {
                    code == 0 -> "GameManager mode set to $mode for $pkg"
                    error.isNotBlank() -> error
                    output.isNotBlank() -> output
                    else -> "Command failed ($code); this Android build may not support GameManager mode"
                }, rootGranted = code == 0)
            }
        }
    }

    private fun runRootCommand(command: String, finish: (Int, String, String) -> Result): Result {
        return try {
            val process = ProcessBuilder("su", "-c", command).start()
            val stdout = StringBuilder()
            val stderr = StringBuilder()
            val outThread = Thread { BufferedReader(InputStreamReader(process.inputStream)).useLines { lines -> lines.forEach { stdout.append(it).append('\n') } } }
            val errThread = Thread { BufferedReader(InputStreamReader(process.errorStream)).useLines { lines -> lines.forEach { stderr.append(it).append('\n') } } }
            outThread.start(); errThread.start()
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return Result(false, "Root command timed out")
            }
            outThread.join(500); errThread.join(500)
            finish(process.exitValue(), stdout.toString().trim(), stderr.toString().trim())
        } catch (e: Exception) {
            Result(false, e.message ?: "Root unavailable")
        }
    }

    private companion object {
        val PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
    }
}
