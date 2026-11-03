# Local Dream integration record

- Upstream: https://github.com/xororz/local-dream
- Version: v2.8.1
- Source commit used for integration review: `a7666f6198412a58c6eb1eacc28828aa40c7d7ae`
- License: CC BY-NC 4.0 (see `LICENSE`)

拾语 only uses the SD1.5 NPU backend and model-import behavior. The upstream
Compose UI, Room history, download service, remote-host mode, upscaler, and
model catalog are not part of the combined app.

The following prebuilt upstream files are embedded in the Android app:

- `lib/arm64-v8a/libstable_diffusion_core.so`
- QNN runtime libraries for Hexagon V75
- `assets/safety_checker.mnn`

Model ZIP files remain user-imported and are not distributed with 拾语.
