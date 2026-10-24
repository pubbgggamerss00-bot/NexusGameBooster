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
    // Kept separately from the user's currently highlighted profile: the active tunnel
    // may still be using an older profile after the selection changes.
    private const val ACTIVE_ID = "active_profile_id"
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

    /** Imports a server-provided profile using a stable ID, updating it on each sync. */
    suspend fun importConfigText(context: Context, remoteId: String, displayName: String, text: String): Result<WireGuardProfile> = withContext(Dispatchers.IO) {
        runCatching {
            require(text.length in 1..128_000) { "Configuration file is empty or too large" }
            Config.parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
            val safeId = "remote-" + remoteId.replace(Regex("[^A-Za-z0-9_-]"), "_").take(100)
            val dir = profileDir(context)
            val target = configFile(context, safeId)
            val temp = File(dir, "$safeId.conf.tmp")
            temp.writeText(text, Charsets.UTF_8)
            require(temp.renameTo(target) || (temp.copyTo(target, overwrite = true).exists().also { temp.delete() })) { "Could not save configuration securely" }
            nameFile(context, safeId).writeText(displayName.replace(Regex("[\\r\\n\\t]"), " ").trim().take(64).ifBlank { "WireGuard profile" }, Charsets.UTF_8)
            WireGuardProfile(safeId, displayName)
        }
    }

    /** Removes remote-managed profiles that are no longer active on the server. */
    suspend fun removeRemoteProfilesNotIn(context: Context, activeServerIds: Set<String>) = withContext(Dispatchers.IO) {
        val keep = activeServerIds.map { "remote-" + it.replace(Regex("[^A-Za-z0-9_-]"), "_").take(100) }.toSet()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val selected = prefs.getString(SELECTED_ID, "").orEmpty()
        profileDir(context).listFiles().orEmpty().filter { it.isFile && it.extension == "conf" && it.nameWithoutExtension.startsWith("remote-") && it.nameWithoutExtension !in keep }.forEach { file ->
            val profileId = file.nameWithoutExtension
            if (activeProfileId(context) == profileId && isConnected(context)) disconnect(context)
            file.delete()
            nameFile(context, profileId).delete()
            if (selected == profileId) prefs.edit().remove(SELECTED_ID).apply()
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
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(ACTIVE_ID, profileId).apply()
        }
    }

    suspend fun disconnect(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // setState returns Tunnel.State; return Unit explicitly so the declared
            // Result<Unit> matches (Kotlin 2.0 does not coerce this automatically).
            backend(context).setState(tunnel, Tunnel.State.DOWN, null)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(ACTIVE_ID).apply()
            Unit
        }
    }

    suspend fun deleteProfile(context: Context, profileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(profileId.isNotBlank()) { "Select a profile to delete" }
            // Do not remove the config backing an active tunnel.
            if (activeProfileId(context) == profileId && isConnected(context)) {
                error("Disconnect WireGuard before deleting the active profile")
            }
            val config = configFile(context, profileId)
            require(config.exists()) { "Profile not found" }
            check(config.delete()) { "Could not delete configuration" }
            nameFile(context, profileId).takeIf { it.exists() }?.delete()
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getString(SELECTED_ID, "") == profileId) {
                prefs.edit().remove(SELECTED_ID).apply()
            }
        }
    }

    fun activeProfileId(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ACTIVE_ID, "").orEmpty()

    fun isConnected(context: Context): Boolean = try {
        val connected = backend(context).getState(tunnel) == Tunnel.State.UP
        if (!connected) {
            // Heal stale metadata after Android tears the tunnel down outside the app.
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(ACTIVE_ID).apply()
        }
        connected
    } catch (_: Exception) { false }
}
