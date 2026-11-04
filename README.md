# 拾语 SHIYU · AI 角色陪伴聊天

拾语是一个面向中文虚拟角色陪伴场景的双端聊天项目，包含 **PC 网页端**和
**Android 客户端**。项目把大模型对话、角色卡、世界书、长期记忆、内部状态、
语音输入、角色语音和图片能力串成一条可运行的聊天链路。

仓库原名 `shiyu-qwen3-chat`，但项目已经不只是 Qwen 推理 Demo，因此现名为
`shiyu-ai-companion-chat`。

## 项目做了什么

| 能力 | PC 网页端 | Android 客户端 |
|---|---|---|
| 云端对话 | OpenCode Go、DeepSeek | 公开 Release 使用 DeepSeek；OpenCode Go 仅 Debug 调试可见 |
| 本地模型 | Qwen3-1.7B Base / LoRA 实验入口 | 应用内下载 Q8 MNN，或导入 MNN Chat 兼容 ZIP |
| 角色系统 | 创建、编辑、删除角色；角色卡、世界书、知识边界 | 相同角色结构；每个角色独立选择云端或本地模型 |
| 上下文 | SSE 流式回答、停止生成、错误提示 | 原生流式回答、停止生成、本地历史 |
| 长期记忆 | 自动提取、审核、更正、主题遗忘、版本恢复 | 自动结构化记忆；本地 MNN 使用滚动摘要 |
| 内部状态 | 心情、熟悉、信任、温暖、关系阶段 | 同结构状态，供提示词使用，不默认显示数值 |
| 图片理解 | 支持发送图片给视觉模型 | 云端模式支持；本地 MNN 模式仅支持文字 |
| 语音输入 | Whisper，可选 | sherpa-onnx 本地 ASR |
| 角色语音 | GPT-SoVITS / MeloTTS，可选 | Android 完整版内置本地 TTS |
| 图片生成 | 本地生图入口已关闭 | 内置 Local Dream SD1.5 QNN；也支持千问图片 API |
| 数据存储 | SQLite、本机加密 Key | Room、Android Keystore |

PC 与 Android 当前是两套独立客户端，**不会自动同步聊天记录，也不要求电脑和手机处于同一网络**。

## 系统结构

![拾语 AI 虚拟角色陪伴系统架构](docs/images/shiyu-ai-companion-architecture-2026-11-04.png)

整体链路由多端交互、虚拟角色对话引擎、多模态交互和对话沉淀四部分组成：

- 角色定位与设定由角色卡、世界书、知识边界和固定事实约束；
- 长期记忆负责候选提取、去重、更正、检索和上下文预算；
- 内部心情、信任、熟悉、温暖和关系阶段以自然语言进入提示词；
- 对话生成支持云端模型、本地 Qwen3 LoRA 和 Android MNN；
- 回答保存后继续沉淀记忆和状态，形成下一轮对话上下文。

## PC 端已实现

### 对话与角色

- React + TypeScript + Vite 前端，FastAPI 后端，SQLite 持久化。
- 创建、编辑和删除自定义角色；角色卡包含身份、背景、性格、说话方式、
  与用户关系、互动边界和示例对话。
- 关键词触发的世界书，以及时代、知识截止、世界范围、禁止知识和禁止词约束。
- 每个角色只有一个持续对话，支持删除角色及关联数据。
- 流式回答、主动停止、Provider 配置错误、超时和空回答处理。
- 支持图片输入，交给具备视觉能力的云端模型。

### 记忆与状态

- 回答保存后，后台异步提取用户事实、角色自述、共同事件、计划和边界，不阻塞聊天。
- 候选事实要求绑定来源消息、说话人和逐字证据。
- 支持新增、合并、更正、疑义、忽略、事实有效期和删除墓碑。
- 手动修正、标记错误、恢复历史值、忘记主题和撤销主题禁止。
- SQLite FTS5 检索；安装 BGE 后加入本地向量检索，失败时自动回退。
- 内部状态只进入角色提示，聊天界面不默认展示“好感度数字”。

### 语音与本机模型

- 语音识别可接 Whisper。
- 角色语音可接 GPT-SoVITS，MeloTTS 作为回退。
- 可选接入本机 Qwen3-1.7B Base 或实验 LoRA；未下载模型时使用云端 API 即可。
- API Key 保存在本机加密 Secret Store，不返回给浏览器。

## Android 端已实现

- Kotlin + Jetpack Compose，Room schema 11，WorkManager，OkHttp 和 Android Keystore。
- 自定义角色、角色卡、世界书、知识边界、固定事实和唯一持续对话。
- 公开 Release 默认 DeepSeek；OpenCode Go 只在 Debug 构建显示，避免公开版保留作者测试入口。
- 本地模式可下载或导入 MNN Chat 兼容模型 ZIP，在手机上完成文字推理。
- 本地 MNN 模式保留最近 24 条原话；更早消息按批次压缩为简短事实，
  不调用云端记忆提取。
- sherpa-onnx 本地语音识别和 VITS 中文语音合成；模型含 5 个 speaker，
  去重后提供 4 个可试听声部（2 个女声、2 个男声），并按角色保存选择。
- 内置 Local Dream SD1.5/QNN 后端，可在应用内下载或导入兼容模型并生成角色头像。
- 设置页分为“对话与模型”和“图片生成”，支持下载进度、SHA-256 校验、删除模型和清理缓存。
- 内置李白、宋知意、江昭谌、裴既明四个角色；许澄和旧实验角色会在升级时删除。
- 可切换千问图片 API；角色外貌提示词会包含明确的主体性别和视觉描述。
- 删除角色时清理所属消息、记忆、Trace、图片、模型状态和本地摘要。

## 快速运行 PC 网页端

### 1. 准备环境

- Windows 10/11
- Python 3.11
- Git
- 可选：Node.js 20+ / pnpm，仅在修改前端源码时需要

```powershell
git clone https://github.com/bawdwl/shiyu-ai-companion-chat.git
cd shiyu-ai-companion-chat

conda create -n shiyu-chat python=3.11 pip -y
conda activate shiyu-chat
python -m pip install -r requirements-api-windows-py311.txt
.\scripts\setup_secret_runtime.ps1 -Python python
```

`requirements-api-windows-py311.txt` 是仅使用云端 API 的最小依赖。仓库已经包含
构建后的 `web/react-dist/`，因此默认不需要安装 Node.js。

### 2. 启动

OpenCode Go：

```powershell
python launch_app.py --provider opencode --speech-provider off --asr-provider off
```

DeepSeek：

```powershell
python launch_app.py --provider deepseek --speech-provider off --asr-provider off
```

打开 <http://127.0.0.1:8000/ui/>，在设置页填入对应的 API Key，然后创建角色开始聊天。

### 3. 修改前端源码后重新构建

```powershell
pnpm --dir frontend install --frozen-lockfile
pnpm --dir frontend build
```

### 可选：启用 BGE 语义记忆

```powershell
python -m pip install huggingface_hub
python scripts/fetch_embedding_model.py
```

模型缺失时 PC 会自动使用 FTS5 和词法检索，不影响基本聊天。

### 可选：启用 PC 语音

PC 语音依赖独立的 Whisper、GPT-SoVITS 或 MeloTTS 模型和服务，不包含在 Git
仓库中。安装和启动方法见 [语音部署](docs/语音部署.md)。

## 构建 Android

当前 Android 源码为 `0.16.1 / versionCode 30`，只提供一个完整安装包
`com.shiyu.chat`。语音输入、角色朗读、MNN 和图片生成入口都保留在同一版本中，
不再维护精简版或应用内 TTS 语音包下载流程。预编译 APK 从
[GitHub Releases](https://github.com/bawdwl/shiyu-ai-companion-chat/releases/tag/v0.16.1)
下载；APK 不提交到 Git 历史。

构建后的 Debug 应用可在设置中填写 OpenCode Go 或 DeepSeek Key；公开 Release
只显示 DeepSeek。手机与电脑不要求局域网连接，因为 Android 云端模式直接调用模型 API。

### 从源码构建 Android APK

需要 JDK 17、Android SDK 35 和 Git LFS。

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-17"
$env:ANDROID_HOME = "C:\path\to\Android\Sdk"
.\gradlew.bat assembleDebug
```

输出：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## 手机本地模型与图片模型

构建出的应用不包含 Qwen MNN 权重或 SD1.5 QNN 模型。可在设置页按需下载，
也可导入本地 ZIP。MNN 与 Dream 模型共用独立存储槽位，重新安装或删除时会清理旧目录：

- MNN 模型 ZIP 至少应包含 `config.json`、`llm.mnn`、`llm.mnn.weight`
  和 `tokenizer.mtok`。
- Local Dream 模型需使用兼容的 SD1.5 QNN ZIP；模型和各第三方组件分别遵循
  自己的许可。
- 本地 MNN 当前只处理文字；图片输入需要切换到云端模式。
- Local Dream 组件采用 CC BY-NC 4.0，当前项目只能作为非商业 Demo 使用。

MeinaMix V12 在 `xororz/sd-qnn` 中只发布三种 QNN 包：

| 包 | 文件大小 | SHA-256 |
|---|---:|---|
| `_min` | 1,156,786,441 bytes | `7ed33339b305f0df97fd746790962808c38e56bb98e560d55d8eb239fa6c4dc8` |
| `_8gen1` | 1,032,314,916 bytes | `10d9f52607146c85428d3f66919a8a86ccf8f680c21b51ef2a39c6c0ff1b2695` |
| `_8gen2` | 1,031,765,133 bytes | `90463ac0db6eb7424bb6c7a048c811bde2996b2321eaf58b64d02d626c333098` |

Local Dream 的 SD1.5 后端要求 Qualcomm Hexagon V68 或更新架构。拾语会识别
SM8350/888、SM8450 与 SM8475/8 Gen 1、SM8550 系列/8 Gen 2、SM8650/8 Gen 3、
SM8750/8 Elite、SM8850 等已知平台。8 Gen 3、8 Elite 和新平台没有单独 MeinaMix
包，Local Dream 当前复用 `_8gen2`；未知高通平台和非高通设备不会强行安装 QNN 包。
这是当前内置的 Qualcomm NPU 路径限制，不代表其他平台永远不能本地生图。

LoRA 训练、Adapter、MNN Q8 导出与对照实验位于独立仓库：
<https://github.com/bawdwl/qwen3-1.7b-lora>

### 公开 MNN Q8 模型

| 角色 | 检查点 | ModelScope |
|---|---|---|
| 宋知意 | 1.5 轮，Q8 block64 | <https://modelscope.cn/models/Alixs666/shiyu-qwen3-1.7b-song-zhiyi-mnn-q8> |
| 裴既明 | 第 1 轮，Q8 block64，实验版 | <https://modelscope.cn/models/Alixs666/shiyu-qwen3-1.7b-pei-jiming-mnn-q8> |

这些是 MNN 3.5.0 Q8 合并模型，不是 GPTQ。模型均包含对应角色提示词和文件哈希；
可以直接下载模型目录，或按 MNN Chat 兼容包要求重新打包后导入 Android。实验版模型
用于端侧工程和角色效果验证，不代表生产级安全通过。

## 数据、密钥与隐私

- 不提交 API Key、聊天数据库、个人录音、生成图片、模型权重或 Adapter。
- PC Key 保存在本机加密 Secret Store；Android Key 使用 Keystore 加密。
- PC 数据写入 `outputs/` 下的 SQLite；Android 数据保存在 Room。
- 系统只默认监听本机，不应在无鉴权情况下将 PC 服务直接暴露到公网。
- 云端模式下，聊天文字或图片会发送给所选 Provider；本地 MNN 模式不把文字发送给云端。

## 复现测试

PC：

```powershell
python -B scripts/check_project.py
node --test tests/frontend_state.test.cjs
pnpm --dir frontend build
```

Android：

```powershell
cd android
.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon
```

当前公开仓库对应的验证记录：

- PC Python：285 项通过。
- API-only 干净环境：208 项通过，1 项按设计跳过。
- PC 前端状态测试：40 项通过，React 生产构建通过。
- Android 64 项 JVM 测试通过；Release Lint、Debug 与关闭 R8 的 Release 构建通过。
- 固定角色质量检查：基础条件 18/24，长期记忆条件 22/24。
- 私有自然记忆固定夹具回放：事实、摘要、混合检索、混合状态均为 90/90；
  仅最近消息为 23/90。

完整本地研究目录还包含未进入公开仓库的训练实验测试，之前记录为 426 项。
以上结果验证的是工程与固定夹具，不代表模型在真实用户上的总体质量。

## 已知限制

- 角色自然度和长期一致性仍需要独立人工评测。
- 本地 Qwen LoRA 在部分设置下会过度简短、复读或丢失细节。
- PC 与 Android 不自动同步；Android 也不会把推理任务转交给电脑。
- 当前语音是“录音识别 → 发送 → 回复后播放”，不是实时双向通话或自动打断。
- PC 与 Android 的记忆摘要实现尚未完全一致。
- Android 未内置 BGE，语义召回弱于 PC。
- 没有账号系统、多用户隔离、内容审核、跨设备同步和生产级并发。
- 没有 Live2D、动作演出和剧情 World Clock；内部状态不默认显示数值。

## 目录

```text
launch_app.py                 PC 启动入口
src/                          FastAPI、Provider、记忆、提示词、存储与语音
frontend/                     React + TypeScript 源码
web/react-dist/               已构建 PC 前端
android/                      Kotlin + Compose + Room Android 项目
tests/                        回归测试
evaluation/                   固定质量与记忆案例
scripts/                      构建、部署、模型下载和检查脚本
docs/                         架构、验收与复现文档
release/                      Android 发布说明与下载入口
```

## 进一步阅读

- [PC / Android 使用与复现](docs/使用与复现.md)
- [PC 记忆检索与内部状态](docs/PC记忆检索与内部状态_2026-10-30.md)
- [Android 本地 Dream 生图](docs/Android本地Dream生图嵌入_2026-11-02.md)
- [Android 模型下载与 NPU 兼容](docs/Android模型下载与NPU兼容_2026-11-04.md)
- [音色标签与自定义音色说明](docs/音色标签与自定义音色说明_2026-11-04.md)
- [Android 完整版构建](docs/Android完整版构建_2026-11-04.md)
- [Android 本地 MNN 双模式](docs/Android本地MNN双模式_2026-11-01.md)
- [角色质量评测、Trace 与记忆语义](docs/角色质量评测Trace与记忆语义_2026-10-30.md)
- [Windows 干净环境验收](docs/Windows干净环境验收_2026-10-31.md)

## 许可证

项目原创代码采用 [AGPL-3.0](LICENSE)。Qwen、MNN、Local Dream、sherpa-onnx、
VITS 中文模型、GPT-SoVITS、千问图片 API 和其他第三方组件分别遵循各自许可，详见
[LICENSE-NOTICE.md](LICENSE-NOTICE.md)。
