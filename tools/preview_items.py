"""Nearest-neighbor inventory proofs and editable native sprite export."""
from pathlib import Path
import zipfile
from PIL import Image, ImageDraw

ROOT=Path(__file__).resolve().parents[1]
ART=ROOT/'src/main/resources/assets/expeditions/textures/item'
NAMES=['expedition_field_journal','expedition_compass','field_assay_kit','disruption_charm',
       'assimilation_essence','expedition_artifact','attunement_sigil','dormant_flake',
       'dormant_knot','severance_core','resonance_cell','survey_lens','assimilation_layer','dormant_layer']


def generate():
    target=ROOT/'docs/art-direction/items';target.mkdir(parents=True,exist_ok=True)
    board=Image.new('RGB',(896,360),'#292b26');d=ImageDraw.Draw(board)
    atlas=Image.new('RGBA',(16*7,16*2))
    for i,name in enumerate(NAMES):
        im=Image.open(ART/(name+'.png')).convert('RGBA');x=(i%7)*128;y=(i//7)*180
        atlas.paste(im,((i%7)*16,(i//7)*16))
        enlarged=im.resize((96,96),Image.Resampling.NEAREST);board.paste(enlarged,(x+16,y+10),enlarged)
        d.text((x+4,y+116),name.replace('expedition_','').replace('_','\n'),fill='#e4d9bf')
    board.save(target/'native-alpha16.png');atlas.save(target/'native-sheet-alpha16.png')
    frames=[Image.open(ART/f'expedition_compass_{i:02d}.png').resize((192,192),Image.Resampling.NEAREST) for i in range(32)]
    frames[0].save(target/'compass-alpha16.gif',save_all=True,append_images=frames[1:],duration=100,loop=0,disposal=2)
    archive=ROOT/'build/art-exports/items-alpha16.zip';archive.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
        for path in sorted(ART.glob('*.png')):z.write(path,'sprites/'+path.name)
        z.write(target/'native-sheet-alpha16.png','native-sheet-alpha16.png')
        z.write(ROOT/'tools/item_art.py','item_art.py')
        z.write(target/'README.md','README.md')
    print('Wrote native sheet, preview, 32-frame animation and',archive)


if __name__=='__main__':generate()
