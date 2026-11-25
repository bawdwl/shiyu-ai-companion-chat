# 裴既明 Qwen3-1.7B LoRA 评测摘要

详细报告：
[裴既明 Base 与 epoch-1 评测](https://github.com/bawdwl/qwen3-1.7b-lora/blob/main/docs/PEI_JIMING_BASE_VS_EPOCH1.md)

## 对比范围

- Base：`Qwen/Qwen3-1.7B`
- LoRA：`weights/pei-jiming-epoch-1`
- 同一 system prompt、生成参数和固定题集

## 当前自动诊断

| 指标 | Base | LoRA epoch-1 |
|---|---:|---:|
| 单轮完全重复 | 0 | 0 |
| 助手腔泄漏 | 0% | 0% |
| 极短回复率 | 0% | 0% |
| 单轮重复短语率 | 0% | 8.3% |
| 多轮近重复率 | 0% | 0% |
| 中位回复字符数 | 72.5 | 17.0 |
| 中位生成时间 | 5.33 s | 2.65 s |

## 历史封存 LLM 评审

| 指标 | Base | LoRA epoch-1 |
|---|---:|---:|
| Persona | 1.182 | 1.545 |
| Context | 1.091 | 1.364 |
| Naturalness | 1.455 | 2.182 |
| Overall | 1.182 | 1.545 |
| Hard failures | 2 | 1 |

11 个封存场景中 LoRA 胜 6、Base 胜 5。

结论：LoRA 的角色感、上下文和自然度改善，但存在明显短回答退化和一次重复短语问题。
结果来自自动诊断和单一 LLM 裁判，不是多人人工盲评，也没有统计显著性结论。
