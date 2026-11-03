"""Stillwood route landmarks. Native 16px grain and actual hollow geometry.

The witness stays in one cell; the recall tree uses nine tracked cells.
FieldMachineShapes reads their same elements for selection and collision.
"""
import json
from pathlib import Path
from PIL import Image, ImageDraw

PALE = ['#38372f', '#797368', '#a5a18f', '#c6c3af', '#e0deca', '#efe9d5']


def generate(root, recall=False):
    root = Path(root)
    assets = root.parent
    bark = Image.new('RGBA', (16, 16), PALE[2]); d = ImageDraw.Draw(bark)
    # Broad longitudinal fibers; the same field runs uninterrupted up each stave.
    d.rectangle((0, 0, 3, 15), fill=PALE[3]); d.line((1, 0, 1, 15), fill=PALE[4])
    d.rectangle((12, 0, 15, 15), fill=PALE[1])
    d.line([(6, 0), (6, 5), (5, 7), (5, 15)], fill=PALE[1])
    d.line([(7, 0), (7, 5), (6, 7), (6, 14)], fill=PALE[4])
    d.line([(10, 2), (10, 9), (11, 11), (11, 15)], fill=PALE[3])
    end = Image.new('RGBA', (16, 16), PALE[2]); e = ImageDraw.Draw(end)
    for inset, color in [(1, PALE[3]), (3, PALE[1]), (5, PALE[3]), (7, PALE[2])]:
        e.rectangle((inset, inset, 15-inset, 15-inset), outline=color)
    e.line([(8, 0), (8, 5), (7, 8)], fill=PALE[0])
    binding = Image.new('RGBA', (16, 16), '#30362e'); b = ImageDraw.Draw(binding)
    for y in [1, 6, 11]:
        b.line((0, y, 15, y), fill='#666b56'); b.line((0, y+2, 15, y+2), fill='#20271f')
    b.line((2, 0, 2, 15), fill='#8a8266'); b.line((13, 0, 13, 15), fill='#222a23')
    amber = Image.new('RGBA', (16, 16), '#704523'); a = ImageDraw.Draw(amber)
    a.rectangle((3, 0, 6, 15), fill='#b57e35'); a.line((4, 0, 4, 15), fill='#dcad58')
    a.line([(10, 0), (10, 6), (9, 8), (9, 15)], fill='#966126')
    a.line((5, 4, 5, 8), fill='#ecd191')
    a.line((7, 0, 7, 15), fill='#d6a44e'); a.line((8, 4, 8, 8), fill='#ecd191')
    for name, image in [('route_bark', bark), ('route_end', end), ('route_binding', binding), ('route_amber', amber)]:
        image.save(root/'block'/f'{name}.png')

    def box(start, stop, material='bark', horizontal=False):
        x, y, z = start; X, Y, Z = stop
        faces = {}
        for face in ['north', 'south', 'east', 'west', 'up', 'down']:
            texture = 'end' if material == 'bark' and face in ['up', 'down'] else material
            # Block-space crops preserve native texel density on narrow pieces.
            uv = [x, z, X, Z] if face in ['up', 'down'] else [x if face in ['north', 'south'] else z, 16-Y, X if face in ['north', 'south'] else Z, 16-y]
            faces[face] = {'texture': '#'+texture, 'uv': uv}
            if horizontal and face not in ['up', 'down']:
                faces[face]['rotation'] = 90
        return {'from': start, 'to': stop, 'faces': faces}

    if not recall:
        # A rooted, broken sapling. No plinth, bands or manufactured uprights.
        parts = [box([3, 0, 5], [13, 3, 12]), box([5, 3, 6], [7, 13, 11]),
                 box([5, 13, 7], [7, 16, 10]), box([9, 3, 6], [12, 11, 11]),
                 box([9, 11, 7], [11, 14, 10]), box([7, 2, 8], [9, 5, 11]),
                 box([7, 6, 8], [9, 10, 10], 'amber'),
                 box([0, 0, 7], [5, 2, 10]), box([11, 0, 8], [16, 2, 12]),
                 box([6, 0, 1], [9, 2, 7]), box([9, 0, 11], [12, 1, 16])]
        name = 'expedition_checkpoint'
    else:
        # Four inward shoulders form a hollow crown; no painted portal face.
        parts = [box([2, 0, 2], [14, 2, 14], 'binding'),
                 box([4, 2, 4], [12, 3, 12]),
                 box([7, 3, 7], [9, 8, 9], 'amber')]
        for x, z in [(3, 3), (11, 3), (3, 11), (11, 11)]:
            parts.append(box([x, 2, z], [x+2, 11, z+2]))
            sx = x+1 if x == 3 else x-1
            sz = z+1 if z == 3 else z-1
            parts.append(box([sx, 11, sz], [sx+2, 14, sz+2]))
        for start, stop in [([5, 14, 5], [11, 16, 7]), ([5, 14, 9], [11, 16, 11]),
                            ([5, 14, 7], [7, 16, 9]), ([9, 14, 7], [11, 16, 9])]:
            parts.append(box(start, stop, horizontal=True))
        name = 'expedition_exit'
    model = {'parent': 'minecraft:block/block', 'ambientocclusion': True,
             'textures': {key: 'expeditions:block/route_'+key for key in ['bark', 'end', 'binding', 'amber']},
             'elements': parts}
    model['textures']['particle'] = model['textures']['bark']
    (assets/'models/block'/f'{name}.json').write_text(json.dumps(model, indent=2)+'\n', encoding='utf-8')
    if recall:
        tree_models(assets, box, model['textures'])


def tree_models(assets, box, textures):
    """Nine actual block cells make a three-wide, three-high hollow trunk."""
    # Thin falling resin threads, not an opaque picture filling the hollow.
    frames = Image.new('RGBA', (16, 64), (37, 41, 33, 35))
    for frame in range(4):
        d = ImageDraw.Draw(frames)
        for x in [1, 4, 11, 14]:
            y = (x*3 + frame*3) % 16
            for dy in range(5):
                yy = frame*16 + (y+dy) % 16
                d.point((x, yy), fill=(184, 146, 83, 65 + dy*12))
    frames.save(assets/'textures/block/stillwood_veil.png')
    (assets/'textures/block/stillwood_veil.png.mcmeta').write_text(json.dumps({
        'animation': {'frametime': 10, 'interpolate': True}})+'\n', encoding='utf-8')
    twig = Image.new('RGBA', (16,16)); t = ImageDraw.Draw(twig)
    for points in [[(1,14),(5,10),(8,9),(11,5),(14,2)], [(5,10),(3,6),(1,5)],
                   [(8,9),(12,10),(15,8)], [(11,5),(8,3),(7,0)]]:
        t.line(points,fill=PALE[1],width=2);t.line(points,fill=PALE[3])
    twig.save(assets/'textures/block/stillwood_twigs.png')
    tex = dict(textures, veil='expeditions:block/stillwood_veil', twig='expeditions:block/stillwood_twigs')

    def veil():
        return {'from':[0,0,7.99], 'to':[16,16,8.01], 'shade':False,
                'faces':{f:{'texture':'#veil','uv':[0,0,16,16]} for f in ['north','south']}}

    def sprig(z):
        return {'from':[0,0,z], 'to':[16,16,z+.01],
                'faces':{f:{'texture':'#twig','uv':[0,0,16,16]} for f in ['north','south']}}

    root = [box([0,0,4],[2,3,13]),box([14,0,5],[16,2,14]),veil()]
    # Roots flare out; the trunk narrows unevenly around a human-sized hollow.
    parts = [
        [box([3,0,4],[16,5,14]),box([7,5,5],[16,16,13]),box([0,0,8],[5,2,12]),box([6,0,0],[10,2,6])],
        [box([0,0,3],[12,4,14]),box([0,4,5],[9,16,13]),box([10,0,8],[16,2,11]),box([4,0,12],[7,2,16])],
        [box([8,0,5],[16,12,13]),box([10,12,6],[16,16,13]),box([11,2,4],[16,10,5],'amber')],
        [box([0,0,5],[9,9,13]),box([0,9,6],[7,16,13]),box([0,3,4],[3,10,5],'amber')],
        [box([8,0,4],[16,10,14]),box([10,10,5],[16,13,14]),box([5,7,6],[10,11,13]),box([3,10,7],[7,14,12]),sprig(9)],
        [box([0,0,4],[16,10,14]),box([1,10,5],[13,13,14]),box([4,13,6],[11,16,13]),
         box([2,1,3],[5,9,4]),box([11,0,3],[14,6,4]),box([6,2,3],[8,6,4],'amber')],
        [box([0,0,4],[9,9,14]),box([0,9,5],[6,12,14]),box([7,6,6],[12,10,13]),box([10,9,7],[14,13,12]),sprig(10)],
        [veil()]
    ]
    def write(name, elements, translucent=False):
        model={'parent':'minecraft:block/block','textures':tex,'ambientocclusion':True,
               'render_type':'minecraft:translucent' if translucent else 'minecraft:cutout','elements':elements}
        (assets/'models/block'/f'{name}.json').write_text(json.dumps(model,indent=2)+'\n',encoding='utf-8')
    write('recall_tree_root',root,True)
    for index,elements in enumerate(parts):write('recall_tree_'+str(index),elements,index==7)
    (assets/'blockstates/recall_tree_part.json').write_text(json.dumps({'variants':{
        'part='+str(i):{'model':'expeditions:block/recall_tree_'+str(i)} for i in range(8)}},indent=2)+'\n',encoding='utf-8')
    (assets/'blockstates/expedition_exit.json').write_text(json.dumps({'variants':{
        'grown=false':{'model':'expeditions:block/expedition_exit'},
        'grown=true':{'model':'expeditions:block/recall_tree_root'}}},indent=2)+'\n',encoding='utf-8')
    # The carried icon shows the whole hollow, not the legacy compact instrument.
    import copy
    miniature=[]
    offsets=[(-1,0,0),(1,0,0),(-1,1,0),(1,1,0),(-1,2,0),(0,2,0),(1,2,0),(0,1,0)]
    for elements,offset in [(root,(0,0,0))]+list(zip(parts,offsets)):
        for element in elements:
            if any(face['texture']=='#veil' for face in element['faces'].values()):continue
            e=copy.deepcopy(element)
            for key in ['from','to']:
                e[key]=[(value+offset[axis]*16)/3+(16/3 if axis!=1 else 0) for axis,value in enumerate(e[key])]
            miniature.append(e)
    (assets/'models/item/expedition_exit.json').write_text(json.dumps({
        'parent':'minecraft:block/block','render_type':'minecraft:cutout','textures':tex,
        'elements':miniature},indent=2)+'\n',encoding='utf-8')
    from stillwood_tree_art import generate_art
    generate_art(assets)

