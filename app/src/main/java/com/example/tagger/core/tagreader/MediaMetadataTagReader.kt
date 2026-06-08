package com.example.tagger.core.tagreader

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.example.tagger.model.AudioMetadata
import com.example.tagger.model.CoverArt
import com.example.tagger.model.coverArtFromBytes
import java.io.File

private const val TAG = "MediaMetadataTagReader"

/**
 * 使用 Android 原生 MediaMetadataRetriever 读取基础信息
 * 负责读取：标题、艺术家、专辑、年份、曲目、流派、时长、码率、格式、封面
 */
class MediaMetadataTagReader : AudioTagReader {

    override fun read(file: File, uri: Uri): AudioMetadata? {
        return try {
            Log.d(TAG, "Reading with MediaMetadataRetriever: ${file.absolutePath}")
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)

            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: ""
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
            val year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR) ?: ""
            val track = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER) ?: ""
            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE) ?: ""
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: ""

            // 读取封面
            val coverBytes = retriever.embeddedPicture
            val coverBitmap = coverBytes?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            val cover = coverArtFromBytes(coverBytes, "image/jpeg")

            retriever.release()

            val format = when {
                mimeType.contains("mp3", ignoreCase = true) || mimeType.contains("mpeg", ignoreCase = true) -> "MP3"
                mimeType.contains("flac", ignoreCase = true) -> "FLAC"
                mimeType.contains("mp4", ignoreCase = true) || mimeType.contains("m4a", ignoreCase = true) -> "M4A"
                mimeType.contains("ogg", ignoreCase = true) -> "OGG"
                mimeType.contains("wav", ignoreCase = true) -> "WAV"
                mimeType.contains("aac", ignoreCase = true) -> "AAC"
                else -> file.extension.uppercase().ifEmpty { "Unknown" }
            }

            Log.d(TAG, "MediaMetadataRetriever succeeded: format=$format, duration=${durationMs / 1000}s, hasCover=${coverBytes != null}")

            AudioMetadata(
                uri = uri,
                filePath = file.absolutePath,
                displayName = file.name,
                format = format,
                title = title,
                artist = artist,
                album = album,
                year = year,
                track = track,
                genre = genre,
                comment = "",  // MediaMetadataRetriever 不支持 comment
                duration = durationMs / 1000,
                bitrate = bitrate / 1000,  // 转换为 kbps
                sampleRate = 0,  // MediaMetadataRetriever 不直接提供采样率
                cover = cover,
                coverArt = coverBitmap,
                coverArtBytes = coverBytes,
                coverArtMimeType = "image/jpeg"  // MediaMetadataRetriever 不提供具体类型
            )
        } catch (e: Exception) {
            Log.e(TAG, "MediaMetadataRetriever failed: ${e.message}")
            null
        }
    }
}
