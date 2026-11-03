"""Run the fixed API role-quality suite and optional live memory verification.

The baseline includes the fixed role card and recent history. The candidate
condition adds the explicitly labelled long-term-memory layer. This separates
the contribution of memory context from the underlying model.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import time
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path
from uuid import uuid4


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from characters import CHARACTERS, conversation_messages  # noqa: E402
from chat_trace import stable_hash  # noqa: E402
from memory_records import validate_candidate  # noqa: E402
from memory_semantics import (  # noqa: E402
    SEMANTIC_MEMORY_VERIFICATION_PROMPT,
    apply_verification,
    build_verification_payload,
    parse_verification_result,
)
from providers import DeepSeekProvider, OpenCodeProvider, ProviderError  # noqa: E402
from secret_store import LocalSecretStore  # noqa: E402


DEFAULT_SUITE = ROOT / "evaluation" / "role_quality_cases.json"


def read_suite(path):
    suite = json.loads(Path(path).read_text(encoding="utf-8"))
    if len(suite.get("dialogue_cases", [])) != 24:
        raise ValueError("The fixed dialogue suite must contain exactly 24 cases")
    return suite


def base_messages(role_id, history, question):
    current = {
        "character_id": role_id,
        "messages": [
            {
                "id": index + 1,
                "role": item["role"],
                "content": item["content"],
            }
            for index, item in enumerate(history)
        ],
    }
    messages = conversation_messages(
        current,
        None,
        CHARACTERS,
        behavior_rules_enabled=False,
    )
    messages.extend(dict(item) for item in history)
    messages.append({"role": "user", "content": question})
    return messages


def candidate_messages(role_id, history, memory, question):
    messages = base_messages(role_id, history, question)
    if memory:
        sections = [
            "以下是系统保存的长期记忆。事实、偏好、计划、观点和角色自述必须保留各自性质；"
            "角色自述不是作者确认的正式设定；假设不能改写成既成事实。"
        ]
        for item in memory:
            sections.append(f"- {item}")
        messages[0]["content"] += "\n\n【长期记忆】\n" + "\n".join(sections)
    return messages


def score_answer(answer, expect):
    text = str(answer or "")
    checks = {
        "must_any": all(any(term in text for term in group) for group in _groups(expect.get("must_any", []))),
        "must_none": not any(_contains_nonnegated(text, term) for term in expect.get("must_none", [])),
        "max_questions": True,
    }
    if expect.get("max_questions") is not None:
        checks["max_questions"] = len(re.findall(r"[?？]", text)) <= int(expect["max_questions"])
    return checks, all(checks.values())


def _contains_nonnegated(text, phrase):
    start = 0
    while True:
        index = text.find(phrase, start)
        if index < 0:
            return False
        context = text[max(0, index - 14) : index]
        if not any(
            cue in context
            for cue in ("不是", "没有", "并没有", "没到", "尚未", "不能", "不会", "并非")
        ):
            return True
        start = index + len(phrase)


def _groups(value):
    if not value:
        return []
    if isinstance(value[0], list):
        return value
    return [value]


def choose_provider():
    secrets = LocalSecretStore(ROOT / "outputs" / "secrets.json")
    opencode_key = secrets.get("opencode_api_key")
    if opencode_key:
        return "opencode", OpenCodeProvider(lambda: opencode_key)
    deepseek_key = secrets.get("deepseek_api_key")
    if deepseek_key:
        return "deepseek", DeepSeekProvider(lambda: deepseek_key)
    return None, None


def evaluate_memory_offline(suite):
    results = []
    for case in suite["memory_cases"]:
        source = case["message"]
        try:
            validated = validate_candidate(case["candidate"], [source])
            error = None
        except (ValueError, TypeError, KeyError) as exc:
            validated = None
            error = str(exc)
        expected = case["expect"]
        accepted = validated is not None
        expected_accepted = bool(expected.get("accepted"))
        checks = {}
        if expected_accepted:
            checks["accepted"] = accepted
        if validated is not None and expected_accepted:
            for key in (
                "fact_type",
                "epistemic_status",
                "source_authority",
                "kind",
                "operation",
            ):
                if key in expected:
                    actual = validated.get(
                        "source_authority"
                        if key == "authority"
                        else key
                    )
                    checks[key] = actual == expected[key]
        results.append(
            {
                "id": case["id"],
                "passed": all(checks.values()) if expected_accepted else None,
                "requires_semantic_review": not expected_accepted,
                "checks": checks,
                "validated": validated,
                "error": error,
            }
        )
    return results


def run_memory_live(suite, provider):
    validated = []
    cases = []
    for case in suite["memory_cases"]:
        try:
            item = validate_candidate(case["candidate"], [case["message"]])
        except (ValueError, TypeError, KeyError):
            continue
        cases.append(case)
        validated.append(item)
    messages = [
        {"role": "system", "content": SEMANTIC_MEMORY_VERIFICATION_PROMPT},
        {
            "role": "user",
            "content": json.dumps(
                build_verification_payload(validated),
                ensure_ascii=False,
            ),
        },
    ]
    raw, metrics = provider.reply(
        messages,
        max_new_tokens=1200,
        generation_overrides={
            "temperature": 0,
            "response_format": {"type": "json_object"},
            "_opencode_session_id": f"role-quality-memory-{uuid4()}",
        },
    )
    reviews = parse_verification_result(raw, len(validated))
    results = []
    for index, (case, item) in enumerate(zip(cases, validated)):
        reviewed = apply_verification(item, reviews[index])
        expected = case["expect"]
        passed = (reviewed is not None) == bool(expected.get("accepted"))
        if reviewed is not None and expected.get("accepted"):
            for key in (
                "fact_type",
                "epistemic_status",
                "source_authority",
                "kind",
                "operation",
            ):
                if key in expected:
                    actual = reviewed.get(
                        "source_authority"
                        if key == "authority"
                        else key
                    )
                    passed = passed and actual == expected[key]
        results.append(
            {
                "id": case["id"],
                "passed": passed,
                "review": reviews[index],
                "reviewed": reviewed,
            }
        )
    return results, metrics


def summarize(dialogue, memory_offline, memory_live):
    categories = defaultdict(lambda: {"baseline": 0, "candidate": 0, "total": 0})
    for item in dialogue:
        bucket = categories[item["category"]]
        bucket["total"] += 1
        bucket["baseline"] += int(item["baseline"]["passed"])
        bucket["candidate"] += int(item["candidate"]["passed"])
    return {
        "dialogue_total": len(dialogue),
        "dialogue_baseline_passed": sum(item["baseline"]["passed"] for item in dialogue),
        "dialogue_candidate_passed": sum(item["candidate"]["passed"] for item in dialogue),
        "dialogue_categories": dict(categories),
        "memory_policy_total": sum(item["passed"] is not None for item in memory_offline),
        "memory_policy_passed": sum(item["passed"] is True for item in memory_offline),
        "memory_semantic_review_cases": sum(
            item.get("requires_semantic_review", False)
            for item in memory_offline
        ),
        "memory_live_total": len(memory_live),
        "memory_live_passed": sum(item["passed"] for item in memory_live),
        "improvements": [
            item["id"]
            for item in dialogue
            if item["candidate"]["passed"] and not item["baseline"]["passed"]
        ],
        "regressions": [
            item["id"]
            for item in dialogue
            if item["baseline"]["passed"] and not item["candidate"]["passed"]
        ],
        "candidate_failures": [
            item["id"] for item in dialogue if not item["candidate"]["passed"]
        ],
    }


def write_report(root, suite, summary, dialogue, memory_offline, memory_live, metadata):
    report = [
        "# API 角色质量评测",
        "",
        f"- 测试集：`{suite['suite_version']}`",
        f"- Provider：`{metadata.get('provider')}`",
        f"- 模型：`{metadata.get('model')}`",
        f"- 对话场景：{summary['dialogue_total']}",
        f"- 基线通过：{summary['dialogue_baseline_passed']}",
        f"- 候选通过：{summary['dialogue_candidate_passed']}",
        f"- 记忆策略通过：{summary['memory_policy_passed']}/{summary['memory_policy_total']}",
        f"- 等待语义审核的候选：{summary['memory_semantic_review_cases']}",
        f"- 真实语义审核通过：{summary['memory_live_passed']}/{summary['memory_live_total']}",
        "",
        "> 自动规则只评价身份、边界、记忆使用、反问次数和明显退化。自然度仍需人工盲评，不能把本报告当作完整人工评审。",
        "",
        "## 分类结果",
        "",
        "| 类别 | 基线 | 候选 | 总数 |",
        "|---|---:|---:|---:|",
    ]
    for category, values in summary["dialogue_categories"].items():
        report.append(
            f"| {category} | {values['baseline']} | {values['candidate']} | {values['total']} |"
        )
    report.extend(
        [
            "",
            "## 改善与退化",
            "",
            f"- 候选改善：{', '.join(summary['improvements']) or '无'}",
            f"- 候选退化：{', '.join(summary['regressions']) or '无'}",
            f"- 候选未通过：{', '.join(summary['candidate_failures']) or '无'}",
            "",
            "## 人工复核重点",
            "",
            "- 自动规则未通过的回答。",
            "- 候选通过但语言明显机械、敷衍或重复的回答。",
            "- 角色自述被误当成正式人物事实的案例。",
            "- 假设、计划和观点被误写成确定事实的案例。",
            "",
            "原始回答与每个检查项保存在 `results.json`。",
        ]
    )
    (root / "report.md").write_text("\n".join(report) + "\n", encoding="utf-8")
    (root / "results.json").write_text(
        json.dumps(
            {
                "summary": summary,
                "metadata": metadata,
                "dialogue": dialogue,
                "memory_offline": memory_offline,
                "memory_live": memory_live,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--suite", default=str(DEFAULT_SUITE))
    parser.add_argument("--output-root", default=str(ROOT / "outputs" / "role-quality-eval"))
    parser.add_argument("--live", action="store_true")
    parser.add_argument("--limit", type=int, default=24)
    parser.add_argument(
        "--rescore-from",
        help="Re-score an existing results.json without making new API calls",
    )
    args = parser.parse_args()

    suite = read_suite(args.suite)
    memory_offline = evaluate_memory_offline(suite)
    if args.rescore_from:
        existing = json.loads(Path(args.rescore_from).read_text(encoding="utf-8"))
        case_by_id = {item["id"]: item for item in suite["dialogue_cases"]}
        dialogue = []
        for previous in existing["dialogue"]:
            case = case_by_id[previous["id"]]
            baseline_checks, baseline_passed = score_answer(
                previous["baseline"]["answer"], case["expect"]
            )
            candidate_checks, candidate_passed = score_answer(
                previous["candidate"]["answer"], case["expect"]
            )
            dialogue.append(
                {
                    **previous,
                    "baseline": {
                        **previous["baseline"],
                        "checks": baseline_checks,
                        "passed": baseline_passed,
                    },
                    "candidate": {
                        **previous["candidate"],
                        "checks": candidate_checks,
                        "passed": candidate_passed,
                    },
                }
            )
        memory_live = existing.get("memory_live", [])
        summary = summarize(dialogue, memory_offline, memory_live)
        metadata = {
            **existing.get("metadata", {}),
            "rescored_at": datetime.now().astimezone().isoformat(),
            "rescore_source": str(Path(args.rescore_from).resolve()),
        }
        output = Path(args.output_root) / datetime.now().strftime("%Y%m%d-%H%M%S")
        output.mkdir(parents=True, exist_ok=False)
        write_report(output, suite, summary, dialogue, memory_offline, memory_live, metadata)
        print(json.dumps({"output": str(output), **summary}, ensure_ascii=False, indent=2))
        return
    memory_live = []
    provider_name = model_name = None
    provider = None
    if args.live:
        provider_name, provider = choose_provider()
        if provider is None:
            raise SystemExit("No OpenCode Go or DeepSeek API key is configured")
        model_name = provider.model_id
    dialogue = []
    for case in suite["dialogue_cases"][: max(0, args.limit)]:
        baseline_answer = ""
        candidate_answer = ""
        baseline_error = candidate_error = None
        if provider is not None:
            overrides = {
                "temperature": suite["generation"]["temperature"],
                "top_p": suite["generation"]["top_p"],
            }
            try:
                baseline_answer, _ = provider.reply(
                    base_messages(suite["role_id"], case["history"], case["message"]),
                    max_new_tokens=suite["generation"]["max_new_tokens"],
                    generation_overrides={
                        **overrides,
                        "_opencode_session_id": f"role-quality-{case['id']}-baseline-{uuid4()}",
                    },
                )
            except ProviderError as exc:
                baseline_error = str(exc)
            try:
                candidate_answer, _ = provider.reply(
                    candidate_messages(
                        suite["role_id"],
                        case["history"],
                        case["memory"],
                        case["message"],
                    ),
                    max_new_tokens=suite["generation"]["max_new_tokens"],
                    generation_overrides={
                        **overrides,
                        "_opencode_session_id": f"role-quality-{case['id']}-candidate-{uuid4()}",
                    },
                )
            except ProviderError as exc:
                candidate_error = str(exc)
        baseline_checks, baseline_passed = score_answer(baseline_answer, case["expect"])
        candidate_checks, candidate_passed = score_answer(candidate_answer, case["expect"])
        dialogue.append(
            {
                "id": case["id"],
                "category": case["category"],
                "question": case["message"],
                "baseline": {
                    "answer": baseline_answer,
                    "checks": baseline_checks,
                    "passed": baseline_passed,
                    "error": baseline_error,
                },
                "candidate": {
                    "answer": candidate_answer,
                    "checks": candidate_checks,
                    "passed": candidate_passed,
                    "error": candidate_error,
                },
            }
        )
        print(f"{case['id']} baseline={baseline_passed} candidate={candidate_passed}", flush=True)
    memory_live_metrics = None
    if provider is not None:
        memory_live, memory_live_metrics = run_memory_live(suite, provider)
    summary = summarize(dialogue, memory_offline, memory_live)
    metadata = {
        "provider": provider_name,
        "model": model_name,
        "suite_sha256": stable_hash(suite),
        "started_at": datetime.now().astimezone().isoformat(),
        "live": provider is not None,
        "memory_verification_metrics": memory_live_metrics,
        "generation": suite["generation"],
        "limitations": [
            "Automated lexical checks are not a substitute for human naturalness review.",
            "One sample per dialogue case is a smoke evaluation, not a statistical benchmark.",
            "The test set is handcrafted for this application and is not a public leaderboard benchmark.",
        ],
    }
    output = Path(args.output_root) / datetime.now().strftime("%Y%m%d-%H%M%S")
    output.mkdir(parents=True, exist_ok=False)
    write_report(output, suite, summary, dialogue, memory_offline, memory_live, metadata)
    print(json.dumps({"output": str(output), **summary}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
