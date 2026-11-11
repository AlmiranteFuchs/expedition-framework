#!/usr/bin/env python3
"""Exports the Rootbound Guardian's code-defined mesh to a Blockbench
project an artist can open, repose and redesign.

The in-game model lives in `GuardianModel.createBodyLayer()` — Java
cube lists, not a data file. This script MIRRORS that cube list; if the
Java model changes, update CUBES below to match.

bbmodel conventions used:
  - `modded_entity` format, per-cube `uv_offset` (MC texOffs semantics)
  - every group sits at root level with its pivot in ABSOLUTE model
    space, so element `from`/`to` stay absolute with zero ambiguity.
    Rib/crown parts are nested parts in code — re-parent them under
    their bone in Blockbench if you want hierarchical posing.
  - MC part space: negative Y is up. Blockbench shows the same space.
"""
import base64
import json
import os
import uuid

ROOT = os.path.join(os.path.dirname(__file__), "..")
TEX = os.path.join(ROOT,
                   "src/main/resources/assets/expeditions/textures/entity")
OUT = os.path.join(ROOT, "art/entity")

# (name, texOffs_u/v, [from], [to], absolute_pivot, rotation_deg)
# from/to are the cube's coords RELATIVE to its code part's origin;
# absolute_pivot is that part's origin summed down the chain
# (body/head offsets included for nested ribs and crowns).
CUBES = [
    ("body",        (0, 0),  (-6, -16, -3), (6, 2, 3),   (0, -4, 0),   (0, 0, 0)),
    ("body_skirt",  (0, 44), (-7, -14, -4), (7, -11, 4), (0, -4, 0),   (0, 0, 0)),
    ("body_sternum",(0, 56), (-2, -10, -4), (2, -4, -3), (0, -4, 0),   (0, 0, 0)),
    ("root_rib",    (54, 16),(-1, 0, -1),   (1, 8, 1),   (-4, -12, -3),(0, 0, -22.92)),
    ("head",        (0, 24), (-5, -10, -4), (5, 0, 4),   (0, -20, 0),  (0, 0, 0)),
    ("crown_left",  (54, 0), (-1, -10, -1), (1, 0, 1),   (-4, -28, 0), (0, 0, -31.51)),
    ("crown_right", (54, 0), (-1, -11, -1), (1, 0, 1),   (4, -29, 1),  (8.59, 0, 27.50)),
    ("crown_center",(54, 0), (-1, -8, -1),  (1, 0, 1),   (0, -30, 2),  (-17.19, 0, 6.88)),
    ("left_arm",    (36, 0), (-2, -2, -2),  (2, 23, 2),  (-8, -18, 0), (0, 0, 0)),
    ("left_claw",   (54, 16),(-2, 23, -2),  (0, 31, 0),  (-8, -18, 0), (0, 0, 0)),
    ("right_arm",   (36, 0), (-2, -2, -2),  (2, 23, 2),  (8, -17, 0),  (0, 0, 0)),
    ("right_claw",  (54, 16),(0, 23, -2),   (2, 31, 0),  (8, -17, 0),  (0, 0, 0)),
    ("left_leg",    (36, 30),(-2, 0, -2),   (2, 26, 2),  (-3, -2, 0),  (0, 0, 0)),
    ("right_leg",   (36, 30),(-2, 0, -2),   (2, 26, 2),  (3, -2, 0),   (0, 0, 0)),
]

# Code part name -> which bbmodel bone the element belongs to
BONE_OF = {
    "body": "body", "body_skirt": "body", "body_sternum": "body",
    "root_rib": "root_rib", "head": "head", "crown_left": "crown_left",
    "crown_right": "crown_right", "crown_center": "crown_center",
    "left_arm": "left_arm", "left_claw": "left_arm",
    "right_arm": "right_arm", "right_claw": "right_arm",
    "left_leg": "left_leg", "right_leg": "right_leg",
}
BONES = {}  # name -> {"pivot": abs, "rotation": deg}


def main():
    elements = []
    bone_children = {}
    for (name, uv, frm, to, pivot, rot) in CUBES:
        abs_from = [pivot[0] + frm[0], pivot[1] + frm[1], pivot[2] + frm[2]]
        abs_to = [pivot[0] + to[0], pivot[1] + to[1], pivot[2] + to[2]]
        eid = str(uuid.uuid4())
        elements.append({
            "name": name, "box_uv": False, "rescale": False, "locked": False,
            "from": abs_from, "to": abs_to, "autouv": 0, "color": 0,
            "uv_offset": list(uv), "origin": list(pivot),
            "faces": {
                f: {"uv": [0, 0, 0, 0], "texture": 0}
                for f in ("north", "east", "south", "west", "up", "down")
            },
            "type": "cube", "uuid": eid,
        })
        bone = BONE_OF[name]
        bone_children.setdefault(bone, []).append(eid)
        BONES.setdefault(bone, {"pivot": pivot, "rotation": rot})

    outliner = []
    for bone, info in BONES.items():
        outliner.append({
            "name": bone, "origin": list(info["pivot"]),
            "rotation": list(info["rotation"]),
            "uuid": str(uuid.uuid4()), "export": True, "mirror_uv": False,
            "isOpen": True, "locked": False, "visibility": True,
            "autouv": 0, "selected": False,
            "children": bone_children.get(bone, []),
        })

    with open(os.path.join(TEX, "rootbound_guardian.png"), "rb") as f:
        tex_b64 = base64.b64encode(f.read()).decode()

    model = {
        "meta": {"format_version": "4.10", "model_format": "modded_entity",
                 "box_uv": False},
        "name": "rootbound_guardian",
        "model_identifier": "rootbound_guardian",
        "resolution": {"width": 64, "height": 64},
        "elements": elements,
        "outliner": outliner,
        "textures": [{
            "name": "rootbound_guardian.png", "folder": "entity",
            "namespace": "expeditions", "id": "0", "particle": False,
            "relative_path": "textures/entity/rootbound_guardian.png",
            "source": "data:image/png;base64," + tex_b64,
            "uuid": str(uuid.uuid4()), "saved": True,
        }],
    }
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, "rootbound_guardian.bbmodel")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(model, f, indent=2)
    print("wrote", path)


if __name__ == "__main__":
    main()
