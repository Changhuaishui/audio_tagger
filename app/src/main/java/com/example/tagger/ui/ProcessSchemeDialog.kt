package com.example.tagger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.tagger.model.ObfuscationMode
import com.example.tagger.model.ReplacementRule
import com.example.tagger.ui.theme.AppPrimaryColor
import com.example.tagger.ui.theme.AppleGray1
import com.example.tagger.ui.theme.AppleGray5

/**
 * 处理方案主对话框
 */
@Composable
fun ProcessSchemeDialog(
    selectedCount: Int,
    replacementRules: List<ReplacementRule>,
    selectedObfuscationMode: ObfuscationMode?,
    useReplacement: Boolean,
    useObfuscation: Boolean,
    saveMapping: Boolean,
    onUseReplacementChange: (Boolean) -> Unit,
    onUseObfuscationChange: (Boolean) -> Unit,
    onSaveMappingChange: (Boolean) -> Unit,
    onManageRules: () -> Unit,
    onSelectMode: () -> Unit,
    onExecute: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .width(300.dp)
            ) {
                // 标题
                Text(
                    text = "处理方案",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "已选择 $selectedCount 个文件",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppleGray1,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 自定义替换规则选项
                ProcessOptionItem(
                    icon = Icons.Outlined.FindReplace,
                    title = "应用自定义替换规则",
                    subtitle = if (replacementRules.isEmpty()) {
                        "暂无规则"
                    } else {
                        "已有 ${replacementRules.filter { it.isEnabled }.size} 条启用规则"
                    },
                    isEnabled = useReplacement,
                    onToggle = onUseReplacementChange,
                    actionText = "管理",
                    onAction = onManageRules
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = AppleGray5
                )

                // 文件名混淆选项
                ProcessOptionItem(
                    icon = Icons.Outlined.Shuffle,
                    title = "应用文件名混淆",
                    subtitle = selectedObfuscationMode?.let {
                        "${it.displayName} (${it.example})"
                    } ?: "未选择模式",
                    isEnabled = useObfuscation,
                    onToggle = onUseObfuscationChange,
                    actionText = "选择",
                    onAction = onSelectMode
                )

                // 保存映射选项（仅在启用混淆时显示）
                if (useObfuscation) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 40.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = saveMapping,
                            onCheckedChange = onSaveMappingChange,
                            colors = CheckboxDefaults.colors(
                                checkedColor = AppPrimaryColor,
                                uncheckedColor = AppleGray1
                            )
                        )
                        Text(
                            text = "保存映射关系（可反查）",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppleGray1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("取消")
                    }

                    Button(
                        onClick = onExecute,
                        modifier = Modifier.weight(1f),
                        enabled = useReplacement || (useObfuscation && selectedObfuscationMode != null),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppPrimaryColor
                        )
                    ) {
                        Text("执行处理")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessOptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    actionText: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isEnabled,
            onCheckedChange = onToggle,
            colors = CheckboxDefaults.colors(
                checkedColor = AppPrimaryColor,
                uncheckedColor = AppleGray1
            )
        )

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isEnabled) AppPrimaryColor else AppleGray1,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = AppleGray1
            )
        }

        TextButton(onClick = onAction) {
            Text(
                text = actionText,
                color = AppPrimaryColor
            )
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = AppPrimaryColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
