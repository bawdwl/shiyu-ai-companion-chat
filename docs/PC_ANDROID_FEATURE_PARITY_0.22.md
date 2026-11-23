# 拾语 0.22.6 PC 与 Android 功能对齐

## 共同基线

- 角色生成、聊天 Prompt、场景、历史窗口和世界书命中以 Android `0.19.2` 为基线；
- 角色档案、固定事实、世界书和长期记忆分层保存；
- 长期记忆包含来源消息和逐字证据；
- 用户可修正、钉住、忽略或删除记忆；
- 回复可重答并保留版本；
- 支持分支、Checkpoint、消息搜索和 Prompt Inspector；
- Android 与 PC 不自动同步用户数据。

## 平台实现

| 能力 | Android | PC/Windows |
| --- | --- | --- |
| UI | Jetpack Compose | React + Vite |
| 后端 | Kotlin | Python + FastAPI |
| 数据库 | Room/SQLite | SQLite |
| 词法检索 | FTS4 | FTS5 |
| 语义检索 | 可选 Qwen3-Embedding ONNX | 可选 BGE-small-zh |
| 后台任务 | WorkManager / 协程 | FastAPI 后台任务 |
| 云端 Provider | OpenCode Go / DeepSeek | OpenCode Go / DeepSeek |
| 本地模型 | 完整版可选 MNN | 可选 Qwen/LoRA |
| 生图 | 云端千问 / 完整版 Local Dream | 可选 MeinaMix |
| 语音 | 完整版 ASR + VITS | 可选 Whisper/CosyVoice |

## 行为边界

- Android 完整版保留 MNN、QNN、Local Dream、ASR 和 TTS 运行库；
- Android 云端版不包含本地模型与语音运行库；
- Windows EXE 不携带大模型、生图模型或完整语音模型；
- PC 网页完整环境允许开发者自行接入本地模型和实验运行库；
- 模型权重、用户数据库、API Key 和个人媒体不进入 Git。

## 当前验证

- Python 单元与接口测试：`319` 项通过；
- 前端状态测试：`40` 项通过；
- React 生产构建：通过；
- Android 完整版/云端版 JVM：各 `137` 项通过；
- Android 两套 lint：通过；
- Android 两个 APK：通过；
- Windows PyInstaller：通过；
- 真实设备连续聊天和 Qwen 模型质量仍由作者验收。
