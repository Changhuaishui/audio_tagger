package com.example.tagger.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.tagger.model.CoverAssignmentMode
import com.example.tagger.ui.theme.AppPrimaryColor
import com.example.tagger.ui.theme.AppleGray5
import com.example.tagger.ui.theme.AppleGray6

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchCoverSheet(
    onDismiss: () -> Unit,
    onApply: (List<Uri>, CoverAssignmentMode) -> Unit
) {
    var selectedMode by remember { mutableStateOf(CoverAssignmentMode.SAME) }
    var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var imageCountText by remember { mutableStateOf("未选择图片") }

    val singleImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUris = listOf(it)
            imageCountText = "已选择 1 张图片"
        }
    }

    val multipleImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            selectedImageUris = uris
            imageCountText = "已选择 ${uris.size} 张图片"
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding()
        ) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "批量添加封面",
                    style = MaterialTheme.typography.headlineSmall
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 图片选择区域
            Text("选择图片", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { singleImagePicker.launch("image/*") },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("单张图片")
                }
                OutlinedButton(
                    onClick = { multipleImagePicker.launch("image/*") },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("多张图片")
                }
            }

            Text(
                text = imageCountText,
                style = MaterialTheme.typography.bodySmall,
                color = AppleGray6,
                modifier = Modifier.padding(top = 8.dp)
            )

            HorizontalDivider(color = AppleGray5, modifier = Modifier.padding(vertical = 16.dp))

            // 分配策略
            Text("分配策略", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            CoverAssignmentMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedMode = mode }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedMode == mode,
                        onClick = { selectedMode = mode },
                        colors = RadioButtonDefaults.colors(selectedColor = AppPrimaryColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(mode.displayName, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            when (mode) {
                                CoverAssignmentMode.SAME -> "所有选中的音频使用同一张封面"
                                CoverAssignmentMode.SEQUENTIAL -> "按顺序依次分配（图片不足时循环）"
                                CoverAssignmentMode.RANDOM -> "为每个音频随机挑选一张封面"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = AppleGray6
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (selectedImageUris.isNotEmpty()) {
                        onApply(selectedImageUris, selectedMode)
                    }
                },
                enabled = selectedImageUris.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = AppPrimaryColor)
            ) {
                Text("开始添加封面")
            }
        }
    }
}
