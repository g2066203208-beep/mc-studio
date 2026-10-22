#!/usr/bin/env python3
"""Generate a safe, spacious EMPTY Minecraft GameTest structure for CI.

The original empty.nbt is 5x4x5, whereas individual actual-world GameTests
place 21x21 patrol arenas and walk NPCs between warehouses beyond that edge.
Minecraft's GameTest framework allocates positions by structure dimensions:
overlapping out-of-bounds fixtures can erase each other's blocks and create
random false failures (mining target vanished, no sky, guard walking off floor).

This changes ONLY the test template, not the game world or NPC physics. All
original assertions remain unchanged. Structure NBT keeps an empty block
palette, so the world fixtures are still laid by real Minecraft commands.
"""
import gzip
import pathlib
import struct

PROJECT = pathlib.Path(__file__).resolve().parents[1]
STRUCTURE = PROJECT / "mod/src/main/resources/data/reme_studio/structure/empty.nbt"
# 48x24x48 gives all currently used 21x21 patrol and 16x16 social
# fixtures room, and ample gap between separate GameTest structures.
SIZE = (48, 24, 48)
PATTERN = b"\x09\x00\x04size\x03\x00\x00\x00\x03"

def main():
    before = STRUCTURE.read_bytes()
    uncompressed = gzip.decompress(before)
    pos = uncompressed.find(PATTERN)
    if pos < 0 or uncompressed.count(PATTERN) != 1:
        raise SystemExit("Refusing to modify unknown GameTest NBT tag format")
    offset = pos + len(PATTERN)
    old = struct.unpack_from(">iii", uncompressed, offset)
    if old not in ((5, 4, 5), SIZE):
        raise SystemExit(f"Unexpected empty test structure size: {old}")
    changed = bytearray(uncompressed)
    struct.pack_into(">iii", changed, offset, *SIZE)
    if changed.count(b"\x09\x00\x06blocks\x0a\x00\x00\x00\x00") != 1:
        raise SystemExit("Refusing to change a nonempty block fixture")
    STRUCTURE.write_bytes(gzip.compress(changed, mtime=0))
    verify = gzip.decompress(STRUCTURE.read_bytes())
    assert struct.unpack_from(">iii", verify, offset) == SIZE
    print(f"Prepared isolated GameTest world template {old} -> {SIZE}: {STRUCTURE}")
    print("Real NeoForge paths, vanilla player actions and checks remain unmodified")

if __name__ == "__main__":
    main()
