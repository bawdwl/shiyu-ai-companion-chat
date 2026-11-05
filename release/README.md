# Shiyu releases

Prebuilt applications are distributed as GitHub Release assets and are not
stored in Git.

## Windows

- Windows `0.18.0` uses the same React workspace, character profile editor,
  role generation flow and automatic structured-memory backend as the PC web
  build.
- Download and extract `Shiyu-0.18.0-Windows-x64.zip`, then run
  `Shiyu-0.18.0-Windows-x64.exe`.
- API keys are entered inside the app and are not bundled.
- ZIP SHA-256:
  `197D4EA9408327C48C8909DCB3DA1C2BE46014DECAAC682A2040CD12BCB11658`
- EXE SHA-256:
  `A1DA433F6792789D05BD968BB27AC146CA03DE13CF5373E1A99872529B19A6A5`

## Android

The current prebuilt APK is distributed as a GitHub Release asset:

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
