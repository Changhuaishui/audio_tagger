package com.example.tagger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    onSave: (AudioMetadata) -> Unit
) {
    var title by remember { mutableStateOf(metadata.title) }
    var artist by remember { mutableStateOf(metadata.artist) }
    var album by remember { mutableStateOf(metadata.album) }
    var year by remember { mutableStateOf(metadata.year) }
    var track by remember { mutableStateOf(metadata.track) }
    var genre by remember { mutableStateOf(metadata.genre) }
    var comment by remember { mutableStateOf(metadata.comment) }

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
                                        comment = comment
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
