# Android 本地 Dream 生图嵌入

日期：2026-11-02
Android：`0.14.3 / versionCode 27`

## 产品结构

拾语 Android 现有两条彼此独立的模型路由：

1. 角色聊天：云端 OpenCode Go / DeepSeek，或用户导入的 Qwen3-1.7B MNN。
2. 角色头像：千问图片 API，或用户导入的 Dream/SD1.5 QNN，默认参数面向 MeinaMix V12。

生成头像时不再启动 MediaPipe。旧版 `files/avatar-models` 会在应用启动后的后台清理任务中删除。

## 内置 Dream 后端

应用内置 Local Dream v2.8.1 的 SD1.5 NPU 后端：

- `libstable_diffusion_core.so`
- Qualcomm QNN HTP V75 运行库
- 安全检查器
- Local Dream CC BY-NC 4.0 许可文本

后端在应用私有目录启动独立进程，监听 `127.0.0.1:18081`。端口使用 18081，因此不会和另行安装的 Local Dream 的 8081 端口冲突。模型和输出均留在手机应用私有目录。

上游来源、提交号和许可边界见 `third_party/local-dream/INTEGRATION.md` 与 `LICENSE-NOTICE.md`。

## 模型导入

设置页“Dream 本地生图”接受 Local Dream 兼容的 SD1.5 QNN ZIP。导入器会：

- 流式读取 ZIP，不把整个压缩包复制到内存；
- 按 Local Dream 目录结构扁平导入所需文件；
- 校验 `tokenizer.json`、`vae_encoder.bin`、`vae_decoder.bin`、`unet.bin`、`clip_v2.mnn`、`pos_emb.bin`、`token_emb.bin`；
- 限制解压后最大 3 GiB；
- 写入 `npucustom` 标记；
- 导入成功后替换应用内当前 Dream 模型。

首次验收模型为 MeinaMix V12 QNN `_8gen2`，大小 1,031,765,133 bytes，SHA-256：

```text
90463AC0DB6EB7424BB6C7A048C811BDE2996B2321EAF58B64D02D626C333098
```

模型权重不进入 APK 或 Git。

## 生图设置

设置页支持：

- 正面提示词模板，`{appearance}` 会替换为角色外观；
- 负面提示词；
- 1 到 50 步；
- CFG 1 到 20；
- 固定种子；
- DPM++ SDE Karras、DPM++ 2M Karras、Euler A Karras 三种预设；
- 当前固定使用与模型包一致的 512×512，其他分辨率暂不开放。

SD1.5 的 CLIP 文本编码器固定为 77 Token，不能简单通过调大应用参数突破。默认正面模板压缩到约 15 Token，并把角色外观放在前段；生成前会调用 `/tokenize`，超过 77 Token 时按顺序保留前段核心角色描述，移除末尾次要风格词。负面提示词超限时同样从末尾裁剪。

创建角色时会强制同时生成详细中文外貌和英文生图提示词。中文外貌需包含明确性别、年龄、脸型、眼睛、头发、身形、服装、配饰和神态；英文提示词自动加主体标签：男性为 `1boy, male, solo`，女性为 `1girl, female, solo`。如果模型漏写主体或出现性别冲突，应用会单独调用一次外貌补全，并保存修正结果。

角色编辑页提示作者将生图专用英文控制在约 35 个单词内。聊天页面中的角色头像背景透明度为 `0.085`，角色轮廓比旧版 `0.055` 稍明显，同时消息气泡仍保持高对比度。

角色编辑页新增“生图专用外貌提示词”，填写后优先于中文外貌描述，用于保持角色脸型、发型、眼睛、服装和神态。

## 角色目录清理

本次升级会将以下旧微调实验角色连同消息、记忆、Trace、状态和头像一并删除：

- `custom-lora-wukong-v1`
- `custom-lora-xucheng-v1`
- `custom-lora-haruhi-v1`

保留并自动补齐的微调角色只有宋知意 `custom-lora-song-zhiyi-v1`。这里修复了旧实现中凉宫春日角色虽然进入待删除列表、但 SQL 删除白名单没有包含其 ID 的问题。

## 验证状态

- Kotlin 编译通过；
- 60 项 Android JVM 单元测试通过；
- `assembleDebug` 通过；
- APK 只包含 `arm64-v8a`；
- APK 中已确认包含 Dream 原生后端、QNN V75 运行库和安全检查器，且不再包含 MediaPipe。
- APK 产物：`android/app/build/outputs/apk/debug/app-debug.apk`
- Android Lint 因当前离线环境缺少 `com.android.tools.lint:lint-gradle:31.7.3`，未能启动；这是工具缓存缺口，编译、测试和打包不受影响。

真机端到端验收仍需设备在线后完成：覆盖安装、导入 MeinaMix V12、启动内置后端、生成头像、检查进程日志和生成耗时。本次构建完成后设备已断开，因此不得把已进入设备目录的 APK 记作真机已验证。
