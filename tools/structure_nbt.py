"""Small standard-library codec for native Minecraft structure NBT."""
import gzip
import io
import struct
from pathlib import Path


def _string(s):
    b=s.encode('utf-8');return struct.pack('>H',len(b))+b


def _type(v):
    if isinstance(v,str):return 8
    if isinstance(v,dict):return 10
    if isinstance(v,list):return 9
    if isinstance(v,int):return 3
    raise TypeError(type(v))


def _payload(v):
    t=_type(v)
    if t==8:return _string(v)
    if t==3:return struct.pack('>i',v)
    if t==10:return b''.join(bytes([_type(x)])+_string(k)+_payload(x) for k,x in v.items())+b'\0'
    if t==9:return bytes([_type(v[0]) if v else 0])+struct.pack('>i',len(v))+b''.join(_payload(x) for x in v)


def write(path,data):
    path=Path(path);path.parent.mkdir(parents=True,exist_ok=True)
    path.write_bytes(gzip.compress(b'\x0a\0\0'+_payload(data),mtime=0))


def read(path):
    raw=Path(path).read_bytes();f=io.BytesIO(gzip.decompress(raw) if raw[:2]==b'\x1f\x8b' else raw)
    def unpack(fmt):return struct.unpack('>'+fmt,f.read(struct.calcsize('>'+fmt)))[0]
    def string():return f.read(unpack('H')).decode('utf-8')
    def payload(t):
        if t in [1,2,3,4,5,6]:return unpack({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t])
        if t==8:return string()
        if t==9:
            kind=unpack('B');return [payload(kind) for _ in range(unpack('i'))]
        if t==10:
            out={}
            while (kind:=unpack('B')):
                key=string();out[key]=payload(kind)
            return out
        if t in [7,11,12]:return [unpack({7:'b',11:'i',12:'q'}[t]) for _ in range(unpack('i'))]
        raise ValueError('Unsupported NBT tag '+str(t))
    kind=unpack('B');string();return payload(kind)


def structure(size,cells,author='F'):
    """cells: (local xyz, registry id, property dict); fills native air cells too."""
    states={tuple(p):(name,properties) for p,name,properties in cells};palette=[];indices={};blocks=[]
    for y in range(size[1]):
        for z in range(size[2]):
            for x in range(size[0]):
                name,props=states.get((x,y,z),('minecraft:air',{}));key=(name,tuple(sorted(props.items())))
                if key not in indices:
                    indices[key]=len(palette);entry={'Name':name}
                    if props:entry['Properties']=dict(props)
                    palette.append(entry)
                blocks.append({'pos':[x,y,z],'state':indices[key]})
    return {'DataVersion':3955,'author':author,'size':list(size),'palette':palette,'blocks':blocks,'entities':[]}
