# REME NPC — independent Minecraft mod

Minecraft 1.21.1, NeoForge 21.1.262+, Java 21.
Independent Mod ID: `reme_npc`. Entry class: `dev.reme.npcstudio.NpcMod`.

Build from this subproject only:
```sh
cd mods/npc
./gradlew test build
./gradlew runGameTestServer
```
The output is `mods/npc/build/libs/reme_npc-0.6.7-dev.jar`.

In the game: `/remecheck` and `/npc` need no cheats;
`/npc spawn` and `/npc demo` require cheats/operator.
The creative-mode egg is `reme_npc:npc_spawn_egg`.

This project does not depend on WorldSeam, PEAK, RPMotion or mechanical Boss.
Never install the historical `reme_studio-0.1.0-dev.jar` in place of the NPC JAR.

**Existing saves warning:** previous NPC versions used registry IDs starting with
`reme_studio:`. This independent build changes them to `reme_npc:`.
There is no tested save migration yet. Test in a NEW disposable world; do not
open an old populated save without a backup and ID mapping.

CI: `.github/workflows/npc-standalone.yml` builds the actual jar,
runs 54 NeoForge GameTests, and enters a real graphical Minecraft client
to verify commands and spawn egg registration. Client compatibility with a
user's custom modpack is separate and not yet verified.
