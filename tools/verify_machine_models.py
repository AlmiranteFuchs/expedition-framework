"""Detect actual coplanar overlapping faces, including tilted machine beams."""
import argparse
import itertools
import json
import math
from pathlib import Path
import subprocess
from machine_surface_mesh import FACES

ROOT=Path(__file__).resolve().parents[1]
NAMES=['stillness_dynamo','living_heart_engine','severance_generator','essence_condenser','expedition_terminal']


def rotate(point,r,normal=False):
    if not r:return list(point)
    origin=[0,0,0] if normal else r['origin'];v=[point[i]-origin[i] for i in range(3)]
    c,s=math.cos(math.radians(r['angle'])),math.sin(math.radians(r['angle']))
    x,y,z=v
    if r['axis']=='x':v=[x,y*c-z*s,y*s+z*c]
    elif r['axis']=='y':v=[x*c+z*s,y,-x*s+z*c]
    else:v=[x*c-y*s,x*s+y*c,z]
    return [v[i]+origin[i] for i in range(3)]


def area(poly):return abs(sum(a[0]*b[1]-b[0]*a[1] for a,b in zip(poly,poly[1:]+poly[:1])))/2 if poly else 0
def cross(a,b,p):return (b[0]-a[0])*(p[1]-a[1])-(b[1]-a[1])*(p[0]-a[0])


def ordered(points):
    cx=sum(p[0] for p in points)/len(points);cy=sum(p[1] for p in points)/len(points)
    return sorted(points,key=lambda p:math.atan2(p[1]-cy,p[0]-cx))


def intersection(subject,clip):
    out=subject
    for a,b in zip(clip,clip[1:]+clip[:1]):
        source=out;out=[]
        if not source:break
        for p,q in zip(source[-1:]+source[:-1],source):
            P,Q=cross(a,b,p),cross(a,b,q)
            if (P>=-1e-9)!=(Q>=-1e-9):
                t=P/(P-Q);out.append([p[i]+t*(q[i]-p[i]) for i in range(2)])
            if Q>=-1e-9:out.append(q)
    return out


def overlaps(model):
    faces=[]
    for index,e in enumerate(model['elements']):
        for direction,face in e['faces'].items():
            if face['texture']=='#empty':continue
            axis,sign,u,v,_,_=FACES[direction];n=[0,0,0];n[axis]=sign
            normal=rotate(n,e.get('rotation'),True);plane=e['to' if sign>0 else 'from'][axis]
            points=[]
            for a,b in [(0,0),(1,0),(1,1),(0,1)]:
                p=[0,0,0];p[axis]=plane;p[u]=e['to' if a else 'from'][u];p[v]=e['to' if b else 'from'][v]
                points.append(rotate(p,e.get('rotation')))
            faces.append((index,direction,face['texture'],normal,points))
    failures=[]
    for a,b in itertools.combinations(faces,2):
        if sum((a[3][i]-b[3][i])**2 for i in range(3))>1e-10:continue
        if abs(sum(a[3][i]*(a[4][0][i]-b[4][0][i]) for i in range(3)))>1e-6:continue
        axis=max(range(3),key=lambda i:abs(a[3][i]));t=[i for i in range(3) if i!=axis]
        p,q=[ordered([[v[i] for i in t] for v in item[4]]) for item in [a,b]]
        shared=area(intersection(p,q))
        if shared>1e-6:failures.append((a[:3],b[:3],round(shared,5)))
    return failures


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--baseline');args=parser.parse_args()
    count=0
    for name in NAMES:
        relative=f'src/main/resources/assets/expeditions/models/block/{name}.json'
        if args.baseline:data=json.loads(subprocess.check_output(['git','-c','safe.directory='+ROOT.as_posix(),'show',args.baseline+':'+relative],cwd=ROOT))
        else:data=json.loads((ROOT/relative).read_text(encoding='utf-8'))
        issues=overlaps(data);count+=len(issues)
        if issues:print(name,len(issues),'overlapping face pairs:',issues[:5])
    print('Coplanar overlaps:',count)
    if count and not args.baseline:raise SystemExit(1)


if __name__=='__main__':main()
