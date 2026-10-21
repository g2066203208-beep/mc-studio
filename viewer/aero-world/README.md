# AeroWorld 0.1 · 可玩的网页方块沙盒

Expected entry: https://g2066203208-beep.github.io/mc-studio/aero-world/

## Implemented
- Actual WebGL 3D voxel world, mouse-look, WASD, jump, flying, editable blocks and simple collisions.
- Browser creative palette with mod registry IDs, hotbar, mouse wheel and number keys.
- Seeded terrain, engineering workbench using genuine registered Create/Simulated block identifiers. No copied block assets.
- Browser autosave and JSON import/export (browser JSON is NOT Minecraft save data).
- JAR local upload accepts user-owned Minecraft Java 1.21.1, Create 6.0.10 and Aero 1.3.2 resource archives; parses common blockstates, parent models, texture faces and pixel filtering. Complex dynamic BER and multipart may differ from MC.
- Chrome real-browser game testing: viewer/aero-world/tests/smoke.cjs.

## Not implemented, no false claims
This website does NOT execute NeoForge Java, original Minecraft/other Mod game code, complete Create mechanical stress/speed logic, native Sable physics, Aeronautics ship assembly, rotating native bearing constraints, inventories and crafting, or multiplayer synchronization. GitHub Pages is static. To provide exact real MC + Mods gameplay in the browser, a real licensed Minecraft client running with the actual binaries must be hosted on a remote GPU/CPU instance, with its video/controls streamed via WebRTC into a GitHub Pages frontend. This requires infrastructure, client/game rights, authentication and protected world persistence, not just webpage code. No such streaming backend is presently deployed.

The current fallback pixel art is original and procedurally generated; Minecraft/Create/Aeronautics licensed textures are only read locally from user-uploaded JARs in the browser, never shipped with this repository.
