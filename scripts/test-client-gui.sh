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
# Independently verify that the newly registered NPC egg is a REAL item in
# the integrated server's registry, not just listed in source/packaging.
xdotool key --clearmodifiers t
sleep 1
xdotool type --clearmodifiers --delay 55 '/item replace entity @s weapon.mainhand with reme_studio:npc_spawn_egg'
xdotool key Return
sleep 3
import -window root client-gui-egg-in-hand.png
xdotool key --clearmodifiers t
sleep 1
xdotool type --clearmodifiers --delay 45 '/execute if items entity @s weapon.mainhand reme_studio:npc_spawn_egg run say REME_NPC_EGG_REGISTERED'
xdotool key Return
sleep 2
import -window root client-gui-egg-validated.png
xdotool key --clearmodifiers t
sleep 1
xdotool type --clearmodifiers --delay 50 '/clear @s reme_studio:npc_spawn_egg'
xdotool key Return
sleep 2
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

# Dedicated real-client acceptance: this mode explicitly stops after genuine
# command registration, egg registry and /npc spawn are observed. The old
# comprehensive screenshot/mining scenario occasionally failed due to a
# separate coordinate fixture and masked an already-working command test.
if [[ "${REME_COMMAND_SMOKE_ONLY:-0}" == "1" ]]; then
  log=run/logs/latest.log
  if ! grep -q 'REME NPC 0.6.7-dev COMMANDS REGISTERED: /npc=true, /remecheck=true' "$log"; then
    echo "FAIL: 21.1.262 server never actually registered both commands"; exit 1
  fi
  if ! grep -q '/npc registered=true' "$log"; then
    echo "FAIL: actual joined client did not execute /remecheck"; exit 1
  fi
  if ! grep -q 'REME_NPC_EGG_REGISTERED' "$log"; then
    echo "FAIL: actual integrated world did not resolve the NPC spawn egg"; exit 1
  fi
  if ! grep -q 'Created player NPC: NPC' "$log"; then
    echo "FAIL: real /npc spawn failed in integrated world"; exit 1
  fi
  echo "PASS: REAL MC 1.21.1 / NeoForge 21.1.262 integrated client loaded mod, registered /npc, successfully ran /remecheck and /npc spawn, and resolved real spawn egg"
  exit 0
fi

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
# New v0.5.9 world-level evidence: exactly the real six resident homes
# form a recognized three-home hamlet without pre-generated buildings.
xdotool key --clearmodifiers t
sleep 1
xdotool type --clearmodifiers --delay 55 '/npc settlement'
xdotool key Return
sleep 3
import -window root client-gui-organic-hamlet.png
xdotool key --clearmodifiers t
sleep 1
xdotool type --clearmodifiers --delay 55 '/npc trails'
xdotool key Return
sleep 3
import -window root client-gui-traffic-status.png
# Turn world-changing ambient trail formation on only in this disposable
# test world; it remains OFF by default for real player saves.
xdotool key --clearmodifiers t
sleep 1
xdotool type --clearmodifiers --delay 55 '/npc trails on'
xdotool key Return
sleep 3
import -window root client-gui-trails-enabled.png
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
# Extra P1 player-equivalent mining witness: real Minecraft commands in
# a disposable world, not a synthetic picture. Capture before and after,
# including chat confirmations and any errors. The world remains isolated.
xdotool key --clearmodifiers t
sleep 1
xdotool type --clearmodifiers --delay 45 '/time set day'
xdotool key Return
sleep 3
# After documenting the village screenshots, clear other NPCs in this
# disposable world so nearest-owned command cannot select the wrong NPC.
xdotool key --clearmodifiers t
sleep 1
xdotool type --clearmodifiers --delay 35 '/kill @e[type=reme_studio:player_npc]'
xdotool key Return
sleep 3
# Isolate the observer AND miner. Earlier random test worlds had a hillside
# at z-6. Teleporting Dev into it caused an ACTUAL "suffocated in a wall"
# death, which was falsely reported as failed NPC mining. First clear headroom
# across BOTH future positions; only then put an actual solid floor underneath.
# This destructive preparation is restricted to this disposable CI world.
xdotool key --clearmodifiers t
sleep 1
xdotool type --clearmodifiers --delay 35 '/fill ~-3 ~ ~-9 ~5 ~3 ~5 minecraft:air'
xdotool key Return
sleep 3
xdotool key --clearmodifiers t
sleep 1
xdotool type --clearmodifiers --delay 35 '/fill ~-2 ~-1 ~-8 ~4 ~-1 ~4 minecraft:stone'
xdotool key Return
sleep 3
xdotool key --clearmodifiers t
sleep 1
xdotool type --clearmodifiers --delay 50 '/npc spawn MinerGUI'
xdotool key Return
sleep 3
xdotool key --clearmodifiers t
sleep 1
xdotool type --clearmodifiers --delay 25 '/item replace entity @e[type=reme_studio:player_npc,name=MinerGUI,limit=1] weapon.mainhand with minecraft:iron_pickaxe'
xdotool key Return
sleep 3
xdotool key --clearmodifiers t
sleep 1
xdotool type --clearmodifiers --delay 50 '/setblock ~2 ~ ~ minecraft:stone'
xdotool key Return
sleep 2
xdotool key --clearmodifiers t
sleep 1
xdotool type --clearmodifiers --delay 30 '/execute if block ~2 ~ ~ minecraft:stone run say REME_MINING_PREPARED'
xdotool key Return
sleep 1
# Face east toward the selected target two blocks away, camera down 30 degrees.
xdotool key --clearmodifiers t
sleep 1
xdotool type --clearmodifiers --delay 50 '/tp @s ~ ~ ~ -90 30'
xdotool key Return
sleep 2
import -window root client-gui-mine-prepared.png
# Physical players auto-pick up dropped cobblestone inside their radius.
# This would steal the NPC's exact UUID-claimed drop and make the GUI test
# seed-dependent. Leave real NPC near the ore, but move human Dev six blocks
# away on the *already prepared* disposable solid test platform BEFORE mining.
xdotool key --clearmodifiers t
sleep 1
xdotool type --clearmodifiers --delay 35 '/clear @s minecraft:cobblestone'
xdotool key Return
sleep 1
xdotool key --clearmodifiers t
sleep 1
xdotool type --clearmodifiers --delay 35 '/tp @s ~ ~ ~-6 -90 30'
xdotool key Return
sleep 2
import -window root client-gui-human-away-before-mining.png
xdotool key --clearmodifiers t
sleep 1
xdotool type --clearmodifiers --delay 50 '/npc mine ~2 ~ ~6'
xdotool key Return
sleep 2
import -window root client-gui-mining-progress.png
sleep 10
import -window root client-gui-mining-finished.png
# Query the actual integrated server's block state, not a screenshot guess.
xdotool key --clearmodifiers t
sleep 1
xdotool type --clearmodifiers --delay 35 '/execute if block ~2 ~ ~6 minecraft:air run say REME_MINING_CONFIRMED_AIR'
xdotool key Return
sleep 2
import -window root client-gui-mining-block-confirmed.png
# Show the miner's actual equipment NBT after its action.
xdotool key --clearmodifiers t
sleep 1
xdotool type --clearmodifiers --delay 30 '/data get entity @e[type=reme_studio:player_npc,name=MinerGUI,limit=1] HandItems'
xdotool key Return
sleep 2
import -window root client-gui-mining-tool-after.png
# A mined item must actually reach the NPC's own persistent inventory.
# The 1.21.1 SimpleContainer tag is a list of Slot/id/count compounds.
sleep 7
xdotool key --clearmodifiers t
sleep 1
xdotool type --clearmodifiers --delay 22 '/data get entity @e[type=reme_studio:player_npc,name=MinerGUI,limit=1] NpcBackpack'
xdotool key Return
sleep 2
import -window root client-gui-loot-backpack.png
xdotool key --clearmodifiers t
sleep 1
xdotool type --clearmodifiers --delay 22 '/execute if data entity @e[type=reme_studio:player_npc,name=MinerGUI,limit=1] NpcBackpack[{id:"minecraft:cobblestone"}] run say REME_NPC_LOOT_IN_BACKPACK'
xdotool key Return
sleep 2
import -window root client-gui-loot-verified.png
xdotool key --clearmodifiers t
sleep 1
xdotool type --clearmodifiers --delay 24 '/execute unless data entity @s Inventory[{id:"minecraft:cobblestone"}] run say REME_PLAYER_EMPTY_AFTER_NPC_PICKUP'
xdotool key Return
sleep 2
import -window root client-gui-player-did-not-steal.png
xdotool key --clearmodifiers t
sleep 1
xdotool type --clearmodifiers --delay 50 '/npc info'
xdotool key Return
sleep 2
import -window root client-gui-mining-status.png
echo "Captured real in-world mining witness: fixture/tool/command/progress/final status."
# These are genuine chat results from the integrated Minecraft server,
# not a screenshot filename being treated as gameplay proof.
if ! grep -Eq 'Organic hamlet \| ([3-9]|[1-9][0-9]+) real households' run/logs/latest.log; then
  echo "Minecraft did not verify organically recognized households"; exit 1
fi
if ! grep -q 'Desire paths: .*physical path shaping OFF' run/logs/latest.log; then
  echo "Minecraft did not verify non-destructive foot traffic by default"; exit 1
fi
if ! grep -q 'NPC physical trail formation ENABLED' run/logs/latest.log; then
  echo "Minecraft did not confirm OP-enabled natural path formation"; exit 1
fi
echo "PASS: integrated Minecraft verified 3-home hamlet and guarded foot-traffic opt-in"
# A screenshot pipeline that silently fails to perform mining is not an
# acceptance test. Require observed integrated-server conditional success.
if ! grep -q 'REME_MINING_PREPARED' run/logs/latest.log; then
  echo "Minecraft did not confirm the prepared stone block"; exit 1
fi
if ! grep -q 'REME_MINING_CONFIRMED_AIR' run/logs/latest.log; then
  echo "Minecraft did not confirm that NPC mined the test block"; exit 1
fi
if ! grep -q 'REME_NPC_LOOT_IN_BACKPACK' run/logs/latest.log; then
  echo "Minecraft did not confirm actual cobblestone in NPC saved backpack"; exit 1
fi
if ! grep -q 'REME_PLAYER_EMPTY_AFTER_NPC_PICKUP' run/logs/latest.log; then
  echo "Real human player unexpectedly collected NPC's mined drop"; exit 1
fi
# Integrated-server command registration MUST be visible on this exact
# Java/NeoForge client run, not inferred from separate headless GameTests.
if ! grep -q 'REME NPC: /npc present on dispatcher for player' run/logs/latest.log; then
  echo "Actual integrated server never registered /npc for the joined player"; exit 1
fi
if ! grep -q 'REME_NPC_EGG_REGISTERED' run/logs/latest.log; then
  echo "Actual client command did not find the registered 0.6.6 NPC spawn egg"; exit 1
fi
echo "PASS: joined Minecraft player has /npc registered AND the usable egg exists in game"
echo "PASS: integrated Minecraft verified NPC got mined cobblestone and actual player did not steal it"

echo "Window id: $found"
echo "Minecraft launch/render indicators:"
grep -E 'Reloading ResourceManager|mod/reme_studio|Created.*atlas' client-gui.log | tail -n 10 || true
# For now don't guess UI locations without inspecting menu screenshot.
# This workflow's successful completion DOES NOT certify we entered a world.
