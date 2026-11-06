# 拾语 Android

当前版本：`0.18.3 / versionCode 33`，只提供一个完整安装包：

- `com.shiyu.chat`：内置本地语音识别、角色朗读、MNN 和图片生成入口。

技术栈：Kotlin、Jetpack Compose、Room schema 11、WorkManager、OkHttp、
Android Keystore、sherpa-onnx、MNN LLM 和 Local Dream。

## 已实现

- Debug 与 Release 都支持 OpenCode Go `deepseek-v4.1-flash` 与 DeepSeek `deepseek-flash`。
- 每个角色可独立选择云端 API 或手机本地 MNN 模型。
- 创建、编辑、删除角色；角色卡、世界书、知识边界和固定事实。
- 一个角色只有一个持续对话，支持主动剧情开场和本地历史。
- 云端结构化长期记忆与持久后台任务。
- 本地 MNN 模式使用最近 24 条原话，并对更早消息做滚动摘要。
- 内部心情、熟悉、信任、温暖、关系阶段与用户边界。
- sherpa-onnx 本地 ASR、VITS zh-ll 本地 TTS；模型含 5 个 speaker，
  去重后提供女声 1、男声 1、女声 2、男声 2 四个选项，支持试听和按角色保存。
- 千问图片 API 与内置 Local Dream SD1.5/QNN 生图。
- 设置中按设备自动选择并下载 MeinaMix V12，显示进度、校验大小与 SHA-256。
- 内置李白、宋知意、裴既明和萧承渊；删除顾清禾、江昭谌、许澄与旧实验角色。
- API Key 使用 Android Keystore；聊天、记忆、图片和状态保存在本机。

## 模型

构建出的应用包含运行库和语音资源，不包含：

- Qwen3 MNN 权重；
- MeinaMix、DreamShaper 等 Local Dream SD1.5 QNN 模型；
- 千问图片 API Key。

在“设置 → 对话与模型”中下载 Q8 MNN 或导入模型 ZIP；在“设置 → 图片生成”中
下载 MeinaMix V12 或导入 Local Dream QNN ZIP。MNN 包需要包含 `config.json`、
`llm.mnn`、`llm.mnn.weight` 和 `tokenizer.mtok`。同一时间只保留一个 MNN 和一个
Dream 模型槽位，下载失败的临时文件会自动清理。
语音识别和角色朗读资源均随完整安装包提供，不再维护单独下载或导入语音包的版本。

本地 MNN 模式只支持文字；需要图片理解时切换到云端模式。

相关模型与 LoRA 实验：
<https://github.com/bawdwl/qwen3-1.7b-lora>

公开 MNN Q8 模型：

- 宋知意：
  <https://modelscope.cn/models/Alixs666/shiyu-qwen3-1.7b-song-zhiyi-mnn-q8>
- 裴既明（实验版）：
  <https://modelscope.cn/models/Alixs666/shiyu-qwen3-1.7b-pei-jiming-mnn-q8>

## 构建

需要 JDK 17、Android SDK 35 和 Git LFS。

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-17"
$env:ANDROID_HOME = "C:\path\to\Android\Sdk"
.\gradlew.bat testDebugUnitTest assembleDebug lintRelease assembleRelease --no-daemon
```

也可以使用 Android Studio 打开 `android/` 并运行 `app`。

输出：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## 当前验证

- 64 项 JVM 单元测试通过。
- Release Lint、Debug 构建和关闭 R8 的 Release 构建通过。
- `0.18.3` 源码已通过 JVM 测试、Release Lint 和 APK 构建。
- 预编译 APK 仅作为 GitHub Release 附件分发，不提交到 Git：
  <https://github.com/bawdwl/shiyu-ai-companion-chat/releases/tag/v0.18.3>

## 已知限制

- 没有 PC 与手机自动同步，也没有“手机输入、电脑推理”的当前产品入口。
- 本地 MNN 模式不支持图片。
- Android 未内置 BGE，长期记忆检索使用 FTS4 与词法排序。
- 本地滚动摘要可能漏记或合并错误。
- Local Dream 采用 CC BY-NC 4.0，只能用于非商业演示。
- Local Dream 的 NPU 路径目前要求 Qualcomm Hexagon V68+；非高通或未知平台使用千问图片 API。
- 当前不是实时双向语音，也没有自动打断。
