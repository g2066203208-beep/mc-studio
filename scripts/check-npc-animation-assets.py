#!/usr/bin/env python3
"""Fail CI if shared player/NPC PlayerAnimator clips cannot be discovered."""
import json
import re
from pathlib import Path

root = Path(__file__).resolve().parents[1]
assets = root / "mod/src/main/resources/assets/reme_studio"
legacy = assets / "player_animation"
active = assets / "player_animations"
contract = root / "mod/src/main/java/dev/reme/mcstudio/npc/SharedHumanoidMotion.java"

source = contract.read_text(encoding="utf-8")
match = re.search(
    r"public static boolean authoredClip\(NpcPose pose\).*?case\s+(.*?)\s*->\s*true;",
    source,
    re.DOTALL,
)
if match is None:
    raise SystemExit("NPC_ANIMATION_CONTRACT_MISSING: cannot parse authoredClip")

poses = {name.strip() for name in match.group(1).split(",")}
if not poses or any(re.fullmatch(r"[A-Z][A-Z_]*", name) is None for name in poses):
    raise SystemExit("NPC_ANIMATION_CONTRACT_INVALID: " + repr(sorted(poses)))

expected = {"actor_" + name.lower() + ".json" for name in poses}
actual = {path.name for path in active.glob("*.json")} if active.is_dir() else set()
old = sorted(path.name for path in legacy.glob("*.json")) if legacy.is_dir() else []
if old:
    raise SystemExit("NPC_ANIMATION_LEGACY_DIRECTORY: " + ", ".join(old))
if actual != expected:
    raise SystemExit(
        "NPC_ANIMATION_MISMATCH missing="
        + repr(sorted(expected - actual))
        + " unexpected="
        + repr(sorted(actual - expected))
    )

for name in sorted(expected):
    data = json.loads((active / name).read_text(encoding="utf-8"))
    emote = data.get("emote", {})
    if data.get("name") != name.removesuffix(".json"):
        raise SystemExit("NPC_ANIMATION_BAD_NAME: " + name)
    if not isinstance(emote.get("moves"), list) or not emote["moves"]:
        raise SystemExit("NPC_ANIMATION_MISSING_KEYFRAMES: " + name)

print(f"NPC_PLAYERANIMATOR_ASSETS_OK count={len(expected)} directory=player_animations")
