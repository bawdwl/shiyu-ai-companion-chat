# 拾语 0.22.6 统一稳定版

这是 PC、Windows、Android 完整版和 Android 云端版的统一发布版本。
聊天行为以 Android `0.19.2` 为稳定基线，同时保留长期记忆、Qwen embedding、
备份、分支和 Prompt Inspector。

## 主要变化

- Android、PC、Windows 统一角色生成和聊天行为；
- 移除会干扰基础表达的 0.22 实验 Prompt；
- Android 设置增加独立“语义”页和启用开关；
- Android 支持语义检索检测；
- Android 角色档案恢复世界书展示和高级 JSON 编辑；
- Android 修复 embedding 下载进度条；
- Windows/PC 使用统一 0.22.6 版本和最新文档；
- 新增项目详细介绍和核心源码包。

## 文件与校验

### Android 完整版

- `apk-0.22.6/Shiyu-0.22.6-Android-arm64-v8a.apk`
- 包名：`com.shiyu.chat`
- 版本：`0.22.6 / versionCode 72`
- 大小：`302410035` bytes
- SHA-256：`60050AB0DDD8A5187978F1E3B64CE9438298807221C4084377845E5E7A0F9EBE`

### Android 云端版

- `apk-0.22.6-cloud/Shiyu-0.22.6-cloud-Android-arm64-v8a.apk`
- 包名：`com.shiyu.chat.cloud`
- 版本：`0.22.6-cloud / versionCode 72`
- 大小：`34136268` bytes
- SHA-256：`A60046A7E9D7F30AD4B0AE62D986B8E48F4E1460DA658A092DC25C1375E2B2F6`

### Windows EXE

- `windows/Shiyu-0.22.6-Windows-x64.zip`
- ZIP 大小：`64547527` bytes
- ZIP SHA-256：`771A58A5201B38982BE5BF34E7364147EECA3FFB42257ABAFEA9286DA15A835D`
- EXE SHA-256：`92CB8784CD4F7CF9FF31CA6B9CF33486128DD2F1336CB47AB3611EF735362482`

### PC 网页

- `pc-web-0.22.6.zip`
- 大小：`9727683` bytes
- SHA-256：`5930897488B7C420AD3E2D503A29D227A866D965D0B71D2112087F8CC5578FA6`

### 核心源码

- `shiyu-core-0.22.6.zip`
- 大小：`10242094` bytes
- SHA-256：`BC1C991FBFF1D9221C9F507E168BB3E8B2DC88A61822E8E596F089EAE0676D0F`

## 验证

- Python：`319` 项通过；
- 前端状态：`40` 项通过；
- React 生产构建：通过；
- Android 完整版/云端版 JVM：各 `137` 项通过；
- Android 两套 lint：通过；
- Android 完整版/云端版 APK：通过；
- Windows PyInstaller：通过；
- 两个 APK 的 v2 签名：通过；
- 真实设备半自动/人工验收仍需作者完成。

## 数据说明

发布包不包含用户聊天数据库、API Key、个人图片、录音或模型权重。
