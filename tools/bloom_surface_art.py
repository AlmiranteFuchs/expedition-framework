"""Eight authored 8-block heartwood scars, at native 16px/block. No noise fill."""
from pathlib import Path
from PIL import Image, ImageDraw


def generate(root):
    root = Path(root)
    atlas = Image.new('RGBA', (512, 256))
    embers = Image.new('RGBA', (512, 256))
    for variant in range(8):
        patch = Image.new('RGBA', (128, 128)); d = ImageDraw.Draw(patch)
        heat = Image.new('RGBA', (128, 128)); e = ImageDraw.Draw(heat)
        for strand, offset in enumerate([-35, -24, -12, 0, 13, 25, 36]):
            top = 16 + (strand * 11 + variant * 7) % 27
            bottom = 91 + (strand * 7 + variant * 11) % 22
            width = [2, 4, 6, 8, 5, 4, 2][strand]
            if variant == 0 and strand in (0, 2, 3, 6):
                continue  # Quiet colonies need genuinely empty tissue, not a smaller stamp.
            centre = []
            left = []; right = []
            for step, y in enumerate(range(top, bottom + 1, 4)):
                x = 64 + offset + (64-y)*3//5 + ((step*3+strand+variant)%5-2)
                taper = min(width, 1+(y-top)//4, 1+(bottom-y)//4)
                left.append((x-taper,y)); right.append((x+taper,y))
                centre.append((x,y))
            d.polygon(left + right[::-1], fill=(181, 171, 153, 215))
            # Broken internal grain: highlights do not outline the entire silhouette.
            start = 2 + (variant + strand) % 4
            end = max(start+2, len(centre)-3-(strand%3))
            d.line(centre[start:end], fill=(220, 209, 188, 225), width=max(1,width//2))
            if width >= 5:
                seam = [(x+2,y) for x,y in centre[start+2:end-1]]
                if len(seam)>1: d.line(seam, fill=(116, 106, 91, 155))
            # Short gaps interrupt ribbons without scattering independent grass-like motifs.
            if strand in (1,4,5):
                x,y=centre[len(centre)//2]
                d.polygon([(x-width-2,y),(x+width+2,y+2),(x+width,y+5),
                           (x-width-2,y+3)], fill=(0,0,0,0))
        # Amber belongs to a few mature patches, never to each covered block.
        if variant in (2,7):
            scar=[(66,60),(65,63),(63,65),(63,68)] if variant==2 else [(57,75),(58,72),(60,71),(61,68)]
            d.line(scar, fill='#795537', width=3)
            d.line(scar, fill='#bd7b32')
            for x,y in scar[1:3]:
                d.point((x,y), fill='#e0a34b'); e.point((x,y), fill='#e0a34b')
        # Transparent margins prevent visible patch rectangles when adjacent cells overlap.
        for x in range(128):
            for y in range(128):
                edge=min(x,y,127-x,127-y)
                if edge < 12:
                    r,g,b,a=patch.getpixel((x,y))
                    patch.putpixel((x,y),(r,g,b,a*max(0,edge-3)//9))
        xy=(variant%4*128,variant//4*128)
        atlas.alpha_composite(patch,xy); embers.alpha_composite(heat,xy)
    atlas.save(root/'block/assimilation_connections.png')
    embers.save(root/'block/assimilation_connections_embers.png')
    print('Generated eight world-scale heartwood scars with rare resin wounds.')
