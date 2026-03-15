package com.emuji.emuji

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Activity displaying user's mood-based playlists.
 * Enhanced with modern transition animations.
 */
class UserPlaylistsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_playlists)
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
