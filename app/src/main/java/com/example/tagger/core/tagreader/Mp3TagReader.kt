package com.example.tagger.core.tagreader

import android.net.Uri
import android.util.Log
import com.example.tagger.model.AudioMetadata
import java.io.File

private const val TAG = "Mp3TagReader"

/**
 * MP3 标签读取器
 * - 优先 MediaMetadataRetriever
 * - 失败时 fallback 到 JAudioTagReader
 */
class Mp3TagReader : AudioTagReader {

    private val mmrReader = MediaMetadataTagReader()
    private val jatReader = JAudioTagReader()

    override fun read(file: File, uri: Uri): AudioMetadata? {
        // 优先使用 MediaMetadataRetriever
        val mmrResult = mmrReader.read(file, uri)
        if (mmrResult != null) {
            Log.d(TAG, "MP3 read via MediaMetadataRetriever: ${file.name}")
            return mmrResult
        }

        // Fallback 到 JAudioTagger
        val jatResult = jatReader.read(file, uri)
        if (jatResult != null) {
            Log.d(TAG, "MP3 read via JAudioTagger fallback: ${file.name}")
            return jatResult
        }

        Log.w(TAG, "MP3 read failed for: ${file.name}")
        return null
    }
}
