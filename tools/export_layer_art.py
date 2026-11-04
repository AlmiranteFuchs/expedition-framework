"""Export the installed Bloom art faithfully, without overwriting an earlier art handoff."""
from pathlib import Path
from PIL import Image, ImageDraw
from zipfile import ZipFile, ZIP_DEFLATED, ZIP_STORED
from io import BytesIO
import csv
import shutil
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
TEXTURES = ROOT / 'src/main/resources/assets/expeditions/textures'
base = ROOT / 'art-exports/bloom-layer-art-alpha9'
out = base
n = 2
while out.exists() or out.with_suffix('.zip').exists():
    out = base.with_name(base.name + f'-{n}')
    n += 1
for name in ('EDIT_ATLASES','EDIT_TILES/material','EDIT_TILES/emission',
             'REFERENCE_ONLY/boundary_masks_16px','SOURCE'):
    (out/name).mkdir(parents=True,exist_ok=True)

material = Image.open(TEXTURES/'block/assimilation_connections.png').convert('RGBA')
emission = Image.open(TEXTURES/'block/assimilation_connections_embers.png').convert('RGBA')
assert material.size == emission.size == (128,128)
for name in ('assimilation_connections.png','assimilation_connections_embers.png'):
    shutil.copy2(TEXTURES/'block'/name,out/'EDIT_ATLASES'/name)
shutil.copy2(TEXTURES/'block/assimilation_layer.png',out/'REFERENCE_ONLY/item_and_fallback_16px.png')

with (out/'tile_mapping.csv').open('w',newline='',encoding='utf-8') as f:
    writer=csv.writer(f)
    writer.writerow(['atlas_index','atlas_x','atlas_y','corner_code','NW','NE','SE','SW',
                     'interior_variant','resin','material_file','emission_file'])
    for index in range(64):
        code=(index%32)//2; variant=index%2; resin=index>=32
        stem=f'{"resin" if resin else "grain"}_c{code:02d}_v{variant}_16px.png'
        x=index%8*16; y=index//8*16
        material.crop((x,y,x+16,y+16)).save(out/'EDIT_TILES/material'/stem)
        emission.crop((x,y,x+16,y+16)).save(out/'EDIT_TILES/emission'/stem)
        writer.writerow([index,x,y,code,*[(code>>i)&1 for i in range(4)],variant,
                         int(resin),f'EDIT_TILES/material/{stem}',f'EDIT_TILES/emission/{stem}'])


def level(d):
    return 0 if d<=0 else .35 if d==1 else .8 if d==2 else 1


def coverage(mask,variant,x,y):
    distance=[y,7-x,7-y,x]; along=[x,y,x,y]; alpha=1
    for side in range(4):
        if not mask&(1<<side):
            fray=1 if 2<=along[side]<=5 and (along[side]+variant+side*2)%5==0 else 0
            alpha=min(alpha,level(distance[side]-fray))
    for corner,(a,b) in enumerate(((0,3),(0,1),(2,1),(2,3))):
        ca=bool(mask&(1<<a)); cb=bool(mask&(1<<b))
        if ca and cb and not mask&(16<<corner): alpha=min(alpha,level(distance[a]+distance[b]))
        if not ca and not cb: alpha=min(alpha,level(distance[a]+distance[b]-2))
    return alpha


def canonical(mask):
    result=mask&15
    for c,(a,b) in enumerate(((0,3),(0,1),(2,1),(2,3))):
        if mask&(1<<a) and mask&(1<<b) and mask&(16<<c): result|=16<<c
    return result


masks=[m for m in range(256) if canonical(m)==m]
assert len(masks)==47
mask_atlas=Image.new('RGBA',(256,192))
with (out/'boundary_mapping.csv').open('w',newline='',encoding='utf-8') as f:
    writer=csv.writer(f); writer.writerow(['filename','neighbor_mask','fray_variant','atlas_x','atlas_y'])
    for i,mask in enumerate(masks):
        for variant in range(4):
            im=Image.new('RGBA',(16,16))
            for y in range(16):
                for x in range(16):
                    im.putpixel((x,y),(255,255,255,round(coverage(mask,variant,x//2,y//2)*255)))
            name=f'boundary_{mask:02x}_f{variant}_16px.png'
            im.save(out/'REFERENCE_ONLY/boundary_masks_16px'/name)
            index=i*4+variant; xy=(index%16*16,index//16*16)
            mask_atlas.alpha_composite(im,xy)
            writer.writerow([name,mask,variant,*xy])
mask_atlas.save(out/'REFERENCE_ONLY/boundary_mask_atlas.png')

# OpenRaster is a genuine layered document for Krita/GIMP and other compatible editors.
stack=ET.Element('image',{'w':'128','h':'128','name':'Bloom material and emission atlases','version':'0.0.1'})
group=ET.SubElement(stack,'stack')
ET.SubElement(group,'layer',{'name':'EMISSION — amber only (additive in game)','src':'data/emission.png',
    'opacity':'1.0','visibility':'visible','composite-op':'svg:src-over','x':'0','y':'0'})
ET.SubElement(group,'layer',{'name':'MATERIAL — native 16px tiles','src':'data/material.png',
    'opacity':'1.0','visibility':'visible','composite-op':'svg:src-over','x':'0','y':'0'})
with ZipFile(out/'EDIT_ATLASES/bloom-layer-atlases.ora','w',ZIP_DEFLATED) as z:
    z.writestr('mimetype','image/openraster',compress_type=ZIP_STORED)
    z.writestr('stack.xml',ET.tostring(stack,encoding='utf-8',xml_declaration=True))
    for name,im in (('data/material.png',material),('data/emission.png',emission)):
        stream=BytesIO(); im.save(stream,format='PNG'); z.writestr(name,stream.getvalue())
    preview=material.copy(); preview.alpha_composite(emission)
    stream=BytesIO(); preview.save(stream,format='PNG'); z.writestr('mergedimage.png',stream.getvalue())

colors=[(171,160,141,18),(171,160,141,55),(185,174,154,95),(198,187,166,140),
        (211,200,178,180),(221,211,192,205),(168,110,42,200),(213,153,63,220)]
with (out/'palette_rgba.csv').open('w',newline='',encoding='utf-8') as f:
    writer=csv.writer(f); writer.writerow(['R','G','B','A']); writer.writerows(colors)
for source in ('tools/bloom_surface_art.py','src/main/java/com/expeditions/core/BloomBoundary.java',
               'src/main/java/com/expeditions/core/BloomSurfacePattern.java'):
    shutil.copy2(ROOT/source,out/'SOURCE'/Path(source).name)

(out/'START_HERE.txt').write_text('''BLOOM / ASSIMILATION LAYER — CURRENT APPROVED ART EXPORT

Easiest: open EDIT_ATLASES/bloom-layer-atlases.ora in Krita/GIMP,
or edit the two 128x128 transparent PNG atlases directly.
Alternative: edit individual 16x16 PNGs in EDIT_TILES.
You only need to edit one form; send back the atlas OR the individual files.

The material and emission are separate layers. Keep transparency (RGBA), native
dimensions and filenames. Use a hard pixel brush / nearest-neighbor zoom.
One 16x16 tile covers one block face. No multi-block picture scaling.

Atlas: 8 columns x 8 rows, 16 pixels per cell, no padding.
Indices 0..31 = normal material; 32..63 = matching resin versions.
tile_mapping.csv lists all positions, corner codes and filenames.
Corner bits are NW, NE, SE, SW; 0/1 are compatibility labels, not colors.
Matching edge labels must retain matching RGBA border pixels, including alpha.
Safe start: paint tile interiors and keep the outermost pixel border unchanged.
If you want new borders, edit matching edge families together; I can check/reconcile them.
Resin versions should keep their normal partner's border pixels.
Emission PNGs are mostly empty by design. Only amber marks glow in game.
The .ora shows normal compositing; the game uses additive emission and a pulse.

REFERENCE_ONLY has the item/fallback icon and 47 boundary states x 4 fray
variants exported as native 16x16 alpha masks. WHITE RGB + varying alpha =
coverage. These masks are currently calculated in code, not loaded as assets;
you can sketch new outlines over them, but I must import those changes.
The same coverage masks BOTH the material and the underlying host bleaching.
The original host block texture is sampled at runtime, so it isn't in this pack.

SOURCE contains the current art generator and boundary/selection rules for reference.
Palette suggestions: pale wood / warm ash / sparse amber; no green vines.

Send back whichever files you changed. I'll assemble and validate them before
replacing the game assets. This export made no changes to the mod.
''',encoding='utf-8')

archive=out.with_suffix('.zip')
with ZipFile(archive,'w',ZIP_DEFLATED) as z:
    for file in sorted(out.rglob('*')):
        if file.is_file(): z.write(file,Path(out.name)/file.relative_to(out))
with ZipFile(archive) as z:
    assert z.testzip() is None
    assert len([p for p in z.namelist() if '/EDIT_TILES/' in p and p.endswith('.png')])==128
print(f'ZIP: {archive}')
print(f'FOLDER: {out}')
print(f'64 material + 64 emission tiles; 188 boundary references; {archive.stat().st_size:,} bytes')
