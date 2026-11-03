"""Derive protected placement from the player-authored native NBT source."""
import json
import hashlib
from pathlib import Path
from structure_nbt import read

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/expeditions'
SOURCE=ROOT/'docs/art-direction/player-tree'


def generate():
    metadata=json.loads((SOURCE/'metadata.json').read_text(encoding='utf-8'))
    native=read(SOURCE/metadata['source']);origin=metadata['origin'];anchor=metadata['anchor']
    cells=[]
    for block in native['blocks']:
        state=native['palette'][block['state']];name=state['Name'];props=state.get('Properties',{})
        if name=='minecraft:air':continue
        if name=='twilightforest:time_log':kind='log_'+props.get('axis','y')
        elif name in ['twilightforest:time_wood','twilightforest:root']:kind='root_'+props.get('axis','y')
        elif name=='twilightforest:time_leaves':kind='leaves'
        elif name=='minecraft:orange_stained_glass':kind='portal'
        elif name=='minecraft:vine':
            faces=sorted(k for k,v in props.items() if v=='true');kind='vine_'+'_'.join(faces)
        else:raise ValueError('Unmapped authored material: '+name)
        pos=[block['pos'][i]+origin[i]-anchor[i] for i in range(3)]
        cells.append(pos+[kind])
    assert len(cells)==metadata['sourceNonAir']
    assert not any(c[:3]==[0,0,0] for c in cells),'The authored entrance must remain clear'
    portals=[c[:3] for c in cells if c[3]=='portal'];assert sorted(portals)==[[0,0,-1],[0,1,-1]]
    data={'source':'player-authored native structure','sourceSha256':hashlib.sha256((SOURCE/metadata['source']).read_bytes()).hexdigest(),
          'cells':cells,'clearance':[[0,1,0]],'floors':[[0,-1,0],[0,-1,-1]]}
    (ASSETS/'route/recall_tree.json').write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
    return data


if __name__=='__main__':print('Authored tree:',len(generate()['cells'])+1,'planned marker cells')
