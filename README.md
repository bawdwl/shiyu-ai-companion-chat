# 拾语 SHIYU

面向中文单角色长期陪伴的原生 Android、Windows 和 PC 网页应用。

拾语围绕“角色档案、稳定对话、长期记忆和用户可控”构建：角色拥有独立的
身份、说话方式、世界书和长期记忆；聊天记录和 API Key 默认只保存在本机，
不依赖项目方云数据库。

[![Latest Release](https://img.shields.io/github/v/release/bawdwl/shiyu-ai-companion-chat)](https://github.com/bawdwl/shiyu-ai-companion-chat/releases)
[![License](https://img.shields.io/badge/license-AGPL--3.0-blue)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3ddc84)](docs/Android安装与演示.md)

当前版本：`0.22.6`

## 项目定位

拾语不是通用 AI 助手，而是以单个角色为中心的长期聊天应用。当前版本以
Android `0.19.2` 的成熟聊天行为为基线，保留 0.20/0.21 的备份、长期记忆、
Prompt Inspector、聊天分支和 Qwen3 语义检索能力。

核心目标：

- 角色说话稳定、自然，不被过多规则干扰。
- 角色档案、世界书和长期记忆分层管理。
- 长对话仍能召回用户资料、共同经历和约定。
- 用户能够查看、修正、忽略和删除记忆。
- 数据保存在用户设备，核心功能不依赖项目方服务器。

## 主要功能

### 角色与聊天

- 创建、编辑和删除角色，配置身份、性格、说话方式和当前关系。
- 角色固定事实、知识边界、示例对话和备用开场白。
- 世界书保存世界规则、地点、组织、历史和秘密，并按关键词进入上下文。
- 云端 API 流式聊天、停止生成、重答和回复版本保留。
- 中文输入法、多行输入、图片输入和可选角色语音。
- 聊天分支、Checkpoint、消息搜索、编辑和收藏。

### 长期记忆

- 结构化事实、偏好、计划、共同经历和角色自述分开存储。
- 每条记忆绑定来源消息和逐字证据，可修正、钉住、标为过时或删除。
- FTS4 中文词法检索作为基础能力。
- Android 可选导入或下载 `Qwen3-Embedding-0.6B-ONNX INT8`，用于语义召回。
- 语义模型不进入 APK；未安装或关闭开关时自动使用 FTS4。
- 设置页提供语义检索检测，检查模型、向量索引和抽样语义分数。

### 数据与调试

- ZIP 备份和恢复，包含聊天、角色、记忆和角色图片。
- 备份默认排除 API Key、模型权重和缓存。
- 数据库迁移、损坏校验和恢复回滚保护。
- Prompt Inspector 显示 Prompt 分区、记忆选择、Token 估算和耗时。
- 不保存完整 Prompt 或 API Key 到 Trace。

## 四种运行形态

| 形态 | 说明 |
| --- | --- |
| Android 完整版 | `com.shiyu.chat`。包含 MNN、Local Dream/QNN、ASR 和本地角色语音运行库；模型权重由用户导入。 |
| Android 云端版 | `com.shiyu.chat.cloud`。包含云端聊天、角色、记忆、世界书、图片输入、千问生图和可选 Qwen embedding。 |
| Windows EXE | 解压即用，复用 React + FastAPI 核心。不附带本地大模型、生图模型或完整语音模型。 |
| PC 网页完整环境 | 从源码运行，可自行接入 MeinaMix、本地 Qwen/LoRA、BGE、CosyVoice、Whisper 等实验链路。 |

Android 完整版和云端版使用不同包名，可以同时安装，数据彼此独立。

## 当前发布文件

GitHub Release 统一提供：

- `Shiyu-0.22.6-Android-arm64-v8a.apk`
- `Shiyu-0.22.6-cloud-Android-arm64-v8a.apk`
- `Shiyu-0.22.6-Windows-x64.zip`
- `pc-web-0.22.6.zip`
- `shiyu-core-0.22.6.zip`：项目核心源码，不含模型、构建缓存和用户数据

发布入口：

[GitHub Releases](https://github.com/bawdwl/shiyu-ai-companion-chat/releases/tag/v0.22.6)

## Android 安装

首次启动后进入设置，选择 DeepSeek 官方 API 或 OpenCode Go 并填写对应 Key。

可选 Qwen 语义记忆：

1. 打开“设置 → 语义”。
2. 下载约 596 MiB 的 Qwen3-Embedding ONNX INT8 模型，或导入模型 ZIP。
3. 等待后台建立记忆向量索引。
4. 点击“检测语义检索”，确认模型、索引和抽样分数正常。

模型保存于 App 专属目录，卸载应用时会一并删除。

详细步骤见 [Android 安装与演示](docs/Android安装与演示.md)。

## PC 与 Windows

- [PC 网页版安装与复现](docs/PC网页版安装与复现.md)
- [Windows 桌面版安装与使用](docs/Windows桌面版安装与使用.md)

Windows 发布包必须完整解压后运行，不要只移动单个 EXE。

## 项目文档

- [文档索引](docs/README.md)
- [项目详细介绍](docs/拾语项目详细介绍.md)
- [项目技术架构与运行原理](docs/项目技术架构与运行原理.md)
- [功能与实现](docs/功能与实现.md)
- [角色卡与世界书](docs/拾语角色卡与世界书功能说明.md)
- [本地生图与 CosyVoice 语音](docs/本地生图与CosyVoice语音.md)

## 本地开发

PC 后端：

```powershell
python launch_app.py
```

运行 Python 测试：

```powershell
python scripts/check_project.py
```

前端：

```powershell
pnpm --dir frontend build
node tests/frontend_state.test.cjs
```

Android：

```powershell
scripts/build_android.ps1 -Edition cloud
scripts/build_android.ps1 -Edition full
```

Windows：

```powershell
python desktop/build_desktop.py
```

## 数据与隐私

- 仓库不包含用户聊天数据库、API Key、个人图片、录音或模型权重。
- Android 数据保存在 `com.shiyu.chat` 或 `com.shiyu.chat.cloud` 的 App 私有目录。
- Windows/PC 数据保存在本机项目数据目录。
- Android、Windows 和 PC 不自动同步聊天数据。
- 删除角色会删除该角色的聊天、记忆、事件、Trace 和角色图片。

## 许可证

项目代码使用 AGPL-3.0。第三方模型、运行库、角色图片和实验资源遵循各自许可证，
详见 [LICENSE-NOTICE.md](LICENSE-NOTICE.md)。
