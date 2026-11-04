"""Compare next-token logits from a PEFT adapter and its merged model."""
from __future__ import annotations

import argparse
import gc
import json
from pathlib import Path

import torch
from peft import PeftModel
from transformers import AutoModelForCausalLM, AutoTokenizer


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-model", type=Path, required=True)
    parser.add_argument("--adapter", type=Path, required=True)
    parser.add_argument("--merged-model", type=Path, required=True)
    parser.add_argument("--cases", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--limit", type=int, default=3)
    return parser.parse_args()


def read_cases(path: Path, limit: int) -> list[dict]:
    rows = [
        json.loads(line)
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    ][:limit]
    if not rows:
        raise ValueError(f"{path}: no cases")
    return rows


def case_messages(case: dict) -> list[dict[str, str]]:
    messages = case.get("messages")
    if not isinstance(messages, list) or not messages:
        raise ValueError(f"{case.get('id', '<unknown>')}: missing messages")
    if messages[-1].get("role") == "assistant":
        messages = messages[:-1]
    system_prompt = case.get("system_prompt")
    if system_prompt:
        messages = [{"role": "system", "content": system_prompt}, *messages]
    return messages


def tokenize_case(tokenizer, messages: list[dict[str, str]]):
    kwargs = {
        "tokenize": True,
        "add_generation_prompt": True,
        "return_tensors": "pt",
        "return_dict": True,
    }
    try:
        return tokenizer.apply_chat_template(
            messages,
            enable_thinking=False,
            **kwargs,
        )
    except TypeError:
        return tokenizer.apply_chat_template(messages, **kwargs)


def next_token_logits(model, inputs):
    with torch.inference_mode():
        outputs = model(
            input_ids=inputs["input_ids"].to("cuda:0"),
            attention_mask=inputs["attention_mask"].to("cuda:0"),
            use_cache=False,
        )
    return outputs.logits[0, -1].float().cpu()


def load_model(path: Path):
    return AutoModelForCausalLM.from_pretrained(
        path,
        local_files_only=True,
        trust_remote_code=False,
        dtype=torch.bfloat16,
        device_map={"": 0},
    ).eval()


def main() -> None:
    args = parse_args()
    cases = read_cases(args.cases, args.limit)
    tokenizer = AutoTokenizer.from_pretrained(
        args.base_model,
        local_files_only=True,
        trust_remote_code=False,
    )
    inputs = [
        tokenize_case(tokenizer, case_messages(case))
        for case in cases
    ]

    base = load_model(args.base_model)
    adapter_model = PeftModel.from_pretrained(
        base,
        args.adapter,
        is_trainable=False,
    ).eval()
    adapter_logits = [next_token_logits(adapter_model, item) for item in inputs]
    del adapter_model
    del base
    gc.collect()
    torch.cuda.empty_cache()

    merged_model = load_model(args.merged_model)
    merged_logits = [next_token_logits(merged_model, item) for item in inputs]

    records = []
    for case, adapter_logit, merged_logit in zip(
        cases,
        adapter_logits,
        merged_logits,
    ):
        adapter_top1 = int(torch.argmax(adapter_logit))
        merged_top1 = int(torch.argmax(merged_logit))
        adapter_top5 = torch.topk(adapter_logit, 5).indices.tolist()
        merged_top5 = torch.topk(merged_logit, 5).indices.tolist()
        difference = (adapter_logit - merged_logit).abs()
        records.append(
            {
                "case_id": case.get("id"),
                "adapter_top1": adapter_top1,
                "merged_top1": merged_top1,
                "top1_equal": adapter_top1 == merged_top1,
                "top5_equal": adapter_top5 == merged_top5,
                "max_abs_difference": round(float(difference.max()), 6),
                "mean_abs_difference": round(float(difference.mean()), 6),
            }
        )
    result = {
        "schema_version": "lora-merge-equivalence-v1",
        "cases": records,
        "top1_equal": sum(row["top1_equal"] for row in records),
        "top5_equal": sum(row["top5_equal"] for row in records),
        "max_abs_difference": max(row["max_abs_difference"] for row in records),
        "mean_abs_difference": round(
            sum(row["mean_abs_difference"] for row in records) / len(records),
            6,
        ),
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
