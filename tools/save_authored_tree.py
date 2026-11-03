"""Preserve the exact captured build as native NBT; never modifies the live world."""
import json
from pathlib import Path
from structure_nbt import structure,write,read

ROOT=Path(__file__).resolve().parents[1]
folder=ROOT/'docs/art-direction/player-tree'
capture=json.loads((folder/'source-blocks.json').read_text(encoding='utf-8'))
blocks=capture['blocks']
origin=[min(b[k] for b in blocks) for k in ['x','y','z']]
size=[max(b[k] for b in blocks)-origin[i]+1 for i,k in enumerate(['x','y','z'])]
cells=[([b[k]-origin[i] for i,k in enumerate(['x','y','z'])],b['id'],b.get('properties',{})) for b in blocks]
path=folder/'stillwood_player_source.nbt';write(path,structure(size,cells))
saved=read(path)
actual={(tuple(b['pos']),saved['palette'][b['state']]['Name'],tuple(sorted(saved['palette'][b['state']].get('Properties',{}).items())))
        for b in saved['blocks'] if saved['palette'][b['state']]['Name']!='minecraft:air'}
expected={(tuple(p),name,tuple(sorted(props.items()))) for p,name,props in cells}
assert actual==expected,'Native structure must preserve every original block state'
markers=capture['portalMarkers'];assert len(markers)==2
# The player faces the entrance from the south; its lip is one block outside the glass.
anchor=[markers[0]['x'],min(m['y'] for m in markers),markers[0]['z']+1]
metadata={'origin':origin,'size':size,'anchor':anchor,'sourceNonAir':len(blocks),'portalMarkers':markers,
          'source':'stillwood_player_source.nbt','author':'F','palette':'pale bark / amber, explicitly chosen by player'}
(folder/'metadata.json').write_text(json.dumps(metadata,indent=2)+'\n',encoding='utf-8')
print('Preserved',len(actual),'exact block states as',size,'native NBT')
