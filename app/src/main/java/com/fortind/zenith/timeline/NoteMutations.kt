package com.fortind.zenith.timeline

fun mergeNewer(existing: List<Note>, incoming: List<Note>): List<Note> {
    if (incoming.isEmpty()) return existing
    val known = existing.mapTo(HashSet()) { it.id }
    val fresh = incoming.filter { known.add(it.id) }
    return if (fresh.isEmpty()) existing else fresh + existing
}

fun mergeOlder(existing: List<Note>, incoming: List<Note>): List<Note> {
    if (incoming.isEmpty()) return existing
    val known = existing.mapTo(HashSet()) { it.id }
    val fresh = incoming.filter { known.add(it.id) }
    return if (fresh.isEmpty()) existing else existing + fresh
}

fun Note.mapNote(id: String, transform: (Note) -> Note): Note {
    val self = if (this.id == id) transform(this) else this
    val renote = self.renote?.mapNote(id, transform)
    val reply = self.reply?.mapNote(id, transform)
    return if (renote === self.renote && reply === self.reply) self else self.copy(renote = renote, reply = reply)
}

fun List<Note>.mapNote(id: String, transform: (Note) -> Note): List<Note> {
    var changed = false
    val mapped = map { note ->
        note.mapNote(id, transform).also { if (it !== note) changed = true }
    }
    return if (changed) mapped else this
}

fun Note.contains(id: String): Boolean = this.id == id || renote?.contains(id) == true || reply?.contains(id) == true

fun Note.asDeleted(): Note = copy(
    text = null,
    textMfm = emptyList(),
    cw = null,
    cwMfm = null,
    files = emptyList(),
    poll = null,
    reactions = emptyMap(),
    renote = null,
    reply = null,
    deleted = true,
)

fun List<Note>.removeNote(id: String): List<Note> {
    val kept = filterNot { it.id == id || (it.isPureRenote && it.renote?.id == id) }
    return kept.mapNote(id) { it.asDeleted() }.let { if (it === kept && kept.size == size) this else it }
}

fun List<Note>.findNote(id: String): Note? {
    forEach { note ->
        if (note.id == id) return note
        note.renote?.let { listOf(it).findNote(id) }?.let { return it }
        note.reply?.let { listOf(it).findNote(id) }?.let { return it }
    }
    return null
}

val Note.captureIds: Sequence<String>
    get() = sequence {
        yield(target.id)
        target.renote?.let { yield(it.id) }
        target.reply?.let { yield(it.id) }
    }
