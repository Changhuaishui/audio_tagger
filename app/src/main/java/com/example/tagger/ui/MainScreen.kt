package com.example.tagger.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.example.tagger.core.SensitiveCheckResult
import com.example.tagger.core.video.AudioFormat
import com.example.tagger.core.video.ExtractionState
import com.example.tagger.model.AudioMetadata
import com.example.tagger.model.ObfuscationMode
import com.example.tagger.model.ReplacementRule
import com.example.tagger.model.ScannedAudioItem
import com.example.tagger.ui.theme.AudioTaggerTheme
import com.example.tagger.ui.theme.AppPrimaryColor
import com.example.tagger.ui.theme.AppleGray1
import com.example.tagger.ui.theme.AppleGray5
import com.example.tagger.ui.theme.AppleGray6
import com.example.tagger.ui.player.FullPlayerScreen
import com.example.tagger.ui.player.MiniPlayer
import com.example.tagger.ui.player.PlayerUiState
import com.example.tagger.ui.player.PlaybackQueueSheet
import com.example.tagger.ui.video.ExtractionProgressDialog
import com.example.tagger.ui.video.FormatSelectorSheet
import com.example.tagger.ui.video.VideoUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: MainUiState,
    videoUiState: VideoUiState = VideoUiState(),
    playerState: PlayerUiState = PlayerUiState(),
    onPickFiles: () -> Unit,
    onPickVideo: () -> Unit = {},
    onSelectItem: (AudioMetadata?) -> Unit,
    onSaveItem: (AudioMetadata) -> Unit,
    onRemoveItem: (AudioMetadata) -> Unit,
    onBatchFill: () -> Unit,
    onBatchSave: () -> Unit,
    onClearAll: () -> Unit,
    onClearMessage: () -> Unit,
    onBatchCheckSensitive: () -> Unit = {},
    onCheckSensitive: (AudioMetadata) -> Unit = {},
    onCloseSensitiveCheck: () -> Unit = {},
    onPickCover: (Uri) -> Unit = {},
    // 多选模式
    onToggleSelectionMode: () -> Unit = {},
    onToggleItemSelection: (AudioMetadata) -> Unit = {},
    onToggleSelectAll: () -> Unit = {},
    onRemoveSelected: () -> Unit = {},
    onOptimizeSelected: () -> Unit = {},  // 优化选中文件的违禁词
    onRemoveSensitiveWords: () -> Unit = {},  // 删除选中文件名的违禁词
    onSmartReplace: () -> Unit = {},  // 智能替换选中文件名的违禁词
    onToggleSyncTitle: () -> Unit = {},  // 切换同步修改歌曲名开关
    onShowBatchCoverSheet: () -> Unit = {},  // 显示批量封面弹窗
    onDismissBatchCoverSheet: () -> Unit = {},  // 关闭批量封面弹窗
    onBatchApplyCover: (List<Uri>, com.example.tagger.model.CoverAssignmentMode) -> Unit = { _, _ -> },  // 批量添加封面
    // 视频提取
    onSelectVideoFormat: (AudioFormat) -> Unit = {},
    onSelectVideoTrack: (Int) -> Unit = {},
    onStartExtraction: () -> Unit = {},
    onDismissFormatSelector: () -> Unit = {},
    onDismissProgressDialog: () -> Unit = {},
    onImportExtractedAudio: () -> Unit = {},
    onClearVideoMessage: () -> Unit = {},
    onRunVideoDiagnostic: () -> Unit = {},
    // 修复扩展名
    onFixExtension: (AudioMetadata) -> Unit = {},
    onOpenSingleFile: (AudioMetadata) -> Unit = {},
    // 雷达扫描
    onShowRadarScan: () -> Unit = {},
    onStartRadarScan: () -> Unit = {},
    onToggleScannedSelection: (Uri) -> Unit = {},
    onSelectAllScanned: () -> Unit = {},
    onDeselectAllScanned: () -> Unit = {},
    onImportScanned: () -> Unit = {},
    onDismissRadarDialog: () -> Unit = {},
    onToggleScanPath: (String) -> Unit = {},
    onToggleAllScanPaths: () -> Unit = {},
    // 处理方案相关
    onShowProcessScheme: () -> Unit = {},
    onDismissProcessScheme: () -> Unit = {},
    onSetUseReplacement: (Boolean) -> Unit = {},
    onSetUseObfuscation: (Boolean) -> Unit = {},
    onSetSaveMapping: (Boolean) -> Unit = {},
    onShowReplacementRules: () -> Unit = {},
    onDismissReplacementRules: () -> Unit = {},
    onShowObfuscationMode: () -> Unit = {},
    onDismissObfuscationMode: () -> Unit = {},
    onSelectObfuscationMode: (ObfuscationMode) -> Unit = {},
    onAddReplacementRule: (String, String) -> Unit = { _, _ -> },
    onDeleteReplacementRule: (String) -> Unit = {},
    onToggleReplacementRule: (String) -> Unit = {},
    onExecuteProcessScheme: () -> Unit = {},
    // 外部应用打开（选中的文件）
    onOpenWithExternalApp: () -> Unit = {},
    // 播放器
    onPlayItem: (AudioMetadata) -> Unit = {},
    onTogglePlayPause: () -> Unit = {},
    onPlayNext: () -> Unit = {},
    onPlayPrevious: () -> Unit = {},
    onSeekTo: (Long) -> Unit = {},
    onClearPlayerError: () -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onCycleRepeat: () -> Unit = {},
    onSeekToItem: (Int) -> Unit = {},
    onRemoveFromQueue: (Int) -> Unit = {}
) {
    // 全屏播放器本地状态
    var showFullPlayer by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    var showMenu by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }  // 右上角 + 按钮菜单

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            onClearMessage()
        }
    }

    // 视频提取消息
    LaunchedEffect(videoUiState.message) {
        videoUiState.message?.let {
            snackbarHostState.showSnackbar(it)
            onClearVideoMessage()
        }
    }

    // 播放器错误提示
    LaunchedEffect(playerState.errorMessage) {
        playerState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearPlayerError()
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = RoundedCornerShape(12.dp),
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        },
        topBar = {
            if (uiState.isSelectionMode) {
                // 选择模式顶栏
                TopAppBar(
                    title = {
                        Text("已选择 ${uiState.selectedUris.size} 项")
                    },
                    navigationIcon = {
                        TextButton(onClick = onToggleSelectionMode) {
                            Text("取消", color = AppPrimaryColor)
                        }
                    },
                    actions = {
                        // 批量封面按钮
                        IconButton(
                            onClick = onShowBatchCoverSheet,
                            enabled = uiState.selectedUris.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Image,
                                contentDescription = "批量封面",
                                tint = if (uiState.selectedUris.isNotEmpty()) AppPrimaryColor
                                       else AppPrimaryColor.copy(alpha = 0.4f)
                            )
                        }
                        // 更多操作菜单
                        var showMoreMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "更多操作",
                                tint = AppPrimaryColor
                            )
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("智能替换") },
                                onClick = {
                                    showMoreMenu = false
                                    onSmartReplace()
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Shield, null, tint = AppPrimaryColor)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("删除违禁词") },
                                onClick = {
                                    showMoreMenu = false
                                    onRemoveSensitiveWords()
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Delete, null, tint = AppPrimaryColor)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("优化违禁词") },
                                onClick = {
                                    showMoreMenu = false
                                    onOptimizeSelected()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Build, null, tint = AppPrimaryColor)
                                }
                            )
                        }
                        TextButton(onClick = onToggleSelectAll) {
                            Text(
                                if (uiState.selectedUris.size == uiState.audioList.size) "取消全选" else "全选",
                                color = AppPrimaryColor
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            } else {
                // 正常模式 - Apple 风格的大标题
                LargeTopAppBar(
                    title = {
                        Text(
                            "音乐标签 [v0610c]", // 版本标记 - 批量违禁词同步歌曲名 + 批量封面
                            style = MaterialTheme.typography.displaySmall
                        )
                    },
                    actions = {
                        // + 按钮 (添加文件) - 始终显示
                        Box {
                            IconButton(onClick = { showAddMenu = true }) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "添加",
                                    tint = AppPrimaryColor
                                )
                            }
                            DropdownMenu(
                                expanded = showAddMenu,
                                onDismissRequest = { showAddMenu = false },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("添加音频文件") },
                                    onClick = {
                                        onPickFiles()
                                        showAddMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.MusicNote, null, tint = AppPrimaryColor)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("从视频提取音轨") },
                                    onClick = {
                                        onPickVideo()
                                        showAddMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.VideoFile, null, tint = AppPrimaryColor)
                                    }
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = AppleGray5
                                )
                                DropdownMenuItem(
                                    text = { Text("雷达搜索") },
                                    onClick = {
                                        onShowRadarScan()
                                        showAddMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Radar, null, tint = AppPrimaryColor)
                                    }
                                )
                            }
                        }

                        // ... 按钮 (更多操作) - 有文件时显示
                        if (uiState.audioList.isNotEmpty()) {
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(
                                        Icons.Outlined.MoreHoriz,
                                        contentDescription = "更多",
                                        tint = AppPrimaryColor
                                    )
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("选择") },
                                        onClick = {
                                            onToggleSelectionMode()
                                            showMenu = false
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.CheckCircle, null, tint = AppPrimaryColor)
                                        }
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        color = AppleGray5
                                    )
                                    DropdownMenuItem(
                                        text = { Text("从文件名填充") },
                                        onClick = {
                                            onBatchFill()
                                            showMenu = false
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Build, null, tint = AppPrimaryColor)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("保存全部") },
                                        onClick = {
                                            onBatchSave()
                                            showMenu = false
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.CloudUpload, null, tint = AppPrimaryColor)
                                        }
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        color = AppleGray5
                                    )
                                    DropdownMenuItem(
                                        text = { Text("违禁词检测") },
                                        onClick = {
                                            onBatchCheckSensitive()
                                            showMenu = false
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.Shield, null, tint = AppPrimaryColor)
                                        }
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        color = AppleGray5
                                    )
                                    DropdownMenuItem(
                                        text = { Text("清空列表", color = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            onClearAll()
                                            showMenu = false
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error)
                                        }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            // 选择模式底栏
            AnimatedVisibility(
                visible = uiState.isSelectionMode && uiState.selectedUris.isNotEmpty()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        // 第一行：核心操作按钮
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 智能替换按钮
                            Button(
                                onClick = onSmartReplace,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppPrimaryColor
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Shield,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("智能替换")
                            }
                            // 删除违禁词按钮
                            OutlinedButton(
                                onClick = onRemoveSensitiveWords,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("删除违禁词")
                            }
                            // 优化违禁词按钮
                            OutlinedButton(
                                onClick = onOptimizeSelected,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.Build,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("优化违禁词")
                            }
                            // 用其他应用打开
                            OutlinedButton(
                                onClick = onOpenWithExternalApp,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("打开")
                            }
                            // 处理方案按钮
                            OutlinedButton(
                                onClick = onShowProcessScheme,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Tune,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("处理方案")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 第二行：同步修改歌曲名 Switch + 移除
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Switch(
                                    checked = uiState.syncTitleWhenRename,
                                    onCheckedChange = { onToggleSyncTitle() },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = AppPrimaryColor,
                                        checkedTrackColor = AppPrimaryColor.copy(alpha = 0.5f)
                                    )
                                )
                                Text(
                                    "同步修改歌曲名",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Button(
                                onClick = onRemoveSelected,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("移除")
                            }
                        }
                    }
                }
            }
            }

            // 普通模式底部迷你播放器
            AnimatedVisibility(
                visible = !uiState.isSelectionMode && playerState.currentItem != null
            ) {
                MiniPlayer(
                    playerState = playerState,
                    onTogglePlayPause = onTogglePlayPause,
                    onPlayNext = onPlayNext,
                    onPlayPrevious = onPlayPrevious,
                    onSeekTo = onSeekTo,
                    onOpenFullPlayer = { showFullPlayer = true }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedVisibility(
                visible = uiState.audioList.isEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                EmptyState(onPickFiles)
            }

            AnimatedVisibility(
                visible = uiState.audioList.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                AudioList(
                    items = uiState.audioList,
                    isSelectionMode = uiState.isSelectionMode,
                    selectedUris = uiState.selectedUris,
                    currentPlayingUri = playerState.currentItem?.uri,
                    isPlayerPlaying = playerState.isPlaying,
                    onItemClick = { item ->
                        if (uiState.isSelectionMode) {
                            onToggleItemSelection(item)
                        } else {
                            onSelectItem(item)
                        }
                    },
                    onItemLongClick = { item ->
                        if (!uiState.isSelectionMode) {
                            onToggleSelectionMode()
                            onToggleItemSelection(item)
                        }
                    },
                    onPlayItem = { item ->
                        if (playerState.currentItem?.uri == item.uri) {
                            onTogglePlayPause()
                        } else {
                            onPlayItem(item)
                        }
                        // 点击播放按钮后打开全屏播放页
                        showFullPlayer = true
                    }
                )
            }

            // 加载指示器
            AnimatedVisibility(
                visible = uiState.isLoading,
                modifier = Modifier.align(Alignment.Center),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                CircularProgressIndicator(
                    color = AppPrimaryColor,
                    strokeWidth = 3.dp
                )
            }
        }
    }

    // 编辑对话框
    uiState.selectedItem?.let { item ->
        EditorDialog(
            metadata = item,
            sensitiveWords = uiState.sensitiveWords,
            onDismiss = { onSelectItem(null) },
            onSave = onSaveItem,
            onPickCover = onPickCover,
            onFixExtension = onFixExtension,
            onOpenWithExternalApp = { onOpenSingleFile(item) }
        )
    }

    // 敏感词检测对话框
    if (uiState.showSensitiveCheck) {
        SensitiveCheckDialog(
            text = uiState.sensitiveCheckText,
            result = uiState.sensitiveCheckResult,
            isLoading = uiState.isSensitiveChecking,
            onDismiss = onCloseSensitiveCheck,
            onOpenOnlineTool = {
                uriHandler.openUri("https://www.lingkechaci.com/")
            }
        )
    }

    // 视频格式选择底部弹窗
    if (videoUiState.showFormatSelector && videoUiState.videoMetadata != null) {
        FormatSelectorSheet(
            metadata = videoUiState.videoMetadata,
            config = videoUiState.extractionConfig,
            onFormatSelected = onSelectVideoFormat,
            onTrackSelected = onSelectVideoTrack,
            onStartExtraction = onStartExtraction,
            onDismiss = onDismissFormatSelector,
            onRunDiagnostic = onRunVideoDiagnostic,
            diagnosticResult = videoUiState.diagnosticResult
        )
    }

    // 视频提取进度对话框
    if (videoUiState.showProgressDialog) {
        ExtractionProgressDialog(
            state = videoUiState.extractionState,
            onDismiss = onDismissProgressDialog,
            onImportAudio = onImportExtractedAudio
        )
    }

    // 雷达扫描对话框
    if (uiState.showRadarDialog) {
        RadarScanDialog(
            isScanning = uiState.isScanning,
            scanProgress = uiState.scanProgress,
            scannedItems = uiState.scannedItems,
            selectedUris = uiState.selectedScannedUris,
            existingPaths = uiState.audioList.map { it.filePath }.toSet(),
            availablePaths = uiState.availablePaths,
            selectedPaths = uiState.scanPaths,
            onTogglePath = onToggleScanPath,
            onToggleAllPaths = onToggleAllScanPaths,
            onStartScan = onStartRadarScan,
            onToggleSelection = onToggleScannedSelection,
            onSelectAll = onSelectAllScanned,
            onDeselectAll = onDeselectAllScanned,
            onImport = onImportScanned,
            onDismiss = onDismissRadarDialog
        )
    }

    // 处理方案对话框
    if (uiState.showProcessSchemeDialog) {
        ProcessSchemeDialog(
            selectedCount = uiState.selectedUris.size,
            replacementRules = uiState.replacementRules,
            selectedObfuscationMode = uiState.selectedObfuscationMode,
            useReplacement = uiState.useReplacement,
            useObfuscation = uiState.useObfuscation,
            saveMapping = uiState.saveMapping,
            onUseReplacementChange = onSetUseReplacement,
            onUseObfuscationChange = onSetUseObfuscation,
            onSaveMappingChange = onSetSaveMapping,
            onManageRules = onShowReplacementRules,
            onSelectMode = onShowObfuscationMode,
            onExecute = onExecuteProcessScheme,
            onDismiss = onDismissProcessScheme
        )
    }

    // 批量封面弹窗
    if (uiState.showBatchCoverSheet) {
        BatchCoverSheet(
            onDismiss = onDismissBatchCoverSheet,
            onApply = onBatchApplyCover
        )
    }

    // 替换规则管理弹窗
    if (uiState.showReplacementRulesSheet) {
        ReplacementRulesSheet(
            rules = uiState.replacementRules,
            onAddRule = onAddReplacementRule,
            onDeleteRule = onDeleteReplacementRule,
            onToggleRule = onToggleReplacementRule,
            onDismiss = onDismissReplacementRules
        )
    }

    // 混淆模式选择弹窗
    if (uiState.showObfuscationModeSheet) {
        ObfuscationModeSheet(
            currentMode = uiState.selectedObfuscationMode,
            onModeSelected = onSelectObfuscationMode,
            onDismiss = onDismissObfuscationMode
        )
    }

    // 全屏播放器覆盖层
    if (showFullPlayer && playerState.currentItem != null) {
        FullPlayerScreen(
            playerState = playerState,
            onClose = { showFullPlayer = false },
            onTogglePlayPause = onTogglePlayPause,
            onPlayNext = onPlayNext,
            onPlayPrevious = onPlayPrevious,
            onSeekTo = onSeekTo,
            onToggleShuffle = onToggleShuffle,
            onCycleRepeat = onCycleRepeat,
            onOpenQueue = { showQueue = true },
            onSeekToItem = onSeekToItem
        )
    }

    // 播放队列弹窗
    if (showQueue) {
        PlaybackQueueSheet(
            playlist = playerState.playlist,
            currentIndex = playerState.currentIndex,
            onItemClick = { index ->
                onSeekToItem(index)
                showQueue = false
            },
            onRemoveItem = onRemoveFromQueue,
            onDismiss = { showQueue = false }
        )
    }
}

@Composable
private fun EmptyState(onPickFiles: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 简洁的图标
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(AppleGray6),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = AppleGray1
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            "没有音频文件",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "点击右上角 + 号添加音频",
            style = MaterialTheme.typography.bodyMedium,
            color = AppleGray1
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Apple 风格的按钮
        Button(
            onClick = onPickFiles,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AppPrimaryColor
            ),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
        ) {
            Icon(
                Icons.Default.Add,
                null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "选择文件",
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AudioList(
    items: List<AudioMetadata>,
    isSelectionMode: Boolean,
    selectedUris: Set<Uri>,
    currentPlayingUri: Uri? = null,
    isPlayerPlaying: Boolean = false,
    onItemClick: (AudioMetadata) -> Unit,
    onItemLongClick: (AudioMetadata) -> Unit,
    onPlayItem: (AudioMetadata) -> Unit = {}
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 文件数量提示
        item {
            Text(
                text = "${items.size} 个文件",
                style = MaterialTheme.typography.labelMedium,
                color = AppleGray1,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
        }

        items(items, key = { it.uri.toString() }) { item ->
            AudioItem(
                metadata = item,
                isSelectionMode = isSelectionMode,
                isSelected = selectedUris.contains(item.uri),
                isCurrentItem = currentPlayingUri == item.uri,
                isPlaying = currentPlayingUri == item.uri && isPlayerPlaying,
                onClick = { onItemClick(item) },
                onLongClick = { onItemLongClick(item) },
                onPlay = { onPlayItem(item) }
            )
        }

        // 底部留白（为迷你播放器留出空间）
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AudioItem(
    metadata: AudioMetadata,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    isCurrentItem: Boolean = false,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlay: () -> Unit = {}
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) AppPrimaryColor.copy(alpha = 0.1f)
                else if (isCurrentItem) AppPrimaryColor.copy(alpha = 0.05f)
                else MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 选择模式显示复选框
            AnimatedVisibility(visible = isSelectionMode) {
                Row {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = null,  // 点击由父组件处理
                        colors = CheckboxDefaults.colors(
                            checkedColor = AppPrimaryColor,
                            uncheckedColor = AppleGray1
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }

            // 封面 - 更大的圆角
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppleGray6),
                contentAlignment = Alignment.Center
            ) {
                val coverBitmap = metadata.coverArt ?: metadata.cover?.toBitmap()
                if (coverBitmap != null) {
                    Image(
                        bitmap = coverBitmap.asImageBitmap(),
                        contentDescription = "封面",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Outlined.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = AppleGray1
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = metadata.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = metadata.displayArtistAlbum,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppleGray1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 文件信息 - 用小标签展示
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    InfoChip(text = metadata.format)
                    if (metadata.duration > 0) {
                        InfoChip(text = metadata.formattedDuration)
                    }
                    if (metadata.bitrate > 0) {
                        InfoChip(text = "${metadata.bitrate}k")
                    }
                }
            }

            // 非选择模式下显示播放按钮
            if (!isSelectionMode) {
                IconButton(
                    onClick = onPlay,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = AppPrimaryColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                // 选择模式显示箭头指示
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = AppleGray1,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun InfoChip(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = AppleGray6
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = AppleGray1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// ==================== Previews ====================

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun MainScreenPreview_Empty() {
    AudioTaggerTheme {
        MainScreen(
            uiState = MainUiState(),
            onPickFiles = {},
            onSelectItem = {},
            onSaveItem = {},
            onRemoveItem = {},
            onBatchFill = {},
            onBatchSave = {},
            onClearAll = {},
            onClearMessage = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun MainScreenPreview_WithList() {
    val sampleList = listOf(
        AudioMetadata(
            uri = Uri.EMPTY,
            filePath = "/test/1.mp3",
            displayName = "Coldplay - Viva La Vida.mp3",
            format = "MP3",
            title = "Viva La Vida",
            artist = "Coldplay",
            album = "Viva la Vida",
            duration = 242,
            bitrate = 320
        ),
        AudioMetadata(
            uri = Uri.parse("content://test/2"),
            filePath = "/test/2.flac",
            displayName = "周杰伦 - 晴天.flac",
            format = "FLAC",
            title = "晴天",
            artist = "周杰伦",
            album = "叶惠美",
            duration = 269,
            bitrate = 1411
        ),
        AudioMetadata(
            uri = Uri.parse("content://test/3"),
            filePath = "/test/3.m4a",
            displayName = "Taylor Swift - Blank Space.m4a",
            format = "M4A",
            title = "Blank Space",
            artist = "Taylor Swift",
            duration = 231,
            bitrate = 256
        )
    )

    AudioTaggerTheme {
        MainScreen(
            uiState = MainUiState(audioList = sampleList),
            onPickFiles = {},
            onSelectItem = {},
            onSaveItem = {},
            onRemoveItem = {},
            onBatchFill = {},
            onBatchSave = {},
            onClearAll = {},
            onClearMessage = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AudioItemPreview() {
    AudioTaggerTheme {
        AudioItem(
            metadata = AudioMetadata(
                uri = Uri.EMPTY,
                filePath = "/test.mp3",
                displayName = "Coldplay - Viva La Vida.mp3",
                format = "MP3",
                title = "Viva La Vida",
                artist = "Coldplay",
                album = "Viva la Vida",
                duration = 242,
                bitrate = 320
            ),
            isSelectionMode = false,
            isSelected = false,
            onClick = {},
            onLongClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AudioItemPreview_Selected() {
    AudioTaggerTheme {
        AudioItem(
            metadata = AudioMetadata(
                uri = Uri.EMPTY,
                filePath = "/test.flac",
                displayName = "周杰伦 - 晴天.flac",
                format = "FLAC",
                title = "晴天",
                artist = "周杰伦",
                duration = 269,
                bitrate = 1411
            ),
            isSelectionMode = true,
            isSelected = true,
            onClick = {},
            onLongClick = {}
        )
    }
}
