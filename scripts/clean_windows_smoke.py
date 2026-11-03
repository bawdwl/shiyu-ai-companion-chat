"""Run an isolated API-only first-use smoke test on Windows.

This script does not load Qwen, Whisper, TTS, private databases or the user's
real API keys. It uses a deterministic provider in a temporary directory and
checks configuration persistence, role creation, conversation opening and one
saved chat turn.
"""
from __future__ import annotations

import argparse
from datetime import datetime
import json
from pathlib import Path
import sys
import tempfile


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from backend import create_app  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402
from providers import MockProvider  # noqa: E402


def custom_card() -> dict:
    path = ROOT / "evaluation" / "characters" / "full_character_fixture.json"
    card = json.loads(path.read_text(encoding="utf-8"))
    card["id"] = "custom-clean-windows-smoke"
    card["name"] = "清洁验收角色"
    card["tagline"] = "仅用于隔离环境验收"
    card["greeting"] = "第一次见面，先和我说说你现在的心情吧。"
    return card


def run_smoke() -> dict:
    if sys.platform != "win32":
        raise RuntimeError("clean Windows smoke must run on Windows")
    with tempfile.TemporaryDirectory(prefix="shiyu-clean-smoke-") as temp:
        root = Path(temp)
        db_path = root / "chat.sqlite3"
        secret_path = root / "secrets.json"
        provider = MockProvider(default="我会记住这句，并继续和你聊。")
        app = create_app(
            db_path=db_path,
            default_provider="opencode",
            opencode_provider=provider,
            speech_factory=lambda: None,
            asr_factory=lambda: None,
            structured_memory=False,
            auto_memory_worker_enabled=False,
            secret_path=secret_path,
        )
        with TestClient(app) as client:
            health_before = client.get("/health")
            if health_before.status_code not in {200, 503}:
                health_before.raise_for_status()
            payload = health_before.json()
            if payload["providers"]["opencode"]["configured"]:
                raise AssertionError("temporary secret store should start empty")
            if payload.get("speech_provider") or payload.get("asr_provider"):
                raise AssertionError("API-only smoke must not initialize speech")
            catalogue = client.get("/characters")
            catalogue.raise_for_status()
            if catalogue.json()["characters"]:
                raise AssertionError("fresh smoke database should have no default roles")

            fake_key = "oc_sk_" + "clean_windows_smoke_not_real_123456789"
            configured = client.put(
                "/settings/opencode-key",
                json={"api_key": fake_key},
            )
            configured.raise_for_status()
            if fake_key in configured.text:
                raise AssertionError("API key was returned by the settings endpoint")

            saved = client.post(
                "/characters",
                json={"card": custom_card()},
            )
            if saved.status_code != 201:
                raise RuntimeError(
                    "custom character creation failed: " + saved.text
                )
            conversation = client.put(
                "/characters/custom-clean-windows-smoke/conversation",
                params={"provider": "opencode"},
            )
            conversation.raise_for_status()
            session_id = conversation.json()["id"]
            answer = client.post(
                "/chat",
                json={
                    "session_id": session_id,
                    "message": "今天刚完成一次干净环境验收。",
                    "max_new_tokens": 64,
                },
            )
            answer.raise_for_status()
            if "我会记住这句" not in answer.json().get("answer", ""):
                raise AssertionError("deterministic provider answer was not saved")
            history = client.get("/sessions/" + session_id)
            history.raise_for_status()
            roles = [item["role"] for item in history.json()["messages"]]
            if roles[-2:] != ["user", "assistant"]:
                raise AssertionError("saved chat did not persist a user/assistant pair")

        restarted = create_app(
            db_path=db_path,
            default_provider="opencode",
            opencode_provider=provider,
            speech_factory=lambda: None,
            asr_factory=lambda: None,
            structured_memory=False,
            auto_memory_worker_enabled=False,
            secret_path=secret_path,
        )
        with TestClient(restarted) as client:
            health_after = client.get("/health")
            health_after.raise_for_status()
            if not health_after.json()["providers"]["opencode"]["configured"]:
                raise AssertionError("API key did not persist after restart")
            history = client.get("/sessions/" + session_id)
            history.raise_for_status()
            if len(history.json()["messages"]) < 3:
                raise AssertionError("isolated chat did not survive restart")

        return {
            "schema_version": 1,
            "status": "passed",
            "platform": sys.platform,
            "python": sys.version.split()[0],
            "provider": "mock-through-opencode-engine",
            "local_model_loaded": False,
            "speech_initialized": False,
            "default_characters": 0,
            "custom_character_created": True,
            "conversation_opened": True,
            "chat_turn_saved": True,
            "history_survived_restart": True,
            "api_key_persisted_without_plaintext_return": True,
            "temporary_database": True,
            "created_at": datetime.now().astimezone().isoformat(),
            "limitations": [
                "Uses a deterministic provider, not the real cloud API.",
                "Runs on the current Windows host; it is not proof of a different clean machine.",
                "The full dependency installation is covered by the wrapper script, not this smoke.",
            ],
        }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report")
    args = parser.parse_args()
    result = run_smoke()
    if args.report:
        output = Path(args.report)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(
            json.dumps(result, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
