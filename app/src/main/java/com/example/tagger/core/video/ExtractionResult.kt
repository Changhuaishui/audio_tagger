package com.example.tagger.core.video

import android.net.Uri

/**
 * Result of audio extraction operation.
 */
sealed class ExtractionResult {
    /** Extraction succeeded */
    data class Success(
        /** URI of the extracted audio file */
        val audioUri: Uri,

        /** File path of the extracted audio file */
        val filePath: String,

        /** Display name of the extracted audio file */
        val displayName: String,

        /** File size in bytes */
        val fileSize: Long
    ) : ExtractionResult()

    /** Extraction failed */
    data class Error(
        /** Error message */
        val message: String,

        /** Optional error code from FFmpeg */
        val errorCode: Int? = null
    ) : ExtractionResult()

    /** Video has no audio track */
    object NoAudioTrack : ExtractionResult()
}

/**
 * Progress update during extraction.
 */
data class ExtractionProgress(
    /** Progress percentage (0-100) */
    val percent: Int,

    /** Current processing time position in milliseconds */
    val timeMs: Long,

    /** Total duration in milliseconds */
    val totalMs: Long,

    /** Processing speed (e.g., "2.5x") */
    val speed: String?
) {
    /** Formatted progress text */
    val progressText: String
        get() = "${percent}%"

    /** Formatted time progress */
    val timeText: String
        get() {
            val currentSec = timeMs / 1000
            val totalSec = totalMs / 1000
            return "${formatTime(currentSec)} / ${formatTime(totalSec)}"
        }

    private fun formatTime(seconds: Long): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format("%d:%02d", mins, secs)
    }
}

/**
 * Overall extraction state for UI.
 */
sealed class ExtractionState {
    /** Idle - no extraction in progress */
    object Idle : ExtractionState()

    /** Analyzing video file */
    object Analyzing : ExtractionState()

    /** Ready to extract - video analyzed, waiting for user to start */
    data class Ready(val metadata: VideoMetadata) : ExtractionState()

    /** Extraction in progress */
    data class Extracting(val progress: ExtractionProgress) : ExtractionState()

    /** Extraction completed successfully */
    data class Completed(val result: ExtractionResult.Success) : ExtractionState()

    /** Extraction failed */
    data class Failed(val error: String) : ExtractionState()
}
