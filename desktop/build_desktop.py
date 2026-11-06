"""Build a self-contained Windows onedir distribution.

Model weights, API keys, databases, and user images are never bundled. The
result should be distributed as the complete ``Shiyu-0.18.3-Windows-x64``
folder (or its zip), not as the loose executable.
"""

from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
DIST = ROOT / "release" / "windows"
BUILD = ROOT / "build" / "desktop"
NAME = "Shiyu-0.18.3-Windows-x64"


def run(command: list[str], *, cwd: Path = ROOT) -> None:
    print("+", " ".join(command), flush=True)
    subprocess.run(command, cwd=cwd, check=True)


def find_pnpm() -> str | None:
    for name in ("pnpm.cmd", "pnpm"):
        found = shutil.which(name)
        if found:
            return found
    return None


def build_frontend() -> None:
    pnpm = find_pnpm()
    if not pnpm:
        if not (ROOT / "web" / "react-dist" / "index.html").is_file():
            raise RuntimeError("缺少 pnpm，且仓库中没有已构建的 web/react-dist")
        print("pnpm not found; reusing web/react-dist", flush=True)
        return
    run([pnpm, "--dir", "frontend", "build"])


def build(*, rebuild_frontend: bool = True, console: bool = False) -> Path:
    if rebuild_frontend:
        build_frontend()
    elif not (ROOT / "web" / "react-dist" / "index.html").is_file():
        raise RuntimeError("仓库中没有可打包的 web/react-dist")
    name = NAME + ("-debug" if console else "")
    for folder in (BUILD, DIST / name):
        if folder.exists():
            shutil.rmtree(folder)
    DIST.mkdir(parents=True, exist_ok=True)
    separator = ";" if os.name == "nt" else ":"
    command = [
        sys.executable,
        "-m",
        "PyInstaller",
        "--noconfirm",
        "--clean",
        "--console" if console else "--windowed",
        "--icon",
        str(ROOT / "web" / "brand-icon.ico"),
        "--onedir",
        "--name",
        name,
        "--distpath",
        str(DIST),
        "--workpath",
        str(BUILD),
        "--specpath",
        str(BUILD),
        "--paths",
        str(ROOT / "src"),
        "--add-data",
        f"{ROOT / 'web'}{separator}web",
        "--add-data",
        f"{ROOT / 'src'}{separator}src",
        "--add-data",
        f"{ROOT / 'outputs' / 'secret-runtime'}{separator}outputs/secret-runtime",
        "--collect-all",
        "webview",
        "--hidden-import",
        "uvicorn.logging",
        "--hidden-import",
        "uvicorn.loops.auto",
        "--hidden-import",
        "uvicorn.protocols.http.auto",
        "--hidden-import",
        "uvicorn.protocols.websockets.auto",
        "--hidden-import",
        "uvicorn.lifespan.on",
        "--exclude-module",
        "torch",
        "--exclude-module",
        "transformers",
        "--exclude-module",
        "peft",
        "--exclude-module",
        "diffusers",
        "--exclude-module",
        "accelerate",
        "--exclude-module",
        "gradio",
        "--exclude-module",
        "pandas",
        "--exclude-module",
        "matplotlib",
        str(ROOT / "desktop" / "shiyu_desktop.py"),
    ]
    run(command)
    target = DIST / name
    readme = ROOT / "docs" / "Windows桌面版安装与使用.md"
    if readme.is_file():
        shutil.copy2(readme, target / "使用说明.md")
    return target


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--no-frontend", action="store_true")
    parser.add_argument("--console", action="store_true")
    args = parser.parse_args()
    target = build(
        rebuild_frontend=not args.no_frontend,
        console=args.console,
    )
    print(target)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
