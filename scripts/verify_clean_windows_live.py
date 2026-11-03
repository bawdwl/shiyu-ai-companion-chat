"""Run a real first-chat acceptance test against an isolated Windows service.

The source API key is decrypted from the existing local secret store, sent only
to a temporary localhost service, and never printed or written to evidence.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import subprocess
import sys
import time
from uuid import uuid4

import httpx


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from secret_store import LocalSecretStore  # noqa: E402


def _json_write(path: Path, value) -> None:
    path.write_text(
        json.dumps(value, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def _wait_for(
    client: httpx.Client,
    predicate,
    *,
    process: subprocess.Popen,
    timeout: float,
):
    deadline = time.monotonic() + timeout
    last = None
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError(
                f"service exited before readiness (code {process.returncode})"
            )
        try:
            response = client.get("/health", timeout=3)
            last = response
            payload = response.json()
            if response.status_code in {200, 503} and predicate(payload):
                return payload
        except (httpx.HTTPError, ValueError):
            pass
        time.sleep(0.25)
    detail = (
        f" last_status={getattr(last, 'status_code', None)}"
        if last is not None else ""
    )
    raise TimeoutError(f"service did not reach the expected state.{detail}")


def run(
    *,
    environment_path: Path,
    port: int,
    source_secret_file: Path,
    report_root: Path | None,
) -> dict:
    if sys.platform != "win32":
        raise RuntimeError("live Windows acceptance must run on Windows")
    python = (environment_path / "python.exe").resolve()
    if not python.is_file():
        raise FileNotFoundError(f"clean environment Python not found: {python}")
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    run_root = (
        report_root.resolve()
        if report_root is not None
        else ROOT / "outputs" / f"clean-windows-live-{stamp}"
    )
    run_root.mkdir(parents=True, exist_ok=True)
    db_path = run_root / "chat.sqlite3"
    secret_path = run_root / "secrets.json"
    stdout_path = run_root / "server.stdout.log"
    stderr_path = run_root / "server.stderr.log"
    evidence_path = run_root / "verification.json"
    command = [
        str(python),
        "-B",
        str(ROOT / "src" / "backend.py"),
        "--db",
        str(db_path),
        "--secret-path",
        str(secret_path),
        "--port",
        str(port),
        "--provider",
        "opencode",
        "--speech-provider",
        "off",
        "--asr-provider",
        "off",
        "--memory-embedding-model",
        str(run_root / "missing-bge-model"),
    ]
    creationflags = getattr(subprocess, "CREATE_NO_WINDOW", 0)
    process = None
    evidence = {
        "schema_version": 1,
        "created_at": datetime.now(timezone.utc).isoformat(),
        "environment": str(environment_path.resolve()),
        "database": str(db_path),
        "server_started": False,
        "frontend_loaded": False,
        "app_version": None,
        "default_provider": None,
        "model_id": None,
        "api_key_configured": False,
        "character_created": False,
        "conversation_created": False,
        "first_chat": "not_run",
        "response_provider": None,
        "first_answer_chars": 0,
    }
    try:
        with stdout_path.open("w", encoding="utf-8") as stdout, (
            stderr_path.open("w", encoding="utf-8")
        ) as stderr:
            process = subprocess.Popen(
                command,
                cwd=ROOT,
                stdout=stdout,
                stderr=stderr,
                creationflags=creationflags,
            )
            with httpx.Client(
                base_url=f"http://127.0.0.1:{port}",
                trust_env=False,
                timeout=20,
            ) as client:
                health = _wait_for(
                    client,
                    lambda value: value.get("storage") == "sqlite",
                    process=process,
                    timeout=45,
                )
                evidence["server_started"] = True
                evidence["app_version"] = health.get("app_version")
                evidence["default_provider"] = health.get("default_provider")

                page = client.get("/ui/", timeout=10)
                if page.status_code != 200 or "拾语" not in page.text:
                    raise RuntimeError("front-end did not load correctly")
                evidence["frontend_loaded"] = True

                api_key = (
                    os.environ.get("SHIYU_OPENCODE_API_KEY")
                    or LocalSecretStore(source_secret_file).get(
                        "opencode_api_key"
                    )
                )
                if not api_key:
                    raise RuntimeError("no local OpenCode API key is available")
                configured = client.put(
                    "/settings/opencode-key",
                    json={"api_key": api_key},
                )
                configured.raise_for_status()
                if api_key in configured.text:
                    raise AssertionError("settings endpoint returned the API key")
                evidence["api_key_configured"] = True

                ready = _wait_for(
                    client,
                    lambda value: (
                        value.get("status") == "ready"
                        and value.get("default_provider") == "opencode"
                        and bool(value.get("providers", {})
                             .get("opencode", {})
                             .get("configured"))
                    ),
                    process=process,
                    timeout=20,
                )
                evidence["app_version"] = ready.get("app_version")
                evidence["model_id"] = ready.get("model_id")

                card = json.loads(
                    (
                        ROOT
                        / "evaluation"
                        / "characters"
                        / "full_character_fixture.json"
                    ).read_text(encoding="utf-8")
                )
                card["id"] = "custom-clean-live-" + uuid4().hex[:10]
                saved = client.post("/characters", json={"card": card})
                if saved.status_code != 201:
                    raise RuntimeError(
                        f"character creation failed: HTTP {saved.status_code}"
                    )
                evidence["character_created"] = True

                conversation = client.put(
                    f"/characters/{card['id']}/conversation",
                    params={"provider": "opencode"},
                )
                conversation.raise_for_status()
                evidence["conversation_created"] = True
                answer = client.post(
                    "/chat",
                    json={
                        "session_id": conversation.json()["id"],
                        "message": "你好，请用一句话介绍一下你自己。",
                        "max_new_tokens": 96,
                        "request_id": str(uuid4()),
                    },
                    timeout=180,
                )
                answer.raise_for_status()
                payload = answer.json()
                text = str(payload.get("answer") or "")
                if not text.strip():
                    raise RuntimeError("first live chat returned an empty answer")
                evidence["first_chat"] = "passed"
                evidence["response_provider"] = (
                    payload.get("metrics") or {}
                ).get("provider")
                evidence["first_answer_chars"] = len(text)
    except Exception as exc:
        evidence["error"] = f"{exc.__class__.__name__}: {exc}"
        raise
    finally:
        if process is not None and process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)
        # Do not retain an encrypted copy of the key in a temporary run.
        secret_path.unlink(missing_ok=True)
        (run_root / "local-secret.key").unlink(missing_ok=True)
        _json_write(evidence_path, evidence)
    print(
        json.dumps(
            {
                "evidence": str(evidence_path.resolve()),
                "app_version": evidence["app_version"],
                "first_chat": evidence["first_chat"],
                "response_provider": evidence["response_provider"],
            },
            ensure_ascii=False,
        )
    )
    return evidence


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--environment",
        type=Path,
        default=ROOT / "outputs" / "conda-api-repro",
    )
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument(
        "--source-secret-file",
        type=Path,
        default=ROOT / "outputs" / "secrets.json",
    )
    parser.add_argument("--report-root", type=Path)
    args = parser.parse_args()
    run(
        environment_path=args.environment,
        port=args.port,
        source_secret_file=args.source_secret_file,
        report_root=args.report_root,
    )


if __name__ == "__main__":
    main()
