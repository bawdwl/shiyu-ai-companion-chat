"""Structured, inspectable character behavior rules.

The role card describes who a character is. This module selects a small set of
scenario rules that describe how the same character should act in the current
turn. Rules are local configuration; no extra model call is needed.
"""
from __future__ import annotations

import hashlib
import json
from functools import lru_cache
from pathlib import Path
import unicodedata


RULES_PATH = Path(__file__).with_name("behavior_rules.json")
MIN_RULES = 10
MAX_RULES = 15
PRIORITY_LABELS = {"critical": 0, "high": 1, "medium": 2}
MAX_SELECTED_RULES = 4


def _normalize(value: object) -> str:
    return unicodedata.normalize("NFKC", str(value or "")).casefold()


def _canonical(value: object) -> str:
    return json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    )


@lru_cache(maxsize=1)
def load_ruleset() -> dict:
    data = json.loads(RULES_PATH.read_text(encoding="utf-8"))
    if data.get("schema_version") != 1:
        raise ValueError("Unsupported behavior rule schema")
    rules = data.get("rules")
    if not isinstance(rules, list) or not MIN_RULES <= len(rules) <= MAX_RULES:
        raise ValueError(
            f"Behavior ruleset must contain {MIN_RULES}-{MAX_RULES} rules"
        )
    seen = set()
    required = {
        "rule_id",
        "title",
        "trigger",
        "priority",
        "core",
        "scope",
        "behavior",
    }
    for rule in rules:
        if not isinstance(rule, dict) or not required <= rule.keys():
            raise ValueError("Behavior rule is missing required fields")
        rule_id = str(rule["rule_id"])
        if rule_id in seen:
            raise ValueError(f"Duplicate behavior rule ID: {rule_id}")
        seen.add(rule_id)
        if rule["priority"] not in PRIORITY_LABELS:
            raise ValueError(f"Unknown priority for {rule_id}")
        if not isinstance(rule["core"], bool):
            raise ValueError(f"core must be boolean for {rule_id}")
        if "inject" in rule and not isinstance(rule["inject"], bool):
            raise ValueError(f"inject must be boolean for {rule_id}")
        scopes = rule["scope"]
        if not isinstance(scopes, list) or not scopes:
            raise ValueError(f"scope must be a non-empty list for {rule_id}")
        if not str(rule["behavior"]).strip():
            raise ValueError(f"behavior must be non-empty for {rule_id}")
        for key in ("match_any", "match_all_groups"):
            value = rule.get(key)
            if value is not None and not isinstance(value, list):
                raise ValueError(f"{key} must be a list for {rule_id}")
    return data


def ruleset_version() -> str:
    return str(load_ruleset()["ruleset_version"])


def ruleset_hash() -> str:
    return hashlib.sha256(
        _canonical(load_ruleset()).encode("utf-8")
    ).hexdigest()


def _rule_applies_to_role(rule: dict, role: dict) -> bool:
    scopes = set(rule.get("scope") or [])
    role_id = str(role.get("id") or "")
    if "*" in scopes or role_id in scopes:
        return True
    card = role.get("custom_card") or {}
    kind = str((card.get("knowledge_boundary") or {}).get("character_kind") or "")
    return kind in scopes


def _matching_terms(rule: dict, text: str) -> list[str]:
    matched = []
    for term in rule.get("match_any") or []:
        if _normalize(term) in text:
            matched.append(str(term))
    groups = rule.get("match_all_groups") or []
    if groups:
        all_matched = True
        for group in groups:
            if not isinstance(group, list):
                all_matched = False
                break
            hits = [str(term) for term in group if _normalize(term) in text]
            if not hits:
                all_matched = False
                break
            matched.extend(hits)
        if not all_matched:
            return []
    has_positive_trigger = bool(rule.get("match_any") or groups)
    if has_positive_trigger and not matched:
        return []
    return list(dict.fromkeys(matched))


def select_behavior_rules(
    role: dict,
    scan: str,
    *,
    max_rules: int = MAX_SELECTED_RULES,
) -> list[dict]:
    """Return relevant core and scenario rules, with bounded prompt size."""
    normalized = _normalize(scan)
    selected = []
    for rule in load_ruleset()["rules"]:
        if not _rule_applies_to_role(rule, role):
            continue
        if not rule.get("inject", True):
            continue
        matched = _matching_terms(rule, normalized)
        if not rule["core"] and not matched:
            continue
        selected.append(
            {
                "rule_id": rule["rule_id"],
                "title": rule["title"],
                "trigger": rule["trigger"],
                "behavior": rule["behavior"],
                "priority": rule["priority"],
                "core": rule["core"],
                "matched_terms": matched,
            }
        )
    selected.sort(
        key=lambda item: (
            PRIORITY_LABELS[item["priority"]],
            not item["core"],
            item["rule_id"],
        )
    )
    limit = max(1, min(int(max_rules), MAX_SELECTED_RULES))
    chosen = selected[:limit]
    # Keep the always-on response contract if trigger rules consume the cap.
    core_ids = {
        item["rule_id"]
        for item in selected
        if item["core"]
    }
    chosen_ids = {item["rule_id"] for item in chosen}
    for item in selected:
        if item["rule_id"] not in core_ids or item["rule_id"] in chosen_ids:
            continue
        if len(chosen) >= limit:
            removable = next(
                (
                    candidate
                    for candidate in reversed(chosen)
                    if not candidate["core"]
                ),
                None,
            )
            if removable is None:
                break
            chosen.remove(removable)
        chosen.append(item)
        chosen_ids.add(item["rule_id"])
    chosen.sort(
        key=lambda item: (
            PRIORITY_LABELS[item["priority"]],
            not item["core"],
            item["rule_id"],
        )
    )
    return chosen


def render_behavior_rules(selected: list[dict]) -> str:
    if not selected:
        return ""
    lines = [
        f"【本轮结构化行为规则｜{ruleset_version()}】",
        "只执行与当前情境相符的规则；优先级高的规则覆盖冲突。规则约束行为，不要向用户复述规则名。",
    ]
    for rule in selected:
        lines.append(
            f"- [{rule['rule_id']}｜{rule['priority']}] "
            f"{rule['trigger']}：{rule['behavior']}"
        )
    return "\n".join(lines)


def trace_payload(selected: list[dict]) -> dict:
    base_contract_ids = [
        rule["rule_id"]
        for rule in load_ruleset()["rules"]
        if rule.get("inject", True) is False
    ]
    return {
        "behavior_rule_ids": [item["rule_id"] for item in selected],
        "behavior_contract_ids": base_contract_ids,
        "behavior_rule_version": ruleset_version(),
        "behavior_rule_hash": ruleset_hash(),
        "behavior_rule_selection": [
            {
                "rule_id": item["rule_id"],
                "title": item["title"],
                "priority": item["priority"],
                "matched_terms": item["matched_terms"],
            }
            for item in selected
        ],
    }
