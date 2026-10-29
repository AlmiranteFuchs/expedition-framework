#!/usr/bin/env python3
"""Procedural texture generator for the expedition-framework asset set.

Every texture is 16x16, vanilla-plus resolution. Style rules:
  - one light source, top-left
  - hue-shifted ramps: bone shadows fall toward mauve-gray, ember shadows
    toward deep red-brown
  - cluster shading and ordered (Bayer) dithering, never raw noise
  - dark colored outlines, not pure black
Run: python3 tools/gen_textures.py   (writes into src/main/resources)
"""
import math
import os
import random
from PIL import Image

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


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(4))


def canvas():
    return Image.new("RGBA", (W, H), (0, 0, 0, 0))


def put(img, x, y, c):
    if 0 <= x < W and 0 <= y < H:
        img.putpixel((x, y), c)


def dist(x1, y1, x2, y2):
    return math.hypot(x1 - x2, y1 - y2)


def seg_dist(px, py, x1, y1, x2, y2):
    """Distance from point to segment."""
    dx, dy = x2 - x1, y2 - y1
    l2 = dx * dx + dy * dy
    if l2 == 0:
        return dist(px, py, x1, y1)
    t = max(0.0, min(1.0, ((px - x1) * dx + (py - y1) * dy) / l2))
    return dist(px, py, x1 + t * dx, y1 + t * dy)


def outline(img, inside, color=OUTLINE):
    """Darken the first empty ring around a filled mask."""
    src = img.load()
    for y in range(H):
        for x in range(W):
            if src[x, y][3] == 0 and any(
                    inside(x + dx, y + dy)
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                put(img, x, y, color)


def sphere_v(x, y, cx, cy, r):
    """Top-left lit sphere shading field, 0..1."""
    nx, ny = (x - cx) / r, (y - cy) / r
    return 0.62 - (nx * 0.45 + ny * 0.6)


def save(img, rel):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    print("wrote", rel)


# ---------------------------------------------------------------- blocks

def gen_heart():
    """Pale pod, jagged shell cracks, ember nucleus glowing through."""
    img, rng = canvas(), random.Random(7)
    cx, cy, r = 7.5, 8.0, 6.4
    mask = {}
    for y in range(H):
        for x in range(W):
            wob = 0.5 * math.sin(x * 2.1) + 0.4 * math.sin(y * 1.7)
            if dist(x + 0.5, y + 0.5, cx, cy) <= r + wob * 0.4:
                mask[(x, y)] = True

    nx_, ny_ = 8.0, 8.5  # nucleus
    cracks = [  # jagged polylines radiating from the nucleus
        [(8, 8), (6, 6), (5, 4), (4, 2)],
        [(8, 8), (10, 7), (12, 6), (13, 5)],
        [(8, 8), (9, 10), (10, 12), (10, 14)],
        [(8, 8), (6, 9), (4, 10), (3, 12)],
        [(8, 8), (10, 9), (12, 10)],
    ]

    def crack_d(x, y):
        best = 99.0
        for line in cracks:
            for i in range(len(line) - 1):
                best = min(best, seg_dist(x + 0.5, y + 0.5,
                                          *line[i], *line[i + 1]))
        return best

    for (x, y) in mask:
        d_nuc = dist(x + 0.5, y + 0.5, nx_, ny_)
        cd = crack_d(x, y)
        if d_nuc <= 2.6:
            # molten nucleus, hottest off-center
            v = 0.95 - d_nuc * 0.22
            put(img, x, y, ramp_at(EMBER, 0.45 + v * 0.5, x, y))
        elif cd <= 0.7 and d_nuc <= 6.0:
            put(img, x, y, mix(EMBER[0], DARK, 0.4))
        else:
            v = sphere_v(x + 0.5, y + 0.5, cx, cy, r)
            # ember light leaking along the crack rims
            leak = max(0.0, 1.0 - (cd - 0.7) / 1.8) * max(0.0, 1.0 - d_nuc / 6.5)
            c = ramp_at(FLESH, v * 0.85 + 0.12, x, y)
            put(img, x, y, mix(c, EMBER[2], leak * 0.55))
    outline(img, lambda x, y: mask.get((x, y), False))
    save(img, "block/assimilation_heart.png")


def gen_layer():
    """Cell-mosaic membrane: pale flesh plates with dark seams, ember flecks."""
    img, rng = canvas(), random.Random(11)
    cells = [(rng.uniform(1, 15), rng.uniform(1, 15),
              rng.uniform(0.3, 0.9)) for _ in range(14)]
    for y in range(H):
        for x in range(W):
            ds = sorted(dist(x + 0.5, y + 0.5, cx, cy) for cx, cy, _ in cells)
            d1, d2 = ds[0], ds[1]
            # nearest cell's own shade + gentle gradient away from its center
            near = min(cells, key=lambda c: dist(x + 0.5, y + 0.5, c[0], c[1]))
            v = near[2] * 0.55 + 0.25 - d1 * 0.02
            c = ramp_at(FLESH, v, x, y)
            if d2 - d1 < 0.9:                       # plate seam
                c = mix(c, FLESH[0], 0.72)
            put(img, x, y, c)
    # ember flecks at a few cell centers — the pulse's path under the skin
    for cx, cy, s in cells:
        if s > 0.78:
            ix, iy = int(cx), int(cy)
            put(img, ix, iy, EMBER[3])
            put(img, ix + 1 if cx % 1 < 0.5 else ix - 1, iy, EMBER[2])
    save(img, "block/assimilation_layer.png")


def _panel(img, rim_color=None):
    """Full-face bone tile: shaded plate, dark inset border, corner bolts."""
    for y in range(H):
        for x in range(W):
            v = sphere_v(x + 0.5, y + 0.5, 8, 8, 11) + 0.05
            c = ramp_at(BONE, v, x, y)
            put(img, x, y, c)
    for x in range(W):  # inset border, lit bottom-right
        put(img, x, 0, BONE[1]); put(img, 0, x, BONE[1])
        put(img, x, H - 1, BONE[4]); put(img, W - 1, x, BONE[4])
    for bx, by in ((1, 1), (14, 1), (1, 14), (14, 14)):
        put(img, bx, by, rim_color or OUTLINE)


def gen_terminal():
    """Charter console: recessed dark screen with an ember readout."""
    img = canvas()
    _panel(img)
    # screen cavity x3..12, y2..7 — inner bevel: dark top-left, lit bottom rim
    for y in range(2, 8):
        for x in range(3, 13):
            base = DARK if (x == 3 or y == 2) else (30, 20, 14, 255)
            put(img, x, y, base)
    for x in range(4, 13):
        put(img, x, 8, BONE[3])   # lower bezel catches the light
    # ember readout: two scan bars and a 3x3 "eye"
    for x in range(4, 8):
        put(img, x, 4, EMBER[3] if x < 6 else EMBER[2])
        put(img, x, 6, EMBER[2] if x < 6 else EMBER[1])
    for dy in range(3):
        for dx in range(3):
            v = 1.0 - dist(10 + dx, 3 + dy, 11, 4) * 0.28
            put(img, 10 + dx, 3 + dy, ramp_at(EMBER, v, dx, dy))
    # bottom panel: vent slit + two keys
    for x in range(3, 13):
        put(img, x, 11, mix(BONE[0], DARK, 0.5))
    put(img, 4, 13, OUTLINE); put(img, 5, 13, EMBER[2])
    put(img, 11, 13, OUTLINE); put(img, 12, 13, BONE[4])
    save(img, "block/expedition_terminal.png")


def gen_checkpoint():
    """Bone plate bearing the waypoint mark: ember diamond over a seam."""
    img = canvas()
    _panel(img)
    for y in range(4, 14):                       # inset seam
        put(img, 7, y, mix(BONE[1], DARK, 0.35))
        put(img, 8, y, mix(BONE[1], DARK, 0.35))
    for y in range(5, 13):                       # ember well inside the seam
        v = 0.55 + 0.35 * math.sin((y - 5) / 8 * math.pi)
        put(img, 7, y, ramp_at(EMBER, v, 7, y))
        put(img, 8, y, ramp_at(EMBER, v * 0.85, 8, y))
    # waypoint diamond crowning the seam
    for dy in range(-2, 3):
        for dx in range(-2, 3):
            if abs(dx) + abs(dy) <= 2:
                v = 1.0 - (abs(dx) + abs(dy)) * 0.22
                put(img, 8 + dx, 3 + dy, ramp_at(EMBER, v, dx + 2, dy + 2))
    save(img, "block/expedition_checkpoint.png")


def gen_exit():
    """Bone doorway: recessed void, ember light pooling at the sill."""
    img = canvas()
    _panel(img)
    for y in range(2, 14):                       # dark opening x4..11
        for x in range(4, 12):
            t = (y - 2) / 12.0
            c = mix((14, 10, 8, 255), EMBER[0], t * t)
            put(img, x, y, c)
    for x in range(4, 12):                       # inner frame bevel
        put(img, x, 2, DARK)
    for y in range(2, 14):
        put(img, 4, y, DARK); put(img, 11, y, BONE[3])
    for x in range(4, 12):                       # ember pools on the sill
        v = 0.5 + 0.4 * math.sin((x - 4) / 8 * math.pi)
        put(img, x, 13, ramp_at(EMBER, v, x, 13))
        if 5 <= x <= 10:
            put(img, x, 12, ramp_at(EMBER, v * 0.55, x, 12))
    put(img, 7, 5, mix(EMBER[0], EMBER[1], 0.5))  # a rising mote
    put(img, 9, 7, mix(EMBER[0], EMBER[1], 0.35))
    save(img, "block/expedition_exit.png")


def gen_portal():
    """Bone ring holding a swirling ember void."""
    img = canvas()
    cx = cy = 7.5
    for y in range(H):
        for x in range(W):
            dx, dy = x - cx, y - cy
            r = math.hypot(dx, dy)
            if r > 7.4:
                continue
            if r >= 4.6:                          # bone band, lit top-left
                v = 0.6 - (dx * 0.4 + dy * 0.55) / 7.0
                c = ramp_at(BONE, v, x, y)
                if r >= 6.9:
                    c = mix(c, OUTLINE, 0.55)
                put(img, x, y, c)
            else:                                 # the void
                ang = math.atan2(dy, dx)
                s = math.sin(ang * 2.0 + r * 1.9)
                v = 0.42 + s * 0.30 + (4.6 - r) * 0.05
                put(img, x, y, ramp_at(EMBER, v, x, y))
    put(img, 7, 7, EMBER[4]); put(img, 8, 8, EMBER[4])  # hot twin core
    save(img, "block/expedition_portal.png")


def gen_device():
    """Charter pylon plate: dark hood, ember lens, vented body."""
    img = canvas()
    _panel(img)
    for y in range(2, 7):                        # hood cavity x4..11
        for x in range(4, 12):
            put(img, x, y, DARK if (x == 4 or y == 2) else (32, 22, 15, 255))
    for dy in range(-2, 3):                      # ember lens diamond
        for dx in range(-2, 3):
            if abs(dx) + abs(dy) <= 2:
                v = 1.0 - (abs(dx) + abs(dy)) * 0.2
                put(img, 8 + dx, 4 + dy, ramp_at(EMBER, v, dx + 2, dy + 2))
    for x in range(4, 12):
        put(img, x, 7, BONE[3])                  # hood lower lip
    for y in (9, 11):                            # body vents: dark slits,
        for x in range(5, 11):                   # bone kept between them
            put(img, x, y, mix(BONE[1], DARK, 0.45))
    put(img, 8, 13, EMBER[1])                    # pilot light
    save(img, "block/expedition_device.png")


# ---------------------------------------------------------------- items

def _circle_item(img, cx, cy, r, face_ramp, rim_ramp, rim_lo, rim_hi):
    """Shaded disc with a darker rim band; returns the mask."""
    mask = {}
    for y in range(H):
        for x in range(W):
            d = dist(x + 0.5, y + 0.5, cx, cy)
            if d <= r:
                mask[(x, y)] = True
                if d > r - 1.4:
                    v = rim_lo + (rim_hi - rim_lo) * sphere_v(x, y, cx, cy, r)
                    put(img, x, y, ramp_at(rim_ramp, v, x, y))
                else:
                    v = sphere_v(x + 0.5, y + 0.5, cx, cy, r)
                    # posterize: banded shading, not a dithered gradient
                    v = round((v + 0.06) / 0.16) * 0.16
                    put(img, x, y, ramp_at(face_ramp, v, x, y))
    outline(img, lambda x, y: mask.get((x, y), False))
    return mask


def gen_compass():
    """Bone compass: dark rim, tick marks, two-tone ember needle."""
    img = canvas()
    _circle_item(img, 8, 8, 7.2, BONE, BONE, 0.12, 0.4)
    for tx, ty in ((8, 4), (8, 12), (4, 8), (12, 8)):   # cardinal ticks
        put(img, tx, ty, OUTLINE)
    put(img, 8, 4, EMBER[3])                            # north burns
    # needle: a crisp two-wide diagonal, ember tip NE, dark tail SW
    for x, y in ((4, 11), (5, 10), (6, 9), (7, 8), (4, 10), (5, 9),
                 (6, 8), (7, 7)):
        put(img, x, y, mix(BONE[0], OUTLINE, 0.45))
    for x, y in ((8, 7), (9, 6), (10, 5), (11, 4), (9, 7), (10, 6),
                 (11, 5), (12, 4)):
        put(img, x, y, EMBER[4] if x + y <= 15 else EMBER[3])
    put(img, 8, 8, EMBER[4])                            # axle pin
    save(img, "item/expedition_compass.png")


def gen_artifact():
    """Faceted bone shard with an ember heart glowing inside."""
    img = canvas()
    cx, cy = 8, 8
    shard = [(8, 0), (12, 5), (11, 11), (8, 15), (4, 11), (3, 5)]
    mask = {}

    def inside_shard(x, y):
        # ray cast against the hexagon
        n = 0
        for i in range(len(shard)):
            x1, y1 = shard[i]
            x2, y2 = shard[(i + 1) % len(shard)]
            if (y1 > y) != (y2 > y) and \
                    x < (x2 - x1) * (y - y1) / (y2 - y1) + x1:
                n += 1
        return n % 2 == 1

    for y in range(H):
        for x in range(W):
            if inside_shard(x + 0.5, y + 0.5):
                mask[(x, y)] = True
                # facet shading: left half light, right dark, center ridge
                v = 0.72 - abs(x - cx) * 0.07 - (y > cy) * 0.12 \
                    + (x < cx) * 0.08
                put(img, x, y, ramp_at(BONE, v, x, y))
    outline(img, lambda x, y: mask.get((x, y), False))
    # facet lines from the heart to the vertices
    for (vx, vy) in ((8, 1), (11, 5), (10, 11), (8, 14), (5, 11), (4, 5)):
        steps = int(dist(cx, cy, vx, vy))
        for i in range(steps):
            x = round(cx + (vx - cx) * i / steps)
            y = round(cy + (vy - cy) * i / steps)
            if dist(x, y, cx, cy) > 2.4 and mask.get((x, y)):
                put(img, x, y, mix(BONE[1], OUTLINE, 0.35))
    # the ember heart: diamond glow, light bleeding onto the facets
    for dy in range(-2, 3):
        for dx in range(-2, 3):
            if abs(dx) + abs(dy) <= 2:
                put(img, cx + dx, cy + dy,
                    ramp_at(EMBER, 1.0 - (abs(dx) + abs(dy)) * 0.2,
                            dx + 2, dy + 2))
    for dy in range(-3, 4):
        for dx in range(-3, 4):
            m = abs(dx) + abs(dy)
            if m == 3 and mask.get((cx + dx, cy + dy)):
                put(img, cx + dx, cy + dy, mix(BONE[2], EMBER[2], 0.45))
    save(img, "item/expedition_artifact.png")


def gen_sigil():
    """Bone disc carved with the ward-rune: ring, spokes, ember nodes."""
    img = canvas()
    mask = _circle_item(img, 8, 8, 7.0, BONE, BONE, 0.15, 0.45)
    cx = cy = 8
    carve = mix(BONE[0], OUTLINE, 0.65)
    # carved ring at r=4 (bold band) and cross spokes to the rim
    for y in range(H):
        for x in range(W):
            if not mask.get((x, y)):
                continue
            d = dist(x + 0.5, y + 0.5, cx, cy)
            if 3.5 <= d <= 4.5:
                put(img, x, y, carve)
    for a in (0, math.pi / 2, math.pi, math.pi * 1.5):
        for r_ in (1.5, 2.4, 5.4, 6.1):
            x = round(cx + math.cos(a) * r_)
            y = round(cy + math.sin(a) * r_)
            if mask.get((x, y)):
                put(img, x, y, carve)
    # ember nodes on the diagonals + a burning center
    for a in (math.pi / 4, math.pi * 3 / 4, math.pi * 5 / 4, math.pi * 7 / 4):
        x = round(cx + math.cos(a) * 4)
        y = round(cy + math.sin(a) * 4)
        put(img, x, y, EMBER[4])
        put(img, round(cx + math.cos(a) * 5), round(cy + math.sin(a) * 5),
            EMBER[2])
    put(img, cx, cy, EMBER[4])
    save(img, "item/attunement_sigil.png")


def gen_essence():
    """A droplet of the Bloom: pale husk, ember spark at its core."""
    img = canvas()
    cx = 8
    drop = [(8, 1), (9, 3), (11, 6), (11, 9), (10, 12), (8, 14),
            (6, 12), (5, 9), (5, 6), (7, 3)]
    mask = {}

    def inside_drop(x, y):
        n = 0
        for i in range(len(drop)):
            x1, y1 = drop[i]
            x2, y2 = drop[(i + 1) % len(drop)]
            if (y1 > y) != (y2 > y) and \
                    x < (x2 - x1) * (y - y1) / (y2 - y1) + x1:
                n += 1
        return n % 2 == 1

    for y in range(H):
        for x in range(W):
            if inside_drop(x + 0.5, y + 0.5):
                mask[(x, y)] = True
                v = 0.7 - (x - 6) * 0.05 - (y - 4) * 0.02
                put(img, x, y, ramp_at(FLESH, v, x, y))
    outline(img, lambda x, y: mask.get((x, y), False))
    put(img, 7, 5, FLESH[5]); put(img, 6, 6, FLESH[5])   # specular
    for dy in range(-1, 2):                              # ember core
        for dx in range(-1, 2):
            if abs(dx) + abs(dy) <= 1:
                put(img, cx + dx, 10 + dy,
                    ramp_at(EMBER, 0.95 - (abs(dx) + abs(dy)) * 0.3,
                            dx + 1, dy + 1))
    save(img, "item/assimilation_essence.png")


def gen_journal():
    """Charter field journal: worn leather, brass clasp, ember-marked strap."""
    img = canvas()
    cover = [(2, 2), (13, 2), (14, 3), (14, 13), (13, 14), (2, 14), (1, 13),
             (1, 3)]
    mask = {}

    def inside_cover(x, y):
        n = 0
        for i in range(len(cover)):
            x1, y1 = cover[i]
            x2, y2 = cover[(i + 1) % len(cover)]
            if (y1 > y) != (y2 > y) and \
                    x < (x2 - x1) * (y - y1) / (y2 - y1) + x1:
                n += 1
        return n % 2 == 1

    for y in range(H):
        for x in range(W):
            if inside_cover(x + 0.5, y + 0.5):
                mask[(x, y)] = True
                v = 0.55 + (8 - abs(x - 7.5)) * 0.02 - (x - 2) * 0.015 \
                    + (14 - y) * 0.008
                c = ramp_at(LEATHER, v, x, y)
                if x <= 2:                       # spine
                    c = ramp_at(LEATHER, v - 0.18, x, y)
                put(img, x, y, c)
    outline(img, lambda x, y: mask.get((x, y), False), color=(48, 30, 18, 255))
    # tooled border
    for x in range(4, 13):
        put(img, x, 3, LEATHER[2]); put(img, x, 12, LEATHER[2])
    for y in range(3, 13):
        put(img, 4, y, LEATHER[2]); put(img, 12, y, LEATHER[2])
    # strap + ember clasp: the journal has drunk blood
    for y in range(2, 15):
        put(img, 9, y, LEATHER[1] if y % 2 else LEATHER[2])
    put(img, 9, 8, EMBER[3]); put(img, 9, 9, EMBER[1])
    save(img, "item/expedition_field_journal.png")


if __name__ == "__main__":
    gen_heart(); gen_layer(); gen_terminal(); gen_checkpoint(); gen_exit()
    gen_portal(); gen_device(); gen_compass(); gen_artifact(); gen_sigil()
    gen_essence(); gen_journal()
