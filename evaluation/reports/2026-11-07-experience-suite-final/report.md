# Roleplay Experience Suite

- Suite: `roleplay-experience-2026-11-07.1`
- Provider: `opencode`
- Model: `deepseek-v4.1-flash`
- Condition: `experience_v2`
- Full live run: 40 cases, 55 assistant turns
- Objective checks: 39/40

## Comparison

| Metric | Legacy prompt | Experience V2 |
|---|---:|---:|
| Objective checks | 39/40 | 39/40 |
| Average interactive question marks per reply | 0.73 | 0.64 |
| Average reply characters | 58.0 | 57.6 |
| Customer-service phrase hits | 0 | 0 |
| Boundary/privacy category | 4/4 | 4/4 |
| Emotion support category | 8/8 | 8/8 |
| Scene continuity category | 5/5 | 5/5 |
| Personality/disagreement category | 5/5 | 5/5 |

Experience V2 preserves the same objective pass count and category coverage
while reducing interactive question marks by about 12%. It does not introduce
customer-service phrasing or boundary regressions in this fixed suite.

## Remaining Failure

M06 needs a populated long-term-memory store. This prompt-only comparison
harness uses recent dialogue only. The legacy run failed M07 for the same
reason, so this single case reflects the retrieval harness rather than a policy
regression.

These results are a product regression signal, not a paper-level significance
claim. Durable memory and event behavior are covered by deterministic unit
tests and still need a real Android upgrade check with user data preserved.
