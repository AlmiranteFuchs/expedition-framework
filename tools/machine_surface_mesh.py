"""Exposed machine faces with exact UV crops and unchanged collision solids."""
import copy
import json

# Normal axis/sign, tangent axes and Minecraft's unrotated UV directions.
FACES={'down':(1,-1,0,2,1,-1),'up':(1,1,0,2,1,1),
       'north':(2,-1,0,1,-1,-1),'south':(2,1,0,1,1,-1),
       'west':(0,-1,2,1,1,-1),'east':(0,1,2,1,-1,-1)}
EPS=1e-7


def subtract(rect,cut):
    x,y,X,Y=rect;a,b,A,B=cut
    a,b,A,B=max(x,a),max(y,b),min(X,A),min(Y,B)
    if A-a<=EPS or B-b<=EPS:return [rect]
    return [r for r in [(x,y,a,Y),(A,y,X,Y),(a,y,A,b),(a,B,A,Y)] if r[2]-r[0]>EPS and r[3]-r[1]>EPS]


def cropped_uv(face,source,rect,direction):
    _,_,u,v,us,vs=FACES[direction]
    bounds=[source['from'][u],source['from'][v],source['to'][u],source['to'][v]]
    fractions=[(rect[0]-bounds[0])/(bounds[2]-bounds[0]),(rect[1]-bounds[1])/(bounds[3]-bounds[1]),
               (rect[2]-bounds[0])/(bounds[2]-bounds[0]),(rect[3]-bounds[1])/(bounds[3]-bounds[1])]
    a,b,A,B=fractions
    if us<0:a,A=1-A,1-a
    if vs<0:b,B=1-B,1-b
    rotation=face.get('rotation',0)
    # Matches BlockFaceUV's shifted corner indices: 90deg maps (u,v) to (v,1-u).
    if rotation==90:a,b,A,B=b,1-A,B,1-a
    elif rotation==180:a,b,A,B=1-A,1-B,1-a,1-b
    elif rotation==270:a,b,A,B=1-B,a,1-b,A
    x,y,X,Y=face['uv']
    return [round(x+(X-x)*a,8),round(y+(Y-y)*b,8),round(x+(X-x)*A,8),round(y+(Y-y)*B,8)]


def exposed(elements):
    result=[]
    def group(e):return json.dumps(e.get('rotation'),sort_keys=True)
    def opaque(e):return all(f['texture'] not in ['#glass','#empty'] for f in e['faces'].values())
    for index,source in enumerate(elements):
        if all(f['texture']=='#empty' for f in source['faces'].values()):
            result.append(copy.deepcopy(source));continue
        # One transparent face is required by Minecraft's element parser. This
        # body preserves the original solid for FieldMachineShapes, not rendering.
        body=copy.deepcopy(source);body['faces']={'north':{'texture':'#empty','uv':[0,0,16,16]}}
        body['expeditions_collision_only']=True;result.append(body)
        for direction,face in source['faces'].items():
            axis,sign,u,v,_,_=FACES[direction]
            plane=source['to' if sign>0 else 'from'][axis]
            rectangles=[(source['from'][u],source['from'][v],source['to'][u],source['to'][v])]
            for other_index,other in enumerate(elements):
                if other_index==index or group(other)!=group(source) or not opaque(other):continue
                low,high=other['from'][axis],other['to'][axis]
                inside=low+EPS<plane<high-EPS
                contact=abs((low if sign>0 else high)-plane)<=EPS
                same=abs((high if sign>0 else low)-plane)<=EPS
                if not (inside or contact or same and (other_index>index or not opaque(source))):continue
                cut=(other['from'][u],other['from'][v],other['to'][u],other['to'][v])
                rectangles=[piece for rect in rectangles for piece in subtract(rect,cut)]
            for rect in rectangles:
                e=copy.deepcopy(source);e['from'][u],e['from'][v],e['to'][u],e['to'][v]=rect
                f=copy.deepcopy(face);f['uv']=cropped_uv(face,source,rect,direction)
                e['faces']={direction:f};e['expeditions_render_only']=True;result.append(e)
    return result
