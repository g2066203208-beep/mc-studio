#!/usr/bin/env python3
"""Offline regression for same-UUID real Minecraft GUI recording evidence."""
import importlib.util
import pathlib
import unittest

MODULE = pathlib.Path(__file__).with_name("verify-npc-gui-case.py")
spec = importlib.util.spec_from_file_location("npc_case", MODULE)
assert spec and spec.loader
case = importlib.util.module_from_spec(spec)
spec.loader.exec_module(case)

A = "3417971f-7d21-4b0f-9d86-559bc553b608"
B = "31bda1ac-a766-4f0e-8e36-c3c39c0f26aa"
GUARD = "3af65c6a-2722-4e67-8fb0-7dd9955643af"
MERCHANT = "1a4c691a-939c-4977-a986-fcb6fdb8574c"


def episode(who: str, start: int, complete: bool = True) -> list[str]:
    return [
        f"REME_NPC_STORY stage=unpaid_pickup tick={start} shop={MERCHANT} suspect={who} payment=0",
        f"[Client] 已连续跟踪真实NPC {who}：CHASE；镜头只移动玩家",
        f"REME_NPC_STORY stage=police_report tick={start+20} shop={MERCHANT} suspect={who} dispatched=1",
        f"REME_NPC_STORY stage=police_contact tick={start+30} guard={GUARD} suspect={who} blows=1 stunned=false",
        f"REME_NPC_STORY stage=police_contact tick={start+40} guard={GUARD} suspect={who} blows=2 stunned=false",
        f"REME_NPC_STORY stage=police_contact tick={start+50} guard={GUARD} suspect={who} blows=3 stunned=true",
        f"REME_NPC_STORY stage=post_stun_arrest tick={start+70} guard={GUARD} suspect={who} holding=-22, 65, 14",
    ] + ([
        # Both events may happen in one server tick; log ORDER still matters.
        f"REME_NPC_STORY stage=jail_reached tick={start+120} guard={GUARD} prisoner={who} cell=-22, 65, 14",
        f"REME_NPC_STORY stage=jail_gate_locked tick={start+120} location=-22, 65, 16",
    ] if complete else [])


def filmed(lines: list[str]) -> str:
    return "\n".join(["REME_NPC_CASE_VIDEO_START", *lines,
                      "REME_NPC_CASE_VIDEO_END"]) + "\n"


class GenuineGuiCaseRegression(unittest.TestCase):
    def test_one_real_complete_case_passes(self):
        self.assertIn("same_guard=true", case.verify(filmed(episode(A, 100))))

    def test_other_prisoner_must_not_rescue_filmed_failed_case(self):
        text = filmed(episode(A, 100, False) + episode(B, 500))
        with self.assertRaisesRegex(ValueError, "missing jail_reached"):
            case.verify(text)

    def test_other_incident_before_camera_does_not_poison_correct_case(self):
        text = filmed(episode(B, 100) + episode(A, 500))
        self.assertIn("thief=" + B, case.verify(text))

    def test_wrong_cell_gate_is_not_real_custody(self):
        lines = episode(A, 100)
        lines[-1] = lines[-1].replace("location=-22, 65, 16", "location=20, 65, 16")
        with self.assertRaisesRegex(ValueError, "never locked"):
            case.verify(filmed(lines))

    def test_three_melee_hits_cannot_be_fake_counter(self):
        lines = episode(A, 100)
        lines[5] = lines[5].replace("blows=3 stunned=true", "blows=3 stunned=false")
        with self.assertRaisesRegex(ValueError, "three REAL"):
            case.verify(filmed(lines))

    def test_arresting_guard_must_match_jail_officer(self):
        lines = episode(A, 100)
        lines[-2] = lines[-2].replace("guard=" + GUARD, "guard=" + B)
        with self.assertRaisesRegex(ValueError, "not the escorting"):
            case.verify(filmed(lines))

    def test_no_camera_is_not_proof_of_filming(self):
        lines = [line for line in episode(A, 100) if "已连续跟踪" not in line]
        with self.assertRaisesRegex(ValueError, "No actual first CHASE"):
            case.verify(filmed(lines))

    def test_camera_after_arrest_cannot_claim_pursuit(self):
        lines = episode(A, 100)
        camera = lines.pop(1)
        lines.insert(6, camera)
        with self.assertRaisesRegex(ValueError, "only AFTER"):
            case.verify(filmed(lines))

    def test_jail_arrival_after_mp4_end_is_not_filmed(self):
        lines = episode(A, 100)
        late_arrival = lines.pop(-2)
        with self.assertRaisesRegex(ValueError, "missing jail_reached"):
            case.verify(filmed(lines) + late_arrival + "\n")

    def test_lock_before_officer_handoff_is_rejected(self):
        lines = episode(A, 100)
        lines[-2], lines[-1] = lines[-1], lines[-2]
        with self.assertRaisesRegex(ValueError, "before officer acknowledged"):
            case.verify(filmed(lines))

    def test_jail_arrival_without_gate_lock_is_not_complete(self):
        lines = episode(A, 100)[:-1]
        with self.assertRaisesRegex(ValueError, "never locked after officer handoff"):
            case.verify(filmed(lines))

    def test_gate_lock_after_video_end_is_not_filmed(self):
        lines = episode(A, 100)
        late_lock = lines.pop()
        with self.assertRaisesRegex(ValueError, "never locked after officer handoff"):
            case.verify(filmed(lines) + late_lock + "\n")

    def test_same_tick_handoff_then_lock_is_valid(self):
        # Event order is authoritative when two events share a game tick.
        self.assertIn("locked_iron_gate=true", case.verify(filmed(episode(A, 100))))

    def test_missing_video_end_marker_is_not_proof(self):
        with self.assertRaisesRegex(ValueError, "video start/end"):
            case.verify(filmed(episode(A, 100)).replace(
                "REME_NPC_CASE_VIDEO_END", ""))


if __name__ == "__main__":
    unittest.main(verbosity=2)
