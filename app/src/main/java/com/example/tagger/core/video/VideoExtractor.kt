package com.example.tagger.core.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.net.toUri
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.FFmpegKitConfig
import com.antonkarpenko.ffmpegkit.FFprobeKit
import com.antonkarpenko.ffmpegkit.ReturnCode
import com.example.tagger.core.MediaScannerUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Core class for extracting audio tracks from video files using FFmpegKit.
 *
 * Uses com.antonkarpenko:ffmpeg-kit-full-gpl which includes all encoders
 * (AAC, libmp3lame, FLAC, PCM, libvorbis, libopus, etc.)
 */
class VideoExtractor(private val context: Context) {

    companion object {
        private const val TAG = "VideoExtractor"
    }

    /**
     * Run diagnostic to check FFmpeg capabilities.
     * Call this to debug encoder availability issues.
     */
    suspend fun runDiagnostic(): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        sb.appendLine("=== FFmpeg 诊断报告 ===")
        sb.appendLine("设备架构: ${android.os.Build.SUPPORTED_ABIS.joinToString()}")

        try {
            // FFmpegKit auto-initializes, query version directly
            val versionSession = FFprobeKit.execute("-version")
            val versionOutput = versionSession.output ?: "无法获取版本信息"
            sb.appendLine("FFmpeg 版本: ${versionOutput.lines().firstOrNull()}")

            // Check encoders
            val encoderSession = FFmpegKit.execute("-hide_banner -encoders")
            val encoderOutput = encoderSession.output ?: ""

            val encodersToCheck = listOf(
                "aac" to "AAC",
                "libmp3lame" to "MP3 (libmp3lame)",
                "flac" to "FLAC",
                "pcm_s16le" to "WAV (PCM)",
                "libvorbis" to "OGG (libvorbis)",
                "libopus" to "Opus"
            )

            sb.appendLine("\n编码器检测:")
            for ((encoder, name) in encodersToCheck) {
                val available = encoderOutput.contains(encoder)
                val status = if (available) "✅" else "❌"
                sb.appendLine("  $status $name ($encoder)")
                Log.i(TAG, "Encoder test: $name ($encoder) = $available")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Diagnostic error", e)
            sb.appendLine("FFmpeg 状态: ❌ 诊断失败: ${e.message}")
        }

        val result = sb.toString()
        Log.i(TAG, result)
        result
    }

    /**
     * Analyze a video file and return its metadata.
     */
    suspend fun analyzeVideo(uri: Uri): VideoMetadata? = withContext(Dispatchers.IO) {
        try {
            val displayName = getDisplayName(uri) ?: "video"
            val durationMs = getDurationFromRetriever(uri) ?: 0L

            val fileSize = try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use {
                    it.statSize
                } ?: 0L
            } catch (e: Exception) {
                0L
            }

            val containerFormat = displayName.substringAfterLast(".", "mp4")

            // 获取缩略图和音轨信息
            var thumbnail: Bitmap? = null
            var thumbnailBytes: ByteArray? = null
            var audioTracks: List<AudioTrackInfo> = emptyList()

            val hasAudio = hasAudioTrack(uri)

            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)

                // 1. 先尝试获取嵌入封面
                val embeddedPicture = retriever.embeddedPicture
                if (embeddedPicture != null) {
                    thumbnail = BitmapFactory.decodeByteArray(embeddedPicture, 0, embeddedPicture.size)
                    thumbnailBytes = embeddedPicture
                    Log.d(TAG, "Got embedded thumbnail: ${thumbnail?.width}x${thumbnail?.height}")
                }

                // 2. 如果没有嵌入封面，截取视频帧（1秒处，避免黑屏开场）
                if (thumbnail == null) {
                    val frameTimeUs = minOf(1_000_000L, durationMs * 1000 / 2) // 1秒或视频中点
                    thumbnail = retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    if (thumbnail != null) {
                        // 将 Bitmap 转换为 ByteArray
                        thumbnailBytes = bitmapToBytes(thumbnail)
                        Log.d(TAG, "Got frame thumbnail: ${thumbnail.width}x${thumbnail.height}")
                    }
                }

                // 获取音轨信息
                if (hasAudio) {
                    val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "audio/aac"
                    val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()?.let { it / 1000 }
                    val sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toIntOrNull() ?: 44100

                    val codec = when {
                        mimeType.contains("aac", ignoreCase = true) -> "aac"
                        mimeType.contains("mp3", ignoreCase = true) -> "mp3"
                        mimeType.contains("opus", ignoreCase = true) -> "opus"
                        mimeType.contains("vorbis", ignoreCase = true) -> "vorbis"
                        mimeType.contains("flac", ignoreCase = true) -> "flac"
                        else -> "aac"
                    }

                    audioTracks = listOf(
                        AudioTrackInfo(
                            index = 0,
                            codec = codec,
                            channels = 2,
                            sampleRate = sampleRate,
                            bitrate = bitrate,
                            language = null
                        )
                    )
                }

                retriever.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting thumbnail or audio info", e)
            }

            VideoMetadata(
                uri = uri,
                displayName = displayName,
                durationMs = durationMs,
                fileSize = fileSize,
                containerFormat = containerFormat,
                audioTracks = audioTracks,
                thumbnail = thumbnail,
                thumbnailBytes = thumbnailBytes
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing video", e)
            null
        }
    }

    /**
     * Convert Bitmap to ByteArray (JPEG format).
     */
    private fun bitmapToBytes(bitmap: Bitmap, quality: Int = 85): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }

    /**
     * Extract audio from video with progress updates.
     */
    fun extractAudio(
        uri: Uri,
        config: ExtractionConfig = ExtractionConfig.DEFAULT
    ): Flow<ExtractionState> = callbackFlow {
        trySend(ExtractionState.Analyzing)

        withContext(Dispatchers.IO) {
            try {
                val metadata = analyzeVideo(uri)
                if (metadata == null) {
                    trySend(ExtractionState.Failed("无法分析视频文件"))
                    close()
                    return@withContext
                }

                if (!metadata.hasAudio) {
                    trySend(ExtractionState.Failed("视频没有音轨"))
                    close()
                    return@withContext
                }

                trySend(ExtractionState.Ready(metadata))

                val inputFile = copyUriToTemp(uri, "video_input")
                if (inputFile == null) {
                    trySend(ExtractionState.Failed("无法读取视频文件"))
                    close()
                    return@withContext
                }

                try {
                    val targetFormat = if (config.format == AudioFormat.ORIGINAL) {
                        val codecName = metadata.audioTracks.getOrNull(config.trackIndex)?.codec
                        AudioFormat.fromCodecName(codecName)
                    } else {
                        config.format
                    }

                    val baseName = metadata.displayName.substringBeforeLast(".")
                    val outputFileName = "${baseName}.${targetFormat.extension}"
                    val outputDir = File(context.cacheDir, "extracted_audio")
                    outputDir.mkdirs()
                    val outputFile = File(outputDir, outputFileName)

                    val command = buildFFmpegCommand(
                        inputFile.absolutePath,
                        outputFile.absolutePath,
                        config,
                        targetFormat,
                        title = baseName  // 使用视频文件名作为标题
                    )

                    Log.i(TAG, "=== FFmpeg Extraction Start ===")
                    Log.i(TAG, "Input: ${inputFile.absolutePath}")
                    Log.i(TAG, "Output: ${outputFile.absolutePath}")
                    Log.i(TAG, "Format: ${targetFormat.displayName} (codec: ${targetFormat.codec})")
                    Log.i(TAG, "Command: $command")

                    val totalDuration = metadata.durationMs

                    // Set up statistics callback for progress updates
                    FFmpegKitConfig.enableStatisticsCallback { statistics ->
                        val timeMs = statistics.time.toLong()
                        val percent = if (totalDuration > 0) {
                            ((timeMs.toFloat() / totalDuration) * 100).toInt().coerceIn(0, 100)
                        } else 0
                        trySend(
                            ExtractionState.Extracting(
                                ExtractionProgress(
                                    percent = percent,
                                    timeMs = timeMs,
                                    totalMs = totalDuration,
                                    speed = null
                                )
                            )
                        )
                    }

                    // Set up log callback
                    FFmpegKitConfig.enableLogCallback { log ->
                        Log.d(TAG, "FFmpeg: ${log.message}")
                    }

                    // Execute synchronously (we're already on Dispatchers.IO)
                    val session = FFmpegKit.execute(command)
                    val returnCode = session.returnCode

                    if (ReturnCode.isSuccess(returnCode)) {
                        Log.d(TAG, "FFmpeg extraction succeeded")

                        val finalDir = getPublicMusicDirectory()
                        if (finalDir == null) {
                            trySend(ExtractionState.Failed("无法访问公共音乐目录，请检查存储权限"))
                            inputFile.delete()
                            close()
                            return@withContext
                        }
                        finalDir.mkdirs()
                        val finalFile = File(finalDir, outputFileName)

                        var targetFile = finalFile
                        var counter = 1
                        while (targetFile.exists()) {
                            val newName = "${baseName}_${counter}.${targetFormat.extension}"
                            targetFile = File(finalDir, newName)
                            counter++
                        }

                        try {
                            outputFile.copyTo(targetFile, overwrite = true)
                            outputFile.delete()

                            MediaScannerUtil.broadcastScan(context, targetFile)
                            Log.d(TAG, "File saved to public directory: ${targetFile.absolutePath}")

                            val result = ExtractionResult.Success(
                                audioUri = targetFile.toUri(),
                                filePath = targetFile.absolutePath,
                                displayName = targetFile.name,
                                fileSize = targetFile.length()
                            )
                            trySend(ExtractionState.Completed(result))
                        } catch (e: Exception) {
                            trySend(ExtractionState.Failed("保存文件失败: ${e.message}"))
                        }
                    } else if (ReturnCode.isCancel(returnCode)) {
                        trySend(ExtractionState.Failed("提取已取消"))
                    } else {
                        val errorLog = session.allLogsAsString ?: "未知错误"
                        Log.e(TAG, "=== FFmpeg Extraction Failed ===")
                        Log.e(TAG, "FFmpeg failed: $errorLog")
                        val userMessage = parseFFmpegError(errorLog, targetFormat)
                        trySend(ExtractionState.Failed(userMessage))
                    }

                    // Clean up temp input file
                    inputFile.delete()
                    Log.i(TAG, "=== FFmpeg Extraction Finished ===")

                } catch (e: Exception) {
                    Log.e(TAG, "Extraction error", e)
                    inputFile.delete()
                    trySend(ExtractionState.Failed("提取出错: ${e.message}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Extraction error", e)
                trySend(ExtractionState.Failed("提取出错: ${e.message}"))
            }
        }

        awaitClose { }
    }

    /**
     * Parse FFmpeg error and provide user-friendly message.
     */
    private fun parseFFmpegError(error: String, targetFormat: AudioFormat): String {
        return when {
            error.contains("Encoder", ignoreCase = true) ||
            error.contains("encoder", ignoreCase = true) ||
            error.contains("Unknown encoder", ignoreCase = true) ||
            error.contains("libmp3lame", ignoreCase = true) ||
            error.contains("libvorbis", ignoreCase = true) -> {
                when (targetFormat) {
                    AudioFormat.MP3 -> "MP3 编码器 (libmp3lame) 不可用。\n\n" +
                        "解决方案：\n" +
                        "• 选择 AAC 或 FLAC 格式（推荐）\n" +
                        "• 或选择「保留原格式」直接复制音轨"
                    AudioFormat.OGG -> "OGG 编码器 (libvorbis) 不可用。\n\n" +
                        "解决方案：\n" +
                        "• 选择 AAC 或 FLAC 格式（推荐）\n" +
                        "• 或选择「保留原格式」直接复制音轨"
                    else -> "编码器不可用，请尝试 AAC 或 FLAC 格式"
                }
            }
            error.contains("No such file", ignoreCase = true) -> "文件访问失败，请检查存储权限"
            error.contains("Permission denied", ignoreCase = true) -> "权限不足，请检查存储权限"
            error.contains("Invalid data", ignoreCase = true) -> "视频文件格式不支持或已损坏"
            error.contains("Conversion failed", ignoreCase = true) -> "转换失败，请尝试其他格式"
            else -> {
                val shortError = if (error.length > 200) error.takeLast(200) else error
                "提取失败: $shortError"
            }
        }
    }

    private fun buildFFmpegCommand(
        inputPath: String,
        outputPath: String,
        config: ExtractionConfig,
        targetFormat: AudioFormat,
        title: String? = null
    ): String {
        val sb = StringBuilder()

        // Input file (keep quotes for paths with spaces)
        sb.append("-i \"$inputPath\" ")

        // Select audio track (only if not the first track, simpler command for track 0)
        if (config.trackIndex > 0) {
            sb.append("-map 0:a:${config.trackIndex} ")
        }

        // Codec settings
        if (config.format == AudioFormat.ORIGINAL) {
            sb.append("-c:a copy ")
        } else {
            targetFormat.codec?.let { codec ->
                sb.append("-c:a $codec ")
            }

            // Use constant bitrate for better compatibility
            when (targetFormat) {
                AudioFormat.MP3 -> sb.append("-b:a 192k ")
                AudioFormat.AAC -> sb.append("-b:a 192k ")
                AudioFormat.OGG -> sb.append("-b:a 192k ")
                AudioFormat.FLAC -> sb.append("-compression_level 5 ")
                else -> {}
            }
        }

        // Write metadata (title) directly during extraction
        // This is more reliable than JAudioTagger for M4A/AAC files
        if (!title.isNullOrBlank()) {
            // Escape special characters in title for FFmpeg
            val escapedTitle = title
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("'", "'\\''")
            sb.append("-metadata title=\"$escapedTitle\" ")
        }

        // Disable video stream
        sb.append("-vn ")

        // Overwrite output without asking
        sb.append("-y ")

        // Output file (keep quotes for paths with spaces)
        sb.append("\"$outputPath\"")

        return sb.toString()
    }

    private fun copyUriToTemp(uri: Uri, prefix: String): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val displayName = getDisplayName(uri) ?: "temp"
            val extension = displayName.substringAfterLast(".", "mp4")
            val tempFile = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.$extension")

            inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy URI to temp", e)
            null
        }
    }

    private fun getDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    cursor.getString(nameIndex)
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get display name", e)
            null
        }
    }

    private fun getDurationFromRetriever(uri: Uri): Long? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            durationStr?.toLongOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get duration from retriever", e)
            null
        }
    }

    private fun hasAudioTrack(uri: Uri): Boolean {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            retriever.release()
            hasAudio == "yes"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check audio track", e)
            false
        }
    }

    private fun getPublicMusicDirectory(): File? {
        return try {
            if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) {
                Log.e(TAG, "External storage is not mounted")
                return null
            }

            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val appMusicDir = File(musicDir, "AudioTagger")

            if (appMusicDir.exists() || appMusicDir.mkdirs()) {
                Log.d(TAG, "Using Music directory: ${appMusicDir.absolutePath}")
                return appMusicDir
            }

            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val appDownloadDir = File(downloadDir, "AudioTagger")

            if (appDownloadDir.exists() || appDownloadDir.mkdirs()) {
                Log.d(TAG, "Using Downloads directory: ${appDownloadDir.absolutePath}")
                return appDownloadDir
            }

            Log.e(TAG, "Could not create output directory")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get public directory", e)
            null
        }
    }
}
