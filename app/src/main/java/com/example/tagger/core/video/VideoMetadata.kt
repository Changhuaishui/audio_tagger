package com.example.tagger.core.video

import android.graphics.Bitmap
import android.net.Uri

/**
 * Metadata for a video file, including audio track information.
 */
data class VideoMetadata(
    /** Content URI of the video file */
    val uri: Uri,

    /** Display name of the video file */
    val displayName: String,

    /** Video duration in milliseconds */
    val durationMs: Long,

    /** Video file size in bytes */
    val fileSize: Long,

    /** Video container format (e.g., "mp4", "mkv") */
    val containerFormat: String,

    /** List of audio tracks in the video */
    val audioTracks: List<AudioTrackInfo>,

    /** Video thumbnail (embedded cover or extracted frame) */
    val thumbnail: Bitmap? = null,

    /** Thumbnail raw bytes for saving to audio file */
    val thumbnailBytes: ByteArray? = null
) {
    /** Video title (display name without extension) */
    val title: String
        get() = displayName.substringBeforeLast(".")

    /** Whether the video has a thumbnail */
    val hasThumbnail: Boolean
        get() = thumbnail != null

    /** Formatted duration string (HH:MM:SS or MM:SS) */
    val formattedDuration: String
        get() {
            val totalSeconds = durationMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%d:%02d", minutes, seconds)
            }
        }

    /** Formatted file size (KB, MB, GB) */
    val formattedFileSize: String
        get() {
            val kb = fileSize / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format("%.2f GB", gb)
                mb >= 1.0 -> String.format("%.1f MB", mb)
                else -> String.format("%.0f KB", kb)
            }
        }

    /** Whether the video has any audio tracks */
    val hasAudio: Boolean
        get() = audioTracks.isNotEmpty()

    /** Get the default (first) audio track, if any */
    val defaultAudioTrack: AudioTrackInfo?
        get() = audioTracks.firstOrNull()
}

/**
 * Information about a single audio track in a video.
 */
data class AudioTrackInfo(
    /** Track index (0-based) */
    val index: Int,

    /** Audio codec name (e.g., "aac", "mp3", "opus") */
    val codec: String,

    /** Number of audio channels (e.g., 2 for stereo) */
    val channels: Int,

    /** Sample rate in Hz (e.g., 44100, 48000) */
    val sampleRate: Int,

    /** Bitrate in kbps, if available */
    val bitrate: Int?,

    /** Language code (e.g., "eng", "chi"), if available */
    val language: String?
) {
    /** Formatted channel info */
    val channelInfo: String
        get() = when (channels) {
            1 -> "单声道"
            2 -> "立体声"
            6 -> "5.1声道"
            8 -> "7.1声道"
            else -> "${channels}声道"
        }

    /** Formatted sample rate */
    val formattedSampleRate: String
        get() = "${sampleRate / 1000.0}kHz"

    /** Formatted bitrate, or "VBR" if not available */
    val formattedBitrate: String
        get() = bitrate?.let { "${it}kbps" } ?: "VBR"

    /** Display name for the track */
    val displayName: String
        get() {
            val langPart = language?.let { " ($it)" } ?: ""
            return "音轨 ${index + 1}$langPart: ${codec.uppercase()} $channelInfo"
        }
}
