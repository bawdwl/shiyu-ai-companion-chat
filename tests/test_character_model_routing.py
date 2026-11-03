import tempfile
import unittest
from pathlib import Path

from fastapi.testclient import TestClient

from backend import create_app


class FakeLocalEngine:
    adapter_available = True
    available_model_modes = ["base", "half", "full", "wukong", "xucheng", "haruhi"]
    model_id = "Qwen/Qwen3-1.7B"

    def __init__(self):
        self.calls = []

    def reply(
        self,
        messages,
        model_mode="base",
        max_new_tokens=256,
        generation_overrides=None,
    ):
        self.calls.append((messages, model_mode))
        return "本地测试回答", {"seconds": 0.01, "output_tokens": 6}


def card(character_id, name, source, local_mode="base"):
    return {
        "id": character_id,
        "name": name,
        "tagline": name + "的测试档案",
        "description": "用于验证角色独立模型路由。",
        "background": "普通日常背景。",
        "personality": "自然、友好。",
        "speech_style": "简短自然。",
        "relationship": "普通朋友。",
        "greeting": "你好。",
        "model_settings": {
            "source": source,
            "local_mode": local_mode,
        },
        "knowledge_boundary": {},
    }


class CharacterModelRoutingTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.engine = FakeLocalEngine()
        self.client = TestClient(
            create_app(
                Path(self.temp.name) / "routing.sqlite3",
                engine_factory=lambda: self.engine,
                enable_local_models=True,
                default_provider="opencode",
            )
        )
        self.client.__enter__()

    def tearDown(self):
        self.client.__exit__(None, None, None)
        self.temp.cleanup()

    def save(self, value):
        response = self.client.post("/characters", json={"card": value})
        self.assertEqual(response.status_code, 201, response.text)

    def test_local_and_api_choices_are_isolated_per_character(self):
        self.save(card("custom-local-half", "本地角色", "local", "half"))
        self.save(card("custom-cloud-api", "云端角色", "api"))

        local = self.client.put(
            "/characters/custom-local-half/conversation",
            params={"provider": "opencode"},
        )
        cloud = self.client.put(
            "/characters/custom-cloud-api/conversation",
            params={"provider": "opencode"},
        )
        self.assertEqual(local.status_code, 200, local.text)
        self.assertEqual(cloud.status_code, 200, cloud.text)
        self.assertEqual(local.json()["provider"], "local-half")
        self.assertEqual(local.json()["model_mode"], "half")
        self.assertEqual(local.json()["model_id"], "Qwen/Qwen3-1.7B")
        self.assertEqual(cloud.json()["provider"], "opencode")
        self.assertNotEqual(local.json()["id"], cloud.json()["id"])

        response = self.client.post(
            "/chat",
            json={
                "session_id": local.json()["id"],
                "message": "你好",
                "max_new_tokens": 32,
            },
        )
        self.assertEqual(response.status_code, 200, response.text)
        self.assertEqual(self.engine.calls[-1][1], "half")
        self.assertEqual(
            self.client.get("/sessions/" + cloud.json()["id"]).json()["provider"],
            "opencode",
        )

    def test_editing_one_role_switches_only_that_roles_provider(self):
        self.save(card("custom-local-base", "本地角色", "local", "base"))
        self.save(card("custom-stays-cloud", "云端角色", "api"))
        local = self.client.put(
            "/characters/custom-local-base/conversation",
            params={"provider": "opencode"},
        ).json()
        cloud = self.client.put(
            "/characters/custom-stays-cloud/conversation",
            params={"provider": "opencode"},
        ).json()

        updated = card("custom-local-base", "本地角色", "local", "full")
        self.save(updated)
        reopened = self.client.put(
            "/characters/custom-local-base/conversation",
            params={"provider": "opencode"},
        ).json()
        self.assertEqual(reopened["id"], local["id"])
        self.assertEqual(reopened["provider"], "local-full")
        self.assertEqual(
            self.client.get("/sessions/" + cloud["id"]).json()["provider"],
            "opencode",
        )

    def test_named_role_adapter_is_selected_only_for_its_character(self):
        self.save(card("custom-lora-wukong", "孙悟空", "local", "wukong"))
        self.save(card("custom-cloud", "云端角色", "api"))

        trained = self.client.put(
            "/characters/custom-lora-wukong/conversation",
            params={"provider": "opencode"},
        )
        cloud = self.client.put(
            "/characters/custom-cloud/conversation",
            params={"provider": "opencode"},
        )

        self.assertEqual(trained.status_code, 200, trained.text)
        self.assertEqual(trained.json()["provider"], "local-wukong")
        self.assertEqual(trained.json()["model_mode"], "wukong")
        self.assertEqual(cloud.json()["provider"], "opencode")
        response = self.client.post(
            "/chat",
            json={
                "session_id": trained.json()["id"],
                "message": "你好",
                "max_new_tokens": 32,
            },
        )
        self.assertEqual(response.status_code, 200, response.text)
        self.assertEqual(self.engine.calls[-1][1], "wukong")


if __name__ == "__main__":
    unittest.main()
