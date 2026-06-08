package com.example.tagger.core.tagreader

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.tagger.model.AudioMetadata
import com.example.tagger.model.CoverArt
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

private const val TAG = "JAudioTagReader"

/**
 * 使用 JAudioTagger 读取标签和封面
 * 不做格式判断，只返回 JAudioTagger 能读到的结果
 */
class JAudioTagReader : AudioTagReader {

    override fun read(file: File, uri: Uri): AudioMetadata? {
        return try {
            Log.d(TAG, "Reading with JAudioTagger: ${file.absolutePath}")
            val audio = AudioFileIO.read(file)
            val tag = audio.tag
            val header = audio.audioHeader

            // 读取封面
            val artwork = tag?.firstArtwork
            val coverBytes = artwork?.binaryData
            val coverBitmap = coverBytes?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            val coverMimeType = artwork?.mimeType ?: "image/jpeg"
            val cover = coverBytes?.let {
                CoverArt(
                    bytes = it,
                    mimeType = coverMimeType,
                    pictureType = artwork?.pictureType ?: CoverArt.FRONT_COVER
                )
            }

            Log.d(TAG, "JAudioTagger succeeded: format=${header.format}, hasCover=${coverBytes != null}")

            AudioMetadata(
                uri = uri,
                filePath = file.absolutePath,
                displayName = file.name,
                format = header.format ?: "Unknown",
                title = tag?.getFirst(FieldKey.TITLE) ?: "",
                artist = tag?.getFirst(FieldKey.ARTIST) ?: "",
                album = tag?.getFirst(FieldKey.ALBUM) ?: "",
                year = tag?.getFirst(FieldKey.YEAR) ?: "",
                track = tag?.getFirst(FieldKey.TRACK) ?: "",
                genre = tag?.getFirst(FieldKey.GENRE) ?: "",
                comment = tag?.getFirst(FieldKey.COMMENT) ?: "",
                duration = header.trackLength.toLong(),
                bitrate = header.bitRateAsNumber.toInt(),
                sampleRate = header.sampleRateAsNumber,
                cover = cover,
                coverArt = coverBitmap,
                coverArtBytes = coverBytes,
                coverArtMimeType = coverMimeType
            )
        } catch (e: Exception) {
            Log.e(TAG, "JAudioTagger failed: ${file.absolutePath}", e)
            null
        }
    }
}
