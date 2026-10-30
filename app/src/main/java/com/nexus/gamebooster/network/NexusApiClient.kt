package com.nexus.gamebooster.network

import android.content.Context
import com.nexus.gamebooster.R
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Cloudflare Worker control-plane client.
 *
 * The production Worker URL is baked into the app. It is not shown or editable
 * in the UI. This hides it from ordinary users but does not make the URL a
 * secret against APK inspection.
 */
object NexusApiClient {
    /** Production control plane. Kept as a constant: updates ship pre-connected. */
    const val DEFAULT_BASE_URL = "https://gameboster.nova-7bc9791a4174.workers.dev"

    val sessionInvalid = MutableStateFlow(false)

    // v1.1.5 account-bound licenses: surfaced to the UI when the Worker reports
    // that the active key has expired mid-session (typed expiry message per key type).
    val licenseExpiryNotice = MutableStateFlow("")

    private const val PREFS = "nexus_api_session"
    private const val API_BASE = "api_base"
    private const val TOKEN = "session_token"
    private const val DEVICE = "device_id"
    private const val SYNC = "last_sync_json"
    private const val LICENSE_CREATED_AT = "license_created_at"
    private const val LICENSE_EXPIRES_AT = "license_expires_at"
    private const val ACCOUNT_CODE = "account_code"

    fun token(context: Context) = prefs(context).getString(TOKEN, "").orEmpty()
    fun apiBase(context: Context): String = DEFAULT_BASE_URL
    fun hasSession(context: Context) = token(context).isNotBlank()
    fun cachedSync(context: Context): JSONObject? = runCatching { JSONObject(prefs(context).getString(SYNC, null) ?: "{}") }.getOrNull()
    /** License timestamps are Unix epoch seconds supplied by the authoritative API. */
    fun licenseCreatedAt(context: Context): Long = prefs(context).getLong(LICENSE_CREATED_AT, 0L)
    fun licenseExpiresAt(context: Context): Long = prefs(context).getLong(LICENSE_EXPIRES_AT, 0L)

    /** The account number issued for THIS installation ("User-XXXXX"), if any. */
    fun accountCode(context: Context): String = prefs(context).getString(ACCOUNT_CODE, "").orEmpty()

    /**
     * v1.1.5 account-bound licenses: issue (or re-fetch) the installation's unique
     * account number from the Worker. One account per install — the server binds
     * it to the immutable device hash and never re-issues for a known install.
     * v2.2.1: reconcile via /account/me FIRST so clearing app data or preferences
     * never creates a duplicate account for the same installation.
     */
    suspend fun issueAccountCode(context: Context): String = withContext(Dispatchers.IO) {
        val device = deviceId(context)
        // v2.2.1: reconcile with the server first; a network failure here just falls
        // through to the issue path (runCatching), never blocks account issuance.
        val restored = runCatching {
            request(context, "${DEFAULT_BASE_URL}/api/v1/account/me", "GET", null, null, device).optString("accountCode")
        }.getOrDefault("")
        if (restored.matches(Regex("User-[A-Z0-9]{5}"))) {
            prefs(context).edit().putString(ACCOUNT_CODE, restored).apply()
            return@withContext restored
        }
        val result = request(context, "${DEFAULT_BASE_URL}/api/v1/account/issue", "POST", JSONObject().put("deviceId", device), null, device)
        val code = result.optString("accountCode")
        require(result.optBoolean("ok") && code.matches(Regex("User-[A-Z0-9]{5}"))) {
            result.optString("message").ifBlank { context.getString(R.string.account_issue_server_hint) }
        }
        prefs(context).edit().putString(ACCOUNT_CODE,code).apply(); code
    }

    fun clearSession(context: Context) { prefs(context).edit().remove(TOKEN).remove(SYNC).apply() }

    suspend fun activate(context: Context, baseUrl: String, key: String): JSONObject = withContext(Dispatchers.IO) {
        val base = baseUrl.trim().trimEnd('/')
        require(base.startsWith("https://")) { context.getString(R.string.api_error_base_url) }
        val device = deviceId(context)
        val response = request(context, "$base/api/v1/license/activate", "POST", JSONObject().put("key", key.trim()).put("deviceId", device), null)
        if (response.optBoolean("valid").not()) error(response.optString("message", context.getString(R.string.api_error_key_rejected)))
        val token = response.optString("token")
        require(token.isNotBlank()) { context.getString(R.string.api_error_no_token) }
        val createdAt = response.optLong("createdAt", 0L)
        val expiresAt = response.optLong("expiresAt", 0L)
        require(expiresAt > 0L) { context.getString(R.string.api_error_no_expiry) }
        prefs(context).edit()
            .remove(API_BASE)
            .putString(TOKEN, token)
            .putLong(LICENSE_CREATED_AT, createdAt)
            .putLong(LICENSE_EXPIRES_AT, expiresAt)
            .apply()
        sessionInvalid.value = false
        sync(context)
        response
    }

    // ---- Phase 12 (1.0.0): support tickets ---------------------------------
    // Every entry point re-checks the manually typed key against the ACTIVE
    // session of this app via the server; the server enforces license+device
    // ownership, so a foreign or stale key is rejected with 403
    // (KEY_SESSION_MISMATCH) without touching the stored app session.

    /** Verify the manually entered key belongs to the same license as this active app session. */
    suspend fun verifyTicketKey(context: Context, key: String): JSONObject = withContext(Dispatchers.IO) {
        val token = token(context)
        require(token.isNotBlank()) { context.getString(R.string.api_error_activate_first) }
        request(context, "${apiBase(context)}/api/v1/tickets/verify", "POST",
            JSONObject().put("key", key.trim()).put("deviceId", deviceId(context)), token, deviceId(context))
    }

    suspend fun createTicket(context: Context, key: String, subject: String, message: String): JSONObject = withContext(Dispatchers.IO) {
        request(context, "${apiBase(context)}/api/v1/tickets", "POST",
            JSONObject().put("key", key.trim()).put("subject", subject.trim()).put("message", message.trim()).put("deviceId", deviceId(context)), token(context), deviceId(context))
    }

    suspend fun listTickets(context: Context, key: String): JSONObject = withContext(Dispatchers.IO) {
        request(context, "${apiBase(context)}/api/v1/tickets/list", "POST",
            JSONObject().put("key", key.trim()).put("deviceId", deviceId(context)), token(context), deviceId(context))
    }

    suspend fun replyTicket(context: Context, key: String, ticketId: String, message: String): JSONObject = withContext(Dispatchers.IO) {
        request(context, "${apiBase(context)}/api/v1/tickets/${ticketId}/reply", "POST",
            JSONObject().put("key", key.trim()).put("message", message.trim()).put("deviceId", deviceId(context)), token(context), deviceId(context))
    }

    suspend fun sync(context: Context): JSONObject = withContext(Dispatchers.IO) {
        val base = apiBase(context)
        val token = token(context)
        require(base.isNotBlank() && token.isNotBlank()) { context.getString(R.string.api_error_activate_first) }
        val result = try {
            request(context, "$base/api/v1/sync", "GET", null, token, deviceId(context))
        } catch (e: SecurityException) {
            // v1.1.5: a typed expiry message from the Worker is promoted to the UI
            // (the gate screen shows it as a dialog) before the session is dropped.
            if (e.message.orEmpty().contains("به پایان رسیده") || e.message.orEmpty().contains("منقضی")) licenseExpiryNotice.value=e.message.orEmpty()
            clearSession(context)
            sessionInvalid.value = true
            throw e
        }
        if (!result.optBoolean("ok")) error(result.optString("message", context.getString(R.string.api_error_sync_failed)))
        sessionInvalid.value = false
        val expiry = result.optLong("licenseExpiresAt", 0L)
        val created = result.optLong("licenseCreatedAt", 0L)
        prefs(context).edit().apply {
            putString(SYNC, result.toString())
            if (expiry > 0L) putLong(LICENSE_EXPIRES_AT, expiry)
            if (created > 0L) putLong(LICENSE_CREATED_AT, created)
        }.apply()
        // Import active remote WireGuard configs into the app's private profile directory.
        val configs = result.optJSONArray("wireguard")
        val activeIds = mutableSetOf<String>()
        if (configs != null) for (i in 0 until configs.length()) {
            val item = configs.optJSONObject(i) ?: continue
            val id = item.optString("id")
            val name = item.optString("name", "WireGuard")
            val text = item.optString("config_text")
            if (id.isNotBlank() && text.isNotBlank()) {
                val imported = WireGuardController.importConfigText(context, id, name, text)
                if (imported.isSuccess) activeIds += id
            }
        }
        WireGuardController.removeRemoteProfilesNotIn(context, activeIds)
        result
    }

    private fun deviceId(context: Context): String {
        val p = prefs(context)
        val existing = p.getString(DEVICE, null)
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        p.edit().putString(DEVICE, created).apply()
        return created
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun request(context: Context, url: String, method: String, body: JSONObject?, token: String?, deviceId: String? = null): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
            if (!deviceId.isNullOrBlank()) setRequestProperty("X-Device-Id", deviceId)
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (code !in 200..299) {
                if (code == 401 || code == 403) throw SecurityException(json.optString("message", context.getString(R.string.api_error_access_denied)))
                error(json.optString("message", context.getString(R.string.api_error_http, code)))
            }
            return json
        } finally { connection.disconnect() }
    }
}
