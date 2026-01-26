package com.example.tagger.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.DocumentsContract
import com.example.tagger.model.AudioMetadata
import com.example.tagger.ui.RenameResult
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.audio.flac.metadatablock.MetadataBlockDataPicture
import java.io.File
import java.io.FileOutputStream
import android.util.Log
import java.io.ByteArrayOutputStream
import java.util.logging.Level
import java.util.logging.Logger

private const val TAG = "TagEditor"

/**
 * 标签写入结果
 */
sealed class WriteResult {
    object Success : WriteResult()
    data class Error(val message: String) : WriteResult()
}

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
     * 检查文件扩展名与实际格式是否匹配
     */
    private fun isFormatMatch(extension: String, actualFormat: String): Boolean {
        return when (actualFormat) {
            "MP3" -> extension in listOf("MP3")
            "FLAC" -> extension in listOf("FLAC")
            "M4A" -> extension in listOf("M4A", "MP4", "AAC")
            "OGG" -> extension in listOf("OGG", "OGA")
            "WAV" -> extension in listOf("WAV")
            else -> true  // 未知格式不做限制
        }
    }

    /**
     * 根据格式获取正确的扩展名
     */
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

    /**
     * 检测文件的实际格式（通过 MediaMetadataRetriever）
     */
    private fun detectActualFormat(file: File): String? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: ""
            retriever.release()

            when {
                mimeType.contains("flac", ignoreCase = true) -> "FLAC"
                mimeType.contains("mp3", ignoreCase = true) || mimeType.contains("mpeg", ignoreCase = true) -> "MP3"
                mimeType.contains("mp4", ignoreCase = true) || mimeType.contains("m4a", ignoreCase = true) -> "M4A"
                mimeType.contains("ogg", ignoreCase = true) -> "OGG"
                mimeType.contains("wav", ignoreCase = true) -> "WAV"
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 写入元数据到文件
     * 根据文件格式选择不同的写入方式：
     * - M4A: 使用 mp4parser (M4aTagWriter)
     * - AAC (裸流): 不支持元数据
     * - 其他格式 (MP3, FLAC, OGG, WAV): 使用 JAudioTagger
     */
    fun write(file: File, metadata: AudioMetadata): WriteResult {
        Log.d(TAG, "Writing tags to: ${file.absolutePath}")
        Log.d(TAG, "Title: ${metadata.title}, Artist: ${metadata.artist}, Album: ${metadata.album}")

        // 检测实际格式
        val extension = file.extension.uppercase()
        val actualFormat = detectActualFormat(file)
        Log.d(TAG, "File extension: $extension, Actual format: $actualFormat")

        // 根据格式选择写入方式
        return when {
            // M4A 格式：使用 mp4parser
            actualFormat == "M4A" || extension in listOf("M4A", "MP4") -> {
                writeWithM4aTagWriter(file, metadata)
            }

            // AAC 裸流：不支持元数据
            extension == "AAC" && !M4aTagWriter.isValidM4aFile(file) -> {
                Log.w(TAG, "Raw AAC file detected, metadata not supported")
                WriteResult.Error("AAC 裸流文件不支持元数据。建议转换为 M4A 或 MP3 格式后再编辑标签。")
            }

            // 其他格式：使用 JAudioTagger
            else -> {
                writeWithJAudioTagger(file, metadata, actualFormat)
            }
        }
    }

    /**
     * 使用 M4aTagWriter (mp4parser) 写入 M4A 文件
     */
    private fun writeWithM4aTagWriter(file: File, metadata: AudioMetadata): WriteResult {
        Log.d(TAG, "Writing M4A tags with mp4parser: ${file.absolutePath}")

        // 检查是否为有效的 M4A 容器（而非裸 AAC）
        if (!M4aTagWriter.isValidM4aFile(file)) {
            return WriteResult.Error("不是有效的 M4A 文件。可能是 AAC 裸流，不支持元数据。")
        }

        val m4aMetadata = M4aTagWriter.M4aMetadata(
            title = metadata.title.ifEmpty { null },
            artist = metadata.artist.ifEmpty { null },
            album = metadata.album.ifEmpty { null },
            year = metadata.year.ifEmpty { null },
            genre = metadata.genre.ifEmpty { null },
            comment = metadata.comment.ifEmpty { null },
            coverArt = metadata.coverArtBytes
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

    /**
     * 使用 JAudioTagger 写入 (MP3, FLAC, OGG, WAV 等格式)
     */
    private fun writeWithJAudioTagger(file: File, metadata: AudioMetadata, actualFormat: String?): WriteResult {
        return try {
            Log.d(TAG, "Writing tags with JAudioTagger: ${file.absolutePath}")

            val extension = file.extension.uppercase()

            // 如果格式不匹配，使用临时文件（正确扩展名）来写入
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

            // 无论是否为空都设置字段（允许清空）
            tag.setField(FieldKey.TITLE, metadata.title)
            tag.setField(FieldKey.ARTIST, metadata.artist)
            tag.setField(FieldKey.ALBUM, metadata.album)
            if (metadata.year.isNotEmpty()) tag.setField(FieldKey.YEAR, metadata.year)
            if (metadata.track.isNotEmpty()) tag.setField(FieldKey.TRACK, metadata.track)
            if (metadata.genre.isNotEmpty()) tag.setField(FieldKey.GENRE, metadata.genre)
            if (metadata.comment.isNotEmpty()) tag.setField(FieldKey.COMMENT, metadata.comment)

            // 写入封面
            metadata.coverArtBytes?.let { bytes ->
                try {
                    val isFlac = tag is FlacTag
                    if (isFlac) {
                        // FLAC 文件：直接使用 MetadataBlockDataPicture 避免 ImageIO 调用
                        writeFlacCover(tag as FlacTag, bytes, metadata.coverArtMimeType ?: "image/jpeg")
                    } else {
                        // 其他格式：使用标准方式
                        tag.deleteArtworkField()
                        val artwork = ArtworkFactory.getNew()
                        artwork.binaryData = bytes
                        artwork.mimeType = metadata.coverArtMimeType ?: "image/jpeg"
                        artwork.pictureType = 3  // Front Cover
                        tag.setField(artwork)
                    }
                    Log.d(TAG, "Cover art written: ${bytes.size} bytes, isFlac=$isFlac")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to write cover art: ${e.message}")
                }
            }

            audio.commit()
            Log.d(TAG, "Tags written successfully to: ${workFile.absolutePath}")

            // 如果使用了临时文件，将内容复制回原文件
            if (workFile != file) {
                workFile.copyTo(file, overwrite = true)
                workFile.delete()
                Log.d(TAG, "Copied back to original file and deleted temp file")
            }

            WriteResult.Success
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

    /**
     * 为 FLAC 文件写入封面，直接使用 MetadataBlockDataPicture 避免 ImageIO 调用
     */
    private fun writeFlacCover(flacTag: FlacTag, imageData: ByteArray, mimeType: String) {
        // 删除现有封面
        flacTag.deleteArtworkField()

        // 解析图片尺寸
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageData, 0, imageData.size, options)
        val width = options.outWidth
        val height = options.outHeight

        // 创建 FLAC Picture block
        // pictureType 3 = Front Cover
        val picture = MetadataBlockDataPicture(
            imageData,
            3,  // pictureType: Front Cover
            mimeType,
            "",  // description
            width,
            height,
            0,  // colourDepth (0 = unknown)
            0   // indexedColourCount (0 for non-indexed)
        )

        flacTag.setField(picture)
        Log.d(TAG, "FLAC cover written: ${imageData.size} bytes, ${width}x${height}, $mimeType")
    }

    /**
     * 通过 Uri 写入（先复制到缓存，修改后写回）
     */
    fun writeToUri(uri: Uri, metadata: AudioMetadata): WriteResult {
        Log.d(TAG, "writeToUri: $uri")

        val tempFile = copyToCache(uri)
        if (tempFile == null) {
            Log.e(TAG, "Failed to copy to cache")
            return WriteResult.Error("无法读取文件")
        }

        val writeResult = write(tempFile, metadata)
        if (writeResult is WriteResult.Error) {
            Log.e(TAG, "Failed to write tags to temp file")
            tempFile.delete()
            return writeResult
        }

        // 写回原文件
        return try {
            val outputStream = context.contentResolver.openOutputStream(uri, "wt")
            if (outputStream == null) {
                Log.e(TAG, "Failed to open output stream for uri: $uri")
                tempFile.delete()
                return WriteResult.Error("无法写入原文件，可能是权限问题")
            }

            outputStream.use { output ->
                tempFile.inputStream().use { input ->
                    val bytes = input.copyTo(output)
                    Log.d(TAG, "Wrote $bytes bytes back to original file")
                }
            }
            tempFile.delete()

            // Notify MediaScanner to refresh metadata in the system media library
            notifyMediaScanner(uri, metadata.filePath)

            Log.d(TAG, "writeToUri completed successfully")
            WriteResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write back to uri: ${e.message}", e)
            tempFile.delete()
            WriteResult.Error("写回文件失败: ${e.message ?: "未知错误"}")
        }
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

    /**
     * 重命名文件（修复扩展名）
     * @param uri 原始文件 Uri
     * @param newName 新文件名（包含扩展名）
     * @return RenameResult 包含新的 Uri 或错误信息
     */
    fun renameFile(uri: Uri, newName: String): RenameResult {
        return try {
            Log.d(TAG, "Renaming file: $uri -> $newName")

            // 策略1: 优先尝试直接文件重命名（更可靠）
            val filePath = getFilePathFromUri(uri)
            if (filePath != null) {
                val file = File(filePath)
                if (file.exists() && file.canWrite()) {
                    val newFile = File(file.parent, newName)
                    if (file.renameTo(newFile)) {
                        Log.d(TAG, "Direct file rename succeeded: ${newFile.absolutePath}")
                        // 通知 MediaScanner 更新
                        notifyMediaScanner(Uri.fromFile(newFile), newFile.absolutePath)
                        return RenameResult.Success(Uri.fromFile(newFile))
                    } else {
                        Log.w(TAG, "Direct rename failed, trying DocumentsContract")
                    }
                }
            }

            // 策略2: 使用 DocumentsContract（需要 SAF 写权限）
            if (DocumentsContract.isDocumentUri(context, uri)) {
                // 先检查是否有写权限
                val hasWritePermission = context.checkUriPermission(
                    uri,
                    android.os.Process.myPid(),
                    android.os.Process.myUid(),
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                ) == PackageManager.PERMISSION_GRANTED

                if (!hasWritePermission) {
                    Log.w(TAG, "No write permission for DocumentUri, attempting anyway...")
                }

                try {
                    val newUri = DocumentsContract.renameDocument(
                        context.contentResolver,
                        uri,
                        newName
                    )
                    if (newUri != null) {
                        Log.d(TAG, "DocumentsContract rename succeeded: $newUri")
                        return RenameResult.Success(newUri)
                    } else {
                        Log.e(TAG, "DocumentsContract rename returned null")
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "Security exception during rename (Fix with AI)", e)
                    // 继续尝试其他方法
                }
            }

            // 策略3: 如果有文件路径但之前重命名失败，尝试复制+删除
            if (filePath != null) {
                val file = File(filePath)
                if (file.exists()) {
                    val newFile = File(file.parent, newName)
                    try {
                        file.copyTo(newFile, overwrite = true)
                        if (file.delete()) {
                            Log.d(TAG, "Copy+delete rename succeeded: ${newFile.absolutePath}")
                            notifyMediaScanner(Uri.fromFile(newFile), newFile.absolutePath)
                            return RenameResult.Success(Uri.fromFile(newFile))
                        } else {
                            // 复制成功但删除失败，删除复制的文件
                            newFile.delete()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Copy+delete rename failed", e)
                        newFile.delete() // 清理可能的部分复制
                    }
                }
            }

            RenameResult.Error("重命名失败：无法修改文件。请尝试重新选择文件或使用文件管理器手动修改")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception during rename", e)
            RenameResult.Error("权限不足，请重新选择文件以获取写入权限")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rename file", e)
            RenameResult.Error("重命名失败: ${e.message ?: "未知错误"}")
        }
    }

    /**
     * 尝试从 Uri 获取实际文件路径
     */
    private fun getFilePathFromUri(uri: Uri): String? {
        return try {
            when (uri.scheme) {
                "file" -> uri.path
                "content" -> {
                    // 尝试从 MediaStore 或其他内容提供者获取路径
                    context.contentResolver.query(uri, arrayOf("_data"), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val columnIndex = cursor.getColumnIndex("_data")
                            if (columnIndex >= 0) cursor.getString(columnIndex) else null
                        } else null
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get file path from uri", e)
            null
        }
    }

    /**
     * Notify MediaScanner to refresh the file's metadata in system media library.
     * This ensures updated tags (title, artist, cover art) appear in music players.
     */
    private fun notifyMediaScanner(uri: Uri, filePath: String?) {
        try {
            // Try to get actual file path if not provided
            val actualPath = filePath ?: getFilePathFromUri(uri)

            if (actualPath != null) {
                Log.d(TAG, "Notifying MediaScanner for: $actualPath")
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(actualPath),
                    null // Let the system detect MIME type
                ) { path, scannedUri ->
                    Log.d(TAG, "MediaScanner completed: $path -> $scannedUri")
                }
            } else {
                Log.w(TAG, "Could not get file path for MediaScanner, uri: $uri")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to notify MediaScanner", e)
        }
    }
}
