package com.emuji.emuji

import org.junit.Assert.*
import org.junit.Test

class PlaylistTest {

    @Test
    fun `playlist data class stores all fields correctly`() {
        val playlist = Playlist(
            id = "abc123",
            name = "smiling face",
            uri = "spotify:playlist:abc123",
            trackCount = 5,
            imageUrl = "https://example.com/cover.jpg",
            owner = "testuser"
        )

        assertEquals("abc123", playlist.id)
        assertEquals("smiling face", playlist.name)
        assertEquals("spotify:playlist:abc123", playlist.uri)
        assertEquals(5, playlist.trackCount)
        assertEquals("https://example.com/cover.jpg", playlist.imageUrl)
        assertEquals("testuser", playlist.owner)
    }

    @Test
    fun `playlist with null imageUrl is valid`() {
        val playlist = Playlist(
            id = "abc123",
            name = "fire",
            uri = "spotify:playlist:abc123",
            trackCount = 0,
            imageUrl = null,
            owner = "testuser"
        )

        assertNull(playlist.imageUrl)
    }

    @Test
    fun `playlists with same data are equal`() {
        val a = Playlist("1", "name", "uri", 3, null, "owner")
        val b = Playlist("1", "name", "uri", 3, null, "owner")
        assertEquals(a, b)
    }

    @Test
    fun `playlists with different ids are not equal`() {
        val a = Playlist("1", "name", "uri", 3, null, "owner")
        val b = Playlist("2", "name", "uri", 3, null, "owner")
        assertNotEquals(a, b)
    }

    @Test
    fun `playlist copy works correctly`() {
        val original = Playlist("1", "happy", "uri:1", 10, "url", "owner")
        val updated = original.copy(trackCount = 11)
        assertEquals(11, updated.trackCount)
        assertEquals("happy", updated.name)
    }
}
