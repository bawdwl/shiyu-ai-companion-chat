import tempfile
import unittest
from pathlib import Path

from memory_query import compose_memory_retrieval_query
from structured_memory import CandidateStore, build_unified_memory_context


def fact(index, content):
    return {
        "status": "candidate",
        "kind": "user_report",
        "subject": "user",
        "fact_key": f"user.test.item_{index}",
        "value": str(index),
        "content": content,
        "operation": "add",
        "evidence": [{
            "message_id": index,
            "role": "user",
            "quote": content,
        }],
    }


def cleaned_fact(
    index,
    content,
    *,
    fact_key=None,
    value=None,
    observed_order=None,
):
    return {
        "subject": "user",
        "fact_key": fact_key or f"user.test.item_{index}",
        "value": value,
        "content": content,
        "kind": "user_report",
        "evidence": [{
            "message_id": index,
            "role": "user",
            "quote": content,
        }],
        "event_date": None,
        "observed_order": observed_order or index,
        "semantic_verified": True,
        "semantic_reason": "test",
        "importance": 0.5,
        "confidence": 0.8,
        "operation": "add",
        "target_item_id": None,
        "fact_type": "fact",
        "epistemic_status": "explicit",
        "source_authority": "user_explicit",
        "knowledge_source": "user_told",
        "known_at": None,
        "visibility": "character_private",
    }


class MemoryRetrievalBudgetTests(unittest.TestCase):
    def test_short_reference_expands_with_previous_user_turns(self):
        messages = [
            {"id": 1, "role": "user", "content": "周五下午要去软件园面试。"},
            {"id": 2, "role": "assistant", "content": "记住了。"},
            {"id": 3, "role": "user", "content": "我有点紧张。"},
        ]
        query, ids = compose_memory_retrieval_query(messages, "那个事怎么样了？")
        self.assertIn("软件园面试", query)
        self.assertIn("有点紧张", query)
        self.assertIn("记住了", query)
        self.assertEqual(ids, [1, 2, 3])

    def test_direct_question_is_not_expanded(self):
        messages = [{"id": 1, "role": "user", "content": "我住在杭州。"}]
        query, ids = compose_memory_retrieval_query(messages, "我住在哪里？")
        self.assertEqual(query, "我住在哪里？")
        self.assertEqual(ids, [])

    def test_context_respects_token_budget(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            store = CandidateStore(
                root / "memory.sqlite3",
                allowed_root=root,
            )
            try:
                for index in range(1, 8):
                    store.auto_ingest(
                        "custom-test",
                        fact(index, f"测试偏好{index}：" + "很长的描述" * 8),
                    )
                context, trace = build_unified_memory_context(
                    store,
                    "custom-test",
                    "测试偏好",
                    max_facts=8,
                    max_excerpts=0,
                    max_chars=1000,
                    max_tokens=30,
                    token_estimator=lambda value: max(1, len(str(value)) // 2),
                )
                self.assertLessEqual(trace["estimated_tokens"], 30)
                self.assertLessEqual(len(context), 1000)
            finally:
                store.close()

    def test_location_query_uses_fact_key_concepts_without_bge(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            store = CandidateStore(
                root / "memory.sqlite3",
                allowed_root=root,
            )
            try:
                store.auto_ingest(
                    "custom-test",
                    {
                        **fact(1, "用户当前住在杭州"),
                        "fact_key": "user.current.place",
                        "value": "杭州",
                    },
                )
                ranked = store.search_hybrid(
                    "custom-test",
                    "我现在住在哪个城市？",
                    limit=3,
                )
                self.assertEqual(ranked[0]["value"], "杭州")
            finally:
                store.close()

    def test_drink_preference_query_uses_consumer_language(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            store = CandidateStore(
                root / "memory.sqlite3",
                allowed_root=root,
            )
            try:
                store.auto_ingest(
                    "custom-test",
                    {
                        **fact(1, "用户现在更喜欢手冲咖啡"),
                        "fact_key": "user.preference.drink",
                        "value": "手冲咖啡",
                    },
                )
                ranked = store.search_hybrid(
                    "custom-test",
                    "你记得我的口味是什么吗？",
                    limit=3,
                )
                self.assertEqual(ranked[0]["value"], "手冲咖啡")
            finally:
                store.close()

    def test_change_question_retrieves_current_and_historical_values(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            store = CandidateStore(
                root / "memory.sqlite3",
                allowed_root=root,
            )
            try:
                store.auto_ingest(
                    "custom-test",
                    {
                        **fact(1, "用户以前用黑色杯子"),
                        "fact_key": "user.item.cup",
                        "value": "黑色杯子",
                        "observed_order": 1,
                    },
                )
                store.auto_ingest(
                    "custom-test",
                    {
                        **fact(2, "用户后来觉得不方便，已经换成透明玻璃杯"),
                        "fact_key": "user.item.cup",
                        "value": "透明玻璃杯",
                        "observed_order": 4,
                    },
                )
                ranked = store.search_hybrid(
                    "custom-test",
                    "我的杯子发生了什么变化？",
                    limit=5,
                    include_superseded=True,
                )
                self.assertEqual(
                    {item["state"] for item in ranked},
                    {"active", "superseded"},
                )
                self.assertEqual(
                    {item["value"] for item in ranked},
                    {"透明玻璃杯", "黑色杯子"},
                )
            finally:
                store.close()

    def test_natural_recall_and_change_queries_use_fact_key_concepts(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            store = CandidateStore(
                root / "memory.sqlite3",
                allowed_root=root,
            )
            try:
                old = store.auto_ingest(
                    "custom-test",
                    {
                        **fact(1, "下周三上午"),
                        "fact_key": "user.interview.time",
                        "value": "下周三上午",
                    },
                )["item"]
                store.auto_ingest(
                    "custom-test",
                    {
                        **fact(2, "更正一下，改到周五下午两点"),
                        "fact_key": "user.interview.time",
                        "value": "周五下午两点",
                        "operation": "supersede",
                        "target_item_id": old["id"],
                    },
                )
                store.auto_ingest(
                    "custom-test",
                    {
                        **fact(3, "爷爷留下来的旧胶片相机"),
                        "fact_key": "user.item.camera",
                        "value": "爷爷留下来的旧胶片相机",
                    },
                )

                remembered, _ = build_unified_memory_context(
                    store,
                    "custom-test",
                    "面试时间改过吗？",
                    max_excerpts=0,
                )
                self.assertIn("下周三上午", remembered)
                self.assertIn("周五下午两点", remembered)

                recalled, trace = build_unified_memory_context(
                    store,
                    "custom-test",
                    "你记得我之前明确告诉过你的一件具体事情吗？",
                    max_excerpts=0,
                )
                self.assertTrue(trace["selected_ids"])
                self.assertIn("旧胶片相机", recalled)

                thing, _ = build_unified_memory_context(
                    store,
                    "custom-test",
                    "那个东西是什么来历？",
                    max_excerpts=0,
                )
                self.assertIn("旧胶片相机", thing)
            finally:
                store.close()

    def test_large_memory_scope_prefilters_without_losing_exact_match(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            store = CandidateStore(
                root / "memory.sqlite3",
                allowed_root=root,
            )
            try:
                with store.db:
                    for index in range(1, 521):
                        store._insert_clean(
                            "custom-test",
                            cleaned_fact(
                                index,
                                f"无关测试记忆{index}",
                                observed_order=100 + index,
                            ),
                            state="active",
                        )
                    store._insert_clean(
                        "custom-test",
                        cleaned_fact(
                            800,
                            "用户喜欢在海边散步",
                            fact_key="user.preference.place",
                            value="海边",
                            observed_order=1,
                        ),
                        state="active",
                    )
                ranked = store.search_hybrid(
                    "custom-test",
                    "海边",
                    limit=3,
                )
                self.assertTrue(ranked)
                self.assertEqual("海边", ranked[0]["value"])
            finally:
                store.close()


if __name__ == "__main__":
    unittest.main()
