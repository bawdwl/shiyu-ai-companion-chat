# 拾语 Android 0.21.0

`0.21.0` 集中改善长对话中的长期记忆、Prompt 组装和可选语义检索。此次同时发布
Android 完整版与云端版；完整版在此版本后冻结，后续迭代只维护云端版。

## 长期记忆

- 修复 OpenCode Go 的 JSON 记忆任务被网关拒绝后无法写入长期记忆的问题。
- 升级后自动重新排队此前因 `response_format`、`atlas` 或截断响应失败的记忆任务。
- 结构化记忆按核心资料、钉住事实、近期事实、相关事实、历史摘录和事件分层选择。
- 历史回忆词覆盖“还记得、第一次、那次、当时、后来、那阵子”等自然表达，并会
  结合最近对话扩写“那件事”一类指代查询。
- 增加云端滚动剧情摘要，减少超长会话全部重复发送历史消息的问题。
- 记忆排序考虑事实时效、计划状态、来源可信度和生命周期；访问次数不再提高排名。
- 完全重复的事实自动合并，旧记录保留；陈旧计划降权而不静默认定取消。

## Prompt 与世界设定

- System Prompt 按稳定区块和优先级组装，在 9,000 字符预算内保留必需内容。
- Prompt Inspector 显示区块预算、命中的世界书、记忆选择、被省略项和耗时。
- 固定事实增加逐条确认状态，只有 `confirmed` 内容进入运行时 Prompt。
- 世界书与聊天长期记忆使用独立检索和注入区块。
- 新建角色默认请求 5 条世界书，最低要求 4 条，并支持常驻规则、分类和 AND/OR/NOT。

## Qwen3 语义记忆

- Android 支持按需下载或导入 `Qwen3-Embedding-0.6B-ONNX` INT8 模型，约 597 MiB，
  模型不进入 APK。
- 使用 ONNX Runtime 1.28、Qwen byte-level BPE、512 token 上限、query instruction、
  last-token pooling、L2 normalization 和 1024 维向量。
- Room schema 22 新增 `memory_embeddings`，向量按角色、模型版本、记忆 ID 和内容哈希
  绑定，并随记忆修改、分支切换和删除同步更新。
- 查询时只对当前记忆候选做精确 cosine 打分，再与 FTS4、词法、来源和时间信号融合。
- 模型缺失、加载失败或索引为空时自动回退到 FTS4，不阻塞聊天。
- 真机自检结果：1024 维，相关语义 `0.462`，无关语义 `0.353`，三次推理约 `3.4 s`。

## Android 版本

- 完整版：`com.shiyu.chat`，`0.21.0 / versionCode 65`
- 云端版：`com.shiyu.chat.cloud`，`0.21.0-cloud / versionCode 65`
- 完整版保留 8 个初始角色、MNN、Local Dream/QNN、ASR 与 VITS。
- 云端版移除宋知意、裴既明及其对话派生数据，保留 6 个初始角色，不包含 MNN、
  Local Dream/QNN、ASR 或 TTS 运行库。
- 两个包使用同一稳定调试签名，可同时安装；覆盖安装不会清除用户对话。

## 验证

- Android 完整版 JVM 测试：137 项通过。
- Android 云端版 JVM 测试：137 项通过。
- `lintFullDebug`、`lintCloudDebug`、两个 APK 构建通过。
- 两个 APK 均只包含一份 arm64 `libonnxruntime.so` 与
  `libonnxruntime4j_jni.so`。
- 已在 OnePlus PKG110 真机使用 `adb install -r -d` 覆盖安装两个版本，版本号与
  用户数据均保留。

## 发布文件

- 完整版：
  `Shiyu-0.21.0-Android-arm64-v8a.apk`
  - 大小：`311,662,979` bytes
  - SHA-256：`9CB6E785D5E5C2E4BBB2BC089952448EAF6E10E6C59E45E08BA9DECD553D6DB5`
- 云端版：
  `Shiyu-0.21.0-cloud-Android-arm64-v8a.apk`
  - 大小：`43,387,492` bytes
  - SHA-256：`F01EC4F0DB52BBA3347E3E0ACD3C8E655CC3915A9EA7739C48D52BBAB2452AD5`

## 已知限制

- 手机端首次加载 Qwen embedding 会读取约 597 MiB 权重并占用较高内存，低内存设备
  可能回退到 FTS4。
- 摘要和记忆提取仍可能出现漏记、错误归纳或时间混淆；用户可以查看、更正、标为
  过时、忽略或删除记忆。
- 本轮验证覆盖自动测试、两个构建产物和一次真机模型自检，不等同于大规模长对话质量
  评测。
