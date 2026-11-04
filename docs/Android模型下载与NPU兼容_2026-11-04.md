# Android 模型下载与 NPU 兼容

日期：2026-11-04`n`nAndroid：`0.15.0 / versionCode 28`

## 目标

APK 只携带运行库、语音资源、安全检查器和应用代码，不携带 Qwen MNN 权重或
MeinaMix V12 权重。用户可在设置中按需下载，安装后直接把模型放入应用私有目录。

模型存储只保留两个槽位：

- 一个 Qwen3-1.7B LoRA MNN Q8 文本模型；
- 一个 Local Dream SD1.5 QNN 生图模型。

再次安装会替换同类模型，删除或下载失败会清理临时文件和 `.part` 文件。

## MNN 文本模型

| 角色 | 发布仓库 | 量化 |
|---|---|---|
| 宋知意 | `Alixs666/shiyu-qwen3-1.7b-song-zhiyi-mnn-q8` | Q8 block64 |
| 裴既明 | `Alixs666/shiyu-qwen3-1.7b-pei-jiming-mnn-q8` | Q8 block64 |

应用读取 ModelScope 根目录的 `MANIFEST.json`，按清单下载 `config.json`、
`llm_config.json`、`llm.mnn`、`llm.mnn.json`、`llm.mnn.weight`、
`tokenizer.mtok` 和对应角色提示词，再逐文件检查大小和 SHA-256。

下载先从缓存进行同盘原子移动；如果文件系统不支持移动，再流式复制到安装区。
ZIP 手工导入限制解压后不超过 3 GiB，并拒绝 `..` 路径和目录逃逸。

MNN 文本推理不要求 Qualcomm NPU。界面只要求设备为 ARM64 且至少有约 6 GB
物理内存；实际速度和内存仍取决于 SoC、散热和后台占用。

## MeinaMix V12 实际发布包

上游仓库：<https://huggingface.co/xororz/sd-qnn>

当前只发布以下三种 MeinaMix V12 QNN 包：

| 后缀 | 大小 | SHA-256 | 拾语选择规则 |
|---|---:|---|---|
| `min` | 1,156,786,441 | `7ed33339b305f0df97fd746790962808c38e56bb98e560d55d8eb239fa6c4dc8` | SM8350 / 骁龙 888，及其他已识别 V68+ 高通平台 |
| `8gen1` | 1,032,314,916 | `10d9f52607146c85428d3f66919a8a86ccf8f680c21b51ef2a39c6c0ff1b2695` | SM8450 / SM8475，骁龙 8 Gen 1 |
| `8gen2` | 1,031,765,133 | `90463ac0db6eb7424bb6c7a048c811bde2996b2321eaf58b64d02d626c333098` | SM8550/QCS8550/QCM8550，及其他上游复用该包的平台 |

**没有单独的 8 Gen 3 或 8 Elite 包。** Local Dream 当前源码把相关新平台映射到
`8gen2` 包，因此拾语也采用相同映射。

## HTP 映射

Local Dream README 明确写明：

- SD1.5 NPU：Qualcomm Hexagon V68 或更新；
- SDXL NPU：骁龙 8 Gen 3 或更新；
- CPU/GPU：可运行 SD1.5 和导入的 SDXL MNN 包。

本应用当前嵌入的是 SD1.5 QNN 路径，并打包 V68、V69、V73、V75、V79、V81
六套 HTP 运行库：

| SoC 标识 | 常见产品 | HTP |
|---|---|---|
| SM8350 / SM8350P | 骁龙 888 | V68 |
| SM8450 / SM8475 | 骁龙 8 Gen 1 | V69 |
| SM8550 / SM8550P / QCS8550 / QCM8550 | 骁龙 8 Gen 2 及派生平台 | V73 |
| SM8650 / SM8650P | 骁龙 8 Gen 3 | V75 |
| SM8750 / SM8750P | 骁龙 8 Elite | V79 |
| SM8850 / SM8850P | 新一代骁龙 8 旗舰 | V81 |

未知高通型号、非高通设备和无法确认 Hexagon 版本的设备不会猜测性安装模型，
而是保留“千问图片 API”入口。这不表示非高通设备绝对不能运行其他本地生图框架；
只是当前 Local Dream QNN 集成不能为它们选择可信的运行库和模型包。

## 存储清理

- 本地 MNN 和 Dream 模型都可从设置页删除；
- 下载中断、校验失败或安装失败时自动清理临时文件；
- 成功安装后删除下载缓存；
- 手工导入 ZIP 不支持穿越路径、符号链接式绝对路径或超过 3 GiB 的解压结果；
- 旧 MediaPipe 生图目录在启动时清理；
- 已移除不再使用的 FelizTTS/Kokoro 旧音色目录和未引用的英式英语词典，
  为 APK 减少约 63 MiB 源资源。

## 许可

Local Dream v2.8.1 采用 CC BY-NC 4.0，当前 Android 集成只能用于非商业 Demo。
MeinaMix 和各 MNN LoRA 模型继续遵循各自发布页的许可与使用条件。
