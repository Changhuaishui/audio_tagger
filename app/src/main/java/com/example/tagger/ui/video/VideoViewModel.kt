package com.example.tagger.ui.video

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tagger.core.video.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for video audio extraction feature.
 */
class VideoViewModel(application: Application) : AndroidViewModel(application) {

    private val extractor = VideoExtractor(application)

    private val _uiState = MutableStateFlow(VideoUiState())

    // 使用 SharedFlow 替代 LocalBroadcastManager 发送事件
    private val _audioExtractedEvent = MutableSharedFlow<AudioExtractedEvent>()
    /** 音频提取事件流，外部可订阅 */
    val audioExtractedEvent: SharedFlow<AudioExtractedEvent> = _audioExtractedEvent.asSharedFlow()
    val uiState: StateFlow<VideoUiState> = _uiState.asStateFlow()

    /**
     * Start analyzing a video file.
     */
    fun analyzeVideo(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                extractionState = ExtractionState.Analyzing,
                selectedVideoUri = uri
            )

            val metadata = extractor.analyzeVideo(uri)
            if (metadata != null) {
                _uiState.value = _uiState.value.copy(
                    extractionState = ExtractionState.Ready(metadata),
                    videoMetadata = metadata,
                    showFormatSelector = true
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    extractionState = ExtractionState.Failed("无法分析视频文件"),
                    message = "无法分析视频文件"
                )
            }
        }
    }

    /**
     * Start audio extraction with the given config.
     */
    fun startExtraction(config: ExtractionConfig = ExtractionConfig.DEFAULT) {
        val uri = _uiState.value.selectedVideoUri ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                showFormatSelector = false,
                showProgressDialog = true,
                extractionConfig = config
            )

            extractor.extractAudio(uri, config).collect { state ->
                _uiState.value = _uiState.value.copy(extractionState = state)

                when (state) {
                    is ExtractionState.Completed -> {
                        _uiState.value = _uiState.value.copy(
                            extractedResult = state.result,
                            message = "提取成功: ${state.result.displayName}"
                        )
                        // 发送事件通知音频已提取
                        emitAudioExtracted(state.result, uri)
                    }
                    is ExtractionState.Failed -> {
                        _uiState.value = _uiState.value.copy(
                            message = state.error
                        )
                    }
                    else -> {}
                }
            }
        }
    }

    /**
     * Update selected format.
     */
    fun selectFormat(format: AudioFormat) {
        _uiState.value = _uiState.value.copy(
            extractionConfig = _uiState.value.extractionConfig.copy(format = format)
        )
    }

    /**
     * Update selected audio track.
     */
    fun selectTrack(trackIndex: Int) {
        _uiState.value = _uiState.value.copy(
            extractionConfig = _uiState.value.extractionConfig.copy(trackIndex = trackIndex)
        )
    }

    /**
     * Dismiss format selector.
     */
    fun dismissFormatSelector() {
        _uiState.value = _uiState.value.copy(
            showFormatSelector = false,
            extractionState = ExtractionState.Idle
        )
    }

    /**
     * Dismiss progress dialog.
     */
    fun dismissProgressDialog() {
        _uiState.value = _uiState.value.copy(
            showProgressDialog = false
        )
    }

    /**
     * Clear the extracted result after it's been handled.
     */
    fun clearExtractedResult() {
        _uiState.value = _uiState.value.copy(
            extractedResult = null,
            extractionState = ExtractionState.Idle,
            showProgressDialog = false
        )
    }

    /**
     * Clear message after it's been shown.
     */
    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    /**
     * 发送音频提取成功的事件
     */
    private fun emitAudioExtracted(result: ExtractionResult.Success, sourceVideoUri: Uri) {
        viewModelScope.launch {
            _audioExtractedEvent.emit(
                AudioExtractedEvent(
                    filePath = result.filePath,
                    audioUri = result.audioUri,
                    sourceVideoUri = sourceVideoUri
                )
            )
        }
    }

    /**
     * Reset state for a new video.
     */
    fun reset() {
        _uiState.value = VideoUiState()
    }
}

/**
 * UI state for video extraction feature.
 */
data class VideoUiState(
    val selectedVideoUri: Uri? = null,
    val videoMetadata: VideoMetadata? = null,
    val extractionState: ExtractionState = ExtractionState.Idle,
    val extractionConfig: ExtractionConfig = ExtractionConfig.DEFAULT,
    val showFormatSelector: Boolean = false,
    val showProgressDialog: Boolean = false,
    val extractedResult: ExtractionResult.Success? = null,
    val message: String? = null
)

/**
 * 音频提取事件（替代 LocalBroadcastManager）
 */
data class AudioExtractedEvent(
    val filePath: String,
    val audioUri: Uri,
    val sourceVideoUri: Uri
)
