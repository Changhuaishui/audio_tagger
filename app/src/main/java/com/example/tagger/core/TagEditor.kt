package com.example.tagger.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.tagger.model.AudioMetadata
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import java.io.FileOutputStream
import android.util.Log
import java.io.ByteArrayOutputStream
import java.util.logging.Level
import java.util.logging.Logger

private const val TAG = "TagEditor"

class TagEditor(private val context: Context) {

    init {
        // 禁用 JAudioTagger 的日志
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    /**
     * 从文件读取音频元数据 - 优先使用 MediaMetadataRetriever（更可靠）
     */
    fun read(file: File, uri: Uri): AudioMetadata? {
        // 先尝试 MediaMetadataRetriever（Android 原生，更可靠）
        return readWithMediaMetadataRetriever(file, uri)
            ?: readWithJAudioTagger(file, uri)  // fallback 到 JAudioTagger
    }

    /**
     * 使用 Android 原生 MediaMetadataRetriever 读取（处理大文件更可靠）
     */
    private fun readWithMediaMetadataRetriever(file: File, uri: Uri): AudioMetadata? {
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

            Log.d(TAG, "MediaMetadataRetriever succeeded: format=$format, duration=${durationMs/1000}s, hasCover=${coverBytes != null}")

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
                coverArt = coverBitmap,
                coverArtBytes = coverBytes,
                coverArtMimeType = "image/jpeg"  // MediaMetadataRetriever 不提供具体类型
            )
        } catch (e: Exception) {
            Log.e(TAG, "MediaMetadataRetriever failed: ${e.message}")
            null
        }
    }

    /**
     * 使用 JAudioTagger 读取（作为 fallback）
     */
    private fun readWithJAudioTagger(file: File, uri: Uri): AudioMetadata? {
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
                coverArt = coverBitmap,
                coverArtBytes = coverBytes,
                coverArtMimeType = coverMimeType
            )
        } catch (e: Exception) {
            Log.e(TAG, "JAudioTagger failed: ${file.absolutePath}", e)
            null
        }
    }

    /**
     * 从 Uri 读取（复制到缓存目录后读取）
     * 自动从文件名解析并预填充空白的标题/艺术家
     */
    fun readFromUri(uri: Uri): AudioMetadata? {
        Log.d(TAG, "readFromUri: $uri")
        val tempFile = copyToCache(uri)
        if (tempFile == null) {
            Log.e(TAG, "copyToCache failed for uri: $uri")
            return null
        }
        Log.d(TAG, "copyToCache succeeded: ${tempFile.absolutePath}")
        val metadata = read(tempFile, uri) ?: return null

        // 如果标题和艺术家都为空，尝试从文件名解析
        return if (metadata.title.isEmpty() && metadata.artist.isEmpty()) {
            val parsed = parseFromFileName(metadata.displayName)
            if (parsed != null) {
                Log.d(TAG, "Auto-filled from filename: artist=${parsed.first}, title=${parsed.second}")
                metadata.copy(artist = parsed.first, title = parsed.second)
            } else {
                metadata
            }
        } else {
            metadata
        }
    }

    /**
     * 写入元数据到文件
     */
    fun write(file: File, metadata: AudioMetadata): Boolean = runCatching {
        val audio = AudioFileIO.read(file)
        val tag = audio.tagOrCreateAndSetDefault

        if (metadata.title.isNotEmpty()) tag.setField(FieldKey.TITLE, metadata.title)
        if (metadata.artist.isNotEmpty()) tag.setField(FieldKey.ARTIST, metadata.artist)
        if (metadata.album.isNotEmpty()) tag.setField(FieldKey.ALBUM, metadata.album)
        if (metadata.year.isNotEmpty()) tag.setField(FieldKey.YEAR, metadata.year)
        if (metadata.track.isNotEmpty()) tag.setField(FieldKey.TRACK, metadata.track)
        if (metadata.genre.isNotEmpty()) tag.setField(FieldKey.GENRE, metadata.genre)
        if (metadata.comment.isNotEmpty()) tag.setField(FieldKey.COMMENT, metadata.comment)

        // 写入封面
        metadata.coverArtBytes?.let { bytes ->
            try {
                tag.deleteArtworkField()  // 先删除旧封面
                val artwork = ArtworkFactory.getNew()
                artwork.binaryData = bytes
                artwork.mimeType = metadata.coverArtMimeType ?: "image/jpeg"
                tag.setField(artwork)
                Log.d(TAG, "Cover art written successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write cover art: ${e.message}")
            }
        }

        audio.commit()
        true
    }.getOrDefault(false)

    /**
     * 通过 Uri 写入（先复制到缓存，修改后写回）
     */
    fun writeToUri(uri: Uri, metadata: AudioMetadata): Boolean {
        val tempFile = copyToCache(uri) ?: return false

        if (!write(tempFile, metadata)) {
            tempFile.delete()
            return false
        }

        // 写回原文件
        return runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                tempFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
            tempFile.delete()
            true
        }.getOrDefault(false)
    }

    /**
     * 复制 Uri 内容到缓存目录
     */
    private fun copyToCache(uri: Uri): File? {
        return try {
            val fileName = getFileName(uri) ?: "temp_audio"
            Log.d(TAG, "copyToCache: fileName=$fileName")
            val tempFile = File(context.cacheDir, fileName)

            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e(TAG, "Failed to open input stream for uri: $uri")
                return null
            }

            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val bytes = input.copyTo(output)
                    Log.d(TAG, "Copied $bytes bytes to ${tempFile.absolutePath}")
                }
            }
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "copyToCache failed", e)
            null
        }
    }

    /**
     * 获取 Uri 对应的文件名
     */
    private fun getFileName(uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            if (nameIndex >= 0) cursor.getString(nameIndex) else null
        }
    }

    /**
     * 从文件名解析标签信息（格式：艺术家 - 标题.mp3）
     */
    fun parseFromFileName(fileName: String): Pair<String, String>? {
        val nameWithoutExt = fileName.substringBeforeLast(".")
        val parts = nameWithoutExt.split(" - ", limit = 2)
        return if (parts.size == 2) {
            Pair(parts[0].trim(), parts[1].trim())
        } else {
            null
        }
    }

    /**
     * 批量从文件名填充标签
     */
    fun batchFillFromFileName(items: List<AudioMetadata>): List<AudioMetadata> {
        return items.map { item ->
            val parsed = parseFromFileName(item.displayName)
            if (parsed != null && item.artist.isEmpty() && item.title.isEmpty()) {
                item.copy(artist = parsed.first, title = parsed.second)
            } else {
                item
            }
        }
    }

    /**
     * 从图片 Uri 加载封面数据
     * @return Pair<Bitmap, ByteArray>? 封面图片和原始字节数据
     */
    fun loadCoverFromUri(imageUri: Uri): Triple<Bitmap, ByteArray, String>? {
        return try {
            val inputStream = context.contentResolver.openInputStream(imageUri) ?: return null
            val bytes = inputStream.use { it.readBytes() }

            // 解析图片
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

            // 获取 MIME 类型
            val mimeType = context.contentResolver.getType(imageUri) ?: "image/jpeg"

            Log.d(TAG, "Loaded cover image: ${bitmap.width}x${bitmap.height}, ${bytes.size} bytes, $mimeType")

            Triple(bitmap, bytes, mimeType)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cover from uri: $imageUri", e)
            null
        }
    }

    /**
     * 将 Bitmap 转换为 ByteArray（用于保存用户选择的图片）
     */
    fun bitmapToBytes(bitmap: Bitmap, quality: Int = 90): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }
}
