#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
source_dir="$root/mod/src/main/java/dev/reme/mcstudio/npc/life"
test_dir="$root/scripts/living-tests"
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT
javac -encoding UTF-8 -d "$out" "$source_dir/LivingPlanner.java" "$source_dir/ResidentMemory.java" "$test_dir/LivingPlannerSpec.java"
java -ea -cp "$out" dev.reme.mcstudio.npc.life.LivingPlannerSpec
javac -encoding UTF-8 -d "$out" "$source_dir/SocietyRules.java" "$source_dir/LifeJourney.java" "$test_dir/SocietySpec.java"
java -ea -cp "$out" dev.reme.mcstudio.npc.life.SocietySpec
javac -encoding UTF-8 -d "$out" "$source_dir/CivicMind.java" "$source_dir/CharacterProfile.java" "$source_dir/Commission.java" "$test_dir/CivicSpec.java"
java -ea -cp "$out" dev.reme.mcstudio.npc.life.CivicSpec
javac -encoding UTF-8 -d "$out" "$source_dir/MovementPolicy.java" "$test_dir/MovementPolicySpec.java"
java -ea -cp "$out" dev.reme.mcstudio.npc.life.MovementPolicySpec
