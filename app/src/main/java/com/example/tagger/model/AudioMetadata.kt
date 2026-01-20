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

    /**
     * 从文件扩展名获取的格式（可能不正确）
     */
    val extensionFormat: String
        get() = displayName.substringAfterLast('.', "").uppercase()

    /**
     * 检测实际格式与扩展名是否匹配
     */
    val isFormatMismatch: Boolean
        get() {
            val ext = extensionFormat
            val actual = format.uppercase()
            if (ext.isEmpty() || actual.isEmpty()) return false

            // 格式别名映射
            val formatAliases = mapOf(
                "M4A" to setOf("AAC", "ALAC", "M4A"),
                "AAC" to setOf("AAC", "M4A"),
                "MP3" to setOf("MP3", "MPEG"),
                "OGG" to setOf("OGG", "VORBIS", "OPUS"),
                "FLAC" to setOf("FLAC"),
                "WAV" to setOf("WAV", "WAVE", "PCM"),
                "WMA" to setOf("WMA"),
                "APE" to setOf("APE")
            )

            val extAliases = formatAliases[ext] ?: setOf(ext)
            val actualAliases = formatAliases[actual] ?: setOf(actual)

            // 如果两个集合有交集，则认为匹配
            return extAliases.intersect(actualAliases).isEmpty()
        }

    /**
     * 获取正确的扩展名
     */
    val correctExtension: String
        get() = when (format.uppercase()) {
            "FLAC" -> "flac"
            "MP3", "MPEG" -> "mp3"
            "AAC", "ALAC" -> "m4a"
            "OGG", "VORBIS" -> "ogg"
            "OPUS" -> "opus"
            "WAV", "WAVE", "PCM" -> "wav"
            "WMA" -> "wma"
            "APE" -> "ape"
            else -> format.lowercase()
        }

    /**
     * 生成修正扩展名后的文件名
     */
    val correctedDisplayName: String
        get() {
            if (!isFormatMismatch) return displayName
            val nameWithoutExt = displayName.substringBeforeLast('.', displayName)
            return "$nameWithoutExt.$correctExtension"
        }

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
