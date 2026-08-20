#!/usr/bin/env python3
"""Download every installer and archive needed to set this project up with no internet.

    python tools/fetch_offline_sources.py C:\\pronunciation-offline

Run this on a machine WITH internet. It fetches the original artifacts - not a copy of an
already-installed toolchain - so the target machine installs from scratch offline.

Two things genuinely cannot be downloaded as an archive, and both are copied instead, each
with a note in the folder explaining why:

  gradle-cache/  Gradle resolves AndroidX, Material and ONNX Runtime from Maven at build
                 time. There is no published bundle of that set. The only way to obtain it
                 is to run one successful build, then copy ~/.gradle/caches/modules-2.

  app-assets/    The speech models and lexicons are generated, not downloaded. Regenerating
                 them needs the HuggingFace weights, which are fetched here too - but the
                 finished assets are 459 MB against 2.8 GB of raw weights, so shipping them
                 saves the target machine both the space and the export step.

Resumable: anything already present with a plausible size is skipped.
"""

from __future__ import annotations

import shutil
import subprocess
import sys
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

ANDROID_REPO = "https://dl.google.com/android/repository/"
SYS_IMG_REPO = ANDROID_REPO + "sys-img/google_apis/"

# Straight downloads: (folder, filename, url)
DIRECT = [
    ("01-jdk", "OpenJDK17-jdk_x64_windows_hotspot.zip",
     "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse"),
    ("02-android-sdk", "commandlinetools-win.zip",
     ANDROID_REPO + "commandlinetools-win-11076708_latest.zip"),
    ("03-gradle", "gradle-8.9-bin.zip",
     "https://services.gradle.org/distributions/gradle-8.9-bin.zip"),
    ("04-python", "python-3.12.7-embed-amd64.zip",
     "https://www.python.org/ftp/python/3.12.7/python-3.12.7-embed-amd64.zip"),
    ("04-python", "get-pip.py", "https://bootstrap.pypa.io/get-pip.py"),
    ("06-espeak", "espeak-ng.msi", None),          # resolved from the GitHub release
    ("05-git", "MinGit-64-bit.zip", None),         # resolved from the GitHub release
    ("07-data", "cmudict.dict",
     "https://raw.githubusercontent.com/cmusphinx/cmudict/master/cmudict.dict"),
    ("07-data", "google-10000-english.txt",
     "https://raw.githubusercontent.com/first20hours/google-10000-english/master/"
     "google-10000-english-usa-no-swears.txt"),
]

# SDK packages, by their sdkmanager path.
#
# build-tools 34.0.0 is not a typo alongside 35.0.0. AGP pulls it in as well, silently, and a
# build without it fails with "Failed to find Build Tools revision 34.0.0" - which on an
# air-gapped machine is unfixable. Found by installing this bundle into a clean location and
# building against it.
SDK_PACKAGES = ["platform-tools", "platforms;android-35",
                "build-tools;35.0.0", "build-tools;34.0.0", "emulator"]
SYSTEM_IMAGES = ["system-images;android-26;google_apis;x86_64",
                 "system-images;android-35;google_apis;x86_64"]

# Everything tools/*.py needs. CPU-only torch: the export runs on CPU and the CUDA build is
# several GB larger.
WHEELS = ["transformers", "onnx", "onnxruntime", "onnxscript", "msvc-runtime", "pypinyin"]
TORCH_INDEX = "https://download.pytorch.org/whl/cpu"


def log(m: str) -> None:
    print(f"[fetch] {m}", flush=True)


def get(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "offline-fetch"})
    with urllib.request.urlopen(req) as r:
        return r.read()


def download(url: str, dest: Path, minimum: int = 1024) -> None:
    if dest.exists() and dest.stat().st_size >= minimum:
        log(f"   have {dest.name} ({dest.stat().st_size / 1e6:.0f} MB)")
        return
    dest.parent.mkdir(parents=True, exist_ok=True)
    log(f"   {dest.name} …")
    req = urllib.request.Request(url, headers={"User-Agent": "offline-fetch"})
    with urllib.request.urlopen(req) as r, dest.open("wb") as fh:
        shutil.copyfileobj(r, fh)
    log(f"   got {dest.name} ({dest.stat().st_size / 1e6:.0f} MB)")


def github_asset(repo: str, pattern: str) -> str:
    import json
    data = json.loads(get(f"https://api.github.com/repos/{repo}/releases/latest"))
    for a in data["assets"]:
        if pattern in a["name"]:
            return a["browser_download_url"]
    raise RuntimeError(f"no asset matching {pattern!r} in {repo}")


def resolve_sdk(paths: list[str], xml_url: str, base: str) -> list[tuple[str, str]]:
    """@return (package path, absolute url) for the Windows archive of each package."""
    root = ET.fromstring(get(xml_url))
    found: list[tuple[str, str]] = []
    seen: set[str] = set()

    for pkg in root.iter("remotePackage"):
        path = pkg.get("path")
        # Google lists some packages once per release channel - "emulator" appears several
        # times - so take the first entry per path or the bundle gains a 456 MB duplicate.
        if path not in paths or path in seen:
            continue
        for archive in pkg.iter("archive"):
            host = archive.findtext("host-os")
            if host not in (None, "windows"):
                continue
            url = archive.findtext("complete/url")
            if url:
                found.append((path, base + url))
                seen.add(path)
                break

    missing = set(paths) - {p for p, _ in found}
    if missing:
        log(f"   WARNING: no Windows archive found for {sorted(missing)}")
    return found


def main() -> None:
    if len(sys.argv) < 2:
        sys.exit("usage: python tools/fetch_offline_sources.py <destination folder>")
    dest = Path(sys.argv[1])
    dest.mkdir(parents=True, exist_ok=True)
    log(f"destination: {dest}")

    # --- 1. direct archives -------------------------------------------------------------
    log("Direct downloads")
    for folder, name, url in DIRECT:
        if url is None:
            if name.startswith("espeak"):
                url = github_asset("espeak-ng/espeak-ng", "espeak-ng.msi")
            else:
                url = github_asset("git-for-windows/git", "MinGit")
        download(url, dest / folder / name)

    # --- 2. Android SDK packages --------------------------------------------------------
    log("Android SDK packages (resolving Google's repository index)")
    for path, url in resolve_sdk(SDK_PACKAGES, ANDROID_REPO + "repository2-3.xml", ANDROID_REPO):
        download(url, dest / "02-android-sdk" / f"{path.replace(';', '_')}__{Path(url).name}")

    log("System images")
    for path, url in resolve_sdk(SYSTEM_IMAGES, SYS_IMG_REPO + "sys-img2-3.xml", SYS_IMG_REPO):
        download(url, dest / "02-android-sdk" / f"{path.replace(';', '_')}__{Path(url).name}")

    # --- 3. Python wheels ---------------------------------------------------------------
    log("Python wheels (pip download, not install)")
    wheels = dest / "04-python" / "wheels"
    wheels.mkdir(parents=True, exist_ok=True)
    python = sys.executable
    if not list(wheels.glob("torch-*.whl")):
        subprocess.run([python, "-m", "pip", "download", "-d", str(wheels),
                        "torch", "--index-url", TORCH_INDEX], check=True)
    if not list(wheels.glob("transformers-*")):
        subprocess.run([python, "-m", "pip", "download", "-d", str(wheels), *WHEELS], check=True)
    log(f"   {len(list(wheels.glob('*')))} wheel files")

    # --- 4. the two things that cannot be downloaded ------------------------------------
    home = Path.home()

    cache_src = home / ".gradle" / "caches" / "modules-2"
    cache_dst = dest / "08-gradle-cache" / "modules-2"
    if cache_src.exists() and not cache_dst.exists():
        log("Gradle dependency cache (copied — no published archive exists)")
        shutil.copytree(cache_src, cache_dst,
                        ignore=shutil.ignore_patterns("*.lock"), dirs_exist_ok=True)
    (dest / "08-gradle-cache" / "WHY-THIS-IS-COPIED.txt").write_text(
        "Gradle resolves AndroidX, Material and ONNX Runtime from Maven at build time.\n"
        "There is no published bundle of that dependency set, so it cannot be downloaded as\n"
        "an archive. The only way to obtain it is to run one successful build while online\n"
        "and copy ~/.gradle/caches/modules-2, which is what this folder is.\n\n"
        "restore: copy modules-2 into %USERPROFILE%\\.gradle\\caches\\\n",
        encoding="utf-8")

    assets_src = ROOT / "app" / "src" / "main" / "assets"
    assets_dst = dest / "09-app-assets"
    if assets_src.exists():
        log("Generated model and lexicon assets (copied — these are build outputs)")
        assets_dst.mkdir(parents=True, exist_ok=True)
        for f in assets_src.iterdir():
            if f.is_file() and not (assets_dst / f.name).exists():
                shutil.copy2(f, assets_dst / f.name)
    (assets_dst / "WHY-THIS-IS-COPIED.txt").write_text(
        "The speech models and lexicons are generated, not downloaded.\n\n"
        "You could rebuild them offline from the HuggingFace weights in 10-hf-models plus\n"
        "espeak and the wheels, but the finished assets are ~459 MB against ~2.8 GB of raw\n"
        "weights, and rebuilding takes 20 minutes.\n\n"
        "restore: copy every file here into <project>\\app\\src\\main\\assets\\\n",
        encoding="utf-8")

    # --- 5. HuggingFace weights, for regenerating models offline ------------------------
    hf_src = home / ".cache" / "huggingface"
    hf_dst = dest / "10-hf-models"
    if hf_src.exists() and not hf_dst.exists():
        log("HuggingFace weights (only needed to regenerate the models)")
        shutil.copytree(hf_src, hf_dst, dirs_exist_ok=True)

    total = sum(f.stat().st_size for f in dest.rglob("*") if f.is_file())
    log(f"done: {total / 1e9:.1f} GB in {dest}")


if __name__ == "__main__":
    main()
