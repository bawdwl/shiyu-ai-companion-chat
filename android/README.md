# 拾语 Android

当前版本：`0.15.0 / versionCode 28`

技术栈：Kotlin、Jetpack Compose、Room schema 11、WorkManager、OkHttp、
Android Keystore、sherpa-onnx、MNN LLM 和 Local Dream。

## 已实现

- Debug 构建支持 OpenCode Go `deepseek-v4.1-flash` 与 DeepSeek `deepseek-flash`；
  公开 Release 只显示 DeepSeek。
- 每个角色可独立选择云端 API 或手机本地 MNN 模型。
- 创建、编辑、删除角色；角色卡、世界书、知识边界和固定事实。
- 一个角色只有一个持续对话，支持主动剧情开场和本地历史。
- 云端结构化长期记忆与持久后台任务。
- 本地 MNN 模式使用最近 24 条原话，并对更早消息做滚动摘要。
- 内部心情、熟悉、信任、温暖、关系阶段与用户边界。
- sherpa-onnx 本地 ASR、Kokoro 本地 TTS；开放模型全部 103 个音色
  （100 个中文音色、Maple/Sol/Vale 三个英文女声），支持搜索和按角色保存选择。
- 千问图片 API 与内置 Local Dream SD1.5/QNN 生图。
- 设置中按设备自动选择并下载 MeinaMix V12，显示进度、校验大小与 SHA-256。
- 内置李白、宋知意、江昭谌、裴既明；删除许澄与旧实验角色。
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
.\scripts\build_android.ps1
```

也可以使用 Android Studio 打开 `android/` 并运行 `app`。

输出：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## 当前验证

- JVM 单元测试：63 项通过。
- `testDebugUnitTest` 与 `assembleDebug` 通过。
- `0.15.0` 源码已通过 JVM 测试和 APK 构建。
- 后续移动端仍会继续调整，因此 GitHub 仓库暂不提供预编译 APK。

## 已知限制

- 没有 PC 与手机自动同步，也没有“手机输入、电脑推理”的当前产品入口。
- 本地 MNN 模式不支持图片。
- Android 未内置 BGE，长期记忆检索使用 FTS4 与词法排序。
- 本地滚动摘要可能漏记或合并错误。
- Local Dream 采用 CC BY-NC 4.0，只能用于非商业演示。
- Local Dream 的 NPU 路径目前要求 Qualcomm Hexagon V68+；非高通或未知平台使用千问图片 API。
- 当前不是实时双向语音，也没有自动打断。
