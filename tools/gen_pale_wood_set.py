#!/usr/bin/env python3
"""Pale-wood building set generator — emits every asset/data JSON for the
derivative blocks (slab, stairs, fence, gate, door, trapdoor, button,
pressure plate) plus the door/trapdoor pixel art in the pale ramp.

Vanilla blockstate rotation tables are reproduced exactly (stairs, door,
button have non-obvious x/y transforms — wrong ones render rotated pieces).
Run: python3 tools/gen_pale_wood_set.py
"""
import json
import os

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..", "src/main/resources")
ASSETS = os.path.join(ROOT, "assets/expeditions")
DATA = os.path.join(ROOT, "data")
TEX = os.path.join(ASSETS, "textures")

PALETTE = {"base": "#cbbfaa", "mid": "#bcb09c", "light": "#dfd4bc",
           "hi": "#eee4cd", "seam": "#72695d", "void": "#3a352c"}

DIR_Y = {"east": 0, "south": 90, "west": 180, "north": 270}
DIR_Y_OPEN = {"north": 0, "east": 90, "south": 180, "west": 270}  # open leaf points back

# (half, shape) -> (model suffix, x base, y base) — rotations for facing=east;
# other facings add DIR_Y[facing] to y.
STAIR_TABLE = {
    ("bottom", "straight"): ("", 0, 0),
    ("bottom", "inner_left"): ("_inner", 0, 270),
    ("bottom", "inner_right"): ("_inner", 0, 0),
    ("bottom", "outer_left"): ("_outer", 0, 270),
    ("bottom", "outer_right"): ("_outer", 0, 0),
    ("top", "straight"): ("", 180, 0),
    ("top", "inner_left"): ("_inner", 180, 0),
    ("top", "inner_right"): ("_inner", 180, 90),
    ("top", "outer_left"): ("_outer", 180, 0),
    ("top", "outer_right"): ("_outer", 180, 90),
}


def w(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def model(parent, textures):
    return {"parent": f"minecraft:block/{parent}", "textures": textures}


# ------------------------------------------------------------------ textures
def planks_fill(d, frame="#8b8574"):
    """Plank face: frame + vertical boards with a seam and grain lines."""
    d.rectangle((0, 0, 15, 15), fill=frame)
    boards = [(1, 4, PALETTE["base"]), (5, 8, PALETTE["light"]),
              (9, 12, PALETTE["base"]), (13, 14, PALETTE["mid"])]
    for a, b, color in boards:
        d.rectangle((a, 1, b, 14), fill=color)
        d.line((a, 2, a, 13), fill=PALETTE["mid"])
    for x in (4, 8, 12):
        d.line((x, 1, x, 14), fill=PALETTE["seam"])


def gen_textures():
    # door top — window with two slats, sill at the waist
    top = Image.new("RGBA", (16, 16))
    d = ImageDraw.Draw(top)
    planks_fill(d)
    d.rectangle((5, 2, 10, 7), fill=PALETTE["void"], outline=PALETTE["seam"])
    d.line((7, 2, 7, 7), fill=PALETTE["seam"])
    d.line((9, 2, 9, 7), fill=PALETTE["seam"])
    d.line((5, 9, 10, 9), fill=PALETTE["seam"])  # rail under the window
    top.save(os.path.join(TEX, "block/pale_door_top.png"))

    # door bottom — rail + handle nub
    bot = Image.new("RGBA", (16, 16))
    d = ImageDraw.Draw(bot)
    planks_fill(d)
    d.line((1, 8, 14, 8), fill=PALETTE["seam"])
    d.point((12, 11)); d.point((11, 11))
    d.rectangle((11, 10, 12, 11), fill=PALETTE["void"], outline=PALETTE["hi"])
    bot.save(os.path.join(TEX, "block/pale_door_bottom.png"))

    # trapdoor — framed planks + center handle hole
    td = Image.new("RGBA", (16, 16))
    d = ImageDraw.Draw(td)
    planks_fill(d)
    d.rectangle((7, 7, 8, 8), fill=PALETTE["void"], outline=PALETTE["seam"])
    td.save(os.path.join(TEX, "block/pale_trapdoor.png"))

    # door item icon — tall door silhouette, window, handle
    it = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(it)
    d.rectangle((4, 1, 12, 15), outline=PALETTE["seam"], fill=PALETTE["base"])
    for x in (6, 8, 10):
        d.line((x, 2, x, 14), fill=PALETTE["mid"])
    d.rectangle((5, 2, 11, 7), fill=PALETTE["void"], outline=PALETTE["seam"])
    d.line((7, 2, 7, 7), fill=PALETTE["seam"]); d.line((9, 2, 9, 7), fill=PALETTE["seam"])
    d.point((5, 10), fill=PALETTE["seam"]); d.point((6, 10), fill=PALETTE["seam"])
    it.save(os.path.join(TEX, "item/pale_door.png"))


# --------------------------------------------------------------- blockstates
def stairs_bs(n):
    variants = {}
    for facing in ("east", "north", "south", "west"):
        for (half, shape), (suf, x, y) in STAIR_TABLE.items():
            y = (y + DIR_Y[facing]) % 360
            v = {"model": f"expeditions:block/{n}_stairs{suf}", "uvlock": True}
            if x: v["x"] = x
            if y: v["y"] = y
            variants[f"facing={facing},half={half},shape={shape}"] = v
    return {"variants": variants}


def door_bs(n):
    variants = {}
    for facing in ("east", "north", "south", "west"):
        for half in ("lower", "upper"):
            for hinge in ("left", "right"):
                for open_ in (False, True):
                    m = f"{n}_door_{'bottom' if half == 'lower' else 'top'}_{hinge}"
                    y = DIR_Y[facing]
                    if open_:
                        m += "_open"
                        y = (y + (90 if hinge == "left" else 270)) % 360
                    v = {"model": f"expeditions:block/{m}"}
                    if y: v["y"] = y
                    variants[f"facing={facing},half={half},hinge={hinge},open={str(open_).lower()}"] = v
    return {"variants": variants}


def trapdoor_bs(n):
    variants = {}
    for facing in ("east", "north", "south", "west"):
        for half in ("bottom", "top"):
            for open_ in (False, True):
                if open_:
                    v = {"model": f"expeditions:block/{n}_trapdoor_open"}
                    y = DIR_Y_OPEN[facing]
                    if y: v["y"] = y
                else:
                    v = {"model": f"expeditions:block/{n}_trapdoor_{half}"}
                variants[f"facing={facing},half={half},open={str(open_).lower()}"] = v
    return {"variants": variants}


def fence_gate_bs(n):
    variants = {}
    gate_y = {"south": 0, "west": 90, "north": 180, "east": 270}
    for facing in ("east", "north", "south", "west"):
        for wall in (False, True):
            for open_ in (False, True):
                m = n + "_fence_gate" + ("_wall" if wall else "") + ("_open" if open_ else "")
                v = {"model": f"expeditions:block/{m}", "uvlock": True}
                y = gate_y[facing]
                if y: v["y"] = y
                variants[f"facing={facing},in_wall={str(wall).lower()},open={str(open_).lower()}"] = v
    return {"variants": variants}


def fence_bs(n):
    side = f"expeditions:block/{n}_fence_side"
    return {"multipart": [
        {"apply": {"model": f"expeditions:block/{n}_fence_post"}},
        {"when": {"north": "true"}, "apply": {"model": side, "uvlock": True}},
        {"when": {"east": "true"}, "apply": {"model": side, "uvlock": True, "y": 90}},
        {"when": {"south": "true"}, "apply": {"model": side, "uvlock": True, "y": 180}},
        {"when": {"west": "true"}, "apply": {"model": side, "uvlock": True, "y": 270}},
    ]}


def button_bs(n):
    variants = {}
    for face in ("floor", "wall", "ceiling"):
        for facing in ("east", "north", "south", "west"):
            for powered in (False, True):
                v = {"model": f"expeditions:block/{n}_button" + ("_pressed" if powered else "")}
                if face == "floor":
                    y = DIR_Y_OPEN[facing]
                    if y: v["y"] = y
                elif face == "ceiling":
                    v["x"] = 180
                    y = (DIR_Y[facing] + 270) % 360
                    if y: v["y"] = y
                else:
                    v["x"] = 90; v["uvlock"] = True
                    y = DIR_Y_OPEN[facing]
                    if y: v["y"] = y
                variants[f"face={face},facing={facing},powered={str(powered).lower()}"] = v
    return {"variants": variants}


# ------------------------------------------------------------------- drivers
def gen_assets(n, tex):
    """All asset JSONs for wood set `n` built on block texture `tex`."""
    bs, bm, im = (os.path.join(ASSETS, d) for d in
                  ("blockstates", "models/block", "models/item"))
    planks = f"expeditions:block/{tex}"

    w(f"{bs}/{n}_slab.json", {"variants": {
        "type=bottom": {"model": f"expeditions:block/{n}_slab"},
        "type=top": {"model": f"expeditions:block/{n}_slab_top"},
        "type=double": {"model": "expeditions:block/pale_planks"}}})
    w(f"{bs}/{n}_stairs.json", stairs_bs(n))
    w(f"{bs}/{n}_fence.json", fence_bs(n))
    w(f"{bs}/{n}_fence_gate.json", fence_gate_bs(n))
    w(f"{bs}/{n}_door.json", door_bs(n))
    w(f"{bs}/{n}_trapdoor.json", trapdoor_bs(n))
    w(f"{bs}/{n}_button.json", button_bs(n))
    w(f"{bs}/{n}_pressure_plate.json", {"variants": {
        "powered=false": {"model": f"expeditions:block/{n}_pressure_plate"},
        "powered=true": {"model": f"expeditions:block/{n}_pressure_plate_down"}}})

    side3 = {"bottom": planks, "side": planks, "top": planks}
    w(f"{bm}/{n}_slab.json", model("slab", side3))
    w(f"{bm}/{n}_slab_top.json", model("slab_top", side3))
    w(f"{bm}/{n}_stairs.json", model("stairs", side3))
    w(f"{bm}/{n}_stairs_inner.json", model("inner_stairs", side3))
    w(f"{bm}/{n}_stairs_outer.json", model("outer_stairs", side3))
    w(f"{bm}/{n}_fence_post.json", model("fence_post", {"texture": planks}))
    w(f"{bm}/{n}_fence_side.json", model("fence_side", {"texture": planks}))
    w(f"{bm}/{n}_fence_inventory.json",
      model("fence_inventory", {"texture": planks}))
    for suf in ("", "_open", "_wall", "_wall_open"):
        w(f"{bm}/{n}_fence_gate{suf}.json",
          model(f"template_fence_gate{suf}", {"texture": planks}))
    for half in ("bottom", "top"):
        for hinge in ("left", "right"):
            for open_ in ("", "_open"):
                w(f"{bm}/{n}_door_{half}_{hinge}{open_}.json",
                  model(f"door_{half}_{hinge}{open_}",
                        {"bottom": f"expeditions:block/{n}_door_bottom",
                         "top": f"expeditions:block/{n}_door_top"}))
    w(f"{bm}/{n}_trapdoor_bottom.json",
      model("template_trapdoor_bottom", {"texture": f"expeditions:block/{n}_trapdoor"}))
    w(f"{bm}/{n}_trapdoor_open.json",
      model("template_trapdoor_open", {"texture": f"expeditions:block/{n}_trapdoor"}))
    w(f"{bm}/{n}_trapdoor_top.json",
      model("template_trapdoor_top", {"texture": f"expeditions:block/{n}_trapdoor"}))
    w(f"{bm}/{n}_button.json", model("button", {"texture": planks}))
    w(f"{bm}/{n}_button_pressed.json", model("button_pressed", {"texture": planks}))
    w(f"{bm}/{n}_button_inventory.json", model("button_inventory", {"texture": planks}))
    w(f"{bm}/{n}_pressure_plate.json", model("pressure_plate_up", {"texture": planks}))
    w(f"{bm}/{n}_pressure_plate_down.json", model("pressure_plate_down", {"texture": planks}))

    for k in ("slab", "stairs", "fence_gate", "pressure_plate"):
        w(f"{im}/{n}_{k}.json", {"parent": f"expeditions:block/{n}_{k}"})
    w(f"{im}/{n}_trapdoor.json", {"parent": f"expeditions:block/{n}_trapdoor_bottom"})
    w(f"{im}/{n}_fence.json", {"parent": f"expeditions:block/{n}_fence_inventory"})
    w(f"{im}/{n}_button.json", {"parent": f"expeditions:block/{n}_button_inventory"})
    w(f"{im}/{n}_door.json", {"parent": "minecraft:item/generated",
                              "textures": {"layer0": f"expeditions:item/{n}_door"}})


def gen_data(n):
    """Loot tables, recipes, vanilla tag entries for wood set `n`."""
    lt = os.path.join(DATA, "expeditions/loot_table/blocks")
    rc = os.path.join(DATA, "expeditions/recipe")
    blocks = ["slab", "stairs", "fence", "fence_gate", "door",
              "trapdoor", "button", "pressure_plate"]

    def self_drop(item, cond=None):
        e = {"type": "minecraft:item", "name": item,
             "conditions": [{"condition": "minecraft:survives_explosion"}]}
        if cond: e["conditions"].append(cond)
        return {"type": "minecraft:block",
                "pools": [{"rolls": 1, "entries": [e]}]}

    for k in blocks:
        item = f"expeditions:{n}_{k}"
        cond = None
        if k == "door":
            cond = {"condition": "minecraft:block_state_property",
                    "block": item, "properties": {"half": "lower"}}
        w(f"{lt}/{n}_{k}.json", self_drop(item, cond))

    P, S = "expeditions:pale_planks", "minecraft:stick"
    shaped = lambda pat, key, res, count: {
        "type": "minecraft:crafting_shaped", "category": "building",
        "pattern": pat,
        "key": {k: ({"item": v} if not v.startswith("#") else {"tag": v[1:]})
                for k, v in key.items()},
        "result": {"id": res, "count": count}}
    w(f"{rc}/{n}_slab.json", shaped(["PPP"], {"P": P}, f"expeditions:{n}_slab", 6))
    w(f"{rc}/{n}_stairs.json", shaped(["P  ", "PP ", "PPP"], {"P": P},
                                      f"expeditions:{n}_stairs", 4))
    w(f"{rc}/{n}_fence.json", shaped(["PSP", "PSP"], {"P": P, "S": S},
                                     f"expeditions:{n}_fence", 3))
    w(f"{rc}/{n}_fence_gate.json", shaped(["SPS", "SPS"], {"P": P, "S": S},
                                          f"expeditions:{n}_fence_gate", 1))
    w(f"{rc}/{n}_door.json", shaped(["PP", "PP", "PP"], {"P": P},
                                    f"expeditions:{n}_door", 3))
    w(f"{rc}/{n}_trapdoor.json", shaped(["PPP", "PPP"], {"P": P},
                                        f"expeditions:{n}_trapdoor", 2))
    w(f"{rc}/{n}_button.json", {"type": "minecraft:crafting_shapeless",
                                "category": "redstone", "ingredients": [{"item": P}],
                                "result": {"id": f"expeditions:{n}_button", "count": 1}})
    w(f"{rc}/{n}_pressure_plate.json", shaped(["PP"], {"P": P},
                                             f"expeditions:{n}_pressure_plate", 1))

    block_tags = {
        "mineable/axe": [f"expeditions:{n}_{k}" for k in blocks],
        "wooden_slabs": [f"expeditions:{n}_slab"],
        "wooden_stairs": [f"expeditions:{n}_stairs"],
        "wooden_fences": [f"expeditions:{n}_fence"],
        "fences": [f"expeditions:{n}_fence"],
        "fence_gates": [f"expeditions:{n}_fence_gate"],
        "wooden_doors": [f"expeditions:{n}_door"],
        "doors": [f"expeditions:{n}_door"],
        "wooden_trapdoors": [f"expeditions:{n}_trapdoor"],
        "trapdoors": [f"expeditions:{n}_trapdoor"],
        "wooden_buttons": [f"expeditions:{n}_button"],
        "buttons": [f"expeditions:{n}_button"],
        "wooden_pressure_plates": [f"expeditions:{n}_pressure_plate"],
        "pressure_plates": [f"expeditions:{n}_pressure_plate"],
    }
    for tag, values in block_tags.items():
        p = f"{DATA}/minecraft/tags/block/{tag}.json"
        if os.path.exists(p):
            d = json.load(open(p))
            d["values"] += [v for v in values if v not in d["values"]]
        else:
            d = {"replace": False, "values": values}
        w(p, d)
    item_tags = {t: v for t, v in block_tags.items() if t != "mineable/axe"}
    for tag, values in item_tags.items():
        p = f"{DATA}/minecraft/tags/item/{tag}.json"
        if os.path.exists(p):
            d = json.load(open(p))
            d["values"] += [v for v in values if v not in d["values"]]
        else:
            d = {"replace": False, "values": values}
        w(p, d)


def main():
    gen_textures()
    gen_assets("pale", "pale_planks")
    gen_data("pale")
    print("pale wood set: 8 blocks, textures + assets + data written")


if __name__ == "__main__":
    main()
