package com.example.tagger.model

import android.net.Uri

data class AudioMetadata(
    val uri: Uri,
    val filePath: String,
    val displayName: String,
    val format: String,
    var title: String = "",
    var artist: String = "",
    var album: String = "",
    var year: String = "",
    var track: String = "",
    var genre: String = "",
    var comment: String = "",
    val duration: Long = 0,
    val bitrate: Int = 0,
    val sampleRate: Int = 0
) {
    val formattedDuration: String
        get() {
            val minutes = duration / 60
            val seconds = duration % 60
            return "%d:%02d".format(minutes, seconds)
        }

    val displayTitle: String
        get() = title.ifEmpty { displayName }

    val displayArtistAlbum: String
        get() = listOf(artist, album)
            .filter { it.isNotEmpty() }
            .joinToString(" - ")
            .ifEmpty { "未知" }
}
