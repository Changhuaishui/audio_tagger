package com.example.tagger

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
        const val VERSION_TAG = "v0129d_open_with_external"
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
                        onFixExtension = { viewModel.fixFileExtension(it) },
                        // 雷达扫描
                        onShowRadarScan = { checkPermissionAndShowRadarScan() },
                        onStartRadarScan = { viewModel.startRadarScan() },
                        onToggleScannedSelection = { viewModel.toggleScannedItemSelection(it) },
                        onSelectAllScanned = { viewModel.selectAllScannedItems() },
                        onDeselectAllScanned = { viewModel.deselectAllScannedItems() },
                        onImportScanned = { viewModel.importSelectedScannedItems() },
                        onDismissRadarDialog = { viewModel.dismissRadarDialog() },
                        onToggleScanPath = { viewModel.toggleScanPath(it) },
                        onToggleAllScanPaths = { viewModel.toggleAllScanPaths() },
                        // 处理方案
                        onShowProcessScheme = { viewModel.showProcessSchemeDialog() },
                        onDismissProcessScheme = { viewModel.dismissProcessSchemeDialog() },
                        onSetUseReplacement = { viewModel.setUseReplacement(it) },
                        onSetUseObfuscation = { viewModel.setUseObfuscation(it) },
                        onSetSaveMapping = { viewModel.setSaveMapping(it) },
                        onShowReplacementRules = { viewModel.showReplacementRulesSheet() },
                        onDismissReplacementRules = { viewModel.dismissReplacementRulesSheet() },
                        onShowObfuscationMode = { viewModel.showObfuscationModeSheet() },
                        onDismissObfuscationMode = { viewModel.dismissObfuscationModeSheet() },
                        onSelectObfuscationMode = { viewModel.selectObfuscationMode(it) },
                        onAddReplacementRule = { find, replace -> viewModel.addReplacementRule(find, replace) },
                        onDeleteReplacementRule = { viewModel.deleteReplacementRule(it) },
                        onToggleReplacementRule = { viewModel.toggleReplacementRule(it) },
                        onExecuteProcessScheme = { viewModel.executeProcessScheme() },
                        onOpenWithExternalApp = {
                            val selectedItems = uiState.audioList.filter { it.uri in uiState.selectedUris }
                            selectedItems.forEach { openWithExternalApp(it.uri) }
                        }
                    )
                }
            }
        }
    }

    private val radarPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.showRadarScanDialog()
        } else {
            Toast.makeText(this, "需要音频读取权限才能扫描", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissionAndShowRadarScan() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                viewModel.showRadarScanDialog()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_AUDIO) -> {
                Toast.makeText(this, "需要音频读取权限来扫描设备上的音频文件", Toast.LENGTH_LONG).show()
                radarPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
            }
            else -> {
                radarPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
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

    private fun openWithExternalApp(uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, contentResolver.getType(uri) ?: "audio/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(Intent.createChooser(intent, "选择应用打开"))
            } else {
                Toast.makeText(this, "未找到可打开音频文件的应用，请先安装网易云音乐", Toast.LENGTH_LONG).show()
            }
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "未找到可打开音频文件的应用，请先安装网易云音乐", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开文件: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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
