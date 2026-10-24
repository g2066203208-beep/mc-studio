#!/usr/bin/env python3
"""Verify ONE real filmed suspect's complete evidence-backed Minecraft case.

Never mix first thief's theft with a later, different thief's jail arrival.
The first CHASE camera is the actor actually filmed in the original MP4.
This checks native integrated-server stdout, not GameTest fixture output.
"""
from __future__ import annotations

import pathlib
import re
import sys

UUID = r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
STAGES = ("unpaid_pickup", "police_report", "post_stun_arrest", "jail_reached")


def fail(message: str) -> None:
    raise ValueError(message)


def key(line: str, field: str) -> str | None:
    m = re.search(r"\b" + re.escape(field) + r"=([^\s]+)", line)
    return m.group(1) if m else None


def xyz(line: str, field: str) -> tuple[int, int, int] | None:
    m = re.search(r"\b" + re.escape(field) + r"=(-?\d+),\s*(-?\d+),\s*(-?\d+)", line)
    return tuple(map(int, m.groups())) if m else None


def verify(text: str) -> str:
    camera = re.search(r"已连续跟踪真实NPC (" + UUID + r")：CHASE", text)
    if camera is None:
        fail("No actual first CHASE camera attachment to a real UUID")
    # The previous verifier read the ENTIRE client session. It could accept
    # a cell arrival AFTER ffmpeg had already stopped, falsely labeling a
    # shop-counter-only MP4 as a full crime-to-cell recording.
    start = re.search(r"REME_NPC_CASE_VIDEO_START", text)
    end = re.search(r"REME_NPC_CASE_VIDEO_END", text)
    if start is None or end is None or start.start() >= end.start():
        fail("No ordered real Minecraft video start/end markers in client log")
    if not (start.start() < camera.start() < end.start()):
        fail("First CHASE camera was not attached during the recorded video")
    target = camera.group(1)
    lines = list(re.finditer(r"^.*$", text, re.MULTILINE))
    events: dict[str, list[re.Match[str]]] = {}
    for m in lines:
        # Ignore crimes from before recording or after ffmpeg stopped.
        if not (start.start() < m.start() < end.start()):
            continue
        match = re.search(r"REME_NPC_STORY stage=([a-z_]+)", m.group())
        if match:
            events.setdefault(match.group(1), []).append(m)

    def actor(m: re.Match[str]) -> str | None:
        return key(m.group(), "suspect") or key(m.group(), "prisoner")

    def next_stage(stage: str, after: int = -1) -> re.Match[str]:
        for m in events.get(stage, ()):
            if m.start() > after and actor(m) == target:
                return m
        other = [actor(m) for m in events.get(stage, ()) if actor(m) != target]
        fail("Filmed suspect " + target + " missing " + stage
             + " (other suspects observed: " + str(other[:5]) + ")")

    pickup = next_stage("unpaid_pickup")
    report = next_stage("police_report", pickup.start())
    if key(pickup.group(), "shop") != key(report.group(), "shop"):
        fail("Different merchant reported the filmed suspect's unpaid goods")
    arrest = next_stage("post_stun_arrest", report.start())
    jail = next_stage("jail_reached", arrest.start())
    if camera.start() > arrest.start():
        fail("Actual chase camera started only AFTER this suspect's arrest")
    if key(arrest.group(), "guard") != key(jail.group(), "guard"):
        fail("Guard that booked the filmed suspect is not the escorting officer")

    hits = [m for m in events.get("police_contact", ())
            if report.start() < m.start() < arrest.start()
            and actor(m) == target
            and key(m.group(), "guard") == key(arrest.group(), "guard")]
    if len(hits) < 3 or not any(
        re.search(r"\bblows=3\s+stunned=true\b", m.group()) for m in hits
    ):
        fail("Filmed suspect lacks three REAL successful guard melee hits and stun")

    holding = xyz(arrest.group(), "holding")
    cell = xyz(jail.group(), "cell")
    if holding is None or cell != holding:
        fail("Filmed prisoner never reached his originally assigned real cell")
    locked_at = (holding[0], holding[1], holding[2] + 2)
    locked = [m for m in events.get("jail_gate_locked", ())
              if arrest.start() < m.start() < jail.start()
              and xyz(m.group(), "location") == locked_at]
    if not locked:
        fail("Real iron door at this prisoner's SOUTH cell gate never locked")
    ticks = [key(m.group(), "tick") for m in (pickup, report, arrest, jail)]
    return ("REME_NPC_REAL_CLIENT_FULL_CASE_RECORDING_OK thief=" + target
            + " ticks=" + ",".join(str(t) for t in ticks)
            + " three_real_hits=true same_guard=true actual_cell=true"
            + " locked_iron_gate=true camera_before_arrest=true")


def main() -> int:
    log = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "mod/client-gui.log")
    try:
        print(verify(log.read_text(encoding="utf-8", errors="replace")))
    except (ValueError, OSError) as exc:
        print("FAIL: real Minecraft filmed incident " + str(exc), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
