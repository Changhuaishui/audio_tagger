package com.example.tagger.core.tagreader

import android.net.Uri
import android.util.Log
import com.example.tagger.model.AudioMetadata
import java.io.File

private const val TAG = "M4aTagReader"

/**
 * M4A 标签读取器
 * - 优先 MediaMetadataRetriever
 * - 暂不新增 mp4parser 读取（保持当前行为）
 */
class M4aTagReader : AudioTagReader {

    private val mmrReader = MediaMetadataTagReader()

    override fun read(file: File, uri: Uri): AudioMetadata? {
        val result = mmrReader.read(file, uri)
        if (result != null) {
            Log.d(TAG, "M4A read via MediaMetadataRetriever: ${file.name}")
            return result
        }

        Log.w(TAG, "M4A read failed for: ${file.name}")
        return null
    }
}
