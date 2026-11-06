# 拾语 PC 网页版安装与复现

PC 网页版面向开发者和需要查看完整源码的评审者。它由 React 前端和 FastAPI
后端组成，聊天记录、角色、长期记忆和 API Key 都保存在本机。

## 环境

- Windows 10/11 x64
- Git
- Python 3.11 x64
- 修改前端时需要 Node.js 20+ 和 pnpm

仅使用云端 API 时不需要 NVIDIA 显卡，也不需要下载 Qwen 或 Stable Diffusion
权重。

## 安装

```powershell
git clone https://github.com/bawdwl/shiyu-ai-companion-chat.git
cd shiyu-ai-companion-chat

conda create -n shiyu-chat python=3.11 pip -y
conda activate shiyu-chat
python -m pip install -r requirements-api-windows-py311.txt
.\scripts\setup_secret_runtime.ps1 -Python python
```

仓库已经包含生产前端 `web/react-dist/`。只有修改 React 源码后才需要执行：

```powershell
pnpm --dir frontend install --frozen-lockfile
pnpm --dir frontend build
```

## 启动

DeepSeek：

```powershell
python launch_app.py --provider deepseek --speech-provider off --asr-provider off
```

OpenCode Go：

```powershell
python launch_app.py --provider opencode --speech-provider off --asr-provider off
```

浏览器打开 <http://127.0.0.1:8000/ui/>。进入“应用设置”，填写对应 API Key。
Key 只保存在后端本机加密 Secret Store，不会返回给浏览器。

首次启动会安装四个可编辑、可删除的演示角色：李白、宋知意、裴既明、萧承渊、
萧承渊。删除后不会在下次启动时自动恢复。

## 复现验收

```powershell
$env:PYTHONUTF8 = "1"
python -B scripts/check_project.py
node --test tests/frontend_state.test.cjs
pnpm --dir frontend build
```

浏览器中至少检查：

1. 四个角色卡和头像正常显示。
2. 新建、编辑、删除角色可用。
3. 填写 Key 后可以流式对话与停止生成。
4. 聊天页顶部栏固定，消息列表独立滚动。
5. 记忆页可以查看、修正、标记错误和删除记录。
6. 角色聊天页可以导入自定义头像。
7. 重新启动服务后 Key、角色和聊天仍在。

## 数据位置

命令行网页版默认写入仓库的 `outputs/`。不要把该目录提交到 Git，因为其中可能
包含数据库、API Key、头像、上传图片和模型缓存。

## 可选模型

- 长期记忆的 BGE 向量模型：运行 `python scripts/fetch_embedding_model.py`。
- 语音输入：准备 OpenAI Whisper ONNX 模型。
- 角色语音：优先接入 CosyVoice 2，并保留 GPT-SoVITS、MeloTTS 回退。
- 本地立绘：准备 Diffusers SD1.5 模型，通过“设置 → 本地图片模型”选择路径。
- 本地 Qwen 与 LoRA：属于开发者实验路径，不是仅使用云端 API 的必要依赖。

模型包、Adapter 和第三方权重不随仓库分发。
