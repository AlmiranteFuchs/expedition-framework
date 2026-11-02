"""Charter route instruments. Native 16px grain, explicit UVs, no cube casing.

Both silhouettes stay inside one route cell. FieldMachineShapes reads these
same elements for selection and collision; the open spaces are real.
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
        # An uneven cleft, held at the foot, with one visible amber splinter.
        parts = [box([4, 0, 4], [12, 2, 12], 'binding'),
                 box([5, 2, 6], [7, 13, 10]), box([5, 13, 7], [7, 16, 9]),
                 box([9, 2, 6], [11, 11, 10]), box([9, 11, 7], [11, 14, 9]),
                 box([7, 2, 7], [9, 5, 9]), box([7, 6, 7], [9, 11, 9], 'amber'),
                 box([4, 3, 5], [12, 5, 11], 'binding')]
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

