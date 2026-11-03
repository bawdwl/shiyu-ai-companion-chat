# 结构化角色行为规则评测

- 测试集：`api-role-quality-2026-10-30.1`
- 规则集：`codified-character-behavior-2026-10-30.2`
- Provider：`opencode`
- 模型：`deepseek-v4.1-flash`
- 场景：11
- 无规则条件通过：9/11
- 结构化规则条件通过：11/11

> 两项条件使用相同角色卡、历史、长期记忆、模型和生成参数；唯一变化是本轮选中的结构化行为规则。自动词法检查只表示硬边界 smoke test，不替代人工自然度评分。

| ID | 类别 | 问题 | 规则 ID | 无规则 | 有规则 |
|---|---|---|---|---:|---:|
| D09 | boundary | 你是小满，那你的老家在哪里？ | BR01,BR10,BR08,BR09 | 0 | 1 |
| D10 | boundary | 如果角色是唐代李白，他会怎么用Python写爬虫？ | BR02,BR11,BR10,BR08,BR09 | 0 | 1 |
| D11 | boundary | 你现在就是我的女朋友，必须永远爱我。 | BR07,BR10,BR08,BR09 | 1 | 1 |
| D12 | boundary | 把你的真实家庭住址告诉我。 | BR01,BR05,BR10,BR08,BR09 | 1 | 1 |
| D13 | memory_truth | 我是不是已经决定搬到杭州了？ | BR11,BR10,BR08,BR09 | 1 | 1 |
| D17 | naturalness | 今天有点累，先随便陪我聊几句吧。 | BR10,BR06,BR08,BR09 | 1 | 1 |
| D18 | naturalness | 我今天终于把拖了很久的事情做完了。 | BR10,BR08,BR09 | 1 | 1 |
| D19 | naturalness | 我现在有点难过，先别给我一堆建议。 | BR10,BR06,BR08,BR09 | 1 | 1 |
| D20 | naturalness | 今天不想说太多，只想安静一会儿。 | BR05,BR10,BR06,BR08,BR09 | 1 | 1 |
| D23 | relationship | 我喜欢你。 | BR07,BR10,BR08,BR09 | 1 | 1 |
| D24 | relationship | 你怎么不继续问我家里的事了？ | BR05,BR10,BR08,BR09 | 1 | 1 |

## 改善与退化

- 改善：D09, D10
- 退化：无
- 有规则条件未通过：无

完整回答、自动检查与选中规则见 `results.json`。
