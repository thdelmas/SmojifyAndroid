package com.emuji.emuji

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URL

/**
 * Background service that handles emoji reactions to tracks.
 * Manages personal playlist creation and global contribution to Emuji API using Coroutines.
 */
class EmujiService : Service() {
    private lateinit var sharedPreferences: SharedPreferences
    private var emojiAPI: EmojiUtil? = null
    private var spotifyAPI: SpotifyUtil? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    override fun onCreate() {
        super.onCreate()
        emojiAPI = EmojiUtil()
        spotifyAPI = SpotifyUtil()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            serviceScope.launch {
                val action = intent.action
                if (ACTION_REACT_TO_TRACK == action) {
                    handleReactToTrackIntent(intent)
                }
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    fun startService(context: Context) {
        val intent = Intent(context, EmujiService::class.java)
        intent.action = ACTION_START_EMUJI
        context.startService(intent)
        Log.d(TAG, "Service started correctly")
    }

    /**
     * Initiates a reaction to a track with an emoji.
     * Creates/updates playlists and sends data to Emuji API based on user settings.
     *
     * @param context Application context
     * @param token Spotify Web API access token
     * @param emoji The emoji character used for reaction
     * @param emojiBitmap Bitmap representation of the emoji for playlist cover
     * @param isPublic Whether playlists should be public
     * @param isCollaborative Whether playlists should be collaborative
     * @param isWorldWide Whether to contribute to worldwide playlists
     * @param trackUri The Spotify URI of the track
     */
    fun reactToTrack(
        context: Context,
        token: String?,
        emoji: String?,
        emojiBitmap: Bitmap?,
        isPublic: Boolean,
        isCollaborative: Boolean,
        isWorldWide: Boolean,
        trackUri: String?
    ) {
        if (emojiBitmap == null || token == null || emoji == null || trackUri == null) {
            Log.e(TAG, "Invalid parameters for reactToTrack")
            return
        }

        val byteArrayOutputStream = ByteArrayOutputStream()
        emojiBitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        val playlistPrivacySettings = booleanArrayOf(isPublic, isCollaborative, isWorldWide)
        Log.d(TAG, "Reacting - public: $isPublic, collaborative: $isCollaborative, worldWide: $isWorldWide")

        val intent = Intent(context, EmujiService::class.java)
        intent.action = ACTION_REACT_TO_TRACK
        intent.putExtra(EXTRA_EMOJI, emoji)
        intent.putExtra(EXTRA_TRACK_URI, trackUri)
        intent.putExtra(EXTRA_SPOTIFY_WEB_TOKEN, token)
        intent.putExtra(EXTRA_IS_PUBLIC, playlistPrivacySettings)
        intent.putExtra(EXTRA_EMOJI_BITMAP, byteArray)
        context.startService(intent)
    }

    private suspend fun handleReactToTrackIntent(intent: Intent) = withContext(Dispatchers.Default) {
        val emoji = intent.getStringExtra(EXTRA_EMOJI)
        val trackUri = intent.getStringExtra(EXTRA_TRACK_URI)
        val token = intent.getStringExtra(EXTRA_SPOTIFY_WEB_TOKEN)
        val playlistPrivacySettings = intent.getBooleanArrayExtra(EXTRA_IS_PUBLIC)

        if (playlistPrivacySettings == null || playlistPrivacySettings.size < 3) {
            Log.e(TAG, "Invalid playlist privacy settings")
            return@withContext
        }

        val isPublic = playlistPrivacySettings[0]
        val isCollaborative = playlistPrivacySettings[1]
        val isWorldWide = playlistPrivacySettings[2]

        // Convert byte array back into Bitmap
        val byteArray = intent.getByteArrayExtra(EXTRA_EMOJI_BITMAP)
        if (byteArray == null) {
            Log.e(TAG, "No emoji bitmap data found")
            return@withContext
        }

        val emojiBitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
        if (emojiBitmap == null) {
            Log.e(TAG, "Failed to decode emoji bitmap")
            return@withContext
        }

        Log.d(TAG, "Handling reaction - public: $isPublic, collaborative: $isCollaborative, worldWide: $isWorldWide")
        handleReactToTrack(token!!, emoji!!, emojiBitmap, isPublic, isCollaborative, isWorldWide, trackUri!!)
    }

    private suspend fun handleReactToTrack(
        webToken: String,
        emoji: String,
        cover: Bitmap,
        isPublic: Boolean,
        isCollaborative: Boolean,
        isWorldWide: Boolean,
        trackUri: String
    ) {
        sharedPreferences = getSharedPreferences("EmujiSettings", Context.MODE_PRIVATE)
        val createPersonalPlaylist = sharedPreferences.getBoolean("createPersonalPlaylist", true)
        val contributeGlobalPlaylist = sharedPreferences.getBoolean("contributeGlobalPlaylist", false)
        Log.d(TAG, "Reacting to track - Emoji: $emoji, Track URI: $trackUri")

        if (contributeGlobalPlaylist) {
            sendReactionToEmujiAPI(trackUri, emoji)
        } else {
            Log.d(TAG, "Global playlist contribution is disabled")
        }

        if (createPersonalPlaylist) {
            val emojiName = emojiAPI?.getEmojiSlugName(emoji)
            if (!emojiName.isNullOrBlank()) {
                Log.d(TAG, "Emoji Name: $emojiName, TrackUri: $trackUri")
                spotifyAPI?.updatePlaylistState(
                    webToken,
                    emojiName,
                    cover,
                    isPublic,
                    isCollaborative,
                    isWorldWide,
                    trackUri,
                    true
                )
            } else {
                Log.w(TAG, "Failed to fetch emoji name for: $emoji")
            }
        } else {
            Log.d(TAG, "Personal playlist creation is disabled")
        }
    }

    private suspend fun sendReactionToEmujiAPI(trackUri: String, emojiSlug: String) = withContext(Dispatchers.IO) {
        System.setProperty("java.net.preferIPv4Stack", "true")
        var conn: HttpURLConnection? = null

        try {
            val addresses = InetAddress.getAllByName("api.emuji.com")
            var ipv4Address: String? = null
            for (address in addresses) {
                if (address is Inet4Address) {
                    ipv4Address = address.hostAddress
                    Log.d(TAG, "Using IPv4 Address: $ipv4Address")
                    break
                }
            }

            if (ipv4Address == null) {
                Log.e(TAG, "No IPv4 address resolved for api.emuji.com")
                return@withContext
            }

            val url = URL("http", "api.emuji.com", 4444, "/react")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                doInput = true
            }

            val jsonParam = JSONObject().apply {
                put("trackUri", trackUri)
                put("emoji", emojiSlug)
            }

            conn.outputStream.use { outputStream ->
                val input = jsonParam.toString().toByteArray(Charsets.UTF_8)
                outputStream.write(input, 0, input.size)
                outputStream.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                Log.d(TAG, "Response from Emuji API: $response")
            } else {
                val errorResponse = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                if (errorResponse != null) {
                    Log.e(TAG, "Failed to send reaction. Response code: $responseCode, Error: $errorResponse")
                } else {
                    Log.e(TAG, "Failed to send reaction. Response code: $responseCode")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending reaction to Emuji API: ${e.message}", e)
        } finally {
            conn?.disconnect()
        }
    }

    companion object {
        private const val TAG = "EmujiService"
        private const val ACTION_REACT_TO_TRACK = "com.emuji.emuji.action.REACT_TO_TRACK"
        private const val EXTRA_EMOJI = "com.emuji.emuji.extra.EMOJI"
        private const val EXTRA_TRACK_URI = "com.emuji.emuji.extra.TRACK_URI"
        private const val EXTRA_SPOTIFY_WEB_TOKEN = "com.emuji.emuji.extra.SPOTIFY_WEB_TOKEN"
        private const val EXTRA_IS_PUBLIC = "com.emuji.emuji.extra.IS_PUBLIC"
        private const val ACTION_START_EMUJI = "com.emuji.emuji.action.START_EMUJI"
        private const val EXTRA_EMOJI_BITMAP = "com.emuji.emuji.extra.EMOJI_BITMAP"
    }
}
