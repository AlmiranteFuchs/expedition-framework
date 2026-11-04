"""Enlarge the real 16px assets beside their previous revision for art review."""
import argparse
import io
from pathlib import Path
import subprocess
from PIL import Image, ImageDraw

parser = argparse.ArgumentParser()
parser.add_argument('--before', default='724c27a')
parser.add_argument('--output', default='build/heartwood-art-review.png')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
assets = 'src/main/resources/assets/expeditions/textures/'
entries = [('Heart', 'block/assimilation_heart'), ('Membrane', 'block/assimilation_layer'),
           ('Terminal', 'block/expedition_terminal'), ('Checkpoint', 'block/expedition_checkpoint'),
           ('Extraction', 'block/expedition_exit'), ('Device', 'block/expedition_device'),
           ('Portal', 'block/expedition_portal'), ('Cargo', 'item/expedition_artifact'),
           ('Sigil', 'item/attunement_sigil'), ('Essence', 'item/assimilation_essence')]
board = Image.new('RGBA', (480, 54 + len(entries)*86), '#22271f')
draw = ImageDraw.Draw(board)
for x, label in [(12, 'ITEM'), (178, 'PREVIOUS'), (280, 'NEW SIDE / ICON'), (398, 'NEW TOP')]:
    draw.text((x, 15), label, fill='#e0d6b6')
for row, (label, key) in enumerate(entries):
    y = 45 + row*86
    draw.text((12, y+26), label, fill='#d2c6a4')
    old = subprocess.check_output(['git', '-c', 'safe.directory='+root.as_posix(), 'show', args.before+':'+assets+key+'.png'], cwd=root)
    sources = [(178, Image.open(io.BytesIO(old))), (286, Image.open(root/assets/(key+'.png')))]
    top = root/assets/(key+'_top.png')
    if top.exists(): sources.append((398, Image.open(top)))
    for x, source in sources:
        draw.rectangle((x-2, y-2, x+65, y+65), fill='#737768', outline='#b4ac8c')
        board.alpha_composite(source.convert('RGBA').resize((64, 64), Image.Resampling.NEAREST), (x, y))
output = root/args.output
output.parent.mkdir(parents=True, exist_ok=True)
board.save(output)
print(output)
