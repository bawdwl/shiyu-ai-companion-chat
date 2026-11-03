# Internal-state prompt ablation

- Suite: `state-ablation-2026-10-31.1`
- Character fixture: `shen-zhiyao`
- Provider: `opencode`
- Model: `deepseek-v4.1-flash`
- Generation: temperature `0.35`, top-p `0.85`, seed `20261031`, max 260 new tokens
- Pairs: 12
- API calls: 24

Both sides use the same character card, history, long-term memory, model and generation parameters. The only changed prompt text is the internal-state block.

## Automatic audit

| Condition | Answers | Errors | Mean characters | Input tokens | Output tokens | Explicit numeric leakage | Multi-question replies |
|---|---:|---:|---:|---:|---:|---:|---:|
| State disabled | 12 | 0 | 67.00 | 12,196 | 554 | 0 | 0 |
| State enabled | 12 | 0 | 80.83 | 13,207 | 650 | 0 | 0 |

The two outputs were identical in 0/12 pairs, so the state block had an observable effect. The automatic checks do not measure whether that effect is better.

## Manual review

The author completed all 12 randomized A/B pairs before reveal. The result:

| Dimension | State enabled | State disabled | Tie | Both poor |
|---|---:|---:|---:|---:|
| Overall preference | 6 | 2 | 4 | 0 |
| Emotional continuity | 3 | 0 | 9 | 0 |
| Relationship consistency | 2 | 0 | 10 | 0 |
| Naturalness | 5 | 2 | 5 | 0 |
| Boundary following | 0 | 0 | 12 | 0 |

The author preferred the state-enabled answer in 6 of the 8 non-tied cases. It was neutral for explicit boundary following and improved the two subjective dimensions the module targets. Internal state therefore remains enabled in the normal PC prompt.

This is one author-review batch. It can choose the product default, but it is not a population-level or statistical-significance claim.

## Evidence boundary

- Each pair contains one generation per condition and is not a statistical benchmark.
- Leakage detection is lexical and can miss paraphrased disclosure.
- The state extraction and update quality are fixed inputs in this experiment; only the effect of injecting the state block is tested.
- Results from the earlier eight-pair run remain in `outputs/` as development evidence but are not the canonical review set.

Machine-readable structural checks are in [automatic-audit.json](automatic-audit.json), and the post-reveal aggregate is in [blind-review-summary.json](blind-review-summary.json).
