# 拾语 SHIYU

面向中文虚拟角色陪伴的本地优先聊天应用，提供 Android 原生客户端、Windows EXE 与
可复现的 PC 网页完整环境。

拾语将角色档案、世界书、长期记忆、剧情摘要、事件、Prompt Inspector 和可选本地语义
检索组织成一条连续链路。聊天记录、角色资料、API Key 和模型默认保存在用户设备上，
不依赖项目方云数据库。

[![Latest Release](https://img.shields.io/github/v/release/bawdwl/shiyu-ai-companion-chat)](https://github.com/bawdwl/shiyu-ai-companion-chat/releases)
[![License](https://img.shields.io/badge/license-AGPL--3.0-blue)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3ddc84)](docs/Android安装与演示.md)

当前 Android 版本：`0.21.0 / versionCode 65`

> `0.21.0` 是 Android 完整版的最后一个功能版本。后续新增功能、修复和 Release
> 只维护 Android 云端版；完整版源码和现有能力继续保留。

## 功能概览

- 新建角色、结构化角色卡、实时流式聊天和停止生成。
- 角色固定事实、世界书、知识边界、用户边界和可选示例对话。
- 近期对话、滚动剧情摘要、结构化事实、计划、共同事件和证据链。
- 用户可查看、修正、钉住、标为过时、忽略或删除长期记忆。
- Android 可选下载 `Qwen3-Embedding-0.6B-ONNX`，与 FTS4 共同进行语义召回。
- 保留式回复版本、聊天分支、Checkpoint、搜索、收藏和完整 ZIP 备份。
- 只读 Prompt Inspector，显示分区预算、记忆选择、Lore 命中和生成耗时。
- 云端图片输入、Android 千问生图与可选 Local Dream SD1.5/QNN 本地生图。
- Android 本地语音识别与 VITS 角色语音，PC 可配置完整 Whisper/CosyVoice 链路。

## 四种运行形态

- **Windows EXE 基础版**：解压即可运行，提供 DeepSeek / OpenCode Go 云端角色生成和
  聊天、四个内置角色、角色编辑与删除、世界书、长期记忆管理及云端图片输入。发布包
  不附带本地大语言模型、生图模型或完整本地语音模型。
- **PC 网页完整环境**：与 EXE 共用 React 和 FastAPI 核心，额外支持自行安装
  MeinaMix V12、本地 Qwen/LoRA、BGE、CosyVoice 2、GPT-SoVITS、MeloTTS 和
  Whisper 等完整实验链路。
- **Android 完整版**：除云端 API 外，可导入 MNN 大语言模型和 Local Dream
  SD1.5/QNN 生图模型；APK 内置 8 个可编辑、可删除的初始角色及对应头像和背景，
  并包含本地语音识别与 VITS 角色语音运行库。模型权重按设置页要求由用户导入。
- **Android 云端版**：独立包名，可与完整版共存，只保留云端聊天、角色生成、长期
  记忆、场景、图片输入、千问云端生图和可选 Qwen3 语义记忆；不包含 MNN、QNN、
  Local Dream、ASR 和 TTS 运行库。初始角色为 6 人，不再包含宋知意、裴既明。

### 对应发布入口

- Windows EXE 基础版：
  [Release v0.18.0](https://github.com/bawdwl/shiyu-ai-companion-chat/releases/tag/v0.18.0)
- PC 网页完整环境：
  [安装与复现文档](docs/PC网页版安装与复现.md)
- Android 完整版：
  [Release v0.21.0](https://github.com/bawdwl/shiyu-ai-companion-chat/releases/tag/v0.21.0)
- Android 云端版：
  [Release v0.21.0](https://github.com/bawdwl/shiyu-ai-companion-chat/releases/tag/v0.21.0)

## 安装 Android

从 [GitHub Releases](https://github.com/bawdwl/shiyu-ai-companion-chat/releases/latest)
下载：

- 完整版：`Shiyu-0.21.0-Android-arm64-v8a.apk`
- 云端版：`Shiyu-0.21.0-cloud-Android-arm64-v8a.apk`

完整版和云端版使用不同包名，可以同时安装：

- `com.shiyu.chat`
- `com.shiyu.chat.cloud`

首次启动后填写自己的 OpenCode Go 或 DeepSeek API Key。模型权重、聊天数据库和 API
Key 不进入 Git 仓库。

完整安装、权限和真机演示步骤见
[Android 安装与演示](docs/Android安装与演示.md)。

## 可选 Qwen3 语义记忆

Android 默认使用 Room、FTS4、中文词面、近义表达和确定性重排。需要更强语义召回时，
可在“设置 → Android 语义记忆”中：

1. 下载约 596 MiB 的 `Qwen3-Embedding-0.6B-ONNX` INT8 包。
2. 或导入包含以下文件的 ZIP：
   - `onnx/model_int8.onnx`
   - `tokenizer.json`
   - `tokenizer_config.json`
   - `config.json`

模型不会进入 APK。安装后会在后台为结构化记忆建立 1024 维向量索引，查询时与 FTS4
共同参与召回。模型未安装、加载失败或索引为空时会自动回退，不阻塞聊天。

## 数据与隐私

- 不包含项目方账号系统和云数据库。
- API Key 保存在 Android Keystore/EncryptedSharedPreferences 或本机加密 Secret Store。
- 角色、消息、记忆、分支、Trace 和图片保存在当前设备。
- Windows、PC 网页和 Android 不会自动同步数据。
- 重置对话只清除该角色对话及派生数据，保留角色档案与图片。
- 删除角色会删除该角色的聊天、记忆、事件、Trace 和角色图片。

## 从源码构建

### Android

需要 JDK 17、Android SDK 35、Build Tools 35 和 Android NDK。

```powershell
.\scripts\build_android.ps1
.\scripts\build_android_cloud.ps1
```

输出：

```text
android/app/build/outputs/apk/full/debug/app-full-debug.apk
android/app/build/outputs/apk/cloud/debug/app-cloud-debug.apk
```

### PC 网页完整环境

```powershell
conda create -n shiyu-chat python=3.11 pip -y
conda activate shiyu-chat
python -m pip install -r requirements-lock-windows-py311.txt
pnpm --dir frontend install --frozen-lockfile
pnpm --dir frontend build
python launch_app.py --provider opencode --speech-provider off --asr-provider off
```

打开 <http://127.0.0.1:8000/ui/>。完整部署见
[PC 网页版安装与复现](docs/PC网页版安装与复现.md)。

## 验证

```powershell
$env:PYTHONDONTWRITEBYTECODE='1'
D:\Others\Study\Anaconda3\envs\qwen3-im-chat\python.exe -B scripts\check_project.py

$toolchain=(Resolve-Path '..\..\outputs\android-toolchain').Path
$env:JAVA_HOME=(Resolve-Path (Join-Path $toolchain 'jdk17\jdk-17.0.20.1+1')).Path
$env:ANDROID_HOME=(Resolve-Path (Join-Path $toolchain 'sdk')).Path
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
$env:GRADLE_USER_HOME=(Resolve-Path (Join-Path $toolchain 'gradle-home-current')).Path
.\android\gradlew.bat -p .\android :app:testEmulatorDebugUnitTest :app:lintFullDebug
```

当前 Android JVM 测试为 `137` 项。测试通过不等同于大规模真实聊天质量评估。

## 目录

```text
launch_app.py                 PC 启动入口
src/                          FastAPI、Provider、记忆、提示词和存储
frontend/                     React + TypeScript 源码
web/react-dist/               已构建 PC 前端
desktop/                      Windows WebView2 桌面启动器
android/                      Kotlin + Compose + Room Android 项目
tests/                        生产链路回归测试
scripts/                      构建、模型下载、发布和检查脚本
docs/                         架构、安装、功能与复现文档
evaluation/                   固定评测夹具
release/                      发布说明与本地发布物索引
```

更多说明见 [文档索引](docs/README.md)。

## 当前限制

- 不做账号系统、跨设备同步和多人协作。
- Qwen3 embedding 在手机上的首次加载、内存峰值和耗电仍取决于设备配置。
- 摘要尚无逐条事实级 grounding，时间语义仍是后续重点。
- Android 完整版不再继续迭代，后续只维护云端版。

## 许可证

项目原创代码采用 [AGPL-3.0](LICENSE)。模型、转换产物、角色图片、语音模型和其他
第三方组件分别遵循上游许可，详见 [LICENSE-NOTICE.md](LICENSE-NOTICE.md)。

## 项目演示

### PC 工作台

<p align="center">
  <img src="docs/images/PC-0.18-home.png" alt="拾语 PC 工作台" width="100%">
</p>

### Android 对话

<p align="center">
  <img src="docs/images/Android-0.18.22-chat.jpg" alt="拾语 Android 聊天界面" width="42%">
  <img src="docs/images/Android-0.18.22-chat-xiao-chengyuan.jpg" alt="拾语 Android 角色对话" width="42%">
</p>
