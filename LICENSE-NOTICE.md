# 许可与本地材料边界

更新：2026-11-03。当前项目代码采用根目录 `LICENSE` 中的 AGPL-3.0。历史版本已取得的
MIT 授权不受当前重新许可影响。

拾语使用自己的HTML/CSS/JavaScript页面，参考SillyTavern的角色卡、世界书与提示组织思路。SillyTavern参考源码仍在third_party/SillyTavern，其上游AGPL许可与版权声明保留；当前拾语运行不依赖酒馆服务。角色图片来源记录在web/portraits/README.md。

GPT-SoVITS实际源码已安装在third_party/GPT-SoVITS，本地上游LICENSE为MIT；拾语通过本机API调用它。模型权重、语音参考素材与代码许可分别适用，不能根据源码许可推出任意声音可公开。

PC记忆检索可选用 `BAAI/bge-small-zh-v1.5`。该模型卡当前标注MIT，模型权重不属于拾语代码许可；发布或再分发前仍须重新核对上游模型卡、文件哈希和许可原文。模型保存在被Git忽略的 `outputs/models/`。

Android 本地角色头像直接嵌入 Local Dream v2.8.1 的 SD1.5/QNN 推理后端，
包含其编译后的后端可执行文件、Qualcomm QNN 运行库与安全检查器。Local Dream
代码及随附编译产物采用 **CC BY-NC 4.0**，只允许非商业使用；许可与集成说明位于
`android/app/src/full/assets/third_party/local-dream/`。MeinaMix V12、
DreamShaper 等 QNN 模型包不包含在 APK、Git 仓库或普通发布包中，由用户在应用内
单独导入，并分别遵守模型卡许可。若要商业发布，必须替换这部分 CC BY-NC 组件。

Android 当前内置的 `sherpa-onnx-vits-zh-ll` 来自 k2-fsa 的公开模型归档，归档注明由社区使用 `Plachtaa/VITS-fast-fine-tuning` 转换。模型包含 `suyingxue / gunian / fushiyu / bingjiao / bazong` 五个 speaker，其中 `bazong` 与 `gunian` 是同一男声；应用只显示去重后的女声 1、男声 1、女声 2、男声 2。该归档没有单独声明模型权重许可或声音身份授权；项目只将其用于当前非商业 Demo，不据此声称可以商业使用或再授权这些声音。文件来源、哈希和边界记录在 `android/app/src/full/assets/speech/vits-zh-ll/MODEL_NOTICE.md`。

Qwen3、MNN/GPTQ衍生产物、Whisper、VITS 模型、PyTorch、Transformers、PEFT以及其他依赖，保留各自上游许可和来源信息。项目许可不覆盖第三方模型或数据。当前公开代码仓库的代码可公开，但模型权重的再分发和商业使用仍需根据对应模型卡和权利来源单独判断。

本机保留LCCC、NaturalConv、RoleBench等数据审查及历史实验，存在来源、研究用途和原作素材边界；原文、派生数据和相关Adapter不因本机保存就获得公开分发授权。旧项目原创教学样例保留其原许可记录，不把该授权扩大到第三方数据。

`archive/` 包含旧代码、文档、用户原始材料及历史研究记录；`outputs/` 包含私人
音频、数据库、模型和环境，两者默认不公开。以后准备发布时仍须按具体文件、数据与
权重复核。
