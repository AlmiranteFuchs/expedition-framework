"""Approved W1A, W2A and W3C assets. Generate only the selected workshop family."""
import copy
import json
from pathlib import Path
from PIL import Image, ImageDraw
from machine_surface_mesh import exposed

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/expeditions'
PALE = ['#3b352e', '#685c4d', '#94836c', '#bcac8e', '#ded0af', '#f0e4c9']


def write(path, data):
    path = ROOT / path
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')


def save(path, image):
    path = ROOT / 'textures' / path
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)


def materials():
    bark = Image.new('RGBA', (16, 16), PALE[3])
    d = ImageDraw.Draw(bark)
    for points in [
        [(2,0),(2,4),(3,6),(3,11),(2,13),(2,15)],
        [(8,0),(7,3),(7,7),(8,9),(8,15)],
        [(13,0),(13,6),(12,9),(12,15)],
    ]:
        d.line([(x+1,y) for x,y in points], fill='#d0bf9f', width=2)
        d.line(points, fill='#8f7f68')
    d.line([(4,2),(4,4),(5,5)], fill=PALE[5])
    d.line([(9,11),(10,12),(10,15)], fill=PALE[2])
    save('block/carved_bark.png', bark)
    end = Image.new('RGBA', (16,16), PALE[3])
    d = ImageDraw.Draw(end)
    for inset, shade in [(1,2),(3,4),(5,2)]:
        d.rectangle((inset,inset,15-inset,15-inset),outline=PALE[shade])
    d.line([(8,8),(9,10),(10,11),(12,12),(14,15)],fill=PALE[1])
    save('block/carved_end.png',end)
    emblem = bark.copy()
    d = ImageDraw.Draw(emblem)
    d.line([(8,13),(8,5),(6,3)],fill=PALE[1])
    d.line([(8,8),(4,6),(3,4)],fill=PALE[1])
    d.line([(8,10),(11,7),(12,4)],fill=PALE[1])
    d.line([(9,12),(9,9)],fill=PALE[4])
    save('block/carved_emblem.png',emblem)


TEXTURES = {
    'bone':'expeditions:block/carved_bark',
    'end':'expeditions:block/carved_end',
    'emblem':'expeditions:block/carved_emblem',
    'copper':'expeditions:block/graft_copper',
    'iron':'expeditions:block/machine_iron',
    'glass':'expeditions:block/machine_glass',
    'resin':'expeditions:block/machine_resin',
    'chart':'expeditions:block/terminal_chart',
    'knot':'expeditions:block/machine_knot',
    'heart':'expeditions:block/machine_heart',
    'severed':'expeditions:block/machine_severed',
    'empty':'expeditions:block/machine_empty',
    'particle':'expeditions:block/carved_bark',
}


def box(a, b, material='bone', rotation=None):
    element = {'from':a,'to':b,'faces':{}}
    spans = [b[i]-a[i] for i in range(3)]
    grain = max(range(3),key=lambda i:spans[i])
    for face in ('up','down','north','south','east','west'):
        normal = 1 if face in ('up','down') else 2 if face in ('north','south') else 0
        width,height = (spans[0],spans[2]) if normal==1 else (spans[0],spans[1]) if normal==2 else (spans[2],spans[1])
        end = material=='bone' and normal==grain
        turn = material=='bone' and not end and (grain==0 or grain==2 and normal==0)
        if turn:
            width,height=height,width
        data={'texture':'#end' if end else '#'+material,'uv':[8-width/2,8-height/2,8+width/2,8+height/2]}
        if turn:
            data['rotation']=90
        if material in ('glass','resin','empty','chart','emblem'):
            data['uv']=[0,0,16,16]
        element['faces'][face]=data
    if rotation:
        element['rotation']=rotation
    return element


def model(name, elements, specimen=None, item_model=True):
    block={'parent':'minecraft:block/block','textures':TEXTURES,
           'elements':exposed(elements),'render_type':'minecraft:cutout','ambientocclusion':True}
    write(Path(f'models/block/{name}.json'),block)
    if not item_model:
        return
    path=ROOT / f'models/item/{name}.json'
    previous=json.loads(path.read_text(encoding='utf-8')) if path.exists() else {}
    inventory=copy.deepcopy(block) if specimen else {'parent':f'expeditions:block/{name}'}
    if specimen:
        for element in inventory['elements']:
            if element.get('expeditions_collision_only'):
                continue
            for face in element['faces'].values():
                if face['texture']=='#empty':
                    face['texture']='#'+specimen
    for key in ('display','gui_light'):
        if key in previous:
            inventory[key]=previous[key]
    write(Path(f'models/item/{name}.json'),inventory)


def machines():
    # Each aperture keeps room for the existing animated core at (8,11,8).
    feet=[box([1,0,2],[5,3,6],'iron'),box([11,0,2],[15,3,6],'iron'),
          box([1,0,10],[5,3,14],'iron'),box([11,0,10],[15,3,14],'iron')]
    base=feet+[box([2,2,3],[14,4,13]),box([4,4,4],[12,5,12],'copper')]
    arch=[box([2,4,5],[4,13,11]),box([12,4,5],[14,13,11]),
          box([3,12,4],[13,14,12]),box([5,14,5],[11,16,11])]
    crown=box([5,12,3.98],[11,15,4],'emblem')
    hanger=[box([7,11,7],[9,13,9],'copper'),box([6,9,6],[10,13,10],'empty')]
    model('stillness_dynamo',base+arch+[crown]+hanger,'knot')
    # A lower refining vessel has a visible bark lid and stepped copper neck.
    condenser=base+[
        box([3,4,3],[5,11,5]),box([11,4,11],[13,11,13]),
        box([4,5,4],[12,6,12],'copper'),box([5,6,5],[11,11,11],'glass'),
        box([6,6,6],[10,8.5,10],'resin'),box([4,11,4],[12,12,12]),
        box([6,12,6],[10,13,10],'iron'),box([7,13,7],[9,16,9],'copper'),
        box([3,4,3.98],[13,7.5,4],'emblem'),
        box([1,8,7],[3,10,9],'copper'),box([1,9,7],[3,14,9],'copper'),
        box([2,13,7],[8,15,9],'copper'),
    ]
    model('essence_condenser',condenser)
    model('living_heart_engine',base+arch+[crown]+[
        box([4,5,4],[12,6,12],'copper'),box([4,13,4],[12,14,12],'copper'),
        box([4.5,6,4.5],[11.5,13,11.5],'glass'),box([6,9,6],[10,13,10],'empty'),
        box([1,6,7],[4,8,9],'copper'),
    ],'heart')
    model('severance_generator',base+arch+[crown]+[
        box([4,7,4],[12,8,6],'copper'),box([4,7,10],[12,8,12],'copper'),
        box([4,7,6],[6,8,10],'copper'),box([10,7,6],[12,8,10],'copper'),
        box([6,9,6],[10,13,10],'empty'),
    ],'severed')


def wayroot():
    legs=[box([2,0,3],[4,9,5]),box([12,0,3],[14,9,5]),
          box([2,0,11],[4,9,13]),box([12,0,11],[14,9,13])]
    desk=legs+[box([1,8,2],[15,10,14]),box([3,4,4],[13,5,12]),
               box([3,5,3],[13,8,4],'emblem'),box([7,6,2.5],[9,7,3],'copper')]
    tilt={'origin':[8,10,8],'axis':'x','angle':-22.5}
    desk += [box([3,10,4],[12,10.8,12],'bone',tilt)]
    paper=box([4,10.82,4.8],[11,10.85,11.2],'chart',tilt)
    for face,data in paper['faces'].items():
        data['texture']='#chart' if face=='up' else '#bone'
    desk += [paper,box([3.5,10.86,4.25],[11.5,11.25,4.8],'copper',tilt),
             box([12.2,10,8],[14.4,10.7,10.2],'copper'),
             box([12.5,10.7,8.3],[14.1,13,9.9],'glass'),
             box([12.8,10.7,8.6],[13.8,12,9.6],'resin'),
             box([12.5,13,8.3],[14.1,13.7,9.9],'copper')]
    model('expedition_terminal',desk)
    # Carved bark posts and a stepped crown keep the clear aperture below the collar.
    post=[box([3,0,3],[13,16,13]),box([2,0,4],[4,5,12]),box([12,0,4],[14,7,12]),
          box([2,10,5],[4,16,11]),box([12,8,4],[14,16,10]),
          box([5,1,2],[8,12,4]),box([9,6,12],[12,16,14])]
    model('graft_post',post)
    # The crown points north in this model; blockstate rotation turns it toward the collar.
    crown=[box([3,0,3],[13,5,13]),box([4,5,3],[13,10,12]),
           box([5,10,2],[13,13,11]),box([6,13,1],[12,15,10]),
           box([7,11,0],[13,14,4]),box([3,0,2],[5,7,4]),
           box([12,0,4],[14,8,10]),box([9,6,11],[12,12,13])]
    model('graft_post_crown',crown,item_model=False)
    variants={}
    for axis,turn in [('y',{}),('z',{'x':90}),('x',{'x':90,'y':90})]:
        for direction,angle in [('north',0),('east',90),('south',180),('west',270),('up',0)]:
            head=axis=='y' and direction!='up'
            variants[f'axis={axis},crown={direction}']={
                'model':'expeditions:block/graft_post_crown' if head else 'expeditions:block/graft_post',
                **({'y':angle} if head else turn),
            }
    write(Path('blockstates/graft_post.json'),{'variants':variants})
    collar=[box([0,5,5],[16,11,11]),box([5,5,0],[11,11,16]),
            box([2,10,4],[14,13,12]),box([4,10,2],[12,13,14]),
            box([4,13,4],[12,15,12]),box([6,4,6],[10,6,10],'copper'),
            box([7,2,7],[9,4,9],'resin')]
    model('graft_collar',collar)


def salvage_ui():
    # Native GUI sprites use nine-slicing so bark edges retain their pixel scale.
    image=Image.new('RGBA',(256,176),(0,0,0,0))
    d=ImageDraw.Draw(image)
    d.rectangle((4,5,251,172),fill=PALE[0])
    d.rectangle((9,10,246,166),fill='#554736')
    d.rectangle((13,13,242,162),fill='#af9a79')
    for x in (7,248):
        d.line([(x,162),(x-2,140),(x+2,119),(x,91),(x+2,65),(x-2,33),(x+2,13)],fill=PALE[2],width=7)
        d.line([(x-1,160),(x-3,140),(x+1,119),(x-1,91),(x+1,65),(x-3,33),(x+1,13)],fill=PALE[4],width=2)
    for y in (7,168):
        d.line([(8,y+2),(31,y-2),(65,y),(97,y-1),(128,y+1),(165,y-2),(209,y),(247,y-2)],fill=PALE[2],width=8)
        d.line([(11,y),(33,y-4),(64,y-2),(97,y-3),(127,y-1),(164,y-4),(208,y-2),(243,y-4)],fill=PALE[4],width=2)
    # Root forks curl around corners rather than branching through the writing area.
    for x,y,sx,sy in [(5,7,1,1),(250,7,-1,1),(5,169,1,-1),(250,169,-1,-1)]:
        d.line([(x,y+22*sy),(x+5*sx,y+12*sy),(x+13*sx,y+5*sy),(x+27*sx,y+3*sy)],fill=PALE[1],width=7)
        d.line([(x,y+21*sy),(x+5*sx,y+11*sy),(x+13*sx,y+4*sy),(x+26*sx,y+2*sy)],fill=PALE[3],width=3)
    d.rectangle((16,14,239,39),fill='#bba788')
    d.line((17,39,238,39),fill='#756149')
    save('gui/sprites/salvage_frame.png',image)
    write(Path('textures/gui/sprites/salvage_frame.png.mcmeta'), {
        'gui':{'scaling':{'type':'nine_slice','width':256,'height':176,'border':14}}})
    leaf=Image.new('RGBA',(224,32),(0,0,0,0))
    d=ImageDraw.Draw(leaf)
    d.rectangle((1,2,222,30),fill='#69563f')
    d.rectangle((2,1,221,28),fill='#c5b397')
    d.line((4,2,218,2),fill='#e0d0b1')
    d.line((4,27,218,27),fill='#9b8361')
    d.line([(219,4),(220,11),(219,16),(220,22)],fill='#ae9877')
    d.line([(4,6),(5,12),(4,20)],fill='#ae9877')
    save('gui/sprites/salvage_leaf.png',leaf)
    write(Path('textures/gui/sprites/salvage_leaf.png.mcmeta'), {
        'gui':{'scaling':{'type':'nine_slice','width':224,'height':32,'border':3}}})


def machine_ui():
    slot=Image.new('RGBA',(18,18),'#6c573f')
    d=ImageDraw.Draw(slot)
    d.rectangle((1,1,16,16),fill='#c4b092')
    d.rectangle((2,2,15,15),fill='#a38c6a')
    d.line((2,2,15,2),fill='#7a6246')
    d.line((2,3,2,15),fill='#826b4c')
    d.line((3,15,15,15),fill='#dfceb0')
    save('gui/sprites/machine_slot.png',slot)

def generate():
    materials()
    machines()
    wayroot()
    salvage_ui()
    machine_ui()


if __name__=='__main__':
    generate()
