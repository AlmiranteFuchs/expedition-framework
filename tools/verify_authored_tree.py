"""Verify the shipped protected structure preserves every authored voxel."""
import hashlib
import json
from pathlib import Path
from structure_nbt import read

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/expeditions'
SOURCE=ROOT/'docs/art-direction/player-tree'
meta=json.loads((SOURCE/'metadata.json').read_text(encoding='utf-8'))
native=read(SOURCE/meta['source'])
layout=json.loads((ASSETS/'route/recall_tree.json').read_text(encoding='utf-8'))
assert layout['sourceSha256']==hashlib.sha256((SOURCE/meta['source']).read_bytes()).hexdigest()
expected=set()
for block in native['blocks']:
    state=native['palette'][block['state']]
    if state['Name']=='minecraft:air':continue
    expected.add(tuple(block['pos'][i]+meta['origin'][i]-meta['anchor'][i] for i in range(3)))
assert expected=={tuple(c[:3]) for c in layout['cells']}
assert len(expected)==meta['sourceNonAir']==930
legacy=json.loads((ASSETS/'route/recall_tree_alpha21.json').read_text(encoding='utf-8'))['cells']
assert len(legacy)==113
first=8+len(legacy)
variants=json.loads((ASSETS/'blockstates/recall_tree_part.json').read_text(encoding='utf-8'))['variants']
assert len(variants)==first+len(expected)
for value in variants.values():assert (ASSETS/'models'/(value['model'].split(':')[1]+'.json')).is_file()
baked=read(ROOT/'src/main/resources/data/expeditions/structure/stillwood_hollow.nbt')
actual={}
for block in baked['blocks']:
    state=baked['palette'][block['state']]
    assert not state['Name'].startswith('twilightforest:'),'Runtime cannot require the source palette mod'
    if state['Name']=='expeditions:recall_tree_part':actual[int(state['Properties']['part'])]=block['pos']
for index,cell in enumerate(layout['cells'],first):
    assert actual[index]==[cell[i]+meta['anchor'][i]-meta['origin'][i] for i in range(3)]
assert baked['size']==native['size']==[17,31,17]
print('Verified 930 exact authored voxels, native NBT, legacy indices and mod-independent runtime palette.')
