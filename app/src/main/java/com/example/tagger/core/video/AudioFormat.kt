package com.example.tagger.core.video

/**
 * Supported audio output formats for video extraction.
 */
enum class AudioFormat(
    val extension: String,
    val displayName: String,
    val codec: String?,
    val mimeType: String
) {
    /** Keep original format - direct stream copy (fastest) */
    ORIGINAL("", "保留原格式", null, "audio/*"),

    /** MP3 - Most compatible */
    MP3("mp3", "MP3", "libmp3lame", "audio/mpeg"),

    /** FLAC - Lossless */
    FLAC("flac", "FLAC", "flac", "audio/flac"),

    /** AAC - High quality, good compression */
    AAC("m4a", "AAC", "aac", "audio/mp4"),

    /** WAV - Uncompressed */
    WAV("wav", "WAV", "pcm_s16le", "audio/wav"),

    /** OGG Vorbis */
    OGG("ogg", "OGG", "libvorbis", "audio/ogg");

    companion object {
        /**
         * Get format from original audio codec name.
         * Used to determine the extension when keeping original format.
         */
        fun fromCodecName(codecName: String?): AudioFormat {
            return when {
                codecName == null -> MP3
                codecName.contains("mp3", ignoreCase = true) -> MP3
                codecName.contains("aac", ignoreCase = true) -> AAC
                codecName.contains("flac", ignoreCase = true) -> FLAC
                codecName.contains("vorbis", ignoreCase = true) -> OGG
                codecName.contains("opus", ignoreCase = true) -> OGG
                codecName.contains("pcm", ignoreCase = true) -> WAV
                else -> MP3
            }
        }
    }
}
