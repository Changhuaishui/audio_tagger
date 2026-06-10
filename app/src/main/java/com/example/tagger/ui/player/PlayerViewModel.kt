package com.example.tagger.ui.player

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
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
                    duration = exoPlayer.duration.coerceAtLeast(0L),
                    errorMessage = null
                )
            }

            override fun onPlayerErrorChanged(error: PlaybackException?) {
                val message = error?.let {
                    Log.e(TAG, "播放错误: ${it.errorCodeName} - ${it.message}")
                    resolveErrorMessage(it)
                }
                _uiState.value = _uiState.value.copy(errorMessage = message)
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _uiState.value = _uiState.value.copy(shuffleMode = shuffleModeEnabled)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _uiState.value = _uiState.value.copy(repeatMode = repeatMode)
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
     * 将 ExoPlayer 错误码转换为中文提示
     */
    private fun resolveErrorMessage(error: PlaybackException): String {
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "无法找到音频文件"
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "没有权限播放该文件"
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "网络连接失败"
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "解码器初始化失败"
            PlaybackException.ERROR_CODE_DECODING_FAILED -> "音频解码失败，格式可能不支持"
            PlaybackException.ERROR_CODE_TIMEOUT -> "播放超时，请重试"
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> "直播流已过期"
            else -> "播放失败: ${error.errorCodeName}"
        }
    }

    /**
     * 清除错误提示
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
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
            isPlaying = true,
            shuffleMode = exoPlayer.shuffleModeEnabled,
            repeatMode = exoPlayer.repeatMode
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

    /**
     * 跳转到播放列表指定项
     */
    fun seekToItem(index: Int) {
        if (index in _uiState.value.playlist.indices) {
            exoPlayer.seekTo(index, 0L)
            exoPlayer.play()
        }
    }

    /**
     * 切换随机播放
     */
    fun toggleShuffle() {
        exoPlayer.shuffleModeEnabled = !exoPlayer.shuffleModeEnabled
    }

    /**
     * 切换循环模式：OFF -> ALL -> ONE -> OFF
     */
    fun cycleRepeatMode() {
        val next = when (exoPlayer.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        exoPlayer.repeatMode = next
    }

    /**
     * 从播放队列移除某一项
     */
    fun removeFromPlaylist(index: Int) {
        if (index in _uiState.value.playlist.indices) {
            exoPlayer.removeMediaItem(index)
            val newPlaylist = _uiState.value.playlist.toMutableList().apply { removeAt(index) }
            _uiState.value = _uiState.value.copy(playlist = newPlaylist)
        }
    }

    override fun onCleared() {
        super.onCleared()
        exoPlayer.release()
        Log.d(TAG, "ExoPlayer 已释放")
    }
}
