package com.example.tagger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.tagger.model.ObfuscationMode
import com.example.tagger.ui.theme.AppPrimaryColor
import com.example.tagger.ui.theme.AppleGray1
import com.example.tagger.ui.theme.AppleGray5
import com.example.tagger.ui.theme.AppleGray6

/**
 * 混淆模式选择弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObfuscationModeSheet(
    currentMode: ObfuscationMode?,
    onModeSelected: (ObfuscationMode) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMode by remember { mutableStateOf(currentMode) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "选择混淆模式",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "混淆后的文件名将无法直接识别内容",
                style = MaterialTheme.typography.bodyMedium,
                color = AppleGray1
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 模式选项
            ObfuscationMode.entries.forEach { mode ->
                ModeOption(
                    mode = mode,
                    isSelected = selectedMode == mode,
                    onClick = { selectedMode = mode }
                )

                if (mode != ObfuscationMode.entries.last()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = AppleGray5
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 确定按钮
            Button(
                onClick = {
                    selectedMode?.let { onModeSelected(it) }
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedMode != null,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppPrimaryColor
                )
            ) {
                Text("确定")
            }
        }
    }
}

@Composable
private fun ModeOption(
    mode: ObfuscationMode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) AppPrimaryColor.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = AppPrimaryColor,
                    unselectedColor = AppleGray1
                )
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mode.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = mode.example,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppleGray1
                )
            }
        }
    }
}
