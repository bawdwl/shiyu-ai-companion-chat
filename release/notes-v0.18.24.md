Android 0.18.24 修复了使用 DeepSeek 官方 API 创建新角色失败的问题。

- 创建角色现在使用设置页当前选择的云端 Provider，不再强制走 OpenCode Go。
- DeepSeek 官方模型更新为 `deepseek-chat`。
- 移除仅适用于 OpenCode 的 `thinking` 请求字段，避免 DeepSeek 官方 API
  拒绝请求。
- OpenCode Go 仍保留原有 `deepseek-v4.1-flash` 与 `thinking` 调用路径。
- 宋知意和裴既明的固定训练 Prompt 未作修改。
- 增加 Provider 路由和请求结构的 PC、Android 回归测试。

验证：308 项 Python 测试、94 项 Android JVM 测试、40 项前端状态测试、
React 生产构建、`assembleDebug` 和 `lintDebug` 通过。本轮尚未执行真机
覆盖安装。
