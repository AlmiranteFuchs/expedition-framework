"""Native 16px corner-compatible heartwood material. In-game art trial."""
from pathlib import Path
from PIL import Image, ImageDraw

PALETTE = [(171,160,141,18),(171,160,141,55),(185,174,154,95),
           (198,187,166,140),(211,200,178,180),(221,211,192,205)]


def material_tiles():
    source = Image.new('RGBA',(15,15),PALETTE[0]); d=ImageDraw.Draw(source)
    for x,y,length,width in [(1,1,4,2),(2,10,4,2),(5,3,4,2),(6,9,3,2),
                              (9,0,5,2),(10,10,4,2),(13,6,4,2),(0,7,2,1)]:
        d.rectangle((x,y,x+width,y+length),fill=PALETTE[2])
        d.line([(x+1,y+1),(x+1,y+length-1)],fill=PALETTE[4])
    for x,y in [(3,2),(7,5),(11,12),(14,8),(6,11)]: d.point((x,y),fill=PALETTE[3])

    def sample(x,y,phase):
        dx,dy=[(0,0),(7,5),(4,10),(11,2)][phase]
        return source.getpixel(((x+dx)%15,(y+dy)%15))

    tiles={}
    for code in range(16):
        for variant in range(2):
            im=Image.new('RGBA',(16,16)); bits=[(code>>i)&1 for i in range(4)]
            for y in range(16):
                for x in range(16):
                    u=x/15; v=y/15
                    weights=[(1-u)*(1-v),u*(1-v),u*v,(1-u)*v]
                    values=[sample(x,y,phase) for phase in bits]
                    rgba=[sum(weights[i]*values[i][c] for i in range(4)) for c in range(4)]
                    amount=.45*16*u*(1-u)*v*(1-v)
                    centre=sample(x,y,2+variant)
                    rgba=[a*(1-amount)+b*amount for a,b in zip(rgba,centre)]
                    color=min(PALETTE,key=lambda c:sum((c[n]-rgba[n])**2 for n in range(4)))
                    im.putpixel((x,y),color)
            tiles[code,variant]=im
    return tiles


def generate(root):
    root=Path(root)
    atlas=Image.new('RGBA',(128,128)); embers=Image.new('RGBA',(128,128))
    tiles=material_tiles()
    for resin in (False,True):
        for (code,variant),base in tiles.items():
            im=base.copy(); heat=Image.new('RGBA',(16,16))
            if resin:
                d=ImageDraw.Draw(im); e=ImageDraw.Draw(heat)
                d.line([(10,8),(10,9),(11,10)],fill=(168,110,42,200))
                d.point((10,9),fill=(213,153,63,220)); e.point((10,9),fill='#d5993f')
            n=code*2+variant+(32 if resin else 0); xy=(n%8*16,n//8*16)
            atlas.alpha_composite(im,xy); embers.alpha_composite(heat,xy)
    atlas.save(root/'block/assimilation_connections.png')
    embers.save(root/'block/assimilation_connections_embers.png')
    print('Generated native 16px corner-compatible material and rare resin variants.')
