# AudioTagger - AI Agent 项目指南

> 本文件面向 AI 编程助手。阅读者应假设对该项目一无所知。

## 项目概述

AudioTagger 是一款 Android 音频标签编辑器应用，使用 Kotlin + Jetpack Compose 构建。核心功能包括：

- **音频标签编辑**：读取和写入 MP3、FLAC、M4A、OGG、WAV 等格式的元数据（标题、艺术家、专辑、封面等）
- **视频音轨提取**：基于 FFmpeg 从视频中提取音频到公共音乐目录
- **敏感词检测与优化**：离线本地词库检测文件名/元数据中的敏感词，支持字符对调、插入分隔符、删除等优化策略
- **雷达扫描**：通过 MediaStore API 扫描设备上的音频文件并批量导入
- **批量处理**：从文件名填充标签、批量保存、批量优化违禁词
- **处理方案**：自定义替换规则、混淆重命名（编号/随机/日期模式）

应用包名：`com.example.tagger`，目标 API 36+，最低支持 API 33（Android 13）。

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 语言 | Kotlin 2.0.21 |
| UI 框架 | Jetpack Compose + Material3 (BOM 2026.01.00) |
| 构建系统 | Gradle with Kotlin DSL (AGP 9.0.0) |
| 架构模式 | MVVM（ViewModel + StateFlow/SharedFlow）|
| 数据持久化 | DataStore Preferences |
| 音频标签 | JAudioTagger 3.0.1 + mp4parser 1.9.56（M4A 专用）|
| 视频处理 | FFmpegKit full-gpl 1.1.0 (`com.antonkarpenko`) |
| 协程调度 | Kotlin Coroutines（Dispatchers.IO 用于文件操作）|

---

## 项目结构

```
audio_tagger/
├── app/src/main/java/com/example/tagger/
│   ├── MainActivity.kt              # 主 Activity：权限申请、文件选择器、Compose 入口
│   ├── core/                        # 核心业务逻辑层
│   │   ├── TagEditor.kt             # 音频标签读写（JAudioTagger / MediaMetadataRetriever / M4aTagWriter）
│   │   ├── M4aTagWriter.kt          # M4A 标签写入器（基于 mp4parser）
│   │   ├── SensitiveWordChecker.kt  # 敏感词离线检测器
│   │   ├── FileNameOptimizer.kt     # 文件名违禁词优化（对调/分隔符/删除）
│   │   ├── FileNameProcessor.kt     # 替换规则与混淆处理
│   │   ├── MediaScannerUtil.kt      # MediaScanner 广播工具
│   │   └── video/                   # 视频提取模块
│   │       ├── VideoExtractor.kt    # FFmpeg 音轨提取核心
│   │       ├── VideoMetadata.kt     # 视频元数据模型
│   │       ├── AudioFormat.kt       # 输出格式枚举
│   │       ├── ExtractionConfig.kt  # 提取配置
│   │       └── ExtractionResult.kt  # 提取结果
│   ├── data/                        # 数据层
│   │   ├── UserPreferencesRepository.kt  # DataStore 持久化（替换规则、混淆映射、计数器）
│   │   └── PreferencesKeys.kt       # DataStore Key 定义
│   ├── model/                       # 数据模型
│   │   ├── AudioMetadata.kt         # 音频元数据（含封面、格式检测）
│   │   ├── ReplacementRule.kt       # 替换规则模型
│   │   ├── ScannedAudioItem.kt      # 雷达扫描结果项
│   │   ├── ObfuscationMode.kt       # 混淆模式枚举
│   │   └── ObfuscationMapping.kt    # 混淆映射记录
│   └── ui/                          # UI 层（Jetpack Compose）
│       ├── MainScreen.kt            # 主界面（含版本号显示）
│       ├── MainViewModel.kt         # 主界面 ViewModel（状态管理核心）
│       ├── EditorDialog.kt          # 标签编辑对话框
│       ├── RadarScanDialog.kt       # 雷达扫描对话框
│       ├── ProcessSchemeDialog.kt   # 处理方案对话框
│       ├── ReplacementRulesSheet.kt # 替换规则底部弹窗
│       ├── ObfuscationModeSheet.kt  # 混淆模式选择弹窗
│       ├── SensitiveCheckDialog.kt  # 敏感词检测结果对话框
│       ├── SensitiveTextField.kt    # 带实时敏感词检测的输入框
│       ├── theme/                   # 主题系统
│       │   ├── Color.kt             # Apple Music 风格配色（红色主题）
│       │   ├── Theme.kt             # 主题定义
│       │   └── Type.kt              # 字体排版
│       └── video/                   # 视频提取 UI
│           ├── VideoViewModel.kt
│           ├── ExtractionProgressDialog.kt
│           └── FormatSelectorSheet.kt
├── app/src/main/res/raw/
│   ├── sensitive_words.txt          # 默认敏感词库
│   └── sensitive_words_new.txt      # 扩展敏感词库
├── app/src/main/resources/
│   └── isoparser-default.properties # mp4parser 必需的资源映射文件
├── gradle/libs.versions.toml        # 版本目录（Version Catalog）
└── app/build.gradle.kts             # 应用模块构建配置
```

---

## 构建与运行

### 环境要求

- Android Studio（支持 AGP 9.0.0）
- JDK 17+
- Android SDK API 36
- Gradle Wrapper（项目已包含）

### 常用构建命令

```bash
# Debug 构建 APK
./gradlew assembleDebug

# 清理后构建
./gradlew clean assembleDebug

# 安装到连接设备
./gradlew installDebug
```

### APK 输出位置

```
app/build/outputs/apk/debug/app-debug.apk
```

### 构建设置说明

- **ABI 过滤**：仅包含 `arm64-v8a`，以减少 APK 体积（主要因 FFmpeg 库体积大）。如需支持其他架构，修改 `app/build.gradle.kts` 中 `ndk.abiFilters`。
- **ProGuard**：Release 构建未启用代码混淆（`isMinifyEnabled = false`）。
- **Java 兼容性**：VERSION_11。

---

## 开发规范

### 版本号规范（**每次修改代码后必须更新**）

为确保运行时能够识别新 APK，**每次提交代码前必须同时更新以下两个位置的版本标识**：

1. **`app/src/main/java/com/example/tagger/ui/MainScreen.kt`**
   - 在 `LargeTopAppBar` 的 `title` 中：
   ```kotlin
   Text("音乐标签 [v0129e]", ...) // 版本标记 - 功能描述
   ```

2. **`app/src/main/java/com/example/tagger/MainActivity.kt`**（可选但强烈建议）
   - `VERSION_TAG` 常量：
   ```kotlin
   const val VERSION_TAG = "v0129e_mediastore_write_permission"
   ```

**版本号格式**：`v + 月日 + 字母序号`
- 示例：`v0120a` = 1月20日第1个版本，`v0120b` = 1月20日第2个版本

### 代码风格

- **注释语言**：中文（所有业务代码注释均使用中文）
- **日志规范**：每个类使用 `private const val TAG = "类名"`，使用 Android `Log.d/e/i/w`
- **协程规范**：所有文件/网络操作必须在 `Dispatchers.IO` 中执行
- **状态管理**：UI 状态使用 `MutableStateFlow` + `StateFlow`；事件使用 `MutableSharedFlow` + `SharedFlow`（已废弃 LocalBroadcastManager）
- **结果封装**：业务操作结果使用密封类（Sealed Class），如 `WriteResult`、`RenameResult`

### 文件组织原则

- `core/`：纯业务逻辑，不依赖 Compose，可独立单元测试
- `ui/`：Compose UI 组件和 ViewModel，ViewModel 持有 `Application` 上下文
- `model/`：纯数据类，无 Android 依赖（除 `Uri`/`Bitmap`）
- `data/`：持久化相关，使用 DataStore 存储用户偏好

---

## 测试说明

### 测试框架

- **单元测试**：JUnit 4（`test/` 目录）
- **仪器测试**：AndroidJUnit4 + Espresso（`androidTest/` 目录）

### 当前测试覆盖

目前测试非常基础，仅包含示例测试：
- `ExampleUnitTest.kt`：占位单元测试
- `ExampleInstrumentedTest.kt`：验证包名正确的仪器测试

### 运行测试

```bash
# 本地单元测试
./gradlew test

# 仪器测试（需要连接设备或模拟器）
./gradlew connectedAndroidTest
```

### 测试建议

由于项目涉及大量文件 I/O 和 Android 存储权限，建议：
- 对 `FileNameOptimizer`、`FileNameProcessor`、`SensitiveWordChecker` 添加纯 Kotlin 单元测试（无 Android 依赖）
- 对 `TagEditor` 和 `VideoExtractor` 使用仪器测试或 Mockito 模拟 `Context`/`ContentResolver`

---

## 核心模块说明

### TagEditor（标签编辑）

- **读取策略**：优先使用 `MediaMetadataRetriever`（更可靠，处理大文件更好），失败时 fallback 到 `JAudioTagger`
- **写入策略**：
  - M4A/MP4 容器 → `M4aTagWriter`（mp4parser）
  - AAC 裸流 → 不支持元数据，返回错误提示
  - MP3/FLAC/OGG/WAV → `JAudioTagger`
- **封面写入**：FLAC 使用 `MetadataBlockDataPicture` 直接写入（避免 ImageIO 问题）；其他格式使用标准 `ArtworkFactory`
- **扩展名修复**：自动检测实际格式与扩展名是否匹配，提供修复功能
- **重命名**：支持多种策略（MediaStore API、直接文件重命名、DocumentsContract、复制+删除），处理 Scoped Storage 限制

### VideoExtractor（视频提取）

- 使用 `FFmpegKit.execute()` 同步执行 FFmpeg 命令
- 支持输出格式：AAC、MP3、FLAC、WAV、OGG、Opus，以及保留原格式（`-c:a copy`）
- 提取后自动保存到 `外部存储/Music/AudioTagger/`，并广播 MediaScanner
- 使用 `MediaMetadataRetriever` 获取视频缩略图（优先嵌入封面，其次截取 1 秒处帧）

### 敏感词系统

- **词库位置**：`app/src/main/res/raw/sensitive_words.txt`
- **检测方式**：离线子串匹配（大小写不敏感）
- **优化策略优先级**：字符对调 → 插入分隔符 → 删除违禁词
- 分隔符候选：`_`、`·`、`-`、` `

---

## 安全与权限

### 声明权限

```xml
<!-- Android 13+ 细分媒体权限 -->
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />

<!-- Android 10-12 兼容 -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="29" />
```

### 存储安全

- 目标 Android 13+（Scoped Storage），使用 `MediaStore.createWriteRequest()` 请求批量写入权限
- 保留 `requestLegacyExternalStorage="true"` 用于兼容性
- 文件选择器使用 `OpenMultipleDocuments` / `OpenDocument`，获取持久化 URI 权限

### 敏感数据处理

- 敏感词词库存储在应用资源中，不上传网络
- 在线检测功能（零克查词 API）目前为占位实现，未实际调用外部 API
- 混淆映射关系保存在本地 DataStore，不上传

---

## 常见修改场景

### 添加新的音频格式支持

1. 在 `TagEditor.write()` 的 `when` 分支中添加格式判断
2. 如有需要，在 `AudioMetadata.correctExtension` 中添加扩展名映射
3. 在 `MainActivity.launchAudioFilePicker()` 的 MIME 类型数组中添加对应类型

### 修改 UI 主题色

编辑 `app/src/main/java/com/example/tagger/ui/theme/Color.kt`：
- 修改 `AppPrimaryColor` 可切换整个 App 的主色调
- 当前为 Apple Music 红色 `#FC3C44`

### 更新敏感词库

直接编辑 `app/src/main/res/raw/sensitive_words.txt`，每行一个词，以 `#` 开头为注释。

### 添加新的 FFmpeg 输出格式

1. 在 `core/video/AudioFormat.kt` 中添加格式枚举
2. 在 `VideoExtractor.buildFFmpegCommand()` 中添加编码器参数
3. 在 `FormatSelectorSheet.kt` UI 中添加选项

---

## 已知限制与注意事项

1. **M4A 封面写入**：`M4aTagWriter` 目前不支持写入封面（mp4parser API 限制），仅支持标题、艺术家、专辑、注释
2. **FFmpeg 体积**：因包含全功能编码器，APK 体积较大（~130MB），已通过 `arm64-v8a` 单 ABI 裁剪
3. **Scoped Storage 重命名**：在 Android 10+ 上，重命名文件可能需要用户通过系统对话框授予写入权限（`RecoverableSecurityException`）
4. **isoparser 资源文件**：`app/src/main/resources/isoparser-default.properties` 必须存在，否则 mp4parser 在 Android 上无法工作
5. **输入流关闭异常**：`TagEditor.copyToCache()` 中有特殊处理——某些 `file://` URI 在 `close()` 时会抛出 `EIO`，但数据已完整复制，因此忽略该异常
