package com.emuji.emuji

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

/**
 * Utility class for fetching emoji information from Emojipedia.
 * Uses Kotlin Coroutines for asynchronous operations.
 */
class EmojiUtil {
    /**
     * Callback interface for receiving emoji name results.
     */
    fun interface EmojiNameListener {
        fun onEmojiNameFetched(emojiName: String?)
    }

    /**
     * Fetches the slug name for an emoji from Emojipedia asynchronously.
     * This is a suspend function that should be called from a coroutine.
     *
     * @param emoji The emoji character to look up
     * @return The emoji name or null if fetch fails
     */
    suspend fun getEmojiSlugName(emoji: String?): String? = withContext(Dispatchers.IO) {
        if (emoji.isNullOrBlank()) {
            Log.e(TAG, "Invalid emoji provided")
            return@withContext null
        }

        val urlString = "https://emojipedia.org/$emoji/"
        fetchEmojiName(urlString)
    }

    /**
     * Legacy callback-based method for compatibility.
     * Consider migrating to suspend function where possible.
     */
    @Deprecated("Use suspend function getEmojiSlugName(emoji: String?) instead",
        ReplaceWith("kotlinx.coroutines.CoroutineScope.launch { getEmojiSlugName(emoji) }"))
    fun getEmojiSlugName(emoji: String?, listener: EmojiNameListener) {
        GlobalScope.launch(Dispatchers.Main) {
            val result = getEmojiSlugName(emoji)
            listener.onEmojiNameFetched(result)
        }
    }

    private suspend fun fetchEmojiName(urlString: String): String? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Failed to fetch emoji name. Response code: $responseCode")
                return@withContext null
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }

            // Try multiple regex patterns to handle different Emojipedia formats
            val patterns = listOf(
                "<title>\\s*([^<]+?)\\s+Emoji\\s*</title>",           // Standard format: "Name Emoji"
                "<title>\\s*🫁\\s+([^<]+?)\\s+Emoji\\s*</title>",    // With emoji: "🫁 Name Emoji"
                "<title>\\s*([^<]+?)\\s*\\|.*?</title>",              // With separator: "Name | Emojipedia"
                "<h1[^>]*>\\s*([^<]+?)\\s+Emoji\\s*</h1>",           // H1 tag format
                "<h1[^>]*>\\s*🫁\\s+([^<]+?)\\s*</h1>"               // H1 with emoji
            )

            for (regexPattern in patterns) {
                val pattern = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE)
                val matcher = pattern.matcher(response)

                if (matcher.find()) {
                    var emojiName = matcher.group(1)
                        ?.replace("\\s+".toRegex(), " ")
                        ?.replace("\\|.*".toRegex(), "")  // Remove everything after |
                        ?.replace("Emoji", "", ignoreCase = true)
                        ?.replace("^\\p{So}+".toRegex(), "")  // Remove emoji characters at start
                        ?.trim()

                    if (!emojiName.isNullOrBlank()) {
                        Log.d(TAG, "Fetched emoji name: $emojiName")
                        return@withContext emojiName
                    }
                }
            }

            // Log a sample of the response for debugging
            val titleSample = response.substringAfter("<title", "")
                .take(200)
                .substringBefore("</title>", "")
            Log.w(TAG, "Could not parse emoji name from response. Title sample: <title$titleSample")
            
            return@withContext null
        } catch (e: IOException) {
            Log.e(TAG, "Error fetching emoji name: ${e.message}", e)
            return@withContext null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * No longer needed with coroutines - kept for compatibility.
     */
    @Deprecated("No longer needed with coroutines")
    fun shutdown() {
        // No-op - coroutines handle cleanup automatically
    }

    companion object {
        private const val TAG = "EmojiUtil"
    }
}
