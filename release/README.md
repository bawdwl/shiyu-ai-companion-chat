# Shiyu Releases

发布包通过 GitHub Release 分发，APK 和 ZIP 不进入 Git。本地目录只保留当前
发布物，避免重复保存每次测试构建：

```text
release/
├─ apk-0.18.24/   当前本地候选 APK
├─ apk-0.18.23/   已发布 Android APK
├─ windows/       当前 Windows onedir ZIP
└─ README.md
```

## Android 0.18.24 本地候选

本地 APK 已构建并通过测试，尚未发布 GitHub Release。主要修复设置中选择
DeepSeek 官方 API 后，创建角色仍然强制使用 OpenCode Go 的问题。

- 文件：`apk-0.18.24/Shiyu-0.18.24-Android-arm64-v8a.apk`
- 大小：`300,290,445` 字节
- SHA-256：`FBEEC4F8F754164D966E498F3204EC31A5195F778DC5A70C27E3D2920CB57202`
- 要求：Android 8.0+、ARM64；API Key 由用户在应用内填写

当前 APK 内置 12 个初始角色及头像、背景。聊天、记忆、API Key 和个人图片不在
发布包中。

## Android 0.18.23 已发布

- GitHub：`https://github.com/bawdwl/shiyu-ai-companion-chat/releases/tag/v0.18.23`
- 文件：`apk-0.18.23/Shiyu-0.18.23-Android-arm64-v8a.apk`
- 大小：`299,852,020` 字节
- SHA-256：`48644B306ABB6400F3080F2CBDF2264C3CB2F506647458046A5D5E61E74FBFFE`

## 上传 APK

先提交并推送源码，再运行发布脚本：

```powershell
git push origin main

.\scripts\publish_android_release.ps1 `
  -Version 0.18.24 `
  -NotesFile release\notes-v0.18.24.md `
  -WhatIf

.\scripts\publish_android_release.ps1 `
  -Version 0.18.24 `
  -NotesFile release\notes-v0.18.24.md
```

完整说明见 `docs/GITHUB_APK_RELEASE.md`。APK 不提交到 Git。

重新构建：

```powershell
.\scripts\build_android.ps1
```

输出：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## Windows 0.18.0

- 文件：`windows/Shiyu-0.18.0-Windows-x64.zip`
- ZIP SHA-256：`9D089A71A83291C04FFCAD814235DBEEF7710106E955F077499DBBB65F4AF8BA`
- EXE SHA-256：`A365221D65CA5FBFF0A6AED6F6C51197D6C71460DEB71AD1538E741C8DBA1D2E`
- 需要完整解压目录后运行 EXE，不要只复制可执行文件
- API Key 由用户在应用内填写

Windows 包是基础云端版。本地 Qwen/LoRA、MeinaMix、BGE、Whisper 和
CosyVoice 需要按 PC 网页完整环境自行安装。
