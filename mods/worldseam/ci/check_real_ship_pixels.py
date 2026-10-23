#!/usr/bin/env python3
"""Guard against certifying a REAL Minecraft MP4 that renders no aircraft.

CI invokes this ONLY after an actual same-UUID Sable native physics roundtrip
log has already passed. It reads three unmodified in-game X11 screenshots
captured AFTER native client-render readiness, during the lower crossing,
and after return. Without colored airplane voxels in the pixels, FAIL.

A colored region is a conservative visibility witness, NOT proof of correct
sublevel ownership, that Aeronautics power produced motion, or of true native
Rapier multi-chart collision. Those remain independent and unmet gates.
"""

import subprocess
import sys
from pathlib import Path

WIDTH, HEIGHT = 320, 180
MIN_SIGNATURE_PIXELS = 70
FRAMES = (
    "real-ship-client-visible.jpg",
    "real-ship-below-seam.jpg",
    "real-ship-returned.jpg",
)


def decode_rgb(file: Path) -> bytes:
    if not file.is_file() or file.stat().st_size == 0:
        raise RuntimeError(f"missing actual client screenshot: {file}")
    result = subprocess.run(
        [
            "ffmpeg", "-hide_banner", "-loglevel", "error",
            "-i", str(file), "-frames:v", "1", "-vf",
            f"scale={WIDTH}:{HEIGHT}", "-f", "rawvideo",
            "-pix_fmt", "rgb24", "pipe:1",
        ],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if len(result.stdout) != WIDTH * HEIGHT * 3:
        raise RuntimeError(f"wrong screenshot RGB frame length: {file}")
    return result.stdout


def colored_voxel_witness(rgb: bytes) -> int:
    hits = 0
    # The genuine fixture uses light_blue_concrete and yellow_concrete.
    # Exclude outside-the-world UI/borders and require actual pixel colors,
    # not only a logged Sable renderer object or successful FFmpeg encoding.
    for y in range(24, 157):
        for x in range(40, 280):
            i = (y * WIDTH + x) * 3
            red, green, blue = rgb[i:i + 3]
            cyan = (
                blue >= 92 and green >= 78 and red <= 150
                and blue >= red + 25 and green >= red + 15
            )
            yellow = (
                red >= 145 and green >= 90 and blue <= 112
                and red >= green + 18 and green >= blue + 22
            )
            if cyan or yellow:
                hits += 1
    return hits


def main() -> int:
    paths = tuple(Path(p) for p in (sys.argv[1:] or FRAMES))
    if len(paths) != 3:
        print("FAIL: require initial, below-seam and returned real screenshots",
              file=sys.stderr)
        return 2
    for path in paths:
        try:
            count = colored_voxel_witness(decode_rgb(path))
        except (OSError, RuntimeError, subprocess.CalledProcessError) as exc:
            print(f"WORLDSEAM_FILM_PIXEL_WITNESS_FAIL: {path}: {exc}",
                  file=sys.stderr)
            return 1
        print(
            f"WORLDSEAM_FILM_PIXEL_WITNESS: file={path} "
            f"cyanOrYellowVisiblePixels={count} "
            f"minimum={MIN_SIGNATURE_PIXELS}"
        )
        if count < MIN_SIGNATURE_PIXELS:
            print(
                "WORLDSEAM_FILM_PIXEL_WITNESS_FAIL: render object may exist "
                "but genuine aircraft is not visibly present in screenshot "
                f"{path}; do NOT certify the film.",
                file=sys.stderr,
            )
            return 1
    print(
        "WORLDSEAM_FILM_PIXEL_WITNESS_PASS: all three real camera frames "
        "contain recognizable blue/yellow aircraft color pixels; "
        "not a cross-chart physics or powered-flight certificate"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
