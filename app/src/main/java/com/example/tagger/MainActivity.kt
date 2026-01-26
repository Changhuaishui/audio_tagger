package com.example.tagger

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.tagger.ui.MainScreen
import com.example.tagger.ui.MainViewModel
import com.example.tagger.ui.theme.AudioTaggerTheme
import com.example.tagger.ui.video.VideoViewModel

class MainActivity : ComponentActivity() {

    companion object {
        // 版本标记 - 用于验证新版本正在运行
        const val VERSION_TAG = "v0125a_ffmpegkit_migration"
        private const val TAG = "MainActivity"
    }

    private val viewModel: MainViewModel by viewModels()
    private val videoViewModel: VideoViewModel by viewModels()

    // 待执行的操作类型
    private var pendingPickerType: PickerType? = null

    private enum class PickerType {
        AUDIO, VIDEO
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchAudioFilePicker()
        } else {
            Toast.makeText(this, "需要音频读取权限", Toast.LENGTH_SHORT).show()
        }
    }

    private val videoPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchVideoFilePicker()
        } else {
            Toast.makeText(this, "需要视频读取权限", Toast.LENGTH_SHORT).show()
        }
    }

    private val audioFilePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: Exception) {}
            }
            viewModel.loadAudioFiles(uris)
        }
    }

    private val videoFilePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            videoViewModel.analyzeVideo(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 版本验证日志
        Log.d(TAG, "========== 版本检查 ==========")
        Log.d(TAG, "当前版本: $VERSION_TAG")
        Log.d(TAG, "功能: 视频音轨提取")
        Log.d(TAG, "===============================")

        setContent {
            AudioTaggerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by viewModel.uiState.collectAsState()
                    val videoUiState by videoViewModel.uiState.collectAsState()

                    MainScreen(
                        uiState = uiState,
                        videoUiState = videoUiState,
                        onPickFiles = { checkAudioPermissionAndPick() },
                        onPickVideo = { checkVideoPermissionAndPick() },
                        onSelectItem = { viewModel.selectItem(it) },
                        onSaveItem = { viewModel.saveItem(it) },
                        onRemoveItem = { viewModel.removeItem(it) },
                        onBatchFill = { viewModel.batchFillFromFileName() },
                        onBatchSave = { viewModel.batchSaveAll() },
                        onClearAll = { viewModel.clearAll() },
                        onClearMessage = { viewModel.clearMessage() },
                        // 敏感词检测
                        onBatchCheckSensitive = { viewModel.batchCheckSensitive() },
                        onCheckSensitive = { viewModel.checkSensitiveWord(it) },
                        onCloseSensitiveCheck = { viewModel.closeSensitiveCheck() },
                        // 封面选择
                        onPickCover = { viewModel.updateSelectedItemCover(it) },
                        // 多选模式
                        onToggleSelectionMode = { viewModel.toggleSelectionMode() },
                        onToggleItemSelection = { viewModel.toggleItemSelection(it) },
                        onToggleSelectAll = { viewModel.toggleSelectAll() },
                        onRemoveSelected = { viewModel.removeSelectedItems() },
                        onOptimizeSelected = { viewModel.optimizeSelectedFileNames() },
                        // 视频提取
                        onSelectVideoFormat = { videoViewModel.selectFormat(it) },
                        onSelectVideoTrack = { videoViewModel.selectTrack(it) },
                        onStartExtraction = { videoViewModel.startExtraction(videoUiState.extractionConfig) },
                        onDismissFormatSelector = { videoViewModel.dismissFormatSelector() },
                        onDismissProgressDialog = { videoViewModel.dismissProgressDialog() },
                        onImportExtractedAudio = {
                            videoUiState.extractedResult?.let { result ->
                                val videoMeta = videoUiState.videoMetadata
                                if (videoMeta != null) {
                                    // 使用视频信息（标题和缩略图）填充音频标签
                                    viewModel.loadAudioFileWithVideoMetadata(
                                        audioUri = result.audioUri,
                                        videoTitle = videoMeta.title,
                                        thumbnail = videoMeta.thumbnail,
                                        thumbnailBytes = videoMeta.thumbnailBytes
                                    )
                                } else {
                                    // 如果没有视频元数据，直接加载
                                    viewModel.loadAudioFiles(listOf(result.audioUri))
                                }
                                videoViewModel.clearExtractedResult()
                            }
                        },
                        onClearVideoMessage = { videoViewModel.clearMessage() },
                        onRunVideoDiagnostic = { videoViewModel.runDiagnostic() },
                        // 修复扩展名
                        onFixExtension = { viewModel.fixFileExtension(it) }
                    )
                }
            }
        }
    }

    private fun checkAudioPermissionAndPick() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                launchAudioFilePicker()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_AUDIO) -> {
                Toast.makeText(this, "需要音频读取权限来编辑标签", Toast.LENGTH_LONG).show()
                audioPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
            }
            else -> {
                audioPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
            }
        }
    }

    private fun checkVideoPermissionAndPick() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED -> {
                launchVideoFilePicker()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_VIDEO) -> {
                Toast.makeText(this, "需要视频读取权限来提取音轨", Toast.LENGTH_LONG).show()
                videoPermissionLauncher.launch(Manifest.permission.READ_MEDIA_VIDEO)
            }
            else -> {
                videoPermissionLauncher.launch(Manifest.permission.READ_MEDIA_VIDEO)
            }
        }
    }

    private fun launchAudioFilePicker() {
        audioFilePickerLauncher.launch(
            arrayOf(
                "audio/mpeg",
                "audio/mp4",
                "audio/flac",
                "audio/ogg",
                "audio/x-wav",
                "audio/wav",
                "audio/*"
            )
        )
    }

    private fun launchVideoFilePicker() {
        videoFilePickerLauncher.launch(
            arrayOf(
                "video/mp4",
                "video/x-matroska",
                "video/webm",
                "video/avi",
                "video/quicktime",
                "video/*"
            )
        )
    }
}
