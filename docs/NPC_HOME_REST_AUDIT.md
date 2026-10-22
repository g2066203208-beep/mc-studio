# NPC 0.6.7 — night rest and navigation fix

MC 1.21.1 / NeoForge 21.1.262; this change touches NPC only.

## Bug and behavioral change
- Previously a `SLEEP` decision awarded +12 energy every 200 game ticks even while traveling outside home, escaping, or detained.
- Routine exploration / courier / settlement behavior could steal night-time navigation from ordinary residents.
- The client displayed `REST` while residents were still traveling home, or merely exhausted.

`RestPolicy` now gates real recovery on proximity to the persisted `homePos`, fear/detention, and guard shifts; the same policy keeps routine society movement from overriding bedtime and only shows the REST pose when home. Raiders keep distinct night routines. Guards with energy >=35 continue their watch; exhausted guards may retire.

## Verified in source / pending CI
A new NeoForge GameTest `bedtime_needs_real_home_before_rest_and_preserves_guard_watches` covers street-vs-home recovery, fear, detention, civilian path priority, guard watches, and animation state. Pull request CI should separately verify full server GameTests and a **real** graphical client /npc smoke, not simply a Java build.

## Still NOT implemented
Actual `BedBlock` interaction, bed claims, horizontal bed pose, player-type sleeping, time skipping, user HMCL installation validation, and long-session multi-mod performance remain unfinished. Do not report this as a finished daily-life simulation.

## Isolation
The long-lived `reme_studio` Mod ID remains unchanged so existing world saves do not lose entity/item registry IDs. Moving NPC to independent `mods/livingnpc` with `livingnpc` registry requires an explicit persistence migration; do not drop original IDs or disturb unrelated mod branches.
