package com.emuji.emuji

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Activity displaying worldwide mood-based playlists.
 * Enhanced with modern transition animations.
 */
class WorldPlaylistsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_world_playlists)
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
