package com.example.tagger.core.tagwriter

import android.util.Log
import com.example.tagger.core.M4aTagWriter
import com.example.tagger.core.WriteResult
import com.example.tagger.model.AudioMetadata
import java.io.File

private const val TAG = "M4aAudioTagWriter"

class M4aAudioTagWriter : AudioTagWriter {
    override fun write(file: File, metadata: AudioMetadata, actualFormat: String?): WriteResult {
        Log.d(TAG, "Writing M4A tags with mp4parser: ${file.absolutePath}")

        if (!M4aTagWriter.isValidM4aFile(file)) {
            return WriteResult.Error("不是有效的 M4A 文件。可能是 AAC 裸流，不支持元数据。")
        }

        // 优先使用新的 CoverArt 模型，fallback 到旧字段
        val coverBytes = metadata.getCoverBytes()

        val m4aMetadata = M4aTagWriter.M4aMetadata(
            title = metadata.title.ifEmpty { null },
            artist = metadata.artist.ifEmpty { null },
            album = metadata.album.ifEmpty { null },
            year = metadata.year.ifEmpty { null },
            genre = metadata.genre.ifEmpty { null },
            comment = metadata.comment.ifEmpty { null },
            coverArt = coverBytes
        )

        return when (val result = M4aTagWriter.writeMetadata(file, m4aMetadata)) {
            is M4aTagWriter.Result.Success -> {
                Log.d(TAG, "M4A tags written successfully")
                WriteResult.Success
            }
            is M4aTagWriter.Result.Error -> {
                Log.e(TAG, "M4A tag write failed: ${result.message}")
                WriteResult.Error(result.message)
            }
        }
    }
}
