package com.fortind.zenith.timeline

import org.json.JSONArray
import org.json.JSONObject

data class PollBody(
    val choices: List<String>,
    val multiple: Boolean,
    val expiresAtMillis: Long?,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("choices", JSONArray(choices))
        .put("multiple", multiple)
        .put("expiresAt", expiresAtMillis ?: JSONObject.NULL)
}

fun Poll.toBody(): PollBody = PollBody(choices.map { it.text }, multiple, expiresAtMillis)

data class NewNote(
    val text: String?,
    val cw: String? = null,
    val visibility: Visibility = Visibility.Public,
    val localOnly: Boolean = false,
    val replyId: String? = null,
    val renoteId: String? = null,
    val fileIds: List<String> = emptyList(),
    val visibleUserIds: List<String> = emptyList(),
    val poll: PollBody? = null,
    val reactionAcceptance: ReactionAcceptance = ReactionAcceptance.All,
)

fun NewNote.toCreateRequest(token: String): JSONObject = JSONObject().apply {
    put("i", token)
    text?.takeIf { it.isNotBlank() }?.let { put("text", it) }
    cw?.let { put("cw", it) }
    put("visibility", visibility.apiName)
    put("localOnly", localOnly)
    replyId?.let { put("replyId", it) }
    renoteId?.let { put("renoteId", it) }
    if (fileIds.isNotEmpty()) put("fileIds", JSONArray(fileIds))
    if (visibility == Visibility.Specified) put("visibleUserIds", JSONArray(visibleUserIds))
    poll?.let { put("poll", it.toJson()) }
    reactionAcceptance.apiName?.let { put("reactionAcceptance", it) }
}

fun NewNote.toEditRequest(token: String, noteId: String): JSONObject = JSONObject().apply {
    put("i", token)
    put("editId", noteId)
    put("text", text?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
    put("cw", cw ?: JSONObject.NULL)
    put("visibility", visibility.apiName)
    put("localOnly", localOnly)
    put("fileIds", JSONArray(fileIds))
    if (visibility == Visibility.Specified) put("visibleUserIds", JSONArray(visibleUserIds))
    renoteId?.let { put("renoteId", it) }
    poll?.let { put("poll", it.toJson()) }
    put("reactionAcceptance", reactionAcceptance.apiName ?: JSONObject.NULL)
}
