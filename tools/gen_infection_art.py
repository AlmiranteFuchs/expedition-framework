"""Connected 16px growth tiles and restrained native infection/assay assets. No noise."""
from pathlib import Path
from PIL import Image, ImageDraw
import json


def generate(root):
    root = Path(root)
    atlas = Image.new('RGBA', (128, 128))
    heat = Image.new('RGBA', (128, 128))
    for mask in range(16):
        for variant in range(4):
            tile = Image.new('RGBA', (16, 16)); d = ImageDraw.Draw(tile)
            ember = Image.new('RGBA', (16, 16)); e = ImageDraw.Draw(ember)
            cx, cy = [(7, 8), (8, 7), (6, 7), (8, 9)][variant]
            paths = [
                [(cx, cy), (6 + variant % 2, 5), (7, 2), (7, 0)],
                [(cx, cy), (10, 6 + variant % 2), (13, 7), (15, 7)],
                [(cx, cy), (9 - variant % 2, 11), (7, 13), (7, 15)],
                [(cx, cy), (4, 9 - variant % 2), (2, 7), (0, 7)],
            ]
            active = [path for i, path in enumerate(paths) if mask & (1 << i)]
            if not active: active = [[(cx - 2, cy - 2), (cx, cy), (cx + 2, cy + 1)]]
            for path in active:
                d.line([(min(15, x + 1), min(15, y + 1)) for x, y in path], fill=(25, 33, 24, 90), width=2)
            for path in active:
                d.line(path, fill='#525b43', width=2)
                d.line(path, fill='#b2b896')
            # Interior forks differ, while every shared edge keeps the same two-pixel port.
            spur = [(cx, cy), (cx - 2, cy + 2), (cx - 1, min(13, cy + 4))]
            d.line(spur, fill='#7b8762'); d.point((cx - 1, cy - 1), fill='#d4d5b0')
            d.point((cx, cy), fill='#704125'); d.point((cx + 1, cy), fill='#393e2d')
            e.point((cx, cy), fill='#d18a3b')
            # Standardize endpoints after drawing shadows/branches.
            ports = [[(7, 0), (8, 0)], [(15, 7), (15, 8)], [(7, 15), (8, 15)], [(0, 7), (0, 8)]]
            for n in range(16):
                for point in [(n, 0), (n, 15), (0, n), (15, n)]: d.point(point, fill=(0, 0, 0, 0))
            for i, points in enumerate(ports):
                if mask & (1 << i):
                    d.point(points[0], fill='#b2b896'); d.point(points[1], fill='#525b43')
            index = mask * 4 + variant
            offset = (index % 8 * 16, index // 8 * 16)
            atlas.alpha_composite(tile, offset); heat.alpha_composite(ember, offset)
    atlas.save(root / 'block/assimilation_connections.png')
    heat.save(root / 'block/assimilation_connections_embers.png')

    # Grain runs along the raised ribs; broad shaded bands read as bark, not metal.
    relief = Image.new('RGBA', (16, 16), '#53583e'); r = ImageDraw.Draw(relief)
    r.rectangle((0, 0, 15, 2), fill='#8b9270')
    r.rectangle((0, 3, 15, 5), fill='#6c7652')
    r.rectangle((0, 12, 15, 15), fill='#303c2c')
    r.line([(0, 7), (5, 7), (8, 9), (15, 9)], fill='#a2aa7f')
    r.line([(1, 11), (6, 11), (10, 13)], fill='#404c34')
    r.line([(6, 2), (8, 4), (13, 4)], fill='#b8bc90')
    relief.save(root / 'block/assimilation_relief.png')
    resin = Image.new('RGBA', (16, 16)); r = ImageDraw.Draw(resin)
    r.rectangle((5, 6, 10, 9), fill='#7c4b26')
    r.rectangle((7, 6, 9, 8), fill='#c18b42')
    resin.save(root / 'block/assimilation_relief_resin.png')

    scars = Image.new('RGBA', (64, 64)); d = ImageDraw.Draw(scars)
    embers = Image.new('RGBA', (64, 64)); e = ImageDraw.Draw(embers)
    # Authored small lesions over common head/torso/limb UV islands. Most skin stays visible.
    for x, y, flip in [(10, 9, 1), (26, 12, -1), (43, 7, 1), (22, 24, 1),
                       (29, 29, -1), (45, 22, -1), (6, 24, 1), (12, 43, -1),
                       (26, 51, 1), (43, 43, -1), (53, 53, 1), (18, 8, 1),
                       (33, 21, 1), (18, 31, -1), (50, 29, 1), (4, 37, 1),
                       (35, 49, -1), (51, 43, 1)]:
        points = [(x, y), (x + flip * 2, y + 2), (x + flip, y + 5)]
        d.line(points, fill=(40, 51, 33, 235), width=3)
        d.line(points, fill=(125, 139, 96, 225))
        d.line([(x + flip * 2, y + 2), (x + flip * 4, y + 1)], fill=(76, 91, 59, 160))
        d.point((x - flip, y), fill=(149, 153, 116, 170))
        d.line([(x + flip, y + 4), (x - flip * 2, y + 6)], fill=(85, 101, 62, 210))
    for x, y in [(11, 11), (23, 26), (44, 24), (27, 53), (19, 10), (34, 23), (51, 45), (7, 26)]:
        d.point((x, y), fill=(116, 68, 31, 200)); e.point((x, y), fill='#b67b35')
    scars.save(root / 'entity/infection_scars.png')
    embers.save(root / 'entity/infection_embers.png')

    kit = Image.new('RGBA', (16, 16)); d = ImageDraw.Draw(kit)
    d.polygon([(2, 4), (11, 2), (14, 5), (14, 12), (5, 15), (1, 11)], fill='#20291f')
    d.polygon([(2, 5), (10, 3), (12, 5), (5, 8)], fill='#697257')
    d.polygon([(2, 6), (5, 9), (13, 6), (13, 11), (5, 14), (2, 11)], fill='#384634')
    d.line([(2, 5), (5, 8), (12, 5)], fill='#c7bd8e')
    d.line([(5, 9), (5, 13)], fill='#9b8054')
    d.line([(7, 9), (10, 8)], fill='#b18a51', width=2)
    # Clipped glass ampoule and the pale lancet at the case's edge.
    d.line([(9, 3), (12, 7)], fill='#d9d6b8', width=2)
    d.line([(10, 4), (12, 7)], fill='#93502e')
    d.line([(3, 8), (3, 11)], fill='#b7baa0'); d.point((3, 7), fill='#e0dbc0')
    kit.save(root / 'item/field_assay_kit.png')
    model = root.parent / 'models/item/field_assay_kit.json'
    model.write_text(json.dumps({'parent':'minecraft:item/generated', 'textures':{
        'layer0':'expeditions:item/field_assay_kit'}}, indent=2)+'\n', encoding='utf-8')
    print('Generated 64 connected growth tiles, sparse mob lesions and the field assay kit.')


if __name__ == '__main__':
    root = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/expeditions/textures'
    (root / 'entity').mkdir(exist_ok=True)
    generate(root)
