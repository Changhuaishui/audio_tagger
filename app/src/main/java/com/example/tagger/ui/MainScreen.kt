package com.example.tagger.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.example.tagger.core.SensitiveCheckResult
import com.example.tagger.model.AudioMetadata
import com.example.tagger.ui.theme.AppleBlue
import com.example.tagger.ui.theme.AppleGray1
import com.example.tagger.ui.theme.AppleGray5
import com.example.tagger.ui.theme.AppleGray6

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: MainUiState,
    onPickFiles: () -> Unit,
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
    onRemoveSelected: () -> Unit = {}
) {
    val uriHandler = LocalUriHandler.current
    var showMenu by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            onClearMessage()
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
                            Text("取消", color = AppleBlue)
                        }
                    },
                    actions = {
                        TextButton(onClick = onToggleSelectAll) {
                            Text(
                                if (uiState.selectedUris.size == uiState.audioList.size) "取消全选" else "全选",
                                color = AppleBlue
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
                            "音乐标签",
                            style = MaterialTheme.typography.displaySmall
                        )
                    },
                    actions = {
                        if (uiState.audioList.isNotEmpty()) {
                            // 选择按钮
                            TextButton(onClick = onToggleSelectionMode) {
                                Text("选择", color = AppleBlue)
                            }
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    Icons.Outlined.MoreHoriz,
                                    contentDescription = "更多",
                                    tint = AppleBlue
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("从文件名填充") },
                                    onClick = {
                                        onBatchFill()
                                        showMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.AutoFixHigh, null, tint = AppleBlue)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("保存全部") },
                                    onClick = {
                                        onBatchSave()
                                        showMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.CloudUpload, null, tint = AppleBlue)
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
                                        Icon(Icons.Outlined.Shield, null, tint = AppleBlue)
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
                    },
                    colors = TopAppBarDefaults.largeTopAppBarColors(
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
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
                            Text("移除 ${uiState.selectedUris.size} 项")
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            // 非选择模式才显示 FAB
            if (!uiState.isSelectionMode) {
                FloatingActionButton(
                    onClick = onPickFiles,
                    shape = CircleShape,
                    containerColor = AppleBlue,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.shadow(
                        elevation = 8.dp,
                        shape = CircleShape,
                        spotColor = AppleBlue.copy(alpha = 0.4f)
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = "添加文件")
                }
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
                    color = AppleBlue,
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
            onPickCover = onPickCover
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
            "点击添加按钮选择音频文件",
            style = MaterialTheme.typography.bodyMedium,
            color = AppleGray1
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Apple 风格的按钮
        Button(
            onClick = onPickFiles,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AppleBlue
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
    onItemClick: (AudioMetadata) -> Unit,
    onItemLongClick: (AudioMetadata) -> Unit
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
                onClick = { onItemClick(item) },
                onLongClick = { onItemLongClick(item) }
            )
        }

        // 底部留白，避免 FAB 遮挡
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
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) AppleBlue.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface,
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
                            checkedColor = AppleBlue,
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
                if (metadata.coverArt != null) {
                    Image(
                        bitmap = metadata.coverArt.asImageBitmap(),
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

            // 箭头指示
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = AppleGray1,
                modifier = Modifier.size(20.dp)
            )
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
