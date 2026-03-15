package com.emuji.emuji

import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * ViewModel for PlayerActivity managing player state and user settings.
 */
class PlayerViewModel : ViewModel() {
    private val _playerState = MutableLiveData<PlayerState>(PlayerState.Idle)
    val playerState: LiveData<PlayerState> = _playerState

    private val _authState = MutableLiveData<AuthState>(AuthState.NotAuthenticated)
    val authState: LiveData<AuthState> = _authState

    private val _isPublic = MutableLiveData<Boolean>(false)
    val isPublic: LiveData<Boolean> = _isPublic

    private val _isCollaborative = MutableLiveData<Boolean>(false)
    val isCollaborative: LiveData<Boolean> = _isCollaborative

    private val _isWorldWide = MutableLiveData<Boolean>(false)
    val isWorldWide: LiveData<Boolean> = _isWorldWide

    private val _currentTrackUri = MutableLiveData<String?>()
    val currentTrackUri: LiveData<String?> = _currentTrackUri

    /**
     * Updates player state
     */
    fun updatePlayerState(state: PlayerState) {
        _playerState.value = state
    }

    /**
     * Updates authentication state
     */
    fun updateAuthState(state: AuthState) {
        _authState.value = state
    }

    /**
     * Updates current track URI
     */
    fun updateCurrentTrackUri(uri: String?) {
        _currentTrackUri.value = uri
    }

    /**
     * Loads settings from SharedPreferences
     */
    fun loadSettings(sharedPreferences: SharedPreferences) {
        _isPublic.value = sharedPreferences.getBoolean("isPlaylistsPublic", false)
        _isCollaborative.value = sharedPreferences.getBoolean("isPlaylistsCollaborative", false)
        _isWorldWide.value = sharedPreferences.getBoolean("contributeGlobalPlaylist", false)
    }

    /**
     * Handles emoji reaction to current track
     */
    fun reactToTrack(
        emujiService: EmujiService?,
        context: android.content.Context,
        token: String?,
        emoji: String,
        emojiBitmap: android.graphics.Bitmap,
        trackUri: String?
    ) {
        viewModelScope.launch {
            if (token == null || trackUri == null) {
                updatePlayerState(PlayerState.Error("Missing authentication or track information"))
                return@launch
            }

            emujiService?.reactToTrack(
                context,
                token,
                emoji,
                emojiBitmap,
                _isPublic.value ?: false,
                _isCollaborative.value ?: false,
                _isWorldWide.value ?: false,
                trackUri
            )
        }
    }
}
