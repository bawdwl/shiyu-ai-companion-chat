"""Minimal, privacy-aware tracing for one API-generated chat turn."""
from __future__ import annotations

import hashlib
import json
from uuid import uuid4

from behavior_rules import ruleset_hash, ruleset_version


PROMPT_PIPELINE_VERSION = "api-role-chat/2026-10-30.3"


def _canonical(value) -> str:
    return json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
        default=str,
    )


def stable_hash(value) -> str:
    return hashlib.sha256(_canonical(value).encode("utf-8")).hexdigest()


def role_version(role) -> str:
    """Hash stable role configuration while excluding per-turn state/memory."""
    if not isinstance(role, dict):
        role = {}
    return stable_hash(
        {
            "id": role.get("id"),
            "name": role.get("name"),
            "tag": role.get("tag"),
            "description": role.get("description"),
            "prompt": role.get("prompt"),
            "identity": role.get("identity"),
            "profile": role.get("profile"),
            "custom_card": role.get("custom_card"),
            "example_dialogue": role.get("example_dialogue"),
            "behavior_rules": role.get("behavior_rules"),
        }
    )


def make_turn_trace(
    *,
    run_id,
    provider,
    model,
    role,
    messages,
    prompt_trace,
    metrics,
    max_new_tokens,
    response_source="model",
):
    """Build a compact trace without storing the full prompt or API key."""
    prompt_trace = prompt_trace if isinstance(prompt_trace, dict) else {}
    metrics = metrics if isinstance(metrics, dict) else {}
    role_hash = role_version(role)
    prompt_hash = stable_hash(
        [{"role": message.get("role"), "content": message.get("content")} for message in messages]
    )
    generation = dict(prompt_trace.get("generation_preset") or {})
    generation["max_new_tokens"] = int(max_new_tokens)
    metadata = {
        "worldbook_ids": prompt_trace.get("worldbook_ids", []),
        "strict_knowledge_mode": bool(prompt_trace.get("strict_knowledge_mode")),
        "behavior_rule_ids": prompt_trace.get("behavior_rule_ids", []),
        "behavior_contract_ids": prompt_trace.get(
            "behavior_contract_ids", []
        ),
        "behavior_rule_version": prompt_trace.get(
            "behavior_rule_version", ruleset_version()
        ),
        "behavior_rule_hash": prompt_trace.get(
            "behavior_rule_hash", ruleset_hash()
        ),
        "behavior_rule_selection": prompt_trace.get(
            "behavior_rule_selection", []
        ),
        "structured_memory_ids": prompt_trace.get("structured_memory_ids", []),
        "session_memory_ids": prompt_trace.get("session_memory_ids", []),
        "memory_retrieval_mode": prompt_trace.get("memory_retrieval_mode"),
        "memory_retrieval_scores": prompt_trace.get("memory_retrieval_scores", []),
        "structured_memory_chars": prompt_trace.get("structured_memory_chars", 0),
        "structured_memory_tokens": prompt_trace.get(
            "structured_memory_tokens", 0
        ),
        "structured_memory_token_budget": prompt_trace.get(
            "structured_memory_token_budget"
        ),
        "memory_retrieval_query_hash": prompt_trace.get(
            "memory_retrieval_query_hash"
        ),
        "memory_retrieval_expanded": bool(
            prompt_trace.get("memory_retrieval_expanded")
        ),
        "memory_retrieval_source_message_ids": prompt_trace.get(
            "memory_retrieval_source_message_ids", []
        ),
        "internal_state": prompt_trace.get("internal_state_snapshot"),
        "repeat_sentences_removed": prompt_trace.get(
            "repeated_history_sentences_removed", 0
        ),
        "provider_switch_from": prompt_trace.get("provider_switch_from"),
        "memory_warning": prompt_trace.get("memory_warning"),
        "image_id": prompt_trace.get("image_id"),
        "response_source": response_source,
    }
    usage = {
        key: metrics.get(key)
        for key in (
            "input_tokens",
            "output_tokens",
            "cached_tokens",
            "seconds",
            "finish_reason",
            "stopped",
            "cost",
            "allowance",
        )
        if metrics.get(key) is not None
    }
    return {
        "run_id": str(run_id or uuid4()),
        "provider": str(provider or "unknown"),
        "model": str(model or metrics.get("model") or "unknown"),
        "prompt_version": (
            f"{PROMPT_PIPELINE_VERSION}:{role_hash[:12]}:"
            f"{metadata['behavior_rule_hash'][:8]}"
        ),
        "prompt_hash": prompt_hash,
        "role_version": role_hash,
        "generation_params": generation,
        "metadata": metadata,
        "usage": usage,
    }
