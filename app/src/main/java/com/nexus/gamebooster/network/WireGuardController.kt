package com.nexus.gamebooster.network

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class WireGuardProfile(val id: String, val name: String)

/** Thin adapter around WireGuard's official embeddable Android tunnel library. */
object WireGuardController {
    private const val PROFILE_DIR = "wireguard-profiles"
    private const val PREFS = "wireguard_profiles"
    private const val SELECTED_ID = "selected_profile_id"
    private const val TUNNEL_NAME = "Nexus WireGuard"
    @Volatile private var backend: GoBackend? = null

    private val tunnel = object : Tunnel {
        override fun getName(): String = TUNNEL_NAME
        override fun onStateChange(newState: Tunnel.State) = Unit
    }

    private fun backend(context: Context): GoBackend = backend ?: synchronized(this) {
        backend ?: GoBackend(context.applicationContext).also { backend = it }
    }

    private fun profileDir(context: Context): File = File(context.filesDir, PROFILE_DIR).apply { mkdirs() }
    private fun configFile(context: Context, id: String) = File(profileDir(context), "$id.conf")
    private fun nameFile(context: Context, id: String) = File(profileDir(context), "$id.name")

    fun listProfiles(context: Context): List<WireGuardProfile> = profileDir(context).listFiles()
        .orEmpty().filter { it.isFile && it.extension == "conf" }
        .map { file ->
            val id = file.nameWithoutExtension
            WireGuardProfile(id, nameFile(context, id).takeIf { it.exists() }?.readText()?.trim().orEmpty().ifBlank { id })
        }.sortedBy { it.name.lowercase() }

    fun selectedProfileId(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(SELECTED_ID, "").orEmpty()
            .takeIf { id -> id.isNotBlank() && configFile(context, id).exists() }.orEmpty()

    fun selectProfile(context: Context, id: String) {
        if (configFile(context, id).exists()) context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(SELECTED_ID, id).apply()
    }

    suspend fun importConfig(context: Context, uri: Uri): Result<WireGuardProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("Cannot open configuration file")
            require(text.length in 1..128_000) { "Configuration file is empty or too large" }
            Config.parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
            val rawName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: uri.lastPathSegment ?: "WireGuard profile"
            val displayName = rawName.substringAfterLast('/').removeSuffix(".conf").replace(Regex("[\\r\\n\\t]"), " ").trim().take(64).ifBlank { "WireGuard profile" }
            val id = UUID.randomUUID().toString()
            val dir = profileDir(context)
            val temp = File(dir, "$id.conf.tmp")
            temp.writeText(text, Charsets.UTF_8)
            require(temp.renameTo(configFile(context, id))) { "Could not save configuration securely" }
            nameFile(context, id).writeText(displayName, Charsets.UTF_8)
            selectProfile(context, id)
            WireGuardProfile(id, displayName)
        }
    }

    suspend fun connect(context: Context, profileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(profileId.isNotBlank()) { "Select a WireGuard profile first" }
            val text = configFile(context, profileId).readText(Charsets.UTF_8)
            val config = Config.parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
            backend(context).setState(tunnel, Tunnel.State.UP, config)
            check(backend(context).getState(tunnel) == Tunnel.State.UP) { "WireGuard tunnel did not become active" }
            selectProfile(context, profileId)
        }
    }

    suspend fun disconnect(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // setState returns Tunnel.State; return Unit explicitly so the declared
            // Result<Unit> matches (Kotlin 2.0 does not coerce this automatically).
            backend(context).setState(tunnel, Tunnel.State.DOWN, null)
            Unit
        }
    }

    fun isConnected(context: Context): Boolean = try {
        backend(context).getState(tunnel) == Tunnel.State.UP
    } catch (_: Exception) { false }
}
