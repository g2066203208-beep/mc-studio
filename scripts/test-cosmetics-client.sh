#!/usr/bin/env bash
set -euo pipefail
cd mod
openbox >/tmp/reme-cosmetics-openbox.log 2>&1 &
./gradlew runClient --no-daemon > client-cosmetics.log 2>&1 &
CLIENT=$!
trap 'kill -INT "$CLIENT" 2>/dev/null || true; sleep 2; kill "$CLIENT" 2>/dev/null || true' EXIT
window=''
for i in $(seq 1 180); do
  if ! kill -0 "$CLIENT" 2>/dev/null; then
    echo "Minecraft dev client ended prematurely" >&2
    tail -n 120 client-cosmetics.log
    exit 1
  fi
  window=$(xdotool search --onlyvisible --name 'Minecraft' 2>/dev/null | head -1 || true)
  if [[ -n "$window" ]] && grep -q 'Reloading ResourceManager' client-cosmetics.log; then break; fi
  sleep 1
done
if [[ -z "$window" ]]; then echo "No visible Minecraft window" >&2; exit 1; fi
xdotool windowactivate --sync "$window" || true
xdotool windowsize "$window" 1280 720 || true
xdotool windowmove "$window" 0 0 || true
sleep 9
import -window root client-cosmetics-start.png
# Continue first-launch accessibility if present (previous verified screenshot).
xdotool mousemove --sync 640 670 click 1
sleep 8
import -window root client-cosmetics-title.png
# Click Mods in actual NeoForge main menu.
xdotool mousemove --sync 640 520 click 1
sleep 7
import -window root client-cosmetics-mods.png
echo "Graphical Mods page captured; review screenshot and actual log, not only process exit."
# Don't claim both installed until both mod IDs appear in actual client resources/log.
for token in skinlayers3d eyemod; do
  if ! grep -Ei "$token" client-cosmetics.log run/logs/latest.log 2>/dev/null; then
    echo "ERROR: missing $token in actual client log" >&2
    exit 1
  fi
done
echo "BOTH_VISUAL_MOD_IDS_FOUND_IN_CLIENT_LOG"
