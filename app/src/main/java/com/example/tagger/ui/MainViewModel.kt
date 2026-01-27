package com.example.tagger.ui

import android.app.Application
import android.content.ContentUris
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tagger.core.FileNameOptimizer
import com.example.tagger.core.SensitiveCheckResult
import com.example.tagger.core.SensitiveWordChecker
import com.example.tagger.core.TagEditor
import com.example.tagger.core.WriteResult
import com.example.tagger.model.AudioMetadata
import com.example.tagger.model.ScannedAudioItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MainUiState(
    val audioList: List<AudioMetadata> = emptyList(),
    val isLoading: Boolean = false,
    val selectedItem: AudioMetadata? = null,
    val message: String? = null,
    // 敏感词检测相关
    val sensitiveWords: Set<String> = emptySet(),
    val showSensitiveCheck: Boolean = false,
    val sensitiveCheckText: String = "",
    val sensitiveCheckResult: SensitiveCheckResult? = null,
    val isSensitiveChecking: Boolean = false,
    // 多选模式
    val isSelectionMode: Boolean = false,
    val selectedUris: Set<Uri> = emptySet(),
    // 雷达扫描状态
    val showRadarDialog: Boolean = false,
    val isScanning: Boolean = false,
    val scanProgress: Float = 0f,
    val scannedItems: List<ScannedAudioItem> = emptyList(),
    val selectedScannedUris: Set<Uri> = emptySet(),
    val scanPaths: List<String> = emptyList(),  // 用户选择的扫描路径
    val availablePaths: List<ScanPathOption> = emptyList()  // 可选的路径列表
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val tagEditor = TagEditor(application)
    private val sensitiveChecker = SensitiveWordChecker(application)
    private val fileNameOptimizer = FileNameOptimizer(sensitiveChecker)

    private val _uiState = MutableStateFlow(MainUiState())

    // 使用 SharedFlow 替代 LocalBroadcastManager 发送事件
    private val _audioSavedEvent = MutableSharedFlow<AudioSavedEvent>()
    /** 音频保存事件流，外部可订阅 */
    val audioSavedEvent: SharedFlow<AudioSavedEvent> = _audioSavedEvent.asSharedFlow()
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    /**
     * 加载选中的音频文件
     */
    fun loadAudioFiles(uris: List<Uri>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val items = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    tagEditor.readFromUri(uri)
                }
            }

            _uiState.value = _uiState.value.copy(
                audioList = _uiState.value.audioList + items,
                isLoading = false,
                message = "已加载 ${items.size} 个文件"
            )
        }
    }

    /**
     * 加载从视频提取的音频文件，并自动填充视频的标题和封面
     *
     * @param audioUri 提取的音频文件 URI
     * @param videoTitle 视频标题（用于填充音频的 title 字段）
     * @param thumbnail 视频缩略图（用于填充音频的封面）
     * @param thumbnailBytes 缩略图原始字节（用于保存）
     */
    fun loadAudioFileWithVideoMetadata(
        audioUri: Uri,
        videoTitle: String,
        thumbnail: Bitmap?,
        thumbnailBytes: ByteArray?
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val item = withContext(Dispatchers.IO) {
                tagEditor.readFromUri(audioUri)
            }

            if (item != null) {
                // 用视频信息填充音频标签
                val updatedItem = item.copy(
                    // 如果音频本身没有标题，使用视频标题
                    title = if (item.title.isEmpty()) videoTitle else item.title,
                    // 如果音频本身没有封面，使用视频缩略图
                    coverArt = if (item.coverArt == null) thumbnail else item.coverArt,
                    coverArtBytes = if (item.coverArtBytes == null) thumbnailBytes else item.coverArtBytes,
                    coverArtMimeType = if (item.coverArtBytes == null && thumbnailBytes != null) "image/jpeg" else item.coverArtMimeType
                )

                _uiState.value = _uiState.value.copy(
                    audioList = _uiState.value.audioList + updatedItem,
                    isLoading = false,
                    message = "已导入并填充视频信息: $videoTitle"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "无法读取提取的音频文件"
                )
            }
        }
    }

    /**
     * 选择要编辑的项目
     */
    fun selectItem(item: AudioMetadata?) {
        _uiState.value = _uiState.value.copy(selectedItem = item)
    }

    /**
     * 更新正在编辑的项目的封面
     */
    fun updateSelectedItemCover(imageUri: Uri) {
        viewModelScope.launch {
            val coverData = withContext(Dispatchers.IO) {
                tagEditor.loadCoverFromUri(imageUri)
            }

            coverData?.let { (bitmap, bytes, mimeType) ->
                val currentItem = _uiState.value.selectedItem ?: return@let
                val updatedItem = currentItem.copy(
                    coverArt = bitmap,
                    coverArtBytes = bytes,
                    coverArtMimeType = mimeType
                )
                _uiState.value = _uiState.value.copy(selectedItem = updatedItem)
            }
        }
    }

    /**
     * 保存编辑后的元数据（包括文件名变更）
     */
    fun saveItem(item: AudioMetadata) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // 找到原始项目，检查文件名是否变更
            val originalItem = _uiState.value.audioList.find { it.uri == item.uri }
            val fileNameChanged = originalItem != null && originalItem.displayName != item.displayName

            var currentUri = item.uri
            var currentItem = item

            // 如果文件名变更，先重命名
            if (fileNameChanged) {
                val renameResult = withContext(Dispatchers.IO) {
                    tagEditor.renameFile(item.uri, item.displayName)
                }
                when (renameResult) {
                    is RenameResult.Success -> {
                        currentUri = renameResult.newUri
                        currentItem = item.copy(uri = currentUri)
                    }
                    is RenameResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            message = "重命名失败: ${renameResult.message}"
                        )
                        return@launch
                    }
                }
            }

            // 写入标签
            val result = withContext(Dispatchers.IO) {
                tagEditor.writeToUri(currentUri, currentItem)
            }

            when (result) {
                is WriteResult.Success -> {
                    // 更新列表中的项目
                    val updatedList = _uiState.value.audioList.map {
                        if (it.uri == item.uri) currentItem else it
                    }
                    val message = if (fileNameChanged) {
                        "保存成功（文件已重命名）"
                    } else {
                        "保存成功"
                    }
                    _uiState.value = _uiState.value.copy(
                        audioList = updatedList,
                        selectedItem = null,
                        isLoading = false,
                        message = message
                    )
                    // 发送事件通知文件已保存
                    emitAudioSaved(currentItem)
                }
                is WriteResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        message = result.message
                    )
                }
            }
        }
    }

    /**
     * 发送单个文件保存成功的事件
     */
    private fun emitAudioSaved(item: AudioMetadata) {
        viewModelScope.launch {
            _audioSavedEvent.emit(AudioSavedEvent.Single(item.filePath, item.uri))
        }
    }

    /**
     * 发送批量保存成功的事件
     */
    private fun emitAudioSavedBatch(items: List<AudioMetadata>) {
        viewModelScope.launch {
            _audioSavedEvent.emit(AudioSavedEvent.Batch(items.map { it.filePath }))
        }
    }

    /**
     * 从文件名批量填充标签
     */
    fun batchFillFromFileName() {
        viewModelScope.launch {
            val updated = tagEditor.batchFillFromFileName(_uiState.value.audioList)
            _uiState.value = _uiState.value.copy(
                audioList = updated,
                message = "已从文件名解析标签"
            )
        }
    }

    /**
     * 批量保存所有修改
     */
    fun batchSaveAll() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val successItems = mutableListOf<AudioMetadata>()
            var failCount = 0
            withContext(Dispatchers.IO) {
                _uiState.value.audioList.forEach { item ->
                    when (tagEditor.writeToUri(item.uri, item)) {
                        is WriteResult.Success -> successItems.add(item)
                        is WriteResult.Error -> failCount++
                    }
                }
            }

            val message = if (failCount == 0) {
                "已保存 ${successItems.size} 个文件"
            } else {
                "保存完成: ${successItems.size} 成功, $failCount 失败（部分文件格式不支持）"
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                message = message
            )

            // 发送事件通知批量保存完成
            if (successItems.isNotEmpty()) {
                emitAudioSavedBatch(successItems)
            }
        }
    }

    /**
     * 移除项目
     */
    fun removeItem(item: AudioMetadata) {
        val updatedList = _uiState.value.audioList.filter { it.uri != item.uri }
        _uiState.value = _uiState.value.copy(audioList = updatedList)
    }

    /**
     * 清空列表
     */
    fun clearAll() {
        _uiState.value = _uiState.value.copy(
            audioList = emptyList(),
            isSelectionMode = false,
            selectedUris = emptySet()
        )
    }

    /**
     * 清除消息
     */
    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    // ==================== 多选模式 ====================

    /**
     * 切换选择模式
     */
    fun toggleSelectionMode() {
        val currentMode = _uiState.value.isSelectionMode
        _uiState.value = _uiState.value.copy(
            isSelectionMode = !currentMode,
            selectedUris = emptySet()  // 退出选择模式时清空选中
        )
    }

    /**
     * 切换单个项目的选中状态
     */
    fun toggleItemSelection(item: AudioMetadata) {
        val currentSelected = _uiState.value.selectedUris
        val newSelected = if (currentSelected.contains(item.uri)) {
            currentSelected - item.uri
        } else {
            currentSelected + item.uri
        }
        _uiState.value = _uiState.value.copy(selectedUris = newSelected)
    }

    /**
     * 全选/取消全选
     */
    fun toggleSelectAll() {
        val allUris = _uiState.value.audioList.map { it.uri }.toSet()
        val currentSelected = _uiState.value.selectedUris
        val newSelected = if (currentSelected.size == allUris.size) {
            emptySet()  // 已全选，则取消全选
        } else {
            allUris  // 否则全选
        }
        _uiState.value = _uiState.value.copy(selectedUris = newSelected)
    }

    /**
     * 移除选中的项目（从列表中移除，不删除源文件）
     */
    fun removeSelectedItems() {
        val selectedUris = _uiState.value.selectedUris
        if (selectedUris.isEmpty()) return

        val updatedList = _uiState.value.audioList.filter { it.uri !in selectedUris }
        val removedCount = _uiState.value.audioList.size - updatedList.size

        _uiState.value = _uiState.value.copy(
            audioList = updatedList,
            selectedUris = emptySet(),
            isSelectionMode = false,
            message = "已移除 $removedCount 个文件"
        )
    }

    /**
     * 优化选中文件的文件名（处理违禁词）
     *
     * 优化策略:
     * 1. 字符对调: "敏感" → "感敏"
     * 2. 插入分隔符: "敏感" → "敏_感"
     * 3. 删除违禁词
     *
     * 注意：此功能只修改文件名，不修改文件内的元数据标签。
     * 这样可以绕过平台的文件名检测，同时保留歌曲的原始标题信息。
     */
    fun optimizeSelectedFileNames() {
        val selectedUris = _uiState.value.selectedUris
        if (selectedUris.isEmpty()) {
            _uiState.value = _uiState.value.copy(message = "请先选择要优化的文件")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val selectedItems = _uiState.value.audioList.filter { it.uri in selectedUris }
            var optimizedCount = 0
            var skippedCount = 0  // 无需优化的文件
            val failedItems = mutableListOf<Pair<String, String>>()  // 文件名 -> 错误原因
            val updatedList = _uiState.value.audioList.toMutableList()

            for (item in selectedItems) {
                try {
                    // 获取文件名（不含扩展名）
                    val nameWithoutExt = item.displayName.substringBeforeLast('.', item.displayName)
                    val extension = item.displayName.substringAfterLast('.', "")

                    // 优化文件名
                    val result = fileNameOptimizer.optimize(nameWithoutExt)

                    if (!result.isChanged) {
                        skippedCount++
                        continue
                    }

                    // 构建新文件名
                    val newDisplayName = if (extension.isNotEmpty()) {
                        "${result.optimizedName}.$extension"
                    } else {
                        result.optimizedName
                    }

                    // 执行重命名（只改文件名，不改元数据）
                    val renameResult = withContext(Dispatchers.IO) {
                        tagEditor.renameFile(item.uri, newDisplayName)
                    }

                    when (renameResult) {
                        is RenameResult.Success -> {
                            // 重新读取文件信息
                            val updatedItem = withContext(Dispatchers.IO) {
                                tagEditor.readFromUri(renameResult.newUri)
                            }
                            if (updatedItem != null) {
                                val index = updatedList.indexOfFirst { it.uri == item.uri }
                                if (index >= 0) {
                                    updatedList[index] = updatedItem
                                }
                                optimizedCount++
                            } else {
                                failedItems.add(item.displayName to "重命名后无法读取文件")
                            }
                        }
                        is RenameResult.Error -> {
                            failedItems.add(item.displayName to renameResult.message)
                        }
                    }
                } catch (e: Exception) {
                    failedItems.add(item.displayName to (e.message ?: "未知错误"))
                }
            }

            val message = buildString {
                if (optimizedCount > 0) {
                    append("✓ 已优化 $optimizedCount 个文件名")
                }
                if (skippedCount > 0) {
                    if (isNotEmpty()) append("，")
                    append("$skippedCount 个无需优化")
                }
                if (failedItems.isNotEmpty()) {
                    if (isNotEmpty()) append("\n")
                    append("✗ ${failedItems.size} 个失败:")
                    failedItems.take(3).forEach { (name, reason) ->
                        append("\n  · $name: $reason")
                    }
                    if (failedItems.size > 3) {
                        append("\n  ...还有 ${failedItems.size - 3} 个")
                    }
                }
                if (optimizedCount == 0 && skippedCount == 0 && failedItems.isEmpty()) {
                    append("选中的文件无需优化")
                }
            }

            _uiState.value = _uiState.value.copy(
                audioList = updatedList,
                selectedUris = emptySet(),
                isSelectionMode = false,
                isLoading = false,
                message = message
            )
        }
    }

    /**
     * 从文件路径加载音频（用于导入转换后的文件）
     */
    fun loadAudioFilesFromPaths(paths: List<String>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val items = withContext(Dispatchers.IO) {
                paths.mapNotNull { path ->
                    val file = java.io.File(path)
                    if (file.exists()) {
                        tagEditor.read(file, android.net.Uri.fromFile(file))
                    } else null
                }
            }

            _uiState.value = _uiState.value.copy(
                audioList = _uiState.value.audioList + items,
                isLoading = false,
                message = "已导入 ${items.size} 个文件"
            )
        }
    }

    // ==================== 敏感词检测功能 ====================

    /**
     * 初始化敏感词检测器
     */
    init {
        viewModelScope.launch {
            sensitiveChecker.initialize()
            // 初始化完成后更新词库到 UI 状态
            _uiState.value = _uiState.value.copy(
                sensitiveWords = sensitiveChecker.getWordSet()
            )
        }
    }

    /**
     * 检测单个文件名
     */
    fun checkSensitiveWord(item: AudioMetadata) {
        val textToCheck = buildString {
            append(item.displayName)
            if (item.title.isNotEmpty()) append(" ${item.title}")
            if (item.artist.isNotEmpty()) append(" ${item.artist}")
            if (item.album.isNotEmpty()) append(" ${item.album}")
        }
        checkSensitiveText(textToCheck)
    }

    /**
     * 检测指定文本
     */
    fun checkSensitiveText(text: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                showSensitiveCheck = true,
                sensitiveCheckText = text,
                sensitiveCheckResult = null,
                isSensitiveChecking = true
            )

            val result = sensitiveChecker.checkOffline(text)

            _uiState.value = _uiState.value.copy(
                sensitiveCheckResult = result,
                isSensitiveChecking = false
            )
        }
    }

    /**
     * 批量检测所有文件
     */
    fun batchCheckSensitive() {
        if (_uiState.value.audioList.isEmpty()) {
            _uiState.value = _uiState.value.copy(message = "没有文件可检测")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            var issueCount = 0
            val allFoundWords = mutableListOf<String>()

            _uiState.value.audioList.forEach { item ->
                val textToCheck = "${item.displayName} ${item.title} ${item.artist}"
                val result = sensitiveChecker.checkOffline(textToCheck)
                if (!result.isClean) {
                    issueCount++
                    allFoundWords.addAll(result.foundWords.map { it.word })
                }
            }

            val message = if (issueCount == 0) {
                "全部文件通过检测，未发现敏感词"
            } else {
                "检测到 $issueCount 个文件包含敏感词: ${allFoundWords.distinct().take(5).joinToString()}"
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                message = message
            )
        }
    }

    /**
     * 关闭敏感词检测对话框
     */
    fun closeSensitiveCheck() {
        _uiState.value = _uiState.value.copy(
            showSensitiveCheck = false,
            sensitiveCheckText = "",
            sensitiveCheckResult = null
        )
    }

    // ==================== 修复扩展名功能 ====================

    /**
     * 修复文件扩展名（重命名文件）
     */
    fun fixFileExtension(item: AudioMetadata) {
        if (!item.isFormatMismatch) {
            _uiState.value = _uiState.value.copy(message = "文件扩展名正确，无需修复")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val result = withContext(Dispatchers.IO) {
                tagEditor.renameFile(item.uri, item.correctedDisplayName)
            }

            when (result) {
                is RenameResult.Success -> {
                    // 重新读取文件信息并更新列表
                    val updatedItem = withContext(Dispatchers.IO) {
                        tagEditor.readFromUri(result.newUri)
                    }

                    if (updatedItem != null) {
                        val updatedList = _uiState.value.audioList.map {
                            if (it.uri == item.uri) updatedItem else it
                        }
                        _uiState.value = _uiState.value.copy(
                            audioList = updatedList,
                            selectedItem = updatedItem,  // 更新选中项
                            isLoading = false,
                            message = "已修复: ${item.displayName} → ${updatedItem.displayName}"
                        )
                    } else {
                        // 重命名成功但无法重新读取，从列表中移除
                        val updatedList = _uiState.value.audioList.filter { it.uri != item.uri }
                        _uiState.value = _uiState.value.copy(
                            audioList = updatedList,
                            selectedItem = null,
                            isLoading = false,
                            message = "已修复扩展名，但无法读取新文件"
                        )
                    }
                }
                is RenameResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        message = "修复失败: ${result.message}"
                    )
                }
            }
        }
    }

    // ==================== 雷达扫描功能 ====================

    /**
     * 显示雷达扫描对话框，并检测可用的音频路径
     */
    fun showRadarScanDialog() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                showRadarDialog = true,
                scannedItems = emptyList(),
                selectedScannedUris = emptySet(),
                isScanning = true,  // 先显示加载状态
                scanProgress = 0f,
                scanPaths = emptyList(),
                availablePaths = emptyList()
            )

            // 在后台检测可用路径
            val paths = withContext(Dispatchers.IO) {
                detectAvailablePaths()
            }

            _uiState.value = _uiState.value.copy(
                isScanning = false,
                availablePaths = paths,
                // 默认选中所有路径
                scanPaths = paths.map { it.path }
            )
        }
    }

    /**
     * 关闭雷达扫描对话框
     */
    fun dismissRadarDialog() {
        _uiState.value = _uiState.value.copy(
            showRadarDialog = false,
            scannedItems = emptyList(),
            selectedScannedUris = emptySet(),
            isScanning = false,
            scanProgress = 0f,
            scanPaths = emptyList(),
            availablePaths = emptyList()
        )
    }

    /**
     * 切换扫描路径的选中状态
     */
    fun toggleScanPath(path: String) {
        val currentPaths = _uiState.value.scanPaths
        val newPaths = if (currentPaths.contains(path)) {
            currentPaths - path
        } else {
            currentPaths + path
        }
        _uiState.value = _uiState.value.copy(scanPaths = newPaths)
    }

    /**
     * 全选/取消全选扫描路径
     */
    fun toggleAllScanPaths() {
        val allPaths = _uiState.value.availablePaths.map { it.path }
        val currentPaths = _uiState.value.scanPaths
        val newPaths = if (currentPaths.size == allPaths.size) {
            emptyList()
        } else {
            allPaths
        }
        _uiState.value = _uiState.value.copy(scanPaths = newPaths)
    }

    /**
     * 检测设备上有音频文件的目录
     */
    private fun detectAvailablePaths(): List<ScanPathOption> {
        val context = getApplication<Application>()
        val pathCounts = mutableMapOf<String, Int>()

        val projection = arrayOf(
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION
        )

        // 只统计 > 30秒的音频
        val selection = "${MediaStore.Audio.Media.DURATION} > ?"
        val selectionArgs = arrayOf("30000")

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataColumn) ?: continue
                    // 提取顶级目录 (如 /storage/emulated/0/Music)
                    val topDir = extractTopDirectory(path)
                    if (topDir != null) {
                        pathCounts[topDir] = (pathCounts[topDir] ?: 0) + 1
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 按文件数量排序，生成选项列表
        return pathCounts.entries
            .sortedByDescending { it.value }
            .map { (path, count) ->
                ScanPathOption(
                    path = path,
                    displayName = formatPathForDisplay(path),
                    audioCount = count,
                    isSelected = true
                )
            }
    }

    /**
     * 从完整路径提取顶级目录
     * 例如: /storage/emulated/0/Music/artist/song.mp3 -> /storage/emulated/0/Music
     */
    private fun extractTopDirectory(fullPath: String): String? {
        // 常见的存储根目录
        val storageRoots = listOf(
            "/storage/emulated/0/",
            "/storage/emulated/legacy/",
            "/sdcard/"
        )

        for (root in storageRoots) {
            if (fullPath.startsWith(root)) {
                val relativePath = fullPath.removePrefix(root)
                val firstDir = relativePath.split("/").firstOrNull { it.isNotEmpty() }
                if (firstDir != null) {
                    return root + firstDir
                }
            }
        }

        // 外部 SD 卡: /storage/XXXX-XXXX/
        if (fullPath.startsWith("/storage/")) {
            val parts = fullPath.removePrefix("/storage/").split("/")
            if (parts.size >= 2) {
                return "/storage/${parts[0]}/${parts[1]}"
            }
        }

        return null
    }

    /**
     * 格式化路径用于显示
     */
    private fun formatPathForDisplay(path: String): String {
        return path
            .replace("/storage/emulated/0/", "")
            .replace("/storage/emulated/legacy/", "")
            .replace("/sdcard/", "")
            .ifEmpty { path }
    }

    /**
     * 开始雷达扫描 - 使用 MediaStore API 查询选中路径下的音频文件
     */
    fun startRadarScan() {
        val selectedPaths = _uiState.value.scanPaths
        if (selectedPaths.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                message = "请至少选择一个扫描目录"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isScanning = true,
                scanProgress = 0f,
                scannedItems = emptyList(),
                selectedScannedUris = emptySet()
            )

            val items = withContext(Dispatchers.IO) {
                scanAudioFiles(selectedPaths)
            }

            _uiState.value = _uiState.value.copy(
                isScanning = false,
                scanProgress = 1f,
                scannedItems = items
            )
        }
    }

    /**
     * 使用 MediaStore API 扫描指定路径下的音频文件
     *
     * @param pathFilters 要扫描的目录路径列表，为空则扫描全部
     */
    private fun scanAudioFiles(pathFilters: List<String> = emptyList()): List<ScannedAudioItem> {
        val context = getApplication<Application>()
        val items = mutableListOf<ScannedAudioItem>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATA  // 文件路径
        )

        // 构建过滤条件
        val selectionBuilder = StringBuilder()
        val selectionArgsList = mutableListOf<String>()

        // 时长过滤：> 30秒
        selectionBuilder.append("${MediaStore.Audio.Media.DURATION} > ?")
        selectionArgsList.add("30000")

        // 路径过滤
        if (pathFilters.isNotEmpty()) {
            selectionBuilder.append(" AND (")
            pathFilters.forEachIndexed { index, path ->
                if (index > 0) selectionBuilder.append(" OR ")
                selectionBuilder.append("${MediaStore.Audio.Media.DATA} LIKE ?")
                selectionArgsList.add("$path/%")
            }
            selectionBuilder.append(")")
        }

        val selection = selectionBuilder.toString()
        val selectionArgs = selectionArgsList.toTypedArray()
        val sortOrder = "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    val item = ScannedAudioItem(
                        uri = contentUri,
                        displayName = cursor.getString(displayNameColumn) ?: "未知文件",
                        artist = cursor.getString(artistColumn),
                        album = cursor.getString(albumColumn),
                        duration = cursor.getLong(durationColumn),
                        size = cursor.getLong(sizeColumn),
                        path = cursor.getString(dataColumn)
                    )
                    items.add(item)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return items
    }

    /**
     * 切换扫描结果项的选中状态
     */
    fun toggleScannedItemSelection(uri: Uri) {
        val currentSelected = _uiState.value.selectedScannedUris
        val newSelected = if (currentSelected.contains(uri)) {
            currentSelected - uri
        } else {
            currentSelected + uri
        }
        _uiState.value = _uiState.value.copy(selectedScannedUris = newSelected)
    }

    /**
     * 全选所有可选的扫描结果 (排除已导入的)
     */
    fun selectAllScannedItems() {
        val existingUris = _uiState.value.audioList.map { it.uri }.toSet()
        val allSelectableUris = _uiState.value.scannedItems
            .filter { it.uri !in existingUris }
            .map { it.uri }
            .toSet()
        _uiState.value = _uiState.value.copy(selectedScannedUris = allSelectableUris)
    }

    /**
     * 取消全选扫描结果
     */
    fun deselectAllScannedItems() {
        _uiState.value = _uiState.value.copy(selectedScannedUris = emptySet())
    }

    /**
     * 导入选中的扫描结果
     */
    fun importSelectedScannedItems() {
        val selectedUris = _uiState.value.selectedScannedUris.toList()
        if (selectedUris.isEmpty()) return

        // 关闭对话框
        _uiState.value = _uiState.value.copy(
            showRadarDialog = false,
            scannedItems = emptyList(),
            selectedScannedUris = emptySet()
        )

        // 复用现有的加载逻辑
        loadAudioFiles(selectedUris)
    }

    /**
     * 获取已导入文件的 URI 集合 (用于在扫描结果中标记已导入)
     */
    fun getExistingUris(): Set<Uri> {
        return _uiState.value.audioList.map { it.uri }.toSet()
    }
}

/**
 * 重命名操作结果
 */
sealed class RenameResult {
    data class Success(val newUri: Uri) : RenameResult()
    data class Error(val message: String) : RenameResult()
}

/**
 * 扫描路径选项
 */
data class ScanPathOption(
    val path: String,
    val displayName: String,
    val audioCount: Int = 0,
    val isSelected: Boolean = false
)

/**
 * 音频保存事件（替代 LocalBroadcastManager）
 */
sealed class AudioSavedEvent {
    /** 单个文件保存 */
    data class Single(val filePath: String, val uri: Uri) : AudioSavedEvent()
    /** 批量保存 */
    data class Batch(val filePaths: List<String>) : AudioSavedEvent()
}
