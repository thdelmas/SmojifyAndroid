package com.emuji.emuji

import com.spotify.protocol.types.Track

/**
 * Sealed class representing different states of the player.
 */
sealed class PlayerState {
    object Idle : PlayerState()
    object Loading : PlayerState()
    data class Playing(val track: Track) : PlayerState()
    data class Paused(val track: Track) : PlayerState()
    data class Error(val message: String, val throwable: Throwable? = null) : PlayerState()
}

/**
 * Sealed class representing authentication state.
 */
sealed class AuthState {
    object NotAuthenticated : AuthState()
    object Authenticating : AuthState()
    data class Authenticated(val token: String) : AuthState()
    data class Error(val message: String) : AuthState()
}

/**
 * Sealed class representing results of operations.
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Exception, val message: String? = null) : Result<Nothing>()
    object Loading : Result<Nothing>()
}
