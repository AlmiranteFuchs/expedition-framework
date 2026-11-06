"""Colony growth sprites: flat cross-planar tufts and drips that sprout on
covered membrane faces. Pure sprite work — no sculpted geometry, matching
the "grass, not a model" rule for infection decoration.

Tufts are drawn rooted at the bottom edge (they stand on a covered floor
face); drips are drawn rooted at the top edge (they hang under a covered
ceiling face). Both render through `block/cross` with random per-position
variant pick from the blockstate's `apply` list.

Palette matches bloom_source_art.py: pale bark ramp + sparse amber embers.
"""
import json
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/expeditions'
PALE = ['#34332d', '#756b61', '#a69c86', '#c9bea4', '#e9dec2', '#fff0cf']
EMBER = ['#8f551f', '#be802f', '#d8a24c']


def write(path, data):
    p = ROOT / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')


def texture(name, im):
    im.save(ROOT / 'textures/block' / f'{name}.png')


def strand(d, x, top, bottom, width, shade, lean=0):
    """One vertical fiber spanning rows top..bottom, optionally leaning
    `lean` px per 4px of run downward."""
    for y in range(top, bottom + 1):
        off = lean * (y - top) // 4
        for dx in range(width):
            xx = x + dx + off
            if 0 <= xx < 16:
                d.point((xx, y), fill=shade)


def tuft_a():
    im = Image.new('RGBA', (16, 16)); d = ImageDraw.Draw(im)
    strand(d, 4, 9, 15, 1, PALE[2])
    strand(d, 8, 5, 15, 2, PALE[3], lean=-1)
    strand(d, 12, 11, 15, 1, PALE[1], lean=1)
    d.point((7, 4), fill=EMBER[2]); d.point((8, 5), fill=EMBER[1])
    d.rectangle((3, 15, 13, 15), fill=PALE[0])
    texture('bloom_tuft_a', im)


def tuft_b():
    im = Image.new('RGBA', (16, 16)); d = ImageDraw.Draw(im)
    strand(d, 7, 3, 15, 1, PALE[3], lean=1)
    d.point((10, 2), fill=PALE[4]); d.point((10, 3), fill=EMBER[1])
    strand(d, 3, 12, 15, 1, PALE[1])
    strand(d, 12, 10, 15, 1, PALE[2], lean=-1)
    d.point((4, 13), fill=PALE[3]); d.point((11, 9), fill=PALE[4])
    d.rectangle((2, 15, 13, 15), fill=PALE[0])
    texture('bloom_tuft_b', im)


def tuft_c():
    im = Image.new('RGBA', (16, 16)); d = ImageDraw.Draw(im)
    d.ellipse((2, 11, 6, 15), fill=PALE[2])
    d.ellipse((6, 9, 10, 15), fill=PALE[3])
    d.ellipse((10, 12, 13, 15), fill=PALE[1])
    d.point((8, 10), fill=EMBER[1]); d.point((4, 12), fill=PALE[4])
    d.point((7, 11), fill=PALE[5])
    d.rectangle((1, 15, 14, 15), fill=PALE[0])
    texture('bloom_tuft_c', im)


def drip_a():
    im = Image.new('RGBA', (16, 16)); d = ImageDraw.Draw(im)
    d.rectangle((2, 0, 13, 0), fill=PALE[0])
    strand(d, 4, 0, 8, 1, PALE[2], lean=1)
    strand(d, 8, 0, 12, 2, PALE[3], lean=-1)
    strand(d, 12, 0, 5, 1, PALE[1], lean=-1)
    d.point((8, 13), fill=PALE[4]); d.point((9, 14), fill=EMBER[2])
    texture('bloom_drip_a', im)


def drip_b():
    im = Image.new('RGBA', (16, 16)); d = ImageDraw.Draw(im)
    d.rectangle((5, 0, 11, 0), fill=PALE[0])
    strand(d, 8, 0, 11, 1, PALE[3])
    d.point((8, 5), fill=EMBER[1]); d.point((8, 6), fill=EMBER[0])
    d.point((8, 12), fill=PALE[4]); d.point((8, 13), fill=EMBER[2])
    strand(d, 11, 0, 4, 1, PALE[1], lean=-1)
    texture('bloom_drip_b', im)


def model(name):
    write(f'models/block/{name}.json', {
        'parent': 'minecraft:block/cross',
        'textures': {'cross': f'expeditions:block/{name}'}})


def main():
    tuft_a(); tuft_b(); tuft_c(); drip_a(); drip_b()
    for n in ['bloom_tuft_a', 'bloom_tuft_b', 'bloom_tuft_c',
              'bloom_drip_a', 'bloom_drip_b']:
        model(n)
    print('bloom growths: 5 sprites + 5 cross models written')


if __name__ == '__main__':
    main()
