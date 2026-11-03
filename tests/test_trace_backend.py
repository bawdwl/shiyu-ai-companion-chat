import tempfile
import unittest
from pathlib import Path

from fastapi.testclient import TestClient

from backend import create_app


class TraceEngine:
    model_id = "trace-fake-model"

    def reply(self, messages, **kwargs):
        return "我会记住这句话。", {
            "seconds": 0.02,
            "input_tokens": 30,
            "output_tokens": 8,
            "model": self.model_id,
        }

    def reply_stream(self, messages, model_mode, stop_event, emit, **kwargs):
        emit("我会记住")
        emit("这句话。")
        return "我会记住这句话。", {
            "seconds": 0.03,
            "input_tokens": 31,
            "output_tokens": 9,
            "model": self.model_id,
        }


class TraceBackendTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.app = create_app(
            self.root / "chat.sqlite3",
            engine_factory=lambda: TraceEngine(),
            structured_memory=False,
            default_provider="local",
        )
        self.client = TestClient(self.app)
        self.client.__enter__()
        response = self.client.put(
            "/characters/companion/conversation",
            params={"provider": "local"},
        )
        self.assertEqual(response.status_code, 200, response.text)
        self.session = response.json()

    def tearDown(self):
        self.client.__exit__(None, None, None)
        self.temp.cleanup()

    def test_regular_chat_persists_trace_metadata(self):
        response = self.client.post(
            "/chat",
            json={
                "session_id": self.session["id"],
                "message": "今天有点累",
                "request_id": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
            },
        )
        self.assertEqual(response.status_code, 200, response.text)
        response_body = response.json()
        run_id = response_body["metrics"]["run_id"]
        trace_response = self.client.get(
            f"/sessions/{self.session['id']}/traces/{run_id}"
        )
        self.assertEqual(trace_response.status_code, 200, trace_response.text)
        trace = trace_response.json()
        self.assertEqual(trace["provider"], "local")
        self.assertEqual(trace["model"], "trace-fake-model")
        self.assertEqual(trace["input_tokens"], 30)
        self.assertEqual(trace["output_tokens"], 8)
        self.assertTrue(trace["prompt_version"].startswith("api-role-chat/"))
        self.assertEqual(len(trace["prompt_hash"]), 64)
        self.assertIn(
            "BR06",
            trace["trace"]["metadata"]["behavior_contract_ids"],
        )
        self.assertNotIn(
            "BR06",
            trace["trace"]["metadata"]["behavior_rule_ids"],
        )
        self.assertTrue(
            trace["trace"]["metadata"]["behavior_rule_hash"]
        )
        self.assertNotIn("今天有点累", str(trace["trace"]))

    def test_stream_chat_trace_is_available_from_listing(self):
        with self.client.stream(
            "POST",
            "/chat/stream",
            json={
                "session_id": self.session["id"],
                "message": "陪我说会儿话",
            },
        ) as response:
            body = "".join(response.iter_text())
        self.assertEqual(response.status_code, 200)
        self.assertIn("event: done", body)
        listed = self.client.get(
            f"/sessions/{self.session['id']}/traces"
        )
        self.assertEqual(listed.status_code, 200, listed.text)
        traces = listed.json()["traces"]
        self.assertEqual(len(traces), 1)
        self.assertEqual(traces[0]["status"], "completed")
        self.assertEqual(traces[0]["input_tokens"], 31)
        self.assertEqual(traces[0]["trace"]["metadata"]["response_source"], "model")


if __name__ == "__main__":
    unittest.main()
