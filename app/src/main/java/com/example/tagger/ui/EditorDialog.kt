package com.example.tagger.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Photo
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorDialog(
    metadata: AudioMetadata,
    sensitiveWords: Set<String>,
    onDismiss: () -> Unit,
    onSave: (AudioMetadata) -> Unit,
    onPickCover: (Uri) -> Unit = {}  // 选择封面回调
) {
    var title by remember { mutableStateOf(metadata.title) }
    var artist by remember { mutableStateOf(metadata.artist) }
    var album by remember { mutableStateOf(metadata.album) }
    var year by remember { mutableStateOf(metadata.year) }
    var track by remember { mutableStateOf(metadata.track) }
    var genre by remember { mutableStateOf(metadata.genre) }
    var comment by remember { mutableStateOf(metadata.comment) }

    // 封面状态
    var coverBitmap by remember { mutableStateOf(metadata.coverArt) }
    var coverBytes by remember { mutableStateOf(metadata.coverArtBytes) }
    var coverMimeType by remember { mutableStateOf(metadata.coverArtMimeType) }

    // 图片选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onPickCover(it) }
    }

    // 当外部传入新的封面时更新
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
                TopAppBar(
                    title = { Text("编辑标签") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "关闭")
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                onSave(
                                    metadata.copy(
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
                            Text("保存")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // 文件信息（只读）
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "文件信息",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = metadata.displayName,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "${metadata.format} · ${metadata.formattedDuration} · ${metadata.bitrate}kbps · ${metadata.sampleRate}Hz",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 封面区域
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 封面预览
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
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
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = "无封面",
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "专辑封面",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = if (coverBitmap != null) "点击更换封面" else "点击添加封面",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 选择按钮
                    FilledTonalButton(
                        onClick = { imagePickerLauncher.launch("image/*") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Photo,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("选择")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 标题 - 使用敏感词检测输入框
                SensitiveTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = "标题",
                    sensitiveWords = sensitiveWords,
                    modifier = Modifier.fillMaxWidth()
                )

                // 艺术家 - 使用敏感词检测输入框
                SensitiveTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = "艺术家",
                    sensitiveWords = sensitiveWords,
                    modifier = Modifier.fillMaxWidth()
                )

                // 专辑 - 使用敏感词检测输入框
                SensitiveTextField(
                    value = album,
                    onValueChange = { album = it },
                    label = "专辑",
                    sensitiveWords = sensitiveWords,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = year,
                        onValueChange = { year = it },
                        label = { Text("年份") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = track,
                        onValueChange = { track = it },
                        label = { Text("曲目") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = genre,
                    onValueChange = { genre = it },
                    label = { Text("流派") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("备注") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
