package com.example.tagger.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.tagger.model.ScannedAudioItem
import com.example.tagger.ui.theme.AppPrimaryColor
import com.example.tagger.ui.theme.AppleGray1
import com.example.tagger.ui.theme.AppleGray5
import com.example.tagger.ui.theme.AppleGray6

/**
 * 雷达扫描对话框 - 支持自定义路径过滤
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarScanDialog(
    isScanning: Boolean,
    scanProgress: Float,
    scannedItems: List<ScannedAudioItem>,
    selectedUris: Set<Uri>,
    existingUris: Set<Uri>,
    // 路径选择相关
    availablePaths: List<ScanPathOption> = emptyList(),
    selectedPaths: List<String> = emptyList(),
    onTogglePath: (String) -> Unit = {},
    onToggleAllPaths: () -> Unit = {},
    // 扫描操作
    onStartScan: () -> Unit,
    onToggleSelection: (Uri) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit
) {
    // 判断当前阶段
    val isDetectingPaths = isScanning && availablePaths.isEmpty() && scannedItems.isEmpty()
    val isPathSelectionPhase = !isScanning && availablePaths.isNotEmpty() && scannedItems.isEmpty()
    val isScanningFiles = isScanning && availablePaths.isNotEmpty()
    val hasResults = scannedItems.isNotEmpty()

    // 可选中的项目 (排除已导入的)
    val selectableItems = scannedItems.filter { it.uri !in existingUris }
    val allSelectableUris = selectableItems.map { it.uri }.toSet()
    val isAllSelected = allSelectableUris.isNotEmpty() && selectedUris.containsAll(allSelectableUris)

    // 路径全选状态
    val isAllPathsSelected = availablePaths.isNotEmpty() &&
        selectedPaths.size == availablePaths.size

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isScanning,
            dismissOnClickOutside = !isScanning
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 标题栏
                TopAppBar(
                    title = {
                        Text(
                            when {
                                isPathSelectionPhase -> "选择扫描目录"
                                hasResults -> "扫描结果"
                                else -> "雷达扫描"
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss, enabled = !isScanning) {
                            Icon(Icons.Default.Close, "关闭")
                        }
                    },
                    actions = {
                        // 路径选择阶段：全选按钮
                        if (isPathSelectionPhase) {
                            TextButton(onClick = onToggleAllPaths) {
                                Text(
                                    if (isAllPathsSelected) "取消全选" else "全选",
                                    color = AppPrimaryColor
                                )
                            }
                        }
                        // 结果阶段：全选按钮
                        if (hasResults && !isScanning) {
                            TextButton(
                                onClick = if (isAllSelected) onDeselectAll else onSelectAll
                            ) {
                                Text(
                                    if (isAllSelected) "取消全选" else "全选",
                                    color = AppPrimaryColor
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // 内容区
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when {
                        // 正在检测可用路径
                        isDetectingPaths -> {
                            LoadingState(message = "正在检测音频目录...")
                        }
                        // 路径选择阶段
                        isPathSelectionPhase -> {
                            PathSelectionList(
                                paths = availablePaths,
                                selectedPaths = selectedPaths,
                                onTogglePath = onTogglePath
                            )
                        }
                        // 扫描文件中
                        isScanningFiles -> {
                            LoadingState(message = "正在扫描音频文件...")
                        }
                        // 显示扫描结果
                        hasResults -> {
                            ScanResultList(
                                items = scannedItems,
                                selectedUris = selectedUris,
                                existingUris = existingUris,
                                onToggleSelection = onToggleSelection
                            )
                        }
                        // 空结果
                        !isScanning && availablePaths.isNotEmpty() -> {
                            EmptyScanResult()
                        }
                    }
                }

                // 底部操作栏 - 路径选择阶段
                AnimatedVisibility(visible = isPathSelectionPhase) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp
                    ) {
                        Column {
                            HorizontalDivider(color = AppleGray5)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 统计信息
                                val totalAudioCount = availablePaths
                                    .filter { it.path in selectedPaths }
                                    .sumOf { it.audioCount }
                                Column {
                                    Text(
                                        "已选 ${selectedPaths.size} 个目录",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "约 $totalAudioCount 个音频文件",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AppleGray1
                                    )
                                }

                                // 开始扫描按钮
                                Button(
                                    onClick = onStartScan,
                                    enabled = selectedPaths.isNotEmpty(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AppPrimaryColor
                                    )
                                ) {
                                    Icon(
                                        Icons.Outlined.Radar,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("开始扫描")
                                }
                            }
                        }
                    }
                }

                // 底部操作栏 - 结果阶段
                AnimatedVisibility(visible = hasResults && !isScanning) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp
                    ) {
                        Column {
                            HorizontalDivider(color = AppleGray5)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 统计信息
                                Column {
                                    Text(
                                        "发现 ${scannedItems.size} 个文件",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val existingInScan = scannedItems.count { it.uri in existingUris }
                                    if (existingInScan > 0) {
                                        Text(
                                            "其中 $existingInScan 个已导入",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AppleGray1
                                        )
                                    }
                                }

                                // 导入按钮
                                Button(
                                    onClick = onImport,
                                    enabled = selectedUris.isNotEmpty(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AppPrimaryColor
                                    )
                                ) {
                                    Text(
                                        if (selectedUris.isEmpty()) "导入"
                                        else "导入 (${selectedUris.size})"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingState(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            color = AppPrimaryColor,
            strokeWidth = 4.dp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PathSelectionList(
    paths: List<ScanPathOption>,
    selectedPaths: List<String>,
    onTogglePath: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "选择要扫描的目录：",
                style = MaterialTheme.typography.bodySmall,
                color = AppleGray1,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
        }

        items(paths, key = { it.path }) { pathOption ->
            PathOptionRow(
                option = pathOption,
                isSelected = pathOption.path in selectedPaths,
                onToggle = { onTogglePath(pathOption.path) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PathOptionRow(
    option: ScanPathOption,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) AppPrimaryColor.copy(alpha = 0.1f)
               else MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(
                    checkedColor = AppPrimaryColor,
                    uncheckedColor = AppleGray1
                )
            )

            Spacer(modifier = Modifier.width(12.dp))

            // 文件夹图标
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppleGray6),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = AppPrimaryColor
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${option.audioCount} 个音频文件",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppleGray1
                )
            }
        }
    }
}

@Composable
private fun EmptyScanResult() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = AppleGray1
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "未发现音频文件",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "选中的目录中没有找到音频文件",
            style = MaterialTheme.typography.bodyMedium,
            color = AppleGray1
        )
    }
}

@Composable
private fun ScanResultList(
    items: List<ScannedAudioItem>,
    selectedUris: Set<Uri>,
    existingUris: Set<Uri>,
    onToggleSelection: (Uri) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.uri.toString() }) { item ->
            val isExisting = item.uri in existingUris
            val isSelected = item.uri in selectedUris

            ScannedAudioItemRow(
                item = item,
                isSelected = isSelected,
                isExisting = isExisting,
                onToggle = {
                    if (!isExisting) {
                        onToggleSelection(item.uri)
                    }
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ScannedAudioItemRow(
    item: ScannedAudioItem,
    isSelected: Boolean,
    isExisting: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isExisting, onClick = onToggle),
        shape = RoundedCornerShape(12.dp),
        color = when {
            isExisting -> AppleGray6.copy(alpha = 0.5f)
            isSelected -> AppPrimaryColor.copy(alpha = 0.1f)
            else -> MaterialTheme.colorScheme.surface
        }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 复选框或已导入图标
            if (isExisting) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = "已导入",
                    modifier = Modifier.size(24.dp),
                    tint = AppPrimaryColor
                )
            } else {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(
                        checkedColor = AppPrimaryColor,
                        uncheckedColor = AppleGray1
                    )
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 音乐图标
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppleGray6),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (isExisting) AppleGray1.copy(alpha = 0.5f) else AppleGray1
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 文件信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isExisting)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = item.displayArtist,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isExisting) AppleGray1.copy(alpha = 0.5f) else AppleGray1
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 时长和大小
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = item.formattedDuration,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isExisting) AppleGray1.copy(alpha = 0.5f) else AppleGray1
                    )
                    Text(
                        text = item.formattedSize,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isExisting) AppleGray1.copy(alpha = 0.5f) else AppleGray1
                    )
                }
            }

            // 已导入标签
            if (isExisting) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = AppPrimaryColor.copy(alpha = 0.1f)
                ) {
                    Text(
                        "已导入",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppPrimaryColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
