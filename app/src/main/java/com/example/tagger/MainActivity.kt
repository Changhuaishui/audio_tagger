package com.example.tagger

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.tagger.ui.MainScreen
import com.example.tagger.ui.MainViewModel
import com.example.tagger.ui.theme.AudioTaggerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchFilePicker()
        } else {
            Toast.makeText(this, "需要音频读取权限", Toast.LENGTH_SHORT).show()
        }
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: Exception) {}
            }
            viewModel.loadAudioFiles(uris)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AudioTaggerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by viewModel.uiState.collectAsState()

                    MainScreen(
                        uiState = uiState,
                        onPickFiles = { checkPermissionAndPickFiles() },
                        onSelectItem = { viewModel.selectItem(it) },
                        onSaveItem = { viewModel.saveItem(it) },
                        onRemoveItem = { viewModel.removeItem(it) },
                        onBatchFill = { viewModel.batchFillFromFileName() },
                        onBatchSave = { viewModel.batchSaveAll() },
                        onClearAll = { viewModel.clearAll() },
                        onClearMessage = { viewModel.clearMessage() },
                        // 敏感词检测
                        onBatchCheckSensitive = { viewModel.batchCheckSensitive() },
                        onCheckSensitive = { viewModel.checkSensitiveWord(it) },
                        onCloseSensitiveCheck = { viewModel.closeSensitiveCheck() },
                        // 封面选择
                        onPickCover = { viewModel.updateSelectedItemCover(it) },
                        // 多选模式
                        onToggleSelectionMode = { viewModel.toggleSelectionMode() },
                        onToggleItemSelection = { viewModel.toggleItemSelection(it) },
                        onToggleSelectAll = { viewModel.toggleSelectAll() },
                        onRemoveSelected = { viewModel.removeSelectedItems() }
                    )
                }
            }
        }
    }

    private fun checkPermissionAndPickFiles() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                launchFilePicker()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_AUDIO) -> {
                Toast.makeText(this, "需要音频读取权限来编辑标签", Toast.LENGTH_LONG).show()
                permissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
            }
            else -> {
                permissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
            }
        }
    }

    private fun launchFilePicker() {
        filePickerLauncher.launch(
            arrayOf(
                "audio/mpeg",
                "audio/mp4",
                "audio/flac",
                "audio/ogg",
                "audio/x-wav",
                "audio/wav",
                "audio/*"
            )
        )
    }
}
