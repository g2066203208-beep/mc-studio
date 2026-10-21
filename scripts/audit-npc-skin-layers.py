#!/usr/bin/env python3
"""Inspect actual 64x64 RGBA PNG alpha by canonical Minecraft outer-layer UV.

No Pillow dependency. A correctly packaged skin is NOT automatically a
finished double-layer illustration. Default mode reports failures without
blocking unrelated gameplay builds. --strict is the future art release gate.
"""
from __future__ import annotations

import argparse
import pathlib
import struct
import sys
import zlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
SKINS = ROOT / "mod/src/main/resources/assets/reme_studio/textures/entity/npc"
# Head helmet; jacket, right/left sleeve and right/left trousers.
OUTER_UV = {
    "hat": (32, 0, 64, 16),
    "jacket": (16, 32, 40, 48),
    "right_sleeve": (40, 32, 56, 48),
    "right_pants": (0, 32, 16, 48),
    "left_sleeve": (48, 48, 64, 64),
    "left_pants": (0, 48, 16, 64),
}


def alpha_pixels(png: bytes) -> bytes:
    if png[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("invalid PNG signature")
    offset = 8
    chunks = []
    width = height = 0
    while offset < len(png):
        size = struct.unpack_from(">I", png, offset)[0]
        tag = png[offset + 4:offset + 8]
        data = png[offset + 8:offset + 8 + size]
        if tag == b"IHDR":
            width, height, depth, color, compression, filtering, interlace = (
                struct.unpack(">IIBBBBB", data[:13]))
            if (width, height, depth, color, compression, filtering, interlace) != (
                    64, 64, 8, 6, 0, 0, 0):
                raise ValueError("expected standard 64x64 noninterlaced 8-bit RGBA")
        elif tag == b"IDAT":
            chunks.append(data)
        elif tag == b"IEND":
            break
        offset += size + 12
    raw = zlib.decompress(b"".join(chunks))
    stride, pixel_bytes = 64 * 4, 4
    previous = bytearray(stride)
    result = bytearray()
    offset = 0
    for row in range(64):
        if offset >= len(raw):
            raise ValueError("PNG truncated")
        filt = raw[offset]
        offset += 1
        source = raw[offset:offset + stride]
        if len(source) != stride:
            raise ValueError("PNG scanline truncated")
        offset += stride
        current = bytearray(stride)
        for x in range(stride):
            a = current[x - pixel_bytes] if x >= pixel_bytes else 0
            b = previous[x]
            c = previous[x - pixel_bytes] if x >= pixel_bytes else 0
            if filt == 0:
                predictor = 0
            elif filt == 1:
                predictor = a
            elif filt == 2:
                predictor = b
            elif filt == 3:
                predictor = (a + b) // 2
            elif filt == 4:
                p = a + b - c
                distances = (abs(p - a), abs(p - b), abs(p - c))
                predictor = (a, b, c)[distances.index(min(distances))]
            else:
                raise ValueError(f"invalid PNG filter {filt}")
            current[x] = (source[x] + predictor) & 255
        result.extend(current)
        previous = current
    if offset != len(raw):
        raise ValueError("unexpected data after PNG rows")
    return bytes(result[3::4])


def inspect(filename: pathlib.Path) -> dict[str, int]:
    alpha = alpha_pixels(filename.read_bytes())
    result = {}
    for name, (left, top, right, bottom) in OUTER_UV.items():
        result[name] = sum(
            alpha[y * 64 + x] > 0
            for y in range(top, bottom) for x in range(left, right)
        )
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--strict", action="store_true",
                        help="Fail until skin artists meet actual 4-part outer-layer standard")
    args = parser.parse_args()
    deficient = []
    for path in sorted(SKINS.glob("*.png")):
        try:
            coverage = inspect(path)
        except (ValueError, OSError) as exc:
            print(f"ERROR {path.name}: {exc}")
            deficient.append(path.name)
            continue
        active_parts = sum(bool(n) for n in coverage.values())
        print(f"{path.name}: {active_parts}/6 real outer parts; "
              + ", ".join(f"{k}={v}" for k, v in coverage.items()))
        if active_parts < 4:
            deficient.append(path.name)
    if not list(SKINS.glob("*.png")):
        print("ERROR: no skins found")
        return 1
    if deficient:
        print("ART_GATE_NOT_PASSED: true double-layer design still required for "
              + ", ".join(deficient))
        return 1 if args.strict else 0
    print("Skin UV presence gate passed; human art-direction/3D-game screenshot still required.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
