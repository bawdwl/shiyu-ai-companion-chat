# RAG Retrieval Evaluation

`retrieval_v1.json` is a synthetic, character-scoped structured-memory fixture.
It contains 60 memories and 68 queries, including:

- lexical matches;
- paraphrases;
- similar distractor facts;
- no-answer questions.

Run:

```powershell
python scripts/evaluate_rag_retrieval.py `
  --embedding-model "outputs/models/bge-small-zh-v1.5"
```

The tracked JSON is the source of truth. The script reads it by default and only
regenerates it when `--refresh-fixture` is explicitly supplied.

The script reports Recall@1/3/5/10, MRR@10, nDCG@10, Precision@5,
no-answer false positives and warm-query latency.

This fixture measures retrieval only. It does not measure final LLM answer
quality, factuality or human preference. The measured implementation is the
PC/server FTS5+BGE path. Android uses a separate FTS4+Qwen embedding stack and
has not yet been evaluated with the same standard IR fixture.
