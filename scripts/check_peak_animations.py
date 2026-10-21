#!/usr/bin/env python3
"""Check PEAK JSON animation loops and restoration poses (not a visual approval)."""
import json
import math
from pathlib import Path

src=Path(__file__).resolve().parents[1] / "mod/src/main/resources/assets/reme_studio/player_animations/peak_climb.animation.json"
clips=json.loads(src.read_text(encoding="utf-8"))["animations"]
expected={"peak_hang","peak_ascend","peak_traverse","peak_mantle",
          "peak_release","peak_ceiling_hang","peak_ceiling_crawl",
          "peak_ceiling_traverse","peak_wall_to_ceiling","peak_ceiling_release",
          "peak_wall_jump"}
errors=[]
if set(clips)!=expected:
    errors.append("Unexpected animation keys; missing %s extra %s"%(expected-set(clips),set(clips)-expected))
for name,clip in clips.items():
    duration=float(clip["animation_length"])
    if not .1<=duration<=3: errors.append(f"{name}: invalid duration")
    if not {"left_arm","right_arm","left_leg","right_leg","torso","head"}.issubset(clip["bones"]):
        errors.append(f"{name}: incomplete skeleton")
    for bone_name,bone in clip["bones"].items():
        for channel_name in ("rotation","position"):
            data=bone.get(channel_name)
            if data is None: continue
            direct="vector" in data
            timed=[(0,data["vector"])] if direct else [
                (float(t),frame["vector"]) for t,frame in data.items()]
            for t,vector in timed:
                if not 0<=t<=duration+1e-5: errors.append(f"{name}/{bone_name}: key beyond duration")
                if not isinstance(vector,list) or len(vector) not in (2,3):
                    errors.append(f"{name}/{bone_name}: invalid vector");continue
                bound=185 if channel_name=="rotation" else 5
                if not all(isinstance(v,(float,int)) and math.isfinite(v) and abs(v)<=bound for v in vector):
                    errors.append(f"{name}/{bone_name}: invalid or excessive {channel_name}")
            if clip.get("loop") and not direct:
                start=next((vec for t,vec in timed if abs(t)<1e-4),None)
                end=next((vec for t,vec in timed if abs(t-duration)<1e-4),None)
                if start is None or end is None or start!=end:
                    errors.append(f"{name}/{bone_name}: loop has a visible seam")
            if name in ("peak_release","peak_ceiling_release"):
                last=next((vec for t,vec in timed if abs(t-duration)<1e-4),None)
                if last is None or any(abs(v)>.0001 for v in last):
                    errors.append(f"{name}/{bone_name}: release must end in vanilla neutral")
        if name.startswith("peak_ceiling_") and name not in ("peak_ceiling_release",) and bone_name in ("torso","left_leg","right_leg"):
            rotation=bone.get("rotation",{})
            values=[rotation["vector"]] if "vector" in rotation else [
                v["vector"] for v in rotation.values()]
            if any(abs(v[0])>20 for v in values):
                errors.append(f"{name}/{bone_name}: hanging torso/legs must stay vertical")
for error in errors: print("ERROR:",error)
if errors: raise SystemExit(f"{len(errors)} animation errors")
print(f"OK: {len(clips)} animation clips have bounded keyframes, loop continuity and neutral release")
