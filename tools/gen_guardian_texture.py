"""Deterministic, UV-mapped 64px entity art. No noise; pale bark and ember cores.

The 16px item/block convention remains in gen_textures.py. This larger sheet
maps GuardianModel's original articulated geometry rather than a flat sprite.
"""
from pathlib import Path
from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/expeditions/textures/entity'
OUT.mkdir(parents=True, exist_ok=True)
skin = Image.new('RGBA', (64, 64), (43, 47, 36, 255))
draw = ImageDraw.Draw(skin)
glow = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
fire = ImageDraw.Draw(glow)
bone = [(82, 78, 59), (117, 110, 82), (155, 150, 116), (190, 188, 151), (220, 215, 177)]

def bark(x, y, w, h, light=0):
    draw.rectangle((x, y, x + w - 1, y + h - 1), fill=bone[max(0, 1 + light)])
    for col in range(w):
        shade = min(4, max(0, 2 + light - (col % 4 == 3) + (col % 4 == 0)))
        draw.line((x + col, y, x + col, y + h - 1), fill=bone[shade])
    # Broad, deliberate split grain and staggered branch scars.
    for row in range(3, h, 7):
        start = x + (row // 7 * 3 + 1) % max(1, w - 1)
        draw.line((start, y + row, min(x + w - 1, start + 2), y + row + 2), fill=bone[0])
    draw.line((x, y, x + w - 1, y), fill=bone[min(4, 3 + light)])

# Body, head, arms, legs, horns, root fingers, shoulder yoke.
for region in [(0, 0, 36, 24), (0, 24, 36, 18), (36, 0, 16, 29),
               (36, 30, 16, 30), (54, 0, 8, 13), (54, 16, 8, 10), (0, 44, 44, 11)]:
    bark(*region)

# Chest recess and ribs on its front UV face (u=6..17, v=6..23).
draw.polygon([(9, 9), (15, 10), (16, 14), (14, 22), (10, 22), (8, 16)], fill=(31, 35, 27))
for y in (11, 15, 19):
    draw.line((6, y, 9, y + 2), fill=bone[3], width=1)
    draw.line((17, y + 1, 14, y + 2), fill=bone[2], width=1)
draw.line((11, 9, 12, 21), fill=(93, 61, 31), width=1)

# Face front at (8,32), width 10: hollow angular mask with a split brow.
draw.polygon([(9, 34), (16, 33), (17, 36), (15, 40), (11, 40), (8, 37)], fill=(33, 37, 28))
draw.line((8, 33, 12, 34), fill=bone[4])
draw.line((14, 33, 17, 32), fill=bone[3])
draw.line((13, 33, 13, 39), fill=bone[2])
draw.line((10, 40, 13, 41), fill=bone[3])
for x, y in [(10, 35), (11, 35), (15, 35), (16, 35)]:
    draw.point((x, y), fill=(246, 137, 39))
    fire.point((x, y), fill=(255, 178, 63, 255))
draw.point((11, 36), fill=(176, 72, 24))
draw.point((15, 36), fill=(176, 72, 24))

# Separate protruding heart box: front u=1..4, v=57..62.
draw.rectangle((0, 56, 9, 63), fill=(44, 39, 28))
for y in range(57, 63):
    for x in range(1, 5):
        distance = abs(x - 2.5) + abs(y - 59.5) * 0.55
        if distance <= 2.6:
            color = (255, 190, 73) if distance < 1.3 else (204, 89, 25)
            draw.point((x, y), fill=color)
            fire.point((x, y), fill=(*color, 255))

skin.save(OUT / 'rootbound_guardian.png')
glow.save(OUT / 'rootbound_guardian_glow.png')
print('Wrote guardian skin and emissive UV sheets.')
