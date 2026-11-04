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
- QNN HTP runtime libraries for Hexagon V68, V69, V73, V75, V79 and V81
- `assets/safety_checker.mnn`

Model ZIP files remain user-imported and are not distributed with 拾语.

The app detects the Qualcomm SoC, maps it to the matching HTP runtime and
selects one of the three published MeinaMix V12 packages:

- `_min`: Hexagon V68+ compatible devices, including SM8350;
- `_8gen1`: SM8450 and SM8475;
- `_8gen2`: SM8550/QCS8550/QCM8550, and reused for the documented newer
  flagship mappings when no separate package is published.

Unknown Qualcomm architectures and non-Qualcomm devices do not receive an NPU
package. They fall back to the Qwen image API. This is a limitation of the
current Local Dream QNN integration, not a claim that other SoCs cannot ever run
local diffusion.
