package com.example.tagger.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.example.tagger.core.tagreader.FlacTagReader
import com.example.tagger.core.tagreader.JAudioTagReader
import com.example.tagger.core.tagreader.M4aTagReader
import com.example.tagger.core.tagreader.Mp3TagReader
import com.example.tagger.core.tagreader.OggTagReader
import com.example.tagger.core.tagreader.WavTagReader
import com.example.tagger.core.tagwriter.FlacTagWriter
import com.example.tagger.core.tagwriter.M4aAudioTagWriter
import com.example.tagger.core.tagwriter.Mp3TagWriter
import com.example.tagger.core.tagwriter.OggTagWriter
import com.example.tagger.core.tagwriter.WavTagWriter
import com.example.tagger.model.AudioMetadata
import com.example.tagger.ui.RenameResult
import java.io.File
import java.io.FileOutputStream
import java.util.logging.Level
import java.util.logging.Logger

private const val TAG = "TagEditor"

/**
 * 标签写入结果
 */
sealed class WriteResult {
    object Success : WriteResult()
    data class PartialSuccess(val message: String) : WriteResult()
    data class Error(val message: String) : WriteResult()
}

class TagEditor(private val context: Context) {

    private val m4aWriter = M4aAudioTagWriter()
    private val mp3Writer = Mp3TagWriter()
    private val flacWriter = FlacTagWriter()
    private val wavWriter = WavTagWriter()
    private val oggWriter = OggTagWriter()

    // 格式专用 Reader
    private val mp3Reader = Mp3TagReader()
    private val flacReader = FlacTagReader()
    private val m4aReader = M4aTagReader()
    private val wavReader = WavTagReader()
    private val oggReader = OggTagReader()
    private val fallbackReader = JAudioTagReader()

    init {
        // 禁用 JAudioTagger 的日志
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    /**
     * 从文件读取音频元数据
     * 根据检测到的格式分发给对应的 Reader
     */
    fun read(file: File, uri: Uri): AudioMetadata? {
        val format = detectActualFormat(file) ?: file.extension.uppercase()
        return readerFor(format).read(file, uri)
    }

    /**
     * Reader 分发
     */
    private fun readerFor(format: String) = when (format.uppercase()) {
        "MP3", "MPEG" -> mp3Reader
        "FLAC" -> flacReader
        "M4A", "AAC", "ALAC", "MP4" -> m4aReader
        "WAV", "WAVE", "PCM" -> wavReader
        "OGG", "VORBIS", "OPUS" -> oggReader
        else -> fallbackReader
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
        val metadata = try {
            val displayName = getFileName(uri) ?: tempFile.name
            read(tempFile, uri)?.copy(
                filePath = getStableFilePath(uri),
                displayName = displayName
            )
        } finally {
            if (!tempFile.delete()) {
                Log.w(TAG, "Failed to delete temp cache file: ${tempFile.absolutePath}")
            }
        } ?: return null

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
     * 检测文件的实际格式（通过 MediaMetadataRetriever）
     */
    private fun detectActualFormat(file: File): String? {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val mimeType = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: ""
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

        // 检测实际格式（优先使用实际格式，而非扩展名）
        val extension = file.extension.uppercase()
        val actualFormat = detectActualFormat(file)
        Log.d(TAG, "File extension: $extension, Actual format: $actualFormat")

        // 根据【实际格式】选择写入方式（修复扩展名与格式不匹配的问题）
        val formatToUse = actualFormat ?: extension
        Log.d(TAG, "Using format for write: $formatToUse")

        return when (formatToUse) {
            // M4A 格式：使用 mp4parser
            "M4A" -> {
                // 双重验证：确保文件确实是有效的 M4A 容器
                if (M4aTagWriter.isValidM4aFile(file)) {
                    m4aWriter.write(file, metadata, actualFormat)
                } else {
                    Log.w(TAG, "File detected as M4A but is not a valid M4A container, trying JAudioTagger")
                    mp3Writer.write(file, metadata, actualFormat)
                }
            }

            // AAC 裸流：不支持元数据
            "AAC" -> {
                if (M4aTagWriter.isValidM4aFile(file)) {
                    // 实际上是 M4A 容器中的 AAC
                    m4aWriter.write(file, metadata, actualFormat)
                } else {
                    Log.w(TAG, "Raw AAC file detected, metadata not supported")
                    WriteResult.Error("AAC 裸流文件不支持元数据。建议转换为 M4A 或 MP3 格式后再编辑标签。")
                }
            }

            "FLAC" -> flacWriter.write(file, metadata, actualFormat)
            "WAV" -> wavWriter.write(file, metadata, actualFormat)
            "OGG" -> oggWriter.write(file, metadata, actualFormat)
            else -> mp3Writer.write(file, metadata, actualFormat)
        }
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
            Log.e(TAG, "Failed to write tags to temp file: ${writeResult.message}")
            tempFile.delete()
            return writeResult
        }

        // 写回原 URI。content:// 不能当作普通文件路径处理，只能通过 ContentResolver 写入。
        return try {
            val outputStream = context.contentResolver.openOutputStream(uri, "wt")
            if (outputStream == null) {
                Log.e(TAG, "Failed to open output stream for uri: $uri")
                tempFile.delete()
                return WriteResult.Error("无法写入原文件：文件提供方不支持写回，请重新通过文件选择器选择或另存为新文件")
            }

            outputStream.use { output ->
                tempFile.inputStream().use { input ->
                    val bytes = input.copyTo(output)
                    Log.d(TAG, "Wrote $bytes bytes back to original file")
                }
            }
            tempFile.delete()

            // Notify MediaScanner to refresh metadata in the system media library
            notifyMediaScanner(uri, null)

            Log.d(TAG, "writeToUri completed successfully")
            // 如果标签写入阶段返回了 PartialSuccess（如封面失败），保持传递
            if (writeResult is WriteResult.PartialSuccess) writeResult else WriteResult.Success
        } catch (e: SecurityException) {
            Log.e(TAG, "No write permission for uri: $uri", e)
            tempFile.delete()
            WriteResult.Error("没有写入权限：该文件来源只授予了读取权限，请在应用内重新选择文件，或保存为新文件")
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
            val extension = fileName.substringAfterLast('.', "")
                .replace(Regex("[^A-Za-z0-9]"), "")
            val prefixBase = fileName.substringBeforeLast('.', "audio")
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .take(40)
                .ifBlank { "audio" }
            val prefix = prefixBase.padEnd(3, '_')
            val suffix = if (extension.isNotBlank()) ".$extension" else ".tmp"
            val tempFile = File.createTempFile("${prefix}_", suffix, context.cacheDir)

            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e(TAG, "Failed to open input stream for uri: $uri")
                return null
            }

            // 注意：不使用 inputStream.use{}，因为某些 file:// URI 在 close() 时
            // 会抛出 EIO 错误，即使数据已经完整复制。我们手动处理 close。
            try {
                FileOutputStream(tempFile).use { output ->
                    val bytes = inputStream.copyTo(output)
                    Log.d(TAG, "Copied $bytes bytes to ${tempFile.absolutePath}")
                }
            } finally {
                try {
                    inputStream.close()
                } catch (closeEx: Exception) {
                    Log.w(TAG, "inputStream.close() failed (data already copied, ignoring): ${closeEx.message}")
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
            if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
        }
    }

    /**
     * 给列表去重、事件通知使用的稳定标识。能拿到真实路径时使用真实路径，否则使用 Uri。
     * 注意不要返回 cache 路径，cache 文件是一次性临时副本。
     */
    private fun getStableFilePath(uri: Uri): String {
        return getFilePathFromUri(uri) ?: uri.toString()
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
     * @return Triple<Bitmap, ByteArray, String>? 封面图片和原始字节数据
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
        val stream = java.io.ByteArrayOutputStream()
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

            val filePath = getFilePathFromUri(uri)

            // 策略1: 对于 MediaStore Uri，使用 ContentResolver.update()
            if (isMediaStoreUri(uri) && filePath != null) {
                val result = renameViaMediaStore(uri, filePath, newName)
                if (result != null) {
                    return result
                }
                Log.w(TAG, "MediaStore rename failed, trying direct file rename")
            }

            // 策略2: 尝试直接文件重命名
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

            // 策略3: 使用 DocumentsContract（需要 SAF 写权限）
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

            // 策略4: 如果有文件路径但之前重命名失败，尝试复制+删除
            if (filePath != null) {
                val file = File(filePath)
                if (file.exists()) {
                    val newFile = File(file.parent, newName)
                    try {
                        file.copyTo(newFile, overwrite = true)
                        if (file.delete()) {
                            Log.d(TAG, "Copy+delete rename succeeded: ${newFile.absolutePath}")
                            notifyMediaScanner(Uri.fromFile(newFile), newFile.absolutePath)
                            // 删除旧的 MediaStore 记录
                            if (isMediaStoreUri(uri)) {
                                try {
                                    context.contentResolver.delete(uri, null, null)
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to delete old MediaStore entry", e)
                                }
                            }
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
     * 检查是否为 MediaStore Uri
     */
    private fun isMediaStoreUri(uri: Uri): Boolean {
        return uri.authority == "media" ||
               uri.authority == "com.android.providers.media.documents" ||
               uri.toString().contains("content://media/")
    }

    /**
     * 通过 MediaStore API 重命名文件（适用于 Android 10+ Scoped Storage）
     *
     * @return RenameResult 如果成功，否则 null
     */
    private fun renameViaMediaStore(uri: Uri, filePath: String, newName: String): RenameResult? {
        return try {
            val file = File(filePath)
            val newFile = File(file.parent, newName)

            Log.d(TAG, "Attempting MediaStore rename: $filePath -> ${newFile.absolutePath}")

            // 方式1: 直接操作文件系统，然后更新 MediaStore
            // 这在某些设备上有效，因为应用可能有文件的所有权
            if (file.renameTo(newFile)) {
                Log.d(TAG, "File.renameTo succeeded, updating MediaStore")

                // 更新 MediaStore 记录
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Audio.Media.DISPLAY_NAME, newName)
                    put(android.provider.MediaStore.Audio.Media.DATA, newFile.absolutePath)
                }

                try {
                    val updated = context.contentResolver.update(uri, values, null, null)
                    Log.d(TAG, "MediaStore update result: $updated rows")
                } catch (e: Exception) {
                    Log.w(TAG, "MediaStore update failed, triggering rescan", e)
                }

                // 无论 MediaStore 更新是否成功，都触发扫描
                notifyMediaScanner(Uri.fromFile(newFile), newFile.absolutePath)

                // 尝试删除旧路径的 MediaStore 记录
                try {
                    MediaScannerConnection.scanFile(context, arrayOf(filePath), null) { _, _ -> }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to scan old path", e)
                }

                return RenameResult.Success(uri)
            }

            // 方式2: 使用 ContentResolver 更新 DISPLAY_NAME（某些系统支持）
            Log.d(TAG, "Direct rename failed, trying ContentValues update")
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Audio.Media.DISPLAY_NAME, newName)
            }

            val updated = context.contentResolver.update(uri, values, null, null)
            if (updated > 0) {
                Log.d(TAG, "ContentValues DISPLAY_NAME update succeeded")
                // 重新查询获取新的文件路径
                val newPath = getFilePathFromUri(uri)
                if (newPath != null) {
                    notifyMediaScanner(uri, newPath)
                    return RenameResult.Success(uri)
                }
            }

            Log.w(TAG, "MediaStore rename methods failed")
            null
        } catch (e: android.app.RecoverableSecurityException) {
            Log.e(TAG, "RecoverableSecurityException in MediaStore rename - need user permission", e)
            RenameResult.NeedPermission(listOf(uri))
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException in MediaStore rename - need user permission", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore rename failed", e)
            null
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
                    if (DocumentsContract.isDocumentUri(context, uri)) {
                        val documentId = DocumentsContract.getDocumentId(uri)
                        if (documentId.startsWith("raw:")) {
                            return documentId.removePrefix("raw:")
                        }
                    }

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
                if (actualPath.startsWith("content://")) {
                    Log.w(TAG, "Skipping MediaScanner for content uri path: $actualPath")
                    return
                }

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
