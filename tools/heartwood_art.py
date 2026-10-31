"""The 16px heartwood asset family: deliberate clusters, no random/noise fill.

Public generators are called by gen_textures.py so its documented command
remains the single source for item and block PNGs and their face assignments.
"""
import json
from pathlib import Path
from PIL import Image, ImageDraw

WOOD = ['#292b21', '#5b5b42', '#89896a', '#bab58c', '#e2d7ac', '#f3e8c9']
IRON = ['#161d19', '#29362c', '#425040', '#64705a']
EMBER = ['#582c1a', '#98441c', '#d97824', '#f7b347', '#ffe2a1']
COPPER = ['#3a3025', '#735236', '#ad8553', '#d7b875']


def canvas(opaque=False):
    return Image.new('RGBA', (16, 16), IRON[0] if opaque else (0, 0, 0, 0))


def save(root, name, image):
    path = Path(root) / (name + '.png')
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)
    print('wrote', name + '.png')


def wood_face():
    image = canvas(True)
    draw = ImageDraw.Draw(image)
    # Four broad irregular boards. Grain is carved in clusters, never stippled.
    for x in range(16):
        shade = 2 + (x % 4 == 1) - (x % 4 == 3)
        draw.line((x, 0, x, 15), fill=WOOD[shade])
    for points in [[(3, 0), (3, 5), (4, 6), (4, 15)], [(11, 0), (11, 8), (10, 9), (10, 15)]]:
        draw.line(points, fill=WOOD[0])
    draw.line((0, 0, 15, 0), fill=WOOD[4])
    draw.line((15, 1, 15, 15), fill=WOOD[0])
    draw.line((1, 15, 14, 15), fill=WOOD[1])
    return image


def metal_frame(image):
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, 0, 15, 15), outline=IRON[0])
    draw.rectangle((1, 1, 14, 14), outline=WOOD[1])
    draw.line((1, 1, 13, 1), fill=WOOD[4])
    draw.line((1, 2, 1, 13), fill=WOOD[3])
    for x, y in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        draw.point((x, y), fill=COPPER[2])


def end_grain():
    image = canvas(True)
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, 0, 15, 15), fill=WOOD[2], outline=WOOD[0])
    for r, color in [(6, WOOD[3]), (5, WOOD[1]), (4, WOOD[3]), (3, WOOD[2])]:
        draw.polygon([(8-r, 5), (5, 8-r), (11, 8-r), (8+r, 5),
                      (8+r, 11), (11, 8+r), (5, 8+r), (8-r, 11)], outline=color)
    draw.line([(8, 2), (7, 5), (8, 8), (10, 10), (10, 14)], fill=WOOD[0])
    draw.point((8, 7), fill=EMBER[2])
    return image


def base(root):
    image = wood_face()
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, 0, 15, 3), fill=IRON[1])
    draw.rectangle((0, 12, 15, 15), fill=IRON[0])
    draw.line((1, 1, 14, 1), fill=IRON[2])
    for x in (2, 12):
        draw.rectangle((x, 4, x+1, 11), fill=COPPER[0])
    save(root, 'block/heartwood_base', image)


def cube_model(root, name, top, bottom='heartwood_base'):
    path = Path(root).parent / 'models/block' / (name + '.json')
    path.write_text(json.dumps({'parent': 'minecraft:block/cube_bottom_top', 'textures': {
        'side': 'expeditions:block/' + name, 'top': 'expeditions:block/' + top,
        'bottom': 'expeditions:block/' + bottom}}, indent=2) + '\n', encoding='utf-8')


def gen_heart(root):
    image = wood_face()
    draw = ImageDraw.Draw(image)
    # Hollow heartwood, split open around a narrow living ember.
    draw.polygon([(6, 2), (10, 3), (12, 7), (10, 12), (7, 14), (4, 10), (4, 6)], fill=IRON[0])
    draw.line([(5, 3), (4, 6), (4, 10), (7, 14)], fill=WOOD[4])
    draw.line([(10, 3), (12, 7), (10, 12)], fill=WOOD[1])
    draw.polygon([(8, 4), (10, 7), (9, 11), (7, 12), (6, 8)], fill=EMBER[1])
    draw.line([(8, 5), (8, 8), (7, 10)], fill=EMBER[3], width=2)
    draw.point((7, 6), fill=EMBER[4])
    for points in [[(3, 2), (6, 4)], [(12, 11), (10, 10)], [(6, 15), (7, 12)]]:
        draw.line(points, fill=WOOD[0])
    save(root, 'block/assimilation_heart', image)
    top = end_grain()
    ImageDraw.Draw(top).line([(7, 6), (8, 8), (7, 10)], fill=EMBER[3])
    save(root, 'block/assimilation_heart_top', top)
    cube_model(root, 'assimilation_heart', 'assimilation_heart_top', 'assimilation_heart_top')


def gen_layer(root):
    image = canvas()
    draw = ImageDraw.Draw(image)
    # Creeping rootlets, not a sheet: most of the host remains exposed.
    roots = [[(7, 15), (6, 12), (7, 9), (8, 7), (6, 4), (7, 0)],
             [(8, 7), (10, 5), (13, 5), (15, 3)],
             [(7, 10), (4, 9), (2, 10), (0, 10)],
             [(6, 4), (3, 3), (2, 0)],
             [(6, 12), (9, 12), (11, 14), (12, 15)]]
    for points in roots:
        draw.line([(x+1, y+1) for x, y in points], fill=(29, 35, 24, 95), width=2)
    for points in roots:
        draw.line(points, fill=(82, 88, 63, 255), width=2)
        draw.line(points, fill=(184, 186, 149, 255))
    for points in [[(7, 14), (6, 12), (7, 10)], [(7, 6), (6, 4), (7, 1)],
                   [(9, 6), (10, 5), (12, 5)], [(5, 9), (4, 9), (2, 10)]]:
        draw.line(points, fill=(223, 222, 183, 255))
    for x, y in [(7, 8), (11, 5)]:
        draw.point((x, y-1), fill=WOOD[0]); draw.point((x+1, y), fill=WOOD[0])
        draw.point((x, y), fill=EMBER[1]); draw.point((x-1, y), fill=EMBER[2])
    save(root, 'block/assimilation_layer', image)
    embers = canvas(); e = ImageDraw.Draw(embers)
    e.point((7, 8), fill=EMBER[3]); e.point((11, 5), fill=EMBER[2])
    save(root, 'block/assimilation_layer_embers', embers)
    # The BER places both root and ember passes directly on the host face.
    # A second raised slab would turn the organic growth back into floor tiles.
    (Path(root).parent / 'models/block/assimilation_layer.json').write_text(json.dumps({
        'parent':'minecraft:block/block', 'textures':{'particle':'expeditions:block/assimilation_layer'}},
        indent=2)+'\n', encoding='utf-8')
    (Path(root).parent / 'models/item/assimilation_layer.json').write_text(json.dumps({
        'parent':'minecraft:item/generated', 'textures':{'layer0':'expeditions:block/assimilation_layer'}},
        indent=2)+'\n', encoding='utf-8')


def gen_terminal(root):
    image = wood_face(); metal_frame(image)
    draw = ImageDraw.Draw(image)
    draw.rectangle((3, 3, 12, 9), fill=IRON[0])
    draw.line((3, 3, 12, 3), fill=IRON[1])
    # A route etched into smoked glass: three amber instruments joined by wire.
    draw.line([(4, 7), (6, 7), (8, 5), (11, 5)], fill=COPPER[1])
    for x, y in [(4, 7), (8, 5), (11, 5)]:
        draw.point((x, y), fill=EMBER[3])
    draw.line((3, 10, 12, 10), fill=WOOD[4])
    draw.rectangle((5, 12, 10, 13), fill=IRON[0])
    draw.point((6, 12), fill=COPPER[2]); draw.point((9, 12), fill=EMBER[1])
    save(root, 'block/expedition_terminal', image)
    top = end_grain(); d = ImageDraw.Draw(top)
    d.rectangle((3, 3, 12, 12), fill=IRON[1], outline=COPPER[1])
    d.line([(5, 9), (7, 5), (10, 7), (8, 11), (5, 9)], fill=WOOD[3])
    d.point((7, 6), fill=EMBER[3]); d.point((9, 8), fill=EMBER[1])
    save(root, 'block/expedition_terminal_top', top)
    side = wood_face(); metal_frame(side)
    d = ImageDraw.Draw(side)
    # Braced casing, with no duplicate screen or controls on the other sides.
    d.rectangle((4, 3, 11, 12), fill=IRON[1])
    for y in (5, 7, 9): d.line((5, y, 10, y), fill=IRON[0])
    d.point((4, 3), fill=COPPER[2]); d.point((11, 12), fill=COPPER[1])
    save(root, 'block/expedition_terminal_side', side)
    base(root)
    model_root = Path(root).parent / 'models/block'
    (model_root / 'expedition_terminal.json').write_text(json.dumps({
        'parent':'minecraft:block/orientable_with_bottom', 'textures':{
            'front':'expeditions:block/expedition_terminal',
            'side':'expeditions:block/expedition_terminal_side',
            'top':'expeditions:block/expedition_terminal_top',
            'bottom':'expeditions:block/heartwood_base'}}, indent=2)+'\n', encoding='utf-8')
    state_root = Path(root).parent / 'blockstates'
    (state_root / 'expedition_terminal.json').write_text(json.dumps({'variants':{
        'facing='+facing:{'model':'expeditions:block/expedition_terminal', 'y':angle}
        for facing, angle in [('north',0),('east',90),('south',180),('west',270)]}}, indent=2)+'\n', encoding='utf-8')


def gen_checkpoint(root):
    image = wood_face(); metal_frame(image)
    draw = ImageDraw.Draw(image)
    draw.rectangle((5, 2, 10, 13), fill=IRON[0])
    draw.line([(4, 3), (4, 11), (6, 13)], fill=WOOD[4])
    draw.line([(11, 3), (11, 11), (9, 13)], fill=WOOD[1])
    draw.polygon([(8, 4), (10, 7), (8, 10), (6, 7)], fill=COPPER[1])
    draw.line((8, 5, 8, 9), fill=EMBER[3])
    draw.point((7, 6), fill=EMBER[4])
    save(root, 'block/expedition_checkpoint', image)
    top = end_grain(); d = ImageDraw.Draw(top)
    d.polygon([(8, 3), (12, 8), (8, 12), (3, 8)], fill=IRON[0], outline=COPPER[1])
    d.line([(6, 8), (8, 5), (10, 8)], fill=EMBER[3])
    d.point((8, 9), fill=EMBER[1])
    save(root, 'block/expedition_checkpoint_top', top)
    base(root); cube_model(root, 'expedition_checkpoint', 'expedition_checkpoint_top')


def gen_exit(root):
    image = wood_face(); metal_frame(image)
    draw = ImageDraw.Draw(image)
    draw.rectangle((3, 4, 12, 12), fill=IRON[0])
    draw.line([(3, 11), (3, 5), (6, 2), (9, 2), (12, 5), (12, 11)], fill=WOOD[4])
    draw.line([(5, 11), (5, 6), (7, 4), (8, 4), (10, 6), (10, 11)], fill=COPPER[1])
    draw.line((5, 12, 10, 12), fill=EMBER[3])
    draw.point((7, 7), fill=EMBER[2]); draw.point((8, 6), fill=EMBER[4])
    save(root, 'block/expedition_exit', image)
    top = end_grain(); d = ImageDraw.Draw(top)
    d.rectangle((4, 4, 11, 11), fill=IRON[0], outline=COPPER[1])
    for y in (6, 9): d.line([(5, y+1), (7, y-1), (10, y+1)], fill=EMBER[3])
    save(root, 'block/expedition_exit_top', top)
    base(root); cube_model(root, 'expedition_exit', 'expedition_exit_top')


def gen_device(root):
    image = wood_face(); metal_frame(image)
    draw = ImageDraw.Draw(image)
    draw.rectangle((3, 3, 12, 12), fill=IRON[0])
    draw.rectangle((6, 4, 9, 10), fill=EMBER[0])
    draw.line((7, 4, 7, 10), fill=EMBER[3]); draw.point((8, 5), fill=EMBER[4])
    for y in (3, 8, 12):
        draw.line((3, y, 12, y), fill=COPPER[1])
        draw.point((4, y), fill=COPPER[3])
    draw.line((3, 4, 3, 11), fill=IRON[3]); draw.line((12, 4, 12, 11), fill=IRON[1])
    save(root, 'block/expedition_device', image)
    top = end_grain(); d = ImageDraw.Draw(top)
    d.rectangle((3, 3, 12, 12), fill=IRON[0], outline=COPPER[1])
    for x in (5, 8, 11): d.line((x, 4, x, 11), fill=COPPER[2])
    d.rectangle((7, 6, 9, 9), fill=EMBER[2]); d.point((7, 6), fill=EMBER[4])
    save(root, 'block/expedition_device_top', top)
    base(root); cube_model(root, 'expedition_device', 'expedition_device_top')


def gen_portal(root):
    image = canvas(); d = ImageDraw.Draw(image)
    # Root aperture: the center is truly transparent, not a painted lava disc.
    d.polygon([(5, 0), (10, 0), (14, 4), (15, 10), (11, 15), (4, 15), (0, 10), (1, 4)], fill=WOOD[0])
    d.polygon([(5, 1), (10, 1), (13, 5), (14, 10), (10, 14), (5, 14), (1, 10), (2, 5)], fill=WOOD[3])
    d.polygon([(5, 3), (10, 3), (11, 6), (12, 10), (9, 12), (6, 12), (3, 10), (4, 6)], fill=(0, 0, 0, 0))
    d.line([(4, 4), (5, 2), (10, 2), (12, 5)], fill=WOOD[5])
    d.line([(3, 10), (6, 13), (9, 13), (12, 10)], fill=EMBER[2])
    for point in [(4, 6), (11, 7), (7, 10)]: d.point(point, fill=EMBER[3])
    save(root, 'block/expedition_portal', image)
    model = {'parent':'minecraft:block/block', 'ambientocclusion':False,
             'textures':{'particle':'expeditions:block/expedition_portal', 'portal':'expeditions:block/expedition_portal'},
             'elements':[
                 {'from':[0,0,7.99], 'to':[16,16,8.01], 'shade':False,
                  'faces':{
                      'north':{'texture':'#portal','uv':[16,0,0,16]},
                      'south':{'texture':'#portal','uv':[0,0,16,16]}}}]}
    (Path(root).parent / 'models/block/expedition_portal.json').write_text(json.dumps(model, indent=2)+'\n', encoding='utf-8')
    (Path(root).parent / 'blockstates/expedition_portal.json').write_text(json.dumps({'variants':{
        'axis=x':{'model':'expeditions:block/expedition_portal'},
        'axis=z':{'model':'expeditions:block/expedition_portal', 'y':90}}}, indent=2)+'\n', encoding='utf-8')


def gen_artifact(root):
    image = canvas(); d = ImageDraw.Draw(image)
    # Sealed specimen canister: clear silhouette and a protected amber window.
    d.polygon([(6,1),(10,1),(12,3),(12,12),(10,14),(5,14),(3,12),(3,4)], fill=IRON[0])
    d.polygon([(5,3),(9,2),(11,4),(11,11),(9,13),(5,13),(4,11),(4,5)], fill=WOOD[2])
    d.line([(5,3),(5,11),(6,12)], fill=WOOD[5])
    d.rectangle((6,5,9,10), fill=IRON[0])
    d.line((7,6,7,9), fill=EMBER[3]); d.point((8,6), fill=EMBER[4]); d.point((8,10), fill=EMBER[1])
    for y in (3,11):
        d.line((4,y,11,y), fill=COPPER[1]); d.point((5,y), fill=COPPER[3])
    d.line((6,1,10,1), fill=WOOD[3])
    save(root, 'item/expedition_artifact', image)


def gen_sigil(root):
    image = canvas(); d = ImageDraw.Draw(image)
    d.line([(6,3),(6,1),(9,1),(10,3)], fill=IRON[1])
    d.polygon([(5,3),(10,3),(13,6),(12,11),(9,14),(5,13),(2,10),(2,6)], fill=WOOD[0])
    d.polygon([(5,4),(9,4),(12,7),(11,11),(8,13),(5,12),(3,9),(3,6)], fill=WOOD[3])
    d.line([(4,6),(5,4),(9,4)], fill=WOOD[5])
    d.polygon([(6,6),(9,6),(10,9),(8,11),(5,9)], fill=IRON[0])
    d.line([(7,10),(7,8),(5,6)], fill=WOOD[2])
    d.line([(7,8),(9,6)], fill=WOOD[2])
    d.point((7,8), fill=EMBER[3]); d.point((8,9), fill=EMBER[1])
    save(root, 'item/attunement_sigil', image)


def gen_essence(root):
    image = canvas(); d = ImageDraw.Draw(image)
    d.polygon([(8,1),(9,4),(12,7),(12,11),(10,14),(6,14),(3,11),(4,7),(7,4)], fill=WOOD[0])
    d.polygon([(8,3),(9,6),(11,8),(11,11),(9,13),(6,12),(5,10),(6,7)], fill=EMBER[1])
    d.polygon([(8,4),(8,7),(10,9),(9,11),(7,11),(6,9),(7,6)], fill=EMBER[2])
    d.line([(7,6),(6,8),(6,9)], fill=EMBER[4])
    # Two pale bark chips remain in the harvested resin.
    d.line([(4,8),(4,11),(6,13)], fill=WOOD[3])
    d.line([(11,9),(10,12),(9,13)], fill=WOOD[1])
    d.point((5,11), fill=WOOD[5]); d.point((9,12), fill=EMBER[0])
    save(root, 'item/assimilation_essence', image)
