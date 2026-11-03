# Android 本地 MNN 与云端 API 双模式

## 本轮目标

每个角色可以在自己的角色档案中选择聊天模型：

- **云端 API**：继续使用设置中选定的 OpenCode Go 或 DeepSeek，支持图片、云端流式回答和后台结构化记忆提取。
- **本地 MNN**：文字由当前手机上的 Qwen3-1.7B 凉宫春日 LoRA MNN 模型生成，不连接电脑，也不把聊天文字发送给聊天 API。

旧角色升级后默认仍为云端 API，不改变原有聊天方式。

## 已实现

- Room `characters` 表新增 `modelSource`，默认值 `api`。
- Room schema 从 11 升到 12，并提供 `MIGRATION_11_12`。
- 角色编辑页可选择“云端 API”或“本地 MNN”；切换后立即保存模型来源，避免用户误以为选择已生效却没有落盘。
- 聊天页标题和输入框状态会显示当前角色使用的模式。
- 本地模式复用角色卡、世界书、近期对话、结构化记忆、摘要和内部状态；聊天主链路不依赖 API Key。
- 本地请求会在最新用户消息末尾加入 Qwen3 的 `/no_think` 软开关，并移除运行时可能返回的思考段。
- 本地模式禁止发送图片，避免把云端视觉消息格式传给纯文本 MNN 会话。
- 本地模式不把本轮聊天计入云端 Token 限额，也不排队新的云端记忆提取任务；本地原话摘录仍会保存。
- 设置页可以导入 MNN 模型 ZIP，显示安装状态和占用空间。
- Android 启动时会创建完整的“凉宫春日（LoRA）”角色卡、世界书和开场白，并默认绑定手机本地 MNN；不会覆盖已有同名角色。
- APK 内打包 MNN ARM64 运行库，模型权重不进入 APK。
- Trace 记录 `local-mnn`、模型 ID、输入/输出 Token、prefill/decode 时间和本地调用错误。

## 模型包

当前 Android 本地模型是把凉宫春日 LoRA 合并到 Qwen3-1.7B 后，用 MNN 3.5.0 导出为 Q4 block64：

```text
outputs/models/qwen3-1.7b-haruhi-lora-mnn-3.5.0-q4b64-v350/
```

模型 ZIP 约 893.6 MB：

```text
outputs/models/qwen3-1.7b-haruhi-lora-mnn-3.5.0-q4b64-v350.zip
SHA-256: EE1DAF21CA687ED9506C7EFCE8E435B9F68D40C267602D04B60C31B30AC56FD4
```

`export_args.json` 中的 `lora_path` 证明导出时已合并：

```text
outputs/lora-haruhi-chatharuhi-v1
```

这是春日专用合并单模型包。电脑 MNN 3.5.0 已实际加载并生成，回答正常。手机运行时仍不能在孙悟空、许澄、凉宫春日 Adapter 之间动态切换；本轮只部署春日。

## 手机导入

推荐使用应用设置：

1. 打开“应用设置”。
2. 找到“手机本地 Qwen3-1.7B（春日 LoRA）”。
3. 选择 `qwen3-1.7b-haruhi-lora-mnn-3.5.0-q4b64-v350.zip`。
4. 等待导入完成，确认设置页显示已安装和模型大小。
5. 打开任意角色，进入“编辑角色档案”。
6. 在“聊天模型”中选择“本地 MNN”并保存。

也可以连接已授权的 Android 设备后运行：

```powershell
.\scripts\deploy_android_local_mnn.ps1
```

该脚本构建并覆盖安装 APK，然后把模型 ZIP 推送到：

```text
/sdcard/Download/qwen3-1.7b-haruhi-lora-mnn-3.5.0-q4b64-v350.zip
```

模型仍必须通过应用内“导入本地 MNN 模型”完成解压。不要用 ADB 直接把模型文件写入 `Android/data/com.shiyu.chat/files/models`：Android 会把新建目录归到 `shell` 用户，应用随后读取 `config.json` 时会得到 `EACCES (Permission denied)`。本轮真机已复现并清理该问题；应用自己导入后 `MnnModelManager` 日志为 `ready=true`。

## 构建与自动验证

```powershell
.\scripts\build_android.ps1
```

本轮结果：

- `versionName 0.13.1`、`versionCode 19`
- Android JVM 单元测试 45 项通过
- `assembleDebug` 通过
- APK：`android/app/build/outputs/apk/debug/app-debug.apk`
- APK SHA-256：`A0B5A795DEF63238829C417E97EC1FE910A9D631D11B5C7F32C2E98C35D3B33A`

## 已知限制

- 春日 LoRA 的电脑 MNN 3.5.0 冒烟可以正常生成，但质量门禁历史结论仍是 No-Go，不能据此宣称生产安全。
- 本地模式暂不支持图片理解。
- 本地模式不运行云端结构化记忆提取；已有记忆仍会进入提示，近期对话摘录仍可在本机生成。
- 模型 ZIP、权重、个人聊天数据库和 API Key 均不进入 Git。
