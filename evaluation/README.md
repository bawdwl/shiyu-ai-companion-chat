# 角色质量评测

本目录用于固定、可重复的 API 角色质量对照。

## 文件

- `role_quality_cases.json`：24 个对话场景和 12 个记忆语义案例。
- `full_character_behavior_cases.json` 与 `characters/full_character_fixture.json`：完整虚构角色、12 个身份/记忆/边界/自然度场景；现为主评测夹具。
- `reports/2026-10-30-api-role-quality/report.md`：首轮自动评分摘要。
- `reports/2026-10-30-api-role-quality/results.json`：逐题回答、检查项和记忆审核结果。
- `reports/2026-10-30-api-role-quality/live-memory-pipeline.json`：真实 API 隔离库自动记忆结果。
- `reports/2026-10-30-behavior-rules/report.md`：结构化角色行为规则前后对照。
- `reports/2026-10-30-behavior-rules/results.json`：11 个固定场景的完整回答、选中规则和检查项。
- `reports/2026-10-30-full-character-behavior/report.md`：完整角色卡下的无规则/规则版真实 API 对照。
- `reports/2026-10-30-full-character-behavior/results.json`：完整角色逐题回答、重试、规则归因和自动评分。
- `outputs/behavior-blind-review-20261030/评分页面.html`：作者使用的 11 对匿名 A/B 评审页。
- `natural-memory/`：18 个案例、448 轮消息和 90 个探针的自然记忆回放协议与脱敏数据生成工具。
- `reports/2026-10-31-natural-memory-bge/report.md`：冻结回放最终结果及 BGE/词法回退边界。
- `reports/2026-10-31-live-memory-pipeline/report.md`：一条合成案例的真实提取、审核、入库、检索和回答链路。
- `state_ablation_cases.json` 与 `characters/full_character_fixture.json`：12 个内部状态开关对照场景。
- `reports/2026-10-31-internal-state-ablation/report.md`：状态 A/B 的自动结构检查和作者盲评汇总；有状态总体胜出 6:2、平局 4。

## 运行

```powershell
python scripts/evaluate_api_role_quality.py --live
python scripts/evaluate_behavior_rules.py --live
python scripts/evaluate_behavior_rules.py --suite evaluation/full_character_behavior_cases.json --live
python scripts/run_natural_memory_live.py --live --case-id C02
python scripts/run_state_ablation.py --live
```

行为规则脚本只切换本轮规则区块，保持角色卡、历史、长期记忆、模型和生成参数不变。也可以只复测指定场景：

```powershell
python scripts/evaluate_behavior_rules.py --live --case-id D09 --case-id D10
```

不调用 API、只检查测试集和离线策略：

```powershell
python scripts/evaluate_api_role_quality.py
```

对已有 `results.json` 重新评分而不重新调用模型：

```powershell
python scripts/evaluate_api_role_quality.py --rescore-from <results.json> --output-root <new-dir>
```

固定题集已经参与首轮结果。后续改动应新增独立留出集，不能反复修改这 24 题来追分。

自然记忆冻结集也只用于回归与阶段归因。最终回放的结构化事实、摘要、混合检索和混合状态四种条件均为 90/90，其中只有一份现有聊天经过本地脱敏，另外 17 份是明确标注的合成补充案例。90/90 不代表真实模型端到端准确率。内部状态匿名评审已经完成并揭盲：有状态总体胜出 6:2、平局 4，成为当前 PC 默认；单个作者结果仍不作为总体显著性证据。

内置小满和言川字段不完整，已经退出角色质量主评测。主评测应使用 `full_character_fixture.json` 这类字段完整、知识边界明确、且专门为测试冻结的角色。

行为规则盲评页不会显示模型、规则或自动评分。完成后导出 JSON，并保留同目录的 `organizer-key.json` 供最终揭盲。
