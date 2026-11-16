package com.fortind.zenith.ui.feed

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteKeysTest {

    @Test
    fun keysMatchSharkeyWeb() {
        assertEquals(NoteKey.Reply, noteKeyFor(Key.R, shift = false))
        assertEquals(NoteKey.React, noteKeyFor(Key.E, shift = false))
        assertEquals(NoteKey.React, noteKeyFor(Key.A, shift = false))
        assertEquals(NoteKey.React, noteKeyFor(Key.Plus, shift = false))
        assertEquals(NoteKey.React, noteKeyFor(Key.Equals, shift = true))
        assertEquals(NoteKey.Renote, noteKeyFor(Key.Q, shift = false))
        assertEquals(NoteKey.Menu, noteKeyFor(Key.M, shift = false))
        assertEquals(NoteKey.OpenMedia, noteKeyFor(Key.O, shift = false))
        assertEquals(NoteKey.ToggleContent, noteKeyFor(Key.V, shift = false))
    }

    @Test
    fun likeKeyIsKept() {
        assertEquals(NoteKey.Like, noteKeyFor(Key.L, shift = false))
    }

    @Test
    fun shiftedLettersAndOtherKeysAreIgnored() {
        assertNull(noteKeyFor(Key.R, shift = true))
        assertNull(noteKeyFor(Key.Equals, shift = false))
        assertNull(noteKeyFor(Key.X, shift = false))
    }
}
