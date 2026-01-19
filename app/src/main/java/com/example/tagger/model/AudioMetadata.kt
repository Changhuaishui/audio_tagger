package com.example.tagger.model

import android.graphics.Bitmap
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
    val sampleRate: Int = 0,
    // 封面相关
    val coverArt: Bitmap? = null,          // 封面图片（用于显示）
    val coverArtBytes: ByteArray? = null,  // 封面原始数据（用于保存）
    val coverArtMimeType: String? = null   // 封面 MIME 类型
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

    val hasCoverArt: Boolean
        get() = coverArt != null || coverArtBytes != null

    // ByteArray 需要自定义 equals/hashCode
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AudioMetadata
        return uri == other.uri &&
                filePath == other.filePath &&
                displayName == other.displayName &&
                title == other.title &&
                artist == other.artist &&
                album == other.album &&
                coverArtBytes.contentEquals(other.coverArtBytes)
    }

    override fun hashCode(): Int {
        var result = uri.hashCode()
        result = 31 * result + filePath.hashCode()
        result = 31 * result + (coverArtBytes?.contentHashCode() ?: 0)
        return result
    }
}
