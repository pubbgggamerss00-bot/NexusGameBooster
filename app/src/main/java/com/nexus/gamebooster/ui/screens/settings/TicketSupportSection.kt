package com.nexus.gamebooster.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nexus.gamebooster.R
import com.nexus.gamebooster.network.NexusApiClient
import com.nexus.gamebooster.ui.components.NgbButton
import com.nexus.gamebooster.ui.components.NgbNeonSpinner
import com.nexus.gamebooster.ui.components.NgbOutlinedButton
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private enum class TicketDialogStage { NONE, REGISTER_KEY, LIST_KEY, CREATE, LIST }

/**
 * Phase 12 (1.0.0): support tickets.
 *
 * Two entry points — "Submit a support ticket" and "My tickets". Both first
 * ask for the access key and verify it via [NexusApiClient.verifyTicketKey]:
 * the server re-checks the typed key against the ACTIVE session of this very
 * app (license + device ownership), so a foreign or stale key is rejected
 * without ever touching the stored app session (a 403 here only surfaces as
 * an inline error message — no logout, the session stays intact).
 *
 * After verification the user can create a ticket (subject + description) or
 * list their tickets with the full message thread and, for open/answered
 * tickets, reply to support. All UI strings are localized EN/FA; server
 * messages are shown verbatim (the control plane owns their wording).
 */
@Composable
fun TicketSupportSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stage by rememberSaveable { mutableStateOf(TicketDialogStage.NONE) }
    var key by rememberSaveable { mutableStateOf("") }
    var subject by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf("") }
    var notice by rememberSaveable { mutableStateOf("") }
    var tickets by remember { mutableStateOf(JSONArray()) }
    val replies = remember { mutableStateMapOf<String, String>() }

    // Captured string values — the coroutines below run outside composition.
    val errEnterKey = stringResource(R.string.ticket_error_enter_key)
    val errKeyMismatch = stringResource(R.string.ticket_error_key_mismatch)
    val errVerify = stringResource(R.string.ticket_error_verify)
    val errCreate = stringResource(R.string.ticket_error_create)
    val errReply = stringResource(R.string.ticket_error_reply)
    val okKeyVerified = stringResource(R.string.ticket_notice_key_ok)
    val okLoadedEmpty = stringResource(R.string.ticket_notice_loaded_empty)
    val okLoaded = stringResource(R.string.ticket_notice_loaded)
    val okCreated = stringResource(R.string.ticket_notice_created)
    val okReplySent = stringResource(R.string.ticket_notice_reply_sent)

    fun begin(next: TicketDialogStage) {
        key = ""; subject = ""; message = ""; error = ""; notice = ""; stage = next
    }
    fun verifyAndContinue(next: TicketDialogStage) {
        if (busy || key.isBlank()) { error = errEnterKey; return }
        busy = true; error = ""; notice = ""
        scope.launch {
            try {
                val result = NexusApiClient.verifyTicketKey(context, key)
                if (!result.optBoolean("valid")) {
                    error = result.optString("message", errKeyMismatch)
                } else if (next == TicketDialogStage.LIST) {
                    val data = NexusApiClient.listTickets(context, key)
                    tickets = data.optJSONArray("items") ?: JSONArray()
                    replies.clear()
                    for (i in 0 until tickets.length()) {
                        replies[tickets.optJSONObject(i)?.optString("id").orEmpty()] = ""
                    }
                    stage = TicketDialogStage.LIST
                    notice = if (tickets.length() == 0) okLoadedEmpty else okLoaded
                } else {
                    stage = TicketDialogStage.CREATE
                    notice = okKeyVerified
                }
            } catch (e: Exception) {
                error = e.message ?: errVerify
            } finally {
                busy = false
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        NgbOutlinedButton(
            onClick = { begin(TicketDialogStage.REGISTER_KEY) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.ticket_open_create))
        }
        NgbOutlinedButton(
            onClick = { begin(TicketDialogStage.LIST_KEY) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.ticket_open_list))
        }
        Text(
            text = stringResource(R.string.ticket_section_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (stage == TicketDialogStage.REGISTER_KEY || stage == TicketDialogStage.LIST_KEY) {
        val isList = stage == TicketDialogStage.LIST_KEY
        AlertDialog(
            onDismissRequest = { if (!busy) stage = TicketDialogStage.NONE },
            title = {
                Text(
                    stringResource(
                        if (isList) R.string.ticket_key_dialog_title_list
                        else R.string.ticket_key_dialog_title_create,
                    )
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.ticket_key_dialog_body))
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it.uppercase() },
                        label = { Text(stringResource(R.string.ticket_key_field_label)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (error.isNotBlank()) {
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (notice.isNotBlank()) {
                        Text(notice, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    }
                    if (busy) NgbNeonSpinner()
                }
            },
            confirmButton = {
                NgbButton(
                    enabled = !busy && key.isNotBlank(),
                    onClick = { verifyAndContinue(if (isList) TicketDialogStage.LIST else TicketDialogStage.CREATE) },
                ) {
                    Text(stringResource(R.string.ticket_key_verify_button))
                }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { stage = TicketDialogStage.NONE }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (stage == TicketDialogStage.CREATE) {
        AlertDialog(
            onDismissRequest = { if (!busy) stage = TicketDialogStage.NONE },
            title = { Text(stringResource(R.string.ticket_create_title)) },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.ticket_create_body))
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text(stringResource(R.string.ticket_subject_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text(stringResource(R.string.ticket_message_label)) },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                    if (notice.isNotBlank()) Text(notice, color = MaterialTheme.colorScheme.primary)
                    if (busy) NgbNeonSpinner()
                }
            },
            confirmButton = {
                NgbButton(
                    enabled = !busy && subject.trim().length >= 3 && message.trim().length >= 5,
                    onClick = {
                        busy = true; error = ""; notice = ""
                        scope.launch {
                            try {
                                NexusApiClient.createTicket(context, key, subject, message)
                                notice = okCreated
                                subject = ""; message = ""
                            } catch (e: Exception) {
                                error = e.message ?: errCreate
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.ticket_create_button))
                }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { stage = TicketDialogStage.NONE }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }

    if (stage == TicketDialogStage.LIST) {
        AlertDialog(
            onDismissRequest = { if (!busy) stage = TicketDialogStage.NONE },
            title = { Text(stringResource(R.string.ticket_list_title)) },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (tickets.length() == 0) {
                        Text(notice.ifBlank { stringResource(R.string.ticket_list_empty) })
                    }
                    for (i in 0 until tickets.length()) {
                        val ticket = tickets.optJSONObject(i) ?: JSONObject()
                        val tid = ticket.optString("id")
                        val status = ticket.optString("status", "open")
                        val statusLabel = when (status) {
                            "open" -> stringResource(R.string.ticket_status_open)
                            "answered" -> stringResource(R.string.ticket_status_answered)
                            "rejected" -> stringResource(R.string.ticket_status_rejected)
                            "closed" -> stringResource(R.string.ticket_status_closed)
                            else -> status
                        }
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                ticket.optString("subject", stringResource(R.string.ticket_default_subject)),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = stringResource(R.string.ticket_status_prefix, statusLabel),
                                color = if (status == "rejected") {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                            val msgs = ticket.optJSONArray("messages") ?: JSONArray()
                            for (j in 0 until msgs.length()) {
                                val m = msgs.optJSONObject(j) ?: continue
                                val sender = if (m.optString("sender") == "admin") {
                                    stringResource(R.string.ticket_sender_admin)
                                } else {
                                    stringResource(R.string.ticket_sender_user)
                                }
                                Text(
                                    stringResource(R.string.ticket_message_line, sender, m.optString("message")),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (status != "rejected" && status != "closed") {
                                OutlinedTextField(
                                    value = replies[tid].orEmpty(),
                                    onValueChange = { replies[tid] = it },
                                    label = { Text(stringResource(R.string.ticket_reply_label)) },
                                    minLines = 2,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                NgbButton(
                                    enabled = !busy && replies[tid].orEmpty().trim().isNotEmpty(),
                                    onClick = {
                                        val reply = replies[tid].orEmpty().trim()
                                        busy = true; error = ""
                                        scope.launch {
                                            try {
                                                NexusApiClient.replyTicket(context, key, tid, reply)
                                                val refreshed = NexusApiClient.listTickets(context, key)
                                                tickets = refreshed.optJSONArray("items") ?: JSONArray()
                                                replies[tid] = ""
                                                notice = okReplySent
                                            } catch (e: Exception) {
                                                error = e.message ?: errReply
                                            } finally {
                                                busy = false
                                            }
                                        }
                                    },
                                ) {
                                    Text(stringResource(R.string.ticket_reply_button))
                                }
                            }
                        }
                        Spacer(Modifier.height(1.dp))
                    }
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                    if (notice.isNotBlank()) Text(notice, color = MaterialTheme.colorScheme.primary)
                    if (busy) NgbNeonSpinner()
                }
            },
            confirmButton = {
                TextButton(onClick = { stage = TicketDialogStage.NONE }) {
                    Text(stringResource(R.string.action_close))
                }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { verifyAndContinue(TicketDialogStage.LIST) }) {
                    Text(stringResource(R.string.ticket_refresh))
                }
            },
        )
    }
}
