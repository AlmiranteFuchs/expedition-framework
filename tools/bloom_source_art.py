"""Shutter Bloom: four broad folded bark plates and localized amber lining.

Native block models are also the authoritative source for selection/collision.
The five key poses use vanilla-supported rotations; no sculpted voxel mound.
"""
import copy
import json
from pathlib import Path
from PIL import Image, ImageDraw

ROOT=Path(__file__).resolve().parents[1]/'src/main/resources/assets/expeditions'
PALE=['#34332d','#756b61','#a69c86','#c9bea4','#e9dec2','#fff0cf']


def write(path,data):
    p=ROOT/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')


def texture(name,im):im.save(ROOT/'textures/block'/f'{name}.png')


def textures():
    bark=Image.new('RGBA',(16,16),PALE[2]);d=ImageDraw.Draw(bark)
    # Long interrupted fibers, concentrated at folds rather than noisy rock facets.
    d.rectangle((0,0,3,15),fill=PALE[3]);d.line((1,0,1,15),fill=PALE[4])
    d.rectangle((12,0,15,15),fill=PALE[1]);d.line((11,1,11,13),fill=PALE[3])
    d.line([(6,0),(6,5),(5,7),(5,15)],fill=PALE[1]);d.line([(7,0),(7,4),(6,7),(6,14)],fill=PALE[4])
    d.line([(9,3),(9,8),(10,10),(10,15)],fill=PALE[3]);d.line((2,2,3,2),fill=PALE[5]);texture('bloom_bark',bark);texture('bloom_bark_cross',bark.transpose(Image.Transpose.ROTATE_90))
    for alive in [False,True]:
        inside=Image.new('RGBA',(16,16),'#35352f');g=ImageDraw.Draw(inside)
        for x in [1,5,10,14]:
            g.line([(x,0),(x,5),(max(0,x-1),8),(max(0,x-1),15)],fill='#645e50')
            g.line((x+1 if x<15 else x,2,x+1 if x<15 else x,12),fill='#242721')
        if alive:
            g.line([(8,5),(7,7),(8,9),(8,12)],fill='#8f551f');g.line((8,7,8,8),fill='#be802f');g.point((8,7),fill='#d8a24c')
        texture('bloom_inner' if alive else 'bloom_inner_cold',inside);texture('bloom_inner_cross' if alive else 'bloom_inner_cold_cross',inside.transpose(Image.Transpose.ROTATE_90))
    end=Image.new('RGBA',(16,16),PALE[1]);e=ImageDraw.Draw(end)
    for y in [2,6,10,14]:e.line([(0,y),(5,y),(7,y+1),(15,y+1)],fill=PALE[3])
    texture('bloom_end',end)
    bed=Image.new('RGBA',(16,16),'#282a24');b=ImageDraw.Draw(bed)
    b.line([(3,12),(6,9),(8,9),(10,5),(12,4)],fill='#7b4b23');b.line((7,8,8,8),fill='#b78035');texture('bloom_resin',bed)
    glow=Image.new('RGBA',(16,16));g=ImageDraw.Draw(glow);g.line([(5,10),(7,8),(9,8),(10,5)],fill='#bd8d43');g.point((7,8),fill='#e1b663');texture('bloom_seam',glow)


def box(a,b,t,rotation=None,inner=None):
    faces={face:{'texture':'#'+t,'uv':[0,0,16,16]} for face in ['up','down','north','south','east','west']}
    for face in ['up','down']:faces[face]['texture']='#bloom_end'
    if inner:faces[inner]['texture']='#bloom_inner'
    out={'from':a,'to':b,'faces':faces}
    if rotation:out['rotation']=rotation
    return out


def model(stage,cold=False):
    tex={n:'expeditions:block/'+n for n in ['bloom_bark','bloom_bark_cross','bloom_end','bloom_inner','bloom_inner_cross','bloom_resin']}
    tex['particle']=tex['bloom_bark']
    if cold:
        tex['bloom_inner']='expeditions:block/bloom_inner_cold';tex['bloom_inner_cross']='expeditions:block/bloom_inner_cold_cross';tex['bloom_resin']='expeditions:block/bloom_inner_cold'
    parts=[box([4,0,5],[11,1,12],'bloom_bark'),box([5,1,5],[11,2,11],'bloom_bark'),box([6,2,6],[10,2.5,10],'bloom_resin')]
    parts[-1]['faces']['up']['texture']='#bloom_resin'
    # Shallow folded fans, not an upright box. Each broad plate has one
    # clipped shoulder; two large pieces make a stepped organic outline.
    shells=[([4,3.5,2],[11,4.3,8.5],[8,3.5,8.5],'x',1,0,False,2,5),
            ([2,4,5],[8.5,4.8,11],[8.5,4,8],'z',-1,0,True,0,5),
            ([7.5,2.8,4],[14,3.6,10],[7.5,2.8,8],'z',1,1,True,0,11),
            ([5,3,7.5],[12,3.8,14],[8,3,7.5],'x',-1,1,False,2,11)]
    angles=[-22.5,0,22.5,45]
    for a,b,origin,axis,sign,delay,cross,split_axis,cut in shells:
        pose=min(3,max(0,stage-delay));rotation={'origin':origin,'axis':axis,'angle':angles[pose]*sign}
        pieces=[(list(a),list(b)),(list(a),list(b))]
        pieces[0][1][split_axis]=cut;pieces[1][0][split_axis]=cut
        tip=0 if a[split_axis]<4 else 1
        width_axis=2 if cross else 0
        pieces[tip][0][width_axis]+=1;pieces[tip][1][width_axis]-=1
        for start,stop in pieces:
            element=box(start,stop,'bloom_bark',rotation)
            for face in ['north','south','east','west']:element['faces'][face]['texture']='#bloom_end'
            element['faces']['up']['texture']='#bloom_bark_cross' if cross else '#bloom_bark'
            element['faces']['down']['texture']='#bloom_inner_cross' if cross else '#bloom_inner'
            uv=[16*(start[0]-a[0])/(b[0]-a[0]),16*(start[2]-a[2])/(b[2]-a[2]),16*(stop[0]-a[0])/(b[0]-a[0]),16*(stop[2]-a[2])/(b[2]-a[2])]
            element['faces']['up']['uv']=uv;element['faces']['down']['uv']=uv
            parts.append(element)
    return {'parent':'minecraft:block/block','render_type':'minecraft:cutout','ambientocclusion':True,'textures':tex,'elements':parts}


def generate():
    textures()
    for stage in range(5):write(Path(f'models/block/bloom_shutter_{stage}.json'),model(stage))
    write(Path('models/block/bloom_shutter_dormant.json'),model(0,True))
    for block,cold in [('assimilation_heart',False),('dormant_heart',True)]:
        variants={}
        for direction,angle in [('north',0),('east',90),('south',180),('west',270)]:
            for stage in range(5):variants[f'facing={direction},open={stage}']={'model':'expeditions:block/bloom_shutter_'+('dormant' if cold else str(stage)),'y':angle}
        write(Path(f'blockstates/{block}.json'),{'variants':variants})
        write(Path(f'models/block/{block}.json'),{'parent':'expeditions:block/bloom_shutter_'+('dormant' if cold else '0')})
        write(Path(f'models/item/{block}.json'),{'parent':'expeditions:block/bloom_shutter_'+('dormant' if cold else '4')})
    # The carried machine contains the same specimen, not a tiny painted cube.
    path=ROOT/'models/item/living_heart_engine.json'
    if path.exists():
        machine=json.loads(path.read_text(encoding='utf-8'))
        machine['elements']=[e for e in machine['elements'] if not all(f['texture']=='#heart' for f in e['faces'].values()) and not any(f['texture'].startswith('#bloom_') for f in e['faces'].values())]
        miniature=model(2);machine['textures'].update(miniature['textures'])
        for e in copy.deepcopy(miniature['elements']):
            for point in ['from','to']:
                e[point]=[6.08+e[point][0]*.24,8.8+e[point][1]*.24,6.08+e[point][2]*.24]
            if 'rotation' in e:
                v=e['rotation']['origin'];e['rotation']['origin']=[6.08+v[0]*.24,8.8+v[1]*.24,6.08+v[2]*.24]
            machine['elements'].append(e)
        write(Path('models/item/living_heart_engine.json'),machine)


if __name__=='__main__':generate()
