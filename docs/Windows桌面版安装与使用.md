# 拾语 Windows 桌面版安装与使用

Windows 桌面版把同一套 FastAPI 后端和 React 界面放进 WebView2 窗口。它适合
普通用户；需要阅读和修改完整源码时使用 PC 网页版。

Windows EXE 是**基础云端版**，主要提供：

- DeepSeek / OpenCode Go 云端角色生成和聊天；
- 五个内置角色及新建、编辑、删除；
- 角色卡、世界书、知识边界和自定义头像；
- 长期记忆自动整理、检索和人工管理；
- 云端视觉模型的图片输入；
- 本地 SQLite 数据与加密 API Key 保存。

完整 PC 网页环境才支持自行安装 Qwen/LoRA 本地大模型、MeinaMix V12
本地生图、BGE 语义记忆、Whisper、CosyVoice 2、GPT-SoVITS 和 MeloTTS。
这些运行库与模型权重不进入基础 EXE。

## 使用发布包

1. 下载并解压 `Shiyu-0.18.2-Windows-x64.zip`。
2. 保持目录结构完整，运行 `Shiyu-0.18.2-Windows-x64.exe`。
3. 打开“应用设置”，填写 DeepSeek 或 OpenCode Go API Key。
4. 首次启动会创建五个演示角色，可直接聊天或编辑。

发布包不包含 API Key、聊天数据库、个人图片或录音，也不包含 Qwen、LoRA、
Stable Diffusion、Whisper 或 TTS 权重。

程序需要 Microsoft Edge WebView2 Runtime。Windows 10/11 通常已经安装；如果
启动失败，请先安装微软官方 WebView2 Runtime。

界面会尝试使用 WebView2 提供的系统语音识别与 Windows 语音朗读作为回退。
该能力取决于 Windows 版本、系统语言和 WebView2 环境，不属于基础 EXE 的稳定
核心功能。需要固定音色、可控延迟或角色专属语音时，请改用完整 PC 网页环境。

## 数据目录

```text
%LOCALAPPDATA%\Shiyu\
  chat.sqlite3
  structured-memory.sqlite3
  secrets.json
  character-assets\
  chat-images\
  logs\desktop.log
  models\
```

卸载程序或替换 EXE 不会自动删除这个目录。需要彻底清空本地数据时，关闭拾语后
删除 `%LOCALAPPDATA%\Shiyu`。

## 从源码运行

```powershell
conda create -n shiyu-desktop python=3.11 pip -y
conda activate shiyu-desktop
python -m pip install -r desktop/requirements-windows.txt

.\scripts\setup_secret_runtime.ps1 -Python python
pnpm --dir frontend install --frozen-lockfile
pnpm --dir frontend build
python desktop/shiyu_desktop.py
```

桌面版只在 `127.0.0.1` 随机端口启动本机服务，API 和数据库不暴露到局域网或公网。

## 从源码构建

```powershell
python desktop/build_desktop.py
```

输出目录：

```text
release/windows/Shiyu-0.18.2-Windows-x64/
```

构建使用 PyInstaller `onedir`。分发时必须保留整个目录，只发送主 `.exe` 会在
缺少运行库时失败。

## 桌面版验收

1. 双击主程序能打开 WebView2 窗口。
2. 未配置 Key 时提示进入设置，界面仍可浏览。
3. 配置 Key 后可以创建角色、连续聊天和停止生成。
4. 关闭窗口后本机 FastAPI 服务退出。
5. 下一次启动保留角色、聊天、记忆和加密 Key。
6. 删除角色后清理该角色的消息、记忆、头像和状态。
7. 模型包缺失时云端聊天仍可用，不因语音或本地模型失败阻止启动。

## 已知限制

- Windows EXE、PC 网页端与 Android 不自动同步聊天记录。
- Windows 首次构建需要联网安装 Python 包。
- 基础 EXE 不支持本地大语言模型和高保真本地语音。
- 基础 EXE 不附带 MeinaMix/Stable Diffusion、Diffusers、图片模型或本地生图运行库。
- 需要上述完整能力时，请复现完整 PC 网页环境并单独下载模型权重。
