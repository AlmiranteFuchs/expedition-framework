"""Native 16px field instruments and open-frame models. Never touches approved Bloom atlases."""
import json
from math import sin, tau
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/expeditions'
PALE=['#34332d','#756b59','#a6977c','#d1c09c','#f2e4bf','#fff1d1']
METAL=['#242622','#353931','#59594e','#b4ad98']
COPPER=['#423026','#795038','#ac754b','#d6a16a','#f0c38b']
AMBER=['#6b381e','#ac5e22','#e49a35','#ffcf66','#fff0b2']

def write(path,data):
    path=ROOT/path;path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')

def save(name,image):
    p=ROOT/'textures'/f'{name}.png';p.parent.mkdir(parents=True,exist_ok=True);image.save(p)

def panel(palette):
    # Calm material fields; joins/rivets are geometry, not repeated stamps on every face.
    im=Image.new('RGBA',(16,16),palette[2]);d=ImageDraw.Draw(im)
    d.line((0,0,15,0),fill=palette[-1]);d.line((0,0,0,15),fill=palette[3])
    d.line((15,1,15,15),fill=palette[0]);d.line((1,15,15,15),fill=palette[1])
    if palette is PALE:
        d.line([(6,0),(6,5),(7,7),(7,15)],fill=palette[1]);d.line([(8,0),(8,5),(9,8),(9,15)],fill=palette[3])
        d.line((11,2,11,12),fill=palette[2]);d.point((5,8),fill=palette[3])
    elif palette is COPPER:
        d.rectangle((3,1,12,2),fill=palette[3]);d.line((2,3,2,12),fill=palette[1]);d.line((13,4,13,12),fill=palette[3])
    else:
        d.line((2,2,13,2),fill=palette[3]);d.line((2,3,2,13),fill=palette[1]);d.point((12,13),fill=palette[1])
    return im


def item(name,draw):
    im=Image.new('RGBA',(16,16));draw(ImageDraw.Draw(im));save('item/'+name,im)
    write(Path('models/item/'+name+'.json'),{'parent':'minecraft:item/generated','textures':{'layer0':'expeditions:item/'+name}})

def graft_materials():
    """Carved bark clusters and quiet mineral patina for the doorway."""
    bark = ['#443e35', '#63594a', '#897a65', '#ab9a80', '#c8b99b', '#dfd2b4', '#f1e6c8']
    for name, shift in [('graft_bark', 0), ('graft_bark_turn', 4)]:
        im = Image.new('RGBA', (16, 16), bark[4])
        d = ImageDraw.Draw(im)
        for x in range(16):
            d.line((x, 0, x, 15), fill=bark[3 if (x + shift) % 7 in (0, 1) else 4])
        for x in (2, 8, 13):
            x = (x + shift) % 16
            points = [(x,0),(x,3),((x+1)%16,5),((x+1)%16,9),(x,12),(x,15)]
            if x == 15:
                points = [(15,0),(15,15)]
            d.line([(min(15, a+1), b) for a,b in points], fill=bark[5], width=2)
            d.line(points, fill=bark[1], width=1)
        x = 5 if shift == 0 else 10
        d.polygon([(x,2),(x+2,3),(x+2,5),(x+1,7),(x-1,9),(x-1,5)], fill=bark[5])
        d.line([(x,2),(x,5),(x-1,7),(x-1,9)], fill=bark[6])
        d.line([(x+2,4),(x+2,6),(x,9),(x,12)], fill=bark[2])
        d.line([(x+4,7),(x+3,9),(x+3,11),(x+4,12)], fill=bark[0])
        d.line([(x+4,9),(x+4,11),(x+5,12)], fill=bark[3])
        for point in [(x+1,4),(x+1,5),(x,6),(x+5,13)]:
            d.point(point, fill=bark[6])
        save('block/'+name, im)

    rings = Image.new('RGBA',(16,16),bark[2]);d = ImageDraw.Draw(rings)
    d.polygon([(2,2),(10,1),(14,4),(15,10),(12,14),(4,14),(1,10),(1,5)],fill=bark[5])
    d.line([(2,7),(4,4),(9,3),(12,5),(13,9),(10,12),(5,12),(3,10)],fill=bark[3],width=1)
    d.line([(5,7),(7,5),(10,6),(11,9),(9,10),(6,10),(5,9)],fill=bark[3],width=1)
    d.line([(8,7),(8,9),(10,11),(12,14)],fill=bark[1])
    d.point((7,7),fill=bark[6])
    save('block/graft_end_grain', rings)

    copper = Image.new('RGBA',(16,16),'#9b633f');d = ImageDraw.Draw(copper)
    d.polygon([(0,0),(15,0),(15,3),(11,4),(6,3),(2,5),(0,5)],fill='#ba8154')
    d.polygon([(0,14),(5,11),(10,12),(15,9),(15,15),(0,15)],fill='#6b4734')
    d.line([(1,2),(7,2),(8,3),(13,3)],fill='#d2a16c')
    d.line([(3,12),(5,10),(8,10),(10,8)],fill='#815035')
    d.line([(4,11),(5,11),(6,10)],fill='#82775b')
    d.line([(10,9),(11,8),(13,8)],fill='#6c7560')
    d.point((9,7),fill='#af8557')
    save('block/graft_copper', copper)


def sap_liquids():
    """Tileable amber ripples with a slower, directional animation on slopes."""
    palette = [(111, 52, 24, 181), (145, 70, 29, 182), (174, 92, 39, 188),
               (199, 118, 52, 193), (221, 153, 73, 204), (242, 193, 115, 222)]
    for name, size in [('still', 16), ('flow', 32), ('side', 16)]:
        colors = palette if name != 'still' else [
            (138, 69, 30, 151), (147, 75, 32, 155), (154, 82, 36, 159),
            (163, 89, 40, 167), (195, 121, 56, 183), (229, 163, 81, 202),
        ]
        frames = 16
        image = Image.new('RGBA', (size, size * frames))
        for frame in range(frames):
            phase = tau * frame / frames
            for y in range(size):
                for x in range(size):
                    u, v = tau * x / size, tau * y / size
                    if name == 'flow':
                        wave = sin(3*u + 0.65*sin(v + phase)) + 0.42*sin(2*v - phase + u)
                        sheen = sin(2*u + v - phase)
                    elif name == 'side':
                        wave = sin(2*v + 0.50*sin(u + phase)) + 0.48*sin(u + v + phase)
                        sheen = sin(3*u - v + phase)
                    else:
                        wave = sin(v + 0.78*sin(u - phase)) + 0.30*sin(u - v + phase)
                        sheen = sin(u - 2*v + phase)
                    color = 0 if wave < -1.1 else 1 if wave < -0.45 else 2 if wave < 0.35 else 3
                    if wave > 0.92 and sheen > (0.82 if name == 'still' else 0.3):
                        color = 5 if wave > 1.18 and sheen > 0.87 else 4
                    image.putpixel((x, frame * size + y), colors[color])
        asset = f'block/return_sap_{name}'
        save(asset, image)
        write(Path(f'textures/{asset}.png.mcmeta'), {
            'animation': {'frametime': 2 if name == 'flow' else 4, 'interpolate': True}
        })

    # A flat reflective surface on top and a deeper, banded body below the meniscus.
    for name, x, base, step in [('reclaiming_basin', 4.125, 5.4, .4), ('sap_jar', 4.15, 3.25, 2.25)]:
        parts = [{'apply': {'model': f'expeditions:block/{name}'}}]
        jar = name == 'sap_jar'
        for fill in range(1, 5):
            model = f'{name}_sap_{fill}'
            faces = {face: {'texture': '#surface' if face == 'up' else '#side', 'uv': [0, 0, 16, 16]}
                     for face in ('up', 'north', 'east', 'south', 'west')}
            write(Path(f'models/block/{model}.json'), {
                'parent': 'minecraft:block/block', 'render_type': 'minecraft:translucent',
                'ambientocclusion': False,
                'textures': {'surface': 'expeditions:block/return_sap_still',
                             'side': 'expeditions:block/return_sap_side'},
                'elements': [{'from': [x, base, x], 'to': [16-x, round(base+fill*step, 3), 16-x],
                              'faces': faces}],
            })
            condition = {'sap_level': str(fill)}
            if jar:
                condition['water'] = 'false'
            parts.append({'when': condition, 'apply': {'model': f'expeditions:block/{model}'}})
        if jar:
            parts.append({'when': {'water': 'true'}, 'apply': {'model': 'expeditions:block/sap_jar_water'}})
        write(Path(f'blockstates/{name}.json'), {'multipart': parts})
    write(Path('models/block/sap_jar_water.json'), {
        'parent': 'expeditions:block/sap_jar_sap_1',
        'textures': {'surface': 'minecraft:block/ice', 'side': 'minecraft:block/ice'},
    })
    write(Path('models/block/return_sap.json'), {'textures': {'particle': 'expeditions:block/return_sap_still'}})


def gen():
    graft_materials()
    for name,pal in [('machine_bone',PALE),('machine_copper',COPPER),('machine_iron',METAL)]:save('block/'+name,panel(pal))
    end=Image.new('RGBA',(16,16),PALE[2]);e=ImageDraw.Draw(end)
    for inset,color in [(1,PALE[1]),(3,PALE[3]),(5,PALE[1]),(7,PALE[4])]:e.rectangle((inset,inset,15-inset,15-inset),outline=color)
    save('block/wood_end',end)
    for name,live in [('machine_knot',False),('machine_heart',True),('machine_severed',True)]:
        im=Image.new('RGBA',(16,16),PALE[1]);d=ImageDraw.Draw(im)
        d.polygon([(0,3),(7,0),(15,4),(13,14),(5,15),(1,10)],fill=PALE[3]);d.line([(1,4),(6,2),(11,4),(10,12),(5,13),(3,8)],fill=PALE[4],width=2)
        d.line([(6,3),(10,6),(8,12)],fill=PALE[0],width=2)
        if live:d.rectangle((7,6,8,10),fill=AMBER[2]);d.point((7,6),fill=AMBER[4])
        save('block/'+name,im)
    item('dormant_knot',lambda d:[d.polygon([(8,1),(13,5),(14,10),(8,15),(2,11),(1,6)],fill=PALE[0]),d.polygon([(8,2),(12,6),(11,11),(7,13),(3,9),(4,5)],fill=PALE[3]),d.line([(7,4),(10,7),(7,11)],fill=PALE[1],width=2)])
    item('severance_core',lambda d:[d.polygon([(8,0),(14,6),(14,10),(8,15),(1,9),(2,5)],fill=METAL[0]),d.polygon([(8,2),(12,7),(8,13),(3,7)],fill=PALE[3]),d.line((8,3,4,7),fill=PALE[5]),d.rectangle((7,5,9,10),fill=AMBER[2]),d.point((7,5),fill=AMBER[4])])
    glass=Image.new('RGBA',(16,16),(0,0,0,0));g=ImageDraw.Draw(glass);g.rectangle((0,0,15,15),outline='#889b8f');g.line((2,2,2,13),fill='#e5eee0');g.line((3,2,3,7),fill='#b9cabb');save('block/machine_glass',glass)
    resin=panel(AMBER);save('block/machine_resin',resin)
    chart=Image.new('RGBA',(16,16),PALE[4]);d=ImageDraw.Draw(chart)
    d.line([(0,0),(0,13),(2,15),(15,15)],fill=PALE[2])
    d.line([(1,0),(13,0),(15,2)],fill=PALE[5])
    d.line([(3,12),(6,9),(10,10),(12,6),(14,5)],fill=COPPER[1])
    for x,y in ((3,12),(6,9),(10,10),(12,6)):d.point((x,y),fill=AMBER[2])
    for y,length in ((3,7),(4,4),(6,3),(13,6)):d.line((2,y,2+length,y),fill=PALE[1])
    save('block/terminal_chart',chart)
    item('dormant_flake',lambda d:[(d.polygon([(x,y),(x+3,y-2),(x+1,y+2),(x+3,y+3),(x,y+5),(x-2,y+3)],fill=PALE[0]),d.line([(x+2,y-1),(x,y+2),(x+1,y+3),(x-1,y+3)],fill=PALE[4],width=2)) for x,y in ((4,4),(10,7),(5,10))])
    def essence(d):
        d.polygon([(8,0),(11,4),(14,9),(13,13),(9,15),(4,14),(2,11),(4,6)],fill=PALE[0]);d.line([(8,1),(5,7),(3,11),(5,13),(8,14)],fill=PALE[4],width=2)
        d.polygon([(9,4),(12,9),(11,12),(8,14),(6,12),(6,9)],fill=AMBER[1]);d.polygon([(9,6),(10,9),(9,12),(7,11),(7,9)],fill=AMBER[3]);d.point((8,9),fill=AMBER[4])
    item('assimilation_essence',essence)
    def sigil(d):
        d.polygon([(8,1),(14,7),(8,14),(1,7)],fill=PALE[0]);d.polygon([(8,2),(13,7),(8,12),(3,7)],fill=PALE[3]);d.line((8,3,4,7),fill=PALE[5]);d.rectangle((6,5,9,8),fill=COPPER[0]);d.polygon([(8,5),(10,7),(8,9),(6,7)],fill=AMBER[2]);d.point((8,6),fill=AMBER[4])
        for box in [(0,6,3,8),(12,6,15,8),(7,0,9,2),(7,12,9,15)]:d.rectangle(box,fill=COPPER[1]);d.point((box[0],box[1]),fill=COPPER[3])
    item('attunement_sigil',sigil)
    def assay(d):
        d.line((1,14,14,1),fill=METAL[0],width=3);d.line((2,13,5,10),fill=COPPER[2],width=2);d.rectangle((5,4,11,10),fill=METAL[0]);d.rectangle((6,5,10,9),fill=PALE[3]);d.line((6,5,9,5),fill=PALE[5]);d.rectangle((5,3,9,4),fill=COPPER[2]);d.rectangle((10,7,12,9),fill=COPPER[1]);d.line((11,4,14,1),fill='#8c3d2b',width=2);d.point((13,1),fill='#e0a18a');d.line((0,13,2,15),fill=METAL[2])
    item('field_assay_kit',assay)
    # Ordinary cube part textures; open space comes from real model geometry.
    textures={k:'expeditions:block/machine_'+v for k,v in [('bone','bone'),('copper','copper'),('iron','iron'),('glass','glass'),('resin','resin')]};textures['chart']='expeditions:block/terminal_chart';textures['end']='expeditions:block/wood_end';textures['knot']='expeditions:block/machine_knot';textures['heart']='expeditions:block/machine_heart';textures['severed']='expeditions:block/machine_severed';textures['empty']='expeditions:block/machine_empty';textures['particle']=textures['bone']
    save('block/machine_empty',Image.new('RGBA',(16,16),(0,0,0,0)))
    def box(a,b,tex='bone',rotation=None):
        spans=[b[i]-a[i] for i in range(3)];axis=max(range(3),key=lambda i:spans[i])
        e={'from':a,'to':b,'faces':{}}
        for face in ['down','up','north','south','west','east']:
            normal=1 if face in ('down','up') else 2 if face in ('north','south') else 0
            width,height=(spans[0],spans[2]) if normal==1 else (spans[0],spans[1]) if normal==2 else (spans[2],spans[1])
            endface=tex=='bone' and normal==axis
            rotate=tex=='bone' and not endface and (axis==0 or axis==2 and normal==0)
            if rotate:width,height=height,width
            f={'texture':'#end' if endface else '#'+tex,'uv':[8-width/2,8-height/2,8+width/2,8+height/2]}
            if rotate:f['rotation']=90
            if tex in ('glass','resin','knot','empty'):f['uv']=[0,0,16,16]
            e['faces'][face]=f
        if rotation:e['rotation']=rotation
        return e
    base=[box([1,1,2],[15,3,14]),box([0,0,1],[3,3,4],'iron'),box([13,0,1],[16,3,4],'iron'),box([0,0,12],[3,3,15],'iron'),box([13,0,12],[16,3,15],'iron')]
    # A fork and suspended knot replace the borrowed flywheel silhouette.
    dynamo=base+[box([2,3,6.25],[4,13,9.75],'bone',{'origin':[3,4,8],'axis':'z','angle':-22.5}),
        box([12,3,6.25],[14,13,9.75],'bone',{'origin':[13,4,8],'axis':'z','angle':22.5}),
        box([3,3,6],[13,5,10],'iron'),box([4,12,6],[6,14,10],'copper'),box([10,12,6],[12,14,10],'copper'),
        box([6,9,6],[10,13,10],'empty'),box([6,3,10],[10,6,13],'copper')]
    living=base+[box([4,4,4],[12,5,12],'copper'),box([4,14,4],[12,15,12],'copper'),box([5,5,5],[11,14,11],'glass'),
        box([3,3,4],[4,15,5],'iron'),box([12,3,11],[13,15,12],'iron'),box([1,5,6],[3,11,9],'copper'),
        box([2,10,7],[6,12,9],'copper'),box([6,9,6],[10,13,10],'empty')]
    severed=base+[box([2,3,5],[4,12,8],'bone',{'origin':[3,3,6],'axis':'z','angle':-22.5}),
        box([12,3,5],[14,12,8],'bone',{'origin':[13,3,6],'axis':'z','angle':22.5}),
        box([7,3,11],[9,15,13],'bone'),box([4,7,4],[12,8,6],'copper'),box([4,7,10],[12,8,12],'copper'),
        box([4,7,6],[6,8,10],'copper'),box([10,7,6],[12,8,10],'copper'),box([6,9,6],[10,13,10],'empty')]
    condenser=base+[box([8,3,6],[10,5,8],'copper'),box([6,5,4],[12,6,10],'copper'),box([6,13,4],[12,14,10],'copper'),box([7,6,5],[11,13,9],'glass'),box([8,6,6],[10,10,8],'resin'),box([5,4,4],[6,14,5]),box([12,4,9],[13,14,10]),box([8,14,6],[10,16,8],'iron'),box([3,13,6],[8,15,8],'copper'),box([2,8,6],[4,14,8],'copper'),box([2,4,6],[4,8,8]),box([3,7,5],[5,8,7],'copper'),box([3,3,4],[6,5,7],'glass'),box([4,3,5],[5,4,6],'resin')]
    terminal=[box([1,0,2],[5,2,6],'iron'),box([11,0,10],[15,2,14],'iron'),box([2,2,3],[4,9,5]),box([12,2,11],[14,11,13]),box([3,3,4],[13,5,12]),box([.25,8,3],[15.75,9,14],'bone',{'origin':[8,8,8],'axis':'x','angle':-45}),box([1,9,4],[15,9.125,13],'chart',{'origin':[8,8,8],'axis':'x','angle':-45}),box([14,7,8],[16,9,10],'copper')]
    tilt=terminal[5]['rotation']
    terminal[6]=box([4,9,4],[13,9.125,12],'chart',tilt)
    for face,data in terminal[6]['faces'].items():
        data['texture']='#chart' if face=='up' else '#bone'
        if face=='up':data['uv']=[0,0,16,16]
    # Paper clamp, resin seal, split graft and lever all follow the desk angle.
    terminal[7:]=[
        box([3.5,9.125,4.25],[13.5,9.65,5.15],'copper',tilt),
        box([10,9.125,10],[12.5,9.5,11.75],'resin',tilt),
        box([1,9,6],[3.25,10.2,12],'bone',tilt),
        box([1.25,10.2,8.25],[3,11.75,11.25],'bone',tilt),
        box([1.65,11.75,9],[2.65,13.25,10.5],'resin',tilt),
        box([.75,11.75,8.65],[1.65,14,10.85],'bone',tilt),
        box([2.65,11.75,8.65],[3.55,14,10.85],'bone',tilt),
        box([13.7,9,8],[15.2,10,11],'copper',tilt),
        box([14.1,10,9],[14.8,12.1,9.8],'copper',tilt),
        box([13.65,12.1,8.65],[15.25,12.8,10.15],'bone',tilt),
    ]
    from machine_surface_mesh import exposed
    basin=[
        box([1,0,1],[4,2,4]), box([12,0,1],[15,2,4]),
        box([1,0,12],[4,2,15]), box([12,0,12],[15,2,15]),
        box([2,2,2],[14,4,14]), box([3,4,3],[13,5,13],'copper'),
        box([2,5,2],[14,7,4],'copper'), box([2,5,12],[14,7,14],'copper'),
        box([2,5,4],[4,7,12],'copper'), box([12,5,4],[14,7,12],'copper'),
        box([1,7,1],[15,8,3],'copper'), box([1,7,13],[15,8,15],'copper'),
        box([1,7,3],[3,8,13],'copper'), box([13,7,3],[15,8,13],'copper'),
        box([6,5,6],[10,5.35,10],'resin'),
        box([1,4,5],[3,9,8]), box([13,4,8],[15,9,11]),
        box([4,4,13],[7,9,15]),
    ]
    write(Path('models/block/reclaiming_basin.json'),{
        'parent':'minecraft:block/block','textures':textures,'elements':exposed(basin),
        'render_type':'minecraft:cutout','ambientocclusion':False,
    })
    graft_textures = dict(textures, bone='expeditions:block/graft_bark',
                          bark_turn='expeditions:block/graft_bark_turn',
                          end='expeditions:block/graft_end_grain',
                          copper='expeditions:block/graft_copper',
                          particle='expeditions:block/graft_bark')
    collar=[
        box([0,5,4],[16,11,12],'bone'), box([4,5,0],[12,11,16],'bone'),
        box([2,4,3],[5,12,13],'copper'), box([11,4,3],[14,12,13],'copper'),
        box([5,4,2],[11,12,4],'copper'), box([5,4,12],[11,12,14],'copper'),
        box([5,3,6],[11,5,10],'copper'), box([6,2,6.5],[10,3,9.5],'resin'),
        box([5,8,3],[11,9,4],'iron'), box([7,6,2.5],[9,10,3],'resin'),
        box([5,8,12],[11,9,13],'iron'), box([7,6,13],[9,10,13.5],'resin'),
    ]
    write(Path('models/block/graft_collar.json'),{
        'parent':'minecraft:block/block','textures':graft_textures,'elements':exposed(collar),
        'render_type':'minecraft:cutout','ambientocclusion':False,
    })
    write(Path('models/item/graft_collar.json'),{'parent':'expeditions:block/graft_collar'})
    write(Path('blockstates/graft_collar.json'),{'variants':{'':{'model':'expeditions:block/graft_collar'}}})
    post=[
        box([2,0,2],[14,16,14]),
        box([1,0,5],[3,16,10]), box([13,0,6],[15,16,11]),
        box([5,0,1],[10,16,3]), box([6,0,13],[11,16,15]),
        box([4,2,.5],[6,12,2]), box([10,6,14],[12,15,15.5]),
    ]
    # Map the grain by position, so ribs share the trunk's pattern instead of
    # repeating the same sliver on every face.
    for part in post:
        low, high = part['from'], part['to']
        for face, data in part['faces'].items():
            if face in ('up','down'):
                data.update(texture='#end', uv=[low[0],low[2],high[0],high[2]])
            else:
                axis = 0 if face in ('north','south') else 2
                start, end = low[axis], high[axis]
                if face in ('north','east'):
                    start, end = 16-end, 16-start
                data.update(texture='#bone' if axis == 0 else '#bark_turn',
                            uv=[start,16-high[1],end,16-low[1]])
    write(Path('models/block/graft_post.json'),{
        'parent':'minecraft:block/block','textures':graft_textures,'elements':exposed(post),
        'render_type':'minecraft:cutout','ambientocclusion':False,
    })
    write(Path('models/item/graft_post.json'),{'parent':'expeditions:block/graft_post'})
    write(Path('blockstates/graft_post.json'),{'variants':{
        'axis=y':{'model':'expeditions:block/graft_post'},
        'axis=z':{'model':'expeditions:block/graft_post','x':90},
        'axis=x':{'model':'expeditions:block/graft_post','x':90,'y':90},
    }})
    jar=[
        box([2,0,2],[14,2,14],'bone'),
        box([3,2,3],[13,3,13],'copper'), box([3,13,3],[13,14,13],'copper'),
        box([4,3,4],[12,13,12],'glass'),
        box([2,2,2],[4,14,4]), box([12,2,2],[14,14,4]),
        box([2,2,12],[4,14,14]), box([12,2,12],[14,14,14]),
        box([6,14,6],[10,15,10],'copper'),
    ]
    wick=[
        box([5,5,0],[11,11,16]),
        box([4,4,0],[12,12,2],'copper'), box([4.5,4.5,14],[11.5,11.5,16]),
        box([7,7,0],[9,9,0.25],'resin'), box([7,7,15.75],[9,9,16],'resin'),
        box([6,11,9],[10,12,12],'copper'),
    ]
    for name,parts in [('sap_jar',jar),('rootwick',wick)]:
        write(Path('models/block/'+name+'.json'),{
            'parent':'minecraft:block/block','textures':textures,'elements':exposed(parts),
            'render_type':'minecraft:cutout','ambientocclusion':False,
        })
        write(Path('models/item/'+name+'.json'),{'parent':'expeditions:block/'+name})
    variants={}
    for direction,rotation in [('north',{}),('east',{'y':90}),('south',{'y':180}),('west',{'y':270}),
                               ('down',{'x':90}),('up',{'x':270})]:
        variants['facing='+direction]={'model':'expeditions:block/rootwick',**rotation}
    write(Path('blockstates/rootwick.json'),{'variants':variants})
    sap_liquids()
    item('return_sap_bucket',lambda d:[
        d.polygon([(2,4),(13,4),(12,13),(10,15),(5,15),(3,12)],fill=METAL[0]),
        d.polygon([(3,5),(12,5),(11,13),(5,13)],fill=METAL[3]),
        d.ellipse((2,2,13,7),fill=METAL[0]),
        d.ellipse((3,3,12,6),fill=AMBER[2]),
        d.line((4,4,8,4),fill=AMBER[4]),d.line((5,8,6,12),fill=PALE[4]),
    ])
    for name,elements in [('stillness_dynamo',dynamo),('living_heart_engine',living),('severance_generator',severed),('essence_condenser',condenser),('expedition_terminal',terminal)]:
        elements=exposed(elements)
        model={'parent':'minecraft:block/block','textures':textures,'elements':elements,'render_type':'minecraft:cutout','ambientocclusion':False}
        write(Path('models/block/'+name+'.json'),model)
        if name in ('stillness_dynamo','living_heart_engine','severance_generator'):
            import copy
            item_model=copy.deepcopy(model)
            for element in item_model['elements']:
                if element.get('expeditions_collision_only'):continue
                for face in element['faces'].values():
                    if face['texture']=='#empty':face['texture']='#heart' if name=='living_heart_engine' else '#severed' if name=='severance_generator' else '#knot'
            write(Path('models/item/'+name+'.json'),item_model)
        else:write(Path('models/item/'+name+'.json'),{'parent':'expeditions:block/'+name})
        variants={}
        for face,angle in [('north',0),('east',90),('south',180),('west',270)]:
            if name=='expedition_terminal':variants['facing='+face]={'model':'expeditions:block/'+name,'y':angle}
            else:
                for lit in ['true','false']:variants['facing='+face+',lit='+lit]={'model':'expeditions:block/'+name,'y':angle}
        write(Path('blockstates/'+name+'.json'),{'variants':variants})
    write(Path('models/block/dormant_layer.json'),{'parent':'expeditions:block/assimilation_layer'})
    write(Path('blockstates/dormant_layer.json'),{'variants':{'':{'model':'expeditions:block/dormant_layer'}}})
    save('block/dormant_heart',panel(PALE))
    write(Path('models/block/dormant_heart.json'),{'parent':'minecraft:block/cube_all','textures':{'all':'expeditions:block/dormant_heart'}})
    write(Path('blockstates/dormant_heart.json'),{'variants':{'':{'model':'expeditions:block/dormant_heart'}}})
    from item_art import field_specimens
    field_specimens()
    gui=Image.new('RGBA',(176,166),PALE[3]);d=ImageDraw.Draw(gui);d.rectangle((0,0,175,165),outline=METAL[0],width=3);d.rectangle((4,4,171,161),outline=COPPER[1]);d.line((5,17,170,17),fill=PALE[1]);d.rectangle((10,19,20,68),fill=METAL[0]);d.rectangle((11,20,19,67),outline=COPPER[1])
    for x,y in [(52,34),(112,34)]+[(7+c*18,83+r*18) for r in range(3) for c in range(9)]+[(7+c*18,141) for c in range(9)]:d.rectangle((x,y,x+17,y+17),fill=PALE[1]);d.rectangle((x+1,y+1,x+16,y+16),fill=PALE[2]);d.line((x+1,y+17,x+17,y+17),fill=PALE[5])
    d.line((80,42,103,42),fill=PALE[1],width=5);d.polygon([(99,36),(107,42),(99,48)],fill=PALE[1]);save('gui/resonance',gui)

    from field_equipment_art import gen as field_art
    field_art()
    from bloom_source_art import generate as bloom_source
    bloom_source()
    from item_art import gen as item_art
    item_art()
    from approved_workshop_art import generate as approved_workshop
    approved_workshop()

if __name__=='__main__':gen()
