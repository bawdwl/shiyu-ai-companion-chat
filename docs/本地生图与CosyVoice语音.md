# 拾语本地生图与 CosyVoice 2 语音

## 本地图片生成

PC 网页版只使用本机 Diffusers 模型生成角色立绘，不调用云端图片 API。

当前默认模型为 `andro-flock/MeinaMix-V12_-Final` 的 Diffusers 版本，下载后约
2.1 GB，存放于 `outputs/models/meinamix-v12/`。初始化命令：

```powershell
.\scripts\setup_local_image_runtime.ps1 `
  -Python "D:\Others\Study\Anaconda3\envs\qwen3-im-chat\python.exe"
```

在“设置 → MeinaMix V12 本地生图”中保存模型目录，然后在角色右侧点击“本地生成立绘”。
服务端默认 50 步、512×512，可通过 `SHIYU_IMAGE_STEPS`、
`SHIYU_IMAGE_WIDTH` 和 `SHIYU_IMAGE_HEIGHT` 调整。

验证命令：

```powershell
python scripts\smoke_local_image.py
```

输出位于 `outputs/image-smoke/meinamix-smoke.webp`。

## CosyVoice 2

CosyVoice 2 的代码和模型采用 Apache-2.0，本地运行不按调用次数收费。
`CosyVoice2-0.5B` 官方页面标称约 4.86 GB；完整官方目录若同时保留
fp16/fp32 编码器和训练缓存，本地实际约 5.62 GB。显卡、磁盘和电费仍由本机承担。

准备独立环境和模型：

```powershell
.\scripts\setup_cosyvoice2_local.ps1 `
  -Conda "D:\Others\Study\Anaconda3\Scripts\conda.exe" `
  -EnvName cosyvoice2
```

启动本地 FastAPI 服务：

```powershell
conda run -n cosyvoice2 python `
  third_party\CosyVoice\runtime\python\fastapi\server.py `
  --port 50000 `
  --model_dir third_party\CosyVoice\pretrained_models\CosyVoice2-0.5B
```

Windows 上建议在启动前设置项目内缓存目录，避免 Numba 尝试写入不可写位置：

```powershell
$env:NUMBA_DISABLE_JIT = "1"
$env:NUMBA_CACHE_DIR = "$PWD\outputs\cosyvoice-cache\numba"
$env:MODELSCOPE_CACHE = "$PWD\outputs\cosyvoice-cache\modelscope"
```

再启动拾语：

```powershell
python launch_app.py `
  --provider opencode `
  --speech-provider profiles `
  --cosyvoice-url http://127.0.0.1:50000 `
  --asr-provider off
```

启动后，“设置 → CosyVoice 2 角色语音”会显示当前语音链路。这里显示“已配置”
表示拾语已接入角色音色链路；要实际合成语音，50000 端口的 CosyVoice 2 服务仍需
保持运行，否则会回退到 GPT-SoVITS 或 MeloTTS。

在角色右侧打开“角色音色”，填写音色名称、声音描述、3–10 秒单声道 WAV，
以及与该录音逐字一致的中文文本。参考音频必须来自本人或已获明确授权的声音。

本地语音顺序：

1. 有完整参考音频和逐字稿时优先使用 CosyVoice 2；
2. CosyVoice 不可用时使用 GPT-SoVITS；
3. 最后回退到 MeloTTS。

CosyVoice 官方服务返回 16 位裸 PCM，拾语按 24000 Hz 封装为 WAV。
