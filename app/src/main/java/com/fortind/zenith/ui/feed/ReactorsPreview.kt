package com.fortind.zenith.ui.feed

import com.fortind.zenith.timeline.NoteUser

const val REACTORS_PREVIEW_LIMIT = 10
const val REACTORS_CACHE_SIZE = 32

data class ReactorsKey(val noteId: String, val reaction: String, val count: Int)

data class ReactorsPreview(
    val loading: Boolean = false,
    val failed: Boolean = false,
    val users: List<NoteUser> = emptyList(),
)

fun Map<ReactorsKey, ReactorsPreview>.needsLoad(key: ReactorsKey): Boolean = this[key]?.failed ?: true

fun Map<ReactorsKey, ReactorsPreview>.withPreview(key: ReactorsKey, preview: ReactorsPreview): Map<ReactorsKey, ReactorsPreview> {
    val updated = LinkedHashMap(this)
    updated.remove(key)
    updated.keys.filter { it.noteId == key.noteId && it.reaction == key.reaction }.forEach(updated::remove)
    updated[key] = preview
    while (updated.size > REACTORS_CACHE_SIZE) updated.remove(updated.keys.first())
    return updated
}
