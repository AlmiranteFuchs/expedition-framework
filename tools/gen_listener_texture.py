"""Native 64px UV sheet for ListenerModel. Warm gray bark, hollow mask, sparse amber."""
from pathlib import Path
from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/expeditions/textures/entity"
OUT.mkdir(parents=True, exist_ok=True)
skin = Image.new("RGBA", (64, 64), (66, 59, 50, 255))
glow = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
d = ImageDraw.Draw(skin)
g = ImageDraw.Draw(glow)
BARK = [(83, 76, 65), (124, 115, 97), (163, 154, 133), (196, 186, 160), (216, 207, 182)]

def face(x, y, w, h, shade):
    d.rectangle((x, y, x + w - 1, y + h - 1), fill=BARK[shade])
    # Hand-placed split lengths and interrupted grain, rather than repeated vertical stripes.
    for i, (offset, start, length) in enumerate([(1, 1, 3), (4, 3, 5), (7, 0, 4), (10, 5, 3)]):
        if offset >= w: continue
        xx = x + offset
        end = min(y + h - 1, y + start + length)
        if y + start <= end:
            d.line((xx, y + start, xx, end), fill=BARK[max(0, shade - 2)])
            if end + 1 < y + h and xx + 1 < x + w:
                d.point((xx + 1, end + 1), fill=BARK[max(0, shade - 1)])
    d.line((x, y, x + w - 1, y), fill=BARK[min(4, shade + 1)])

def box(u, v, w, h, depth):
    face(u + depth, v, w, depth, 3)
    face(u + depth + w, v, w, depth, 1)
    face(u, v + depth, depth, h, 1)
    face(u + depth, v + depth, w, h, 2)
    face(u + depth + w, v + depth, depth, h, 1)
    face(u + depth * 2 + w, v + depth, w, h, 2)

box(0, 0, 8, 5, 9)
box(0, 42, 6, 2, 7)
box(0, 17, 10, 7, 3)
box(28, 17, 5, 8, 1)
box(36, 0, 2, 7, 2)
box(0, 32, 8, 6, 1)
# Carapace front has overlapping chevrons and one sealed resin scar.
for yy in (10, 13):
    d.line((10, yy, 13, yy + 1, 15, yy), fill=BARK[0], width=1)
    d.line((10, yy - 1, 13, yy, 15, yy - 1), fill=BARK[3], width=1)
d.line((13, 11, 13, 13), fill=(142, 93, 47))
# One half-mask is mirrored by the model. Dark tapered recess, chalk brow, closed inner edge.
d.polygon([(33, 20), (32, 22), (33, 25)], fill=(88, 76, 61))
d.line((30, 20, 30, 22, 31, 23), fill=BARK[1])
d.line((29, 24, 31, 25), fill=BARK[3])
d.line((29, 19, 31, 19, 33, 18), fill=BARK[4])
d.line((33, 20, 33, 24), fill=(117, 78, 42))
d.point((33, 22), fill=(180, 122, 58))
# The throat is behind the plates. A single descending amber split, no bright eyes.
d.rectangle((1, 33, 8, 38), fill=(43, 36, 31))
d.line((4, 33, 4, 35, 5, 36, 5, 38), fill=(168, 107, 48))
d.point((4, 35), fill=(201, 145, 67))
g.line((4, 34, 4, 35, 5, 36, 5, 37), fill=(131, 87, 38, 255))
skin.save(OUT / "bloom_listener.png")
glow.save(OUT / "bloom_listener_glow.png")
print("Generated Bloom Listener bark and throat UV sheets.")
