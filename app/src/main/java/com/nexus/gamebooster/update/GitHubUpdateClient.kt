package com.nexus.gamebooster.update

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase 11 — real HTTPS client for the app's GitHub releases.
 *
 * Deliberately NO api.github.com calls: the public JSON API is rate-limited per
 * IP, which breaks hard for users behind CGNAT/shared addresses (one exhausted
 * neighbor quota = "update check failed" for everyone). Instead this client uses
 * only plain github.com traffic:
 *   1. `…/releases/latest` → 302 Location header → release tag (no API, no quota).
 *   2. APK + whatsnew assets via their deterministic `releases/download/<tag>/…`
 *      URLs (same rate class as any GitHub file download).
 * All calls run on [Dispatchers.IO] and return honest failures — a missing piece
 * degrades (size -1 → indeterminate progress, notes null → generic text), it is
 * never faked.
 */
object GitHubUpdateClient {

    private const val REPO_BASE = "https://github.com/pubbgggamerss00-bot/NexusGameBooster"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val COPY_BUFFER_BYTES = 64 * 1024

    /**
     * Resolves the latest published release for [languageTag]:
     * tag via the releases/latest redirect, APK URL + HEAD size, friendly notes via
     * the `whatsnew-<lang>.txt` asset when it exists. A notes/size failure never
     * fails the check — those degrade gracefully; a tag failure DOES fail (honest).
     */
    suspend fun fetchLatestRelease(languageTag: String): Result<UpdateTools.LatestRelease> =
        withContext(Dispatchers.IO) {
            runCatching {
                val tag = openGet("$REPO_BASE/releases/latest", followRedirects = false) { connection ->
                    when (connection.responseCode) {
                        in 300..399 -> UpdateTools.parseTagFromLocation(
                            connection.getHeaderField("Location"),
                        ) ?: error("releases/latest redirected without a tag: ${connection.getHeaderField("Location")}")

                        else -> error("HTTP ${connection.responseCode} for $REPO_BASE/releases/latest")
                    }
                }
                val versionName = tag.removePrefix("v")
                val apkUrl = UpdateTools.apkAssetUrl(REPO_BASE, tag)
                val size = runCatching {
                    openGet(apkUrl, method = "HEAD") { it.contentLengthLong }
                }.getOrNull()?.takeIf { it > 0 } ?: -1L
                val notes = runCatching {
                    httpGetString(UpdateTools.whatsNewAssetUrl(REPO_BASE, tag, languageTag))
                }.getOrNull()
                UpdateTools.LatestRelease(
                    tagName = tag,
                    versionName = versionName,
                    apkUrl = apkUrl,
                    apkSizeBytes = size,
                    whatsNewText = notes?.trim()?.takeIf { it.isNotEmpty() },
                )
            }
        }

    /**
     * Streams [url] into [destDir]/[fileName] with real progress callbacks.
     * Pre-existing files in [destDir] with the same name are deleted first, so a
     * previously interrupted download can never be mistaken for a fresh one.
     */
    suspend fun downloadApk(
        url: String,
        destDir: File,
        fileName: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            destDir.mkdirs()
            val dest = File(destDir, fileName)
            if (dest.exists()) dest.delete()
            openGet(url) { connection ->
                val total = connection.contentLengthLong
                connection.inputStream.use { input ->
                    File(dest.absolutePath).outputStream().use { output ->
                        val buffer = ByteArray(COPY_BUFFER_BYTES)
                        var downloaded = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, total)
                        }
                        output.flush()
                    }
                }
                if (total > 0 && dest.length() != total) {
                    dest.delete()
                    error("downloaded ${dest.length()} of $total bytes — connection ended early")
                }
                dest
            }
        }
    }

    private fun httpGetString(
        url: String,
        configure: (HttpURLConnection) -> Unit = {},
    ): String = openGet(url, configure = configure) { it.inputStream.readBytes().decodeToString() }

    /** GET/HEAD with timeouts, optional redirect following and honest non-2xx errors. */
    private inline fun <T> openGet(
        url: String,
        method: String = "GET",
        followRedirects: Boolean = true,
        configure: (HttpURLConnection) -> Unit = {},
        read: (HttpURLConnection) -> T,
    ): T {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = followRedirects
        configure(connection)
        try {
            return read(connection)
        } finally {
            connection.disconnect()
        }
    }
}
