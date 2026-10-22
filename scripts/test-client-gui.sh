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
# The city visual test is a disposable OP creative showcase, not a survival
# combat test. Real prior run proved creepers can kill the operator BEFORE
# city commands execute; make the player invulnerable and clear hostiles now.
# This is ONLY set for the dedicated new scratch-world city screenshot run.
if [[ "${REME_CITY_VISUAL:-0}" == "1" || "${REME_POLICE_FILM:-0}" == "1" ]]; then
  xdotool key --clearmodifiers t
  sleep 1
  xdotool type --clearmodifiers --delay 25 '/gamemode creative'
  xdotool key Return
  sleep 3
  xdotool key --clearmodifiers t
  sleep 1
  xdotool type --clearmodifiers --delay 25 '/difficulty peaceful'
  xdotool key Return
  sleep 3
  xdotool key --clearmodifiers t
  sleep 1
  xdotool type --clearmodifiers --delay 25 '/gamerule doMobSpawning false'
  xdotool key Return
  sleep 3
  xdotool key --clearmodifiers t
  sleep 1
  xdotool type --clearmodifiers --delay 25 '/time set day'
  xdotool key Return
  sleep 3
  xdotool key --clearmodifiers t
  sleep 1
  xdotool type --clearmodifiers --delay 25 '/weather clear 999999'
  xdotool key Return
  sleep 3
  xdotool key --clearmodifiers t
  sleep 1
  xdotool type --clearmodifiers --delay 25 '/gamerule doWeatherCycle false'
  xdotool key Return
  sleep 3
  import -window root client-gui-city-creative-fixture.png
fi
# Evidence-grade real client video: original X11 Minecraft pixels.
if [[ "${REME_POLICE_FILM:-0}" == "1" ]]; then
  say_film() {
    xdotool key --clearmodifiers t
    sleep 0.35
    xdotool type --clearmodifiers --delay 12 "$1"
    xdotool key Return
    sleep "${2:-1}"
  }
  # /fill is restricted to the new disposable acceptance world.
  say_film '/fill ~-20 ~-1 ~-20 ~20 ~-1 ~20 minecraft:stone' 2
  say_film '/fill ~-20 ~ ~-20 ~20 ~10 ~20 minecraft:air' 2
  say_film '/time set 1000' 1
  say_film '/npc city survey' 1
  import -window root client-gui-police-preflight.png
  say_film '/npc city demo' 1
  say_film '/npc city census' 1
  say_film '/npc city audit' 1
  ffmpeg -hide_banner -loglevel error -y -f x11grab -draw_mouse 0 \
    -framerate 8 -video_size 1280x720 -i "${DISPLAY}.0" \
    -vcodec libx264 -preset ultrafast -crf 29 -pix_fmt yuv420p \
    client-gui-police-full-process.mp4 >client-police-ffmpeg.log 2>&1 &
  RECORDER=$!
  # Explicit operator test trigger transfers actual food; no teleports.
  say_film '/npc city filmcrime' 1
  import -window root client-gui-police-actual-theft.png
  say_film '/gamemode spectator' 1
  for step in $(seq 1 18); do
    say_film '/execute at @e[type=reme_studio:player_npc,tag=reme_city_shoplifter,limit=1] run tp @s ~-3 ~2 ~4 facing entity @e[type=reme_studio:player_npc,tag=reme_city_shoplifter,limit=1] eyes' 1
    if (( step == 1 || step == 5 || step == 10 || step == 15 || step == 18 )); then
      import -window root "client-gui-police-chase-${step}.png"
    fi
    sleep 3
  done
  say_film '/execute at @e[type=reme_studio:player_npc,tag=reme_city_guard,limit=1] run tp @s ~-3 ~2 ~5 facing entity @e[type=reme_studio:player_npc,tag=reme_city_guard,limit=1] eyes' 2
  import -window root client-gui-police-guard-after-chase.png
  say_film '/npc city audit' 1
  import -window root client-gui-police-inmate-audit.png
  kill -INT "$RECORDER" 2>/dev/null || true
  wait "$RECORDER" || true
  test -s client-gui-police-full-process.mp4 || {
    echo "FAIL: no native GPU/video footage"; exit 1;
  }
  log=run/logs/latest.log
  for token in 'REME_NPC_FILM staged_real_goods_theft=1' \
    'REME_NPC_STORY stage=unpaid_pickup' \
    'REME_NPC_STORY stage=guard_dispatch' \
    'REME_NPC_STORY stage=nonlethal_arrest' \
    'REME_NPC_STORY stage=jail_reached'; do
    if ! grep -Fq "$token" "$log" && ! grep -Fq "$token" client-gui.log; then
      echo "FAIL: NO full native city crime/escort evidence: $token"
      exit 1
    fi
  done
  echo "PASS: original Minecraft 1.21.1 video recorded with verified city crime/escort."
  exit 0
fi

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
# Actual end-user bed command, typed via real integrated MC client.
xdotool key --clearmodifiers t
sleep 1
xdotool type --clearmodifiers --delay 80 '/npc bed'
xdotool key Return
sleep 3
import -window root client-gui-npc-bed-status.png
xdotool key --clearmodifiers t
sleep 1
xdotool type --clearmodifiers --delay 80 '/npc market'
xdotool key Return
sleep 3
import -window root client-gui-npc-market.png
echo "Captured real client command attempts for visual inspection"

# This branch's actual 3D city screenshot acceptance runs in a fresh
# disposable Minecraft single-player save only. The ADMIN manually flattens
# its scratch fixture using genuine vanilla /fill commands; /npc city demo
# itself NEVER bulldozes or edits pre-existing terrain.
if [[ "${REME_CITY_VISUAL:-0}" == "1" ]]; then
  say_city() {
    xdotool key --clearmodifiers t
    sleep 1
    xdotool type --clearmodifiers --delay 25 "$1"
    xdotool key Return
    sleep "${2:-3}"
  }
  # The initial /npc spawn is part of command-smoke coverage. Remove only
  # that one test entity from this disposable scratch-world before founding
  # the district; otherwise the subsequent 12-citizen census shows 13.
  say_city '/kill @e[type=reme_studio:player_npc,distance=..15]' 3
  say_city '/fill ~-20 ~-1 ~-20 ~20 ~-1 ~20 minecraft:stone' 5
  say_city '/fill ~-20 ~ ~-20 ~20 ~10 ~20 minecraft:air' 5
  say_city '/npc city survey' 4
  import -window root client-gui-city-survey.png
  say_city '/npc city demo' 20
  say_city '/npc city census' 4
  say_city '/npc city audit' 4
  import -window root client-gui-city-census.png
  import -window root client-gui-city-street-level.png
  # Identity tags are attached during actual NPC creation in CityDistrictDemo.
  # A moving customer must NEVER be found by its obsolete spawn coordinate.
  say_city '/execute if entity @e[type=reme_studio:player_npc,tag=reme_city_clerk] run say REME_CITY_CLERK_ENTITY_EXISTS' 2
  say_city '/execute if entity @e[type=reme_studio:player_npc,tag=reme_city_guard] run say REME_CITY_GUARD_ENTITY_EXISTS' 2
  say_city '/execute if entity @e[type=reme_studio:player_npc,tag=reme_city_park] run say REME_CITY_PARK_ENTITY_EXISTS' 2
  say_city '/execute if entity @e[type=reme_studio:player_npc,tag=reme_city_shoplifter] run say REME_CITY_SHOPLIFTER_ENTITY_EXISTS' 2
  # Spectator gives a reproducible free-camera vista while leaving
  # all world blocks and authentic NPC entities untouched.
  say_city '/gamemode spectator' 4
  say_city '/tp @s ~-24 ~23 ~-24 facing ~20 ~-14 ~20' 15
  import -window root client-gui-city-aerial-northwest.png
  say_city '/tp @s ~48 ~ ~ facing ~-20 ~-14 ~20' 12
  import -window root client-gui-city-aerial-northeast.png
  # HARD visual acceptance: camera is teleported next to the ACTUAL loaded
  # tracked resident, faces the entity's eyes and records a close-up.
  # 'at' changes the source coordinates, not its identity (@s = our player).
  say_city '/execute at @e[type=reme_studio:player_npc,tag=reme_city_park,limit=1] run tp @s ~ ~2 ~5 facing entity @e[type=reme_studio:player_npc,tag=reme_city_park,limit=1] eyes' 6
  import -window root client-gui-city-npc-park-closeup.png
  say_city '/execute at @e[type=reme_studio:player_npc,tag=reme_city_guard,limit=1] run tp @s ~ ~2 ~4 facing entity @e[type=reme_studio:player_npc,tag=reme_city_guard,limit=1] eyes' 6
  import -window root client-gui-city-npc-guard-closeup.png
  say_city '/execute at @e[type=reme_studio:player_npc,tag=reme_city_clerk,limit=1] run tp @s ~ ~2 ~4 facing entity @e[type=reme_studio:player_npc,tag=reme_city_clerk,limit=1] eyes' 6
  import -window root client-gui-city-npc-shopkeeper-closeup.png
  # Follow a REAL individual for multiple game seconds; note that images
  # prove position/visuals, while unpaid/paid amounts come only from server
  # inventory-based NeoForge tests and REME_NPC_STORY trace transitions.
  say_city '/execute at @e[type=reme_studio:player_npc,tag=reme_city_shoplifter,limit=1] run tp @s ~ ~2 ~5 facing entity @e[type=reme_studio:player_npc,tag=reme_city_shoplifter,limit=1] eyes' 8
  import -window root client-gui-city-shoplifter-t0.png
  sleep 15
  say_city '/execute at @e[type=reme_studio:player_npc,tag=reme_city_shoplifter,limit=1] run tp @s ~ ~2 ~5 facing entity @e[type=reme_studio:player_npc,tag=reme_city_shoplifter,limit=1] eyes' 8
  import -window root client-gui-city-shoplifter-t1.png
  say_city '/npc city audit' 4
  import -window root client-gui-city-live-audit.png
  say_city '/npc market' 4
  import -window root client-gui-city-market-activity.png
  log=run/logs/latest.log
  if ! grep -q '场地通过：41×41空地' "$log"; then
    echo 'FAIL: GUI /npc city survey did not certify the actual flat loaded world'
    exit 1
  fi
  if ! grep -q '已在真实 Minecraft 世界建成 41×41 街区' "$log"; then
    echo 'FAIL: GUI /npc city demo did not build true road/mall blocks'
    exit 1
  fi
  if ! grep -q '实际生活NPC：13' "$log"; then
    echo 'FAIL: GUI real city did not spawn the 12 citizen entities'
    exit 1
  fi
  if ! grep -q '城市治安直播统计' "$log"; then
    echo "FAIL: live in-client police/store register audit missing"; exit 1
  fi
  if ! grep -q '城市实体普查：周围42格内真正存活 NPC' "$log"; then
    echo "FAIL: real in-client population census did not count living entities"
    exit 1
  fi
  for tag in CLERK GUARD PARK SHOPLIFTER; do
    if ! grep -q "REME_CITY_${tag}_ENTITY_EXISTS" "$log"; then
      echo "FAIL: real tracked NPC ${tag} not found in 3D city world"
      exit 1
    fi
  done
  for role in park guard shopkeeper; do
    test -s "client-gui-city-npc-${role}-closeup.png" || {
      echo "FAIL: no actual in-client NPC closeup screenshot for ${role}"
      exit 1
    }
  done
  echo "PASS: real NeoForge 1.21.1 GUI visual city world generated streets, two-floor mall, houses, police post and NPCs."
  exit 0
fi

# Dedicated real-client acceptance: this mode explicitly stops after genuine
# command registration, egg registry and /npc spawn are observed. The old
# comprehensive screenshot/mining scenario occasionally failed due to a
# separate coordinate fixture and masked an already-working command test.
if [[ "${REME_COMMAND_SMOKE_ONLY:-0}" == "1" ]]; then
  log=run/logs/latest.log
  if ! grep -q 'REME NPC 0.7.1-dev COMMANDS REGISTERED: /npc=true, /remecheck=true' "$log"; then
    echo "FAIL: 21.1.262 server never actually registered both commands"; exit 1
  fi
  if ! grep -q '/npc registered=true' "$log"; then
    echo "FAIL: actual joined client did not execute /remecheck"; exit 1
  fi
  if ! grep -q 'REME_NPC_EGG_REGISTERED' "$log"; then
    echo "FAIL: actual integrated world did not resolve the NPC spawn egg"; exit 1
  fi
  if ! grep -q '原生睡眠状态:' "$log"; then
    echo "FAIL: actual joined client did not execute /npc bed"; exit 1
  fi
  if ! grep -q '附近20格 NPC 市集' "$log"; then
    echo "FAIL: real joined Minecraft client did not execute /npc market"; exit 1
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
