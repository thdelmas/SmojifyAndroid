package com.emuji.emuji

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import com.emuji.emuji.databinding.CurrentSongBinding
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.types.Track
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import java.util.concurrent.TimeUnit

/**
 * Activity that manages the music player interface with Spotify integration.
 * Allows users to view current playing track and react with emojis.
 * Uses ViewModel for state management and ViewBinding for view access.
 * Enhanced with Material Design animations and haptic feedback.
 */
class PlayerActivity : AppCompatActivity() {
    private lateinit var binding: CurrentSongBinding
    private val viewModel: PlayerViewModel by viewModels()
    private lateinit var vibrator: Vibrator
    
    private var mSpotifyAppRemote: SpotifyAppRemote? = null
    private var emojiManager: EmojiUtil? = null
    private var emujiService: EmujiService? = null
    private var spotifyWebToken: String? = null
    
    // Progress tracking
    private val progressHandler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null
    
    // Emoji keyboard
    private lateinit var emojiAdapter: EmojiAdapter
    private var allEmojis: List<String> = emptyList()
    private var recentEmojis: MutableList<String> = mutableListOf()
    private lateinit var emojiPrefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = CurrentSongBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize services
        emojiManager = EmojiUtil()
        emujiService = EmujiService()
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        emojiPrefs = getSharedPreferences("emoji_prefs", Context.MODE_PRIVATE)

        // Load settings from SharedPreferences
        val sharedPreferences = getSharedPreferences("EmujiSettings", Context.MODE_PRIVATE)
        viewModel.loadSettings(sharedPreferences)

        // Initialize emoji keyboard
        loadRecentEmojis()
        allEmojis = getEmojis()
        setupEmojiKeyboard()

        // Show loading state initially
        showLoadingState()

        // Observe ViewModel state
        observeViewModel()

        // Check if we already have a token, otherwise start auth flow
        if (spotifyWebToken != null) {
            connectToSpotifyAppRemote(spotifyWebToken)
        } else {
            initiateSpotifyAuthentication()
        }
    }

    /**
     * Observes ViewModel LiveData for state changes
     */
    private fun observeViewModel() {
        viewModel.playerState.observe(this, Observer { state ->
            when (state) {
                is PlayerState.Playing -> updatePlayerUI(state.track, false)
                is PlayerState.Paused -> updatePlayerUI(state.track, true)
                is PlayerState.Error -> {
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "Player error: ${state.message}", state.throwable)
                }
                else -> { /* Handle other states if needed */ }
            }
        })

        viewModel.authState.observe(this, Observer { state ->
            when (state) {
                is AuthState.Authenticated -> {
                    spotifyWebToken = state.token
                    emujiService?.startService(this)
                    // Now that we have the token, connect to Spotify App Remote
                    connectToSpotifyAppRemote(state.token)
                }
                is AuthState.Error -> {
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
                else -> { /* Handle other states if needed */ }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // Reload settings when activity resumes in case they were changed
        val sharedPreferences = getSharedPreferences("EmujiSettings", Context.MODE_PRIVATE)
        viewModel.loadSettings(sharedPreferences)
        // Only reconnect if we have a valid token and aren't already connected
        if (spotifyWebToken != null && mSpotifyAppRemote == null) {
            connectToSpotifyAppRemote(spotifyWebToken)
        }
    }

    /**
     * Called when successfully connected to Spotify App Remote.
     * Sets up UI components and subscribes to player state updates.
     */
    protected fun connected() {
        // Subscribe to PlayerState
        mSpotifyAppRemote?.playerApi
            ?.subscribeToPlayerState()
            ?.setEventCallback { playerState ->
                val track = playerState.track
                if (track != null) {
                    viewModel.updateCurrentTrackUri(track.uri)
                    
                    // Update progress
                    updateProgressBar(playerState.playbackPosition, track.duration)
                    
                    if (playerState.isPaused) {
                        viewModel.updatePlayerState(PlayerState.Paused(track))
                        stopProgressUpdate()
                    } else {
                        viewModel.updatePlayerState(PlayerState.Playing(track))
                        startProgressUpdate()
                    }
                }
            }

        setupClickListeners()
        setupEmojiInput()
        setupNavigationButtons()
    }
    
    /**
     * Starts periodic progress bar updates
     */
    private fun startProgressUpdate() {
        stopProgressUpdate() // Clear any existing updates
        
        progressRunnable = object : Runnable {
            override fun run() {
                mSpotifyAppRemote?.playerApi?.playerState?.setResultCallback { playerState ->
                    val track = playerState.track
                    if (track != null && !playerState.isPaused) {
                        updateProgressBar(playerState.playbackPosition, track.duration)
                        progressHandler.postDelayed(this, 1000) // Update every second
                    }
                }
            }
        }
        progressHandler.post(progressRunnable!!)
    }
    
    /**
     * Stops progress bar updates
     */
    private fun stopProgressUpdate() {
        progressRunnable?.let { progressHandler.removeCallbacks(it) }
    }
    
    /**
     * Updates the progress bar with current playback position
     */
    private fun updateProgressBar(position: Long, duration: Long) {
        if (duration > 0) {
            val progress = ((position.toFloat() / duration.toFloat()) * 100).toInt()
            binding.playbackProgress.setProgressCompat(progress, true)
            
            // Update time labels
            binding.currentTimeText.text = formatTime(position)
            binding.totalTimeText.text = formatTime(duration)
        }
    }
    
    /**
     * Formats milliseconds to MM:SS format
     */
    private fun formatTime(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(minutes)
        return String.format("%d:%02d", minutes, seconds)
    }

    /**
     * Sets up click listeners for UI controls with haptic feedback
     */
    private fun setupClickListeners() {
        binding.pauseButton.setOnClickListener { 
            performHapticFeedback()
            animateFabPress(it) { 
                mSpotifyAppRemote?.playerApi?.pause() 
            }
        }
        
        binding.playButton.setOnClickListener { 
            performHapticFeedback()
            animateFabPress(it) { 
                mSpotifyAppRemote?.playerApi?.resume() 
            }
        }
        
        binding.nextButton.setOnClickListener { 
            performHapticFeedback()
            animateFabPress(it) { 
                mSpotifyAppRemote?.playerApi?.skipNext() 
            }
        }
        
        binding.previousButton.setOnClickListener { 
            performHapticFeedback()
            animateFabPress(it) { 
                mSpotifyAppRemote?.playerApi?.skipPrevious() 
            }
        }
    }
    
    /**
     * Sets up navigation buttons for accessing playlists and settings
     */
    private fun setupNavigationButtons() {
        // Settings button
        binding.settingsButton.setOnClickListener {
            performHapticFeedback()
            animateCardPress(it) {
                navigateToActivity(SettingsActivity::class.java)
            }
        }
        
        // Playlists button - shows a bottom sheet with playlist options
        binding.playlistsButton.setOnClickListener {
            performHapticFeedback()
            animateCardPress(it) {
                showPlaylistsMenu()
            }
        }
    }
    
    /**
     * Shows a bottom sheet with playlist navigation options
     */
    private fun showPlaylistsMenu() {
        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_playlists, null)
        
        sheetView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.btnUserMoodSheet)?.setOnClickListener {
            performHapticFeedback()
            bottomSheet.dismiss()
            navigateToActivity(UserPlaylistsActivity::class.java)
        }
        
        sheetView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.btnWorldMoodSheet)?.setOnClickListener {
            performHapticFeedback()
            bottomSheet.dismiss()
            navigateToActivity(WorldPlaylistsActivity::class.java)
        }
        
        bottomSheet.setContentView(sheetView)
        bottomSheet.show()
    }
    
    /**
     * Animates card press with scale effect
     */
    private fun animateCardPress(view: View, onComplete: () -> Unit) {
        view.animate()
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .withEndAction(onComplete)
                    .start()
            }
            .start()
    }
    
    /**
     * Navigates to activity with custom transition animation
     */
    private fun navigateToActivity(activityClass: Class<*>) {
        startActivity(Intent(this, activityClass))
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    /**
     * Handles the selected emoji from the keyboard
     */
    private fun handleEmojiInput(emoji: String) {
        Log.d(TAG, "User Reaction: $emoji")
        addToRecent(emoji)
        
        // Create a temporary view for bitmap generation
        val emojiView = android.widget.TextView(this).apply {
            text = emoji
            textSize = 72f
            measure(
                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY)
            )
            layout(0, 0, 200, 200)
        }

        val emojiBitmap = getBitmapFromView(emojiView)
        if (emojiBitmap == null) {
            Log.w(TAG, "Failed to create emoji bitmap")
            return
        }

        viewModel.reactToTrack(
            emujiService,
            applicationContext,
            spotifyWebToken,
            emoji,
            emojiBitmap,
            viewModel.currentTrackUri.value
        )
        
        Toast.makeText(this, "Reacted with $emoji", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Sets up the embedded emoji keyboard
     */
    private fun setupEmojiKeyboard() {
        // Setup emoji grid
        emojiAdapter = EmojiAdapter(allEmojis) { emoji ->
            performHapticFeedback()
            handleEmojiInput(emoji)
        }

        binding.emojiRecyclerView.apply {
            layoutManager = GridLayoutManager(this@PlayerActivity, 7)
            adapter = emojiAdapter
            itemAnimator?.apply {
                addDuration = 150
                removeDuration = 150
            }
        }
        
        // Setup search bar
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterEmojis(s.toString())
            }
        })
        
        // Setup category filters
        binding.chipRecent.setOnClickListener { 
            performHapticFeedback()
            showCategory("recent") 
        }
        binding.chipSmileys.setOnClickListener { 
            performHapticFeedback()
            showCategory("smileys") 
        }
        binding.chipMusic.setOnClickListener { 
            performHapticFeedback()
            showCategory("music") 
        }
        binding.chipHearts.setOnClickListener { 
            performHapticFeedback()
            showCategory("hearts") 
        }
        binding.chipAll.setOnClickListener { 
            performHapticFeedback()
            showCategory("all") 
        }
    }
    
    /**
     * Loads recently used emojis from SharedPreferences
     */
    private fun loadRecentEmojis() {
        val recentString = emojiPrefs.getString("recent_emojis", "") ?: ""
        recentEmojis = if (recentString.isNotEmpty()) {
            recentString.split(",").toMutableList()
        } else {
            mutableListOf()
        }
    }
    
    /**
     * Saves recently used emojis to SharedPreferences
     */
    private fun saveRecentEmojis() {
        emojiPrefs.edit().putString("recent_emojis", recentEmojis.joinToString(",")).apply()
    }
    
    /**
     * Adds an emoji to recent list
     */
    private fun addToRecent(emoji: String) {
        recentEmojis.remove(emoji)
        recentEmojis.add(0, emoji)
        if (recentEmojis.size > 30) {
            recentEmojis = recentEmojis.take(30).toMutableList()
        }
        saveRecentEmojis()
    }
    
    /**
     * Filters emojis based on search query
     */
    private fun filterEmojis(query: String) {
        val filtered = if (query.isEmpty()) {
            allEmojis
        } else {
            allEmojis
        }
        updateEmojiList(filtered)
    }
    
    /**
     * Shows emojis for a specific category
     */
    private fun showCategory(category: String) {
        binding.searchEditText.text?.clear()
        
        val filtered = when (category) {
            "recent" -> {
                if (recentEmojis.isEmpty()) {
                    binding.emptyStateText.visibility = View.VISIBLE
                    binding.emojiRecyclerView.visibility = View.GONE
                    binding.emptyStateText.text = "No recent emojis yet"
                    return
                }
                recentEmojis
            }
            "smileys" -> getSmileyEmojis()
            "music" -> getMusicEmojis()
            "hearts" -> getHeartEmojis()
            else -> allEmojis
        }
        
        updateEmojiList(filtered)
    }
    
    /**
     * Updates the emoji list in the adapter
     */
    private fun updateEmojiList(emojis: List<String>) {
        if (emojis.isEmpty()) {
            binding.emptyStateText.visibility = View.VISIBLE
            binding.emojiRecyclerView.visibility = View.GONE
        } else {
            binding.emptyStateText.visibility = View.GONE
            binding.emojiRecyclerView.visibility = View.VISIBLE
            emojiAdapter.updateEmojis(emojis)
        }
    }
    
    /**
     * Sets up emoji input - deprecated, kept for compatibility
     */
    private fun setupEmojiInput() {
        // No longer needed with embedded keyboard
    }


    /**
     * Updates the UI with current track information from Spotify.
     * Enhanced with smooth animations and transitions.
     */
    private fun updatePlayerUI(track: Track, isPaused: Boolean) {
        Log.i(TAG, "Update Player view")

        // Hide loading state
        hideLoadingState()
        
        // Animate text changes with cross-fade
        track.name?.let { newTitle ->
            if (binding.songTitleTextView.text != newTitle) {
                binding.songTitleTextView.animate()
                    .alpha(0f)
                    .setDuration(150)
                    .withEndAction {
                        binding.songTitleTextView.text = newTitle
                        binding.songTitleTextView.animate()
                            .alpha(1f)
                            .setDuration(150)
                            .start()
                    }
                    .start()
            }
        }
        
        track.artist?.name?.let { newArtist ->
            if (binding.artistTextView.text != newArtist) {
                binding.artistTextView.animate()
                    .alpha(0f)
                    .setDuration(150)
                    .withEndAction {
                        binding.artistTextView.text = newArtist
                        binding.artistTextView.animate()
                            .alpha(1f)
                            .setDuration(150)
                            .start()
                    }
                    .start()
            }
        }

        // Smooth play/pause button transition with scale animation
        if (isPaused) {
            if (binding.pauseButton.visibility == View.VISIBLE) {
                binding.pauseButton.animate()
                    .alpha(0f)
                    .scaleX(0.8f)
                    .scaleY(0.8f)
                    .setDuration(150)
                    .withEndAction {
                        binding.pauseButton.visibility = View.GONE
                        binding.playButton.visibility = View.VISIBLE
                        binding.playButton.alpha = 0f
                        binding.playButton.scaleX = 0.8f
                        binding.playButton.scaleY = 0.8f
                        binding.playButton.animate()
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(200)
                            .setInterpolator(android.view.animation.OvershootInterpolator())
                            .start()
                    }
                    .start()
            } else {
                binding.pauseButton.visibility = View.GONE
                binding.playButton.visibility = View.VISIBLE
            }
        } else {
            if (binding.playButton.visibility == View.VISIBLE) {
                binding.playButton.animate()
                    .alpha(0f)
                    .scaleX(0.8f)
                    .scaleY(0.8f)
                    .setDuration(150)
                    .withEndAction {
                        binding.playButton.visibility = View.GONE
                        binding.pauseButton.visibility = View.VISIBLE
                        binding.pauseButton.alpha = 0f
                        binding.pauseButton.scaleX = 0.8f
                        binding.pauseButton.scaleY = 0.8f
                        binding.pauseButton.animate()
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(200)
                            .setInterpolator(android.view.animation.OvershootInterpolator())
                            .start()
                    }
                    .start()
            } else {
                binding.playButton.visibility = View.GONE
                binding.pauseButton.visibility = View.VISIBLE
            }
        }

        // Load and animate album art with fade transition
        if (mSpotifyAppRemote != null && track.imageUri != null) {
            // Fade out current image
            binding.albumCoverImageView.animate()
                .alpha(0.5f)
                .setDuration(200)
                .withEndAction {
                    mSpotifyAppRemote?.imagesApi?.getImage(track.imageUri)
                        ?.setResultCallback { bitmap ->
                            bitmap?.let { 
                                binding.albumCoverImageView.setImageBitmap(it)
                                // Fade in new album art with scale
                                binding.albumCoverImageView.animate()
                                    .alpha(1f)
                                    .setDuration(300)
                                    .start()
                                
                                // Subtle scale animation on album art card
                                binding.albumArtCard.animate()
                                    .scaleX(0.95f)
                                    .scaleY(0.95f)
                                    .setDuration(100)
                                    .withEndAction {
                                        binding.albumArtCard.animate()
                                            .scaleX(1f)
                                            .scaleY(1f)
                                            .setDuration(200)
                                            .setInterpolator(android.view.animation.OvershootInterpolator())
                                            .start()
                                    }
                                    .start()
                            }
                        }
                }
                .start()
        }
    }
    
    /**
     * Shows loading state for player UI with animation
     */
    private fun showLoadingState() {
        binding.songTitleTextView.text = "Loading..."
        binding.artistTextView.text = "Please wait"
        binding.albumCoverImageView.animate()
            .alpha(0.5f)
            .setDuration(200)
            .start()
        binding.loadingProgress.visibility = View.VISIBLE
        binding.progressContainer.visibility = View.GONE
        
        // Animate loading indicator
        binding.loadingProgress.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
    }
    
    /**
     * Hides loading state and shows content with animation
     */
    private fun hideLoadingState() {
        binding.albumCoverImageView.animate()
            .alpha(1.0f)
            .setDuration(300)
            .start()
        
        binding.loadingProgress.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                binding.loadingProgress.visibility = View.GONE
            }
            .start()
        
        // Show progress container
        binding.progressContainer.visibility = View.VISIBLE
        binding.progressContainer.alpha = 0f
        binding.progressContainer.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
    }
    
    /**
     * Animates FAB button press with scale effect
     */
    private fun animateFabPress(view: View, onComplete: () -> Unit) {
        view.animate()
            .scaleX(0.85f)
            .scaleY(0.85f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .withEndAction(onComplete)
                    .start()
            }
            .start()
    }
    
    /**
     * Animates button press with scale effect
     */
    private fun animateButtonPress(view: View, onComplete: () -> Unit) {
        view.animate()
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(80)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(80)
                    .withEndAction(onComplete)
                    .start()
            }
            .start()
    }
    
    /**
     * Provides haptic feedback for interactions
     */
    private fun performHapticFeedback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(10)
        }
    }
    
    /**
     * Returns smiley and emotion emojis
     */
    private fun getSmileyEmojis(): List<String> {
        return listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂",
            "🙂", "🙃", "😉", "😊", "😇", "🥰", "😍", "🤩",
            "😘", "😗", "😚", "😙", "🥲", "😋", "😛", "😜",
            "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔", "🤐",
            "🤨", "😐", "😑", "😶", "😏", "😒", "🙄", "😬",
            "😌", "😔", "😪", "🤤", "😴", "😎", "🤓", "🧐",
            "😕", "😟", "🙁", "☹️", "😮", "😯", "😲", "😳",
            "🥺", "😦", "😧", "😨", "😰", "😥", "😢", "😭",
            "😱", "😖", "😣", "😞", "😓", "😩", "😫", "🥱",
            "😤", "😡", "😠", "🤬", "😈", "👿", "💀", "☠️",
            "🤠", "🥳", "🥸", "🥵", "🥶", "😶‍🌫️", "😵", "😵‍💫", "🤯"
        )
    }
    
    /**
     * Returns music and entertainment emojis
     */
    private fun getMusicEmojis(): List<String> {
        return listOf(
            "🎵", "🎶", "🎼", "🎹", "🥁", "🎷", "🎺", "🎸",
            "🪕", "🎻", "🎤", "🎧", "📻", "🎬", "🎭", "🎪",
            "🎨", "🎰", "🎲", "🎯", "🎳", "🎮", "🕹️", "🎴",
            "🎺", "🪘", "🎙️", "🎚️", "🎛️", "📀", "💿", "📱"
        )
    }
    
    /**
     * Returns heart emojis
     */
    private fun getHeartEmojis(): List<String> {
        return listOf(
            "💋", "💌", "💘", "💝", "💖", "💗", "💓", "💞",
            "💕", "💟", "❣️", "💔", "❤️", "🧡", "💛", "💚",
            "💙", "💜", "🤎", "🖤", "🤍", "💯", "💢", "💥",
            "❤️‍🔥", "❤️‍🩹", "💑", "💏", "👩‍❤️‍👨", "👨‍❤️‍👨", "👩‍❤️‍👩"
        )
    }
    
    /**
     * Returns a comprehensive list of commonly used emojis organized by category
     */
    private fun getEmojis(): List<String> {
        return listOf(
            // Smileys & Emotion
            "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂",
            "🙂", "🙃", "😉", "😊", "😇", "🥰", "😍", "🤩",
            "😘", "😗", "😚", "😙", "🥲", "😋", "😛", "😜",
            "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔", "🤐",
            "🤨", "😐", "😑", "😶", "😏", "😒", "🙄", "😬",
            "🤥", "😌", "😔", "😪", "🤤", "😴", "😷", "🤒",
            "🤕", "🤢", "🤮", "🤧", "🥵", "🥶", "😶‍🌫️", "😵",
            "😵‍💫", "🤯", "🤠", "🥳", "🥸", "😎", "🤓", "🧐",
            "😕", "😟", "🙁", "☹️", "😮", "😯", "😲", "😳",
            "🥺", "😦", "😧", "😨", "😰", "😥", "😢", "😭",
            "😱", "😖", "😣", "😞", "😓", "😩", "😫", "🥱",
            "😤", "😡", "😠", "🤬", "😈", "👿", "💀", "☠️",
            
            // Hearts & Love
            "💋", "💌", "💘", "💝", "💖", "💗", "💓", "💞",
            "💕", "💟", "❣️", "💔", "❤️", "🧡", "💛", "💚",
            "💙", "💜", "🤎", "🖤", "🤍", "💯", "💢", "💥",
            
            // Gestures & Hands
            "👍", "👎", "👊", "✊", "🤛", "🤜", "🤞", "✌️",
            "🤟", "🤘", "👌", "🤌", "🤏", "👈", "👉", "👆",
            "👇", "☝️", "👋", "🤚", "🖐️", "✋", "🖖", "👏",
            "🙌", "👐", "🤲", "🤝", "🙏", "✍️", "💅", "🤳",
            
            // Body Parts & People
            "💪", "🦾", "🦿", "🦵", "🦶", "👂", "🦻", "👃",
            "🧠", "🫀", "🫁", "🦷", "🦴", "👀", "👁️", "👅",
            "👄", "👶", "🧒", "👦", "👧", "🧑", "👱", "👨",
            "🧔", "👨‍🦰", "👨‍🦱", "👨‍🦳", "👨‍🦲", "👩", "👩‍🦰", "🧑‍🦰",
            
            // Activities & Sports
            "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉",
            "🥏", "🎱", "🏓", "🏸", "🏒", "🏑", "🥍", "🏏",
            "🥅", "⛳", "🏹", "🎣", "🤿", "🥊", "🥋", "🎽",
            "⛸️", "🥌", "🛹", "🛼", "🏆", "🥇", "🥈", "🥉",
            
            // Music & Entertainment
            "🎵", "🎶", "🎼", "🎹", "🥁", "🎷", "🎺", "🎸",
            "🪕", "🎻", "🎤", "🎧", "📻", "🎬", "🎭", "🎪",
            "🎨", "🎰", "🎲", "🎯", "🎳", "🎮", "🕹️", "🎴",
            
            // Food & Drink
            "🍕", "🍔", "🍟", "🌭", "🍿", "🧂", "🥓", "🥚",
            "🍳", "🧇", "🥞", "🧈", "🍞", "🥐", "🥨", "🥯",
            "🥖", "🧀", "🥗", "🥙", "🥪", "🌮", "🌯", "🫔",
            "🥫", "🍝", "🍜", "🍲", "🍛", "🍣", "🍱", "🥟",
            "🦪", "🍤", "🍙", "🍚", "🍘", "🍥", "🥠", "🥮",
            "🍢", "🍡", "🍧", "🍨", "🍦", "🥧", "🧁", "🍰",
            "🎂", "🍮", "🍭", "🍬", "🍫", "🍿", "🍩", "🍪",
            "🌰", "🥜", "🍯", "🥛", "🍼", "☕", "🍵", "🧃",
            "🥤", "🍶", "🍺", "🍻", "🥂", "🍷", "🥃", "🍸",
            "🍹", "🧉", "🍾", "🧊", "🥄", "🍴", "🍽️", "🥢",
            
            // Animals & Nature
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼",
            "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🐔",
            "🐧", "🐦", "🐤", "🐣", "🐥", "🦆", "🦅", "🦉",
            "🦇", "🐺", "🐗", "🐴", "🦄", "🐝", "🪱", "🐛",
            "🦋", "🐌", "🐞", "🐜", "🪰", "🪲", "🪳", "🦟",
            "🦗", "🕷️", "🕸️", "🦂", "🐢", "🐍", "🦎", "🦖",
            "🦕", "🐙", "🦑", "🦐", "🦞", "🦀", "🐡", "🐠",
            "🐟", "🐬", "🐳", "🐋", "🦈", "🐊", "🐅", "🐆",
            
            // Nature & Weather
            "🌸", "💐", "🌹", "🥀", "🌺", "🌻", "🌼", "🌷",
            "🌱", "🪴", "🌲", "🌳", "🌴", "🌵", "🌾", "🌿",
            "☘️", "🍀", "🍁", "🍂", "🍃", "🌍", "🌎", "🌏",
            "🌕", "🌖", "🌗", "🌘", "🌑", "🌒", "🌓", "🌔",
            "🌙", "🌛", "🌜", "☀️", "🌝", "🌞", "⭐", "🌟",
            "💫", "✨", "☄️", "🌠", "🌌", "☁️", "⛅", "⛈️",
            "🌤️", "🌥️", "🌦️", "🌧️", "🌨️", "🌩️", "🌪️", "🌫️",
            "🌬️", "🌀", "🌈", "⚡", "❄️", "☃️", "⛄", "☔",
            
            // Travel & Places
            "🚗", "🚕", "🚙", "🚌", "🚎", "🏎️", "🚓", "🚑",
            "🚒", "🚐", "🛻", "🚚", "🚛", "🚜", "🏍️", "🛵",
            "🚲", "🛴", "✈️", "🛫", "🛬", "🚀", "🛸", "🚁",
            "⛵", "🚤", "🛳️", "⛴️", "🚢", "⚓", "🎢", "🎡",
            "🎠", "🏗️", "🗼", "🗽", "⛲", "⛱️", "🏖️", "🏝️",
            
            // Objects & Symbols
            "💻", "⌨️", "🖥️", "🖨️", "🖱️", "🖲️", "💾", "💿",
            "📱", "📞", "☎️", "📟", "📠", "📺", "📻", "🎙️",
            "🎚️", "🎛️", "🧭", "⏱️", "⏰", "⏲️", "⌚", "📡",
            "🔋", "🔌", "💡", "🔦", "🕯️", "🪔", "🧯", "🛢️",
            "💸", "💵", "💴", "💶", "💷", "🪙", "💰", "💳",
            "🎁", "🎀", "🎊", "🎉", "🎈", "🎏", "🎎", "🎐",
            
            // Symbols
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍",
            "🤎", "❤️‍🔥", "❤️‍🩹", "💔", "❣️", "💕", "💞", "💓",
            "💗", "💖", "💘", "💝", "✔️", "✅", "❌", "❎",
            "➕", "➖", "✖️", "➗", "💯", "🔥", "⚡", "💥",
            "🔴", "🟠", "🟡", "🟢", "🔵", "🟣", "🟤", "⚫",
            "⚪", "🟥", "🟧", "🟨", "🟩", "🟦", "🟪", "🟫",
            "⬛", "⬜", "🔶", "🔷", "🔸", "🔹", "🔺", "🔻"
        )
    }

    override fun onStop() {
        super.onStop()
        stopProgressUpdate()
        SpotifyAppRemote.disconnect(mSpotifyAppRemote)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProgressUpdate()
        @Suppress("DEPRECATION")
        emojiManager?.shutdown()
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)

        if (requestCode == REQUEST_CODE) {
            Log.i(TAG, "Spotify Authorization Result")
            val response = AuthorizationClient.getResponse(resultCode, intent)

            when (response.type) {
                AuthorizationResponse.Type.TOKEN -> {
                    if (response.error == null) {
                        viewModel.updateAuthState(AuthState.Authenticated(response.accessToken))
                        Log.d(TAG, "Spotify Token -> ${response.accessToken}")
                    } else {
                        viewModel.updateAuthState(AuthState.Error("Authentication failed: ${response.error}"))
                        Log.e(TAG, "Spotify Token Error -> ${response.error}")
                    }
                }
                AuthorizationResponse.Type.ERROR -> {
                    viewModel.updateAuthState(AuthState.Error("Failed to authenticate with Spotify"))
                    Log.e(TAG, "Spotify authentication error: ${response.error}")
                }
                else -> {
                    viewModel.updateAuthState(AuthState.Error("Spotify authentication was cancelled"))
                    Log.w(TAG, "Spotify authentication cancelled or undefined behavior")
                }
            }
        }
    }

    /**
     * Initiates Spotify OAuth authentication flow.
     * Requests necessary permissions for playlist management and streaming.
     */
    private fun initiateSpotifyAuthentication() {
        Log.i(TAG, "Initiate Spotify Auth")

        val request = AuthorizationRequest.Builder(
            CLIENT_ID,
            AuthorizationResponse.Type.TOKEN,
            REDIRECT_URI
        ).setScopes(
            arrayOf(
                "streaming",
                "playlist-read-private",
                "playlist-read-collaborative",
                "playlist-modify-private",
                "playlist-modify-public",
                "ugc-image-upload"
            )
        ).build()

        AuthorizationClient.openLoginActivity(this, REQUEST_CODE, request)
        Log.i(TAG, "Open Spotify Auth")
    }

    /**
     * Connects to Spotify App Remote for player control.
     * @param accessToken The Spotify access token (currently unused, kept for future use)
     */
    private fun connectToSpotifyAppRemote(accessToken: String?) {
        val connectionParams = ConnectionParams.Builder(CLIENT_ID)
            .setRedirectUri(REDIRECT_URI)
            .showAuthView(true)
            .build()

        SpotifyAppRemote.connect(this, connectionParams,
            object : Connector.ConnectionListener {
                override fun onConnected(spotifyAppRemote: SpotifyAppRemote) {
                    mSpotifyAppRemote = spotifyAppRemote
                    Log.d(TAG, "Successfully connected to Spotify App Remote")
                    connected()
                }

                override fun onFailure(throwable: Throwable) {
                    Log.e(TAG, "Failed to connect to Spotify: ${throwable.message}", throwable)
                    runOnUiThread {
                        Toast.makeText(
                            this@PlayerActivity,
                            "Failed to connect to Spotify. Please ensure Spotify app is installed and you're logged in.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            })
    }

    /**
     * Converts a View to a Bitmap for uploading as playlist cover.
     */
    private fun getBitmapFromView(view: View): Bitmap? {
        if (view.width <= 0 || view.height <= 0) {
            Log.w(TAG, "Cannot create bitmap from invalid view")
            return null
        }

        return try {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            view.layout(view.left, view.top, view.right, view.bottom)
            view.draw(canvas)
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error creating bitmap from view: ${e.message}", e)
            null
        }
    }

    companion object {
        private const val TAG = "PlayerActivity"
        private const val REQUEST_CODE = 0x10
        private const val REDIRECT_URI = "emuji://callback"
        private const val CLIENT_ID = "9b9780df420f49028c410f8102b0b74c"
    }
}
