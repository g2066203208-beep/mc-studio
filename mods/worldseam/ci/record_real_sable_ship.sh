#!/usr/bin/env bash
# Genuine, never synthesized Minecraft 1.21.1 frames, NeoForge+Create+Sable.
# This CI-only harness MUST record failure evidence if native two-way crossing
# does not happen. Raw /fill air commands execute inside integrated server.
set -euo pipefail
export LIBGL_ALWAYS_SOFTWARE=1

bash ./gradlew runClient -PworldseamShipFilm --no-daemon --console=plain > real-ship-film-client.log 2>&1 &
minecraft_pid=$!
film_pid=""
# Background ffmpeg can inherit SIGINT=ignored from a non-interactive bash.
# SIGTERM is handled by ffmpeg and flushes the actual MP4 moov atom. Never
# wait unboundedly for a recorder after the real Sable test has completed.
stop_film() {
  [[ -n "$film_pid" ]] || return 0
  kill -TERM "$film_pid" 2>/dev/null || true
  for i in $(seq 1 60); do
    kill -0 "$film_pid" 2>/dev/null || break
    sleep 0.25
  done
  kill -KILL "$film_pid" 2>/dev/null || true
  wait "$film_pid" 2>/dev/null || true
  film_pid=""
}
cleanup() {
  stop_film
  kill "$minecraft_pid" 2>/dev/null || true
}
trap cleanup EXIT

echo "WORLDSEAM_FILM_LAUNCH: genuine Minecraft window under Xvfb"
for step in $(seq 1 180); do
  if grep -q "Reloading ResourceManager" real-ship-film-client.log; then break; fi
  if ! kill -0 "$minecraft_pid" 2>/dev/null; then
    tail -n 70 real-ship-film-client.log
    exit 1
  fi
  sleep 1
done
sleep 22
xdotool search --name Minecraft getwindowname 2>/dev/null || true
# Click genuine Minecraft first-run welcome, then Singleplayer/Create World.
xdotool mousemove 640 568 click 1
sleep 8
xdotool mousemove 640 336 click 1
sleep 8
xdotool mousemove 640 540 click 1
sleep 8
# On New World UI: enable cheats and CREATE a disposable real game world.
xdotool mousemove 640 403 click 1
sleep 2

# The capture starts BEFORE Minecraft world loading. Do not replace or
# composite frames or drop crashes; failure attempts are still viewable.
ffmpeg -hide_banner -loglevel warning -nostdin -y \
  -f x11grab -framerate 10 -video_size 1280x720 -i "$DISPLAY" \
  -vf "scale=854:480" -c:v libx264 -preset ultrafast \
  -crf 30 -pix_fmt yuv420p -movflags +faststart \
  real-ship-attempt-UNVERIFIED.mp4 > real-ship-ffmpeg.log 2>&1 &
film_pid=$!
xdotool mousemove 480 565 click 1

# Each still is a direct raw X11 frame from the running Minecraft client,
# not a composite or synthesized aircraft image. A second capture process
# never edits the uninterrupted main MP4.
capture_real_frame() {
  local output="$1"
  timeout 15 ffmpeg -hide_banner -loglevel error -nostdin -y \
    -f x11grab -framerate 1 -video_size 1280x720 -i "$DISPLAY" \
    -frames:v 1 -vf "scale=960:540" -q:v 3 "$output" || true
}

# Capture JVM thread stacks if the real integrated client/server stalls.
# An MP4 of a stuck image is diagnostic evidence, NOT a successful flight.
capture_jvm_diagnostics() {
  local label="$1"
  {
    echo "=== native-flight watchdog: $label ==="
    date -u
    jcmd -l || true
    ps -eo pid,ppid,stat,pcpu,pmem,etime,comm,args | grep -E 'java|Minecraft|gradle|ffmpeg' | head -n 45 || true
    for pid in $(jcmd -l | awk '$2 == "net.neoforged.devlaunch.Main" {print $1}'); do
      echo "=== JVM PID $pid threads ==="
      timeout 25 jcmd "$pid" Thread.print -l || true
    done
  } >> real-ship-jvm-thread-dump.txt 2>&1
}
ready=0; returned=0; failure=0; ready_step=0; last_diag=0
camera_ui_hidden=0; ready_still=0; descent_still=0; arena_seen=0; arena_step=0
for step in $(seq 1 240); do
  if [ "$arena_seen" = 0 ] &&
     grep -q "WORLDSEAM_FILM_ARENA_READY" real-ship-film-client.log; then
    arena_seen=1
    arena_step="$step"
    echo "WORLDSEAM_FILM_WATCHDOG: real cleared arena ready, starting server progress check"
  fi
  if [ "$arena_seen" = 1 ] && [ "$ready" = 0 ] &&
     [ "$step" -ge "$((arena_step+55))" ] &&
     ! grep -q "WORLDSEAM_FILM_NATIVE_TICK_HANDSHAKE" real-ship-film-client.log; then
    echo "WORLDSEAM_FILM_STALL_BEFORE_ASSEMBLY: integrated server stopped tick progress after real /fill"
    capture_jvm_diagnostics "server-thread-stuck-after-fill-before-assembly"
    failure=1
    break
  fi
  if [[ "$camera_ui_hidden" = 0 ]] &&
     grep -q "WORLDSEAM_FILM_ARENA_READY" real-ship-film-client.log; then
    xdotool key F1 || true
    camera_ui_hidden=1
  fi
  if [[ "$ready_still" = 0 ]] &&
     grep -q "WORLDSEAM_FILM_RECORD_READY" real-ship-film-client.log; then
    capture_real_frame real-ship-first-frame.jpg
    ready_still=1
  fi
  if [[ "$descent_still" = 0 ]] &&
     grep -q "WORLDSEAM_FILM_REAL_SHIP_DOWN" real-ship-film-client.log; then
    # Screenshot after the real client has received recent Sable pose and
    # spectator orientation; server leaves 35 ticks of REAL descent hold.
    sleep 1
    capture_real_frame real-ship-below-seam.jpg
    descent_still=1
  fi
  if grep -q "WORLDSEAM_FILM_RECORD_READY" real-ship-film-client.log; then
    if [ "$ready" = 0 ]; then ready_step="$step"; fi
    ready=1
  fi
  if [ "$ready" = 1 ] && [ "$step" -ge "$((ready_step+45))" ] &&
     ! grep -q "WORLDSEAM_FILM_FRAME" real-ship-film-client.log; then
    echo "WORLDSEAM_FILM_STALLED: no real server-tick movement telemetry 45 seconds after Sable assembly; exporting Java thread stacks"
    capture_jvm_diagnostics "no-server-ticks-after-assembly"
    failure=1
    break
  fi
  if [ "$ready" = 1 ] && [ "$step" -ge "$((ready_step+90))" ] &&
     [ "$last_diag" = 0 ]; then
    capture_jvm_diagnostics "flight-not-complete-90s"
    last_diag=1
  fi
  if grep -q "WORLDSEAM_FILM_PASS_REAL_SHIP_RETURN" real-ship-film-client.log; then
    returned=1
    # The recorded MP4 is continuous; do not grab the post-return still
    # before the client actually draws the server's final camera pose.
    sleep 2
    capture_real_frame real-ship-returned.jpg
    sleep 5 # show actual completed in-game view; no artificial frames
    break
  fi
  if grep -q "WORLDSEAM_FILM_FAIL_REAL_SHIP_CROSSING" real-ship-film-client.log; then
    failure=1
    sleep 5 # preserve the genuine crashed/failed image on tape
    break
  fi
  if ! kill -0 "$minecraft_pid" 2>/dev/null; then
    echo "WORLDSEAM_FILM_MINECRAFT_EXITED_BEFORE_RETURN"
    break
  fi
  sleep 1
done

stop_film
test -s real-ship-attempt-UNVERIFIED.mp4 ||
  { echo 'REAL_SHIP_FILM_CAPTURE_FAILED'; tail -n 60 real-ship-ffmpeg.log; exit 1; }
ffprobe -v error -show_entries format=duration -of default=nw=1 \
  real-ship-attempt-UNVERIFIED.mp4 || true
grep -E 'WORLDSEAM_FILM_(REAL_VANILLA_COMMAND|RECORD_READY|FRAME|REAL_SHIP_DOWN|PASS_REAL_SHIP_RETURN|FAIL_REAL_SHIP_CROSSING)' \
  real-ship-film-client.log > real-ship-flight-telemetry.log || true
test "$ready" -eq 1 || {
  echo 'WORLDSEAM_FILM_FAIL: actual Sable aircraft never assembled or game world never started'
  tail -n 95 real-ship-film-client.log
  exit 1
}
if [[ "$returned" -ne 1 || "$failure" -eq 1 ]]; then
  echo "WORLDSEAM_FILM_FAIL: recorded genuine aircraft flight attempt, but no verified bidirectional physical crossing. The MP4 is explicitly UNVERIFIED."
  tail -n 75 real-ship-flight-telemetry.log
  exit 1
fi
# Do not call a server-only trajectory a visible movie. Candidate colors
# must appear together in EACH raw screenshot. This is only a necessary
# pixel gate; a human still must review the uninterrupted MP4 and Sable UUID.
python3 ci/verify_ship_pixels.py \
  real-ship-first-frame.jpg \
  real-ship-below-seam.jpg \
  real-ship-returned.jpg || {
    echo 'WORLDSEAM_FILM_FAIL: visible aircraft pixels not verified at all three stages';
    exit 1;
  }
# Only successful, completely real two-direction native impulse motion
# may ever receive this name. It still does NOT prove dual-world contacts.
# A server-side bbox crossing without the true Sable client sublevel is
# NOT a visible ship recording. This explicitly catches the old success
# whose real MP4 showed only sky, lava and a dark seam.
if ! grep -q 'WORLDSEAM_FILM_CLIENT_VISIBLE_SHIP_READY' real-ship-film-client.log; then
  echo 'WORLDSEAM_FILM_FAIL: native Sable ship never became ClientLevel renderable; MP4 cannot count as visible gameplay'
  exit 1
fi
# Never label externally-forced Rapier motion as Aeronautics-only
# propulsion, or claim a chart-fused solver that does not exist.
mv real-ship-attempt-UNVERIFIED.mp4 real-ship-bidirectional-EXTERNAL-FORCE-ONLY.mp4
echo 'WORLDSEAM_FILM_PHYSICS_ROUNDTRIP_ONLY: genuine same-UUID Sable native Rapier movement crossed plane both directions; real Create Aeronautics actor spun, BUT movement is externally forced, client pixels and true dual-chart contacts remain UNVERIFIED'
