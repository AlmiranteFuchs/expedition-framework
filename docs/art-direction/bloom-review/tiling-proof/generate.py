"""Review-only 16px material tiles with two-state corner constraints. No runtime edits."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parent
PALETTE = [(171,160,141,18),(171,160,141,55),(185,174,154,95),
           (198,187,166,140),(211,200,178,180),(221,211,192,205)]
source = Image.new('RGBA',(15,15),PALETTE[0]); d=ImageDraw.Draw(source)
# Short, blunt wood fibres within a material. No isolated branching emblem.
for x,y,length,width in [(1,1,4,2),(2,10,4,2),(5,3,4,2),(6,9,3,2),
                          (9,0,5,2),(10,10,4,2),(13,6,4,2),(0,7,2,1)]:
    d.rectangle((x,y,x+width,y+length),fill=PALETTE[2])
    d.line([(x+1,y+1),(x+1,y+length-1)],fill=PALETTE[4])
for x,y in [(3,2),(7,5),(11,12),(14,8),(6,11)]: d.point((x,y),fill=PALETTE[3])


def sample(x,y,phase):
    dx,dy=[(0,0),(7,5),(4,10),(11,2)][phase]
    return source.getpixel(((x+dx)%15,(y+dy)%15))


def tile(code,variant):
    im=Image.new('RGBA',(16,16))
    bits=[(code>>i)&1 for i in range(4)]  # NW, NE, SE, SW
    for y in range(16):
        for x in range(16):
            u=x/15; v=y/15
            weights=[(1-u)*(1-v),u*(1-v),u*v,(1-u)*v]
            values=[sample(x,y,phase) for phase in bits]
            rgba=[sum(weights[i]*values[i][c] for i in range(4)) for c in range(4)]
            # Variant detail vanishes exactly at all edges and corners.
            amount=.45*16*u*(1-u)*v*(1-v)
            centre=sample(x,y,2+variant)
            rgba=[a*(1-amount)+b*amount for a,b in zip(rgba,centre)]
            color=min(PALETTE,key=lambda c:sum((c[n]-rgba[n])**2 for n in range(4)))
            im.putpixel((x,y),color)
    return im


tiles={(code,var):tile(code,var) for code in range(16) for var in range(2)}


def edge_code(code,side):
    pairs={'n':(0,1),'e':(1,2),'s':(3,2),'w':(0,3)}
    a,b=pairs[side]
    return ((code>>a)&1,(code>>b)&1)


checks=0
for (ca,va),a in tiles.items():
    for (cb,vb),b in tiles.items():
        if edge_code(ca,'e')==edge_code(cb,'w'):
            assert [a.getpixel((15,y)) for y in range(16)]==[b.getpixel((0,y)) for y in range(16)]
            checks+=1
        if edge_code(ca,'s')==edge_code(cb,'n'):
            assert [a.getpixel((x,15)) for x in range(16)]==[b.getpixel((x,0)) for x in range(16)]
            checks+=1


def bit(x,y):
    h=(x*0x632be59b)^(y*0x9e3779b9)
    h=((h^(h>>13))*0x85ebca6b)&0xffffffff
    return (h>>17)&1


def repeat(mixed):
    im=Image.new('RGBA',(128,128),'#696761')
    for y in range(8):
        for x in range(8):
            code=(bit(x,y)|bit(x+1,y)<<1|bit(x+1,y+1)<<2|bit(x,y+1)<<3) if mixed else 0
            var=(x*7+y*3+x*y)%2 if mixed else 0
            piece=tiles[code,var].copy()
            if mixed and (x,y) in ((2,5),(6,1)):
                d=ImageDraw.Draw(piece)
                d.line([(10,8),(10,9),(11,10)],fill=(168,110,42,200))
                d.point((10,9),fill=(213,153,63,220))
            im.alpha_composite(piece,(x*16,y*16))
    return im.convert('RGB')


for name,key in [('grain-0-16',(0,0)),('grain-1-16',(5,0)),('grain-2-16',(10,1)),('grain-3-16',(15,1))]:
    tiles[key].save(OUT/f'{name}.png')
resin=tiles[0,0].copy(); d=ImageDraw.Draw(resin)
d.line([(10,8),(10,9),(11,10)],fill=(168,110,42,200)); d.point((10,9),fill=(213,153,63,220))
resin.save(OUT/'resin-16.png')
atlas=Image.new('RGBA',(128,64))
for (code,var),im in tiles.items():
    n=code*2+var; atlas.alpha_composite(im,(n%8*16,n//8*16))
atlas.save(OUT/'corner-compatible-tiles.png')
repeat(False).save(OUT/'same-tile-8x8.png'); repeat(True).save(OUT/'mixed-tiles-8x8.png')
board=Image.new('RGB',(1152,752),'#242321'); d=ImageDraw.Draw(board)
font=ImageFont.truetype('C:/Windows/Fonts/consola.ttf',18)
small=ImageFont.truetype('C:/Windows/Fonts/consola.ttf',14)
d.text((24,14),'NATIVE 16 x 16 MATERIAL — REPEAT TEST / REVIEW ONLY',font=font,fill='#e8e0d1')
for i,key in enumerate([(0,0),(5,0),(10,1),(15,1)]):
    under=Image.new('RGBA',(16,16),'#696761'); under.alpha_composite(tiles[key])
    board.paste(under.convert('RGB').resize((64,64),Image.Resampling.NEAREST),(24+i*104,50))
    d.text((24+i*104,118),f'GRAIN {i}',font=small,fill='#c6c1b3')
under=Image.new('RGBA',(16,16),'#696761'); under.alpha_composite(resin)
board.paste(under.convert('RGB').resize((64,64),Image.Resampling.NEAREST),(440,50))
d.text((440,118),'RESIN',font=small,fill='#c6c1b3')
d.text((24,157),'ONE TILE REPEATED 8 x 8',font=font,fill='#e8e0d1')
d.text((600,157),'CORNER-MATCHED VARIANTS / 8 x 8',font=font,fill='#e8e0d1')
board.paste(repeat(False).resize((512,512),Image.Resampling.NEAREST),(24,190))
board.paste(repeat(True).resize((512,512),Image.Resampling.NEAREST),(600,190))
d.text((24,717),'1 tile = 1 block face. Matching edge/corner profiles. Flat. Optional rare amber.',font=small,fill='#c6c1b3')
board.save(OUT/'repeat-proof.png')
print(f'32 native 16x16 drafts; {checks} compatible edge pairings pass in RGBA.')
