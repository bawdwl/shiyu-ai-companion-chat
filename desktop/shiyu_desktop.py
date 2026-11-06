"""Windows desktop shell for Shiyu.

The desktop application runs the same FastAPI + React product as the web
version in a private local server and displays it in Microsoft Edge WebView2.
Business logic stays in ``src/backend.py``; this module only owns the process
lifecycle and Windows data locations.
"""

from __future__ import annotations

import logging
import os
import sys
import threading
import time
import traceback
import argparse
from pathlib import Path


APP_NAME = "Shiyu"
APP_VERSION = "0.18.0"


def application_root() -> Path:
    if getattr(sys, "frozen", False):
        return Path(getattr(sys, "_MEIPASS", Path(sys.executable).resolve().parent))
    return Path(__file__).resolve().parent.parent


ROOT = application_root()
SOURCE = ROOT / "src"
if str(SOURCE) not in sys.path:
    sys.path.insert(0, str(SOURCE))

from backend import create_app  # noqa: E402


def data_root() -> Path:
    configured = os.environ.get("SHIYU_DATA_DIR")
    if configured:
        return Path(configured).expanduser().resolve()
    local = os.environ.get("LOCALAPPDATA")
    if local:
        return Path(local) / APP_NAME
    return Path.home() / ".shiyu"


DATA = data_root()
LOGS = DATA / "logs"
MODELS = DATA / "models"
IMAGES = DATA / "images"
for folder in (DATA, LOGS, MODELS, IMAGES):
    folder.mkdir(parents=True, exist_ok=True)

LOG_FILE = LOGS / "desktop.log"
log_handlers: list[logging.Handler] = []
if sys.stderr is not None:
    log_handlers.append(logging.StreamHandler())
try:
    log_handlers.append(logging.FileHandler(LOG_FILE, encoding="utf-8"))
except OSError:
    # A second instance or an antivirus scanner may briefly hold the log file.
    # Never prevent the application window from starting because logging failed.
    pass
if not log_handlers:
    log_handlers.append(logging.NullHandler())
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    handlers=log_handlers,
)
logger = logging.getLogger("shiyu.desktop")


class DesktopService:
    def __init__(self) -> None:
        import uvicorn

        self.uvicorn = uvicorn
        self.error: BaseException | None = None
        self.server = None
        self.thread: threading.Thread | None = None
        self.port = 0

    def start(self, timeout: float = 25.0) -> str:
        if sys.stdout is None:
            sys.stdout = open(os.devnull, "w", encoding="utf-8")
        if sys.stderr is None:
            sys.stderr = open(os.devnull, "w", encoding="utf-8")
        database = DATA / "chat.sqlite3"
        secret_store = DATA / "secrets.json"
        structured_memory = DATA / "structured-memory.sqlite3"
        app = create_app(
            db_path=str(database),
            default_provider="deepseek",
            secret_path=str(secret_store),
            structured_memory=True,
            structured_memory_db_path=str(structured_memory),
            auto_memory_worker_enabled=True,
            memory_job_ready_seconds=0.5,
            seed_product_character_cards=True,
            image_model_path=str(MODELS / "stable-diffusion-v1-5"),
        )
        config = self.uvicorn.Config(
            app,
            host="127.0.0.1",
            port=0,
            log_level="info",
            access_log=False,
            log_config=None,
        )
        self.server = self.uvicorn.Server(config)

        def run() -> None:
            try:
                self.server.run()
            except BaseException as exc:  # surfaced in the desktop error page
                self.error = exc
                logger.exception("Desktop server stopped unexpectedly")

        self.thread = threading.Thread(target=run, name="shiyu-fastapi", daemon=True)
        self.thread.start()
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if self.error is not None:
                raise RuntimeError("本机服务启动失败") from self.error
            if self.server.started and self.server.servers:
                self.port = self.server.servers[0].sockets[0].getsockname()[1]
                return f"http://127.0.0.1:{self.port}/ui/"
            if self.thread and not self.thread.is_alive():
                raise RuntimeError("本机服务在启动完成前退出")
            time.sleep(0.08)
        raise TimeoutError(f"本机服务启动超时，请查看 {LOG_FILE}")

    def stop(self) -> None:
        if self.server is not None:
            self.server.should_exit = True
        if self.thread is not None:
            self.thread.join(timeout=8)


def show_fatal_error(title: str, message: str) -> None:
    logger.error("%s\n%s", title, message)
    try:
        import tkinter as tk
        from tkinter import messagebox

        root = tk.Tk()
        root.withdraw()
        messagebox.showerror(title, message)
        root.destroy()
    except Exception:
        print(f"{title}\n{message}", file=sys.stderr)


def main() -> int:
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--service-only", action="store_true")
    parser.add_argument("--port-file")
    args, _ = parser.parse_known_args()
    service = DesktopService()
    try:
        url = service.start()
        logger.info("Serving %s at %s", APP_NAME, url)
        if args.port_file:
            Path(args.port_file).write_text(url, encoding="utf-8")
        if args.service_only:
            print(url, flush=True)
            while True:
                time.sleep(1)
        import webview

        window = webview.create_window(
            f"拾语 {APP_VERSION}",
            url,
            width=1460,
            height=940,
            min_size=(900, 640),
            background_color="#F4F1E8",
            text_select=True,
        )
        window.events.closed += service.stop
        webview.start(
            gui="edgechromium",
            debug=False,
            icon=str(ROOT / "web" / "brand-icon.ico"),
        )
        return 0
    except Exception as exc:
        detail = (
            f"{exc}\n\n数据目录：{DATA}\n日志：{LOG_FILE}\n\n"
            "请确认 Windows 10/11 已安装 Microsoft Edge WebView2 Runtime。\n\n"
            + traceback.format_exc(limit=8)
        )
        show_fatal_error("拾语无法启动", detail)
        return 1
    finally:
        service.stop()


if __name__ == "__main__":
    raise SystemExit(main())
