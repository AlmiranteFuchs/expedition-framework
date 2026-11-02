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
    im=Image.new('RGBA',(16,16),palette[2]);d=ImageDraw.Draw(im)
    d.rectangle((0,0,15,15),outline=palette[0]);d.line((1,1,14,1),fill=palette[-1]);d.line((1,1,1,14),fill=palette[-2])
    for x in (4,10):d.line((x,3,x,13),fill=palette[1]);d.line((x+1,4,x+1,9),fill=palette[3])
    for x,y in ((2,2),(13,2),(2,13),(13,13)):d.point((x,y),fill=palette[0])
    return im

def item(name,draw):
    im=Image.new('RGBA',(16,16));draw(ImageDraw.Draw(im));save('item/'+name,im)
    write(Path('models/item/'+name+'.json'),{'parent':'minecraft:item/generated','textures':{'layer0':'expeditions:item/'+name}})

def gen():
    for name,pal in [('machine_bone',PALE),('machine_copper',COPPER),('machine_iron',METAL)]:save('block/'+name,panel(pal))
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
    textures={k:'expeditions:block/machine_'+v for k,v in [('bone','bone'),('copper','copper'),('iron','iron'),('glass','glass'),('resin','resin')]};textures['chart']='expeditions:block/terminal_chart';textures['particle']=textures['bone']
    def box(a,b,tex='bone',rotation=None):
        e={'from':a,'to':b,'faces':{face:{'texture':'#'+tex} for face in ['down','up','north','south','west','east']}}
        if tex=='glass':
            for f in e['faces'].values(): f['uv']=[0,0,16,16]
        if rotation:e['rotation']=rotation
        return e
    base=[box([1,1,2],[15,3,14]),box([0,0,1],[3,3,4],'iron'),box([13,0,1],[16,3,4],'iron'),box([0,0,12],[3,3,15],'iron'),box([13,0,12],[16,3,15],'iron')]
    dynamo=base+[box([2,3,5],[4,10,8]),box([12,3,5],[14,10,8]),box([2,8,5],[14,10,7],'iron'),box([5,4,10],[11,9,14],'copper'),box([6,5,9.8],[10,8,10],'resin'),box([7,8,4],[9,10,14],'iron')]
    wheel=[]
    for a,b in [([2,6,8],[3.5,13,10]),([12.5,6,8],[14,13,10]),([5,14,8],[11,15.5,10]),([5,3.5,8],[11,5,10])]:wheel.append(box(a,b,'copper'))
    for x,y in [(3.5,13),(11.5,13),(3.5,5),(11.5,5)]:wheel.append(box([x-1.5,y-.75,8],[x+1.5,y+.75,10],'copper',{'origin':[x,y,9],'axis':'z','angle':-45 if (x<8)==(y<9) else 45}))
    wheel += [box([7,4,8.5],[9,15,9.5],'bone'),box([3,8.5,8.5],[13,10.5,9.5],'bone'),box([6.5,8,7.5],[9.5,11,10.5],'iron')]
    # Bring the complete flywheel ahead of its frame, with correctly joined octagonal corners.
    for element in wheel:
        element['from'][2]-=5.5;element['to'][2]-=5.5
        if 'rotation' in element: element['rotation']['origin'][2]-=5.5
    for x in [5,7,9]: dynamo.append(box([x,3.8,10],[x+1,9.2,14],'copper'))
    condenser=base+[box([8,3,6],[10,5,8],'copper'),box([6,5,4],[12,6,10],'copper'),box([6,13,4],[12,14,10],'copper'),box([7,6,5],[11,13,9],'glass'),box([8,6,6],[10,10,8],'resin'),box([5,4,4],[6,14,5]),box([12,4,9],[13,14,10]),box([8,14,6],[10,16,8],'iron'),box([3,13,6],[8,15,8],'copper'),box([2,8,6],[4,14,8],'copper'),box([2,4,6],[4,8,8]),box([3,7,5],[5,8,7],'copper'),box([3,3,4],[6,5,7],'glass'),box([4,3,5],[5,4,6],'resin')]
    terminal=[box([1,0,2],[5,2,6],'iron'),box([11,0,10],[15,2,14],'iron'),box([2,2,3],[4,9,5]),box([12,2,11],[14,11,13]),box([3,3,4],[13,5,12]),box([0,8,3],[16,9,14],'bone',{'origin':[8,8,8],'axis':'x','angle':-45}),box([1,9.01,4],[15,9.05,13],'chart',{'origin':[8,8,8],'axis':'x','angle':-45}),box([14,7,8],[16,9,10],'copper')]
    for face in terminal[6]['faces']:
        terminal[6]['faces'][face]['texture']='#chart' if face=='up' else '#bone'
    for name,elements in [('stillness_dynamo',dynamo+wheel),('essence_condenser',condenser),('expedition_terminal',terminal)]:
        model={'parent':'minecraft:block/block','textures':textures,'elements':elements,'render_type':'minecraft:cutout','ambientocclusion':False}
        write(Path('models/block/'+name+'.json'),model);write(Path('models/item/'+name+'.json'),{'parent':'expeditions:block/'+name})
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
