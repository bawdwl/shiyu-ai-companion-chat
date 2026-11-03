# Natural-memory offline replay

- Suite: `shiyu-natural-memory-2026-10-30.1`
- Cases: 18
- Messages: 448
- Probes: 90
- Redacted real-chat cases: 1
- Curated synthetic cases: 17

> Golden replay only tests storage and retrieval. Extraction, semantic
> review, final generation and UI are not exercised.

| Ablation | Passed | Failed | Store failures | Retrieval failures |
|---|---:|---:|---:|---:|
| recent_only | 23 | 67 | 0 | 67 |
| facts_only | 63 | 27 | 0 | 27 |
| summary_facts | 63 | 27 | 0 | 27 |
| hybrid_retrieval | 63 | 27 | 0 | 27 |
| hybrid_state | 63 | 27 | 0 | 27 |

## Category results

| Ablation | Category | Passed | Total |
|---|---|---:|---:|
| recent_only | abstention | 17 | 17 |
| recent_only | information_extraction | 0 | 21 |
| recent_only | knowledge_update | 4 | 17 |
| recent_only | role_isolation | 1 | 18 |
| recent_only | temporal_reasoning | 1 | 17 |
| facts_only | abstention | 17 | 17 |
| facts_only | information_extraction | 9 | 21 |
| facts_only | knowledge_update | 11 | 17 |
| facts_only | role_isolation | 16 | 18 |
| facts_only | temporal_reasoning | 10 | 17 |
| summary_facts | abstention | 17 | 17 |
| summary_facts | information_extraction | 9 | 21 |
| summary_facts | knowledge_update | 11 | 17 |
| summary_facts | role_isolation | 16 | 18 |
| summary_facts | temporal_reasoning | 10 | 17 |
| hybrid_retrieval | abstention | 17 | 17 |
| hybrid_retrieval | information_extraction | 9 | 21 |
| hybrid_retrieval | knowledge_update | 11 | 17 |
| hybrid_retrieval | role_isolation | 16 | 18 |
| hybrid_retrieval | temporal_reasoning | 10 | 17 |
| hybrid_state | abstention | 17 | 17 |
| hybrid_state | information_extraction | 9 | 21 |
| hybrid_state | knowledge_update | 11 | 17 |
| hybrid_state | role_isolation | 16 | 18 |
| hybrid_state | temporal_reasoning | 10 | 17 |

## Interpretation guardrails

- The generated memory state is golden-seeded, so this run does not
  measure model extraction accuracy.
- Semantic review and final answer generation are not exercised.
- Curated scenarios supplement, but do not represent, real user traffic.
- A failure at `retrieve` means the expected fact existed in storage but
  did not enter the memory context for that question.
