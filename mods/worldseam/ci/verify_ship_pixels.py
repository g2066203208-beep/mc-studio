#!/usr/bin/env python3
"""Fail-closed screenshot gate for the real blue/yellow Minecraft test aircraft.

Looks for candidate pixels of BOTH actual light-blue and yellow concrete in
one compact region of EACH unmodified X11 screenshot. Passing is necessary,
not sufficient: this does not establish Sable UUID, native physics, two-chart
contacts, draw-call ownership, or real unassisted propulsion. Review MP4.
No third-party Python packages required; ffmpeg decodes actual captured pixels.
"""
import json
import subprocess
import sys
from pathlib import Path

W, H = 854, 480


def inspect_frame(path):
    if not Path(path).is_file():
        return {"file": str(path), "pass": False, "reason": "missing screenshot"}
    cmd = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-i", str(path),
           "-vf", f"scale={W}:{H}", "-frames:v", "1", "-pix_fmt", "rgb24",
           "-f", "rawvideo", "-"]
    try:
        proc = subprocess.run(cmd, capture_output=True, timeout=25)
    except (OSError, subprocess.TimeoutExpired) as exc:
        return {"file": str(path), "pass": False, "reason": str(exc)}
    if proc.returncode or len(proc.stdout) != W * H * 3:
        return {"file": str(path), "pass": False, "reason": "invalid RGB screenshot"}
    blue, yellow = [], []
    data = proc.stdout
    for y in range(45, H - 45, 2):
        for x in range(75, W - 75, 2):
            off = (y * W + x) * 3
            r, g, b = data[off:off + 3]
            # Exclude bright blue sky; concrete has B<240.
            if (20 < r < 110 and 70 < g < 220 and 100 < b < 240
                    and b * 10 > g * 11 and g * 10 > r * 14):
                blue.append((x, y))
            # Exclude lava: yellow concrete has a strong green component.
            if (r > 110 and g > 95 and b < 145
                    and r * 100 > g * 105 and g * 10 > b * 15):
                yellow.append((x, y))
    nearby = False
    if len(blue) >= 20 and len(yellow) >= 8:
        blue_cells = {(x // 64, y // 64) for x, y in blue}
        yellow_cells = {(x // 64, y // 64) for x, y in yellow}
        nearby = any(abs(a - c) <= 2 and abs(b - d) <= 2
                     for a, b in blue_cells for c, d in yellow_cells)
    passed = len(blue) >= 20 and len(yellow) >= 8 and nearby
    return {"file": str(path), "pass": passed, "sampled_blue": len(blue),
            "sampled_yellow": len(yellow), "co_located": nearby}


def main(argv):
    if len(argv) != 4:
        print("usage: verify_ship_pixels.py first.jpg below.jpg returned.jpg",
              file=sys.stderr)
        return 2
    frames = [inspect_frame(p) for p in argv[1:]]
    print(json.dumps({
        "ship_pixel_candidates_in_all_stages": all(f["pass"] for f in frames),
        "frames": frames,
        "limitations": "candidate colors only; human MP4 review and native UUID/contact tests still required",
    }, indent=2))
    return 0 if all(f["pass"] for f in frames) else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))
