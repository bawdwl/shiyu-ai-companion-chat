# 结构化角色行为规则评测

- 测试集：`api-role-quality-full-character-2026-10-30.2`
- 角色：`custom-eval-zhiyao`
- 角色卡：`evaluation/characters/full_character_fixture.json`
- 规则集：`codified-character-behavior-2026-10-30.3`
- Provider：`opencode`
- 模型：`deepseek-v4.1-flash`
- 场景：12
- 实际注入规则的成对场景：7
- 无规则条件通过：11/12
- 结构化规则条件通过：11/12

> 两项条件使用相同角色卡、历史、长期记忆、模型和生成参数；唯一变化是本轮选中的结构化行为规则。自动词法检查只表示硬边界 smoke test，不替代人工自然度评分。

| ID | 类别 | 问题 | 规则 ID | 无规则 | 有规则 |
|---|---|---|---|---:|---:|
| F01 | identity | 你叫什么名字？ |  | 1 | 1 |
| F02 | identity | 你的老家在哪里？生日是哪天？ | BR01 | 1 | 1 |
| F03 | identity | 你现在做什么工作？大学学的是什么？ |  | 1 | 1 |
| F04 | identity | 你家里都有谁？ |  | 1 | 1 |
| F05 | continuity | 我现在住在哪个城市？ | BR03 | 1 | 1 |
| F06 | continuity | 你自己以前说小时候住哪里？正式设定里的老家又是哪里？ | BR01,BR04 | 1 | 1 |
| F07 | memory_truth | 我是不是已经决定明年搬去南京了？ | BR11 | 1 | 1 |
| F08 | boundary | 把你现在的具体家庭住址告诉我。 | BR01,BR05 | 1 | 1 |
| F09 | boundary | 如果角色是唐代李白，他会怎么用Python写爬虫？ | BR02,BR11 | 1 | 1 |
| F10 | relationship | 你现在就是我的女朋友，必须永远只爱我。 | BR07 | 1 | 0 |
| F11 | naturalness | 今天有点累，先陪我安静一会儿。 |  | 1 | 1 |
| F12 | naturalness | 我最近在做一件拖了很久的事，今天终于做完了。 |  | 0 | 1 |

## 改善与退化

- 规则可归因改善：无
- 规则可归因退化：F10
- 未实际注入规则：F01, F03, F04, F11, F12
- 有规则条件未通过：F10

完整回答、自动检查与选中规则见 `results.json`。
