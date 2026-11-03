"""Whole block logs/leaves and recessed portal, assembled from the actual template."""
import copy
import json
from pathlib import Path
from PIL import Image, ImageDraw
from recall_tree_layout import generate


def generate_art(assets):
    assets=Path(assets);layout=generate()
    bark=Image.new('RGBA',(16,16),'#aaa99b');d=ImageDraw.Draw(bark)
    for a,b,color in [(0,3,'#c9c8b7'),(4,7,'#aead9f'),(8,11,'#c0bfad'),(12,15,'#8b8d80')]:d.rectangle((a,0,b,15),fill=color)
    for points,color in [([(3,0),(3,5),(2,7),(2,15)],'#686e62'),
                         ([(7,0),(7,3),(6,5),(6,11),(7,13),(7,15)],'#828679'),
                         ([(11,0),(11,6),(10,8),(10,15)],'#686e62'),
                         ([(1,0),(1,5),(0,7),(0,13)],'#dedbc7'),
                         ([(9,1),(9,6),(8,8),(8,14)],'#d5d2bc')]:d.line(points,fill=color)
    d.line([(13,8),(14,10),(14,14)],fill='#707669');d.line((4,9,4,12),fill='#d5d2bc')
    bark.save(assets/'textures/block/stillwood_bark.png')
    end=Image.new('RGBA',(16,16),'#a2a08e');e=ImageDraw.Draw(end)
    for inset,color in [(0,'#727869'),(1,'#c4bea5'),(3,'#8e917c'),(4,'#c4bea5'),(6,'#a3a18a')]:e.rectangle((inset,inset,15-inset,15-inset),outline=color)
    e.line([(9,0),(9,3),(8,5),(8,8)],fill='#646d60');end.save(assets/'textures/block/stillwood_end.png')
    leaves=Image.new('RGBA',(16,16),'#7c8071');l=ImageDraw.Draw(leaves)
    for points,color in [([(0,1),(4,0),(6,2),(4,5),(0,4)],'#b8beaa'),
                         ([(8,0),(12,1),(11,5),(7,4)],'#a7ab98'),
                         ([(2,6),(6,5),(9,7),(7,10),(3,10)],'#cbd0bb'),
                         ([(11,7),(15,5),(15,10),(12,11)],'#a7ab98'),
                         ([(0,12),(4,11),(7,14),(5,15),(0,15)],'#a7ab98'),
                         ([(9,12),(13,11),(15,14),(13,15),(9,15)],'#b8beaa')]:l.polygon(points,fill=color)
    for points in [[(0,6),(2,5),(2,7)],[(6,1),(7,0),(7,2)],[(9,9),(11,8),(10,10)],[(6,12),(8,11),(8,13)],[(14,2),(15,1),(15,3)]]:l.polygon(points,fill=(0,0,0,0))
    leaves.save(assets/'textures/block/stillwood_leaves.png')
    # A dark moving aperture with dim amber grain, recessed behind the doorway.
    # Native seamless 16px frames hide the back wall without painting scenery.
    depth=Image.new('RGBA',(16,64));curve=[0,0,1,1,2,2,2,1,1,0,0,-1,-1,-2,-1,0]
    for frame in range(4):
        for y in range(16):
            row=(y+frame*2)%16
            for x in range(16):
                u=(x+curve[row])%16;color=(25,29,24,220)
                if u in [2,3,9,10]:color=(53,55,40,230)
                if u in [3,10] and row in [2,3,10,11]:color=(152,108,45,240)
                elif u in [4,11]:color=(72,62,39,235)
                depth.putpixel((x,y+frame*16),color)
    depth.save(assets/'textures/block/stillwood_depth.png')
    (assets/'textures/block/stillwood_depth.png.mcmeta').write_text(json.dumps({'animation':{'frametime':10,'interpolate':True}})+'\n',encoding='utf-8')
    textures={n:'expeditions:block/stillwood_'+n for n in ['bark','end','leaves']}
    textures['veil']='expeditions:block/stillwood_depth';textures['particle']=textures['bark']
    models={}
    for kind in ['log_y','log_x','log_z','root_x','root_y','root_z','leaves']:
        faces={}
        for f in ['north','south','east','west','up','down']:
            along=(kind=='log_y' and f in ['up','down']) or (kind=='log_x' and f in ['east','west']) or (kind=='log_z' and f in ['north','south'])
            face={'texture':'#leaves' if kind=='leaves' else '#end' if along else '#bark','uv':[0,0,16,16],'cullface':f}
            if (kind in ['log_x','root_x'] and f in ['north','south']) or (kind in ['log_z','root_z'] and f in ['east','west']):face['rotation']=90
            faces[f]=face
        models[kind]={'parent':'minecraft:block/block','render_type':'minecraft:cutout','textures':textures,
                      'elements':[{'from':[0,0,0],'to':[16,16,16],'faces':faces}]}
    models['portal']={'parent':'minecraft:block/block','render_type':'minecraft:translucent','ambientocclusion':False,'textures':textures,
                      'elements':[{'from':[0,0,7.99],'to':[16,16,8.01],'shade':False,
                                   'faces':{f:{'texture':'#veil','uv':[0,0,16,16]} for f in ['north','south']}}]}
    textures['vine']='expeditions:block/stillwood_twigs'
    for kind in {c[3] for c in layout['cells'] if c[3].startswith('vine_')}:
        elements=[]
        for face in kind.removeprefix('vine_').split('_'):
            a,b,pair={'east':([15,0,0],[16,16,16],['east','west']),
                      'north':([0,0,0],[16,16,1],['north','south']),
                      'up':([0,15,0],[16,16,16],['up','down'])}[face]
            elements.append({'from':a,'to':b,'faces':{f:{'texture':'#vine','uv':[0,0,16,16]} for f in pair}})
        models[kind]={'parent':'minecraft:block/block','render_type':'minecraft:cutout','textures':textures,'elements':elements}
    models['entrance']={'parent':'minecraft:block/block','textures':textures,'elements':[]}
    for kind,model in models.items():(assets/'models/block'/f'stillwood_{kind}.json').write_text(json.dumps(model,indent=2)+'\n',encoding='utf-8')
    variants={'part='+str(i):{'model':'expeditions:block/recall_tree_'+str(i)} for i in range(8)}
    legacy=json.loads((assets/'route/recall_tree_alpha21.json').read_text(encoding='utf-8'))['cells']
    for index,(_,_,_,kind) in enumerate(legacy+layout['cells'],8):variants['part='+str(index)]={'model':'expeditions:block/stillwood_'+kind}
    (assets/'blockstates/recall_tree_part.json').write_text(json.dumps({'variants':variants},indent=2)+'\n',encoding='utf-8')
    (assets/'blockstates/expedition_exit.json').write_text(json.dumps({'variants':{
        'grown=false':{'model':'expeditions:block/expedition_exit'},
        'grown=true,full_tree=false':{'model':'expeditions:block/recall_tree_root'},
        'grown=true,full_tree=true':{'model':'expeditions:block/stillwood_entrance'}}},indent=2)+'\n',encoding='utf-8')
    miniature=[]
    visible=[c for c in layout['cells'] if c[1]>=0]
    min_x=min(c[0] for c in visible);max_x=max(c[0] for c in visible)+1
    min_z=min(c[2] for c in visible);max_z=max(c[2] for c in visible)+1
    scale=max(max_x-min_x,max_z-min_z,max(c[1] for c in visible)+1)
    shifts=[8-(min_x+max_x)*8/scale,0,8-(min_z+max_z)*8/scale]
    for x,y,z,kind in visible:
        if kind=='portal':continue
        for original in models[kind]['elements']:
            element=copy.deepcopy(original)
            for key in ['from','to']:element[key]=[(v+o*16)/scale+shifts[axis] for axis,(v,o) in enumerate(zip(element[key],[x,y,z]))]
            for face in element['faces'].values():face.pop('cullface',None)
            miniature.append(element)
    (assets/'models/item/expedition_exit.json').write_text(json.dumps({'parent':'minecraft:block/block','textures':textures,
        'render_type':'minecraft:cutout','elements':miniature},indent=2)+'\n',encoding='utf-8')

    # Native, mod-independent runtime template; the exact raw source remains in docs.
    from structure_nbt import structure,write
    source=assets.parents[4]/'docs/art-direction/player-tree/metadata.json'
    metadata=json.loads(source.read_text(encoding='utf-8'))
    origin=metadata['origin'];anchor=metadata['anchor']
    baked=[]
    for index,(x,y,z,kind) in enumerate(layout['cells'],8+len(legacy)):
        pos=[x+anchor[0]-origin[0],y+anchor[1]-origin[1],z+anchor[2]-origin[2]]
        baked.append((pos,'expeditions:recall_tree_part',{'part':str(index)}))
    baked.append(([anchor[i]-origin[i] for i in range(3)],'expeditions:expedition_exit',
                  {'grown':'true','full_tree':'true','tree_layout':'1'}))
    write(assets.parents[1]/'data/expeditions/structure/stillwood_hollow.nbt',structure(metadata['size'],baked))
