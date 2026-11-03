import json
import tempfile
import unittest
from pathlib import Path

from fastapi.testclient import TestClient

from backend import create_app


ROOT = Path(__file__).resolve().parents[1]


def memory_candidate(value="苏州", message_id=1):
    return {
        "status": "candidate",
        "kind": "user_report",
        "subject": "user",
        "fact_key": "user.current.place",
        "value": value,
        "content": f"用户当前住在{value}",
        "fact_type": "fact",
        "epistemic_status": "explicit",
        "operation": "add",
        "evidence": [
            {
                "message_id": message_id,
                "role": "user",
                "quote": f"我现在住在{value}。",
                "created_at": "2026-10-30T10:00:00+08:00",
            }
        ],
    }


class MemoryAdminApiTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        root = Path(self.temp.name)
        app = create_app(
            root / "chat.sqlite3",
            engine_factory=lambda: None,
            structured_memory=True,
            structured_memory_db_path=root / "memory.sqlite3",
            default_provider="local",
        )
        self.client = TestClient(app)
        self.client.__enter__()
        card = json.loads(
            (
                ROOT
                / "evaluation"
                / "characters"
                / "full_character_fixture.json"
            ).read_text(encoding="utf-8")
        )
        self.character_id = card["id"]
        response = self.client.post("/characters", json={"card": card})
        self.assertEqual(response.status_code, 201, response.text)
        self.store = self.client.app.state.structured_memory
        self.item = self.store.auto_ingest(
            self.character_id,
            memory_candidate(),
        )["item"]

    def tearDown(self):
        self.client.__exit__(None, None, None)
        self.temp.cleanup()

    def test_patch_edit_updates_active_value_and_preserves_history(self):
        response = self.client.patch(
            f"/characters/{self.character_id}/structured-memory/{self.item['id']}",
            json={
                "action": "edit",
                "content": "用户当前住在杭州",
                "value": "杭州",
                "reason": "用户确认搬到杭州",
                "expected_updated_at": self.item["updated_at"],
            },
        )
        self.assertEqual(response.status_code, 200, response.text)
        body = response.json()
        self.assertEqual(body["record"]["state"], "active")
        self.assertEqual(body["record"]["value"], "杭州")
        self.assertEqual(
            self.store.get(self.character_id, self.item["id"])["state"],
            "superseded",
        )
        self.assertEqual(
            self.store.list_admin_events(self.character_id)[0]["action"],
            "manual_edit",
        )

    def test_patch_reject_and_restore_use_versioned_actions(self):
        second = self.store.auto_ingest(
            self.character_id,
            {
                **memory_candidate("杭州", 2),
                "operation": "supersede",
                "target_item_id": self.item["id"],
            },
        )["item"]
        rejected = self.client.patch(
            f"/characters/{self.character_id}/structured-memory/{second['id']}",
            json={
                "action": "reject",
                "reason": "杭州不是用户说的",
                "expected_updated_at": second["updated_at"],
            },
        )
        self.assertEqual(rejected.status_code, 200, rejected.text)
        self.assertEqual(rejected.json()["record"]["state"], "deleted")
        restored = self.client.patch(
            f"/characters/{self.character_id}/structured-memory/{self.item['id']}",
            json={
                "action": "restore",
                "reason": "恢复苏州记录",
                "expected_updated_at": self.store.get(
                    self.character_id,
                    self.item["id"],
                )["updated_at"],
            },
        )
        self.assertEqual(restored.status_code, 200, restored.text)
        self.assertEqual(restored.json()["record"]["value"], "苏州")
        self.assertEqual(
            restored.json()["record"]["auto_action"],
            "manual_restore",
        )

    def test_forget_topic_deactivates_and_suppresses_future_writes(self):
        response = self.client.post(
            f"/characters/{self.character_id}/structured-memory/forget-topic",
            json={
                "subject": "user",
                "category": "fact",
                "fact_key_prefix": "user.current.place",
                "reason": "以后不要记录住址",
            },
        )
        self.assertEqual(response.status_code, 201, response.text)
        body = response.json()
        self.assertIn(self.item["id"], body["affected_ids"])
        replay = self.store.auto_ingest(
            self.character_id,
            memory_candidate("南京", 3),
        )
        self.assertEqual(replay["action"], "ignored_suppressed")
        listed = self.client.get(
            f"/characters/{self.character_id}/structured-memory/suppressions"
        )
        self.assertEqual(listed.status_code, 200)
        suppression = listed.json()["suppressions"][0]
        removed = self.client.delete(
            f"/characters/{self.character_id}/structured-memory/"
            f"suppressions/{suppression['id']}"
        )
        self.assertEqual(removed.status_code, 200, removed.text)
        self.assertEqual(
            self.client.get(
                f"/characters/{self.character_id}/structured-memory"
            ).json()["suppressions"],
            [],
        )

    def test_stale_edit_is_rejected(self):
        response = self.client.patch(
            f"/characters/{self.character_id}/structured-memory/{self.item['id']}",
            json={
                "action": "edit",
                "content": "用户当前住在杭州",
                "value": "杭州",
                "reason": "修正",
                "expected_updated_at": "stale",
            },
        )
        self.assertEqual(response.status_code, 409, response.text)


if __name__ == "__main__":
    unittest.main()
