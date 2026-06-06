package com.example.tagger.core.tagreader

import android.net.Uri
import android.util.Log
import com.example.tagger.model.AudioMetadata
import java.io.File

private const val TAG = "FlacTagReader"

/**
 * FLAC 标签读取器
 * - 优先 MediaMetadataRetriever
 * - 失败或封面缺失时可 fallback 到 JAudioTagReader
 * - 不改变当前 FLAC 成功路径
 */
class FlacTagReader : AudioTagReader {

    private val mmrReader = MediaMetadataTagReader()
    private val jatReader = JAudioTagReader()

    override fun read(file: File, uri: Uri): AudioMetadata? {
        // 优先使用 MediaMetadataRetriever
        val mmrResult = mmrReader.read(file, uri)

        // 如果 MMR 成功且读到了封面，直接返回
        if (mmrResult != null && mmrResult.coverArtBytes != null) {
            Log.d(TAG, "FLAC read via MediaMetadataRetriever (with cover): ${file.name}")
            return mmrResult
        }

        // MMR 失败或没读到封面，尝试 JAudioTagger
        val jatResult = jatReader.read(file, uri)
        if (jatResult != null) {
            Log.d(TAG, "FLAC read via JAudioTagger: ${file.name}, hasCover=${jatResult.coverArtBytes != null}")
            // 如果 MMR 成功但缺少封面，用 JAudioTagger 的结果（可能包含封面）
            // 如果 MMR 失败，直接用 JAudioTagger 结果
            return jatResult
        }

        // JAudioTagger 也失败，但至少返回 MMR 的结果（如果有）
        if (mmrResult != null) {
            Log.d(TAG, "FLAC read via MediaMetadataRetriever (no cover): ${file.name}")
            return mmrResult
        }

        Log.w(TAG, "FLAC read failed for: ${file.name}")
        return null
    }
}
