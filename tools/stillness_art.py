"""Native 16px field instruments and open-frame models. Never touches approved Bloom atlases."""
import json
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

def gen():
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
    chart=Image.new('RGBA',(16,16),PALE[3]);d=ImageDraw.Draw(chart);d.rectangle((0,0,15,15),outline=METAL[0]);d.rectangle((1,1,14,14),outline=COPPER[2]);d.line((3,3,12,3),fill=PALE[5]);d.line([(3,12),(5,9),(9,9),(12,5)],fill=COPPER[1],width=1)
    for x,y in ((3,12),(5,9),(9,9),(12,5)):d.point((x,y),fill=AMBER[2])
    d.line((3,5,6,5),fill=PALE[1]);d.line((3,6,5,6),fill=PALE[2]);save('block/terminal_chart',chart)
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
    dynamo=base+[box([2,3,6],[4,13,10],'bone',{'origin':[3,4,8],'axis':'z','angle':-22.5}),
        box([12,3,6],[14,13,10],'bone',{'origin':[13,4,8],'axis':'z','angle':22.5}),
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
    terminal=[box([1,0,2],[5,2,6],'iron'),box([11,0,10],[15,2,14],'iron'),box([2,2,3],[4,9,5]),box([12,2,11],[14,11,13]),box([3,3,4],[13,5,12]),box([0,8,3],[16,9,14],'bone',{'origin':[8,8,8],'axis':'x','angle':-45}),box([1,9.01,4],[15,9.05,13],'chart',{'origin':[8,8,8],'axis':'x','angle':-45}),box([14,7,8],[16,9,10],'copper')]
    for face in terminal[6]['faces']:
        terminal[6]['faces'][face]['texture']='#chart' if face=='up' else '#bone'
    for name,elements in [('stillness_dynamo',dynamo),('living_heart_engine',living),('severance_generator',severed),('essence_condenser',condenser),('expedition_terminal',terminal)]:
        model={'parent':'minecraft:block/block','textures':textures,'elements':elements,'render_type':'minecraft:cutout','ambientocclusion':False}
        write(Path('models/block/'+name+'.json'),model)
        if name in ('stillness_dynamo','living_heart_engine','severance_generator'):
            import copy
            item_model=copy.deepcopy(model)
            for element in item_model['elements']:
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
    write(Path('models/item/dormant_layer.json'),{'parent':'minecraft:item/generated','textures':{'layer0':'expeditions:item/dormant_flake'}})
    save('block/dormant_heart',panel(PALE))
    write(Path('models/block/dormant_heart.json'),{'parent':'minecraft:block/cube_all','textures':{'all':'expeditions:block/dormant_heart'}})
    write(Path('models/item/dormant_heart.json'),{'parent':'expeditions:block/dormant_heart'})
    write(Path('blockstates/dormant_heart.json'),{'variants':{'':{'model':'expeditions:block/dormant_heart'}}})
    gui=Image.new('RGBA',(176,166),PALE[3]);d=ImageDraw.Draw(gui);d.rectangle((0,0,175,165),outline=METAL[0],width=3);d.rectangle((4,4,171,161),outline=COPPER[1]);d.line((5,17,170,17),fill=PALE[1]);d.rectangle((10,19,20,68),fill=METAL[0]);d.rectangle((11,20,19,67),outline=COPPER[1])
    for x,y in [(52,34),(112,34)]+[(7+c*18,83+r*18) for r in range(3) for c in range(9)]+[(7+c*18,141) for c in range(9)]:d.rectangle((x,y,x+17,y+17),fill=PALE[1]);d.rectangle((x+1,y+1,x+16,y+16),fill=PALE[2]);d.line((x+1,y+17,x+17,y+17),fill=PALE[5])
    d.line((80,42,103,42),fill=PALE[1],width=5);d.polygon([(99,36),(107,42),(99,48)],fill=PALE[1]);save('gui/resonance',gui)

if __name__=='__main__':gen()
