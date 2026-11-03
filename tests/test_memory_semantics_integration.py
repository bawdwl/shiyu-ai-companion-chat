import tempfile
import unittest
from pathlib import Path

from memory_records import validate_candidate
from structured_memory import CandidateStore, build_unified_memory_context


ROOT = Path(__file__).resolve().parents[1]


class MemorySemanticsIntegrationTests(unittest.TestCase):
    def setUp(self):
        output_root = ROOT / "outputs" / "memory-research"
        output_root.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=output_root)
        self.store = CandidateStore(Path(self.temp.name) / "memory.sqlite3")

    def tearDown(self):
        self.store.close()
        self.temp.cleanup()

    def test_role_claim_is_rendered_as_noncanonical_memory(self):
        source = {
            "id": 1,
            "role": "assistant",
            "content": "我小时候在海边住过一阵。",
        }
        validated = validate_candidate(
            {
                "kind": "character_detail",
                "subject": "character",
                "fact_key": "character.history.home",
                "value": "海边",
                "content": "小满自述小时候住在海边",
                "operation": "add",
                "evidence": [
                    {
                        "message_id": 1,
                        "role": "assistant",
                        "quote": source["content"],
                    }
                ],
            },
            [source],
        )
        result = self.store.auto_ingest("xiaoman", validated)["item"]
        self.assertEqual(result["kind"], "role_claim")
        self.assertEqual(result["source_authority"], "character_self_report")
        context, _ = build_unified_memory_context(
            self.store,
            "xiaoman",
            "你小时候住哪里",
        )
        self.assertIn("角色此前自述", context)
        self.assertIn("不是正式人物设定", context)

    def test_hypothesis_is_not_rendered_as_current_fact(self):
        source = {
            "id": 1,
            "role": "user",
            "content": "我可能考虑搬到杭州，但还没决定。",
        }
        validated = validate_candidate(
            {
                "kind": "user_report",
                "subject": "user",
                "fact_key": "user.current.place",
                "value": "杭州",
                "content": "用户可能考虑搬到杭州",
                "operation": "add",
                "evidence": [
                    {
                        "message_id": 1,
                        "role": "user",
                        "quote": source["content"],
                    }
                ],
            },
            [source],
        )
        self.store.auto_ingest("xiaoman", validated)
        context, _ = build_unified_memory_context(
            self.store,
            "xiaoman",
            "我是不是要搬去杭州",
        )
        self.assertIn("假设或想法", context)
        self.assertNotIn("- 当前用户事实", context)


if __name__ == "__main__":
    unittest.main()
