package com.example.tagger.core.tagreader

import android.net.Uri
import android.util.Log
import com.example.tagger.model.AudioMetadata
import java.io.File

private const val TAG = "WavTagReader"

/**
 * WAV 标签读取器
 * - 先读 MediaMetadataRetriever，保留时长、码率、格式等基础信息
 * - 再读 JAudioTagReader
 * - 用 JAudioTagger 的文字标签和封面覆盖 MMR 的标签字段
 * - 保留 MMR 的 duration/bitrate/sampleRate
 */
class WavTagReader : AudioTagReader {

    private val mmrReader = MediaMetadataTagReader()
    private val jatReader = JAudioTagReader()

    override fun read(file: File, uri: Uri): AudioMetadata? {
        // 先读 MediaMetadataRetriever 获取基础信息
        val mmrResult = mmrReader.read(file, uri)

        // 再读 JAudioTagger 获取标签和封面
        val jatResult = jatReader.read(file, uri)

        return if (mmrResult != null && jatResult != null) {
            // 两者都成功，合并结果
            Log.d(TAG, "WAV merged MMR + JAudioTagger: ${file.name}")
            mergeTagFields(mmrResult, jatResult)
        } else if (jatResult != null) {
            // 只有 JAudioTagger 成功
            Log.d(TAG, "WAV read via JAudioTagger: ${file.name}")
            jatResult
        } else if (mmrResult != null) {
            // 只有 MMR 成功
            Log.d(TAG, "WAV read via MediaMetadataRetriever: ${file.name}")
            mmrResult
        } else {
            Log.w(TAG, "WAV read failed for: ${file.name}")
            null
        }
    }

    /**
     * 合并 MMR 和 JAudioTagger 的结果
     * - 保留 MMR 的基础信息（duration, bitrate, sampleRate, format, displayName, filePath, uri）
     * - 使用 JAudioTagger 的标签字段覆盖，但如果 JAT 字段为空则保留 MMR 的
     */
    private fun mergeTagFields(mmr: AudioMetadata, jat: AudioMetadata): AudioMetadata {
        return mmr.copy(
            title = jat.title.takeIf { it.isNotEmpty() } ?: mmr.title,
            artist = jat.artist.takeIf { it.isNotEmpty() } ?: mmr.artist,
            album = jat.album.takeIf { it.isNotEmpty() } ?: mmr.album,
            year = jat.year.takeIf { it.isNotEmpty() } ?: mmr.year,
            track = jat.track.takeIf { it.isNotEmpty() } ?: mmr.track,
            genre = jat.genre.takeIf { it.isNotEmpty() } ?: mmr.genre,
            comment = jat.comment.takeIf { it.isNotEmpty() } ?: mmr.comment,
            // JAudioTagger 的封面更可靠
            cover = jat.cover ?: mmr.cover,
            coverArt = jat.coverArt ?: mmr.coverArt,
            coverArtBytes = jat.coverArtBytes ?: mmr.coverArtBytes,
            coverArtMimeType = jat.coverArtMimeType ?: mmr.coverArtMimeType
        )
    }
}
