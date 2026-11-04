# Android releases

The current prebuilt APK is distributed as a GitHub Release asset and is not
stored in Git:

- [Shiyu Android 0.16.1](https://github.com/bawdwl/shiyu-ai-companion-chat/releases/tag/v0.16.1)

Release requirements:

- Android 8.0 or newer
- ARM64 device
- API keys are configured by each user inside the app

The `v0.16.1` APK is `282,548,528` bytes with SHA-256
`5B487796E5768CB80BD5E44BD395D0792067ADA75D0A6DFE23F187C7E3BE101A`.

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
