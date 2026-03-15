package com.emuji.emuji

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Utility class for interacting with Spotify Web API.
 * Handles playlist creation, modification, and track management using Coroutines.
 */
class SpotifyUtil {
    private val emujiUri = "31drfiwkyk7jftdlfccrnmylm5li"
    private val client = OkHttpClient()

    suspend fun updatePlaylistState(
        webToken: String?,
        playlistName: String?,
        cover: Bitmap,
        isPublic: Boolean,
        isCollaborative: Boolean,
        isWorldWide: Boolean,
        trackUri: String?,
        first: Boolean
    ) {
        if (webToken == null || playlistName == null || trackUri == null) {
            Log.e(TAG, "Cannot update playlist state: missing required parameters")
            return
        }

        Log.d(TAG, "Updating playlist state for: $playlistName")
        withContext(Dispatchers.IO) {
            fetchPlaylists(webToken, playlistName, cover, isPublic, isCollaborative, isWorldWide, trackUri, first, "me")
        }
    }

    private suspend fun fetchPlaylists(
        webToken: String,
        playlistTargetName: String,
        cover: Bitmap,
        isPublic: Boolean,
        isCollaborative: Boolean,
        isWorldWide: Boolean,
        trackUri: String,
        first: Boolean,
        userUri: String
    ): Unit = withContext(Dispatchers.IO) {
        try {
            var offset = 0
            val limit = 50
            var found = false
            var hasMorePlaylists = true

            while (hasMorePlaylists) {
                val url = URL("$API_BASE_URL/$userUri/playlists?limit=$limit&offset=$offset")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $webToken")
                }

                val responseCode = connection.responseCode
                when {
                    responseCode == HttpURLConnection.HTTP_OK -> {
                        val response = connection.inputStream.bufferedReader().use { it.readText() }
                        val responseJson = JSONObject(response)
                        val playlistsJson = responseJson.getJSONArray("items")
                        
                        Log.d(TAG, "Searching playlists (offset: $offset, found: ${playlistsJson.length()} playlists)")

                        for (i in 0 until playlistsJson.length()) {
                            val playlistJson = playlistsJson.getJSONObject(i)
                            val playlistName = playlistJson.getString("name")
                            val playlistSnapId = playlistJson.getString("snapshot_id")
                            val playlistUri = playlistJson.getString("uri")
                            Log.d(TAG, "Retrieved playlist: $playlistName (uri: $playlistUri)")
                            if (playlistName == playlistTargetName) {
                                found = true
                                Log.d(TAG, "✓ Found existing playlist: $playlistName")
                                fetchTrackAndAdd(webToken, playlistUri, playlistSnapId, trackUri)
                                return@withContext
                            }
                        }

                        // Check if there are more playlists to fetch
                        if (playlistsJson.length() < limit) {
                            // No more playlists available
                            hasMorePlaylists = false
                            Log.d(TAG, "Completed searching all playlists. Found: $found")
                        } else {
                            // Continue to next page
                            offset += limit
                            Log.d(TAG, "Fetching more playlists - new offset: $offset")
                            delay(500)
                        }
                    }
                    responseCode == 429 -> {
                        Log.e(TAG, "Rate limit exceeded. Fetching playlists. Waiting for 30 seconds before retrying...")
                        delay(30000)
                    }
                    else -> {
                        Log.e(TAG, "Error fetching playlists: HTTP $responseCode - ${connection.responseMessage}")

                        connection.errorStream?.bufferedReader()?.use { errorReader ->
                            val errorResponse = errorReader.readText()
                            Log.e(TAG, "Error detail: $errorResponse")
                        } ?: Log.e(TAG, "No detailed error message available from the server.")

                        Log.e(TAG, "URL causing error: $url")
                        Log.e(TAG, "Request params - Limit: $limit, Offset: $offset")
                        Log.e(TAG, "Aborting fetch due to error. Stopping further playlist fetch attempts.")

                        hasMorePlaylists = false
                        return@withContext
                    }
                }

                connection.disconnect()
            }

            // Playlist was not found after searching all pages
            if (!found) {
                if (userUri == "me") {
                    Log.d(TAG, "Playlist '$playlistTargetName' not found. Creating new playlist...")
                    createPlaylist(webToken, playlistTargetName, cover, isPublic, isCollaborative, trackUri)
                } else {
                    Log.d(TAG, "Playlist '$playlistTargetName' not found in $userUri playlists")
                }
            }
            
            // Call the updatePlaylistState function here for world-wide playlists
            if (first && userUri == "me" && isWorldWide) {
                Log.d(TAG, "Checking world-wide Emuji playlists...")
                fetchPlaylists(webToken, playlistTargetName, cover, isPublic, isCollaborative, isWorldWide, trackUri, false, "users/$emujiUri")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to fetch playlists: ${e.message}")
        } catch (e: JSONException) {
            Log.e(TAG, "Failed to fetch playlists: ${e.message}")
        }
    }

    /**
     * Trims transparent pixels from a bitmap and centers it in a square.
     * Used for creating consistent playlist cover images.
     *
     * @param bmp The bitmap to trim
     * @return A square bitmap with the content centered
     */
    private fun trimBitmap(bmp: Bitmap): Bitmap {
        val imgHeight = bmp.height
        val imgWidth = bmp.width

        // Find bounding box of non-transparent pixels
        var smallX = imgWidth
        var smallY = imgHeight
        var largeX = 0
        var largeY = 0

        for (y in 0 until imgHeight) {
            for (x in 0 until imgWidth) {
                if (Color.alpha(bmp.getPixel(x, y)) > 0) {
                    if (x < smallX) smallX = x
                    if (y < smallY) smallY = y
                    if (x > largeX) largeX = x
                    if (y > largeY) largeY = y
                }
            }
        }

        // If the bitmap is entirely transparent, return the original
        if (smallX >= largeX || smallY >= largeY) return bmp

        val boundingBoxWidth = largeX - smallX + 1
        val boundingBoxHeight = largeY - smallY + 1
        val squareSize = maxOf(boundingBoxWidth, boundingBoxHeight)

        // Create a square bitmap and center the content
        val squareBitmap = Bitmap.createBitmap(squareSize, squareSize, bmp.config)
        val drawX = (squareSize - boundingBoxWidth) / 2
        val drawY = (squareSize - boundingBoxHeight) / 2

        val canvas = Canvas(squareBitmap)
        canvas.drawBitmap(bmp, (-smallX + drawX).toFloat(), (-smallY + drawY).toFloat(), null)

        return squareBitmap
    }

    suspend fun createPlaylist(
        webToken: String?,
        playlistName: String?,
        cover: Bitmap,
        isPublic: Boolean,
        isCollaborative: Boolean,
        trackUri: String?
    ) = withContext(Dispatchers.IO) {
        if (webToken == null || playlistName == null || trackUri == null) {
            Log.e(TAG, "Cannot create playlist: missing required parameters")
            return@withContext
        }

        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i(TAG, "📝 CREATING NEW PLAYLIST: $playlistName")
        Log.i(TAG, "   Public: $isPublic | Collaborative: $isCollaborative")
        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        try {
            // Convert bitmap to base64
            val trimmedCover = trimBitmap(cover)
            val base64Cover = ByteArrayOutputStream().use { stream ->
                trimmedCover.compress(Bitmap.CompressFormat.PNG, 90, stream)
                Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP).trim().replace("\n", "")
            }

            val bodyContent: String = if (isCollaborative) {
                if (isPublic) {
                    Log.w(TAG, "Collaborative playlists can only be private.")
                }
                "{\n\"name\":\"$playlistName\",\n\"public\":false,\n\"collaborative\":true\n}"
            } else {
                "{\n\"name\":\"$playlistName\",\n\"public\":$isPublic,\n\"collaborative\":false\n}"
            }

            val mediaType = "application/json".toMediaType()
            val body = bodyContent.toRequestBody(mediaType)
            Log.e(TAG, "Creating Playlist: $bodyContent")
            val request = Request.Builder()
                .url("$API_BASE_URL/me/playlists")
                .post(body)
                .addHeader("Authorization", "Bearer $webToken")
                .addHeader("Content-Type", "application/json")
                .build()

            var response: Response? = null
            do {
                if (response?.code == 429) {
                    Log.e(TAG, "Rate limit exceeded. Waiting for 60 seconds before retrying...")
                    delay(60000)
                }

                response = client.newCall(request).execute()
            } while (response?.code == 429)

            // Extract the playlist ID from the response
            val responseBody = response?.body?.string()
            val jsonResponse = JSONObject(responseBody!!)
            Log.w(TAG, jsonResponse.toString())
            val playlistUri = jsonResponse.getString("uri")
            val playlistId = playlistUri.split(":")[2]

            Log.i(TAG, "✅ Successfully created playlist: $playlistName (ID: $playlistId)")
            fetchTrackAndAdd(webToken, playlistUri, "", trackUri)

            // Upload cover image
            val imageMediaType = "image/png".toMediaType()
            val coverBody = base64Cover.toRequestBody(imageMediaType)
            val coverRequest = Request.Builder()
                .url("$API_BASE_URL/playlists/$playlistId/images")
                .put(coverBody)
                .addHeader("Authorization", "Bearer $webToken")
                .addHeader("Content-Type", "image/png")
                .build()

            client.newCall(coverRequest).execute().use { coverResponse ->
                if (!coverResponse.isSuccessful) {
                    Log.e(TAG, "Failed to upload playlist cover. Response: ${coverResponse.body?.string()}")
                } else {
                    Log.d(TAG, "Uploaded playlist cover.")
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to create playlist: ${e.message}")
        } catch (e: JSONException) {
            Log.e(TAG, "Failed to parse JSON response: ${e.message}")
        }
    }

    suspend fun fetchTrackAndAdd(webToken: String?, playlistUri: String?, snapshotId: String?, trackUri: String?) = withContext(Dispatchers.IO) {
        if (webToken == null || playlistUri == null || trackUri == null) {
            Log.e(TAG, "Cannot fetch track and add: missing required parameters")
            return@withContext
        }

        try {
            val parts = playlistUri.split(":")
            if (parts.size < 3) {
                Log.e(TAG, "Invalid playlist URI format: $playlistUri")
                return@withContext
            }
            val playlistId = parts[2]

            // Retrieve the playlist tracks
            val getTracksRequest = Request.Builder()
                .url("$API_BASE_URL/playlists/$playlistId/tracks")
                .get()
                .addHeader("Authorization", "Bearer $webToken")
                .build()

            client.newCall(getTracksRequest).execute().use { getTracksResponse ->
                if (getTracksResponse.isSuccessful) {
                    val responseString = getTracksResponse.body?.string()
                    val responseObject = JSONObject(responseString!!)
                    val tracksArray = responseObject.getJSONArray("items")

                    var position = -1

                    // Find the track in the playlist and retrieve its position
                    for (i in 0 until tracksArray.length()) {
                        val trackObject = tracksArray.getJSONObject(i)
                        val track = trackObject.getJSONObject("track")
                        val uri = track.getString("uri")
                        if (uri == trackUri) {
                            position = i
                            break
                        }
                    }

                    // Remove the track if it's in the playlist
                    if (position != -1) {
                        // Decrease the position by one if it's not at position 0
                        if (position > 0) {
                            position--
                        }

                        val requestBody = JSONObject().apply {
                            put("tracks", JSONArray().apply {
                                put(JSONObject().put("uri", trackUri))
                            })
                            put("snapshot_id", snapshotId)
                        }

                        val mediaType = "application/json".toMediaType()
                        val body = requestBody.toString().toRequestBody(mediaType)

                        val removeTrackRequest = Request.Builder()
                            .url("$API_BASE_URL/playlists/$playlistId/tracks")
                            .delete(body)
                            .addHeader("Authorization", "Bearer $webToken")
                            .addHeader("Content-Type", "application/json")
                            .build()

                        client.newCall(removeTrackRequest).execute().use { removeTrackResponse ->
                            if (!removeTrackResponse.isSuccessful) {
                                val removeTrackResponseBody = removeTrackResponse.body?.string()
                                Log.e(TAG, "Failed to remove track from playlist: $removeTrackResponseBody")
                                return@withContext
                            }
                        }
                    } else {
                        position = 0
                    }

                    // Add the track to the playlist at the updated position
                    val mediaType = "application/json".toMediaType()
                    val body = "{\n\"uris\":[\"$trackUri\"],\n\"position\":$position\n}".toRequestBody(mediaType)
                    val addTrackRequest = Request.Builder()
                        .url("$API_BASE_URL/playlists/$playlistId/tracks")
                        .post(body)
                        .addHeader("Authorization", "Bearer $webToken")
                        .addHeader("Content-Type", "application/json")
                        .build()

                    client.newCall(addTrackRequest).execute().use { addTrackResponse ->
                        if (addTrackResponse.isSuccessful) {
                            Log.d(TAG, "Added track to playlist: $playlistId")
                        } else {
                            val addTrackResponseBody = addTrackResponse.body?.string()
                            Log.e(TAG, "Failed to add track to playlist: $addTrackResponseBody")
                        }
                    }
                } else {
                    throw IOException("Failed to retrieve playlist tracks: status code = ${getTracksResponse.code}")
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to add track to playlist: ${e.message}")
        } catch (e: JSONException) {
            Log.e(TAG, "Failed to parse playlist tracks response: ${e.message}")
        }
    }

    companion object {
        private const val API_BASE_URL = "https://api.spotify.com/v1"
        private const val TAG = "SpotifyUtil"
    }
}
