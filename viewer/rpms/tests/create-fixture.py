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
cube={'parent':'block/cube_all','textures':{'all':'simulated:block/swivel_bearing'}}
# No minecraft:block/cube_all geometry is shipped in this fixture: the viewer
# must resolve the vanilla namespace and build the geometry fallback itself.
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
    z.writestr('assets/simulated/blockstates/swivel_bearing.json',json.dumps(states))
    z.writestr('assets/simulated/models/block/swivel_bearing/block.json',json.dumps(cube))
    z.writestr('assets/simulated/textures/block/swivel_bearing.png',png())

    # Entirely original tiny Create-like fixture: NOT copied from Create assets.
    z.writestr('assets/create/blockstates/shaft.json',json.dumps({
        'variants':{f'axis={axis}':{'model':'create:block/shaft','x':90 if axis=='x' else 0,'y':90 if axis=='z' else 0}
        for axis in ('x','y','z')}}))
    z.writestr('assets/create/models/block/shaft.json',json.dumps({
        'textures':{'all':'create:block/fixture_metal'},
        'elements':[{'from':[6,0,6],'to':[10,16,10],
            'faces':{k:{'texture':'#all'} for k in ('north','south','east','west','up','down')}}]}))
    z.writestr('assets/create/textures/block/fixture_metal.png',png())
    # The multipart test verifies additively applying matching elements,
    # rather than pretending only one variant can render at a time.
    z.writestr('assets/create/blockstates/creative_motor.json',json.dumps({
        'multipart':[{'apply':{'model':'create:block/motor_body'}},
           {'when':{'facing':'east|west'},'apply':{'model':'create:block/motor_cap'}}]}))
    z.writestr('assets/create/models/block/motor_body.json',json.dumps({
        'textures':{'all':'create:block/fixture_metal'},
        'elements':[{'from':[1,1,1],'to':[15,15,15],
            'faces':{k:{'texture':'#all'} for k in ('north','south','east','west','up','down')}}]}))
    z.writestr('assets/create/models/block/motor_cap.json',json.dumps({
        'parent':'minecraft:block/cube_all',
        'textures':{'all':'create:block/fixture_metal'}}))
print(out, out.stat().st_size)
