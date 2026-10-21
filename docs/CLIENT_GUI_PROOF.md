# Client GUI proof gate

- Previous `runClient` workflow stayed alive under Xvfb but did not capture a single visual frame or enter a game world.
- This workflow runs actual **Minecraft 1.21.1 NeoForge** `./gradlew runClient` on GitHub Linux, with Xvfb, Openbox, xdotool and ImageMagick.
- Captures visible Minecraft window from Xvfb and attaches a PNG to CI artifacts, plus full `client-gui.log` and Minecraft latest.log.
- **Do not claim world entered / NPC spawned until screenshot visibly shows the game world and client/server logs show the command/game world.** First run is deliberately just UI capture; follow-up uses the screenshot coordinates to automate menus or can use deterministic Quick Play world.
- This only validates a fresh vanilla + REME dev-client run, **not HMCL / 222-Mod user installation**.
