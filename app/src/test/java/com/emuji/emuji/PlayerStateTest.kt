package com.emuji.emuji

import org.junit.Assert.*
import org.junit.Test

class PlayerStateTest {

    @Test
    fun `idle state is singleton`() {
        assertSame(PlayerState.Idle, PlayerState.Idle)
    }

    @Test
    fun `loading state is singleton`() {
        assertSame(PlayerState.Loading, PlayerState.Loading)
    }

    @Test
    fun `error state stores message`() {
        val error = PlayerState.Error("Connection failed")
        assertEquals("Connection failed", error.message)
        assertNull(error.throwable)
    }

    @Test
    fun `error state stores throwable`() {
        val exception = RuntimeException("test")
        val error = PlayerState.Error("fail", exception)
        assertEquals(exception, error.throwable)
    }
}

class AuthStateTest {

    @Test
    fun `not authenticated is singleton`() {
        assertSame(AuthState.NotAuthenticated, AuthState.NotAuthenticated)
    }

    @Test
    fun `authenticating is singleton`() {
        assertSame(AuthState.Authenticating, AuthState.Authenticating)
    }

    @Test
    fun `authenticated stores token`() {
        val state = AuthState.Authenticated("token123")
        assertEquals("token123", state.token)
    }

    @Test
    fun `auth error stores message`() {
        val state = AuthState.Error("Invalid token")
        assertEquals("Invalid token", state.message)
    }
}

class ResultTest {

    @Test
    fun `success wraps data`() {
        val result = Result.Success("hello")
        assertEquals("hello", result.data)
    }

    @Test
    fun `error wraps exception and message`() {
        val ex = IllegalArgumentException("bad")
        val result = Result.Error(ex, "Something went wrong")
        assertEquals(ex, result.exception)
        assertEquals("Something went wrong", result.message)
    }

    @Test
    fun `error message defaults to null`() {
        val result = Result.Error(RuntimeException())
        assertNull(result.message)
    }

    @Test
    fun `loading is singleton`() {
        assertSame(Result.Loading, Result.Loading)
    }
}
