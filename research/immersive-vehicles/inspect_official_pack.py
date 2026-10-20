#!/usr/bin/env python3
"""Inspect actual MTS 1.21.1 JAR files, render a vehicle mesh from archived OBJ + PNG."""
import hashlib, io, json, math, os, urllib.parse, urllib.request, zipfile
from collections import Counter
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

OUT = Path("research/immersive-vehicles")
OUT.mkdir(parents=True, exist_ok=True)
CACHE = Path("/tmp/mts-inspect")
CACHE.mkdir(exist_ok=True)
UA = {"User-Agent": "mc-studio-reference-inspector/1.0 (Minecraft vehicle modeling study)"}

def get(url):
    req=urllib.request.Request(url,headers=UA)
    with urllib.request.urlopen(req, timeout=180) as f:
        return f.read()

def download_project(project, label):
    versions=json.loads(get("https://api.modrinth.com/v2/project/"+project+"/version?game_versions=%5B%221.21.1%22%5D&loaders=%5B%22neoforge%22%5D"))
    if not versions: raise RuntimeError("No Minecraft 1.21.1 NeoForge release for "+label)
    v=next((x for x in versions if x.get("version_type")=="release"),versions[0])
    f=next((f for f in v["files"] if f.get("primary")),v["files"][0])
    content=get(f["url"])
    sha=hashlib.sha256(content).hexdigest()
    if f.get("hashes",{}).get("sha512"):
        assert hashlib.sha512(content).hexdigest()==f["hashes"]["sha512"],"SHA512 mismatch"
    if f.get("hashes",{}).get("sha1"):
        assert hashlib.sha1(content).hexdigest()==f["hashes"]["sha1"],"SHA1 mismatch"
    p=CACHE/(label+".jar");p.write_bytes(content)
    print(label,"downloaded",len(content),"bytes",v["name"],"SHA256",sha,flush=True)
    return p,{"name":v["name"],"file":f["filename"],"version":v["version_number"],"size":len(content),"sha256":sha,"download":f["url"],"published":v["date_published"]}

core,core_meta=download_project("BCzBuhJ5","immersive-vehicles-core")
pack,pack_meta=download_project("C7TiPPJz","official-content-pack")

with zipfile.ZipFile(pack) as z:
    names=z.namelist()
    def asset(folder,filename):
        found=[n for n in names if ("/"+folder+"/" in "/"+n.lower()) and n.lower().endswith("/"+filename.lower())]
        if not found: found=[n for n in names if n.lower().endswith("/"+filename.lower())]
        if not found: raise RuntimeError("Missing asset: "+filename)
        return found[0],z.read(found[0])
    obj_name,obj_data=asset("objmodels","fordmustang69.obj")
    tex_name,tex_data=asset("textures/vehicles","fordmustang69_red.png")

vs=[];uvs=[];faces=[];current="unnamed";groupnames=set()
for line in obj_data.decode("utf-8-sig",errors="replace").splitlines():
    if line.startswith("v "):
        a=line.split();vs.append(tuple(map(float,a[1:4])))
    elif line.startswith("vt "):
        a=line.split();uvs.append(tuple(map(float,a[1:3])))
    elif line.startswith("o "):
        current=line[2:].strip();groupnames.add(current)
    elif line.startswith("f "):
        vertex=[];uv_idx=[]
        for seg in line[2:].strip().split():
            tok=seg.split("/")
            i=int(tok[0]);vertex.append(i-1 if i>0 else len(vs)+i)
            t=int(tok[1]) if len(tok)>1 and tok[1] else 0
            uv_idx.append(t-1 if t>0 else -1)
        faces.append((vertex,uv_idx,current))

texture=Image.open(io.BytesIO(tex_data)).convert("RGBA")
print("actual JAR entries:",obj_name,tex_name,flush=True)
print("mesh:",len(vs),"vertices",len(uvs),"UVs",len(faces),"polygons",len(groupnames),"objects",flush=True)

def font(size,bold=False):
    pref="/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
    return ImageFont.truetype(pref,size)

def render(deg, elev, label, width=720, height=640):
    az=math.radians(deg);el=math.radians(elev)
    direction=(math.sin(az)*math.cos(el),math.sin(el),math.cos(az)*math.cos(el))
    right=(math.cos(az),0,-math.sin(az))
    up=(-math.sin(az)*math.sin(el),math.cos(el),-math.cos(az)*math.sin(el))
    def dot(a,b):return sum(x*y for x,y in zip(a,b))
    vv=[(dot(v,right),dot(v,up),dot(v,direction)) for v in vs]
    xmin=min(v[0] for v in vv);xmax=max(v[0] for v in vv)
    ymin=min(v[1] for v in vv);ymax=max(v[1] for v in vv)
    scale=min((width-80)/(xmax-xmin),(height-160)/(ymax-ymin))
    def P(idx):
        v=vv[idx]
        return (int(width/2+(v[0]-(xmin+xmax)/2)*scale),int(height/2-5-(v[1]-(ymin+ymax)/2)*scale))
    img=Image.new("RGB",(width,height),(25,35,47));d=ImageDraw.Draw(img)
    d.rounded_rectangle((12,12,width-12,height-12),radius=16,fill=(29,42,56),outline=(61,77,96),width=2)
    ordered=[]
    for ids,tids,obj in faces:
        depth=sum(vv[i][2] for i in ids)/len(ids)
        ordered.append((depth,ids,tids,obj))
    ordered.sort(key=lambda f:f[0])
    tw,th=texture.size
    for _,ids,tids,obj in ordered:
        points=[P(i) for i in ids]
        if len(points)<3:continue
        p0,p1,p2=(vs[ids[i]] for i in range(3))
        A=[p1[i]-p0[i] for i in range(3)]
        B=[p2[i]-p0[i] for i in range(3)]
        n=(A[1]*B[2]-A[2]*B[1],A[2]*B[0]-A[0]*B[2],A[0]*B[1]-A[1]*B[0])
        norm=math.sqrt(dot(n,n)) or 1.0
        diffuse=.68+.32*abs(dot(n,(.46,.81,.36))/norm)
        if all(0<=i<len(uvs) for i in tids):
            u=sum(uvs[i][0] for i in tids)/len(tids)
            v=sum(uvs[i][1] for i in tids)/len(tids)
            pix=texture.getpixel((max(0,min(tw-1,int(u*(tw-1)))),max(0,min(th-1,int((1-v)*(th-1))))))
            rgb=pix[:3]
            if pix[3]<8:rgb=(160,48,56)
        else:rgb=(160,48,56)
        fill=tuple(max(0,min(255,int(x*diffuse))) for x in rgb)
        # Light colored CAD seam lines keep actual OBJ topology visible.
        d.polygon(points,fill=fill)
        d.line(points+[points[0]],fill=tuple(int(x*.52) for x in fill),width=1,joint="curve")
    d.text((31,35),label,font=font(25,True),fill=(246,248,252))
    d.text((31,height-64),"Actual MTS V29 OBJ + red PNG from JAR",font=font(14),fill=(196,211,223))
    d.text((31,height-42),"Offline mesh preview | no Minecraft client launched",font=font(13),fill=(150,167,183))
    return img

views=[
    ("mustang_front_3quarter_actual_jar.png",44,18,"Front three-quarter"),
    ("mustang_side_actual_jar.png",88,10,"Right-side view"),
    ("mustang_rear_3quarter_actual_jar.png",216,22,"Rear three-quarter")
]
renders=[]
for name,az,el,label in views:
    im=render(az,el,label);im.save(OUT/name,optimize=True);renders.append(im)
collage=Image.new("RGB",(2160,710),(17,25,37))
for i,im in enumerate(renders): collage.paste(im,(i*720,70))
dt=ImageDraw.Draw(collage)
dt.text((26,20),"IMMERSIVE VEHICLES | REAL OFFICIAL PACK ASSETS",font=font(30,True),fill=(244,247,252))
collage.save(OUT/"mustang_jar_model_inspection.png",optimize=True)
metadata={"core":core_meta,"official_pack":pack_meta,"model_in_pack":obj_name,"texture_in_pack":tex_name,"texture_size":texture.size,"vertices":len(vs),"uv_vertices":len(uvs),"faces":len(faces),"object_count":len(groupnames),"objects":sorted(groupnames),"screenshots":[v[0] for v in views]+["mustang_jar_model_inspection.png"],"method":"unmodified OBJ vertex/face topology + PNG atlas from downloaded modpack JAR; Python offscreen software-rendered projection, not in-game screenshot"}
(OUT/"jar_inspection.json").write_text(json.dumps(metadata,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
(OUT/"README.md").write_text("# Immersive Vehicles 1.21.1 actual JAR inspection\n\nDownloaded the **official core mod** and **official content pack** from Modrinth release binaries at CI run time; verified checksums. Extracted the Ford Mustang 1969 OBJ and red PNG texture from inside the **official pack JAR**; rendered the genuine mesh in three views.\n\n- Screenshot: `mustang_jar_model_inspection.png`\n- Reproduction: `python3 research/immersive-vehicles/inspect_official_pack.py` (requires Pillow and network access)\n- Source metadata and SHA256: `jar_inspection.json`\n- Important: These are **offline mesh previews, not running-Minecraft or Blender GUI screenshots**. Removable wheels, seats, and other separately mounted content parts are not assembled by this renderer.\n- Copyright: Original models, textures and jars are the creators' assets; binaries and original assets are **not republished** here.\n",encoding="utf-8")
print("inspection screenshots completed",flush=True)
