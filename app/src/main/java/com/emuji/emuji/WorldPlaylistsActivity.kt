package com.emuji.emuji

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.emuji.emuji.databinding.ActivityWorldPlaylistsBinding
import kotlinx.coroutines.launch

/**
 * Activity displaying worldwide Emuji community playlists.
 */
class WorldPlaylistsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWorldPlaylistsBinding
    private val spotifyUtil = SpotifyUtil()
    private lateinit var adapter: PlaylistAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorldPlaylistsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { onBackPressed() }

        adapter = PlaylistAdapter { playlist ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(playlist.uri))
            startActivity(intent)
        }
        binding.playlistRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.playlistRecyclerView.adapter = adapter

        loadPlaylists()
    }

    private fun loadPlaylists() {
        val token = getSharedPreferences("EmujiSettings", Context.MODE_PRIVATE)
            .getString("spotifyWebToken", null)

        if (token == null) {
            showEmpty()
            return
        }

        showLoading()
        lifecycleScope.launch {
            try {
                val playlists = spotifyUtil.getWorldPlaylists(token)
                if (playlists.isEmpty()) {
                    showEmpty()
                } else {
                    showPlaylists(playlists)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load world playlists", e)
                showEmpty()
            }
        }
    }

    private fun showLoading() {
        binding.progressIndicator.visibility = View.VISIBLE
        binding.playlistRecyclerView.visibility = View.GONE
        binding.emptyState.visibility = View.GONE
    }

    private fun showPlaylists(playlists: List<Playlist>) {
        binding.progressIndicator.visibility = View.GONE
        binding.playlistRecyclerView.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE
        adapter.submitList(playlists)
    }

    private fun showEmpty() {
        binding.progressIndicator.visibility = View.GONE
        binding.playlistRecyclerView.visibility = View.GONE
        binding.emptyState.visibility = View.VISIBLE
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    companion object {
        private const val TAG = "WorldPlaylistsActivity"
    }
}
