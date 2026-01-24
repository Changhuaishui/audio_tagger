package com.example.tagger.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.tooling.preview.Preview
import com.example.tagger.ui.theme.AudioTaggerTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.tagger.model.AudioMetadata
import com.example.tagger.ui.theme.AppPrimaryColor
import com.example.tagger.ui.theme.AppleGray1
import com.example.tagger.ui.theme.AppleGray5
import com.example.tagger.ui.theme.AppleGray6

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorDialog(
    metadata: AudioMetadata,
    sensitiveWords: Set<String>,
    onDismiss: () -> Unit,
    onSave: (AudioMetadata) -> Unit,
    onPickCover: (Uri) -> Unit = {},
    onFixExtension: ((AudioMetadata) -> Unit)? = null  // 修复扩展名回调
) {
    // 文件名（不含扩展名）
    val originalNameWithoutExt = remember { metadata.displayName.substringBeforeLast('.', metadata.displayName) }
    val extension = remember { metadata.displayName.substringAfterLast('.', "") }
    var fileName by remember { mutableStateOf(originalNameWithoutExt) }

    var title by remember { mutableStateOf(metadata.title) }
    var artist by remember { mutableStateOf(metadata.artist) }
    var album by remember { mutableStateOf(metadata.album) }
    var year by remember { mutableStateOf(metadata.year) }
    var track by remember { mutableStateOf(metadata.track) }
    var genre by remember { mutableStateOf(metadata.genre) }
    var comment by remember { mutableStateOf(metadata.comment) }

    var coverBitmap by remember { mutableStateOf(metadata.coverArt) }
    var coverBytes by remember { mutableStateOf(metadata.coverArtBytes) }
    var coverMimeType by remember { mutableStateOf(metadata.coverArtMimeType) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onPickCover(it) }
    }

    LaunchedEffect(metadata.coverArt, metadata.coverArtBytes) {
        coverBitmap = metadata.coverArt
        coverBytes = metadata.coverArtBytes
        coverMimeType = metadata.coverArtMimeType
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                // Apple 风格顶栏
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "编辑标签",
                            style = MaterialTheme.typography.headlineSmall
                        )
                    },
                    navigationIcon = {
                        TextButton(onClick = onDismiss) {
                            Text(
                                "取消",
                                color = AppPrimaryColor,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                // 构建新的文件名（如果有变更）
                                val newDisplayName = if (extension.isNotEmpty()) {
                                    "$fileName.$extension"
                                } else {
                                    fileName
                                }
                                onSave(
                                    metadata.copy(
                                        displayName = newDisplayName,  // 传递新文件名
                                        title = title,
                                        artist = artist,
                                        album = album,
                                        year = year,
                                        track = track,
                                        genre = genre,
                                        comment = comment,
                                        coverArt = coverBitmap,
                                        coverArtBytes = coverBytes,
                                        coverArtMimeType = coverMimeType
                                    )
                                )
                            }
                        ) {
                            Text(
                                "保存",
                                color = AppPrimaryColor,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // 封面区域 - 居中大图
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(AppleGray6)
                        .clickable { imagePickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (coverBitmap != null) {
                        Image(
                            bitmap = coverBitmap!!.asImageBitmap(),
                            contentDescription = "封面",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.MusicNote,
                            contentDescription = "无封面",
                            modifier = Modifier.size(64.dp),
                            tint = AppleGray1
                        )
                    }

                    // 相机图标叠加
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AppPrimaryColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.CameraAlt,
                            contentDescription = "更换封面",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "点击更换封面",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppleGray1
                )

                Spacer(modifier = Modifier.height(32.dp))

                // 文件名编辑卡片
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        // 文件名输入框
                        AppleTextField(
                            value = fileName,
                            onValueChange = { fileName = it },
                            label = "文件名",
                            sensitiveWords = sensitiveWords
                        )

                        // 扩展名提示（不可编辑）
                        if (extension.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "扩展名: .$extension",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppleGray1
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = AppleGray5)
                        Spacer(modifier = Modifier.height(8.dp))

                        // 文件格式信息
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${metadata.format} · ${metadata.formattedDuration} · ${metadata.bitrate}kbps",
                                style = MaterialTheme.typography.labelMedium,
                                color = AppleGray1
                            )
                            // 格式不匹配警告
                            if (metadata.isFormatMismatch) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Outlined.Warning,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "扩展名错误",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }

                        // 格式不匹配修复选项
                        if (metadata.isFormatMismatch && onFixExtension != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "文件实际格式为 ${metadata.format}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "建议重命名为: ${metadata.correctedDisplayName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AppleGray1
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    TextButton(
                                        onClick = { onFixExtension(metadata) },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = AppPrimaryColor
                                        )
                                    ) {
                                        Text(
                                            "修复",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 表单区域
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 标题
                        AppleTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = "标题",
                            sensitiveWords = sensitiveWords
                        )

                        HorizontalDivider(color = AppleGray5)

                        // 艺术家
                        AppleTextField(
                            value = artist,
                            onValueChange = { artist = it },
                            label = "艺术家",
                            sensitiveWords = sensitiveWords
                        )

                        HorizontalDivider(color = AppleGray5)

                        // 专辑
                        AppleTextField(
                            value = album,
                            onValueChange = { album = it },
                            label = "专辑",
                            sensitiveWords = sensitiveWords
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 更多信息
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            AppleTextField(
                                value = year,
                                onValueChange = { year = it },
                                label = "年份",
                                modifier = Modifier.weight(1f)
                            )
                            AppleTextField(
                                value = track,
                                onValueChange = { track = it },
                                label = "曲目",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        HorizontalDivider(color = AppleGray5)

                        AppleTextField(
                            value = genre,
                            onValueChange = { genre = it },
                            label = "流派"
                        )

                        HorizontalDivider(color = AppleGray5)

                        AppleTextField(
                            value = comment,
                            onValueChange = { comment = it },
                            label = "备注",
                            singleLine = false,
                            minLines = 2
                        )
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

/**
 * Apple 风格的简洁输入框
 */
@Composable
private fun AppleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    sensitiveWords: Set<String> = emptySet(),
    singleLine: Boolean = true,
    minLines: Int = 1
) {
    val foundWords = remember(value, sensitiveWords) {
        if (sensitiveWords.isEmpty() || value.isEmpty()) emptyList()
        else {
            val lowerText = value.lowercase()
            sensitiveWords.filter { word ->
                word.isNotEmpty() && lowerText.contains(word.lowercase())
            }.take(3)
        }
    }

    val hasSensitiveWords = foundWords.isNotEmpty()

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (hasSensitiveWords) {
                    MaterialTheme.colorScheme.error
                } else {
                    AppleGray1
                }
            )

            // 敏感词提示
            if (hasSensitiveWords) {
                Text(
                    text = foundWords.joinToString(" "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 简洁的无边框输入
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge,
            singleLine = singleLine,
            minLines = minLines,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = if (hasSensitiveWords) {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                } else {
                    AppleGray6
                },
                unfocusedContainerColor = if (hasSensitiveWords) {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                } else {
                    AppleGray6
                },
                focusedIndicatorColor = if (hasSensitiveWords) {
                    MaterialTheme.colorScheme.error
                } else {
                    AppPrimaryColor
                },
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                cursorColor = AppPrimaryColor
            ),
            shape = RoundedCornerShape(10.dp)
        )
    }
}

// ==================== Preview ====================

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun EditorDialogPreview() {
    AudioTaggerTheme {
        EditorDialog(
            metadata = AudioMetadata(
                uri = Uri.EMPTY,
                filePath = "/storage/emulated/0/Music/test.mp3",
                displayName = "Coldplay - Viva La Vida.mp3",
                format = "FLAC",  // 故意设置不匹配，展示警告
                title = "Viva La Vida",
                artist = "Coldplay",
                album = "Viva la Vida",
                year = "2008",
                duration = 242,
                bitrate = 916
            ),
            sensitiveWords = setOf("test", "敏感词"),
            onDismiss = {},
            onSave = {},
            onPickCover = {},
            onFixExtension = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EditorDialogPreview_Clean() {
    AudioTaggerTheme {
        EditorDialog(
            metadata = AudioMetadata(
                uri = Uri.EMPTY,
                filePath = "/storage/emulated/0/Music/test.flac",
                displayName = "周杰伦 - 晴天.flac",
                format = "FLAC",  // 匹配，无警告
                title = "晴天",
                artist = "周杰伦",
                album = "叶惠美",
                year = "2003",
                duration = 269,
                bitrate = 1411
            ),
            sensitiveWords = emptySet(),
            onDismiss = {},
            onSave = {}
        )
    }
}
