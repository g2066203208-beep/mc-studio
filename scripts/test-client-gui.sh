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
echo "Saved real client first visible screen"
# Minecraft first-run accessibility wizard blocks single-player until Continue.
# This is the actual GUI screenshot confirmed from the prior workflow.
xdotool mousemove --sync 640 670 click 1
sleep 8
import -window root client-gui-after-continue.png
echo "Saved visible menu after first-run Continue"
# Verified title screen pixel position in preceding real-client screenshot.
xdotool mousemove --sync 642 304 click 1
sleep 9
import -window root client-gui-world-list.png
echo "Saved actual Singleplayer world creation screen"
# Inspected the actual 1.21.1 GUI screenshot: creation form, cheats OFF at x640,y425.
xdotool mousemove --sync 640 425 click 1
sleep 1
import -window root client-gui-commands-on.png
# Inspected Create New World button at x400,y672.
xdotool mousemove --sync 400 673 click 1
echo "Clicked Create New World with commands enabled"
sleep 95
import -window root client-gui-world.png
echo "Captured in-world attempt (must inspect picture; not automatically proof of world entry)"
xdotool key --clearmodifiers t
sleep 1
xdotool type --clearmodifiers --delay 80 '/remecheck'
xdotool key Return
sleep 4
import -window root client-gui-remecheck.png
xdotool key --clearmodifiers t
sleep 1
xdotool type --clearmodifiers --delay 80 '/npc spawn'
xdotool key Return
sleep 10
import -window root client-gui-npc-spawn.png
xdotool key --clearmodifiers t
sleep 1
xdotool type --clearmodifiers --delay 80 '/npc info'
xdotool key Return
sleep 4
import -window root client-gui-npc-info.png
echo "Captured real client command attempts for visual inspection"
xdotool key --clearmodifiers t
sleep 1
xdotool type --clearmodifiers --delay 75 '/npc demo'
xdotool key Return
sleep 20
import -window root client-gui-six-residents.png
xdotool key --clearmodifiers t
sleep 1
xdotool type --clearmodifiers --delay 75 '/npc info'
xdotool key Return
sleep 3
import -window root client-gui-six-status.png
xdotool key --clearmodifiers t
sleep 1
xdotool type --clearmodifiers --delay 75 '/npc skins'
xdotool key Return
sleep 2
import -window root client-gui-skin-list.png
xdotool key --clearmodifiers t
sleep 1
xdotool type --clearmodifiers --delay 75 '/npc skin 3'
xdotool key Return
sleep 7
import -window root client-gui-skin-changed.png
# Observe day/night transition on the same real NPCs, without assuming
# that this proves POI/bed/farming systems are finished.
xdotool key --clearmodifiers t
sleep 1
xdotool type --clearmodifiers --delay 75 '/time set night'
xdotool key Return
sleep 15
import -window root client-gui-night.png
echo "Captured six NPC demo and night-cycle screen evidence"
echo "Window id: $found"
echo "Minecraft launch/render indicators:"
grep -E 'Reloading ResourceManager|mod/reme_studio|Created.*atlas' client-gui.log | tail -n 10 || true
# For now don't guess UI locations without inspecting menu screenshot.
# This workflow's successful completion DOES NOT certify we entered a world.
