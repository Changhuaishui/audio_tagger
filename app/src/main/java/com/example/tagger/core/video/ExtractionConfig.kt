package com.example.tagger.core.video

/**
 * Configuration for audio extraction from video.
 */
data class ExtractionConfig(
    /** Target audio format. Use ORIGINAL to keep the source format. */
    val format: AudioFormat = AudioFormat.ORIGINAL,

    /** Audio track index to extract (0-based). Default is the first audio track. */
    val trackIndex: Int = 0
) {
    companion object {
        /** Default config - keep original format, first audio track */
        val DEFAULT = ExtractionConfig()
    }
}
