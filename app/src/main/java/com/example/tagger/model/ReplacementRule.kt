package com.example.tagger.model

import java.util.UUID

/**
 * 自定义替换规则
 * @param id 规则唯一标识
 * @param findText 要查找的文本
 * @param replaceWith 替换为的文本
 * @param isEnabled 是否启用此规则
 */
data class ReplacementRule(
    val id: String = UUID.randomUUID().toString(),
    val findText: String,
    val replaceWith: String,
    val isEnabled: Boolean = true
)

/**
 * 文件名混淆模式
 */
enum class ObfuscationMode(val displayName: String, val example: String) {
    NUMBERED("编号模式", "Track_001.mp3"),
    RANDOM("随机模式", "a7f3k9m2.mp3"),
    DATE("日期模式", "audio_20240128_143052.mp3")
}

/**
 * 混淆映射记录 - 用于记录混淆前后的文件名对应关系
 * @param obfuscatedName 混淆后的文件名
 * @param originalName 原始文件名
 * @param originalTitle 原始标题标签
 * @param originalArtist 原始艺术家标签
 * @param timestamp 记录创建时间
 */
data class ObfuscationMapping(
    val obfuscatedName: String,
    val originalName: String,
    val originalTitle: String,
    val originalArtist: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 处理方案配置
 */
data class ProcessSchemeConfig(
    val useReplacementRules: Boolean = false,
    val useObfuscation: Boolean = false,
    val obfuscationMode: ObfuscationMode? = null,
    val saveMapping: Boolean = true
)
