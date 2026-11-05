# Android releases

The current prebuilt APK is distributed as a GitHub Release asset and is not
stored in Git:

- [Shiyu Android 0.17.0](https://github.com/bawdwl/shiyu-ai-companion-chat/releases/tag/v0.17.0)

Release requirements:

- Android 8.0 or newer
- ARM64 device
- API keys are configured by each user inside the app

The `v0.17.0` APK is `283,040,467` bytes with SHA-256
`0F6F28E2876329DBA0F1B5E9701E3475F83BD46F1992D65024514C38F51B600B`.

Build from source:

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-17"
$env:ANDROID_HOME = "C:\path\to\Android\Sdk"
.\scripts\build_android.ps1
```

Output:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```
