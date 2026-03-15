package com.emuji.emuji

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.emuji.emuji.databinding.ItemPlaylistBinding

class PlaylistAdapter(
    private val onClick: (Playlist) -> Unit
) : ListAdapter<Playlist, PlaylistAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPlaylistBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemPlaylistBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(playlist: Playlist) {
            binding.playlistName.text = playlist.name
            binding.playlistTrackCount.text = "${playlist.trackCount} tracks"
            binding.playlistCover.load(playlist.imageUrl) {
                crossfade(true)
                transformations(RoundedCornersTransformation(16f))
                placeholder(R.drawable.ic_music_note)
                error(R.drawable.ic_music_note)
            }
            binding.root.setOnClickListener { onClick(playlist) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<Playlist>() {
        override fun areItemsTheSame(old: Playlist, new: Playlist) = old.id == new.id
        override fun areContentsTheSame(old: Playlist, new: Playlist) = old == new
    }
}
