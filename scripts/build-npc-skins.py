#!/usr/bin/env python3
"""Fetch author's MIT-licensed ORIGINAL skins and modernize Java skin UV.
All imports are pinned. No third-party unlicensed SkinDex/NameMC scrape.
Run manually via workflow; resulting PNGs MUST be versioned in Git.
"""
from pathlib import Path
import colorsys
import io
import struct
import urllib.request
from PIL import Image, ImageOps

SOURCE_COMMIT = "957f54e0e6c3c154ba083818305c13e6680030fb"
SRC = "https://raw.githubusercontent.com/raws/minecraft-skins/" + SOURCE_COMMIT + "/"
OUT = Path(__file__).resolve().parents[1] / "mod/src/main/resources/assets/reme_studio/textures/entity/npc"
SKINS = {
    "green_farmer": ("casual.png", 0),
    "field_explorer": ("indy.png", 0),
    "formal_trader": ("tuxedo.png", 0),
    "blue_farmer": ("casual.png", 1),
    "teal_explorer": ("indy.png", 1),
    "brown_trader": ("tuxedo.png", 1),
}

def png_legacy_to_modern(data):
    image = Image.open(io.BytesIO(data)).convert("RGBA")
    if image.size != (64, 32):
        raise ValueError("Unexpected original skin size (expected 64x32): " + str(image.size))
    atlas = Image.new("RGBA", (64, 64))
    atlas.paste(image, (0, 0))
    # Java 1.8+ left leg (16..31,48..63) / left arm (32..47,48..63).
    # Legacy sheets have only right arm and right leg; mirrored limb atlas
    # creates correct separate limb UVs instead of blank Steve-colored arms.
    atlas.paste(ImageOps.mirror(image.crop((0, 16, 16, 32))), (16, 48))
    atlas.paste(ImageOps.mirror(image.crop((40, 16, 56, 32))), (32, 48))
    return atlas

def recolor_outfit(image, style):
    if style == 0:
        return image
    out = image.copy()
    px = out.load()
    # Only clothing areas are recolored, leaving head/hair/skin colors.
    for x1, y1, x2, y2 in ((16,16,40,32),(40,16,56,32),(0,16,16,32),
                            (16,48,32,64),(32,48,48,64)):
        for y in range(y1, y2):
            for x in range(x1,x2):
                r,g,b,a = px[x,y]
                if a < 30:
                    continue
                h,s,v = colorsys.rgb_to_hsv(r/255, g/255, b/255)
                # Human skin or skin-colored pixels on hands/arms are not replaced.
                skin_like = 0.01 < h < 0.13 and 0.12 < s < 0.65 and v > 0.35
                if skin_like:
                    continue
                if style == 1:
                    # Keep white shirt, shirt cuffs and exposed skin untouched.
                    if "trader" in CURRENT:
                        if s < .10 and v > .56:
                            continue
                        # Transform near-black formal jacket into a textured
                        # warm-brown wool suit, with a visible (not black) shade.
                        if v < .56:
                            new_h, new_s = .085, .52
                            new_v = min(.48, max(.23, v * 1.27 + .16))
                            rr,gg,bb=colorsys.hsv_to_rgb(new_h,new_s,new_v)
                            px[x,y]=(round(rr*255),round(gg*255),round(bb*255),a)
                    elif "farmer" in CURRENT:
                        # Only re-dye saturated green shirt pixels, never hands.
                        if .16 < h < .47 and s > .20 and v > .16:
                            rr,gg,bb=colorsys.hsv_to_rgb(.59,max(.4,s),v)
                            px[x,y]=(round(rr*255),round(gg*255),round(bb*255),a)
                    elif "explorer" in CURRENT:
                        if s < .10 or v < .14: continue
                        rr,gg,bb=colorsys.hsv_to_rgb(.48,max(.25,min(.7,s)),v)
                        px[x,y]=(round(rr*255),round(gg*255),round(bb*255),a)
    return out

def validate_png(path):
    b = path.read_bytes()
    if not b.startswith(b"\x89PNG\r\n\x1a\n"):
        raise AssertionError("Invalid PNG: "+str(path))
    if struct.unpack(">II",b[16:24]) != (64,64):
        raise AssertionError("Not 64x64: "+str(path))
    with Image.open(path) as im:
        im.verify()

if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    cache = {}
    for name, (src, style) in SKINS.items():
        if src not in cache:
            req = urllib.request.Request(SRC + src, headers={"User-Agent":"REME-Mod-Licensed-Skin-Importer/1.0"})
            with urllib.request.urlopen(req, timeout=25) as resp:
                b = resp.read(1_000_000)
            if len(b) > 999_999 or len(b) < 500:
                raise ValueError("Unexpected source size: " + src)
            cache[src] = png_legacy_to_modern(b)
        CURRENT = name
        transformed = recolor_outfit(cache[src], style)
        dest = OUT / (name + ".png")
        transformed.save(dest, optimize=True)
        validate_png(dest)
        print(f"OK {name} {dest.stat().st_size} bytes")
    print("Created",len(SKINS),"bundled skin files from MIT-licensed Ross Paffett textures.")
