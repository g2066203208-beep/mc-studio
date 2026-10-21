#!/usr/bin/env python3
"""Original synthetic JAR for parser testing; NO copied Mod art."""
import json,pathlib,struct,zlib,zipfile
out=pathlib.Path(__file__).with_name('fixture-simulated.jar')
def chunk(tag,body):
    return struct.pack('>I',len(body))+tag+body+struct.pack('>I',zlib.crc32(tag+body)&0xffffffff)
def png():
    w=h=16
    raw=b''.join(b'\0'+bytes([225,168,91,255])*w for y in range(h))
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',w,h,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b'')
states={'variants':{f'assembled=false,facing={f},powered=false':{'model':'simulated:block/swivel_bearing/block'} for f in ('north','south','east','west','up','down')}}
cube={'textures':{'all':'simulated:block/swivel_bearing'},'elements':[{'from':[0,0,0],'to':[16,16,16],
    'faces':{k:{'uv':[0,0,16,16],'texture':'#all'} for k in ('north','south','east','west','up','down')}}]}
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
    z.writestr('assets/simulated/blockstates/swivel_bearing.json',json.dumps(states))
    z.writestr('assets/simulated/models/block/swivel_bearing/block.json',json.dumps(cube))
    z.writestr('assets/simulated/textures/block/swivel_bearing.png',png())
print(out, out.stat().st_size)
