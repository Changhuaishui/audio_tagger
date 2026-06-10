package com.example.tagger.model

/**
 * 批量封面分配策略
 */
enum class CoverAssignmentMode(val displayName: String) {
    /** 所有文件使用同一张图片 */
    SAME("全部相同"),
    /** 按顺序循环分配 */
    SEQUENTIAL("按顺序分配"),
    /** 随机分配 */
    RANDOM("随机分配")
}
