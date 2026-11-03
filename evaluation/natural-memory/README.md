# Natural memory evaluation

This directory defines a private, redacted long-conversation memory evaluation
for the PC application. Only the schema, generator and non-private fixtures are
tracked. Generated material that contains redacted personal conversations is
written under `outputs/natural-memory-private/`, which is ignored by Git.

The suite uses four layers:

1. Long conversations replayed in chronological order.
2. Expected memory operations after specific turns.
3. Probe questions at intermediate and final checkpoints.
4. Stage-level failures: extract, validate, semantic_review, store, retrieve,
   generate and UI.

## Prepare

```powershell
python scripts/prepare_natural_memory_eval.py inspect
python scripts/prepare_natural_memory_eval.py prepare
python scripts/prepare_natural_memory_eval.py validate --dataset outputs/natural-memory-private/natural-memory-eval-v1/dataset.json
```

The preparation command opens the production databases read-only. It never
prints message bodies and never writes to the production databases.

The current source audit found one 40-message role conversation. That single
conversation is redacted and supplemented with 15 clearly labelled curated
scenarios. No test result may describe the curated scenarios as representative
real-user chat data.

## Replay

```powershell
python scripts/prepare_natural_memory_eval.py replay \
  --dataset outputs/natural-memory-private/natural-memory-eval-v1/dataset.json \
  --output-dir outputs/natural-memory-private/replay-golden
```

Enable the real local BGE model for the hybrid ablations:

```powershell
python scripts/prepare_natural_memory_eval.py replay \
  --dataset outputs/natural-memory-private/natural-memory-eval-v5/dataset.json \
  --output-dir outputs/natural-memory-private/replay-bge \
  --embedding-model outputs/models/bge-small-zh-v1.5
```

The offline golden replay tests validation, storage and retrieval semantics
against an isolated temporary SQLite database. It deliberately marks extraction,
semantic review, final generation and UI as `not_exercised`; it must not be
reported as a full end-to-end result.

Available ablations:

- `recent_only`
- `facts_only`
- `summary_facts`
- `hybrid_retrieval`
- `hybrid_state`

Use the same frozen dataset and generated memory state for comparisons. A live
run must record the actual model/provider, prompt hash, selected memory IDs,
latency and token usage.
