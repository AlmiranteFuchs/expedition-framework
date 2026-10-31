#!/usr/bin/env python3
"""Procedural texture generator for the expedition-framework asset set.

Every texture is 16x16, vanilla-plus resolution. Style rules:
  - one light source, top-left
  - muted heartwood/bone ramps and warm ember accents
  - cluster shading and ordered (Bayer) dithering, never raw noise
  - dark colored outlines, not pure black
Run: python3 tools/gen_textures.py   (writes into src/main/resources)
"""
import math
import json
import os
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..",
                    "src/main/resources/assets/expeditions/textures")
W = H = 16

# ---------------------------------------------------------------- ramps
# Bone — bleached calcium, the Charter's tissue-tech and the colony's shell.
BONE = [(58, 48, 38, 255), (110, 94, 74, 255), (160, 140, 110, 255),
        (206, 186, 150, 255), (236, 220, 184, 255), (250, 242, 216, 255)]
# Flesh — membrane tissue, cooler and flatter than bone.
FLESH = [(72, 62, 58, 255), (118, 104, 98, 255), (162, 148, 138, 255),
         (202, 190, 176, 255), (230, 222, 206, 255), (246, 240, 226, 255)]
# Ember — the organism's heat. Index 0 is near-black cavity, 5 is the lub.
EMBER = [(34, 18, 10, 255), (122, 36, 14, 255), (217, 72, 15, 255),
         (240, 138, 32, 255), (255, 196, 61, 255), (255, 233, 122, 255)]
# Leather — Charter gear.
LEATHER = [(42, 26, 16, 255), (80, 50, 30, 255), (122, 80, 48, 255),
           (158, 110, 66, 255), (196, 146, 92, 255), (222, 176, 118, 255)]
OUTLINE = (38, 30, 24, 255)
DARK = (26, 20, 16, 255)

BAYER = ((0, 2), (3, 1))


def ramp_at(ramp, v, x, y):
    """Continuous 0..1 field -> ramp color with 2x2 ordered dither."""
    v = max(0.0, min(1.0, v))
    t = v * (len(ramp) - 1)
    i = int(t)
    if (t - i) * 4.0 > BAYER[y % 2][x % 2] + 0.5:
        i += 1
    return ramp[min(i, len(ramp) - 1)]


def canvas():
    return Image.new("RGBA", (W, H), (0, 0, 0, 0))


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(4))


def put(img, x, y, c):
    if 0 <= x < W and 0 <= y < H:
        img.putpixel((x, y), c)


def dist(x1, y1, x2, y2):
    return math.hypot(x1 - x2, y1 - y2)


def save(img, rel):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    print("wrote", rel)


def _heartwood(name):
    import runpy
    painters = runpy.run_path(os.path.join(os.path.dirname(__file__), "heartwood_art.py"))
    painters[name](ROOT)


# ---------------------------------------------------------------- blocks

def gen_heart():
    _heartwood("gen_heart")


def gen_layer():
    _heartwood("gen_layer")


def gen_terminal():
    _heartwood("gen_terminal")


def gen_checkpoint():
    _heartwood("gen_checkpoint")


def gen_exit():
    _heartwood("gen_exit")


def gen_portal():
    _heartwood("gen_portal")


def gen_device():
    _heartwood("gen_device")


# ---------------------------------------------------------------- items


def gen_compass():
    """A dark wooden heart-cage, pale root ribs and a living ember needle.

    All 32 directional models are ours: vanilla angle overrides must never
    replace the carved housing with the ordinary red-and-silver compass.
    """
    bark = [(27, 26, 24, 255), (48, 46, 40, 255), (79, 77, 64, 255),
            (123, 122, 101, 255), (173, 173, 145, 255), (220, 219, 190, 255)]
    housing = canvas()
    cx, cy = 7.5, 8.0
    for y in range(H):
        for x in range(W):
            d = dist(x, y, cx, cy)
            # A pale heartwood shell with a dark recessed dial. The outer
            # edge is chipped; the inner lip gives the instrument depth.
            if d > 6.8 or abs(x - cx) + abs(y - cy) > 9.6:
                continue
            if d >= 4.6:
                v = 0.64 - (x - cx) * 0.045 - (y - cy) * 0.048
                if d > 6.2:
                    v -= 0.28
                elif d < 5.2:
                    v -= 0.22
                color = ramp_at(bark, v, x, y)
            else:
                color = mix(DARK, bark[1], max(0, 0.55 - d * 0.09))
            put(housing, x, y, color)
    # A split suspension loop and pale roots bind the carved housing.
    for x, y, value in ((6, 0, 2), (7, 0, 3), (8, 0, 2),
                        (5, 1, 2), (9, 1, 1), (5, 2, 3), (9, 2, 1)):
        put(housing, x, y, bark[value])
    roots = [[(3, 3), (3, 4), (4, 5)],
             [(12, 3), (11, 4)],
             [(1, 10), (2, 10), (3, 9)],
             [(12, 12), (11, 11)],
             [(5, 14), (6, 13), (6, 12)]]
    for root in roots:
        for x, y in root:
            put(housing, x, y, bark[5] if x + y < 13 else bark[3])
            put(housing, x + 1, y, bark[2] if x + y < 13 else bark[1])
    # Long bark fissures and tiny dark bindings keep the surface legible
    # as worn wood, with no random noise or ornament competing with the needle.
    for x, y in ((4, 3), (5, 4), (2, 7), (2, 8), (12, 8), (13, 9), (8, 13), (8, 14)):
        put(housing, x, y, bark[0])
    for x, y in ((6, 6), (9, 6)):
        put(housing, x, y, EMBER[1])  # quiet, creaking-like eye slits
    for x, y in ((7, 3), (12, 8), (8, 13), (3, 8)):
        put(housing, x, y, bark[3])
    for x, y in ((8, 10), (9, 11), (9, 12)):
        put(housing, x, y, EMBER[1])

    model_root = os.path.abspath(os.path.join(ROOT, "../models/item"))
    os.makedirs(model_root, exist_ok=True)
    parent = "expeditions:item/expedition_compass_housing"
    with open(os.path.join(model_root, "expedition_compass_housing.json"), "w", encoding="utf-8") as file:
        # A palm-sized instrument held low, tilted back towards the player.
        # ItemTransform mirrors this same pose for the opposite hand.
        grip = {"rotation": [-35, -20, 0], "translation": [-1.3, 3.0, -0.5],
                "scale": [0.42, 0.42, 0.42]}
        json.dump({"parent": "item/generated", "display": {
            "firstperson_righthand": grip, "firstperson_lefthand": grip}}, file, indent=2)
        file.write("\n")
    frames = []
    for frame in range(32):
        img = housing.copy()
        angle = (frame - 16) * math.tau / 32
        dx, dy = math.sin(angle), -math.cos(angle)
        # A tapered ember thorn with a pale root tail. Rasterizing the same
        # shape at each angle preserves its weight when it turns diagonally.
        for y in range(4, 13):
            for x in range(3, 13):
                rx, ry = x - cx, y - cy
                along = rx * dx + ry * dy
                across = abs(rx * dy - ry * dx)
                if 0.4 < along < 4.4 and across < 1.15 - along * 0.17:
                    put(img, x, y, EMBER[5 if along > 3.2 else 3])
                elif -3.5 < along < -0.4 and across < 0.58:
                    put(img, x, y, bark[3 if across < 0.3 else 2])
        put(img, 7, 8, EMBER[2]); put(img, 8, 8, EMBER[4])
        put(img, 7, 9, bark[2]); put(img, 8, 9, EMBER[1])
        frames.append(img)
        name = f"expedition_compass_{frame:02d}"
        save(img, f"item/{name}.png")
        with open(os.path.join(model_root, f"{name}.json"), "w", encoding="utf-8") as file:
            json.dump({"parent": parent, "textures": {
                "layer0": f"expeditions:item/{name}"}}, file, indent=2)
            file.write("\n")
    save(frames[16], "item/expedition_compass.png")
    overrides = [{"predicate": {"angle": 0.0}, "model": "expeditions:item/expedition_compass_16"}]
    for step in range(1, 33):
        overrides.append({"predicate": {"angle": (2 * step - 1) / 64},
                          "model": f"expeditions:item/expedition_compass_{(16 + step) % 32:02d}"})
    with open(os.path.join(model_root, "expedition_compass.json"), "w", encoding="utf-8") as file:
        json.dump({"parent": parent, "textures": {
            "layer0": "expeditions:item/expedition_compass"}, "overrides": overrides}, file, indent=2)
        file.write("\n")

    ember = canvas()
    for y in range(H):
        for x in range(W):
            d = abs(x - 7.5) + abs(y - 7.5)
            if d < 7:
                color = ramp_at(EMBER, max(0.2, 1 - d / 7), x, y)
                put(ember, x, y, (*color[:3], int(255 * max(0, 1 - d / 7) ** 1.5)))
    put(ember, 7, 7, EMBER[5]); put(ember, 8, 7, EMBER[4])
    save(ember, "item/compass_ember.png")


def gen_artifact():
    _heartwood("gen_artifact")


def gen_sigil():
    _heartwood("gen_sigil")


def gen_essence():
    _heartwood("gen_essence")


def gen_journal():
    """An angled field book: visible paper, pale root spine, one amber clasp.

    The silhouette carries the item identity at 16px. Broad material clusters
    replace the old front-facing mask, which read as a badge rather than a book.
    """
    img = canvas()
    draw = ImageDraw.Draw(img)
    edge = (29, 32, 25, 255)
    cover = (52, 62, 43, 255)
    lit_cover = (73, 81, 55, 255)
    # Complete bound volume. The long diagonal page edge is recognizable
    # before any cover ornament is visible.
    draw.polygon([(9, 0), (10, 1), (14, 9), (15, 10), (14, 12),
                  (8, 15), (6, 15), (5, 13), (1, 6), (2, 4), (7, 1)], fill=edge)
    draw.polygon([(7, 12), (13, 9), (14, 10), (13, 12), (7, 14), (6, 14)], fill=BONE[2])
    draw.line([(7, 12), (13, 9), (14, 10)], fill=BONE[5])
    draw.line([(7, 13), (13, 10)], fill=BONE[4])
    draw.line([(8, 14), (13, 11)], fill=BONE[3])
    put(img, 10, 12, BONE[5]); put(img, 12, 11, BONE[5])
    # Rounded pale-heartwood spine, with only two large binding knots.
    draw.polygon([(2, 5), (3, 4), (7, 12), (6, 14), (5, 13), (2, 7)], fill=BONE[1])
    draw.line([(2, 5), (6, 13)], fill=BONE[3], width=2)
    draw.line([(2, 5), (5, 11)], fill=BONE[4])
    put(img, 3, 7, BONE[2]); put(img, 4, 8, BONE[4])
    put(img, 5, 11, BONE[2]); put(img, 6, 12, BONE[4])
    # Quiet leather face. Broad clusters keep it readable rather than noisy.
    draw.polygon([(3, 4), (9, 1), (13, 9), (7, 12)], fill=cover)
    draw.polygon([(4, 4), (9, 2), (11, 6), (7, 10)], fill=lit_cover)
    draw.line([(3, 4), (9, 1)], fill=(111, 116, 78, 255))
    draw.line([(7, 12), (13, 9)], fill=(37, 44, 31, 255))
    # A restrained root crease, not a literal face or glowing cover emblem.
    draw.line([(8, 9), (8, 7), (7, 6)], fill=(93, 94, 61, 255))
    put(img, 9, 6, (93, 94, 61, 255))
    # Tiny protruding resin clasp, top-left highlight and ember underside.
    draw.line([(10, 7), (13, 6)], fill=LEATHER[1], width=2)
    put(img, 12, 6, EMBER[4]); put(img, 13, 7, EMBER[3])
    put(img, 12, 7, EMBER[2]); put(img, 13, 8, LEATHER[1])
    save(img, "item/expedition_field_journal.png")


if __name__ == "__main__":
    gen_heart(); gen_layer(); gen_terminal(); gen_checkpoint(); gen_exit()
    gen_portal(); gen_device(); gen_compass(); gen_artifact(); gen_sigil()
    gen_essence(); gen_journal()
