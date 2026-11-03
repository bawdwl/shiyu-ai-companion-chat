import tempfile
import unittest
from pathlib import Path

from structured_memory import CandidateStore


def candidate(**overrides):
    value = {
        "status": "candidate",
        "kind": "user_report",
        "subject": "user",
        "fact_key": "user.current.place",
        "value": "苏州",
        "content": "用户当前住在苏州",
        "fact_type": "fact",
        "epistemic_status": "explicit",
        "operation": "add",
        "evidence": [
            {
                "message_id": 1,
                "role": "user",
                "quote": "我现在住在苏州。",
                "created_at": "2026-10-01T10:00:00+08:00",
            }
        ],
    }
    value.update(overrides)
    return value


class MemoryAdminTests(unittest.TestCase):
    def setUp(self):
        root = Path(__file__).resolve().parents[1] / "outputs"
        self.temp = tempfile.TemporaryDirectory(dir=root)
        self.store = CandidateStore(
            Path(self.temp.name) / "memory.sqlite3",
            allowed_root=root,
        )
        self.character = "companion"
        outcome = self.store.auto_ingest(self.character, candidate())
        self.old = outcome["item"]

    def tearDown(self):
        self.store.close()
        self.temp.cleanup()

    def test_manual_edit_creates_new_active_version_with_audit(self):
        updated = self.store.manual_edit(
            self.character,
            self.old["id"],
            content="用户当前住在杭州",
            value="杭州",
            reason="用户确认已经搬到杭州",
            expected_updated_at=self.old["updated_at"],
        )
        self.assertEqual(updated["content"], "用户当前住在杭州")
        self.assertEqual(updated["value"], "杭州")
        self.assertEqual(updated["auto_action"], "manual_edit")
        self.assertEqual(updated["source_authority"], "user_explicit")
        self.assertGreater(
            int(updated["observed_order"]),
            int(self.old["observed_order"]),
        )
        self.assertEqual(
            self.store.get(self.character, self.old["id"])["state"],
            "superseded",
        )
        self.assertEqual(self.store.search_active(
            self.character,
            "杭州",
        )[0]["value"], "杭州")
        events = self.store.list_admin_events(self.character)
        self.assertEqual(events[0]["action"], "manual_edit")
        self.assertEqual(events[0]["reason"], "用户确认已经搬到杭州")

    def test_mark_incorrect_marks_one_item_and_prevents_reextract(self):
        rejected = self.store.mark_incorrect(
            self.character,
            self.old["id"],
            reason="这条住址不是用户说的",
            expected_updated_at=self.old["updated_at"],
        )
        self.assertEqual(rejected["state"], "deleted")
        replay = self.store.auto_ingest(
            self.character,
            candidate(
                evidence=[
                    {
                        "message_id": 1,
                        "role": "user",
                        "quote": "我现在住在苏州。",
                        "created_at": "2026-10-01T10:00:00+08:00",
                    }
                ]
            ),
        )
        self.assertEqual(replay["action"], "ignored_deleted")

    def test_restore_history_supersedes_current_value(self):
        new = self.store.auto_ingest(
            self.character,
            candidate(
                value="杭州",
                content="用户当前住在杭州",
                operation="supersede",
                target_item_id=self.old["id"],
                evidence=[
                    {
                        "message_id": 2,
                        "role": "user",
                        "quote": "我更正一下，我现在住在杭州。",
                        "created_at": "2026-10-02T10:00:00+08:00",
                    }
                ],
            ),
        )["item"]
        restored = self.store.restore_history(
            self.character,
            self.old["id"],
            reason="用户确认杭州那一条是误解",
        )
        self.assertEqual(restored["value"], "苏州")
        self.assertEqual(restored["auto_action"], "manual_restore")
        self.assertGreater(
            int(restored["observed_order"]),
            max(
                int(self.old["observed_order"]),
                int(new["observed_order"]),
            ),
        )
        self.assertEqual(
            self.store.get(self.character, new["id"])["state"],
            "superseded",
        )

    def test_topic_suppression_deactivates_current_and_blocks_future_write(self):
        result = self.store.create_suppression(
            self.character,
            subject="user",
            category="fact",
            fact_key_prefix="user.current.place",
            reason="以后不要记录住址",
        )
        self.assertIn(self.old["id"], result["affected_ids"])
        self.assertEqual(
            self.store.get(self.character, self.old["id"])["state"],
            "deleted",
        )
        replay = self.store.auto_ingest(
            self.character,
            candidate(
                evidence=[
                    {
                        "message_id": 3,
                        "role": "user",
                        "quote": "我现在住在南京。",
                        "created_at": "2026-10-03T10:00:00+08:00",
                    }
                ],
                value="南京",
                content="用户当前住在南京",
            ),
        )
        self.assertEqual(replay["action"], "ignored_suppressed")
        self.assertEqual(len(self.store.list_suppressions(self.character)), 1)

    def test_stale_editor_version_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "变化"):
            self.store.manual_edit(
                self.character,
                self.old["id"],
                content="用户当前住在杭州",
                value="杭州",
                reason="修正",
                expected_updated_at="stale",
            )


if __name__ == "__main__":
    unittest.main()
