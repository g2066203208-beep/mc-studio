#!/usr/bin/env python3
"""Validate RPMS system-engineering MACHINE DESIGN, not actual Sable locomotion."""
from __future__ import annotations
import csv
import json
from pathlib import Path
import re

ROOT=Path(__file__).resolve().parents[2]
SPEC=ROOT/"projects/physical-machinery/quad12.design.json"
BOM=ROOT/"docs/physical-machinery/COMPONENT_REGISTER.csv"
REQ=ROOT/"docs/physical-machinery/01_SYSTEM_REQUIREMENTS.md"
VVP=ROOT/"docs/physical-machinery/05_TEST_VALIDATION_AND_RELEASE.md"
ERR=[]

def ok(condition, note):
    if not condition: ERR.append(note)

def main():
    d=json.loads(SPEC.read_text(encoding="utf-8"))
    seg=d.get("segments", [])
    j=d.get("joints", [])
    feet=d.get("feet",[])
    seg_ids=[s["id"] for s in seg]
    joint_ids=[x["id"] for x in j]
    ok(len(seg)==13 and len(set(seg_ids))==13, "Need exactly 13 UNIQUE real rigid-body segment descriptors")
    ok(len(j)==12 and len(set(joint_ids))==12, "Need exactly 12 UNIQUE active joint descriptors")
    ok(len(feet)==4 and len({x["id"] for x in feet})==4, "Need 4 separate physical foot-pad descriptors")
    ok(d.get("prototype",{}).get("manualPoseTeleportForbidden") is True, "Pose teleport must remain forbidden")
    ok(d.get("status")=="DESIGN_ONLY", "Do not falsely present configuration as a running machine")
    ok(all(s["runtimeSubLevelUuid"] is None for s in seg), "Unverified runtime UUID must remain null")
    ok(all(x["measuredTorqueMax"] is None and x["measuredSpeedMax"] is None
           and x["jointZeroCalibration"] is None for x in j), "Do not invent torque or calibrations")
    refs={s["id"]:s["parent"] for s in seg}
    roots=[s["id"] for s in seg if s["parent"] is None]
    ok(roots==["BODY"], "Single root BODY required")
    for sid, parent in refs.items():
        ok(parent is None or parent in refs, f"{sid}: parent segment is missing")
        seen=set()
        p=sid
        while p is not None and p not in seen:
            seen.add(p)
            p=refs.get(p)
        ok(p is None, f"Assembly cyclic at {sid}")
    connection_pairs=set()
    for x in j:
        p,c=x["parentSegment"],x["childSegment"]
        ok(p in refs and c in refs and p!=c,f"{x['id']}: invalid physical pair")
        ok(refs.get(c)==p,f"{x['id']}: joint not aligned to physical segment parent")
        ok((p,c) not in connection_pairs,f"{x['id']}: duplicate joint physical pair")
        connection_pairs.add((p,c))
        a=x.get("axisParentLocal",[])
        ok(len(a)==3 and sum(v*v for v in a)==1,f"{x['id']}: joint axis not normalized")
        ok(-180<=x["minDeg"]<x["maxDeg"]<=180,f"{x['id']}: joint travel invalid")
        ok(x["validationState"]=="NOT_MEASURED",f"{x['id']}: test not recorded")
    ok(len(connection_pairs)==len(seg)-1,"Assembly graph should have exactly 12 joint edges")
    for leg in ("LF","RF","LR","RR"):
        ok({leg+".HAA",leg+".HFE",leg+".KFE"}.issubset(joint_ids),
           leg+" missing HAA/HFE/KFE")
        ok(any(x["segmentId"]==leg+"_LOWER_FOOT" for x in feet),
           leg+" foot sensor not connected to correct Sable segment")

    with BOM.open(newline="",encoding="utf-8") as f:
        rows=list(csv.DictReader(f))
    ids=[r["id"] for r in rows]
    ok(len(ids)==len(set(ids)), "Duplicate component BOM IDs")
    for key,count in [("physical_root",1),("hip_carrier",4),("upper_leg",4),
                      ("lower_foot",4),("active_rotary_servo",12),("foot_contact_pad",4),
                      ("joint_angle_feedback",12),("safety_supervisor",1)]:
        row=next((x for x in rows if x["key"]==key),None)
        ok(row is not None and int(row["prototype_quantity"])==count,
           f"Missing/incorrect BOM {key}: {count}")

    requirements=REQ.read_text(encoding="utf-8")
    ids=set(re.findall(r"\|\s*(SYS-\d{3})\s*\|",requirements))
    ok(len(ids)>=25,"Too few traceable requirements")
    ok(all(f"SYS-{i:03d}" in ids for i in range(1,31)),"SYS-001..SYS-030 requirements must be present")
    vv=VVP.read_text(encoding="utf-8")
    for gate in ("G0","G1","G2","G3","G4","G5","G6"):
        ok(gate in vv,f"Missing verification gate {gate}")
    if ERR:
        print("\n".join("DESIGN ERROR: "+x for x in ERR))
        raise SystemExit(f"{len(ERR)} design-baseline violations")
    print(f"RPMS design-baseline OK: {len(seg)} segments, {len(j)} joint constraints, "
          f"{len(feet)} foot pads, {len(rows)} component definitions, "
          f"{len(ids)} requirements and 7 gate families.")
    print("This is design data validation ONLY; physical standing/walking is NOT tested.")

if __name__=="__main__":
    main()
