package com.emuji.emuji

data class Playlist(
    val id: String,
    val name: String,
    val uri: String,
    val trackCount: Int,
    val imageUrl: String?,
    val owner: String
)
