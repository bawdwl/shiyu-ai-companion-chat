# Shiyu Releases

发布包通过 GitHub Release 分发，APK 和 ZIP 不进入 Git。本地目录只保留当前
发布物，避免重复保存每次测试构建：

```text
release/
├─ apk-0.18.22/   当前 Android APK
├─ windows/       当前 Windows onedir ZIP
└─ README.md
```

## Android 0.18.22

- GitHub：[`v0.18.22`](https://github.com/bawdwl/shiyu-ai-companion-chat/releases/tag/v0.18.22)
- 文件：`apk-0.18.22/Shiyu-0.18.22-Android-arm64-v8a.apk`
- 大小：`299,731,904` 字节
- SHA-256：`9A2DDA53BE979FD9F1B40FB7C4CBE0FD097C9A298B5ABB318972222A99CD4E8E`
- 要求：Android 8.0+、ARM64；API Key 由用户在应用内填写

当前 APK 内置 12 个初始角色及头像、背景。聊天、记忆、API Key 和个人图片不在
发布包中。

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
