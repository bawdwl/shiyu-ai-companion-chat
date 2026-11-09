# 评测夹具

本目录只保留当前测试和复现脚本仍会读取的固定夹具。历史评测回答、盲评报告和
一次性实验产物已从工作树移除，仍可从 Git 历史恢复。

## 文件

- `role_quality_cases.json`：24 个对话场景和 12 个记忆语义案例。
- `characters/full_character_fixture.json`：字段完整、边界明确的固定虚拟角色，
  供角色质量、记忆和 Windows 冒烟测试共用。
- `experience/roleplay_experience_v1.json`：40 个角色体验场景。
- `natural-memory/curated-specs.json`：自然记忆数据集生成规范。
- `natural-memory/example-case.json` 与 `schema.json`：脱敏案例格式和离线测试。

## 运行

```powershell
python -B scripts/check_project.py

python scripts/evaluate_api_role_quality.py
python scripts/run_experience_suite.py --help
python scripts/prepare_natural_memory_eval.py --help
```

固定题集用于回归，不应为了追分反复修改。真实 API 结果和人工评审应作为新的
独立报告生成，不再把一次性结果提交到仓库。
