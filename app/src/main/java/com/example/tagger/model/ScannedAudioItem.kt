package com.example.tagger.model

import android.net.Uri

/**
 * 雷达扫描结果项 - 表示在设备上发现的音频文件
 */
data class ScannedAudioItem(
    /** MediaStore content:// URI */
    val uri: Uri,
    /** 文件显示名称 */
    val displayName: String,
    /** 艺术家 (来自 MediaStore) */
    val artist: String?,
    /** 专辑 (来自 MediaStore) */
    val album: String?,
    /** 时长 (毫秒) */
    val duration: Long,
    /** 文件大小 (字节) */
    val size: Long,
    /** 文件路径 (用于去重检查) */
    val path: String?
) {
    /** 格式化的时长 (mm:ss) */
    val formattedDuration: String
        get() {
            val totalSeconds = duration / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }

    /** 格式化的文件大小 */
    val formattedSize: String
        get() {
            return when {
                size < 1024 -> "$size B"
                size < 1024 * 1024 -> "%.1f KB".format(size / 1024.0)
                else -> "%.1f MB".format(size / (1024.0 * 1024.0))
            }
        }

    /** 显示用的艺术家信息 */
    val displayArtist: String
        get() = artist?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "未知艺术家"
}
