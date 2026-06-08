package com.example.tagger.ui.player

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.tagger.model.AudioMetadata
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "PlayerViewModel"

/**
 * 播放器 ViewModel：持有 ExoPlayer，管理播放状态和播放列表
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(application).build().apply {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        setAudioAttributes(audioAttributes, true)
    }

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val isBuffering = playbackState == Player.STATE_BUFFERING
                _uiState.value = _uiState.value.copy(
                    isBuffering = isBuffering,
                    duration = if (playbackState == Player.STATE_READY) exoPlayer.duration.coerceAtLeast(0L) else _uiState.value.duration
                )
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = exoPlayer.currentMediaItemIndex
                val item = _uiState.value.playlist.getOrNull(index)
                _uiState.value = _uiState.value.copy(
                    currentIndex = index,
                    currentItem = item,
                    currentPosition = 0L,
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                )
            }
        })

        // 启动进度更新协程
        viewModelScope.launch {
            while (isActive) {
                if (exoPlayer.isPlaying) {
                    _uiState.value = _uiState.value.copy(
                        currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L),
                        duration = exoPlayer.duration.coerceAtLeast(0L)
                    )
                }
                delay(500L)
            }
        }
    }

    /**
     * 设置播放列表并开始播放指定项
     */
    fun setPlaylistAndPlay(list: List<AudioMetadata>, item: AudioMetadata) {
        if (list.isEmpty()) return

        val startIndex = list.indexOfFirst { it.uri == item.uri }.coerceAtLeast(0)

        exoPlayer.stop()
        exoPlayer.clearMediaItems()

        list.forEach { metadata ->
            val mediaItem = MediaItem.Builder()
                .setUri(metadata.uri)
                .setMediaId(metadata.uri.toString())
                .build()
            exoPlayer.addMediaItem(mediaItem)
        }

        exoPlayer.prepare()
        exoPlayer.seekTo(startIndex, 0L)
        exoPlayer.play()

        _uiState.value = PlayerUiState(
            playlist = list,
            currentIndex = startIndex,
            currentItem = list.getOrNull(startIndex),
            isPlaying = true
        )

        Log.d(TAG, "设置播放列表: ${list.size} 首, 开始播放第 $startIndex 首")
    }

    /**
     * 切换播放/暂停
     */
    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            if (exoPlayer.playbackState == Player.STATE_IDLE) {
                exoPlayer.prepare()
            }
            exoPlayer.play()
        }
    }

    /**
     * 播放下一首
     */
    fun playNext() {
        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNext()
        } else {
            // 列表循环：回到第一首
            if (_uiState.value.playlist.isNotEmpty()) {
                exoPlayer.seekTo(0, 0L)
                exoPlayer.play()
            }
        }
    }

    /**
     * 播放上一首
     */
    fun playPrevious() {
        if (exoPlayer.hasPreviousMediaItem()) {
            exoPlayer.seekToPrevious()
        } else {
            // 列表循环：回到最后一首
            val lastIndex = (_uiState.value.playlist.size - 1).coerceAtLeast(0)
            exoPlayer.seekTo(lastIndex, 0L)
            exoPlayer.play()
        }
    }

    /**
     * 跳转到指定位置（毫秒）
     */
    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs.coerceAtLeast(0L))
        _uiState.value = _uiState.value.copy(currentPosition = positionMs.coerceAtLeast(0L))
    }

    override fun onCleared() {
        super.onCleared()
        exoPlayer.release()
        Log.d(TAG, "ExoPlayer 已释放")
    }
}
