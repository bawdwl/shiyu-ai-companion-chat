# 拾语 Windows 桌面版

桌面版复用与网页版完全相同的 FastAPI 后端和 React 前端，只增加 Windows
启动器、数据目录和 WebView2 窗口。它不复制聊天业务逻辑。

## 源码运行

```powershell
conda create -n shiyu-desktop python=3.11 pip -y
conda activate shiyu-desktop
python -m pip install -r desktop/requirements-windows.txt

.\scripts\setup_secret_runtime.ps1 -Python python
pnpm --dir frontend install --frozen-lockfile
pnpm --dir frontend build
python desktop/shiyu_desktop.py
```

首次启动后，在应用设置里填写 DeepSeek 或 OpenCode Go API Key。对话、角色、
长期记忆、密钥和日志都保存在：

```text
%LOCALAPPDATA%\Shiyu
```

可用 `SHIYU_DATA_DIR` 指向另一个目录，便于便携运行或测试。

## 构建 EXE

```powershell
python desktop/build_desktop.py
```

输出：

```text
release/windows/Shiyu-0.18.3-Windows-x64/
```

采用 PyInstaller `onedir`，发布时必须分发整个目录或重新压缩该目录。不要只发送
`Shiyu-0.18.3-Windows-x64.exe`。

程序不会打入模型权重、API Key、SQLite 数据库或个人图片。Windows 10/11 需要
Microsoft Edge WebView2 Runtime；新版 Windows 通常已自带。

发布 EXE 是基础云端版，支持角色生成、聊天、世界书、长期记忆和云端图片输入。
本地 Qwen/LoRA、MeinaMix、BGE、Whisper 与 CosyVoice 等完整能力需要运行
PC 网页工程并单独安装环境和模型，不能通过基础 EXE 自动获得。
