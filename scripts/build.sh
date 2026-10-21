#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"
python3 scripts/check_peak_animations.py
cd "$root/mod"
bash ./gradlew build --no-daemon --console=plain
sha256sum build/libs/*.jar
