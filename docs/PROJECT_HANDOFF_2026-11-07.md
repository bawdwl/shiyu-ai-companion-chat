# 拾语项目交接文档

更新日期：2026-11-09
仓库：`bawdwl/shiyu-ai-companion-chat`
当前分支：`main`
当前 Android：`0.18.24 / versionCode 54`

`0.18.24` 修复 Android 创建角色错误固定使用 OpenCode Go，以及官方 DeepSeek
使用无效模型名和 OpenCode 专用字段的问题。`0.18.23` 包含 2026-11-07 角色体验优化：
版本化角色上下文、轻量对话策略、记忆需求判断、事件链、当前轮临时情绪、
场景状态和 PC 对齐。详情见
`docs/角色体验与连续性优化_2026-11-07.md`。该版本还包含首页 pager、
背景/头像两步裁剪和透明启动图标修复；Windows 仍为 `0.18.0`。

## 1. 新对话先读什么

接手项目时按以下顺序阅读：

1. 本文件：获得完整上下文和下一步方向。
2. `README.md`：产品定位、当前版本和主要能力。
3. `docs/README.md`：按主题查找详细文档。
4. `CHANGELOG.md`：当前版本和近期改动。
5. `docs/ROLEPLAY_TECHNICAL_REVIEW_2026-11-07.md`：角色卡、世界书、
   长期记忆、内部状态和技术优化路线。
6. `docs/当前维护指南.md`：保留的维护入口，内容较早，冲突时以本文件和源码为准。

当前 GitHub 发布：

- 最近已发布：Android [`v0.18.23`](https://github.com/bawdwl/shiyu-ai-companion-chat/releases/tag/v0.18.23)
- 本地待发布候选：`0.18.24`
- 待发布 APK：`release/apk-0.18.24/Shiyu-0.18.24-Android-arm64-v8a.apk`
- Windows：`release/windows/Shiyu-0.18.0-Windows-x64.zip`

## 2. 项目是什么

拾语是一套中文虚拟角色陪伴系统，包含三种运行形态：

1. **Windows EXE 基础版**：解压即用，支持云端角色生成、聊天、世界书、
   长期记忆、角色管理、图片输入和基础语音回退。
2. **PC 网页完整环境**：React + TypeScript + FastAPI + SQLite，可按需安装本地
   Qwen/LoRA、MeinaMix V12、BGE、Whisper、CosyVoice 2 等模型和运行库。
3. **Android**：Kotlin + Jetpack Compose + Room，支持云端 API、本地 MNN 语言
   模型、Local Dream SD1.5/QNN 生图、本地语音识别和 VITS 角色语音。

三端共用产品设计，但数据不会自动同步。每个角色在每台设备上只有一个持续会话。

## 3. 当前目录分区

```text
shiyu-ai-companion-chat-final/
├─ README.md                 产品首页与使用入口
├─ CHANGELOG.md              版本记录
├─ src/                      PC FastAPI 后端、角色、Prompt、记忆、语音与模型入口
├─ frontend/                 React/TypeScript 源码与本地依赖
├─ web/                      内置品牌资源、角色立绘和已构建 React 前端
├─ desktop/                  Windows WebView2 启动器与 PyInstaller 构建
├─ android/                  Android Gradle 工程、资源、初始角色与 APK 源码
├─ scripts/                  构建、部署、模型下载、测试和验证入口
├─ tests/                    PC 与前端测试
├─ evaluation/               固定评测集和评测代码
├─ docs/                     安装、架构、记忆、平台和实验文档
├─ release/                  当前 APK、Windows ZIP 与发布说明
├─ outputs/                  本地运行库和少量评测证据，Git 忽略
├─ third_party/              第三方集成说明
├─ design-system/            工作台设计系统资料
└─ launch_app.py             PC 网页端启动入口
```

### 必须保留

- `src/`、`frontend/`、`web/`、`desktop/`、`android/`：应用源码与内置资源。
- `tests/`、`evaluation/`：回归和角色质量评测。
- `docs/`：设计决策、踩坑记录和交接信息。
- `release/apk-0.18.24/`：当前待发布候选 APK。
- `release/windows/`：当前 Windows 发布 ZIP。
- `outputs/image-runtime/`：PC 本地 Diffusers 生图运行库。
- `outputs/secret-runtime/`：Windows EXE 需要的隔离密钥运行库。
- `outputs/memory-research/`：少量记忆评测证据。
- `frontend/node_modules/`：保留用于立即构建前端；删除后可通过
  `pnpm --dir frontend install --frozen-lockfile` 恢复。

### 不在 Git

- 模型权重、LoRA Adapter、MNN、QNN、ONNX、GPTQ；
- APK 和 Windows ZIP；
- API Key、SQLite 聊天数据库、个人图片和录音；
- Android 构建缓存、Python 缓存和临时测试目录。

## 4. 当前已实现功能

### 角色

- 使用一段灵感生成结构化角色卡；
- 字段完整度检查与第二次定向补齐；
- 编辑、保存、删除角色；
- 12 个 Android 初始角色及独立头像、背景；
- 角色开场白、初次见面介绍、固定事实和知识边界；
- 固定角色精简 Prompt；
- 世界书关键词触发、优先级和启用开关；
- 角色级云端/本地模型来源切换；
- 固定事实、示例对话、场景、动机、边界和稳定目标；
- 时代外知识和禁止词守卫。

### 聊天

- DeepSeek 和 OpenCode Go 云端 Provider；
- PC SSE 流式回答、Android 流式回答与停止生成；
- 本地 Qwen3-1.7B MNN；
- 图片输入；
- 当前轮即时语气与按需交流策略，不增加主聊天模型调用；
- 长期记忆、会话摘要和最近原文共同进入上下文；
- 当前回答与滚动摘要分开，不阻塞主聊天；
- Prompt、模型、记忆、状态、Token 和延迟 Trace。

### 长期记忆

- 回复后异步创建持久任务；
- 从原话抽取用户事实、角色自述、共同事件、偏好、计划、假设和观点；
- 引文、说话人、主体、日期、事实键和操作类型确定性校验；
- 第二次 LLM 语义审核；
- `add / merge / supersede / contested / ignore`；
- 当前值和历史旧值、有效期、别名、墓碑和版本链；
- PC FTS5 + 可选 BGE 混合检索；
- Android FTS4 + 中文词法检索；
- 每个角色每日 30,000 Token 后台记忆预算；
- 网页端记忆纠错、标记错误、恢复历史、遗忘主题和删除审计。
- 最小事件链与未完成事项，只在相关话题出现时进入 Prompt。

### 内部状态

- 心情效价、唤醒度、熟悉度、信任、温暖、关系阶段和用户边界；
- 模型只提出事件，程序限幅、去重和衰减；
- 用户命令不能直接提高信任；
- 状态只以自然语言进入 Prompt，不展示好感度数值；
- 冲突、修复和边界事件可影响关系阶段。
- 好消息、困难、冲突、修复和边界可即时影响本轮语气，但不直接永久修改关系数值。

### 语音与图片

- Android 本地 sherpa-onnx ASR；
- Android VITS 中文角色语音，已移除 Kokoro 和无效音色；
- PC 可配置 MeloTTS、GPT-SoVITS 和 CosyVoice 2；
- PC 本地 MeinaMix V12；
- Android Local Dream SD1.5/QNN；
- 手机模型导入、下载、文件校验和安装；
- 角色头像裁剪、聊天背景裁剪、9:16 背景和 1:1 头像。

### Android 界面

- 首页、消息、创建角色、角色管理、设置；
- 首页上下切换角色，状态平滑，不闪白；
- 聊天只显示最新回复，历史按钮展开全部；
- 输入法弹出时消息层上移，背景保持固定；
- `（）` 快捷插入动作括号；
- 回退到此前用户发言；
- 重置二次确认；
- 重置保留角色档案、头像和背景；
- 删除角色才删除角色图片和完整数据。

### Windows/PC

- 三栏角色工作台；
- PC 与 EXE 共用 React 和 FastAPI 业务；
- 角色生成、角色卡审核、世界书与长期记忆；
- Windows 使用 ICO 和 WebView2；
- EXE 不包含模型权重、API Key 或用户数据。

## 5. 一次聊天如何生成

核心顺序：

```text
角色卡
→ 最近对话
→ 当前问题相关的世界书
→ 当前角色范围的长期记忆
→ 内部状态
→ 低权限数据说明和输出约束
→ 云端或本地模型
→ 禁止知识检查
→ 保存消息和 Trace
→ 后台记忆/状态更新
```

详细机制见：

- `docs/ROLEPLAY_TECHNICAL_REVIEW_2026-11-07.md`
- `src/prompt_pipeline.py`
- `src/structured_memory.py`
- `src/internal_state.py`
- `android/app/src/main/java/com/shiyu/chat/data/ChatRepository.kt`
- `android/app/src/main/java/com/shiyu/chat/data/MemorySelection.kt`

宋知意和裴既明使用 LoRA 训练时的精简固定 Prompt。固定 Prompt 能稳定人格和小
模型语言风格，但当前会绕过完整世界书、长期记忆和状态注入。不要在没有 A/B
评测的情况下直接把完整角色卡重新拼进去。

## 6. PC 网页端启动与测试

### 环境

- Windows 10/11
- Python 3.11
- Git
- pnpm 或 Node.js 20+
- 云端模式不需要 NVIDIA GPU

### 安装

```powershell
git clone https://github.com/bawdwl/shiyu-ai-companion-chat.git
cd shiyu-ai-companion-chat

conda create -n shiyu-chat python=3.11 pip -y
conda activate shiyu-chat
python -m pip install -r requirements-lock-windows-py311.txt

pnpm --dir frontend install --frozen-lockfile
pnpm --dir frontend build
```

当前工作区已保留 `frontend/node_modules` 和 `web/react-dist`，可直接启动。

### 云端聊天

```powershell
python launch_app.py --provider opencode --speech-provider off --asr-provider off
```

或：

```powershell
python launch_app.py --provider deepseek --speech-provider off --asr-provider off
```

打开 `http://127.0.0.1:8000/ui/`，在设置中填写自己的 Key。

### 测试

```powershell
python -B scripts/check_project.py
node --test tests/frontend_state.test.cjs
pnpm --dir frontend build
```

### 本地生图与语音

- Diffusers 运行库：`outputs/image-runtime`
- 模型默认目录：`outputs/models/meinamix-v12`
- 安装脚本：`scripts/setup_local_image_runtime.ps1`
- CosyVoice 安装：`scripts/setup_cosyvoice2_local.ps1`
- 模型权重不进入 Git

## 7. Android 构建与设备部署

Android 工具链位于项目父目录：

```text
C:\Users\31153\Documents\Qwen3 IM Chat\outputs\android-toolchain
```

包含 JDK 17、Android SDK 35、Gradle 缓存和稳定 Debug Keystore。

2026-11-07 已复核以下必要工具仍然存在且可执行：JDK 17.0.20.1、Android SDK
Platform 35、Build Tools 35.0.0、`aapt.exe`、`adb.exe`、Gradle 缓存和
`shiyu-debug.keystore`。清理只删除了项目内可重新生成的 Android 构建缓存。

推荐直接运行：

```powershell
.\scripts\build_android.ps1
```

脚本同时查找项目内和父目录的 `outputs/android-toolchain`。

完整验证命令：

```powershell
$env:JAVA_HOME='C:\Users\31153\Documents\Qwen3 IM Chat\outputs\android-toolchain\jdk17\jdk-17.0.20.1+1'
$env:ANDROID_HOME='C:\Users\31153\Documents\Qwen3 IM Chat\outputs\android-toolchain\sdk'
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
$env:GRADLE_USER_HOME='C:\Users\31153\Documents\Qwen3 IM Chat\outputs\android-toolchain\gradle-home-current'
$env:Path="$env:JAVA_HOME\bin;$env:Path"

cd android
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug --no-daemon --offline
```

构建输出：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

设备安装：

```powershell
C:\Users\31153\Documents\Qwen3 IM Chat\outputs\platform-tools\adb.exe devices
C:\Users\31153\Documents\Qwen3 IM Chat\outputs\platform-tools\adb.exe install -r -d <apk>
```

不要为了测试清除用户数据，除非用户明确要求。覆盖安装会保留角色、聊天和 API Key。

## 8. Windows EXE

源码运行和完整依赖见 `desktop/README.md`。

```powershell
python desktop/shiyu_desktop.py
```

重新构建：

```powershell
python desktop/build_desktop.py
```

必须发布整个 onedir 目录或其 ZIP，不能只发送 EXE。

## 9. 当前测试基线

最近一次 Android 发布前验证：

- 89 项 Android JVM 测试通过；
- `assembleDebug` 通过；
- `lintDebug` 通过；
- `0.18.23` APK 已构建；当前没有 ADB 设备，尚未执行本次真机覆盖安装；
- APK 包含 12 个角色和 24 张头像/背景，不含聊天、Key 或设备路径。

PC 既有回归结果：

- Python 全量回归 307 项；
- 前端状态测试 40 项；
- React 生产构建通过。

未发布体验优化的真实 40 场景对照位于
`evaluation/reports/2026-11-07-experience-suite-final/`。最终规则下
Experience V2 为 39/40，旧 Prompt 为 39/40；这是一轮产品回归信号，
不是论文级显著性结论。

清理构建缓存后第一次重建会更慢，不代表依赖或源码损坏。

## 10. 数据与隐私

- PC Key、数据库和语音档案默认在 Git 忽略的 `outputs/`。
- Windows 用户数据默认在 `%LOCALAPPDATA%\Shiyu`。
- Android Key 使用 Keystore 加密；角色、聊天、记忆和图片在应用私有目录。
- 不得提交或截图暴露 API Key、聊天数据库、个人图片和录音。
- 2026-11-07 清理时已删除 `release/seed-export/`、
  `release/inspected-device/`、测试 SQLite、日志、临时密钥和 `secrets.json`。
- 这些删除只清理开发工作区，不改手机和 Windows 用户实际安装的数据。

## 11. 发布流程

Android：

1. 修改 `android/app/build.gradle.kts` 的 `versionCode` 和 `versionName`。
2. 运行测试、`assembleDebug`、`lintDebug`。
3. 真机覆盖安装并检查启动、聊天、重置、角色图片。
4. 复制到 `release/apk-<version>/`。
5. 计算 SHA-256。
6. 更新 `README.md`、`docs/Android安装与演示.md`、`CHANGELOG.md` 和
   `release/README.md`。
7. 用户明确要求后，先运行 `git push origin main`。
8. 用无网络预检验证版本与 APK：

```powershell
.\scripts\publish_android_release.ps1 `
  -Version <version> `
  -NotesFile release\notes-v<version>.md `
  -WhatIf
```

9. 正式创建 Release 并上传：

```powershell
.\scripts\publish_android_release.ps1 `
  -Version <version> `
  -NotesFile release\notes-v<version>.md
```

详细说明见 `docs/GITHUB_APK_RELEASE.md`。本机没有 `gh`；脚本使用 Git
Credential Manager 中的凭据调用 GitHub API，不打印 Token。

Windows：

1. 构建 onedir；
2. 实机确认 EXE 可打开；
3. 压缩完整目录；
4. 计算 ZIP 和 EXE 哈希；
5. 更新发布说明；
6. 用户明确确认后上传 Release。

发布资产不进入 Git。

## 12. 已知限制

- 当前长期记忆主要是平坦事实和原话，缺少事件卡和关系图。
- Android 没有打包 BGE，语义召回弱于 PC。
- PC 的指代查询扩展尚未完整同步到 Android。
- 固定 LoRA 角色为保持人格，当前绕过完整记忆和状态。
- Android 简化角色卡未完整保存 PC 的固定事实、示例和部分背景字段。
- 世界书只做关键词匹配，没有语义、组合条件或递归触发。
- 没有独立场景状态、回合意图规划器和生成后角色一致性 Critic。
- 内部状态维度偏少，阈值和衰减参数尚未做大样本自然度校准。
- 1.7B 本地模型复杂人格、知识和长对话质量仍低于云端大模型。
- 评测规模仍小，90/90 记忆回放只证明固定夹具上的流程正确。
- 三端数据不自动同步。

## 13. 历史踩坑与经验

### LoRA

- 单角色 LoRA 能学习口癖和节奏，但不能替代记忆系统。
- 数据量大不等于质量高；动作少、说话人混淆和安全边界不足会造成模式坍塌。
- 训练和推理 Prompt 结构差异会让小模型人格不稳定。
- 轮数增加不一定更好，必须同时看验证集、硬失败和长对话复读。
- 许澄、孙悟空和凉宫春日等历史实验暴露过复读、身份混淆、过度简短和安全问题，
  失败证据不能改写为成功结论。
- 宋知意选择 half1 是开发门禁结果；测试有明显提升，但仍是 LLM 评审，不是
  paper 级显著性结论。

### Prompt

- 角色字段“温柔、体贴、善于沟通”过多，模型容易变成客服或心理咨询模板。
- 需要写具体行为、自然结束和何时不应反问。
- 完整角色卡并非越长越好；1.7B 固定角色应使用训练时相同短提示。
- 世界书只在相关话题按需注入，全部塞入会挤占上下文并引发设定冲突。

### 记忆

- 模型提出的记忆不能直接写库，必须有逐字证据。
- “可能在考虑”不能变成“已经决定”。
- 新旧事实必须保留版本；删除要有墓碑，否则旧原话扫描会复活。
- 结构化事实覆盖的原话不能重复注入。
- 用户当前消息中的明确更正优先于所有旧记忆。
- 固定评测不能冒充真实模型准确率。

### 图片

- MeinaMix V12 的竖图实测会产生噪声；当前保持方形生成后裁剪 9:16。
- 过度详细的五官微描述会降低整体画面质量，应保留核心视觉锚点。
- 生图英文提示词应由模型根据整体角色独立生成，不要逐句翻译中文。
- 当前固定正面模板：
  `masterpiece, best quality, anime illustration, three-quarter upper body, {appearance}`
- 不要增加 `beautiful detailed face` 等没有稳定收益的审美词。

### Android UI

- 角色图片、聊天层和底栏必须使用同一尺寸与定位，避免进入聊天时缩放。
- 首页角色切换不能先显示空白、紫色圆点或旧图层。
- 全屏编辑页必须为系统导航栏和底部按钮预留安全区。
- 重置对话必须保留头像和背景，删除角色才删除图片。
- 不要一次清除应用数据来验证迁移，会破坏用户实际数据。

### Windows

- WebView2 图标必须是 ICO；PNG 图标曾导致 EXE 静默退出。
- Windows 必须发布整个 onedir 目录。
- 应用创建角色优先 DeepSeek；OpenCode 的 JSON mode 不稳定，需要通过提示约束
  并兼容其上游返回。

## 14. 下一阶段建议

最优先：

1. 增加轻量回合意图规划：先决定解释、安慰、拒绝、开玩笑、回避或结束。
2. 增加场景状态：时间、地点、在场人物、活动、承诺和未解决话题。
3. 增加生成后角色一致性 Critic。
4. 把平坦事实升级为事实、事件和关系弧线三层记忆。

其次：

1. Android 接入量化中文 embedding，并同步 PC 的查询扩展。
2. 世界书增加语义、作用域、可见性和冲突检测。
3. 建立 100 轮长期对话、更正、冲突、边界和多角色辨识度测试。
4. 固定 LoRA 角色实验“短人格核心 + 极小长期记忆摘要”。

不要优先做：

- 盲目增加 LoRA 轮数；
- 无限增加角色卡和世界书字数；
- 用更多 Prompt 规则掩盖缺少场景状态的问题；
- 用 1.7B 本地模型追求与云端大模型完全相同的知识与推理。

## 15. 新对话接手检查清单

1. 运行 `git status --short`，确认是否有用户未提交改动。
2. 阅读本文件和 `docs/ROLEPLAY_TECHNICAL_REVIEW_2026-11-07.md`。
3. 确认目标平台：Android、PC 网页或 Windows EXE。
4. Android 真机操作前先确认设备在线，再覆盖安装。
5. 不读取或输出聊天数据库、API Key 和个人图片，除非用户明确要求诊断且只读。
6. 修改后运行对应测试。
7. 发布前实际启动验证。
8. 未经用户明确要求，不推送、不删除 Release、不覆盖用户数据。

## 16. 本地清理记录

2026-11-07 已执行：

- 删除 18 个旧 Android APK 测试包；
- 删除旧 Windows 构建目录；
- 删除 Android、Python 和前端 TypeScript 构建缓存；
- 删除手机数据库导出、设备检查数据库和图片路径清单；
- 删除测试聊天数据库、结构化记忆实验库、日志、临时 URL 和本地测试密钥；
- 删除重复的中文技术评审副本，保留 ASCII 文件名的可打开版本；
- 合计释放约 6.8 GB；
- 保留当前 Android `0.18.23`、Windows `0.18.0`、模型运行库、评测证据、
  前端依赖和全部源码。
