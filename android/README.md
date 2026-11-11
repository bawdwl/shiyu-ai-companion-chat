# 拾语 Android

当前源码版本：`0.19.1 / versionCode 56`，Room schema 13，提供两个安装版本：

- `com.shiyu.chat`：完整版，内置本地语音识别、角色朗读、MNN 和 Local Dream。
- `com.shiyu.chat.cloud`：云端精简版，移除 MNN、QNN、Local Dream、ASR 和 TTS。

技术栈：Kotlin、Jetpack Compose、Room schema 13、WorkManager、OkHttp、
Android Keystore、sherpa-onnx、MNN LLM 和 Local Dream。

## 已实现

- 创建角色使用设置页当前选择的云端 Provider，不再固定走 OpenCode Go。
- Debug 与 Release 都支持 OpenCode Go `deepseek-v4.1-flash` 与 DeepSeek 官方
  `deepseek-chat`；OpenCode 专用 `thinking` 字段不会进入官方 DeepSeek 请求。
- 首页、消息、创建、设置四个固定底部页签；首页支持上下循环浏览角色，聊天返回时回到进入前页签。
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
- 内置 8 个初始角色：宋知意、裴既明、李白、萧承渊、姜拂衣、
  空条承太郎、凌清鸢和沈砚辞，并使用同一套角色档案、头像和聊天背景。
- 角色管理页使用独立缩略图缓存，限制并发图片解码并减少列表绘制开销。
- API Key 使用 Android Keystore；聊天、记忆、图片和状态保存在本机。
- 聊天页使用固定全屏角色背景、默认仅显示最新角色回复，并提供独立历史开关；键盘弹出时消息和输入栏上移。输入栏包含“（）”动作快捷插入，并可从菜单回退到任意一条用户发言之前。
- Local Dream 保留模型生成的方形原图；用户先选择 `9:16` 聊天背景，再从该背景
  中选择 `1:1` 头像。相册导入使用相同流程，不再自动执行中间裁切。
- Dream 默认优先使用 `1024.patch`、50 步、DPM++ 2M SDE Karras；设置页可切换 512/768/1024、5 种调度器与 Karras。
- 默认正面模板仅保留 `masterpiece, best quality, anime illustration, three-quarter upper body, {appearance}`；新角色要求更详细的中文外貌和 30–50 个英文视觉标签。

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
.\gradlew.bat testFullDebugUnitTest assembleFullDebug lintFullDebug --no-daemon
.\gradlew.bat testCloudDebugUnitTest assembleCloudDebug lintCloudDebug --no-daemon
```

也可以使用 Android Studio 打开 `android/` 并运行 `app`。

输出：

```text
android/app/build/outputs/apk/full/debug/app-full-debug.apk
android/app/build/outputs/apk/cloud/debug/app-cloud-debug.apk
```

## 当前验证

- `0.19.1` 源码通过完整版与云端版各 104 项 JVM 测试、Debug Lint 和 APK 构建。
- 完整版与云端版均执行覆盖安装验证，不清理已有用户数据。
- 覆盖安装后两个包均确认只保留上述 8 个默认角色，头像与背景路径有效。
- 预编译 APK 仅作为 GitHub Release 附件分发，不提交到 Git：
  <https://github.com/bawdwl/shiyu-ai-companion-chat/releases/tag/v0.19.0>

## 已知限制

- 没有 PC 与手机自动同步，也没有“手机输入、电脑推理”的当前产品入口。
- 本地 MNN 模式不支持图片。
- Android 未内置 BGE，长期记忆检索使用 FTS4 与词法排序。
- 本地滚动摘要可能漏记或合并错误。
- Local Dream 采用 CC BY-NC 4.0，只能用于非商业演示。
- Local Dream 的 NPU 路径目前要求 Qualcomm Hexagon V68+；非高通或未知平台使用千问图片 API。
- 当前不是实时双向语音，也没有自动打断。
