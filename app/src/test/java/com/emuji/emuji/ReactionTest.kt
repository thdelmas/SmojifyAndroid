package com.emuji.emuji

import org.junit.Assert.*
import org.junit.Test

class ReactionTest {

    @Test
    fun `reaction stores emoji and track uri`() {
        val reaction = Reaction(inputText = "😊", trackUri = "spotify:track:abc123")
        assertEquals("😊", reaction.inputText)
        assertEquals("spotify:track:abc123", reaction.trackUri)
    }

    @Test
    fun `reactions with same data are equal`() {
        val a = Reaction("🔥", "spotify:track:1")
        val b = Reaction("🔥", "spotify:track:1")
        assertEquals(a, b)
    }

    @Test
    fun `reactions with different emojis are not equal`() {
        val a = Reaction("🔥", "spotify:track:1")
        val b = Reaction("😭", "spotify:track:1")
        assertNotEquals(a, b)
    }

    @Test
    fun `reactions with different tracks are not equal`() {
        val a = Reaction("🔥", "spotify:track:1")
        val b = Reaction("🔥", "spotify:track:2")
        assertNotEquals(a, b)
    }
}
