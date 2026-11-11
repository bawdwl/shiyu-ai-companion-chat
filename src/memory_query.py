"""Build a memory-only retrieval query without changing the visible user text."""
from __future__ import annotations


ANAPHORA_CUES = (
    "那个",
    "那件",
    "这件事",
    "那件事",
    "上次",
    "刚才",
    "之前说",
    "后来呢",
    "然后呢",
    "怎么样了",
)


def compose_memory_retrieval_query(messages, question):
    """Return the retrieval text and source message IDs used for expansion."""
    current = str(question or "").strip()
    if not any(cue in current for cue in ANAPHORA_CUES):
        return current, []
    candidates = []
    for message in list(messages or [])[-8:]:
        text = str(message.get("content") or "").strip()
        if not text or text == current:
            continue
        message_id = message.get("id")
        candidates.append(
            (
                text,
                int(message_id) if message_id is not None else None,
            )
        )
    selected = candidates[-4:]
    query = "\n".join(
        [text for text, _ in selected] + [current]
    )
    return query, [item_id for _, item_id in selected if item_id is not None]
