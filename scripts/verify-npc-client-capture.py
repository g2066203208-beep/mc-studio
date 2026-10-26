#!/usr/bin/env python3
"""Verify *actual* Minecraft client video artifacts, not a successful Gradle build.

This complements verify-npc-gui-case.py's same-UUID crime chronology.
A successful CI job must contain two original decodable, non-frozen,
continuous 1280x720 H.264 recordings plus one contiguous HUD-free cut
of the original crime recording and genuine client RGB screenshots. It must
also have no PlayerAnimator "wrong directory" runtime warnings.
"""
import argparse
import json
from pathlib import Path
import re
import struct
import subprocess
import sys
import unittest

VIDEO_CONTRACT = {
    # Completion is now tied to same-UUID case events inside recording
    # markers, not a padded 90s wall-clock minimum. The real crime-to-cell
    # sequence can complete sooner; preserve enough continuous frames.
    "client-gui-city-full-crime-to-locked-jail.mp4": 35.0,
    # Derived by trimming consecutive genuine frames AFTER F1; the uncut
    # source remains the authoritative same-UUID chronology evidence.
    "client-gui-city-clean-chase-to-cell.mp4": 15.0,
    "client-gui-city-physical-npc-follow.mp4": 45.0,
}
SCREENSHOTS = (
    "client-gui-city-full-case-t0.png",
    "client-gui-city-full-case-t3.png",
    "client-gui-city-full-case-t5.png",
    "client-gui-city-shoplifter-t0.png",
    "client-gui-city-shoplifter-t2.png",
    "client-gui-city-guard-native-possession.png",
)
MD5_LINE = re.compile(r",\s*([0-9a-f]{32})\s*$")
BAD_ANIMATION_PATH = re.compile(
    r"Animation\s+\S+\s+is in wrong directory", re.IGNORECASE
)


def command(*args):
    result = subprocess.run(
        args, check=False, capture_output=True, text=True, timeout=120
    )
    if result.returncode:
        raise ValueError(
            f"{args[0]} exited {result.returncode}: {result.stderr[-1200:]}"
        )
    return result.stdout


def parse_video_info(raw):
    payload = json.loads(raw)
    streams = payload.get("streams", [])
    if len(streams) != 1:
        raise ValueError(f"Expected exactly one video stream: {streams!r}")
    video = streams[0]
    if (video.get("codec_name"), video.get("width"), video.get("height")) != (
        "h264", 1280, 720
    ):
        raise ValueError(f"Wrong video codec or geometry: {video!r}")
    duration = float(payload.get("format", {}).get("duration", 0))
    return duration


def frame_hashes(raw):
    return {
        match.group(1)
        for line in raw.splitlines()
        if (match := MD5_LINE.search(line))
    }


def check_video(path, min_duration):
    if not path.is_file() or path.stat().st_size < 100_000:
        raise ValueError(f"Missing or empty original MP4: {path}")
    duration = parse_video_info(command(
        "ffprobe", "-v", "error", "-select_streams", "v:0",
        "-show_entries", "stream=codec_name,width,height",
        "-show_entries", "format=duration", "-of", "json", str(path)
    ))
    if duration < min_duration:
        raise ValueError(
            f"Truncated recording {path.name}: {duration:.2f}s < {min_duration:.2f}s"
        )
    # Full stream decoding detects broken or prematurely truncated MP4s.
    command("ffmpeg", "-v", "error", "-xerror", "-i", str(path), "-f", "null", "-")
    hashes = frame_hashes(command(
        "ffmpeg", "-v", "error", "-xerror", "-i", str(path),
        "-vf", "fps=1,scale=64:36,format=gray", "-f", "framemd5", "-"
    ))
    if len(hashes) < 8:
        raise ValueError(
            f"Frozen/static recording {path.name}: only {len(hashes)} unique seconds"
        )
    print(
        f"NPC_REAL_VIDEO_OK file={path.name} duration={duration:.2f}s "
        f"resolution=1280x720 unique_second_frames={len(hashes)}"
    )


def png_size(path):
    with path.open("rb") as stream:
        header = stream.read(24)
    if len(header) < 24 or header[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"Not an actual PNG: {path}")
    return struct.unpack(">II", header[16:24])


def verify(root):
    log = root / "client-gui.log"
    if not log.is_file() or log.stat().st_size < 10_000:
        raise ValueError("Missing original real-client stdout/stderr log")
    content = log.read_text(encoding="utf-8", errors="replace")
    if BAD_ANIMATION_PATH.search(content):
        raise ValueError("Real client still reports wrong PlayerAnimator resource directory")
    # This proves absence of the old warning, not visual animation quality.
    for name, min_duration in VIDEO_CONTRACT.items():
        check_video(root / name, min_duration)
    for name in SCREENSHOTS:
        path = root / name
        if not path.is_file() or path.stat().st_size < 25_000:
            raise ValueError(f"Missing real GUI RGB screenshot: {path}")
        if png_size(path) != (1280, 720):
            raise ValueError(f"Wrong native screenshot dimensions: {path}")
    print(
        f"NPC_REAL_CLIENT_CAPTURE_OK videos={len(VIDEO_CONTRACT)} "
        f"screenshots={len(SCREENSHOTS)} "
        "animation_wrong_directory_warnings=0"
    )


class ContractTests(unittest.TestCase):
    def test_accepts_valid_video_info(self):
        raw = json.dumps({
            "streams": [{"codec_name": "h264", "width": 1280, "height": 720}],
            "format": {"duration": "101.5"}
        })
        self.assertEqual(parse_video_info(raw), 101.5)

    def test_rejects_wrong_codec(self):
        raw = json.dumps({
            "streams": [{"codec_name": "mpeg4", "width": 1280, "height": 720}],
            "format": {"duration": "101.5"}
        })
        with self.assertRaises(ValueError):
            parse_video_info(raw)

    def test_rejects_no_video_stream(self):
        with self.assertRaises(ValueError):
            parse_video_info('{"streams": [], "format": {"duration": "100"}}')

    def test_distinguishes_frozen_and_moving_frames(self):
        self.assertEqual(len(frame_hashes("0, 0, 0, 1, 4, " + "a"*32 + "\n"*3)), 1)
        self.assertEqual(len(frame_hashes(
            "0, 0, 0, 1, 4, " + "a"*32 + "\n"
            + "0, 1, 1, 1, 4, " + "b"*32 + "\n"
        )), 2)

    def test_rejects_runtime_wrong_directory_warning(self):
        self.assertIsNotNone(BAD_ANIMATION_PATH.search(
            'Animation player_animation/actor_escort.json is in wrong directory: '
            '"player_animation", please place it in "player_animations".'
        ))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path("mod"))
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        result = unittest.main(argv=[sys.argv[0]], exit=False, verbosity=2).result
        if not result.wasSuccessful():
            raise SystemExit(1)
    else:
        try:
            verify(args.root)
        except (OSError, ValueError, subprocess.TimeoutExpired) as exc:
            raise SystemExit(f"NPC_REAL_CLIENT_CAPTURE_FAIL: {exc}")
