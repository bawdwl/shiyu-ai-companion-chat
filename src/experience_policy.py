"""Deterministic, inspectable dialogue and continuity policies.

These helpers do not call a model. They only decide whether a small prompt
hint is worth adding for the current turn.
"""
from __future__ import annotations

import re


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

RECALL_CUES = (
    "记得",
    "还记得",
    "我以前",
    "我之前",
    "原来",
    "原本",
    "最初",
    "以前住",
    "现在住",
    "我的偏好",
    "我说过",
    "共同经历",
    "我们的约定",
)

CORRECTION_CUES = (
    "不是",
    "改成",
    "改到",
    "改为",
    "更正",
    "说错",
    "现在已经",
    "后来",
    "不再",
    "取消了",
    "搬到",
    "换成",
    "目前",
    "如今",
)

BOUNDARY_CUES = (
    "不要记",
    "别记",
    "不要主动提",
    "不希望被",
    "不要追问",
    "别追问",
)


def dialogue_policy(current_text, messages=None):
    text = str(current_text or "").strip()
    if not text:
        return {"strategy": "NONE", "reason": ""}
    if any(
        cue in text
        for cue in (
            "不想听建议",
            "别给建议",
            "不要建议",
            "别劝我",
            "不要安慰我",
            "只想倾诉",
            "先听我说",
            "别追问",
            "不要追问",
        )
    ):
        return {
            "strategy": "LISTEN",
            "secondary": None,
            "question_preference": "low",
            "reply_length": "short",
            "reason": "用户明确要求先倾听或不要追问",
        }
    if text.lower() in {"晚安", "拜拜", "再见", "先这样", "不聊了", "睡了"}:
        return {
            "strategy": "CLOSE",
            "secondary": None,
            "question_preference": "none",
            "reply_length": "short",
            "reason": "用户表达了结束或暂停当前话题",
        }
    if any(cue in text for cue in ANAPHORA_CUES):
        return {
            "strategy": "CONTINUE",
            "secondary": None,
            "question_preference": "natural",
            "reply_length": "natural",
            "reason": "用户要求承接之前的话题或事件",
        }
    if any(cue in text for cue in ("考上", "通过", "拿到offer", "拿到 offer", "录取", "升职", "中奖")):
        return {
            "strategy": "CELEBRATE",
            "secondary": None,
            "question_preference": "low",
            "reply_length": "short",
            "reason": "用户分享了明确的好消息",
        }
    if any(cue in text for cue in ("难过", "好累", "很累", "压力大", "焦虑", "委屈", "想哭", "崩溃", "心累")):
        return {
            "strategy": "LISTEN",
            "secondary": None,
            "question_preference": "low",
            "reply_length": "natural",
            "reason": "用户正在表达压力、难过或疲惫",
        }
    if any(cue in text for cue in ("不同意", "我不这么觉得", "你错了", "不是这样", "我有不同看法")):
        return {
            "strategy": "DISCUSS",
            "secondary": "DISAGREE",
            "question_preference": "natural",
            "reply_length": "natural",
            "reason": "用户提出了不同意见",
        }
    if any(cue in text for cue in ("你觉得呢", "你怎么看", "你呢", "你会怎么做", "你以前有没有")):
        return {
            "strategy": "SHARE",
            "secondary": None,
            "question_preference": "low",
            "reply_length": "natural",
            "reason": "用户邀请角色分享自己的看法或经历",
        }
    recent = " ".join(
        str(item.get("content") or "")
        for item in list(messages or [])[-6:]
    )
    if any(cue in recent + " " + text for cue in ("我觉得", "我认为", "是不是应该", "到底该", "怎么看")):
        return {
            "strategy": "DISCUSS",
            "secondary": None,
            "question_preference": "natural",
            "reply_length": "natural",
            "reason": "当前话题适合表达角色自己的判断",
        }
    return {
        "strategy": "NONE",
        "secondary": None,
        "question_preference": "natural",
        "reply_length": "natural",
        "reason": "",
    }


def dialogue_prompt(policy):
    strategy = (policy or {}).get("strategy", "NONE")
    if strategy == "NONE":
        return ""
    secondary = (policy or {}).get("secondary")
    suffix = f"，可轻微叠加 {secondary}" if secondary and secondary != strategy else ""
    return (
        f"本轮交流倾向：{strategy}{suffix}；"
        f"追问倾向为 {policy.get('question_preference', 'natural')}，"
        f"回复篇幅为 {policy.get('reply_length', 'natural')}。"
        "这只是表达倾向，不是硬脚本；以用户明确要求和角色人格为准。"
    )


def memory_need(messages, question):
    current = str(question or "").strip()
    if not current:
        return {
            "retrieve": False,
            "query": current,
            "expanded_source_ids": [],
            "reason": "empty_message",
        }
    expanded, source_ids = compose_query(messages, current)
    if any(cue in current for cue in CORRECTION_CUES):
        return {
            "retrieve": True,
            "query": expanded,
            "expanded_source_ids": source_ids,
            "reason": "explicit_correction_or_current_state",
        }
    if any(cue in current for cue in BOUNDARY_CUES):
        return {
            "retrieve": True,
            "query": expanded,
            "expanded_source_ids": source_ids,
            "reason": "explicit_boundary",
        }
    if any(cue in current for cue in RECALL_CUES):
        return {
            "retrieve": True,
            "query": expanded,
            "expanded_source_ids": source_ids,
            "reason": "explicit_recall",
        }
    if any(cue in current for cue in ANAPHORA_CUES):
        return {
            "retrieve": True,
            "query": expanded,
            "expanded_source_ids": source_ids,
            "reason": "anaphoric_reference",
        }
    normalized = re.sub(r"\s+", "", current.lower())
    question_like = any(
        cue in current
        for cue in ("什么", "哪", "多少", "谁", "为什么", "怎么",
                    "是否", "有没有", "告诉我")
    ) or current.rstrip().endswith(("?", "？"))
    if not question_like and (len(normalized) <= 6 or normalized in {
        "哈哈",
        "嘿嘿",
        "嗯嗯",
        "好的",
        "收到",
        "谢谢",
        "晚安",
        "早安",
        "在吗",
        "我回来了",
        "笑死",
    }):
        return {
            "retrieve": False,
            "query": current,
            "expanded_source_ids": [],
            "reason": "low_information_casual_turn",
        }
    return {
        "retrieve": True,
        "query": current,
        "expanded_source_ids": [],
        "reason": "topic_may_need_context",
    }


def compose_query(messages, current):
    needs_expansion = (
        any(cue in current for cue in ANAPHORA_CUES)
        or any(cue in current for cue in CORRECTION_CUES)
        or len(current) <= 18
    )
    if not needs_expansion:
        return current, []
    previous = [
        item
        for item in list(messages or [])
        if item.get("role") == "user"
        and str(item.get("content") or "").strip()
        and str(item.get("content") or "").strip() != current
    ][-2:]
    if not previous:
        return current, []
    return (
        "\n".join([str(item["content"]) for item in previous] + [current]),
        [
            int(item["id"])
            for item in previous
            if item.get("id") is not None
        ],
    )


def current_turn_affect(text):
    value = str(text or "").strip()
    if not value:
        return None
    if any(cue in value for cue in ("考上", "通过", "拿到offer", "拿到 offer", "录取", "升职", "中奖")):
        return {
            "label": "替用户感到高兴",
            "tone": "轻快自然",
            "humor": "允许轻微玩笑",
            "initiative": "先回应好消息，不急于追问细节",
            "reason": "current_turn_good_news",
        }
    if any(cue in value for cue in ("难过", "很累", "好累", "焦虑", "委屈", "想哭", "崩溃", "心累")):
        return {
            "label": "关切，先接住情绪",
            "tone": "放轻",
            "humor": "低",
            "initiative": "除非用户求助，不立即给方案",
            "reason": "current_turn_distress",
        }
    if any(cue in value for cue in ("不舒服", "生气", "失望", "你让我难受", "你刚才")):
        return {
            "label": "意识到关系出现张力",
            "tone": "认真",
            "humor": "关闭",
            "initiative": "先回应具体感受，不急着辩解",
            "reason": "current_turn_conflict",
        }
    if any(cue in value for cue in ("对不起", "是我误会", "我道歉", "刚才是我")):
        return {
            "label": "愿意修复",
            "tone": "坦诚",
            "humor": "低",
            "initiative": "接住和解，不反复翻旧账",
            "reason": "current_turn_repair",
        }
    if any(cue in value for cue in ("不要记", "别记", "不要追问", "别追问", "不想听建议")):
        return {
            "label": "尊重用户刚明确的边界",
            "tone": "克制",
            "humor": "低",
            "initiative": "不主动越过边界",
            "reason": "current_turn_boundary",
        }
    return None


def affect_prompt(affect):
    if not affect:
        return ""
    return (
        f"本轮即时反应：{affect['label']}；语气{affect['tone']}，"
        f"幽默{affect['humor']}，{affect['initiative']}。"
        "这只是本轮临时反应，不改写角色的稳定人格，"
        "也不代表关系数值已经永久变化。"
    )


SCENE_PATTERNS = (
    re.compile(r"(?:我们现在在|我们正坐在|我们已经在|我们来到|场景是|设定在|想象我们在)([^，。！？]{2,36})"),
    re.compile(r"把场景(?:改成|换到)([^，。！？]{2,36})"),
)
SCENE_EXIT_CUES = ("退出剧情", "回到现实", "结束这个场景", "不演了", "从场景里出来")
SCENE_ACTIVITY_WORDS = (
    "散步",
    "吃饭",
    "喝茶",
    "喝咖啡",
    "看书",
    "聊天",
    "坐车",
    "等人",
    "逛街",
    "看海",
    "休息",
)


def scene_from_messages(messages, current_text):
    mode = "daily"
    location = ""
    activity = ""
    for item in list(messages or []) + [{"role": "user", "content": current_text}]:
        if item.get("role") != "user":
            continue
        text = str(item.get("content") or "").strip()
        if any(cue in text for cue in SCENE_EXIT_CUES):
            mode, location, activity = "daily", "", ""
            continue
        captured = next(
            (
                match.group(1).strip()
                for pattern in SCENE_PATTERNS
                if (match := pattern.search(text))
            ),
            None,
        )
        if not captured:
            continue
        activity = next(
            (word for word in SCENE_ACTIVITY_WORDS if word in captured),
            "",
        )
        location = (
            captured
            if not activity
            else captured.replace(activity, "").strip().strip("的")
        )
        mode = "immersive"
    if mode != "immersive":
        return {
            "mode": "daily",
            "prompt": (
                "当前为日常陪伴模式。除非用户明确建立场景，不要自行声称双方身处某个地点，"
                "也不要替用户安排现实行动。"
            ),
        }
    parts = []
    if location:
        parts.append(f"地点：{location}")
    if activity:
        parts.append(f"当前活动：{activity}")
    return {
        "mode": "immersive",
        "location": location,
        "activity": activity,
        "prompt": (
            "当前为沉浸式剧情模式。"
            + "；".join(parts)
            + "。只使用用户已明确建立或前文已经发生的内容，不擅自推进未发生的剧情。"
        ),
    }


def derive_event_update(memory_item):
    if not memory_item:
        return None
    kind = str(memory_item.get("kind") or "")
    fact_type = str(memory_item.get("fact_type") or "")
    if kind not in {"plan", "shared_event"} and fact_type != "event":
        return None
    fact_key = str(memory_item.get("fact_key") or "").strip().lower()
    parts = [part for part in fact_key.split(".") if part]
    if parts and parts[0] in {"user", "character", "shared", "current", "profile"}:
        parts = parts[1:]
    if len(parts) < 2:
        return None
    topic_key = ".".join(parts[:-1])
    status = (
        "completed"
        if kind == "shared_event" or fact_type == "event"
        else "open"
    )
    return {
        "topic_key": topic_key,
        "title": str(memory_item.get("content") or "")[:80],
        "summary": str(memory_item.get("content") or ""),
        "status": status,
        "participants": (
            ["user"]
            if memory_item.get("subject") == "user"
            else ["character"]
            if memory_item.get("subject") == "character"
            else ["user", "character"]
        ),
    }
