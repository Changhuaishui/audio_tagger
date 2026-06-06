package com.example.tagger.core.tagwriter

import android.util.Log
import com.example.tagger.core.WriteResult
import com.example.tagger.model.AudioMetadata
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File

private const val TAG = "JAudioTagWriter"

abstract class JAudioTagWriter : AudioTagWriter {

    override fun write(file: File, metadata: AudioMetadata, actualFormat: String?): WriteResult {
        return withWriteOptions {
            writeWithCurrentOptions(file, metadata, actualFormat)
        }
    }

    private fun writeWithCurrentOptions(
        file: File,
        metadata: AudioMetadata,
        actualFormat: String?
    ): WriteResult {
        return try {
            Log.d(TAG, "Writing tags with JAudioTagger: ${file.absolutePath}")

            val extension = file.extension.uppercase()
            val workFile = if (actualFormat != null && !isFormatMatch(extension, actualFormat)) {
                Log.w(TAG, "Format mismatch: extension=$extension, actual=$actualFormat. Using temp file with correct extension.")
                val correctExt = getExtensionForFormat(actualFormat)
                val tempFile = File(file.parent, "${file.nameWithoutExtension}.$correctExt")
                file.copyTo(tempFile, overwrite = true)
                tempFile
            } else {
                file
            }

            val audio = AudioFileIO.read(workFile)
            val tag = audio.tagOrCreateAndSetDefault

            writeTextFields(tag, metadata)

            var coverError: String? = null
            metadata.coverArtBytes?.let { bytes ->
                coverError = try {
                    writeCover(tag, bytes, metadata.coverArtMimeType ?: "image/jpeg")
                    null
                } catch (e: NoClassDefFoundError) {
                    Log.e(TAG, "封面写入失败：Android 不支持 javax.imageio.ImageIO，该格式暂不支持写入封面")
                    "该格式暂不支持写入封面"
                } catch (e: Exception) {
                    val message = e.message ?: "封面写入失败"
                    Log.e(TAG, "Failed to write cover art: $message", e)
                    message
                }
            }

            beforeCommit(tag)
            audio.commit()
            Log.d(TAG, "Tags written successfully to: ${workFile.absolutePath}")

            if (workFile != file) {
                workFile.copyTo(file, overwrite = true)
                workFile.delete()
                Log.d(TAG, "Copied back to original file and deleted temp file")
            }

            if (coverError != null) {
                WriteResult.PartialSuccess(coverError)
            } else {
                WriteResult.Success
            }
        } catch (e: org.jaudiotagger.audio.exceptions.InvalidAudioFrameException) {
            Log.e(TAG, "Invalid audio frame: ${e.message}", e)
            WriteResult.Error("文件格式损坏或不支持，无法写入标签")
        } catch (e: org.jaudiotagger.audio.exceptions.CannotReadException) {
            Log.e(TAG, "Cannot read audio: ${e.message}", e)
            WriteResult.Error("无法读取音频文件")
        } catch (e: org.jaudiotagger.audio.exceptions.CannotWriteException) {
            Log.e(TAG, "Cannot write audio: ${e.message}", e)
            WriteResult.Error("无法写入音频文件，可能是权限问题")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write tags: ${e.message}", e)
            WriteResult.Error("保存失败: ${e.message ?: "未知错误"}")
        }
    }

    private fun writeTextFields(tag: Tag, metadata: AudioMetadata) {
        tag.setField(FieldKey.TITLE, metadata.title)
        tag.setField(FieldKey.ARTIST, metadata.artist)
        tag.setField(FieldKey.ALBUM, metadata.album)
        if (metadata.year.isNotEmpty()) tag.setField(FieldKey.YEAR, metadata.year)
        if (metadata.track.isNotEmpty()) tag.setField(FieldKey.TRACK, metadata.track)
        if (metadata.genre.isNotEmpty()) tag.setField(FieldKey.GENRE, metadata.genre)
        if (metadata.comment.isNotEmpty()) tag.setField(FieldKey.COMMENT, metadata.comment)
    }

    protected open fun writeCover(tag: Tag, imageData: ByteArray, mimeType: String) {
        tag.deleteArtworkField()
        val artwork = ArtworkFactory.getNew()
        artwork.binaryData = imageData
        artwork.mimeType = mimeType
        artwork.pictureType = 3
        tag.setField(artwork)
        Log.d(TAG, "Cover art written: ${imageData.size} bytes")
    }

    protected open fun beforeCommit(tag: Tag) = Unit

    protected open fun <T> withWriteOptions(block: () -> T): T = block()

    private fun isFormatMatch(extension: String, actualFormat: String): Boolean {
        return when (actualFormat) {
            "MP3" -> extension in listOf("MP3")
            "FLAC" -> extension in listOf("FLAC")
            "M4A" -> extension in listOf("M4A", "MP4", "AAC")
            "OGG" -> extension in listOf("OGG", "OGA")
            "WAV" -> extension in listOf("WAV")
            else -> true
        }
    }

    private fun getExtensionForFormat(format: String): String {
        return when (format) {
            "MP3" -> "mp3"
            "FLAC" -> "flac"
            "M4A" -> "m4a"
            "OGG" -> "ogg"
            "WAV" -> "wav"
            else -> "tmp"
        }
    }
}
