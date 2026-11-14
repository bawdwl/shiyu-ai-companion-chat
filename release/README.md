# Shiyu Releases

发布包通过 GitHub Releases 分发；APK、EXE ZIP 和模型权重不进入 Git。

## Android 0.21.0

- 完整版：`apk-0.21.0/Shiyu-0.21.0-Android-arm64-v8a.apk`
- 云端版：
  `apk-0.21.0-cloud/Shiyu-0.21.0-cloud-Android-arm64-v8a.apk`
- 完整版 SHA-256：
  `9CB6E785D5E5C2E4BBB2BC089952448EAF6E10E6C59E45E08BA9DECD553D6DB5`
- 云端版 SHA-256：
  `F01EC4F0DB52BBA3347E3E0ACD3C8E655CC3915A9EA7739C48D52BBAB2452AD5`

`0.21.0` 是 Android 完整版的最终功能版本。后续新功能、修复与 Release 只维护云端版。

## Windows 0.18.0

- 文件：`windows/Shiyu-0.18.0-Windows-x64.zip`
- ZIP SHA-256：
  `9D089A71A83291C04FFCAD814235DBEEF7710106E955F077499DBBB65F4AF8BA`
- EXE SHA-256：
  `A365221D65CA5FBFF0A6AED6F6C51197D6C71460DEB71AD1538E741C8DBA1D2E`

Windows 包是云端基础版。完整解压后运行 EXE，不要把单个可执行文件移出目录。

## 重新构建

```powershell
.\scripts\build_android.ps1
.\scripts\build_android_cloud.ps1
```

发布步骤见 [docs/GITHUB_APK_RELEASE.md](../docs/GITHUB_APK_RELEASE.md)。
