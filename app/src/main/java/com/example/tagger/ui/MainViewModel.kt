package com.example.tagger.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tagger.core.SensitiveCheckResult
import com.example.tagger.core.SensitiveWordChecker
import com.example.tagger.core.TagEditor
import com.example.tagger.core.WriteResult
import com.example.tagger.model.AudioMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    val selectedUris: Set<Uri> = emptySet()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val tagEditor = TagEditor(application)
    private val sensitiveChecker = SensitiveWordChecker(application)

    private val _uiState = MutableStateFlow(MainUiState())
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
     * 保存编辑后的元数据
     */
    fun saveItem(item: AudioMetadata) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val result = withContext(Dispatchers.IO) {
                tagEditor.writeToUri(item.uri, item)
            }

            when (result) {
                is WriteResult.Success -> {
                    // 更新列表中的项目
                    val updatedList = _uiState.value.audioList.map {
                        if (it.uri == item.uri) item else it
                    }
                    _uiState.value = _uiState.value.copy(
                        audioList = updatedList,
                        selectedItem = null,
                        isLoading = false,
                        message = "保存成功"
                    )
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

            var successCount = 0
            var failCount = 0
            withContext(Dispatchers.IO) {
                _uiState.value.audioList.forEach { item ->
                    when (tagEditor.writeToUri(item.uri, item)) {
                        is WriteResult.Success -> successCount++
                        is WriteResult.Error -> failCount++
                    }
                }
            }

            val message = if (failCount == 0) {
                "已保存 $successCount 个文件"
            } else {
                "保存完成: $successCount 成功, $failCount 失败（部分文件格式不支持）"
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                message = message
            )
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
}
