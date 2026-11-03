"""Block-by-block Stillwood tree. One layout is shared by art and server placement."""
import json
from pathlib import Path

ASSETS=Path(__file__).resolve().parents[1]/'src/main/resources/assets/expeditions'


def generate():
    cells={}
    def put(x,y,z,kind='log_y'):cells[(x,y,z)]=kind
    # A solid three-by-three trunk with a chamber carved one block into it.
    for y in range(4):
        for x in range(-1,2):
            for z in range(3):
                if y==3 and abs(x)==1 and z in [0,2]:continue
                if x==0 and y<2 and z in [0,1]:continue
                put(x,y,z)
    for y in [0,1]:put(0,y,1,'portal')
    # Roots follow the grain away from the trunk in full block steps.
    for x,z,kind in [(-2,1,'root_x'),(2,1,'root_x'),(-1,-1,'root_z'),(1,-1,'root_z'),
                     (-1,3,'root_z'),(1,3,'root_z')]:put(x,0,z,kind)
    # Uneven fork and lateral limbs connect the crown to the main trunk.
    for x,y,z,kind in [(0,4,1,'log_y'),(-1,4,1,'log_x'),(-2,4,1,'log_x'),
                        (1,4,1,'log_x'),(1,4,2,'log_z'),(2,4,2,'log_x'),
                        (-2,5,1,'log_y'),(2,5,2,'log_y'),(0,5,1,'log_y')]:put(x,y,z,kind)
    # An offset canopy, broad and broken at the edge, with visible branch tips.
    for y,radius,cx,cz in [(4,2,-1,1),(5,3,0,1),(6,2,0,1),(7,1,0,1)]:
        for x in range(cx-radius,cx+radius+1):
            for z in range(cz-radius,cz+radius+1):
                if abs(x-cx)+abs(z-cz)>radius+1:continue
                if (x,y,z) in cells:continue
                if y==4 and abs(x)<=1 and z in [0,1,2]:continue
                if (x,z) in [(cx-radius,cz+1),(cx+radius,cz-1)]:continue
                put(x,y,z,'leaves')
    # The front doorway is air. A recessed veil sits a full block inside it.
    cells.pop((0,0,0),None);cells.pop((0,1,0),None)
    data={'cells':[[x,y,z,kind] for (x,y,z),kind in sorted(cells.items(),key=lambda p:(p[0][1],p[0][2],p[0][0]))],
          'clearance':[[0,1,0]],'floors':[[0,-1,0],[0,-1,1]]+
          [[x,-1,z] for (x,y,z),kind in cells.items() if y==0 and kind.startswith(('log','root'))]}
    path=ASSETS/'route/recall_tree.json';path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
    return data


if __name__=='__main__':
    data=generate();print('Stillwood tree:',len(data['cells'])+1,'tracked block cells')
