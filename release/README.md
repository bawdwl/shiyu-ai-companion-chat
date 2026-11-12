# Shiyu Releases

发布包通过 GitHub Release 分发，APK 和 ZIP 不进入 Git。本地目录只保留当前
发布物，避免重复保存每次测试构建：

```text
release/
├─ apk-0.19.1/       当前完整版 Android APK 与云端精简版 APK
├─ windows/       当前 Windows onedir ZIP
└─ README.md
```

## Android 0.19.2 当前构建

`apk-0.19.2/` 是多行输入框自动增高和多行内容可见性修复后的发布包：

- 完整版：`apk-0.19.2/Shiyu-0.19.2-Android-arm64-v8a.apk`
- 云端版：`apk-0.19.2/Shiyu-0.19.2-cloud-Android-arm64-v8a.apk`
- 版本：完整版 `0.19.2 / versionCode 61`，云端版 `0.19.2-cloud / versionCode 61`
- 完整版 SHA-256：`60D2334D6555B6CB00EE35655BAD7574316C4A6F7C760E0F7E4BB9C3C5677B4F`
- 云端版 SHA-256：`37702DBAF3F95FD69C621A1D1DC466C20E61E256C9D751B4C984A0C31FD75588`

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
