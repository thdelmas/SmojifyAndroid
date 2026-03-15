package com.emuji.emuji

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.emoji2.bundled.BundledEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat
import com.google.android.material.card.MaterialCardView
import com.emuji.emuji.databinding.ActivityMainBinding

/**
 * Main menu activity providing navigation to different app sections.
 * Enhanced with Material Design animations and haptic feedback.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var vibrator: Vibrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize EmojiCompat for consistent emoji rendering
        initializeEmojiCompat()
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        
        setupClickListeners()
        animateCardsEntrance()
    }
    
    private fun initializeEmojiCompat() {
        if (!EmojiCompat.isConfigured()) {
            val config = BundledEmojiCompatConfig(this)
            EmojiCompat.init(config)
        }
    }
    
    /**
     * Animates navigation cards with staggered entrance animation
     */
    private fun animateCardsEntrance() {
        val fadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        val slideInAnimation = AnimationUtils.loadAnimation(this, R.anim.slide_in_right)
        
        // Animate logo and title first
        binding.logoCard.alpha = 0f
        binding.appTitle.alpha = 0f
        binding.appSubtitle.alpha = 0f
        
        binding.logoCard.postDelayed({
            binding.logoCard.startAnimation(fadeInAnimation)
            binding.logoCard.alpha = 1f
        }, 100)
        
        binding.appTitle.postDelayed({
            binding.appTitle.startAnimation(fadeInAnimation)
            binding.appTitle.alpha = 1f
        }, 200)
        
        binding.appSubtitle.postDelayed({
            binding.appSubtitle.startAnimation(fadeInAnimation)
            binding.appSubtitle.alpha = 1f
        }, 300)
        
        // Animate navigation cards with stagger
        val cards = listOf(
            binding.btnSettings,
            binding.btnPlayer,
            binding.btnUserMood,
            binding.btnWorldMood
        )
        
        cards.forEachIndexed { index, card ->
            card.alpha = 0f
            card.postDelayed({
                card.startAnimation(slideInAnimation)
                card.alpha = 1f
            }, 400L + (index * 100L))
        }
    }

    private fun setupClickListeners() {
        setupCardClick(binding.btnSettings) {
            navigateToActivity(SettingsActivity::class.java)
        }

        setupCardClick(binding.btnPlayer) {
            navigateToActivity(PlayerActivity::class.java)
        }

        setupCardClick(binding.btnUserMood) {
            navigateToActivity(UserPlaylistsActivity::class.java)
        }

        setupCardClick(binding.btnWorldMood) {
            navigateToActivity(WorldPlaylistsActivity::class.java)
        }
    }
    
    /**
     * Sets up click listener with scale animation and haptic feedback
     */
    private fun setupCardClick(card: MaterialCardView, onClick: () -> Unit) {
        card.setOnClickListener {
            performHapticFeedback()
            animateCardPress(card) {
                onClick()
            }
        }
    }
    
    /**
     * Animates card press with scale effect
     */
    private fun animateCardPress(view: View, onComplete: () -> Unit) {
        view.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
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
     * Provides haptic feedback for button presses
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
     * Navigates to activity with custom transition animation
     */
    private fun navigateToActivity(activityClass: Class<*>) {
        startActivity(Intent(this, activityClass))
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
