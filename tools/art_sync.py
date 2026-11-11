#!/usr/bin/env python3
"""Syncs the artist workspace (art/) with the game assets folder.

  python3 tools/art_sync.py push   # art/ -> src/main/resources  (test your edits)
  python3 tools/art_sync.py pull   # src/main/resources -> art/  (refresh after code changes)

Mirrored paths (relative to art/ and assets/expeditions/):
  textures/  models/  blockstates/  lang/  sounds/  sounds.json
art/entity/*.bbmodel and art/palettes/ are workspace-only and never push.
"""
import os
import shutil
import sys

ROOT = os.path.join(os.path.dirname(__file__), "..")
ART = os.path.join(ROOT, "art")
ASSETS = os.path.join(ROOT, "src/main/resources/assets/expeditions")
ENTRIES = ["textures", "models", "blockstates", "lang", "sounds", "sounds.json"]


def sync(src_root, dst_root):
    n = 0
    for entry in ENTRIES:
        src = os.path.join(src_root, entry)
        dst = os.path.join(dst_root, entry)
        if not os.path.exists(src):
            continue
        for dirpath, _dirs, files in os.walk(src):
            for f in files:
                s = os.path.join(dirpath, f)
                rel = os.path.relpath(s, src)
                d = os.path.join(dst, rel)
                if (not os.path.exists(d)
                        or os.path.getmtime(s) > os.path.getmtime(d)
                        or open(s, "rb").read() != open(d, "rb").read()):
                    os.makedirs(os.path.dirname(d), exist_ok=True)
                    shutil.copy2(s, d)
                    n += 1
    return n


def main():
    if len(sys.argv) != 2 or sys.argv[1] not in ("push", "pull"):
        sys.exit(__doc__)
    if sys.argv[1] == "push":
        print(f"pushed {sync(ART, ASSETS)} changed file(s) into game assets")
    else:
        print(f"pulled {sync(ASSETS, ART)} changed file(s) into art/")


if __name__ == "__main__":
    main()
