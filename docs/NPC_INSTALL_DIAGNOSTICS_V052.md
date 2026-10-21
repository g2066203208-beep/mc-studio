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
