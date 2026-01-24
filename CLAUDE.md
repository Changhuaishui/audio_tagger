# AudioTagger 开发规范

## 版本号规范

每次更新代码后，必须更新 UI 显示的版本号，便于识别运行的是新 APK。

### 版本号位置

```
app/src/main/java/com/example/tagger/ui/MainScreen.kt
```

在 `LargeTopAppBar` 的 `title` 中：

```kotlin
"音乐标签 [v0124a]", // 版本标记 - 功能描述
```

### 版本号格式

```
v + 月日 + 字母序号
```

示例：
- `v0120a` - 1月20日第一个版本
- `v0120b` - 1月20日第二个版本
- `v0124a` - 1月24日第一个版本

### 更新步骤

1. 修改 `MainScreen.kt` 中的版本号
2. 更新注释中的功能描述
3. 同步更新 `MainActivity.kt` 中的 `VERSION_TAG`（可选，用于日志调试）

## 项目结构

```
new_audio_tagger/
├── app/src/main/java/com/example/tagger/
│   ├── MainActivity.kt          # 主 Activity
│   ├── core/                    # 核心业务逻辑
│   │   ├── TagEditor.kt         # 音频标签读写
│   │   ├── SensitiveWordChecker.kt
│   │   └── video/               # 视频提取功能
│   │       ├── VideoExtractor.kt
│   │       ├── VideoMetadata.kt
│   │       └── ...
│   ├── model/                   # 数据模型
│   │   └── AudioMetadata.kt
│   └── ui/                      # UI 层
│       ├── MainScreen.kt        # 主界面（含版本号）
│       ├── MainViewModel.kt
│       ├── EditorDialog.kt
│       └── video/
└── README.md
```

## 关键功能模块

| 模块 | 文件 | 说明 |
|------|------|------|
| 标签编辑 | `TagEditor.kt` | JAudioTagger + MediaMetadataRetriever |
| 视频提取 | `VideoExtractor.kt` | FFmpegX-Android 音轨提取 |
| 敏感词检测 | `SensitiveWordChecker.kt` | 本地词库检测 |
| UI | `MainScreen.kt` | Jetpack Compose Material3 |

## 构建命令

```bash
# Debug 构建
./gradlew assembleDebug

# 清理后构建
./gradlew clean assembleDebug
```

APK 输出位置：`app/build/outputs/apk/debug/app-debug.apk`
