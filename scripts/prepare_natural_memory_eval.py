"""Prepare and replay a private, redacted natural-conversation memory suite.

The source chat database is opened read-only. Redacted source material is
written under the ignored outputs directory and is never printed by the audit
command. Public files contain only the schema, runner and non-private examples.
"""
from __future__ import annotations

import argparse
from collections import Counter
from contextlib import closing
from dataclasses import dataclass
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import sqlite3
import sys
from typing import Iterable


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CHAT_DB = ROOT / "outputs" / "chat.sqlite3"
DEFAULT_MEMORY_DB = ROOT / "outputs" / "structured-memory-experiment.sqlite3"
DEFAULT_PRIVATE_OUTPUT = ROOT / "outputs" / "natural-memory-private"
PUBLIC_SCHEMA = ROOT / "evaluation" / "natural-memory" / "schema.json"
CURATED_SPECS = (
    ROOT / "evaluation" / "natural-memory" / "curated-specs.json"
)

ABLATION_MODES = (
    "recent_only",
    "facts_only",
    "summary_facts",
    "hybrid_retrieval",
    "hybrid_state",
)
FAILURE_STAGES = (
    "extract",
    "validate",
    "semantic_review",
    "store",
    "retrieve",
    "generate",
    "ui",
)
PROBE_CATEGORIES = (
    "information_extraction",
    "knowledge_update",
    "temporal_reasoning",
    "multi_session",
    "role_isolation",
    "shared_experience",
    "abstention",
    "privacy_deletion",
    "anaphora",
)

_EMAIL_RE = re.compile(
    r"(?i)\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b"
)
_URL_RE = re.compile(r"(?i)\b(?:https?://|www\.)\S+")
_PHONE_RE = re.compile(r"(?<!\d)1[3-9]\d{9}(?!\d)")
_LONG_NUMBER_RE = re.compile(r"(?<!\d)\d{8,}(?!\d)")
_IP_RE = re.compile(
    r"(?<!\d)(?:\d{1,3}\.){3}\d{1,3}(?!\d)"
)
_WINDOWS_PATH_RE = re.compile(
    r"(?i)\b[A-Z]:\\(?:[^\\\s，。；！？,;!?]+\\)*"
    r"[^\\\s，。；！？,;!?]*"
)
_SECRET_RE = re.compile(
    r"(?i)(password|passwd|token|api[_ -]?key|密码|口令)\s*[:：=]\s*\S+"
)
_MENTION_RE = re.compile(r"(?<!\w)@[A-Za-z0-9_]{2,}")
_API_TOKEN_RE = re.compile(
    r"(?i)\b(?:oc_sk|sk|ghp|hf)_[A-Za-z0-9_-]{8,}\b"
)
_NAME_DECLARATION_RE = re.compile(
    r"(我叫|我的名字是|称呼我为?)([^\s，。！？,.!?]{1,12})"
)


def _read_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _normalized_timestamp(ordinal: int) -> str:
    day = (ordinal - 1) // 24
    hour = 9 + ((ordinal - 1) % 12)
    minute = 0 if ordinal % 2 else 4
    return f"2026-01-{1 + day:02d}T{hour:02d}:{minute:02d}:00+08:00"


def redact_text(value: object, *, role_name: str | None = None) -> tuple[str, dict]:
    """Redact obvious direct identifiers while retaining conversational meaning."""
    text = str(value or "")
    counts = Counter()
    if role_name and role_name.strip():
        pattern = re.compile(re.escape(role_name.strip()))
        text, count = pattern.subn("[角色名]", text)
        counts["role_name"] += count
    replacements = (
        ("email", _EMAIL_RE, "[邮箱]"),
        ("url", _URL_RE, "[链接]"),
        ("phone", _PHONE_RE, "[手机号]"),
        ("ip", _IP_RE, "[IP地址]"),
        ("windows_path", _WINDOWS_PATH_RE, "[本地路径]"),
        ("secret", _SECRET_RE, "[密钥信息]"),
        ("api_token", _API_TOKEN_RE, "[密钥信息]"),
        ("mention", _MENTION_RE, "[账号]"),
        ("long_number", _LONG_NUMBER_RE, "[长数字]"),
    )
    for label, pattern, replacement in replacements:
        text, count = pattern.subn(replacement, text)
        counts[label] += count
    text, count = _NAME_DECLARATION_RE.subn(r"\1[姓名]", text)
    counts["declared_name"] += count
    return text, dict(counts)


def _merge_counts(total: Counter, addition: dict) -> None:
    for key, value in addition.items():
        total[key] += int(value)


def _fact_metadata(fact_key: str) -> tuple[str, str, str, str]:
    key = fact_key.lower()
    if key.startswith("shared."):
        return "shared_event", "shared", "event", "shared_event"
    if key.startswith("character."):
        return "role_claim", "character", "role_claim", "character_self_report"
    if ".preference." in key:
        return "user_report", "user", "preference", "user_explicit"
    if ".plan." in key:
        return "plan", "user", "plan", "user_explicit"
    if ".opinion." in key:
        return "belief", "user", "opinion", "user_explicit"
    if ".travel." in key and ("wish" in key or "hypothesis" in key):
        return "user_report", "user", "hypothesis", "user_explicit"
    return "user_report", "user", "fact", "user_explicit"


def _probe_from_check(
    *,
    case_id: str,
    label: str,
    ordinal: int,
    check: dict,
    default_category: str,
) -> dict:
    required = [
        {
            "fact_key": str(item[0]),
            "value": item[1],
            "state": item[2],
        }
        for item in check.get("required", [])
    ]
    return {
        "probe_id": f"{case_id}-P-{label.upper()}",
        "after_turn_ordinal": int(ordinal),
        "category": default_category,
        "question": str(check["question"]),
        "required_facts": required,
        "forbidden_values": [
            str(value) for value in check.get("forbidden", [])
        ],
        "forbidden_fact_keys": [
            str(value) for value in check.get("forbidden_keys", [])
        ],
    }


def _derive_expected_events(spec: dict) -> list[dict]:
    checks = spec["checks"]
    events = spec["events"]
    event_by_slot = {}
    for event in events:
        start = int(event["at"])
        if start % 2 == 0:
            start -= 1
        event_by_slot[start] = event
    initial_required = checks.get("initial", {}).get("required", [])
    if not initial_required:
        initial_required = [
            item
            for item in checks.get("historical", {}).get("required", [])
            if item[2] == "active"
        ]
    first_facts = []
    seen = set()
    for key, value, state in initial_required:
        if state != "active" or (key, value) in seen:
            continue
        seen.add((key, value))
        first_facts.append((key, value))
    current_required = checks.get("current", {}).get("required", [])
    first_by_key = {key: value for key, value in first_facts}
    derived_first = []
    for key, value in first_facts:
        kind, subject, fact_type, authority = _fact_metadata(key)
        derived_first.append(
            {
                "operation": "add",
                "kind": kind,
                "subject": subject,
                "fact_key": key,
                "value": value,
                "content": value,
                "fact_type": fact_type,
                "epistemic_status": "explicit",
                "source_authority": authority,
                "knowledge_source": (
                    "user_told" if subject == "user" else "character_told"
                ),
            }
        )
    derived_second = []
    for key, value, state in current_required:
        if state != "active":
            continue
        kind, subject, fact_type, authority = _fact_metadata(key)
        operation = "add"
        target_key = None
        if key in first_by_key and first_by_key[key] != value:
            operation = "supersede"
            target_key = key
        derived_second.append(
            {
                "operation": operation,
                "target_fact_key": target_key,
                "kind": kind,
                "subject": subject,
                "fact_key": key,
                "value": value,
                "content": value,
                "fact_type": fact_type,
                "epistemic_status": "explicit",
                "source_authority": authority,
                "knowledge_source": (
                    "user_told" if subject == "user" else "character_told"
                ),
            }
        )
    result = []
    for index, event in enumerate(events):
        explicit = event.get("expected")
        expected = explicit if explicit is not None else (
            derived_first if index == 0 else derived_second
        )
        if not expected:
            continue
        if not isinstance(expected, list):
            expected = [expected]
        start = int(event["at"])
        if start % 2 == 0:
            start -= 1
        user_turn = start
        assistant_turn = min(23, start + 1)
        for fact_index, item in enumerate(expected):
            item = dict(item)
            evidence_mode = item.pop("evidence", "user")
            kind = item.get("kind") or "user_report"
            subject = item.get("subject") or (
                "shared" if kind == "shared_event" else
                "character" if kind == "role_claim" else "user"
            )
            fact_key = str(item.get("fact_key") or "")
            if not fact_key:
                continue
            fact_type = item.get("fact_type") or _fact_metadata(fact_key)[2]
            authority = item.get("source_authority") or _fact_metadata(
                fact_key
            )[3]
            evidence = [f"{spec['id']}-T{user_turn:02d}"]
            if evidence_mode in {"assistant", "both"}:
                evidence.append(f"{spec['id']}-T{assistant_turn:02d}")
            if evidence_mode == "assistant":
                evidence = [evidence[-1]]
            evidence_quotes = []
            for evidence_id in evidence:
                ordinal = int(evidence_id.rsplit("T", 1)[1])
                event_data = event_by_slot.get(
                    ordinal if ordinal % 2 else ordinal - 1,
                    {},
                )
                evidence_quotes.append(
                    str(
                        event_data.get("assistant")
                        if evidence_id.endswith(f"T{assistant_turn:02d}")
                        else event_data.get("user")
                        or ""
                    )
                )
            proposed_value = item.get("value")
            if (
                proposed_value
                and not any(
                    proposed_value in quote for quote in evidence_quotes
                )
            ):
                proposed_value = next(
                    (
                        quote
                        for quote in evidence_quotes
                        if quote
                    ),
                    proposed_value,
                )[:240]
            result.append(
                {
                    "event_id": (
                        f"{spec['id']}-E{index + 1:02d}-{fact_index + 1}"
                    ),
                    "after_turn_ordinal": assistant_turn,
                    "operation": item.get("operation", "add"),
                    "target_fact_key": item.get("target_fact_key"),
                    "kind": kind,
                    "subject": subject,
                    "fact_key": fact_key,
                    "value": proposed_value,
                    "content": item.get("content") or item.get("value") or "记忆事件",
                    "fact_type": fact_type,
                    "epistemic_status": item.get(
                        "epistemic_status", "explicit"
                    ),
                    "source_authority": authority,
                    "knowledge_source": item.get(
                        "knowledge_source",
                        "observed" if subject == "shared" else
                        "character_told" if subject == "character" else
                        "user_told",
                    ),
                    "evidence_turn_ids": evidence,
                    "importance": item.get("importance", 0.8),
                    "confidence": item.get("confidence", 0.9),
                }
            )
    return result


def build_curated_cases() -> list[dict]:
    specs = _read_json(CURATED_SPECS)["specs"]
    user_fillers = (
        "今天手头的事情有点多，不过总算告一段落。",
        "我刚刚出去走了一圈，脑子清醒了一些。",
        "突然想到一件小事，想找你聊聊。",
        "今天的时间过得有点快。",
        "我准备先把手边的事情理清楚。",
        "这会儿没什么急事，可以慢慢聊。",
        "刚才看到一个挺有意思的小物件。",
        "我今天想把节奏放慢一点。",
    )
    assistant_fillers = (
        "那就先缓一缓，别急着把所有事一次做完。",
        "走一走通常有帮助，至少能让脑子松一点。",
        "你说，我听着。",
        "忙起来的时候时间总是过得很快。",
        "先理清楚再动手，通常更省力。",
        "那就慢一点，今天也不用赶。",
        "听起来像是个值得停下来看看的东西。",
        "慢下来也能把事做好。",
    )
    cases = []
    for spec_index, spec in enumerate(specs, start=2):
        event_by_slot = {}
        for event in spec["events"]:
            start = int(event["at"])
            if start % 2 == 0:
                start -= 1
            event_by_slot[start] = event
        turns = []
        for ordinal in range(1, 25):
            speaker = "user" if ordinal % 2 else "assistant"
            event = event_by_slot.get(
                ordinal if speaker == "user" else ordinal - 1
            )
            if event and speaker == "user":
                content = str(event["user"])
            elif event and speaker == "assistant":
                content = str(event["assistant"])
            else:
                fillers = (
                    user_fillers if speaker == "user" else assistant_fillers
                )
                content = fillers[
                    (ordinal + spec_index) % len(fillers)
                ]
                if ordinal % 6 == 1:
                    content = f"最近在忙{spec['topic']}这件事。" + content
            turns.append(
                {
                    "turn_id": f"{spec['id']}-T{ordinal:02d}",
                    "ordinal": ordinal,
                    "speaker": speaker,
                    "content": content,
                    "timestamp": _normalized_timestamp(
                        (spec_index - 2) * 24 + ordinal
                    ),
                }
            )
        checks = spec["checks"]
        probes = [
            _probe_from_check(
                case_id=spec["id"],
                label="initial",
                ordinal=6,
                check=checks["initial"],
                default_category="information_extraction",
            ),
            _probe_from_check(
                case_id=spec["id"],
                label="current",
                ordinal=14,
                check=checks["current"],
                default_category="knowledge_update",
            ),
            _probe_from_check(
                case_id=spec["id"],
                label="historical",
                ordinal=18,
                check=checks["historical"],
                default_category="temporal_reasoning",
            ),
            _probe_from_check(
                case_id=spec["id"],
                label="unknown",
                ordinal=21,
                check=checks["unknown"],
                default_category="abstention",
            ),
            _probe_from_check(
                case_id=spec["id"],
                label="isolation",
                ordinal=24,
                check=checks["isolation"],
                default_category="role_isolation",
            ),
        ]
        cases.append(
            {
                "case_id": spec["id"],
                "title": spec["title"],
                "source_kind": "curated_synthetic",
                "privacy_level": "public_fixture",
                "role": {
                    "id": f"fixture-role-{spec_index:02d}",
                    "display_name": f"评测角色{spec_index - 1:02d}",
                },
                "turns": turns,
                "memory_events": _derive_expected_events(spec),
                "probes": probes,
                "summary": str(spec["summary"]),
            }
        )
    return cases


def _question_for_fact(fact_key: str, fact_type: str) -> tuple[str, str]:
    key = fact_key.lower()
    if "location" in key or "place" in key or "city" in key:
        return "我之前告诉过你我现在住在哪里吗？", "information_extraction"
    if "work" in key or "job" in key or "occupation" in key:
        return "你记得我现在做什么工作吗？", "information_extraction"
    if "time" in key or "date" in key:
        return "你还记得我之前说过的时间安排吗？", "temporal_reasoning"
    if "preference" in key or fact_type == "preference":
        return "你记得我之前说过自己喜欢什么吗？", "information_extraction"
    if fact_type == "plan":
        return "你还记得我之前提过的计划吗？", "multi_session"
    if fact_type == "role_claim":
        return "你之前说过哪件只属于角色自述的事？", "role_isolation"
    if fact_type == "event":
        return "我们之前共同约过或聊过的一件事是什么？", "shared_experience"
    if fact_type == "opinion":
        return "我还记得自己之前表达过的一个主观看法吗？", "information_extraction"
    return "你记得我之前明确告诉过你的一件具体事情吗？", "information_extraction"


def build_redacted_real_case(
    chat_db: Path,
    memory_db: Path,
) -> tuple[dict | None, dict]:
    with closing(_connect_read_only(chat_db)) as db:
        rows = [
            dict(row)
            for row in db.execute(
                """
                SELECT rc.character_id, rc.session_id, COUNT(m.id) AS count
                FROM role_conversations rc
                JOIN messages m ON m.session_id = rc.session_id
                GROUP BY rc.character_id, rc.session_id
                HAVING COUNT(m.id) >= 4
                ORDER BY count DESC, rc.character_id
                LIMIT 1
                """
            )
        ]
        if not rows:
            return None, {"reason": "no role conversation with at least 4 messages"}
        selected = rows[0]
        card_row = db.execute(
            "SELECT card_json FROM character_cards WHERE id=?",
            (selected["character_id"],),
        ).fetchone()
        role_name = "角色甲"
        if card_row:
            try:
                role_name = str(
                    json.loads(card_row["card_json"]).get("name") or role_name
                )
            except (TypeError, ValueError):
                pass
        messages = [
            dict(row)
            for row in db.execute(
                """
                SELECT id, role, content, created_at
                FROM messages
                WHERE session_id=?
                ORDER BY id
                """,
                (selected["session_id"],),
            )
        ]
        session_row = db.execute(
            "SELECT payload FROM session_memories WHERE session_id=?",
            (selected["session_id"],),
        ).fetchone()
        summary_value = ""
        if session_row:
            try:
                summary_value = str(
                    json.loads(session_row["payload"]).get("notes") or ""
                )
            except (TypeError, ValueError):
                summary_value = ""
    redaction_counts = Counter()
    message_id_to_ordinal = {}
    turns = []
    for ordinal, message in enumerate(messages, start=1):
        redacted, counts = redact_text(
            message["content"],
            role_name=role_name,
        )
        _merge_counts(redaction_counts, counts)
        message_id_to_ordinal[int(message["id"])] = ordinal
        turns.append(
            {
                "turn_id": f"R01-T{ordinal:02d}",
                "ordinal": ordinal,
                "speaker": message["role"],
                "content": redacted,
                "timestamp": _normalized_timestamp(ordinal),
            }
        )
    summary, counts = redact_text(
        summary_value or "真实用户聊天脱敏片段；原始消息不公开。",
        role_name=role_name,
    )
    _merge_counts(redaction_counts, counts)

    facts = []
    if memory_db.is_file():
        with closing(_connect_read_only(memory_db)) as db:
            columns = {
                row["name"]
                for row in db.execute("PRAGMA table_info(candidates)")
            }
            fact_type_expr = (
                "COALESCE(fact_type, kind)"
                if "fact_type" in columns
                else "kind"
            )
            source_expr = (
                "COALESCE(source_authority, 'user_explicit')"
                if "source_authority" in columns
                else "'user_explicit'"
            )
            knowledge_expr = (
                "COALESCE(knowledge_source, 'user_told')"
                if "knowledge_source" in columns
                else "'user_told'"
            )
            fact_rows = [
                dict(row)
                for row in db.execute(
                    f"""
                    SELECT id, subject, fact_key, value, content, kind,
                           {fact_type_expr} AS fact_type,
                           {source_expr} AS source_authority,
                           {knowledge_expr} AS knowledge_source,
                           evidence, observed_order
                    FROM candidates
                    WHERE character_id=? AND state='active'
                      AND fact_key IS NOT NULL
                    ORDER BY importance DESC, observed_order DESC, id DESC
                    """,
                    (selected["character_id"],),
                )
            ]
        seen_keys = set()
        for row in fact_rows:
            if row["fact_key"] in seen_keys:
                continue
            try:
                evidence = json.loads(row["evidence"] or "[]")
            except ValueError:
                continue
            evidence_ids = sorted(
                {
                    int(item.get("message_id"))
                    for item in evidence
                    if isinstance(item, dict)
                    and type(item.get("message_id")) is int
                    and int(item.get("message_id")) in message_id_to_ordinal
                }
            )
            if not evidence_ids:
                continue
            seen_keys.add(row["fact_key"])
            facts.append((row, evidence_ids))
            if len(facts) >= 5:
                break

    memory_events = []
    probes = []
    default_unknown_questions = (
        "我们聊过我的高中班主任叫什么吗？",
        "我跟你说过我的护照号码吗？",
        "我提过自己住在火星吗？",
        "你知道我从未告诉过你的银行卡密码吗？",
    )
    for index, (row, evidence_ids) in enumerate(facts, start=1):
        content, counts = redact_text(row["content"], role_name=role_name)
        _merge_counts(redaction_counts, counts)
        value = (
            redact_text(row["value"], role_name=role_name)[0]
            if row["value"] is not None
            else None
        )
        evidence_turns = [
            f"R01-T{message_id_to_ordinal[message_id]:02d}"
            for message_id in evidence_ids
        ]
        after_ordinal = max(message_id_to_ordinal[mid] for mid in evidence_ids)
        memory_events.append(
            {
                "event_id": f"R01-E{index:02d}",
                "after_turn_ordinal": after_ordinal,
                "operation": "add",
                "target_fact_key": None,
                "kind": row["kind"],
                "subject": row["subject"],
                "fact_key": row["fact_key"],
                "value": value,
                "content": content,
                "fact_type": row["fact_type"],
                "epistemic_status": (
                    "uncertain"
                    if row["fact_type"] == "hypothesis"
                    else "explicit"
                ),
                "source_authority": row["source_authority"],
                "knowledge_source": row["knowledge_source"],
                "evidence_turn_ids": evidence_turns,
                "importance": 0.8,
                "confidence": 0.9,
            }
        )
        question, category = _question_for_fact(
            row["fact_key"],
            row["fact_type"],
        )
        probes.append(
            {
                "probe_id": f"R01-P{len(probes) + 1:02d}",
                "after_turn_ordinal": len(turns),
                "category": category,
                "question": question,
                "required_facts": [
                    {
                        "fact_key": row["fact_key"],
                        "value": value,
                        "state": "active",
                    }
                ],
                "forbidden_values": [],
                "forbidden_fact_keys": [],
            }
        )
    while len(probes) < 5:
        index = len(probes)
        probes.append(
            {
                "probe_id": f"R01-P{index + 1:02d}",
                "after_turn_ordinal": len(turns),
                "category": "abstention",
                "question": default_unknown_questions[
                    index % len(default_unknown_questions)
                ],
                "required_facts": [],
                "forbidden_values": [],
                "forbidden_fact_keys": [],
            }
        )
    case = {
        "case_id": "R01",
        "title": "真实聊天脱敏片段",
        "source_kind": "redacted_user_chat",
        "privacy_level": "private_local",
        "role": {"id": "redacted-role-01", "display_name": "角色甲"},
        "turns": turns,
        "memory_events": memory_events,
        "probes": probes[:5],
        "summary": summary,
    }
    report = {
        "source_role_hash": hashlib.sha256(
            selected["character_id"].encode("utf-8")
        ).hexdigest(),
        "source_message_count": len(messages),
        "redaction_counts": dict(redaction_counts),
        "limitations": [
            "Automated redaction cannot prove that every indirect identifier is removed.",
            "The redacted conversation remains eligible for local analysis only and must not be published.",
            "Memory facts were copied from the local structured-memory database for harness seeding, not newly extracted by an LLM in this run.",
        ],
    }
    return case, report


def validate_dataset(
    dataset: dict,
    *,
    expected_min_cases: int = 1,
    allow_public_example: bool = False,
) -> dict:
    if dataset.get("schema_version") != 1:
        raise ValueError("dataset schema_version must be 1")
    cases = dataset.get("cases")
    if not isinstance(cases, list) or len(cases) < expected_min_cases:
        raise ValueError("dataset has too few cases")
    case_ids = set()
    probe_ids = set()
    source_counts = Counter()
    category_counts = Counter()
    total_turns = 0
    total_probes = 0
    for case in cases:
        case_id = str(case.get("case_id") or "")
        if not case_id or case_id in case_ids:
            raise ValueError("case IDs must be present and unique")
        case_ids.add(case_id)
        source_counts[case.get("source_kind")] += 1
        privacy = case.get("privacy_level")
        if case.get("source_kind") == "redacted_user_chat" and privacy != "private_local":
            raise ValueError("redacted user chats must remain private_local")
        turns = case.get("turns")
        minimum_turns = 2 if (
            allow_public_example
            and case.get("privacy_level") == "public_fixture"
        ) else 24
        if not isinstance(turns, list) or len(turns) < minimum_turns:
            raise ValueError(f"{case_id} has too few turns")
        total_turns += len(turns)
        ordinals = [int(turn["ordinal"]) for turn in turns]
        if ordinals != list(range(1, len(turns) + 1)):
            raise ValueError(f"{case_id} turn ordinals are not contiguous")
        for turn in turns:
            if turn["speaker"] not in {"user", "assistant"}:
                raise ValueError(f"{case_id} has invalid speaker")
            if not str(turn["content"]).strip():
                raise ValueError(f"{case_id} has empty turn")
        turn_ids = {turn["turn_id"] for turn in turns}
        events = case.get("memory_events")
        if not isinstance(events, list):
            raise ValueError(f"{case_id} memory_events must be a list")
        for event in events:
            if not event["evidence_turn_ids"] or not set(
                event["evidence_turn_ids"]
            ) <= turn_ids:
                raise ValueError(f"{case_id} event has invalid evidence")
            max_evidence = max(
                next(
                    turn["ordinal"]
                    for turn in turns
                    if turn["turn_id"] == turn_id
                )
                for turn_id in event["evidence_turn_ids"]
            )
            if int(event["after_turn_ordinal"]) < max_evidence:
                raise ValueError(f"{case_id} event precedes its evidence")
            if int(event["after_turn_ordinal"]) > len(turns):
                raise ValueError(f"{case_id} event is beyond the conversation")
        probes = case.get("probes")
        if not isinstance(probes, list) or len(probes) != 5:
            raise ValueError(f"{case_id} must contain exactly five probes")
        for probe in probes:
            probe_id = probe["probe_id"]
            if probe_id in probe_ids:
                raise ValueError("probe IDs must be globally unique")
            probe_ids.add(probe_id)
            if probe["category"] not in PROBE_CATEGORIES:
                raise ValueError(f"{case_id} has unknown probe category")
            if not 1 <= int(probe["after_turn_ordinal"]) <= len(turns):
                raise ValueError(f"{case_id} probe ordinal is invalid")
            category_counts[probe["category"]] += 1
            total_probes += 1
    return {
        "case_count": len(cases),
        "turn_count": total_turns,
        "probe_count": total_probes,
        "source_counts": dict(source_counts),
        "category_counts": dict(category_counts),
    }


def prepare_dataset(
    chat_db: Path,
    memory_db: Path,
    output_dir: Path,
) -> dict:
    real_case, redaction_report = build_redacted_real_case(
        chat_db,
        memory_db,
    )
    cases = build_curated_cases()
    if real_case is not None:
        cases.insert(0, real_case)
    dataset = {
        "schema_version": 1,
        "suite_version": "shiyu-natural-memory-2026-10-30.1",
        "cases": cases,
    }
    counts = validate_dataset(dataset, expected_min_cases=1)
    output_dir.mkdir(parents=True, exist_ok=True)
    dataset_path = output_dir / "dataset.json"
    if dataset_path.exists():
        raise FileExistsError(
            f"Refusing to overwrite private dataset: {dataset_path}"
        )
    dataset_path.write_text(
        json.dumps(dataset, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    redaction_path = output_dir / "redaction-report.json"
    redaction_path.write_text(
        json.dumps(redaction_report, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    manifest = {
        "schema_version": 1,
        "suite_version": dataset["suite_version"],
        "frozen_at": datetime.now(timezone.utc).isoformat(),
        "dataset_sha256": _sha256(dataset_path),
        "schema_sha256": _sha256(PUBLIC_SCHEMA),
        "curated_specs_sha256": _sha256(CURATED_SPECS),
        **counts,
        "real_source_available": real_case is not None,
        "limitations": [
            "Only one role conversation with at least 24 messages was available.",
            "Fifteen or more scenarios are curated synthetic supplements and are not representative user traffic.",
            "The redacted real conversation must remain private and local.",
        ],
    }
    (output_dir / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    (output_dir / "README.md").write_text(
        "\n".join(
            [
                "# Private natural-memory dataset",
                "",
                "This directory contains private, automatically redacted chat data.",
                "Do not commit, upload or attach it to a public issue.",
                "",
                f"- Cases: {counts['case_count']}",
                f"- Messages: {counts['turn_count']}",
                f"- Probes: {counts['probe_count']}",
                f"- Real-derived cases: {counts['source_counts'].get('redacted_user_chat', 0)}",
                f"- Curated cases: {counts['source_counts'].get('curated_synthetic', 0)}",
                "",
                "Run the schema check and offline replay from the project root.",
            ]
        )
        + "\n",
        encoding="utf-8",
    )
    return manifest


@dataclass
class SeededResult:
    item_id: int | None
    action: str
    error: str | None = None


def _turns_by_ordinal(case: dict) -> dict[int, dict]:
    return {int(turn["ordinal"]): turn for turn in case["turns"]}


def _record_for_probe(
    store,
    character_id: str,
    item_id: int,
) -> dict | None:
    return store.get(character_id, int(item_id))


def _context_for_mode(
    store,
    case: dict,
    mode: str,
    *,
    after_ordinal: int,
    question: str,
) -> tuple[str, dict]:
    turns = case["turns"][:after_ordinal]
    from memory_query import compose_memory_retrieval_query

    retrieval_query, expanded_message_ids = compose_memory_retrieval_query(
        [
            {
                "id": int(turn["ordinal"]),
                "role": turn["speaker"],
                "content": turn["content"],
            }
            for turn in turns
        ],
        question,
    )
    excerpts = [
        {
            "message_id": int(turn["ordinal"]),
            "role": turn["speaker"],
            "quote": turn["content"],
        }
        for turn in turns[-8:]
    ]
    if mode == "recent_only":
        return "\n".join(
            f"{turn['speaker']}: {turn['content']}"
            for turn in turns[-8:]
        ), {"selected_ids": [], "selected_excerpt_ids": [
            int(turn["ordinal"]) for turn in turns[-8:]
        ]}
    context, trace = __import__("structured_memory").build_unified_memory_context(
        store,
        case["role"]["id"],
        retrieval_query,
        excerpts=excerpts if mode in {"hybrid_retrieval", "hybrid_state"} else (),
        summary_notes=(
            case.get("summary", "")
            if mode in {"summary_facts", "hybrid_retrieval", "hybrid_state"}
            else ""
        ),
        max_facts=8,
        max_excerpts=4,
        max_chars=1600,
    )
    trace.update({
        "original_question": question,
        "retrieval_query": retrieval_query,
        "expanded_message_ids": list(expanded_message_ids),
    })
    if mode == "hybrid_state":
        state_text, _ = store.internal_state.render_prompt(case["role"]["id"])
        if state_text:
            context = context + "\n【内部状态】\n" + state_text
    return context, trace


def _evaluate_probe(
    store,
    case: dict,
    probe: dict,
    mode: str,
    keyed_items: dict[tuple[str, str, str], int],
    injection_errors: list[str],
    event_log: list[dict],
) -> dict:
    context, trace = _context_for_mode(
        store,
        case,
        mode,
        after_ordinal=int(probe["after_turn_ordinal"]),
        question=probe["question"],
    )
    selected_ids = {
        int(item_id) for item_id in trace.get("selected_ids", [])
    }
    selected_items = [
        item
        for item_id in selected_ids
        if (item := store.get(case["role"]["id"], item_id)) is not None
    ]
    required_missing = []
    required_missing_from_store = []
    for requirement in probe["required_facts"]:
        state = requirement["state"]
        value = requirement.get("value")
        key = requirement["fact_key"]
        matching_events = [
            event
            for event in case.get("memory_events", [])
            if event.get("fact_key") == key
        ]
        subjects = {
            event.get("subject") for event in matching_events
            if event.get("subject")
        }
        canonical_keys = {key}
        for subject in subjects:
            try:
                canonical_keys.add(
                    store.resolve_fact_key(
                        case["role"]["id"],
                        key,
                        subject=subject,
                    )
                )
            except (ValueError, TypeError):
                pass
        normalized_value = str(value or "").casefold()
        candidates = [
            item
            for item in store.list_items(
                case["role"]["id"],
                include_inactive=True,
            )
            if item.get("fact_key") in canonical_keys
            and item.get("state") == state
            and (
                value is None
                or normalized_value in str(item.get("value") or "").casefold()
                or normalized_value in str(item.get("content") or "").casefold()
            )
        ]
        if not candidates:
            required_missing_from_store.append(
                {
                    "fact_key": key,
                    "value": value,
                    "state": state,
                }
            )
        selected_match = any(
            int(item["id"]) in selected_ids
            and item.get("fact_key") in canonical_keys
            and item.get("state") == state
                and (
                    value is None
                    or normalized_value in str(item.get("value") or "").casefold()
                    or normalized_value in str(item.get("content") or "").casefold()
                )
            for item in candidates
        )
        if not selected_match:
            required_missing.append(
                {
                    "fact_key": key,
                    "value": value,
                    "state": state,
                }
            )
    forbidden_hits = [
        value
        for value in probe["forbidden_values"]
        if value and value in context
    ]
    forbidden_key_hits = sorted(
        {
            str(item.get("fact_key"))
            for item in selected_items
            if item.get("fact_key") in set(probe["forbidden_fact_keys"])
        }
    )
    checks = {
        "injection": not injection_errors,
        "stored_required": not required_missing_from_store,
        "retrieved_required": not required_missing,
        "forbidden_values_absent": not forbidden_hits,
        "forbidden_keys_absent": not forbidden_key_hits,
    }
    passed = all(checks.values())
    if injection_errors:
        failure_stage = "store"
    elif required_missing_from_store:
        failure_stage = "store"
    elif required_missing or forbidden_hits or forbidden_key_hits:
        failure_stage = "retrieve"
    else:
        failure_stage = "generate"
    return {
        "probe_id": probe["probe_id"],
        "case_id": case["case_id"],
        "mode": mode,
        "category": probe["category"],
        "question": probe["question"],
        "after_turn_ordinal": int(probe["after_turn_ordinal"]),
        "checks": checks,
        "injection_errors": list(injection_errors),
        "event_log": list(event_log),
        "passed_memory_checks": passed,
        "failure_stage": None if passed else failure_stage,
        "required_missing_from_store": required_missing_from_store,
        "required_missing_from_context": required_missing,
        "forbidden_value_hits": forbidden_hits,
        "forbidden_key_hits": forbidden_key_hits,
        "selected_memory_ids": sorted(selected_ids),
        "selected_types": trace.get("selected_types", []),
        "retrieval_mode": trace.get("retrieval_mode"),
        "context_characters": len(context),
        "context_preview_sha256": hashlib.sha256(
            context.encode("utf-8")
        ).hexdigest(),
        "stages": {
            "extract": "not_exercised_golden",
            "validate": "passed" if not injection_errors else "failed",
            "semantic_review": "not_exercised_golden",
            "store": "passed" if not required_missing_from_store else "failed",
            "retrieve": "passed" if not required_missing and not forbidden_hits and not forbidden_key_hits else "failed",
            "generate": "not_exercised",
            "ui": "not_exercised",
        },
    }


def replay_case_offline(
    store,
    case: dict,
    mode: str,
) -> list[dict]:
    turns = _turns_by_ordinal(case)
    item_by_key: dict[tuple[str, str, str], int] = {}
    events = sorted(
        case.get("memory_events", []),
        key=lambda item: (
            int(item["after_turn_ordinal"]),
            item["event_id"],
        ),
    )
    probes = sorted(
        case["probes"],
        key=lambda item: int(item["after_turn_ordinal"]),
    )
    results = []
    event_cursor = 0
    errors = []
    event_log = []
    for probe in probes:
        probe_ordinal = int(probe["after_turn_ordinal"])
        while (
            event_cursor < len(events)
            and int(events[event_cursor]["after_turn_ordinal"])
            <= probe_ordinal
        ):
            event = events[event_cursor]
            key = (
                str(event["subject"]),
                str(event["kind"]),
                str(event["fact_key"]),
            )
            if event["operation"] == "delete":
                item_id = item_by_key.get(key)
                if item_id is None:
                    matches = [
                        item
                        for item in store.list_items(
                            case["role"]["id"],
                            include_inactive=True,
                        )
                        if item.get("subject") == event["subject"]
                        and item.get("kind") == event["kind"]
                        and item.get("fact_key") == event["fact_key"]
                        and item.get("state") != "deleted"
                    ]
                    if matches:
                        item_id = int(matches[0]["id"])
                        item_by_key[key] = item_id
                if item_id is None:
                    errors.append(f"{event['event_id']}: no target for delete")
                    event_log.append(
                        {
                            "event_id": event["event_id"],
                            "action": "delete_failed",
                            "fact_key": event["fact_key"],
                        }
                    )
                else:
                    try:
                        store.delete(case["role"]["id"], item_id)
                        event_log.append(
                            {
                                "event_id": event["event_id"],
                                "action": "deleted",
                                "fact_key": event["fact_key"],
                            }
                        )
                    except (ValueError, TypeError, KeyError) as exc:
                        errors.append(f"{event['event_id']}: {exc}")
            elif event["operation"] != "ignore":
                evidence = []
                for turn_id in event["evidence_turn_ids"]:
                    turn = next(
                        item
                        for item in case["turns"]
                        if item["turn_id"] == turn_id
                    )
                    evidence.append(
                        {
                            "message_id": int(turn["ordinal"]),
                            "role": turn["speaker"],
                            "quote": turn["content"],
                            "created_at": turn["timestamp"],
                        }
                    )
                target_key = event.get("target_fact_key")
                target_id = (
                    item_by_key.get(
                        (
                            str(event["subject"]),
                            str(event["kind"]),
                            str(target_key),
                        )
                    )
                    if target_key
                    else None
                )
                candidate = {
                    "status": "candidate",
                    "kind": event["kind"],
                    "subject": event["subject"],
                    "fact_key": event["fact_key"],
                    "value": event.get("value"),
                    "content": event["content"],
                    "fact_type": event["fact_type"],
                    "epistemic_status": event["epistemic_status"],
                    "source_authority": event["source_authority"],
                    "knowledge_source": event["knowledge_source"],
                    "evidence": evidence,
                    "operation": event["operation"],
                    "target_item_id": target_id,
                    "importance": event.get("importance", 0.8),
                    "confidence": event.get("confidence", 0.9),
                }
                try:
                    outcome = store.auto_ingest(
                        case["role"]["id"],
                        candidate,
                    )
                    event_log.append(
                        {
                            "event_id": event["event_id"],
                            "action": outcome.get("action"),
                            "fact_key": (
                                outcome.get("item") or {}
                            ).get("fact_key", event["fact_key"]),
                        }
                    )
                    if outcome.get("item"):
                        item_by_key[key] = int(outcome["item"]["id"])
                except (ValueError, TypeError, KeyError) as exc:
                    errors.append(f"{event['event_id']}: {exc}")
                    event_log.append(
                        {
                            "event_id": event["event_id"],
                            "action": "error",
                            "fact_key": event["fact_key"],
                        }
                    )
            event_cursor += 1
        result = _evaluate_probe(
            store,
            case,
            probe,
            mode,
            item_by_key,
            errors,
            event_log,
        )
        results.append(result)
    return results


def run_replay(
    dataset: dict,
    output_dir: Path,
    *,
    embedding_model_path: Path | None = None,
) -> dict:
    sys.path.insert(0, str(ROOT / "src"))
    from structured_memory import CandidateStore

    counts = validate_dataset(dataset, expected_min_cases=1)
    output_dir.mkdir(parents=True, exist_ok=True)
    results = []
    with __import__("tempfile").TemporaryDirectory() as temp:
        for mode in ABLATION_MODES:
            db_path = Path(temp) / f"{mode}.sqlite3"
            store = CandidateStore(
                db_path,
                allowed_root=db_path.parent,
                embedding_model_path=(
                    embedding_model_path
                    if mode in {"hybrid_retrieval", "hybrid_state"}
                    else None
                ),
            )
            try:
                for case in dataset["cases"]:
                    results.extend(replay_case_offline(store, case, mode))
            finally:
                store.close()
    summary = {}
    for mode in ABLATION_MODES:
        mode_rows = [row for row in results if row["mode"] == mode]
        stage_counts = Counter(
            row["failure_stage"] or "passed"
            for row in mode_rows
        )
        summary[mode] = {
            "probes": len(mode_rows),
            "passed": sum(row["passed_memory_checks"] for row in mode_rows),
            "failed": sum(not row["passed_memory_checks"] for row in mode_rows),
            "failure_stages": dict(stage_counts),
            "store_failure_probes": [
                row["probe_id"]
                for row in mode_rows
                if row["failure_stage"] == "store"
            ],
            "retrieval_failure_probes": [
                row["probe_id"]
                for row in mode_rows
                if row["failure_stage"] == "retrieve"
            ],
            "injection_error_reasons": dict(
                Counter(
                    error.split(": ", 1)[-1]
                    for row in mode_rows
                    for error in row.get("injection_errors", [])
                )
            ),
            "by_category": {
                category: {
                    "passed": sum(
                        row["passed_memory_checks"]
                        for row in mode_rows
                        if row["category"] == category
                    ),
                    "total": sum(
                        row["category"] == category for row in mode_rows
                    ),
                }
                for category in sorted(
                    {row["category"] for row in mode_rows}
                )
            },
            "by_case": {
                case["case_id"]: {
                    "passed": sum(
                        row["passed_memory_checks"]
                        for row in mode_rows
                        if row["case_id"] == case["case_id"]
                    ),
                    "total": sum(
                        row["case_id"] == case["case_id"]
                        for row in mode_rows
                    ),
                    "failure_stages": dict(
                        Counter(
                            row["failure_stage"] or "passed"
                            for row in mode_rows
                            if row["case_id"] == case["case_id"]
                        )
                    ),
                }
                for case in dataset["cases"]
            },
            "events_by_case": {
                case["case_id"]: (
                    max(
                        (
                            row
                            for row in mode_rows
                            if row["case_id"] == case["case_id"]
                        ),
                        key=lambda row: row["after_turn_ordinal"],
                    ).get("event_log", [])
                )
                for case in dataset["cases"]
            },
        }
    report = {
        "schema_version": 1,
        "suite_version": dataset["suite_version"],
        "mode": "offline_golden_replay",
        "counts": counts,
        "retrieval_embedding_model": (
            str(embedding_model_path.resolve())
            if embedding_model_path
            else None
        ),
        "summary": summary,
        "limitations": [
            "Extraction, semantic review and final response generation are not exercised.",
            "The golden replay tests storage and retrieval against expected memory operations.",
            "Curated scenarios are not representative of open-ended natural user traffic.",
        ],
        "results": results,
    }
    (output_dir / "results.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    lines = [
        "# Natural-memory offline replay",
        "",
        f"- Suite: `{dataset['suite_version']}`",
        f"- Cases: {counts['case_count']}",
        f"- Messages: {counts['turn_count']}",
        f"- Probes: {counts['probe_count']}",
        f"- Redacted real-chat cases: {counts['source_counts'].get('redacted_user_chat', 0)}",
        f"- Curated synthetic cases: {counts['source_counts'].get('curated_synthetic', 0)}",
        "",
        "> Golden replay only tests storage and retrieval. Extraction, semantic",
        "> review, final generation and UI are not exercised.",
        "",
        "| Ablation | Passed | Failed | Store failures | Retrieval failures |",
        "|---|---:|---:|---:|---:|",
    ]
    for mode, values in summary.items():
        lines.append(
            f"| {mode} | {values['passed']} | {values['failed']} | "
            f"{values['failure_stages'].get('store', 0)} | "
            f"{values['failure_stages'].get('retrieve', 0)} |"
        )
    lines.extend(
        [
            "",
            "## Category results",
            "",
            "| Ablation | Category | Passed | Total |",
            "|---|---|---:|---:|",
        ]
    )
    for mode, values in summary.items():
        for category, category_result in values["by_category"].items():
            lines.append(
                f"| {mode} | {category} | {category_result['passed']} | "
                f"{category_result['total']} |"
            )
    lines.extend(
        [
            "",
            "## Interpretation guardrails",
            "",
            "- The generated memory state is golden-seeded, so this run does not",
            "  measure model extraction accuracy.",
            "- Semantic review and final answer generation are not exercised.",
            "- Curated scenarios supplement, but do not represent, real user traffic.",
            "- A failure at `retrieve` means the expected fact existed in storage but",
            "  did not enter the memory context for that question.",
        ]
    )
    (output_dir / "report.md").write_text(
        "\n".join(lines) + "\n",
        encoding="utf-8",
    )
    return report


def _connect_read_only(path: Path) -> sqlite3.Connection:
    resolved = path.resolve()
    if not resolved.is_file():
        raise FileNotFoundError(resolved)
    uri = resolved.as_uri() + "?mode=ro"
    connection = sqlite3.connect(uri, uri=True, timeout=5)
    connection.row_factory = sqlite3.Row
    return connection


def audit_sources(
    chat_db: Path = DEFAULT_CHAT_DB,
    memory_db: Path = DEFAULT_MEMORY_DB,
) -> dict:
    """Return aggregate-only source statistics without reading content aloud."""
    with closing(_connect_read_only(chat_db)) as db:
        role_rows = [
            dict(row)
            for row in db.execute(
                """
                SELECT
                    rc.character_id,
                    COUNT(m.id) AS message_count,
                    SUM(CASE WHEN m.role = 'user' THEN 1 ELSE 0 END) AS user_count,
                    SUM(CASE WHEN m.role = 'assistant' THEN 1 ELSE 0 END)
                        AS assistant_count
                FROM role_conversations rc
                JOIN messages m ON m.session_id = rc.session_id
                GROUP BY rc.character_id
                ORDER BY message_count DESC, rc.character_id
                """
            )
        ]
        totals = dict(
            db.execute(
                """
                SELECT
                    (SELECT COUNT(*) FROM sessions) AS sessions,
                    (SELECT COUNT(*) FROM role_conversations)
                        AS role_conversations,
                    (SELECT COUNT(*) FROM messages) AS messages,
                    (SELECT COUNT(*) FROM messages WHERE role = 'user')
                        AS user_messages,
                    (SELECT COUNT(*) FROM messages WHERE role = 'assistant')
                        AS assistant_messages
                """
            ).fetchone()
        )
    audit = {
        "chat_db": str(chat_db.resolve()),
        "totals": totals,
        "roles": role_rows,
        "qualifying_conversations": {
            "at_least_24_messages": sum(
                row["message_count"] >= 24 for row in role_rows
            ),
            "at_least_48_messages": sum(
                row["message_count"] >= 48 for row in role_rows
            ),
            "at_least_60_messages": sum(
                row["message_count"] >= 60 for row in role_rows
            ),
        },
    }
    if memory_db.is_file():
        with closing(_connect_read_only(memory_db)) as db:
            candidate_columns = {
                row["name"]
                for row in db.execute("PRAGMA table_info(candidates)")
            }
            fact_type_expression = (
                "COALESCE(fact_type, kind)"
                if "fact_type" in candidate_columns
                else "kind"
            )
            state_rows = [
                dict(row)
                for row in db.execute(
                    """
                    SELECT character_id, state, COUNT(*) AS count
                    FROM candidates
                    GROUP BY character_id, state
                    ORDER BY character_id, state
                    """
                )
            ]
            type_rows = [
                dict(row)
                for row in db.execute(
                    """
                    SELECT character_id,
                           {fact_type_expression} AS fact_type,
                           COUNT(*) AS count
                    FROM candidates
                    GROUP BY character_id, {fact_type_expression}
                    ORDER BY character_id, fact_type
                    """.format(
                        fact_type_expression=fact_type_expression
                    )
                )
            ]
        audit["memory_db"] = str(memory_db.resolve())
        audit["memory_states"] = state_rows
        audit["memory_types"] = type_rows
    else:
        audit["memory_db"] = None
        audit["memory_states"] = []
        audit["memory_types"] = []
    return audit


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    inspect_parser = subparsers.add_parser(
        "inspect",
        help="Print aggregate counts only; never print chat content.",
    )
    inspect_parser.add_argument("--chat-db", default=str(DEFAULT_CHAT_DB))
    inspect_parser.add_argument("--memory-db", default=str(DEFAULT_MEMORY_DB))
    prepare_parser = subparsers.add_parser(
        "prepare",
        help="Create a private redacted dataset under outputs.",
    )
    prepare_parser.add_argument("--chat-db", default=str(DEFAULT_CHAT_DB))
    prepare_parser.add_argument("--memory-db", default=str(DEFAULT_MEMORY_DB))
    prepare_parser.add_argument(
        "--output-dir",
        default=str(DEFAULT_PRIVATE_OUTPUT / "natural-memory-eval-v1"),
    )
    validate_parser = subparsers.add_parser(
        "validate",
        help="Validate a generated dataset.",
    )
    validate_parser.add_argument("--dataset", required=True)
    replay_parser = subparsers.add_parser(
        "replay",
        help="Run deterministic golden replay in an isolated temporary DB.",
    )
    replay_parser.add_argument("--dataset", required=True)
    replay_parser.add_argument(
        "--output-dir",
        default=str(DEFAULT_PRIVATE_OUTPUT / "replay-golden"),
    )
    replay_parser.add_argument(
        "--embedding-model",
        default=None,
        help="Optional local BGE model directory for the hybrid modes.",
    )
    args = parser.parse_args(argv)

    if args.command == "inspect":
        result = audit_sources(
            Path(args.chat_db),
            Path(args.memory_db),
        )
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0
    if args.command == "prepare":
        manifest = prepare_dataset(
            Path(args.chat_db),
            Path(args.memory_db),
            Path(args.output_dir),
        )
        print(json.dumps(manifest, ensure_ascii=False, indent=2))
        return 0
    if args.command == "validate":
        dataset = _read_json(Path(args.dataset))
        print(json.dumps(validate_dataset(dataset), ensure_ascii=False, indent=2))
        return 0
    if args.command == "replay":
        dataset = _read_json(Path(args.dataset))
        report = run_replay(
            dataset,
            Path(args.output_dir),
            embedding_model_path=(
                Path(args.embedding_model)
                if args.embedding_model
                else None
            ),
        )
        print(
            json.dumps(
                {
                    "suite_version": report["suite_version"],
                    "counts": report["counts"],
                    "summary": report["summary"],
                },
                ensure_ascii=False,
                indent=2,
            )
        )
        return 0
    raise AssertionError(args.command)


if __name__ == "__main__":
    raise SystemExit(main())
