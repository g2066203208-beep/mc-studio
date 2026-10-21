# v0.5.2 client-visible mod load diagnosis

This patch responds to an actual user's screenshot: `/npc spawn` is red from **character 1**, indicating the server did not expose the command at all. It does NOT show an AI malfunction. The screenshot alone cannot reveal whether the JAR was installed in the active HMCL game profile.

## How to distinguish install vs command failure in-game

1. Launch the **same Minecraft 1.21.1 NeoForge profile**. On the Mods screen search `REME MC Studio`.
2. If it is missing, the JAR is NOT loaded. In HMCL use the current **version's** Mod Management / Add Local Mod to install. HMCL version isolation can place the active `mods` directory under `.minecraft/versions/<selected instance>/mods`, not `.minecraft/mods`. The actual game directory depends on user setup.
3. If the mod loads, **on entering any world** v0.5.2 sends a message: `REME NPC v0.5.2 loaded; /npc registered`, or `... /npc command MISSING`. `logs/latest.log` records diagnostics.
4. Run `/remecheck` without cheats. It is public and explicitly reports whether `/npc` was registered on the command dispatcher.
5. `/npc spawn` requires **cheats / permission level 2**. If `/remecheck` works but `/npc spawn` is inaccessible, enable cheats in a throwaway world (Open to LAN > Allow Cheats = ON), or use an op server account.
6. Only after `/remecheck` and `/npc spawn` work test real gameplay. Remove earlier v0.5.x JARs to avoid duplicate `reme_studio`.

## Verification

- NeoForge GameTest asserts in a real server command dispatcher that `npc`, `npc spawn`, `npc demo`, and unprivileged `remecheck` exist. It actually executes `remecheck` with permission 0.
- Original fix v0.5.1 had 10/10 GameTests and GUI boot resource log but did not prove actual user's HMCL instance installed or loaded it.
- This patch adds a login chat message for visibility and is still **not proof of in-game client gameplay on user's PC**.
- Reference: NeoForge `RegisterCommandsEvent` fires on `NeoForge.EVENT_BUS`; MineColonies has a `@SubscribeEvent onCommandsRegister` handler. We follow the same event family, but add explicit diagnostics rather than claiming other projects' code is ours.

## Verified CI / user status (2026-10-21)

- The user's uploaded screenshot shows the **root** slash command is unknown/incomplete at the first character. This is evidence of missing command registration in that running profile, **not evidence that NPC pathfinding failed**. We cannot remotely see the user's active HMCL mods folder, so exact cause remains undetermined.
- The distributed v0.5.1 JAR was inspected locally: a valid ZIP JAR with `META-INF/neoforge.mods.toml` (modId `reme_studio`, version 0.5.1-dev), compiled `StudioMod` and `NpcCommands`; NeoForge dependency requires 21.1.248+.
- v0.5.2 source `b7942080143f3ce7b02e8b857c5340853323b1e6`: pass [CI Build 39539464195](https://github.com/g2066203208-beep/mc-studio/actions/runs/39539464195); pass [GameTest 39539498111](https://github.com/g2066203208-beep/mc-studio/actions/runs/39539498111) **11/11** (logged `/npc registered=true` on no-cheats diagnostic).
- Follow-up workflow-only `82bf974a318ccab9cf8a24a4b958024344fd01b2`: [Build 39539710056](https://github.com/g2066203208-beep/mc-studio/actions/runs/39539710056) SUCCESS; [Server GameTest 39539710127](https://github.com/g2066203208-beep/mc-studio/actions/runs/39539710127) **11/11** PASS.
- Uploaded artifact `12318707103`: extracted and verified `reme_studio-0.5.2-dev.jar`, 126796 bytes, SHA-256 `48d17f2b798d07d31e461d5d7e5771cd5026d7d651ea345ef3a0506693ae065e`; includes mod descriptor and diagnostic code. It is NOT an installation proof for user's PC.
- [Client virtual display run 39539704680](https://github.com/g2066203208-beep/mc-studio/actions/runs/39539704680) requested; check outcome and logs before calling client boot successful. Even if it passes, the harness does not interact with a survival world or read user's instance folder.
- Exact next input required for the user's failure: an **HMCL selected-version Mod Management screenshot showing REME MC Studio or not**, or the active selected game's `logs/latest.log`. Cannot diagnose exact folder/missing jar solely from slash error.

## Completed virtual-display client startup
- [GitHub Actions 39539704680](https://github.com/g2066203208-beep/mc-studio/actions/runs/39539704680) **SUCCESS** (head `82bf974a318ccab9cf8a24a4b958024344fd01b2`). Minecraft NeoForge `runClient` under Linux Xvfb survived until the harness terminated it deliberately after 180 seconds (exit code 124). Log shows Render thread reloaded `mod/reme_studio` textures/resources and created texture atlases. No script-filtered fatal window error/crash. There was a sound engine error under headless CI due to virtual environment, not counted as successful sound output.
- **Limitations:** virtual client was never controlled to open a Minecraft save or type `/npc`; 11/11 GameTests ran in separate actual server environment. The user's own HMCL launch profile remains unverified and this does not supersede their screenshot.
