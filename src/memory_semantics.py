"""Semantic guards and a bounded verifier for automatically extracted memory.

The first validation layer proves that a quote and proposed value are present in
the source. This module addresses the next question: does the source actually
entail the proposed fact, and what authority may that fact have?
"""
from __future__ import annotations

import json
import re


FACT_TYPES = {
    "fact",
    "preference",
    "plan",
    "hypothesis",
    "opinion",
    "event",
    "role_claim",
}
EPISTEMIC_STATUSES = {"explicit", "uncertain", "inferred", "disputed"}
SOURCE_AUTHORITIES = {
    "canon",
    "user_explicit",
    "shared_event",
    "character_self_report",
    "model_inference",
}
KNOWLEDGE_SOURCES = {
    "author",
    "user_told",
    "character_told",
    "observed",
    "inferred",
}
VISIBILITIES = {"character_private"}

_HYPOTHESIS_CUES = (
    "如果", "假如", "假如说", "要是", "万一", "或许", "也许", "可能",
    "说不定", "不一定", "未必", "假设", "设想", "希望有一天",
)
_PLAN_CUES = (
    "计划", "打算", "准备", "下次", "以后", "回头", "考虑搬到",
    "考虑换", "准备去", "准备搬", "将会", "约好", "约定",
)
_OPINION_CUES = ("我觉得", "我认为", "在我看来", "我感觉", "个人认为")
_PREFERENCE_CUES = (
    "我喜欢", "我不喜欢", "我最喜欢", "我更喜欢", "我偏爱",
    "喜欢", "讨厌", "偏好",
)
_SENSITIVE_FACT_HINTS = (
    "boundary", "address", "home", "family", "health", "medic", "work",
    "job", "birth", "relationship", "money", "salary", "password",
)


SEMANTIC_MEMORY_VERIFICATION_PROMPT = """
你是拾语后台记忆的语义审核器。你的任务不是续写对话，而是判断“候选记忆”是否能从给出的逐字证据中被严格推出。

规则：
1. 只根据 evidence 判断；不得引入外部知识，不得替模型补全日期、地点、动机或结果。
2. “喜欢、不喜欢”属于 preference；“打算、计划、准备、以后要去”属于 plan；
   “如果、假如、可能、也许、未必”属于 hypothesis；“我觉得、我认为”属于 opinion；
   已经发生的共同经历属于 event。
3. 假设、愿望、反问、玩笑、转述、否定和主观判断不能记录成确定事实。
4. 助手第一人称的即兴自述只能作为 role_claim，不能作为角色正式设定（canon）。
5. 证据仅支持“用户可能在考虑”，不能改成“用户已经决定”。
6. 证据仅支持“过去住过”，不能改成“现在住在这里”。
7. 只有明确发生并由用户证据支持的内容才可以作为 user_explicit；双方共同发生的事才可以作为 shared_event。
8. 改写不得改变主体、极性、时态、确定性和适用范围。无法确认时使用 reject。
9. 输出只能是 JSON 对象，不要输出 Markdown。

输出格式：
{"items":[{
  "candidate_index":0,
  "decision":"accept或downgrade或reject",
  "fact_type":"fact、preference、plan、hypothesis、opinion、event或role_claim",
  "epistemic_status":"explicit、uncertain、inferred或disputed",
  "source_authority":"user_explicit、shared_event、character_self_report或model_inference",
  "knowledge_source":"user_told、character_told、observed或inferred",
  "reason":"不超过120字，说明证据是否蕴含候选"
}]}
""".strip()


def _contains_any(text, cues):
    return any(cue in text for cue in cues)


def _infer_fact_type(kind, text):
    if kind == "role_claim":
        return "role_claim"
    if any(cue in text for cue in ("不要", "别", "不希望", "不喜欢", "不想")):
        return "fact"
    if _contains_any(text, _HYPOTHESIS_CUES):
        return "hypothesis"
    if kind == "plan" or _contains_any(text, _PLAN_CUES):
        return "plan"
    if kind == "belief" or _contains_any(text, _OPINION_CUES):
        return "opinion"
    if _contains_any(text, _PREFERENCE_CUES):
        return "preference"
    if kind == "shared_event":
        return "event"
    return "fact"


def _infer_epistemic_status(text, fact_type):
    if _contains_any(text, _HYPOTHESIS_CUES):
        return "uncertain"
    if any(cue in text for cue in ("似乎", "听起来", "看起来")):
        return "inferred"
    return "explicit"


def classify_candidate(candidate, evidence):
    """Normalize memory kind, type and authority before model verification."""
    kind = candidate.get("kind")
    roles = {item.get("role") for item in evidence}
    text = " ".join(
        part
        for part in (
            str(candidate.get("content") or ""),
            str(candidate.get("value") or ""),
            " ".join(str(item.get("quote") or "") for item in evidence),
        )
        if part
    )
    if kind == "character_detail" and roles and roles <= {"assistant"}:
        kind = "role_claim"
    fact_type = candidate.get("fact_type")
    if fact_type not in FACT_TYPES:
        fact_type = _infer_fact_type(kind, text)
    epistemic = candidate.get("epistemic_status")
    if epistemic not in EPISTEMIC_STATUSES:
        epistemic = _infer_epistemic_status(text, fact_type)

    if kind == "role_claim":
        fact_type = "role_claim"
        authority = "character_self_report"
        knowledge_source = "character_told"
    elif kind == "shared_event":
        authority = "shared_event"
        knowledge_source = "observed"
    elif roles == {"assistant"}:
        authority = "character_self_report"
        knowledge_source = "character_told"
    elif kind == "belief":
        authority = "user_explicit" if "user" in roles else "model_inference"
        knowledge_source = "user_told" if "user" in roles else "inferred"
    elif "user" in roles:
        authority = "user_explicit"
        knowledge_source = "user_told"
    else:
        authority = "model_inference"
        knowledge_source = "inferred"

    if fact_type == "hypothesis":
        epistemic = "uncertain"
    if kind == "role_claim":
        subject = "character"
    else:
        subject = candidate.get("subject")
    known_at = max(
        (
            str(item.get("created_at"))
            for item in evidence
            if item.get("created_at")
        ),
        default=None,
    )
    return {
        **candidate,
        "kind": kind,
        "subject": subject,
        "fact_type": fact_type,
        "epistemic_status": epistemic,
        "source_authority": authority,
        "knowledge_source": knowledge_source,
        "known_at": known_at,
        "visibility": candidate.get("visibility")
        if candidate.get("visibility") in VISIBILITIES
        else "character_private",
        "semantic_verified": False,
        "semantic_reason": "等待语义审核",
        "operation": (
            "contested"
            if epistemic != "explicit"
            and candidate.get("operation") == "supersede"
            else candidate.get("operation", "add")
        ),
    }


def needs_semantic_review(candidate):
    """All ordinary candidates receive one batched semantic check.

    Role claims also need checking so that an assistant statement is stored as
    a claim rather than promoted to canonical biography.
    """
    return candidate.get("kind") != "ignore"


def build_verification_payload(candidates):
    rows = []
    for index, item in enumerate(candidates):
        rows.append(
            {
                "candidate_index": index,
                "kind": item.get("kind"),
                "subject": item.get("subject"),
                "fact_type": item.get("fact_type"),
                "fact_key": item.get("fact_key"),
                "value": item.get("value"),
                "content": item.get("content"),
                "event_date": item.get("event_date"),
                "evidence": item.get("evidence"),
            }
        )
    return {"candidates": rows}


def parse_verification_result(raw, count):
    start, end = raw.find("{"), raw.rfind("}")
    if start < 0 or end <= start:
        raise ValueError("语义审核没有返回 JSON 对象")
    decoded = json.loads(raw[start : end + 1])
    items = decoded.get("items")
    if not isinstance(items, list):
        raise ValueError("语义审核缺少 items")
    results = {}
    decisions = {"accept", "downgrade", "reject"}
    for item in items:
        if not isinstance(item, dict):
            continue
        index = item.get("candidate_index")
        decision = item.get("decision")
        if (
            type(index) is not int
            or index < 0
            or index >= count
            or decision not in decisions
        ):
            continue
        fact_type = item.get("fact_type")
        epistemic = item.get("epistemic_status")
        authority = item.get("source_authority")
        source = item.get("knowledge_source")
        reason = str(item.get("reason") or "").strip()[:300]
        results[index] = {
            "decision": decision,
            "fact_type": fact_type if fact_type in FACT_TYPES else None,
            "epistemic_status": (
                epistemic if epistemic in EPISTEMIC_STATUSES else None
            ),
            "source_authority": (
                authority
                if authority in {
                    "user_explicit",
                    "shared_event",
                    "character_self_report",
                    "model_inference",
                }
                else None
            ),
            "knowledge_source": (
                source if source in KNOWLEDGE_SOURCES else None
            ),
            "reason": reason,
        }
    if len(results) != count:
        raise ValueError("语义审核没有覆盖全部候选")
    return results


def apply_verification(candidate, review):
    """Apply a conservative verifier result; missing reviews are rejected."""
    if not review or review.get("decision") == "reject":
        return None
    result = dict(candidate)
    for key in (
        "fact_type",
        "epistemic_status",
        "source_authority",
        "knowledge_source",
    ):
        value = review.get(key)
        if value:
            result[key] = value
    reason = review.get("reason") or "通过后台语义审核"
    result["semantic_verified"] = True
    result["semantic_reason"] = reason
    if result.get("fact_type") == "role_claim":
        result["kind"] = "role_claim"
        result["subject"] = "character"
        result["source_authority"] = "character_self_report"
        result["knowledge_source"] = "character_told"
    if result.get("epistemic_status") != "explicit":
        if result.get("operation") == "supersede":
            result["operation"] = "contested"
        if result.get("source_authority") == "user_explicit":
            result["confidence"] = min(
                float(result.get("confidence", 0.5)), 0.55
            )
    return result


def is_sensitive_candidate(candidate):
    key = str(candidate.get("fact_key") or "").lower()
    return candidate.get("operation") in {"supersede", "contested"} or any(
        token in key for token in _SENSITIVE_FACT_HINTS
    )
