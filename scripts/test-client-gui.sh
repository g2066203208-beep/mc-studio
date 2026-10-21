#!/usr/bin/env bash
# Startup smoke + image evidence. Never claim in-world validation without logs/images.
set -euo pipefail
cd mod
openbox >/tmp/reme-openbox.log 2>&1 &
./gradlew runClient --no-daemon > client-gui.log 2>&1 &
CLIENT=$!
cleanup() {
  kill -INT "$CLIENT" 2>/dev/null || true
  sleep 2
  kill "$CLIENT" 2>/dev/null || true
}
trap cleanup EXIT
found=''
for i in $(seq 1 180); do
  if ! kill -0 "$CLIENT" 2>/dev/null; then
    echo "Minecraft launcher exited before opening game window"
    tail -n 100 client-gui.log
    exit 1
  fi
  found=$(xdotool search --onlyvisible --name 'Minecraft' 2>/dev/null | head -1 || true)
  if [[ -n "$found" ]] && grep -q 'Reloading ResourceManager' client-gui.log; then break; fi
  sleep 1
done
if [[ -z "$found" ]]; then
  echo "No Minecraft X window ever appeared"
  tail -n 100 client-gui.log
  exit 1
fi
xdotool windowactivate --sync "$found" || true
xdotool windowsize "$found" 1280 720 || true
xdotool windowmove "$found" 0 0 || true
sleep 12
import -window root client-gui-menu.png
echo "Saved REAL CLIENT visible main menu screenshot"
echo "Window id: $found"
echo "Minecraft launch/render indicators:"
grep -E 'Reloading ResourceManager|mod/reme_studio|Created.*atlas' client-gui.log | tail -n 10 || true
# For now don't guess UI locations without inspecting menu screenshot.
# This workflow's successful completion DOES NOT certify we entered a world.
