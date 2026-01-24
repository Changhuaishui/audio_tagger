package com.example.tagger.core.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.net.toUri
import com.example.tagger.core.MediaScannerUtil
import com.mzgs.ffmpegx.FFmpeg
import com.mzgs.ffmpegx.FFmpegHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Core class for extracting audio tracks from video files using FFmpegX-Android.
 *
 * Note: For full MP3 support (libmp3lame), switch to FFmpegKit full-gpl:
 * implementation("com.arthenica:ffmpeg-kit-full-gpl:6.0-2")
 */
class VideoExtractor(private val context: Context) {

    companion object {
        private const val TAG = "VideoExtractor"
    }

    private var ffmpeg: FFmpeg? = null

    private suspend fun ensureFFmpegInitialized(): FFmpegInitResult = withContext(Dispatchers.IO) {
        try {
            // Check if we're on a supported architecture first
            val supportedAbis = listOf("arm64-v8a", "armeabi-v7a")
            val deviceAbis = android.os.Build.SUPPORTED_ABIS.toList()
            val isArchitectureSupported = deviceAbis.any { it in supportedAbis }

            if (!isArchitectureSupported) {
                Log.e(TAG, "Unsupported architecture: ${deviceAbis.joinToString()}")
                return@withContext FFmpegInitResult.UnsupportedArchitecture(deviceAbis.firstOrNull() ?: "unknown")
            }

            if (ffmpeg == null) {
                ffmpeg = FFmpeg.initialize(context)
            }
            val ff = ffmpeg ?: return@withContext FFmpegInitResult.Error("FFmpeg 初始化失败")

            if (!ff.isInstalled()) {
                try {
                    ff.install()
                } catch (e: UnsatisfiedLinkError) {
                    Log.e(TAG, "FFmpeg native library load failed", e)
                    return@withContext FFmpegInitResult.UnsupportedArchitecture(deviceAbis.firstOrNull() ?: "unknown")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to install FFmpeg", e)
                    return@withContext FFmpegInitResult.Error("FFmpeg 安装失败: ${e.message}")
                }
            }
            FFmpegInitResult.Success
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Architecture not supported", e)
            FFmpegInitResult.UnsupportedArchitecture(android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
        } catch (e: Exception) {
            Log.e(TAG, "FFmpeg initialization error", e)
            FFmpegInitResult.Error("初始化错误: ${e.message}")
        }
    }

    private sealed class FFmpegInitResult {
        object Success : FFmpegInitResult()
        data class UnsupportedArchitecture(val arch: String) : FFmpegInitResult()
        data class Error(val message: String) : FFmpegInitResult()
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
                // Initialize FFmpeg
                when (val initResult = ensureFFmpegInitialized()) {
                    is FFmpegInitResult.Success -> { /* Continue */ }
                    is FFmpegInitResult.UnsupportedArchitecture -> {
                        val errorMsg = "此设备架构不支持视频提取功能 (${initResult.arch})。\n\n" +
                            "FFmpeg 库仅支持 ARM 设备。如果你在使用 x86 模拟器，请改用：\n" +
                            "• ARM64 模拟器\n" +
                            "• 真实的 Android 手机"
                        trySend(ExtractionState.Failed(errorMsg))
                        close()
                        return@withContext
                    }
                    is FFmpegInitResult.Error -> {
                        trySend(ExtractionState.Failed(initResult.message))
                        close()
                        return@withContext
                    }
                }

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
                        targetFormat
                    )

                    Log.d(TAG, "FFmpeg command: $command")

                    val totalDuration = metadata.durationMs
                    var extractionSucceeded = false

                    val callback = object : FFmpegHelper.FFmpegCallback {
                        override fun onStart() {
                            Log.d(TAG, "FFmpeg extraction started")
                        }

                        override fun onProgress(progress: Float, time: Long) {
                            val percent = if (totalDuration > 0) {
                                ((time.toFloat() / totalDuration) * 100).toInt().coerceIn(0, 100)
                            } else {
                                (progress * 100).toInt().coerceIn(0, 100)
                            }

                            trySend(
                                ExtractionState.Extracting(
                                    ExtractionProgress(
                                        percent = percent,
                                        timeMs = time,
                                        totalMs = totalDuration,
                                        speed = null
                                    )
                                )
                            )
                        }

                        override fun onSuccess(output: String?) {
                            Log.d(TAG, "FFmpeg extraction succeeded")
                            extractionSucceeded = true

                            val finalDir = getPublicMusicDirectory()
                            if (finalDir == null) {
                                trySend(ExtractionState.Failed("无法访问公共音乐目录，请检查存储权限"))
                                return
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
                        }

                        override fun onFailure(error: String) {
                            Log.e(TAG, "FFmpeg extraction failed: $error")
                            val userMessage = parseFFmpegError(error, targetFormat)
                            trySend(ExtractionState.Failed(userMessage))
                        }

                        override fun onFinish() {
                            Log.d(TAG, "FFmpeg extraction finished")
                            inputFile.delete()
                            if (!extractionSucceeded) {
                                close()
                            }
                        }

                        override fun onOutput(line: String) {
                            Log.v(TAG, "FFmpeg output: $line")
                        }
                    }

                    val success = ffmpeg?.execute(command, callback) ?: false
                    if (!success && !extractionSucceeded) {
                        trySend(ExtractionState.Failed("FFmpeg 执行失败"))
                    }

                } catch (e: UnsatisfiedLinkError) {
                    Log.e(TAG, "Architecture not supported", e)
                    val arch = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
                    trySend(ExtractionState.Failed("此设备架构不支持视频提取 ($arch)"))
                } catch (e: Exception) {
                    Log.e(TAG, "Extraction error", e)
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
            // Encoder not found - common issue with FFmpegX
            error.contains("Encoder", ignoreCase = true) ||
            error.contains("encoder", ignoreCase = true) ||
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
            error.contains("No such file", ignoreCase = true) -> {
                "文件访问失败，请检查存储权限"
            }
            error.contains("Permission denied", ignoreCase = true) -> {
                "权限不足，请检查存储权限"
            }
            error.contains("Invalid data", ignoreCase = true) -> {
                "视频文件格式不支持或已损坏"
            }
            else -> {
                val shortError = if (error.length > 150) error.take(150) + "..." else error
                "提取失败: $shortError\n\n建议尝试 AAC 或 FLAC 格式"
            }
        }
    }

    private fun buildFFmpegCommand(
        inputPath: String,
        outputPath: String,
        config: ExtractionConfig,
        targetFormat: AudioFormat
    ): String {
        val sb = StringBuilder()

        // Input file (quote path for spaces)
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

        // Disable video stream
        sb.append("-vn ")

        // Overwrite output without asking
        sb.append("-y ")

        // Output file (quote path for spaces)
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
