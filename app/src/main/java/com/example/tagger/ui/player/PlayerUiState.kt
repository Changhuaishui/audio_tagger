package com.example.tagger.ui.player

import com.example.tagger.model.AudioMetadata

/**
 * 播放器 UI 状态
 */
data class PlayerUiState(
    val isPlaying: Boolean = false,
    val currentItem: AudioMetadata? = null,
    val playlist: List<AudioMetadata> = emptyList(),
    val currentIndex: Int = 0,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val isBuffering: Boolean = false,
    val errorMessage: String? = null
)
