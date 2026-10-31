"""World-scale 16px/block growth and restrained native infection/assay assets. No noise."""
from pathlib import Path
from PIL import Image, ImageDraw
import json


def generate(root):
    root = Path(root)
    from bloom_surface_art import generate as generate_surface
    generate_surface(root)

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
    print('Generated world-scale growth, sparse mob lesions and the field assay kit.')


if __name__ == '__main__':
    root = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/expeditions/textures'
    (root / 'entity').mkdir(exist_ok=True)
    generate(root)
