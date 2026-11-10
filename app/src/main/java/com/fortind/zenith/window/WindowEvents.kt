package com.fortind.zenith.window

import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.fortind.zenith.timeline.Note
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

sealed interface WindowEvent {
    data class NotePosted(val note: Note, val edited: Boolean, val openerWindowId: String? = null) : WindowEvent

    data class NoteDeleted(val noteId: String) : WindowEvent

    data class MyReaction(val noteId: String, val reaction: String?) : WindowEvent

    data class Renoted(val noteId: String, val renoted: Boolean) : WindowEvent

    data class Voted(val noteId: String, val choices: List<Int>) : WindowEvent

    data class Bookmarked(val note: Note, val present: Boolean) : WindowEvent

    data class ThreadMuted(val noteId: String, val muted: Boolean) : WindowEvent

    data class AuthorHidden(val userId: String, val renotesOnly: Boolean) : WindowEvent

    data class RelationChanged(val userId: String) : WindowEvent
}

class WindowEvents {
    private class Envelope(val origin: Any, val event: WindowEvent)

    private val events = MutableSharedFlow<Envelope>(extraBufferCapacity = BUFFER)

    fun link(origin: Any): WindowLink = object : WindowLink {
        override fun publish(event: WindowEvent) {
            events.tryEmit(Envelope(origin, event))
        }

        override val fromOtherWindows: Flow<WindowEvent> = events.filter { it.origin !== origin }.map { it.event }
    }

    private companion object {
        const val BUFFER = 64
    }
}

interface WindowLink {
    fun publish(event: WindowEvent)

    val fromOtherWindows: Flow<WindowEvent>

    object Alone : WindowLink {
        override fun publish(event: WindowEvent) = Unit

        override val fromOtherWindows: Flow<WindowEvent> = emptyFlow()
    }
}

fun CreationExtras.windowLink(events: WindowEvents): WindowLink =
    events.link(this[VIEW_MODEL_STORE_OWNER_KEY]?.viewModelStore ?: Any())
