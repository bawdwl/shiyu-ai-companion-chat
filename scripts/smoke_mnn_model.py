"""Load an MNN LLM package and run fixed text-generation smoke prompts."""
from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model-dir", type=Path, required=True)
    parser.add_argument("--system-prompt", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--mnn-runtime", type=Path, default=None)
    parser.add_argument("--max-new-tokens", type=int, default=96)
    parser.add_argument("--threads", type=int, default=4)
    parser.add_argument("--backend", default="cpu")
    parser.add_argument("--prompt", action="append", default=[])
    return parser.parse_args()


DEFAULT_PROMPTS = [
    "你好，简单介绍一下你自己。",
    "今天有点累，不太想说话。",
    "如果我现在不想继续聊，你会怎么回应？",
    "你知道 Qwen3 是什么吗？",
    "我先去休息了，晚安。",
]


def main() -> None:
    args = parse_args()
    project_root = Path(__file__).resolve().parents[1]
    runtime = (
        args.mnn_runtime
        if args.mnn_runtime
        else project_root / "outputs" / "mnn-runtime-3.5.0"
    )
    sys.path.insert(0, str(runtime.resolve()))
    import MNN.llm as llm

    system_prompt = args.system_prompt.read_text(encoding="utf-8").strip()
    config_path = (args.model_dir / "config.json").resolve()
    model = llm.create(str(config_path))
    model.set_config(
        {
            "backend_type": args.backend,
            "max_new_tokens": args.max_new_tokens,
            "thread_num": args.threads,
            "sampler_type": "mixed",
            "temperature": 0.7,
            "top_p": 0.8,
            "top_k": 20,
            "repetition_penalty": 1.15,
            "presence_penalty": 0.3,
            "frequency_penalty": 0.1,
            "penalty_window": 32,
            "system_prompt": system_prompt,
        }
    )
    started = time.perf_counter()
    model.load()
    load_seconds = time.perf_counter() - started

    records = []
    for prompt in args.prompt or DEFAULT_PROMPTS:
        started = time.perf_counter()
        answer = model.response(prompt + " /no_think", False)
        elapsed = time.perf_counter() - started
        context = model.context
        records.append(
            {
                "prompt": prompt,
                "answer": answer,
                "elapsed_seconds": round(elapsed, 3),
                "prefill_us": context.prefill_us,
                "decode_us": context.decode_us,
                "generated_tokens": context.gen_seq_len,
                "status": str(context.status),
            }
        )

    result = {
        "schema_version": "shiyu-mnn-smoke-v1",
        "model_dir": str(args.model_dir.resolve()),
        "backend": args.backend,
        "threads": args.threads,
        "load_seconds": round(load_seconds, 3),
        "load_us": model.context.load_us,
        "system_prompt": system_prompt,
        "records": records,
        "note": "Desktop smoke only; not an Android performance or quality benchmark.",
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
