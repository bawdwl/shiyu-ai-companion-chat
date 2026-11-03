# Natural-memory offline baseline

Date: 2026-10-31

## Source availability

The production chat database was inspected read-only:

- 150 sessions in total.
- 6 role conversations.
- 238 messages.
- Only one role conversation had at least 24 messages (40 messages).

Because one conversation cannot represent a memory benchmark, the suite keeps
that conversation as one locally redacted real-derived case and adds 17 clearly
labelled curated scenarios.

## Frozen suite

- Cases: 18.
- Messages: 448.
- Probes: 90.
- Redacted real-chat cases: 1.
- Curated synthetic cases: 17.
- Probes per case: 5.

The private dataset and its full redaction report remain under:

`outputs/natural-memory-private/natural-memory-eval-v5/`

Only the hash-only manifest is published under
`evaluation/reports/2026-10-30-natural-memory-offline/`.

## Golden replay result

The isolated replay seeds the expected facts/events directly. It therefore
tests validation/storage/retrieval integration, not model extraction.

| Ablation | Passed | Failed | Store failures | Retrieval failures |
|---|---:|---:|---:|---:|
| recent_only | 23 | 67 | 0 | 67 |
| facts_only | 90 | 0 | 0 | 0 |
| summary_facts | 90 | 0 | 0 | 0 |
| hybrid_retrieval | 90 | 0 | 0 | 0 |
| hybrid_state | 90 | 0 | 0 | 0 |

`facts_only` category result:

| Category | Passed | Total |
|---|---:|---:|
| abstention | 17 | 17 |
| role isolation | 18 | 18 |
| knowledge update | 17 | 17 |
| temporal reasoning | 17 | 17 |
| information extraction | 21 | 21 |

## Findings

- Golden event insertion succeeds without storage failures in the final run.
- Supplying structured facts improves memory-context checks from 23/90 to
  90/90 after natural-query aliases, historical cues and anaphoric expansion
  were made deterministic.
- The earlier 63/90 run remains a useful regression baseline; its failures
  identified the natural wording gaps that were then repaired.
- With the corrected deterministic retrieval fallback, `facts_only`,
  `summary_facts`, `hybrid_retrieval` and `hybrid_state` all reach 90/90.
- The frozen suite therefore no longer demonstrates an independent BGE gain.
  BGE is loaded and remains available, but a new holdout would be required to
  measure its increment over the deterministic fallback.

These results must not be described as end-to-end chat quality:

- Extraction was golden-seeded.
- Semantic review was not exercised.
- The answer model was not run.
- UI behavior was not exercised.
- 17 of 18 cases are curated, not representative user traffic.

## Reproduce

```powershell
python scripts/prepare_natural_memory_eval.py inspect
python scripts/prepare_natural_memory_eval.py prepare `
  --output-dir outputs/natural-memory-private/natural-memory-eval-v5
python scripts/prepare_natural_memory_eval.py replay `
  --dataset outputs/natural-memory-private/natural-memory-eval-v5/dataset.json `
  --output-dir outputs/natural-memory-private/replay-golden-final-v3
```

To exercise the local vector path:

```powershell
python scripts/prepare_natural_memory_eval.py replay `
  --dataset outputs/natural-memory-private/natural-memory-eval-v5/dataset.json `
  --output-dir outputs/natural-memory-private/replay-bge-v2 `
  --embedding-model outputs/models/bge-small-zh-v1.5
```

The report is intentionally written in English so its identifiers are stable;
the implementation and command help remain available in the Chinese project
documentation.
