# Shiyu Releases

发布包通过 GitHub Release 分发，APK 和 ZIP 不进入 Git。本地目录只保留当前
发布物，避免重复保存每次测试构建：

```text
release/
├─ apk-0.19.0/       当前完整版 Android APK
├─ apk-0.19.0-cloud/ 当前云端精简版 APK
├─ windows/       当前 Windows onedir ZIP
└─ README.md
```

## Android 0.19.3 本机测试构建

`apk-0.19.3-test/` 是内部状态策略收敛为 `off` 后的覆盖测试包，尚未替换公开
Release：

- 完整版：`apk-0.19.3-test/Shiyu-0.19.3-full-test-android-arm64.apk`
- 云端版：`apk-0.19.3-test/Shiyu-0.19.3-cloud-test-android-arm64.apk`
- 版本：完整版 `0.19.3 / versionCode 58`，云端版 `0.19.3-cloud / versionCode 58`
- Python、前端、Android JVM、Lint 和 APK 构建均通过。
- 本机当时没有连接 ADB 设备，两个包尚未执行覆盖安装验证。

## Android 0.19.0 已发布

两个安装包共用同一套 8 个初始角色和 16 张头像/背景图片：
宋知意、裴既明、李白、萧承渊、姜拂衣、空条承太郎、凌清鸢和沈砚辞。
本次同时降低角色管理与消息页头像的解码和绘制成本，改善快速滑动。

完整版：

- 包名：`com.shiyu.chat`
- 文件：`apk-0.19.0/Shiyu-0.19.0-Android-arm64-v8a.apk`
- 大小：`299,207,736` 字节
- SHA-256：`D06A7C1A85298B77CDEA9227E679FD9388625C7F3B79706DAA791D87F43EA2B1`

云端精简版：

- 包名：`com.shiyu.chat.cloud`
- 文件：`apk-0.19.0-cloud/Shiyu-0.19.0-cloud-Android-arm64-v8a.apk`
- 大小：`22,310,896` 字节
- SHA-256：`D922F63B110C5F5BC6801D684ED639926CF9F2612A38494D0D9CC3D47285B41D`
- 保留：云端聊天、角色、记忆、状态、场景、图片输入和千问云端生图。
- 移除：MNN、QNN、Local Dream、ASR、TTS 和相关原生运行库。

两个包要求 Android 8.0+、ARM64，可以同时安装且数据独立。API Key 由用户在
应用内填写。聊天、记忆、API Key 和个人图片不进入发布包。

## 发布后续版本

当前版本已经发布。后续版本使用
`scripts/publish_android_release.ps1`，完整步骤见
`docs/GITHUB_APK_RELEASE.md`。APK 不提交到 Git。

重新构建：

```powershell
.\scripts\build_android.ps1
.\scripts\build_android_cloud.ps1
```

输出：

```text
android/app/build/outputs/apk/full/debug/app-full-debug.apk
android/app/build/outputs/apk/cloud/debug/app-cloud-debug.apk
```

## Windows 0.18.0

- 文件：`windows/Shiyu-0.18.0-Windows-x64.zip`
- ZIP SHA-256：`9D089A71A83291C04FFCAD814235DBEEF7710106E955F077499DBBB65F4AF8BA`
- EXE SHA-256：`A365221D65CA5FBFF0A6AED6F6C51197D6C71460DEB71AD1538E741C8DBA1D2E`
- 需要完整解压目录后运行 EXE，不要只复制可执行文件
- API Key 由用户在应用内填写

Windows 包是基础云端版。本地 Qwen/LoRA、MeinaMix、BGE、Whisper 和
CosyVoice 需要按 PC 网页完整环境自行安装。
