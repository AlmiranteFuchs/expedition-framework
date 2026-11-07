"""The Charter's native 16px inventory family.

Authored clusters, hard alpha and one lighting direction. Run after the older
world-material generators; only inventory assets are written here.
"""
import json
import math
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/expeditions'
B = ['#292b26', '#625d51', '#948876', '#c5b79d', '#e4d8bb', '#fff0cf']
M = ['#1f2321', '#383c36', '#606156', '#999787']
C = ['#49362b', '#80553b', '#b58154', '#e2b37b']
A = ['#5d361e', '#99521e', '#cb7f28', '#efb752', '#ffe3a2']
RED = ['#452b29', '#7f4438', '#b77c60']


def write(path, data):
    path=ROOT/path;path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')


def save(name, im, model=True):
    im.save(ROOT/'textures/item'/f'{name}.png')
    if model:write(Path(f'models/item/{name}.json'),{'parent':'minecraft:item/generated','textures':{'layer0':'expeditions:item/'+name}})


def sprite(name, painter):
    im=Image.new('RGBA',(16,16));painter(ImageDraw.Draw(im));save(name,im);return im


def assay(d):
    # A hinged case: a dark lid, two glass samples and a short lancet.
    d.polygon([(3,1),(12,1),(14,3),(13,6),(2,6),(1,3)],fill=M[0])
    d.line([(3,2),(12,2),(13,3)],fill=M[2]);d.rectangle((3,3,11,4),fill=M[1]);d.point((8,1),fill=C[2])
    d.polygon([(2,6),(13,6),(14,8),(13,14),(3,15),(1,12)],fill=M[0])
    d.polygon([(3,7),(12,7),(12,12),(4,13),(3,12)],fill=M[1])
    for x,col in [(4,B[3]),(8,RED[1])]:
        d.rectangle((x,8,x+1,11),fill=col);d.point((x,8),fill=B[5]);d.point((x,7),fill=C[2]);d.point((x+1,12),fill=M[2])
    d.line((11,8,11,11),fill=B[3]);d.point((11,8),fill=B[5])
    d.line((3,14,10,14),fill=M[2]);d.rectangle((7,13,9,14),fill=C[1]);d.point((7,13),fill=C[3])


def charm(d):
    d.line([(1,0),(2,3),(5,5),(8,6),(12,4),(14,1)],fill=M[1])
    d.point((2,2),fill=B[2]);d.point((12,3),fill=C[2]);d.rectangle((7,5,8,7),fill=C[1])
    d.polygon([(7,7),(10,8),(11,13),(10,15),(8,12),(7,10),(5,14),(3,14),(4,10)],fill=B[0])
    d.polygon([(7,7),(8,9),(6,12),(5,13),(4,13),(5,10)],fill=B[3])
    d.line([(7,8),(6,10),(5,11)],fill=B[5]);d.polygon([(9,8),(10,11),(10,13),(9,12),(8,10)],fill=B[2])
    d.point((8,9),fill=A[2]);d.point((8,10),fill=A[0])


def essence(d):
    d.polygon([(11,0),(12,2),(10,5),(13,8),(12,12),(8,15),(4,14),(2,11),(3,6),(7,2)],fill=B[0])
    d.polygon([(10,1),(8,4),(6,6),(5,10),(7,13),(5,13),(3,10),(4,6),(7,3)],fill=B[3])
    d.line([(9,2),(6,5),(5,7)],fill=B[5]);d.line([(4,8),(4,10),(6,12)],fill=B[1])
    d.polygon([(9,4),(9,7),(11,8),(11,11),(8,14),(6,12),(6,9)],fill=A[0])
    d.polygon([(9,6),(8,8),(9,10),(7,12),(8,13),(10,10),(10,8)],fill=A[2])
    d.line([(8,8),(8,9),(7,10)],fill=A[3]);d.point((8,7),fill=A[4])


def artifact(d):
    d.polygon([(4,2),(11,2),(14,5),(14,11),(10,14),(3,13),(1,10),(1,6)],fill=B[0])
    d.polygon([(4,3),(10,3),(13,5),(9,8),(2,6)],fill=B[4])
    d.polygon([(2,7),(8,9),(8,13),(3,12),(2,10)],fill=B[2])
    d.polygon([(9,9),(13,6),(13,10),(9,13)],fill=B[1])
    d.line([(4,4),(9,5),(11,4)],fill=B[5]);d.line([(3,9),(6,10),(6,12)],fill=B[3])
    d.line([(7,3),(6,6),(6,8),(5,12)],fill=C[1]);d.line([(2,8),(8,10),(13,8)],fill=C[0])
    d.rectangle((5,6,8,9),fill=RED[0]);d.rectangle((6,6,8,8),fill=RED[1]);d.point((6,6),fill=RED[2])


def sigil(d):
    d.polygon([(7,0),(9,1),(14,13),(13,15),(1,15),(0,13)],fill=B[0])
    d.polygon([(7,1),(8,2),(5,8),(3,9)],fill=B[4]);d.line((7,2,5,6),fill=B[5])
    d.polygon([(9,4),(11,7),(11,10),(9,8)],fill=B[2])
    d.polygon([(2,11),(5,10),(5,12),(10,12),(12,11),(13,13),(11,14),(2,14)],fill=B[3])
    d.line((3,12,4,12),fill=B[5]);d.line((7,13,10,13),fill=B[1])
    d.line((3,8,5,9),fill=C[1]);d.point((4,8),fill=C[3]);d.line((6,12,6,14),fill=C[1])
    d.rectangle((10,8,11,9),fill=A[1]);d.point((10,8),fill=A[3])


def flakes(d):
    for points in [[(2,3),(7,2),(12,0),(13,1),(10,4),(5,6),(1,5)],[(1,8),(3,7),(7,11),(8,14),(5,14),(2,11)],[(10,8),(12,7),(15,10),(14,13),(12,12)]]:
        d.polygon(points,fill=B[0])
    d.polygon([(2,3),(6,3),(11,1),(9,3),(5,5),(2,4)],fill=B[3]);d.line([(3,3),(6,3),(9,2)],fill=B[5])
    d.polygon([(2,8),(3,9),(6,12),(6,13),(4,11)],fill=B[3]);d.line((2,8,4,10),fill=B[5])
    d.polygon([(11,8),(12,8),(14,10),(14,11),(12,10)],fill=B[3]);d.point((11,8),fill=B[5])


def knot(d):
    d.polygon([(6,0),(10,1),(14,5),(14,10),(10,15),(5,15),(1,11),(0,6),(3,2)],fill=B[0])
    d.polygon([(6,1),(9,2),(12,5),(12,11),(9,14),(5,13),(2,10),(1,6),(4,3)],fill=B[2])
    d.line([(6,2),(4,3),(2,6),(3,10),(6,12),(9,12),(11,9),(10,5),(8,4),(5,5),(4,8),(6,10),(8,9),(8,7),(6,7)],fill=B[4])
    d.line([(6,2),(4,3),(3,5)],fill=B[5]);d.line([(7,5),(6,6),(5,8)],fill=B[1])
    d.line([(10,2),(9,5),(11,8),(10,11),(12,12)],fill=B[0]);d.point((10,9),fill=B[3])


def core(d):
    d.polygon([(8,0),(11,4),(11,10),(8,14),(5,13),(3,9),(5,3)],fill=M[0])
    d.polygon([(8,2),(9,4),(8,7),(6,10),(5,8),(6,4)],fill=M[2]);d.line((8,3,6,6),fill=M[3])
    d.polygon([(3,4),(3,9),(6,12),(6,15),(3,12),(1,8),(2,5)],fill=B[0])
    d.line([(3,5),(2,8),(4,11),(6,12)],fill=B[4]);d.point((2,6),fill=B[5])
    d.polygon([(12,4),(14,5),(14,10),(11,14),(9,15),(9,12),(12,9)],fill=B[0])
    d.line([(13,5),(13,9),(10,12)],fill=B[3]);d.point((12,5),fill=B[5])
    d.line((5,13,10,13),fill=C[1]);d.line((5,12,10,12),fill=C[2]);d.point((5,12),fill=C[3])
    d.point((9,7),fill=A[3]);d.point((8,8),fill=A[1])


def cell(d):
    d.polygon([(3,3),(12,3),(15,6),(15,13),(12,15),(2,14),(1,12),(1,5)],fill=M[0])
    d.rectangle((3,4,11,12),fill=B[3]);d.line((3,5,3,11),fill=B[5]);d.line((4,6,4,10),fill=B[1])
    d.polygon([(12,5),(14,6),(14,12),(12,14)],fill=C[0]);d.line((13,6,13,10),fill=C[2])
    d.rectangle((2,3,12,4),fill=C[1]);d.line((3,3,10,3),fill=C[3]);d.rectangle((2,12,11,13),fill=C[1]);d.line((3,12,10,12),fill=C[2])
    for x in [3,10]:
        d.rectangle((x,0,x+1,2),fill=C[1]);d.line((x,0,x+1,0),fill=C[3]);d.point((x,1),fill=C[2])
    d.rectangle((6,5,9,11),fill=M[0]);d.line((6,5,9,5),fill=C[0])
    for y in [6,8,10]:d.line((7,y,8,y),fill=A[2]);d.point((7,y),fill=A[4])


def lens(d):
    d.polygon([(8,0),(12,0),(15,3),(15,8),(12,11),(7,11),(4,8),(4,4)],fill=M[0])
    d.polygon([(8,1),(12,1),(14,3),(14,7),(11,10),(7,10),(5,8),(5,4)],fill=C[1])
    d.line([(7,2),(9,1),(12,2)],fill=C[3]);d.line((5,5,5,7),fill=C[2])
    d.polygon([(9,3),(11,3),(12,4),(12,7),(10,9),(7,8),(7,5)],fill=A[0])
    d.line([(8,4),(7,5),(7,6)],fill=B[5]);d.line((10,8,11,7),fill=A[2]);d.point((12,5),fill=A[1])
    d.line((5,10,3,12),fill=C[1],width=2);d.point((5,10),fill=C[3])
    d.polygon([(3,11),(5,12),(4,14),(2,15),(2,13),(0,15),(0,13)],fill=B[0])
    d.line((3,12,1,14),fill=B[4]);d.point((3,12),fill=B[5]);d.point((3,14),fill=B[2])


def tissue(d, live):
    d.polygon([(2,6),(6,4),(8,1),(10,4),(14,5),(12,8),(15,11),(11,12),(9,15),(7,12),(2,13),(3,9),(0,8)],fill=B[0])
    d.polygon([(3,6),(6,5),(8,3),(9,5),(12,6),(10,8),(12,10),(9,11),(8,13),(6,10),(3,11),(4,8),(2,7)],fill=B[2])
    d.line([(4,6),(7,5),(8,4)],fill=B[4]);d.line([(3,8),(5,8),(7,10),(8,12)],fill=B[3])
    d.line([(8,5),(7,7),(9,9),(9,10)],fill=B[0]);d.line([(10,6),(9,7),(10,8)],fill=B[1])
    if live:d.point((7,7),fill=A[2]);d.point((9,9),fill=A[1])


def field_specimens():
    # Separate bark plates and open cord loops must survive the 16px inventory view.
    outline, shadow, bark, light, edge = B[0], B[1], B[2], B[3], B[4]

    def husk(d):
        d.polygon([(1,2),(3,2),(5,6),(5,10),(7,12),(5,14),(2,11),(2,7)], fill=outline)
        d.polygon([(2,3),(3,5),(4,7),(4,10),(5,12),(3,10)], fill=bark)
        d.line([(2,3),(2,6),(3,8),(3,10)], fill=light)
        d.point((2,3), fill=edge)
        d.polygon([(7,0),(10,1),(10,4),(8,7),(8,11),(6,13),(5,10),(6,7),(7,5)], fill=outline)
        d.polygon([(8,1),(9,2),(8,5),(7,7),(7,10),(6,11),(6,8),(7,5)], fill=bark)
        d.line([(8,1),(8,4),(7,6)], fill=light)
        d.point((8,1), fill=edge)
        d.polygon([(13,3),(15,2),(14,7),(12,9),(12,12),(9,14),(7,13),(9,10),(10,6)], fill=outline)
        d.polygon([(13,4),(13,7),(11,9),(11,11),(9,12),(10,9),(11,7)], fill=bark)
        d.line([(13,4),(12,6),(11,7)], fill=light)
        d.polygon([(3,11),(6,12),(9,11),(12,12),(11,15),(7,15),(4,14)], fill=outline)
        d.line([(4,12),(6,13),(9,12),(11,12)], fill=bark)
        d.line([(5,14),(8,14),(10,13)], fill=shadow)
        d.point((9,10), fill=A[0])

    def crust(d):
        d.polygon([(1,9),(3,6),(6,6),(8,3),(12,2),(14,4),(11,7),(13,9),(10,12),(7,11),(5,14),(2,12)], fill=outline)
        d.polygon([(2,9),(4,7),(7,7),(9,4),(12,3),(11,5),(8,8),(10,10),(8,10),(5,12),(3,11)], fill=bark)
        d.line([(3,8),(6,8),(9,5),(11,4)], fill=light)
        d.point((11,3), fill=edge)
        d.line([(3,10),(5,10),(7,9)], fill=shadow)
        d.line([(9,8),(11,9),(10,10)], fill=light)
        d.line([(4,12),(5,11)], fill=light)
        d.point((7,7), fill=outline)
        d.point((10,5), fill=outline)

    def tether(d):
        # Two distinct loops, with the lower strand passing under the tied center.
        d.line([(6,7),(3,6),(2,3),(4,1),(7,2),(8,5),(7,8)], fill=outline, width=3)
        d.line([(6,7),(3,5),(3,3),(4,2),(6,3),(7,5),(6,7)], fill=bark)
        d.line([(3,4),(3,3),(4,2),(6,3)], fill=light)
        d.line([(8,7),(10,4),(13,4),(14,6),(13,9),(10,10),(8,8)], fill=outline, width=3)
        d.line([(9,7),(11,5),(12,5),(13,6),(12,8),(10,9),(9,8)], fill=bark)
        d.line([(11,5),(12,5),(13,6)], fill=light)
        d.line([(7,9),(5,11),(4,14),(2,14)], fill=outline, width=3)
        d.line([(7,9),(5,11),(4,13),(2,14)], fill=bark)
        d.line([(6,10),(5,11),(4,13)], fill=light)
        d.line([(9,9),(10,12),(13,14)], fill=outline, width=3)
        d.line([(9,10),(10,12),(12,13)], fill=bark)
        d.point((10,12), fill=light)
        d.polygon([(5,7),(7,5),(10,7),(10,9),(8,11),(5,9)], fill=outline)
        d.line([(6,7),(8,6),(9,7)], fill=light)
        d.line([(6,8),(8,10),(9,9)], fill=bark)
        d.point((7,8), fill=A[2])
        d.point((8,8), fill=A[1])

    sprite('dormant_heart', husk)
    sprite('dormant_layer', crust)
    sprite('return_knot', tether)

def compass():
    im=Image.new('RGBA',(16,16));d=ImageDraw.Draw(im)
    d.polygon([(5,2),(10,2),(13,5),(13,11),(10,14),(4,14),(1,11),(1,6)],fill=B[0])
    d.polygon([(5,3),(9,3),(12,6),(12,10),(9,13),(5,13),(2,10),(2,6)],fill=B[3])
    d.line([(5,3),(3,5),(2,7)],fill=B[5]);d.line([(12,7),(12,10),(10,12)],fill=B[1])
    d.polygon([(5,5),(9,4),(11,6),(11,10),(9,12),(5,12),(3,10),(3,7)],fill=M[0])
    d.line([(5,5),(4,7),(4,9)],fill=M[1]);d.point((7,5),fill=B[2]);d.point((10,8),fill=B[1]);d.point((7,11),fill=B[1]);d.point((4,8),fill=B[2])
    d.rectangle((10,0,12,2),fill=C[0]);d.line((10,0,12,0),fill=C[3]);d.point((11,1),fill=M[0]);d.point((12,2),fill=C[2])
    frames=[]
    for frame in range(32):
        out=im.copy();pixels=out.load();angle=(frame-16)*math.tau/32;dx,dy=math.sin(angle),-math.cos(angle)
        for y in range(5,12):
            for x in range(4,11):
                rx,ry=x-7,y-8;along=rx*dx+ry*dy;across=rx*dy-ry*dx
                if .5<along<3.9 and abs(across)<1.08-along*.16:pixels[x,y]=rgba(A[3] if across<0 else A[2])
                elif -2.8<along<-.4 and abs(across)<.6:pixels[x,y]=rgba(B[2])
        paint=ImageDraw.Draw(out);paint.point((7,8),fill=C[3]);paint.point((7,9),fill=C[0])
        save(f'expedition_compass_{frame:02d}',out,False);frames.append(out)
    save('expedition_compass',frames[16],False)
    # Preserve the tested angle model ordering and first-person grip.
    return frames


def rgba(color):
    return tuple(bytes.fromhex(color[1:]))+(255,)


def gen():
    # Preserve the preferred angled leather journal from the original native painter.
    from gen_textures import gen_journal
    gen_journal()
    for name,draw in [('field_assay_kit',assay),('disruption_charm',charm),
                      ('assimilation_essence',essence),('expedition_artifact',artifact),('attunement_sigil',sigil),
                      ('dormant_flake',flakes),('dormant_knot',knot),('severance_core',core),('resonance_cell',cell),('survey_lens',lens)]:sprite(name,draw)
    sprite('assimilation_layer',lambda d:tissue(d,True))
    field_specimens()
    frames=compass()
    # GUI framing changes only; world geometry, selection and hand transforms stay authoritative.
    for name in ['expedition_terminal','expedition_checkpoint','expedition_exit','assimilation_heart',
                 'stillness_dynamo','essence_condenser','living_heart_engine','severance_generator','field_ward']:
        path=ROOT/f'models/item/{name}.json';data=json.loads(path.read_text(encoding='utf-8'))
        data['gui_light']='side';data.setdefault('display',{})['gui']={'rotation':[30,225,0],'translation':[0,0,0],'scale':[.625,.625,.625]}
        write(Path(f'models/item/{name}.json'),data)
    # Native, small, hard-edged elevation ember; its renderer controls the pulse.
    ember=Image.new('RGBA',(16,16));d=ImageDraw.Draw(ember)
    d.polygon([(7,2),(10,7),(8,12),(6,13),(4,8)],fill=A[0]);d.polygon([(7,4),(8,7),(7,10),(6,10),(5,8)],fill=A[2]);d.point((7,6),fill=A[4]);save('compass_ember',ember,False)
    return frames


if __name__=='__main__':gen()
