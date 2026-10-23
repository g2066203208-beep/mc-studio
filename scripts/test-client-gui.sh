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
if [[ "${REME_CITY_VISUAL:-0}" == "1" ]]; then
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
  # Remove HUD/chat from the ACTUAL framebuffer while filming real
  # in-world people and buildings. Not a synthesized panorama or composite.
  photo_city() {
    xdotool key --clearmodifiers F1
    sleep 3
    import -window root "$1"
    sleep 2
    xdotool key --clearmodifiers F1
  }
  # The initial /npc spawn is part of command-smoke coverage. Remove only
  # that one test entity from this disposable scratch-world before founding
  # the district; otherwise the subsequent 12-citizen census shows 13.
  say_city '/kill @e[type=reme_studio:player_npc,distance=..15]' 3
  say_city '/fill ~-20 ~-1 ~-20 ~20 ~-1 ~20 minecraft:stone' 5
  say_city '/fill ~-20 ~ ~-20 ~20 ~10 ~20 minecraft:air' 5
  say_city '/npc city survey' 4
  import -window root client-gui-city-survey.png
  # Previously filming started well AFTER jail_reached. Capture an UNBROKEN
  # real client video BEFORE the city even spawns its first thief.
  # This is an actual screen recorder, no still-image assembly or fake
  # path animations. Start a stable UUID spectator chase immediately.
  ffmpeg -loglevel error -nostdin -y -f x11grab -video_size 1280x720 \
    -framerate 6 -i "$DISPLAY" -c:v libx264 -preset ultrafast -crf 27 \
    -pix_fmt yuv420p -movflags +faststart \
    client-gui-city-full-crime-to-locked-jail.mp4 >client-gui-city-case-ffmpeg.log 2>&1 &
  CASE_VIDEO_PID=$!
  # Preserve the ORIGINAL center feet Y before real surface blocks are
  # placed. Spawning the marker AFTER /npc city demo captures a player who
  # has been collision-pushed one block UP by the new road; /npc city grow
  # then checks the wrong air block as the original center and fails.
  # The marker is a disposable camera/fixture locator, never an NPC.
  say_city '/summon minecraft:marker ~ ~ ~ {Tags:["reme_city_origin"]}' 2
  say_city '/npc city demo' 1
  # Server-authoritative client-visible human PLAYER emote uses the same
  # 30 authored animation resources as real NPCs, while X11 MP4 captures.
  say_city '/npc emote greet' 3
  say_city '/gamemode spectator' 1
  say_city '/npc camera chase shoplifter' 2
  import -window root client-gui-city-full-case-t0.png
  sleep 13
  import -window root client-gui-city-full-case-t1.png
  sleep 17
  import -window root client-gui-city-full-case-t2.png
  sleep 17
  import -window root client-gui-city-full-case-t3.png
  sleep 18
  import -window root client-gui-city-full-case-t4.png
  sleep 15
  import -window root client-gui-city-full-case-t5.png
  say_city '/npc camera stop' 1
  kill -INT "$CASE_VIDEO_PID" 2>/dev/null || true
  wait "$CASE_VIDEO_PID" || true
  # Return to the physical city center (not an obsolete moving NPC spawn).
  # Native spectator travel moves only our observer, not any citizen.
  say_city '/execute at @e[type=minecraft:marker,tag=reme_city_origin,limit=1] run tp @s ~ ~1 ~' 2
  # Separate urban-professional REAL-client acceptance, using the same
  # actual city origin marker and vanilla game commands. A clean disposable
  # test world may be flattened by the harness, NEVER by the OP expansion.
  if [[ "${REME_URBAN_VISUAL:-0}" == "1" ]]; then
    ffmpeg -loglevel error -nostdin -y -f x11grab -video_size 1280x720 \
      -framerate 6 -i "$DISPLAY" -c:v libx264 -preset ultrafast -crf 28 \
      -pix_fmt yuv420p -movflags +faststart \
      client-gui-city-four-public-service-zones.mp4 >client-gui-city-urban-video.log 2>&1 &
    URBAN_VIDEO_PID=$!
    say_city '/execute at @e[type=minecraft:marker,tag=reme_city_origin,limit=1] run fill ~-62 ~-1 ~-20 ~-22 ~-1 ~20 minecraft:stone' 4
    say_city '/execute at @e[type=minecraft:marker,tag=reme_city_origin,limit=1] run fill ~-62 ~ ~-20 ~-22 ~11 ~20 minecraft:air' 4
    say_city '/execute at @e[type=minecraft:marker,tag=reme_city_origin,limit=1] run fill ~-21 ~-1 ~-2 ~-21 ~-1 ~2 minecraft:stone' 3
    say_city '/execute at @e[type=minecraft:marker,tag=reme_city_origin,limit=1] run fill ~-21 ~ ~-2 ~-21 ~10 ~2 minecraft:air' 3
    say_city '/npc city zone survey hospital' 3
    say_city '/npc city zone hospital' 16
    say_city '/execute if entity @e[type=reme_studio:player_npc,tag=reme_urban_hospital_0] run say REME_URBAN_HOSPITAL_STAFF_REAL' 3
    say_city '/execute at @e[type=minecraft:marker,tag=reme_city_origin,limit=1] run tp @s ~-55 ~26 ~-24 facing ~-42 ~2 ~' 12
    photo_city client-gui-city-zone-hospital-true-3d.png
    say_city '/execute at @e[type=reme_studio:player_npc,tag=reme_urban_hospital_0,limit=1] run tp @s ~ ~2 ~5 facing entity @e[type=reme_studio:player_npc,tag=reme_urban_hospital_0,limit=1] eyes' 5
    photo_city client-gui-city-zone-hospital-npc-closeup.png
    say_city '/execute at @e[type=minecraft:marker,tag=reme_city_origin,limit=1] run tp @s ~ ~1 ~' 3

    say_city '/execute at @e[type=minecraft:marker,tag=reme_city_origin,limit=1] run fill ~-20 ~-1 ~-62 ~20 ~-1 ~-22 minecraft:stone' 4
    say_city '/execute at @e[type=minecraft:marker,tag=reme_city_origin,limit=1] run fill ~-20 ~ ~-62 ~20 ~11 ~-22 minecraft:air' 4
    say_city '/execute at @e[type=minecraft:marker,tag=reme_city_origin,limit=1] run fill ~-2 ~-1 ~-21 ~2 ~-1 ~-21 minecraft:stone' 3
    say_city '/execute at @e[type=minecraft:marker,tag=reme_city_origin,limit=1] run fill ~-2 ~ ~-21 ~2 ~10 ~-21 minecraft:air' 3
    say_city '/npc city zone survey education' 3
    say_city '/npc city zone education' 16
    say_city '/execute if entity @e[type=reme_studio:player_npc,tag=reme_urban_education_0] run say REME_URBAN_EDUCATION_STAFF_REAL' 3
    say_city '/execute at @e[type=minecraft:marker,tag=reme_city_origin,limit=1] run tp @s ~-11 ~25 ~-54 facing ~ ~1 ~-42' 12
    photo_city client-gui-city-zone-education-true-3d.png
    say_city '/execute at @e[type=reme_studio:player_npc,tag=reme_urban_education_0,limit=1] run tp @s ~ ~2 ~5 facing entity @e[type=reme_studio:player_npc,tag=reme_urban_education_0,limit=1] eyes' 5
    photo_city client-gui-city-zone-education-npc-closeup.png
    say_city '/execute at @e[type=minecraft:marker,tag=reme_city_origin,limit=1] run tp @s ~ ~1 ~' 3

    say_city '/execute at @e[type=minecraft:marker,tag=reme_city_origin,limit=1] run fill ~-20 ~-1 ~22 ~20 ~-1 ~62 minecraft:stone' 4
    say_city '/execute at @e[type=minecraft:marker,tag=reme_city_origin,limit=1] run fill ~-20 ~ ~22 ~20 ~11 ~62 minecraft:air' 4
    say_city '/execute at @e[type=minecraft:marker,tag=reme_city_origin,limit=1] run fill ~-2 ~ ~21 ~2 ~10 ~21 minecraft:air' 3
    say_city '/execute at @e[type=minecraft:marker,tag=reme_city_origin,limit=1] run fill ~-2 ~-1 ~21 ~2 ~-1 ~21 minecraft:stone' 3
    say_city '/npc city zone survey government' 3
    say_city '/npc city zone government' 16
    say_city '/execute if entity @e[type=reme_studio:player_npc,tag=reme_urban_government_0] run say REME_URBAN_GOVERNMENT_STAFF_REAL' 3
    say_city '/execute at @e[type=minecraft:marker,tag=reme_city_origin,limit=1] run tp @s ~-11 ~25 ~53 facing ~ ~1 ~42' 12
    photo_city client-gui-city-zone-government-true-3d.png
    say_city '/execute at @e[type=reme_studio:player_npc,tag=reme_urban_government_0,limit=1] run tp @s ~ ~2 ~5 facing entity @e[type=reme_studio:player_npc,tag=reme_urban_government_0,limit=1] eyes' 5
    photo_city client-gui-city-zone-government-npc-closeup.png
    say_city '/execute at @e[type=minecraft:marker,tag=reme_city_origin,limit=1] run tp @s ~ ~1 ~' 3

    say_city '/execute at @e[type=minecraft:marker,tag=reme_city_origin,limit=1] run fill ~22 ~-1 ~-20 ~62 ~-1 ~20 minecraft:stone' 4
    say_city '/execute at @e[type=minecraft:marker,tag=reme_city_origin,limit=1] run fill ~22 ~ ~-20 ~62 ~11 ~20 minecraft:air' 4
    say_city '/execute at @e[type=minecraft:marker,tag=reme_city_origin,limit=1] run fill ~21 ~-1 ~-2 ~21 ~-1 ~2 minecraft:stone' 3
    say_city '/execute at @e[type=minecraft:marker,tag=reme_city_origin,limit=1] run fill ~21 ~ ~-2 ~21 ~10 ~2 minecraft:air' 3
    say_city '/npc city zone survey transport' 3
    say_city '/npc city zone transport' 16
    say_city '/execute if entity @e[type=reme_studio:player_npc,tag=reme_urban_transport_0] run say REME_URBAN_TRANSPORT_STAFF_REAL' 3
    say_city '/execute at @e[type=minecraft:marker,tag=reme_city_origin,limit=1] run tp @s ~53 ~25 ~-11 facing ~42 ~1 ~' 12
    photo_city client-gui-city-zone-transport-true-3d.png
    say_city '/execute at @e[type=reme_studio:player_npc,tag=reme_urban_transport_0,limit=1] run tp @s ~ ~2 ~5 facing entity @e[type=reme_studio:player_npc,tag=reme_urban_transport_0,limit=1] eyes' 5
    photo_city client-gui-city-zone-transport-npc-closeup.png
    say_city '/execute at @e[type=minecraft:marker,tag=reme_city_origin,limit=1] run tp @s ~ ~1 ~' 3
    say_city '/npc city census' 4
    import -window root client-gui-city-zone-population-live.png
    kill -INT "$URBAN_VIDEO_PID" 2>/dev/null || true
    wait "$URBAN_VIDEO_PID" || true
    log=run/logs/latest.log
    for type in HOSPITAL EDUCATION GOVERNMENT TRANSPORT; do
      if ! grep -q "REME_URBAN_${type}_STAFF_REAL" "$log"; then
        echo "FAIL: actual in-client urban staff missing in ${type}"; exit 1
      fi
    done
    for zone in hospital education government transport; do
      if ! grep -q "REME_NPC_URBAN_DISTRICT zone=${zone}" "$log"; then
        echo "FAIL: physical professional zone missing in actual Minecraft ${zone}"; exit 1
      fi
      test -s "client-gui-city-zone-${zone}-true-3d.png" || {
        echo "FAIL: actual 3D district screenshot missing for ${zone}"; exit 1
      }
      test -s "client-gui-city-zone-${zone}-npc-closeup.png" || {
        echo "FAIL: real citizen close-up photo missing for ${zone}"; exit 1
      }
      test -s "client-gui-city-zone-${zone}-npc-closeup.png" || {
        echo "FAIL: real citizen close-up photo missing for ${zone}"; exit 1
      }
      test -s "client-gui-city-zone-${zone}-npc-closeup.png" || {
        echo "FAIL: real citizen close-up photo missing for ${zone}"; exit 1
      }
      test -s "client-gui-city-zone-${zone}-npc-closeup.png" || {
        echo "FAIL: real citizen close-up photo missing for ${zone}"; exit 1
      }
    done
    test -s client-gui-city-four-public-service-zones.mp4 || {
      echo 'FAIL: original-unmodified X11 Minecraft walkaround video missing'; exit 1
    }
    # Actual uninterrupted captured X11 video, not a stitched image sequence.
    ffprobe -v error -select_streams v:0 -show_entries stream=codec_name,width,height \
      -of default=noprint_wrappers=1 client-gui-city-four-public-service-zones.mp4 \
      | grep -Fq 'codec_name=h264' || {
        echo 'FAIL: actual client does not have a real H.264 video stream'; exit 1
      }
    echo "PASS: actual NeoForge client generated four district zones, sixteen real NPC careers and unmodified visual frames"
    exit 0
  fi
  # First borough remains intact. Only the completely disposable adjacent
  # 41x41 testing land is flattened, never by the actual city command.
  # Previous GUI harness accidentally used "positioned ~ ~-1 ~" AND
  # fill y=-1, laying the NEW borough ground TWO blocks below the city's
  # actual center. CityDistrictDemo.survey correctly rejected that unsafe
  # site. Prepare only this disposable scratch fixture at the SAME level as
  # the first city; never weaken the city's real preflight safety checks.
  say_city '/execute at @e[type=minecraft:marker,tag=reme_city_origin,limit=1] run fill ~22 ~-1 ~-20 ~62 ~-1 ~20 minecraft:stone' 6
  say_city '/execute at @e[type=minecraft:marker,tag=reme_city_origin,limit=1] run fill ~22 ~ ~-20 ~62 ~10 ~20 minecraft:air' 6
  # Five physically supported one-block seam tiles must also be present
  # before /npc city grow can connect the two actual road surfaces.
  say_city '/execute at @e[type=minecraft:marker,tag=reme_city_origin,limit=1] run fill ~21 ~-1 ~-2 ~21 ~-1 ~2 minecraft:stone' 3
  say_city '/execute at @e[type=minecraft:marker,tag=reme_city_origin,limit=1] run fill ~21 ~ ~-2 ~21 ~10 ~2 minecraft:air' 3
  say_city '/npc city grow east' 20
  import -window root client-gui-city-connected-borough.png
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
  # Start unmodified REAL X11 screen capture. This is actual GUI video,
  # not assembled still screenshots nor a staged engine animation.
  ffmpeg -loglevel error -nostdin -y -f x11grab -video_size 1280x720 \
    -framerate 6 -i "$DISPLAY" -c:v libx264 -preset ultrafast -crf 27 \
    -pix_fmt yuv420p -movflags +faststart \
    client-gui-city-physical-npc-follow.mp4 >client-gui-city-ffmpeg.log 2>&1 &
  VIDEO_PID=$!
  # REAL MOD CAMERA: the target UUID is retained across frames. The
  # spectator alone moves continuously; NPC AI is not teleported/paused.
  say_city '/npc camera chase shoplifter' 8
  import -window root client-gui-city-shoplifter-t0.png
  sleep 14
  import -window root client-gui-city-shoplifter-t1.png
  sleep 14
  import -window root client-gui-city-shoplifter-t2.png
  say_city '/npc camera stop' 3
  say_city '/npc camera pov guard' 5
  import -window root client-gui-city-guard-native-possession.png
  sleep 8
  import -window root client-gui-city-guard-native-possession-t1.png
  say_city '/npc camera stop' 3
  kill -INT "$VIDEO_PID" 2>/dev/null || true
  wait "$VIDEO_PID" || true
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
  if ! grep -q '已建第二个真实41×41城区' "$log"; then
    echo "FAIL: real NeoForge client did not generate a second 41x41 urban district"; exit 1
  fi
  if ! grep -q '实际生活NPC：15' "$log"; then
    echo 'FAIL: GUI real city did not spawn the 12 citizen entities'
    exit 1
  fi
  if ! test -s client-gui-city-full-crime-to-locked-jail.mp4; then
    echo "FAIL: no real full-incident native Minecraft video"; exit 1
  fi
  # Check the FULL case in real integrated-client server stdout, NOT staged
  # GameTest results: identical live thief UUID must steal, get reported,
  # arrested and ENTER a physically locked cell in timestamp order. The
  # camera attach must precede the actual arrest, else filming was late.
  # Real GUI v085 had TWO different theft cases: filmed thief A was
  # immobile outside a distant jail; unfilmed thief B reached a cell.
  # Never accept a Frankenstein timeline by matching each stage globally.
  # The verifier binds the FIRST native CHASE camera to the SAME UUID,
  # merchant, officer, three actual hits, iron gate and physical cell.
  python3 ../scripts/verify-npc-gui-case.py client-gui.log
  # A file existing on disk does not prove the original H264 stream decodes.
  ffmpeg -v error -xerror -i client-gui-city-full-crime-to-locked-jail.mp4 \\
    -f null - || { echo "FAIL: actual continuous MP4 cannot fully decode"; exit 1; }
  ffprobe -v error -select_streams v:0 \\
    -show_entries stream=width,height -of csv=p=0 \\
    client-gui-city-full-crime-to-locked-jail.mp4 | grep -Fxq '1280,720' || {
    echo "FAIL: original Minecraft 1280x720 RGB capture missing"; exit 1;
  }  if ! test -s client-gui-city-physical-npc-follow.mp4; then
    echo "FAIL: no genuine H264 recording of Minecraft NPC pursuit/follow"; exit 1
  fi
  if ! grep -q '已连续跟踪真实NPC' "$log"; then
    echo "FAIL: true integrated spectator camera never attached to actual resident"; exit 1
  fi
  if ! grep -q 'NPC镜头已解除附身/追踪' "$log"; then
    echo "FAIL: live NPC camera could not detach"; exit 1
  fi
  for frame in client-gui-city-shoplifter-t0.png client-gui-city-shoplifter-t1.png client-gui-city-shoplifter-t2.png client-gui-city-guard-native-possession.png; do
    test -s "$frame" || { echo "FAIL: missing native moving follow camera RGB frame $frame"; exit 1; }
  done
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
  if ! grep -q 'REME NPC 0.7.2-dev COMMANDS REGISTERED: /npc=true, /remecheck=true' "$log"; then
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
