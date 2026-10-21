"""Run: python3 -m unittest discover -s skills/immersive-vehicles-modeling/tests -v"""
import importlib.util
import json
import struct
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("inspect_obj", ROOT / "scripts" / "inspect_obj.py")
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

GOOD_OBJ = """# original tiny test fixture, not copied from another pack
o body
v 0 0 0
v 1 0 0
v 0 1 0
vt 0 0
vt 1 0
vt 0 1
vn 0 0 1
f 1/1/1 2/2/1 3/3/1
"""
NEGATIVE_OBJ = """o body
v 0 0 0
v 1 0 0
v 0 1 0
vt 0 0
vt 1 0
vt 0 1
f -3/1 -2/2 -1/3
"""


class InspectorTests(unittest.TestCase):
    def test_obj_counts_bounds(self):
        result = mod.parse_obj(GOOD_OBJ)
        g = result["geometry"]
        self.assertEqual((g["vertices"], g["uvs"], g["faces"], g["object_count"]), (3, 3, 1, 1))
        self.assertEqual(g["triangulated_faces"], 1)
        self.assertEqual(g["bounds_xyz"], [[0, 1], [0, 1], [0, 0]])
        self.assertFalse(result["errors"])

    def test_negative_obj_indices(self):
        result = mod.parse_obj(NEGATIVE_OBJ)
        self.assertEqual(result["geometry"]["faces"], 1)
        self.assertFalse(result["errors"])

    def test_missing_uv_warns(self):
        r = mod.parse_obj("o x\nv 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n")
        self.assertEqual(r["geometry"]["faces_missing_uv"], 1)
        self.assertTrue(any("missing complete UV" in e for e in r["warnings"]))

    def test_out_of_range_fails(self):
        self.assertTrue(mod.parse_obj("v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 4\n")["errors"])

    def test_jsonc_and_reference_validation(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp)
            obj = p / "model.obj"
            doc = p / "vehicle.json"
            obj.write_text(GOOD_OBJ)
            doc.write_text('{"parts": [{"pos":[0,0,1], "types":["ground_wheel"]},], // comment\n'
                           '"rendering": {"animatedObjects": [{"objectName":"body"}, {"objectName":"missing"}]}}')
            result = mod.inspect(obj, definition=doc)
            self.assertFalse(result["errors"], result["errors"])
            self.assertEqual(result["definition"]["parts_count"], 1)
            self.assertEqual(result["definition"]["missing_animated_objects"], ["missing"])

    def test_jsonc_comment_in_string(self):
        r = json.loads(mod._clean_jsonc('{"url":"https://example.com//assets","ok":true, /*x*/ }'))
        self.assertEqual(r["url"], "https://example.com//assets")

    def test_png_dimensions(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp) / "atlas.png"
            p.write_bytes(b"\x89PNG\r\n\x1a\n" + struct.pack(">I", 13) + b"IHDR"
                          + struct.pack(">II", 512, 256))
            self.assertEqual(mod.png_dimensions(p), (512, 256))

    def test_nan_is_error(self):
        r = mod.parse_obj("v NaN 0 0\nv 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n")
        self.assertTrue(any("NaN/Inf" in e for e in r["errors"]))


if __name__ == "__main__":
    unittest.main()
