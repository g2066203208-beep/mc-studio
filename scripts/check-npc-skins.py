#!/usr/bin/env python3
from pathlib import Path
import struct,zipfile,hashlib
p=Path(__file__).resolve().parents[1]
folder=p/"mod/src/main/resources/assets/reme_studio/textures/entity/npc"
skins=["green_farmer","field_explorer","formal_trader","blue_farmer","teal_explorer","brown_trader"]
digests=set()
for name in skins:
    file=folder/(name+".png")
    data=file.read_bytes()
    assert data[:8]==b"\x89PNG\r\n\x1a\n",f"{name}: not PNG"
    assert struct.unpack(">II",data[16:24])==(64,64),f"{name}: wrong dimensions"
    digests.add(hashlib.sha256(data).hexdigest())
assert len(digests)==len(skins),"skin assets not unique"
jar=p/"mod/build/libs/reme_studio-0.5.5-dev.jar"
if jar.is_file():
    with zipfile.ZipFile(jar) as z:
        for name in skins:
            fname=f"assets/reme_studio/textures/entity/npc/{name}.png"
            assert fname in z.namelist(),f"{name} skin missing from build JAR"
            assert z.read(fname)==(folder/(name+".png")).read_bytes()
print(f"PASS: {len(skins)} distinct, valid 64x64 skins; JAR contents verified when available.")
