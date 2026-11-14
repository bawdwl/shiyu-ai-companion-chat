# ONNX Runtime Android Java API

`onnxruntime-android-1.28.0.jar` contains only `classes.jar` extracted from:

- Maven: `com.microsoft.onnxruntime:onnxruntime-android:1.28.0`
- Source: https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/1.28.0/
- License: MIT

The extracted jar SHA-256 is:

`E671ACBFF1BB86FFBBBE7701CF9351A04E1F58C51AE5B031912451E5988471B6`

The large `libonnxruntime.so` native library is intentionally excluded from the
jar. The full official 1.28.0 arm64 runtime and its `libonnxruntime4j_jni.so`
bridge are stored in shared `src/main/jniLibs` so both the full and cloud
editions can run the optional embedding model.

`sherpa-onnx-1.13.8.aar` contains another ONNX Runtime copy for the full
edition. Gradle uses `pickFirsts` for `libonnxruntime.so`, and the tracked AAR
entry was replaced with the same official 1.28.0 library to keep the native
ABI and Java bridge on one runtime version.

Tracked native hashes:

- `libonnxruntime.so`:
  `F826D8EFB03ADF0A84F10E7BA408F9D4CD11B0A2CCD8D08AEB0F7451FB50CACC`
- `libonnxruntime4j_jni.so`:
  `99DB53CA399095CFA17F3F40924B1012A0FF5846C32198E8CEF01DD8480F6B45`
