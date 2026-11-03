# Android release status

No prebuilt APK is published in this repository yet.

The Android client is still under active development. Build it from source:

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-17"
$env:ANDROID_HOME = "C:\path\to\Android\Sdk"
.\scripts\build_android.ps1
```

Output:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```
