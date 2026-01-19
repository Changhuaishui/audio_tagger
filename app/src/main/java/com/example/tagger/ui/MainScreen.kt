package com.example.tagger.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.example.tagger.core.SensitiveCheckResult
import com.example.tagger.model.AudioMetadata

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
    // 敏感词检测
    onBatchCheckSensitive: () -> Unit = {},
    onCheckSensitive: (AudioMetadata) -> Unit = {},
    onCloseSensitiveCheck: () -> Unit = {}
) {
    val uriHandler = LocalUriHandler.current
    var showMenu by remember { mutableStateOf(false) }

    // Snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            onClearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Audio Tagger") },
                actions = {
                    if (uiState.audioList.isNotEmpty()) {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "菜单")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("从文件名填充标签") },
                                onClick = {
                                    onBatchFill()
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.AutoFixHigh, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("保存全部") },
                                onClick = {
                                    onBatchSave()
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.SaveAlt, null) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("违禁词检测") },
                                onClick = {
                                    onBatchCheckSensitive()
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.Shield, null) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("清空列表") },
                                onClick = {
                                    onClearAll()
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.DeleteSweep, null) }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onPickFiles) {
                Icon(Icons.Default.Add, contentDescription = "添加文件")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.audioList.isEmpty()) {
                EmptyState(onPickFiles)
            } else {
                AudioList(
                    items = uiState.audioList,
                    onItemClick = onSelectItem,
                    onItemLongClick = onRemoveItem
                )
            }

            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
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
            onSave = onSaveItem
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
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "没有音频文件",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "点击下方按钮添加音频文件",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(onClick = onPickFiles) {
            Icon(Icons.Default.Add, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("选择文件")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AudioList(
    items: List<AudioMetadata>,
    onItemClick: (AudioMetadata) -> Unit,
    onItemLongClick: (AudioMetadata) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.uri.toString() }) { item ->
            AudioItem(
                metadata = item,
                onClick = { onItemClick(item) },
                onLongClick = { onItemLongClick(item) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AudioItem(
    metadata: AudioMetadata,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .padding(8.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = metadata.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = metadata.displayArtistAlbum,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = metadata.format,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    if (metadata.duration > 0) {
                        Text(
                            text = " · ${metadata.formattedDuration}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    if (metadata.bitrate > 0) {
                        Text(
                            text = " · ${metadata.bitrate}kbps",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
