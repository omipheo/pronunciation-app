# Uploading the offline bundle to GitHub

`C:\pronunciation-offline-zips` — **6.11 GB across 76 files**, one archive per component,
split into 95 MB parts where needed.

| Archive | Size | Parts | Needed for |
|---|---|---|---|
| `01-jdk` | 178 MB | 2 | building — required |
| `02-android-sdk` | 3,316 MB | 35 | building — **see the licence note** |
| `03-gradle` | 130 MB | 2 | building — required |
| `04-python` | 199 MB | 3 | regenerating models and content |
| `05-git` | 37 MB | 1 | optional |
| `06-espeak` | 12 MB | 1 | regenerating the Chinese lexicon |
| `07-data` | 1 MB | 1 | regenerating English content |
| `08-gradle-cache` | 225 MB | 3 | building — required, and cannot be re-downloaded |
| `09-app-assets` | 316 MB | 4 | running — required, the speech models |
| `10-hf-models` | 1,845 MB | 20 | only to rebuild the models from scratch |

## Do not upload `02-android-sdk` to a public repository

Google's Android SDK Terms prohibit redistributing the SDK or any part of it. That archive is
the command-line tools, platform, build-tools, platform-tools, emulator and system images —
3.3 GB, and more than half the bundle.

Leaving it out costs whoever restores the bundle one command, run once with internet:

```powershell
python tools/fetch_offline_sources.py C:\pronunciation-offline
```

That re-fetches the SDK (and anything else missing) straight from Google. Everything else here
is freely redistributable: Temurin is GPL+CE, Gradle Apache 2.0, Python PSF, espeak-ng GPL,
MinGit GPL.

**`10-hf-models` is also worth skipping** — 1.8 GB that only matters if you want to re-export
the speech models, and the finished models are already in `09-app-assets`. They re-download
from HuggingFace when needed.

Without those two: **~890 MB across 17 files**, all freely redistributable.

## Where to put them

95 MB parts are valid in both places, so the choice is yours:

**Release assets (recommended)** — they don't bloat the repository or its clone size, and the
per-file limit is 2 GB, so the parts are comfortably under it.

```powershell
gh release create offline-bundle-v1 C:\pronunciation-offline-zips\* `
    --title "Offline development bundle" --notes "See tools/UPLOAD-GUIDE.md"
```

**Committed into the repository** — works, because every part is under GitHub's hard 100 MB
per-file limit. But the payload lands in git history permanently and cannot be removed without
rewriting it, and GitHub flags repositories past 5 GB.

## Restoring

Download every part, then in that folder:

```powershell
.\rejoin.ps1                                                   # reassembles and verifies SHA-256
Get-ChildItem *.zip | ForEach-Object { Expand-Archive $_.FullName -DestinationPath . -Force }
.\offline-install.ps1 -ProjectDir C:\pronunciation-app
```

`rejoin.ps1` checks every reassembled archive against `CHECKSUMS.txt` and refuses a file whose
hash does not match, so a missing or truncated part is caught before it becomes a confusing
build failure rather than after.
