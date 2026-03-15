package com.emuji.emuji

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.animation.AnimationUtils
import android.widget.CompoundButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.emuji.emuji.databinding.ActivitySettingsBinding

/**
 * Settings activity for managing user preferences including playlist visibility,
 * collaboration settings, and emoji style preferences.
 * Enhanced with Material Design animations and haptic feedback.
 */
class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var vibrator: Vibrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        sharedPreferences = getSharedPreferences("EmujiSettings", Context.MODE_PRIVATE)
        
        syncSettings()
        animateSettingsEntrance()
    }
    
    /**
     * Animates settings cards with staggered entrance
     */
    private fun animateSettingsEntrance() {
        val fadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        val slideInAnimation = AnimationUtils.loadAnimation(this, R.anim.slide_in_right)
        
        // Get all setting cards
        val cards = listOf(
            binding.llPersonalContribution,
            binding.llWorldContribution,
            binding.llPlaylistPrivacy,
            binding.llPlaylistContribution,
            binding.llEmojiStyle
        )
        
        // Animate each card with stagger effect
        cards.forEachIndexed { index, card ->
            card.alpha = 0f
            card.postDelayed({
                card.startAnimation(slideInAnimation)
                card.alpha = 1f
            }, 100L + (index * 80L))
        }
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

    private fun syncSettings() {
        syncPersonalContribution()
        syncWorldContribution()
        syncPlaylistPrivacy()
        syncPlaylistContribution()
        syncEmojiStyle()
    }

    /**
     * Helper function to update switch status text and color
     */
    private fun updateSwitchStatus(
        textView: TextView,
        isChecked: Boolean,
        enabledText: String,
        disabledText: String
    ) {
        textView.text = if (isChecked) enabledText else disabledText
        val colorRes = if (isChecked) R.color.green else R.color.red
        textView.setTextColor(ContextCompat.getColor(this, colorRes))
    }

    /**
     * Helper function to setup switch with listener
     */
    private fun setupSwitch(
        switch: CompoundButton,
        statusTextView: TextView,
        prefKey: String,
        defaultValue: Boolean,
        enabledText: String,
        disabledText: String
    ) {
        switch.isChecked = sharedPreferences.getBoolean(prefKey, defaultValue)
        updateSwitchStatus(statusTextView, switch.isChecked, enabledText, disabledText)

        switch.setOnCheckedChangeListener { _, isChecked ->
            performHapticFeedback()
            updateSwitchStatus(statusTextView, isChecked, enabledText, disabledText)
            sharedPreferences.edit().putBoolean(prefKey, isChecked).apply()
            
            // Animate status text change
            val fadeAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in)
            statusTextView.startAnimation(fadeAnimation)
        }
    }

    /**
     * Helper function to setup checkbox
     */
    private fun setupCheckbox(checkbox: CompoundButton, prefKey: String, defaultValue: Boolean) {
        checkbox.isChecked = sharedPreferences.getBoolean(prefKey, defaultValue)
        checkbox.setOnCheckedChangeListener { _, isChecked ->
            performHapticFeedback()
            sharedPreferences.edit().putBoolean(prefKey, isChecked).apply()
        }
    }

    private fun syncEmojiStyle() {
        setupCheckbox(
            binding.checkboxUpdateEmojiStyle,
            "retroactiveEmojiStyle",
            false
        )

        binding.radioGroupEmojiStyle.setOnCheckedChangeListener { _, checkedId ->
            performHapticFeedback()
            val radioButton = findViewById<android.widget.RadioButton>(checkedId)
            val selectedStyle = radioButton.text.toString()
            sharedPreferences.edit().putString("selectedEmojiStyle", selectedStyle).apply()
        }

        val selectedStyle = sharedPreferences.getString("selectedEmojiStyle", "Device")
        when (selectedStyle) {
            "Device" -> binding.radioGroupEmojiStyle.check(R.id.radioButtonDevice)
            "Google" -> binding.radioGroupEmojiStyle.check(R.id.radioButtonGoogle)
            "Apple" -> binding.radioGroupEmojiStyle.check(R.id.radioButtonApple)
            "Twitter" -> binding.radioGroupEmojiStyle.check(R.id.radioButtonTwitter)
            "Openmoji" -> binding.radioGroupEmojiStyle.check(R.id.radioButtonOpenmoji)
        }
    }

    private fun syncPersonalContribution() {
        setupSwitch(
            binding.switchPersonalContribution,
            binding.switchPersonalContributionStatus,
            "createPersonalPlaylist",
            true,
            "Emuji will create personal playlists",
            "Emuji will not create personal playlists"
        )
    }

    private fun syncWorldContribution() {
        setupSwitch(
            binding.switchWorldContribution,
            binding.switchWorldContributionStatus,
            "contributeGlobalPlaylist",
            false,
            "You're contributing to world wide playlists",
            "You aren't contributing to world wide playlists"
        )
    }

    private fun syncPlaylistPrivacy() {
        setupCheckbox(
            binding.checkboxUpdatePlaylistPrivacy,
            "retroactivePlaylistPrivacy",
            false
        )

        setupSwitch(
            binding.switchPlaylistPrivacy,
            binding.switchPlaylistPrivacyStatus,
            "isPlaylistsPublic",
            false,
            "Your playlists will be public",
            "Your playlists will be private"
        )
    }

    private fun syncPlaylistContribution() {
        setupCheckbox(
            binding.checkboxUpdatePlaylistContribution,
            "retroactivePlaylistCollaborative",
            false
        )

        setupSwitch(
            binding.switchPlaylistContribution,
            binding.switchPlaylistContributionStatus,
            "isPlaylistsCollaborative",
            false,
            "Your playlists will be collaborative",
            "Your playlists won't be collaborative"
        )
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
