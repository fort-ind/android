package com.fortind.zenith.timeline

import org.json.JSONArray
import org.json.JSONObject

data class NewNote(
    val text: String?,
    val cw: String? = null,
    val visibility: Visibility = Visibility.Public,
    val localOnly: Boolean = false,
    val replyId: String? = null,
    val renoteId: String? = null,
    val fileIds: List<String> = emptyList(),
    val visibleUserIds: List<String> = emptyList(),
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
}
