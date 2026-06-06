package com.example.tagger.core.tagreader

import android.net.Uri
import com.example.tagger.model.AudioMetadata
import java.io.File

/**
 * 音频标签读取接口
 * 每种格式实现各自的读取策略
 */
interface AudioTagReader {
    fun read(file: File, uri: Uri): AudioMetadata?
}
