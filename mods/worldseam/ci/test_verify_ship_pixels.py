#!/usr/bin/env python3
"""Unit tests for the *detector*, not evidence of Minecraft flight.

Generated PPM swatches are synthetic test inputs and MUST NEVER be used as
Minecraft screenshots, movie frames, or gameplay evidence.
"""
import tempfile
import unittest
from pathlib import Path

from verify_ship_pixels import inspect_frame


def ppm(path, blue=False, yellow=False, sky=False, lava=False):
    width, height = 854, 480
    image = bytearray([45, 45, 45] * width * height)

    def paint(x1, y1, x2, y2, rgb):
        for y in range(y1, y2):
            for x in range(x1, x2):
                at = (y * width + x) * 3
                image[at:at + 3] = bytes(rgb)

    if blue:
        paint(300, 190, 390, 245, (44, 132, 194))
    if yellow:
        paint(390, 195, 430, 240, (220, 195, 54))
    if sky:
        paint(300, 190, 390, 245, (125, 173, 255))
    if lava:
        paint(390, 195, 430, 240, (230, 86, 17))
    path.write_bytes(f"P6\n{width} {height}\n255\n".encode() + image)


class VisibilityVerifierTest(unittest.TestCase):
    def test_synthetic_palette_candidate(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "synthetic-palette.ppm"
            ppm(path, blue=True, yellow=True)
            self.assertTrue(inspect_frame(path)["pass"])

    def test_sky_and_lava_do_not_count_as_aircraft(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "synthetic-sky-lava.ppm"
            ppm(path, sky=True, lava=True)
            self.assertFalse(inspect_frame(path)["pass"])

    def test_single_color_does_not_count_as_aircraft(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "synthetic-only-blue.ppm"
            ppm(path, blue=True)
            self.assertFalse(inspect_frame(path)["pass"])

    def test_missing_screenshot_fails_closed(self):
        self.assertFalse(inspect_frame("/no-such-real-ship-frame.jpg")["pass"])


if __name__ == "__main__":
    unittest.main()
