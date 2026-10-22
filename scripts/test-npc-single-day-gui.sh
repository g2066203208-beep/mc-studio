#!/usr/bin/env bash
# Real Minecraft 1.21.1 integrated-server / Xvfb observer:
# 24 screenshots at about 50-second wall-clock intervals, camera follows
# one named NPC; not a claim of exactly 1000 game ticks per screenshot.
set -euo pipefail
cd mod
openbox >/tmp/reme-daywatch-openbox.log 2>&1 &
./gradlew runClient --no-daemon >daywatch-client-boot.log 2>&1 &
CLIENT=$!
cleanup() {
  kill -INT "$CLIENT" 2>/dev/null || true
  sleep 2
  kill "$CLIENT" 2>/dev/null || true
}
trap cleanup EXIT
window=''
for i in $(seq 1 180); do
  if ! kill -0 "$CLIENT" 2>/dev/null; then
    tail -n 100 daywatch-client-boot.log
    echo 'FAIL: Minecraft terminated before rendering a window'; exit 1
  fi
  window=$(xdotool search --onlyvisible --name 'Minecraft' 2>/dev/null | head -1 || true)
  if [[ -n "$window" ]] && grep -q 'Reloading ResourceManager' daywatch-client-boot.log; then break; fi
  sleep 1
done
test -n "$window" || { echo 'FAIL: no real Minecraft window'; exit 1; }
xdotool windowactivate --sync "$window" || true
xdotool windowsize "$window" 1280 720 || true
xdotool windowmove "$window" 0 0 || true
sleep 12
import -window root daywatch-main-menu.png
# The same actual 1.21.1 GUI coordinates previously inspected in CI.
xdotool mousemove --sync 640 670 click 1
sleep 8
xdotool mousemove --sync 642 304 click 1
sleep 9
import -window root daywatch-world-creation.png
xdotool mousemove --sync 640 425 click 1
sleep 1
xdotool mousemove --sync 400 673 click 1
sleep 95
import -window root daywatch-entered-world.png

sendcmd() {
  xdotool key --clearmodifiers t
  sleep 0.5
  xdotool type --clearmodifiers --delay 14 "$1"
  xdotool key Return
  sleep 1.5
}
grep -q 'REME NPC: /npc present on dispatcher for player' run/logs/latest.log ||
  { echo 'FAIL: integrated real MC server never registered /npc'; exit 1; }
sendcmd '/remecheck'
import -window root daywatch-command-check.png
# All world modification is confined to the disposable GUI CI save.
sendcmd '/fill ~-12 ~ ~-12 ~12 ~5 ~12 minecraft:air'
sendcmd '/fill ~-12 ~-1 ~-12 ~12 ~-1 ~12 minecraft:stone'
sendcmd '/fill ~3 ~-1 ~-1 ~5 ~-1 ~1 minecraft:farmland[moisture=7]'
sendcmd '/fill ~3 ~ ~-1 ~5 ~ ~1 minecraft:wheat[age=7]'
sendcmd '/npc spawn DayWatchFarmer'
sendcmd '/npc farmer'
sendcmd '/time set day'
sendcmd '/weather clear'
sendcmd '/gamerule doDaylightCycle true'
sendcmd '/gamerule doMobSpawning false'
sendcmd '/gamemode spectator'
sendcmd '/execute if entity @e[type=reme_studio:player_npc,name=DayWatchFarmer,limit=1] run say REME_DAYWATCH_GUI_SUBJECT_EXISTS'
sendcmd '/time set 0'
grep -q 'REME_DAYWATCH_GUI_SUBJECT_EXISTS' run/logs/latest.log ||
  { echo 'FAIL: exact named NPC was not created'; exit 1; }
echo 'REME_DAYWATCH_GUI_BEGIN real Minecraft GUI camera tracking one NPC'

# Spectate command shows only first-person eyes and hides NPC model, so keep
# the HUMAN in spectator mode and teleport camera behind the SAME NPC.
for hour in $(seq -w 0 23); do
  if ! kill -0 "$CLIENT" 2>/dev/null; then
    echo "FAIL: client closed at observation $hour"; exit 1
  fi
  sendcmd '/execute at @e[type=reme_studio:player_npc,name=DayWatchFarmer,limit=1] run tp @s ~5 ~3 ~5 facing entity @e[type=reme_studio:player_npc,name=DayWatchFarmer,limit=1] eyes'
  sendcmd '/npc info'
  sendcmd '/time query daytime'
  sleep 1
  import -window root "daywatch-hour-${hour}.png"
  echo "REME_DAYWATCH_GUI_FRAME interval=${hour} image=daywatch-hour-${hour}.png"
  sleep 50
done
sendcmd '/execute if entity @e[type=reme_studio:player_npc,name=DayWatchFarmer,limit=1] run say REME_DAYWATCH_GUI_SAME_NPC_ALIVE'
sendcmd '/time query daytime'
sendcmd '/npc info'
import -window root daywatch-next-sunrise.png
grep -q 'REME_DAYWATCH_GUI_SAME_NPC_ALIVE' run/logs/latest.log ||
  { echo 'FAIL: same observed named NPC no longer exists'; exit 1; }
frames=$(find . -maxdepth 1 -name 'daywatch-hour-??.png' -type f | wc -l)
test "$frames" -eq 24 || { echo "FAIL: expected 24 genuine render frames, got $frames"; exit 1; }
echo "REME_DAYWATCH_GUI_RESULT namedNPC=DayWatchFarmer frames=$frames alive=true"
echo '24 ~50s camera samples; accelerated server GameTest separately verifies 24000 real world ticks.'
