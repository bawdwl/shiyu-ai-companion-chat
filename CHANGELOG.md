# Changelog

## 2026-11-24 — `0.22.6` PC、Windows 与 Android 统一稳定版

### 聊天基线

- Android、PC 和 Windows 统一回到 Android `0.19.2` 的角色生成、聊天 Prompt、
  场景、历史窗口和世界书命中行为；
- 移除会影响基础表达的 0.22 实验 Prompt、自动 Prompt 缩容和固定滚动摘要注入；
- 恢复角色互动模式和 0.19.2 的动作、追问与对话节奏。

### 长期记忆

- 保留结构化长期记忆、来源证据、生命周期和用户修正；
- Android 保留 FTS4 + 可选 Qwen3-Embedding；
- PC 保留 FTS5 和可选 BGE；
- Android 增加语义检索检测，检查模型、索引和抽样语义分数；
- 事件状态继续区分未完成、已完成和已取消。

### Android

- 完整版和云端版统一为 `0.22.6 / versionCode 72`；
- 语义记忆设置独立成页，并提供启用开关；
- 恢复角色档案中的世界书展示和高级 JSON 编辑；
- 聊天消息操作文字和时间改为黑色；
- 移除聊天菜单中的重复“删除角色”；
- Qwen embedding 下载开始后持续显示进度；
- Room schema 22。

### PC 与 Windows

- 保留新版 React 工作台；
- 同步 0.19.2 聊天行为和角色生成基线；
- 保留 SQLite FTS5、长期记忆、备份、分支、回复版本和 Prompt Inspector；
- Windows 构建版本更新为 `0.22.6`。

### 验证

- Python：`319` 项通过；
- 前端状态：`40` 项通过；
- React 生产构建：通过；
- Android 完整版/云端版 JVM：各 `137` 项通过；
- Android 两套 lint：通过；
- Android 完整版/云端版 APK：通过；
- Windows PyInstaller：通过。
