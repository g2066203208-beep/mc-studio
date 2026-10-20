#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
source_dir="$root/mod/src/main/java/dev/reme/mcstudio/npc/life"
test_dir="$root/mod/src/test/java/dev/reme/mcstudio/npc/life"
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT
javac -encoding UTF-8 -d "$out" "$source_dir/LivingPlanner.java" "$source_dir/ResidentMemory.java" "$test_dir/LivingPlannerSpec.java"
java -ea -cp "$out" dev.reme.mcstudio.npc.life.LivingPlannerSpec
