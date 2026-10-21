#!/usr/bin/env python3
"""Inspect an original OBJ mesh, PNG atlas and optional MTS vehicle JSON/JSONC.
Pure Python 3.11+ standard library; never modifies original assets.
"""
from __future__ import annotations

import argparse
import json
import math
import re
import struct
import sys
from collections import Counter
from pathlib import Path


def _clean_jsonc(s: str) -> str:
    """Remove // and /* */ comments plus trailing commas without touching strings."""
    out = []
    pos = 0
    quoted = False
    escaped = False
    while pos < len(s):
        ch = s[pos]
        if quoted:
            out.append(ch)
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == '"':
                quoted = False
            pos += 1
            continue
        if ch == '"':
            quoted = True
            out.append(ch)
            pos += 1
        elif s.startswith("//", pos):
            pos = s.find("\n", pos)
            if pos == -1:
                break
            out.append("\n")
            pos += 1
        elif s.startswith("/*", pos):
            end = s.find("*/", pos + 2)
            if end == -1:
                raise ValueError("unclosed JSONC block comment")
            out.append("\n" * s[pos:end + 2].count("\n"))
            pos = end + 2
        else:
            out.append(ch)
            pos += 1
    return re.sub(r",(?=\s*[}\]])", "", "".join(out))


def parse_obj(src: str) -> dict:
    vertices: list[tuple[float, float, float]] = []
    uvs: list[tuple[float, float]] = []
    normals: list[tuple[float, float, float]] = []
    objects: list[str] = []
    groups: list[str] = []
    material_names: set[str] = set()
    faces = 0
    triangles = 0
    uv_faces = 0
    missing_uv_faces = 0
    uv_outside = 0
    degenerate = 0
    max_polygon = 0
    errors: list[str] = []
    warnings: list[str] = []
    current_object = "(unassigned)"
    polygons_per_object: Counter[str] = Counter()

    def vec(parts: list[str], need: int, lineno: int):
        if len(parts) < need:
            errors.append(f"line {lineno}: too few coordinates")
            return None
        try:
            values = tuple(float(v) for v in parts[:need])
        except ValueError:
            errors.append(f"line {lineno}: nonnumeric coordinate")
            return None
        if not all(math.isfinite(v) for v in values):
            errors.append(f"line {lineno}: NaN/Inf coordinate")
            return None
        return values

    def idx(value: str, length: int, lineno: int, label: str) -> int | None:
        try:
            n = int(value)
        except ValueError:
            errors.append(f"line {lineno}: malformed {label} index {value!r}")
            return None
        candidate = n - 1 if n > 0 else length + n if n < 0 else -1
        if not 0 <= candidate < length:
            errors.append(f"line {lineno}: {label} index {n} out of range (count={length})")
            return None
        return candidate

    for lineno, line in enumerate(src.splitlines(), start=1):
        line = line.split("#", 1)[0].strip()
        if not line:
            continue
        pieces = line.split()
        op, args = pieces[0], pieces[1:]
        if op == "v":
            v = vec(args, 3, lineno)
            if v:
                vertices.append(v)
        elif op == "vt":
            vt = vec(args, 2, lineno)
            if vt:
                uvs.append(vt)
                if not all(0 <= x <= 1 for x in vt):
                    uv_outside += 1
        elif op == "vn":
            vn = vec(args, 3, lineno)
            if vn:
                normals.append(vn)
        elif op == "o":
            current_object = " ".join(args) if args else "(unnamed)"
            objects.append(current_object)
        elif op == "g":
            groups.append(" ".join(args) if args else "(unnamed)")
        elif op == "usemtl" and args:
            material_names.add(" ".join(args))
        elif op == "f":
            if len(args) < 3:
                errors.append(f"line {lineno}: polygon with less than 3 vertices")
                continue
            vi: list[int] = []
            all_uv = True
            for ref in args:
                blocks = ref.split("/")
                if len(blocks) > 3 or not blocks[0]:
                    errors.append(f"line {lineno}: invalid face token {ref!r}")
                    continue
                n = idx(blocks[0], len(vertices), lineno, "vertex")
                if n is not None:
                    vi.append(n)
                if len(blocks) < 2 or not blocks[1]:
                    all_uv = False
                else:
                    idx(blocks[1], len(uvs), lineno, "UV")
                if len(blocks) == 3 and blocks[2]:
                    idx(blocks[2], len(normals), lineno, "normal")
            if len(vi) != len(args):
                continue
            faces += 1
            triangles += len(vi) - 2
            max_polygon = max(max_polygon, len(vi))
            polygons_per_object[current_object] += 1
            if all_uv:
                uv_faces += 1
            else:
                missing_uv_faces += 1
            a, b, c = (vertices[n] for n in vi[:3])
            ab = [b[i] - a[i] for i in range(3)]
            ac = [c[i] - a[i] for i in range(3)]
            cross = (ab[1]*ac[2] - ab[2]*ac[1],
                     ab[2]*ac[0] - ab[0]*ac[2],
                     ab[0]*ac[1] - ab[1]*ac[0])
            if sum(x*x for x in cross) < 1e-18:
                degenerate += 1

    if not vertices:
        errors.append("no vertices present")
    if not faces:
        errors.append("no valid faces present")
    if missing_uv_faces:
        warnings.append(f"{missing_uv_faces} faces missing complete UV mapping")
    if degenerate:
        warnings.append(f"{degenerate} polygons have degenerate first triangle")
    if uv_outside:
        warnings.append(f"{uv_outside} UVs outside [0,1], possibly intentionally tiled")
    if max_polygon > 4:
        warnings.append(f"largest polygon has {max_polygon} vertices: verify triangulation/rendering")
    if len(objects) != len(set(objects)):
        warnings.append("duplicate OBJ object names (animation references may be ambiguous)")
    if not objects:
        warnings.append("no explicit OBJ object names")
    if not uvs:
        warnings.append("no vt texture coordinates")
    dimensions = ([(min(v[i] for v in vertices),
                    max(v[i] for v in vertices)) for i in range(3)]
                  if vertices else [])
    return {
        "geometry": {
            "vertices": len(vertices), "uvs": len(uvs),
            "normals": len(normals), "faces": faces, "triangulated_faces": triangles,
            "object_count": len(objects), "objects": objects, "groups": groups,
            "per_object_faces": dict(sorted(polygons_per_object.items())),
            "materials_used": sorted(material_names), "uv_mapped_faces": uv_faces,
            "faces_missing_uv": missing_uv_faces,
            "uv_outside_unit_count": uv_outside, "degenerate_faces": degenerate,
            "bounds_xyz": [[round(float(t), 6) for t in d] for d in dimensions],
            "max_polygon_vertices": max_polygon
        }, "errors": errors, "warnings": warnings
    }


def png_dimensions(path: Path) -> tuple[int, int]:
    with path.open("rb") as f:
        h = f.read(24)
    if len(h) < 24 or h[:8] != b"\x89PNG\r\n\x1a\n" or h[12:16] != b"IHDR":
        raise ValueError("not a valid PNG IHDR header")
    w, hgt = struct.unpack(">II", h[16:24])
    if not 0 < w < 100000 and 0 < hgt < 100000:
        raise ValueError("invalid PNG width/height")
    return w, hgt


def inspect(obj_path: Path, texture: Path | None = None,
            definition: Path | None = None) -> dict:
    try:
        result = parse_obj(obj_path.read_text(encoding="utf-8-sig"))
    except (OSError, UnicodeError) as ex:
        return {"source": str(obj_path), "geometry": {}, "errors": [str(ex)], "warnings": []}
    result["source"] = str(obj_path)
    if texture:
        try:
            result["texture"] = {"path": str(texture), "dimensions": list(png_dimensions(texture))}
            if max(result["texture"]["dimensions"]) <= 64:
                result["warnings"].append("texture <=64px: verify this is a vehicle UV atlas, not item icon")
        except (OSError, ValueError) as ex:
            result["errors"].append(f"texture: {ex}")
    if definition:
        try:
            doc = json.loads(_clean_jsonc(definition.read_text(encoding="utf-8-sig")))
            rendering = doc.get("rendering", {})
            parts = doc.get("parts", [])
            objects = set(result["geometry"].get("objects", []))
            animated = [
                a.get("objectName") for a in rendering.get("animatedObjects", [])
                if isinstance(a, dict) and a.get("objectName")
            ]
            missing = sorted(set(animated) - objects)
            result["definition"] = {
                "path": str(definition),
                "top_level_keys": sorted(doc),
                "parts_count": len(parts),
                "animated_objects_count": len(animated),
                "missing_animated_objects": missing,
                "light_objects_count": len(rendering.get("lightObjects", [])),
                "collision_groups_count": len(doc.get("collisionGroups", []))
            }
            if missing:
                result["warnings"].append(
                    f"{len(missing)} animated objectName values do not occur in this OBJ (check cross-file assets)")
            for n, part in enumerate(parts):
                if not isinstance(part, dict):
                    result["errors"].append(f"parts[{n}] must be an object")
                elif "pos" in part and (
                    not isinstance(part["pos"], list) or len(part["pos"]) != 3
                    or any(not isinstance(a, (int, float)) or not math.isfinite(a) for a in part["pos"])):
                    result["errors"].append(f"parts[{n}].pos must be finite XYZ array")
        except (OSError, ValueError, TypeError, AttributeError) as ex:
            result["errors"].append(f"definition: {ex}")
    return result


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("obj", type=Path, help="OBJ path on disk")
    p.add_argument("--texture", type=Path, help="optional vehicle UV atlas PNG (not item icon)")
    p.add_argument("--definition", type=Path, help="optional MTS vehicle JSON/JSONC")
    p.add_argument("--json", action="store_true", help="emit machine-readable JSON report")
    p.add_argument("--fail-on-warning", action="store_true",
                   help="also treat warnings as failures (usually best used for newly authored models)")
    args = p.parse_args(argv)
    result = inspect(args.obj, args.texture, args.definition)
    if args.json:
        print(json.dumps(result, indent=2, ensure_ascii=False, sort_keys=True))
    else:
        print("SOURCE", result["source"])
        for key, value in result.get("geometry", {}).items():
            if key not in ("objects", "per_object_faces", "groups"):
                print(f"  {key}: {value}")
        if result.get("texture"):
            print("TEXTURE", result["texture"])
        if result.get("definition"):
            print("DEFINITION", result["definition"])
        for issue in result["errors"]:
            print("ERROR:", issue, file=sys.stderr)
        for issue in result["warnings"]:
            print("WARN:", issue, file=sys.stderr)
    return 2 if result["errors"] else (1 if result["warnings"] and args.fail_on_warning else 0)


if __name__ == "__main__":
    raise SystemExit(main())
