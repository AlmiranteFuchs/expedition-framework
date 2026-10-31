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
            cx, cy = [(7, 9), (8, 6), (6, 7), (9, 9)][variant]
            paths = [
                [(cx, cy), (cx + 2, max(3, cy - 3)), (5, 3), (7, 0)],
                [(cx, cy), (min(12, cx + 3), cy + 2), (12, 5), (15, 7)],
                [(cx, cy), (cx - 2, min(12, cy + 3)), (10, 12), (7, 15)],
                [(cx, cy), (max(3, cx - 3), cy - 2), (3, 10), (0, 7)],
            ]
            active = [path for i, path in enumerate(paths) if mask & (1 << i)]
            if not active: active = [[(cx - 3, cy + 3), (cx - 2, cy), (cx, cy), (cx + 2, cy - 2)]]
            for path in active:
                d.line([(min(15, x + 1), min(15, y + 1)) for x, y in path], fill=(75, 68, 60, 65), width=5)
            for path in active:
                # A swollen root narrows towards its neighbour, with broken bark highlights.
                for i in range(len(path) - 1):
                    d.line(path[i:i+2], fill='#aaa296', width=max(2, 5-i*2))
                    d.line(path[i:i+2], fill='#d1c9ba', width=max(1, 3-i))
                d.point(path[1], fill='#e0dace')
            # Chipped pale bark grips the host, split by one narrow amber fissure.
            d.polygon([(cx-2, cy-1), (cx-1, cy-3), (cx+2, cy-2), (cx+3, cy),
                       (cx+1, cy+2), (cx-2, cy+2)], fill='#aaa499')
            d.line([(cx-2, cy+2), (cx-3, cy+2), (cx-3, cy+1)], fill='#d9d3c7')
            d.line([(cx+1, cy-1), (cx+1, cy-3), (cx+3, cy-3)], fill='#bcb6a9')
            d.line([(cx-1, cy+1), (cx-1, cy-1), (cx+1, cy-1)], fill='#e8e0d1')
            # Broken grain lives inside the pale tissue, without a uniform bright outline.
            d.point((cx-2, cy), fill='#70665a')
            d.point((cx+2, cy-1), fill='#ded5c4')
            d.line([(cx+1, cy+1), (cx+2, cy+2)], fill='#9c6a39')
            d.line([(cx, cy+1), (cx, cy-1)], fill='#765037')
            d.point((cx, cy), fill='#be792d')
            e.point((cx, cy), fill='#e3a348')
            # Standardize endpoints after drawing shadows/branches.
            ports = [[(7, 0), (8, 0)], [(15, 7), (15, 8)], [(7, 15), (8, 15)], [(0, 7), (0, 8)]]
            for n in range(16):
                for point in [(n, 0), (n, 15), (0, n), (15, n)]: d.point(point, fill=(0, 0, 0, 0))
            for i, points in enumerate(ports):
                if mask & (1 << i):
                    d.point(points[0], fill='#c6c1b3'); d.point(points[1], fill='#77716a')
            index = mask * 4 + variant
            offset = (index % 8 * 16, index // 8 * 16)
            atlas.alpha_composite(tile, offset); heat.alpha_composite(ember, offset)
    atlas.save(root / 'block/assimilation_connections.png')
    heat.save(root / 'block/assimilation_connections_embers.png')

    scars = Image.new('RGBA', (64, 64)); d = ImageDraw.Draw(scars)
    embers = Image.new('RGBA', (64, 64)); e = ImageDraw.Draw(embers)
    # Authored small lesions over common head/torso/limb UV islands. Most skin stays visible.
    for x, y, flip in [(10, 9, 1), (26, 12, -1), (43, 7, 1), (22, 24, 1),
                       (29, 29, -1), (45, 22, -1), (6, 24, 1), (12, 43, -1),
                       (26, 51, 1), (43, 43, -1), (53, 53, 1), (18, 8, 1),
                       (33, 21, 1), (18, 31, -1), (50, 29, 1), (4, 37, 1),
                       (35, 49, -1), (51, 43, 1)]:
        points = [(x, y), (x + flip * 2, y + 2), (x + flip, y + 5)]
        d.line(points, fill=(91, 83, 73, 235), width=3)
        d.line(points, fill=(193, 185, 168, 225))
        d.line([(x + flip * 2, y + 2), (x + flip * 4, y + 1)], fill=(157, 148, 132, 160))
        d.point((x - flip, y), fill=(222, 214, 196, 170))
        d.line([(x + flip, y + 4), (x - flip * 2, y + 6)], fill=(174, 164, 145, 210))
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
