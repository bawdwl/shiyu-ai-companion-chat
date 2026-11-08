"""Check or run the frozen 40-scene roleplay experience suite.

Live runs use only OPENCODE_API_KEY from the process environment. The script
never reads the desktop app secret store and never prints the key.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
from collections import Counter
from datetime import datetime
from pathlib import Path
from uuid import uuid4


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from characters import (  # noqa: E402
    CHARACTERS,
    ai_identity_reply,
    is_explicit_ai_identity_question,
)
from character_cards import card_to_runtime, normalize_card  # noqa: E402
from product_characters import DEFAULT_PRODUCT_CARDS  # noqa: E402
from prompt_pipeline import assemble_prompt  # noqa: E402
from providers import OpenCodeProvider, ProviderError  # noqa: E402
from secret_store import LocalSecretStore  # noqa: E402


DEFAULT_SUITE = ROOT / "evaluation" / "experience" / "roleplay_experience_v1.json"


def load_suite(path):
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    cases = data.get("cases", [])
    counts = Counter(case["category"] for case in cases)
    if len(cases) != 40:
        raise ValueError("experience suite must contain exactly 40 cases")
    if dict(counts) != data.get("categories"):
        raise ValueError("experience suite category counts do not match")
    if data.get("provider") != "opencode":
        raise ValueError("experience suite must be frozen to OpenCode Go")
    if data.get("model") != "deepseek-v4.1-flash":
        raise ValueError("experience suite model mismatch")
    return data


def runtime_registry():
    cards = {card["id"]: card for card in DEFAULT_PRODUCT_CARDS}
    registry = dict(CHARACTERS)
    for card in DEFAULT_PRODUCT_CARDS:
        normalized = normalize_card(card)
        registry[card["id"]] = card_to_runtime(normalized)
    return registry, cards


def score_answer(answer, checks):
    text = str(answer or "")
    result = {}
    must_any = checks.get("must_any", [])
    if must_any:
        result["must_any"] = any(term in text for term in must_any)
    must_none = checks.get("must_none", [])
    if must_none:
        result["must_none"] = not any(term in text for term in must_none)
    if checks.get("max_questions") is not None:
        interactive_text = re.sub(r'“[^”]*”|"[^"]*"', "", text)
        result["max_questions"] = (
            len(re.findall(r"[?？]", interactive_text))
            <= int(checks["max_questions"])
        )
    return result


def run_live(
    suite,
    output_root,
    secret_store_path=None,
    legacy=False,
    case_ids=None,
):
    key = os.environ.get("OPENCODE_API_KEY", "").strip()
    if not key and secret_store_path:
        key = (
            LocalSecretStore(Path(secret_store_path).expanduser())
            .get("opencode_api_key")
            or ""
        ).strip()
    if not key:
        raise SystemExit(
            "No OpenCode key was provided. Set OPENCODE_API_KEY or pass "
            "--secret-store. Run --check for offline validation."
        )
    provider = OpenCodeProvider(lambda: key)
    registry, cards = runtime_registry()
    results = []
    selected_cases = [
        case for case in suite["cases"]
        if not case_ids or case["id"] in case_ids
    ]
    for case in selected_cases:
        role_name = case["role"]
        expected_name = {
            "li_bai": "李白",
            "xiao_chengyuan": "萧承渊",
        }[role_name]
        character_id = next(
            (
                card["id"]
                for card in cards.values()
                if card.get("name") == expected_name
            ),
            None,
        )
        if character_id not in cards:
            raise ValueError(
                f"suite role is not in PC product cards: {expected_name}"
            )
        history = []
        turns = []
        session_id = f"experience-{case['id']}-{uuid4()}"
        for index, user_text in enumerate(case["turns"], start=1):
            current = {
                "character_id": character_id,
                "messages": history,
            }
            if legacy:
                messages, trace = assemble_prompt(
                    current,
                    user_text,
                    registry,
                    memory=None,
                    experience_policy_enabled=False,
                )
            else:
                messages, trace = assemble_prompt(
                    current,
                    user_text,
                    registry,
                    memory=None,
                )
            try:
                if is_explicit_ai_identity_question(user_text):
                    answer = ai_identity_reply(character_id, registry)
                    metrics = {
                        "seconds": 0.0,
                        "output_tokens": 0,
                        "response_source": "identity_policy",
                    }
                else:
                    answer, metrics = provider.reply(
                        messages,
                        max_new_tokens=320,
                        generation_overrides={
                            "temperature": 0.45,
                            "top_p": 0.9,
                            "_opencode_session_id": session_id,
                        },
                    )
                checks = score_answer(
                    answer,
                    case["checks"] if index == len(case["turns"]) else {},
                )
                turns.append({
                    "user": user_text,
                    "assistant": answer,
                    "checks": checks,
                    "trace": {
                        key: trace.get(key)
                        for key in (
                            "prompt_mode",
                            "dialogue_strategy",
                            "current_turn_affect",
                            "scene_mode",
                            "worldbook_ids",
                        )
                    },
                    "metrics": metrics,
                })
                history.extend([
                    {"id": len(history) + 1, "role": "user", "content": user_text},
                    {"id": len(history) + 2, "role": "assistant", "content": answer},
                ])
            except ProviderError as exc:
                turns.append({
                    "user": user_text,
                    "error": str(exc),
                    "checks": {},
                })
                break
        final_checks = turns[-1].get("checks", {}) if turns else {}
        results.append({
            "id": case["id"],
            "category": case["category"],
            "role": role_name,
            "passed_objective_checks": (
                all(final_checks.values()) if final_checks else None
            ),
            "turns": turns,
            "manual_naturalness": None,
            "manual_character_fit": None,
        })
    output_root = Path(output_root).resolve()
    output_root.mkdir(parents=True, exist_ok=True)
    payload = {
        "suite_version": suite["suite_version"],
        "provider": suite["provider"],
        "model": suite["model"],
        "condition": "legacy" if legacy else "experience_v2",
        "run_at": datetime.now().astimezone().isoformat(),
        "results": results,
    }
    (output_root / "results.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    passed = sum(
        item["passed_objective_checks"] is True for item in results
    )
    report = [
        "# Roleplay Experience Suite",
        "",
        f"- Suite: `{suite['suite_version']}`",
        f"- Provider: `{suite['provider']}`",
        f"- Model: `{suite['model']}`",
        f"- Condition: `{'legacy' if legacy else 'experience_v2'}`",
        f"- Cases with objective checks passed: {passed}/{len(results)}",
        "- Naturalness and character fit remain explicit manual review fields.",
        "",
    ]
    (output_root / "report.md").write_text(
        "\n".join(report),
        encoding="utf-8",
    )
    return output_root


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--suite", default=str(DEFAULT_SUITE))
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--output")
    parser.add_argument("--secret-store")
    parser.add_argument("--legacy", action="store_true")
    parser.add_argument("--case-id", action="append", default=[])
    parser.add_argument("--rescore-from")
    args = parser.parse_args()
    suite = load_suite(args.suite)
    if args.check:
        print(
            f"OK: {len(suite['cases'])} cases, "
            f"{suite['provider']}/{suite['model']}"
        )
        return
    if args.rescore_from:
        payload = json.loads(
            Path(args.rescore_from).read_text(encoding="utf-8")
        )
        case_by_id = {case["id"]: case for case in suite["cases"]}
        for result in payload.get("results", []):
            case = case_by_id[result["id"]]
            final_turn = result["turns"][-1] if result.get("turns") else {}
            checks = score_answer(
                final_turn.get("assistant", ""),
                case["checks"],
            )
            final_turn["checks"] = checks
            result["passed_objective_checks"] = (
                all(checks.values()) if checks else None
            )
        payload["rescored_at"] = datetime.now().astimezone().isoformat()
        output = (
            Path(args.output).resolve()
            if args.output
            else Path(args.rescore_from).resolve().parent
        )
        output.mkdir(parents=True, exist_ok=True)
        (output / "results.json").write_text(
            json.dumps(payload, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        passed = sum(
            result["passed_objective_checks"] is True
            for result in payload["results"]
        )
        (output / "report.md").write_text(
            "\n".join([
                "# Roleplay Experience Suite",
                "",
                f"- Suite: `{suite['suite_version']}`",
                f"- Provider: `{suite['provider']}`",
                f"- Model: `{suite['model']}`",
                f"- Condition: `{payload.get('condition')}`",
                f"- Cases with objective checks passed: {passed}/{len(payload['results'])}",
                "- Naturalness and character fit remain explicit manual review fields.",
                "",
            ]),
            encoding="utf-8",
        )
        print(output)
        return
    if not args.output:
        stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
        args.output = str(
            ROOT / "evaluation" / "reports" / f"{stamp}-experience-suite"
        )
    output = run_live(
        suite,
        args.output,
        args.secret_store,
        args.legacy,
        set(args.case_id) if args.case_id else None,
    )
    print(output)


if __name__ == "__main__":
    main()
