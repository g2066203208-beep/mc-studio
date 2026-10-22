# RPMotion — system-only Create Aeronautics locomotion controller

This is NOT an Aero block or model port. We do not register any motors, joints, items, blocks, textures or fake physics. Real mechanical building is done solely using **the official Create Aeronautics blocks**.

Mandatory separately installed dependencies: Minecraft 1.21.1 + NeoForge 21.1.248, Create 6.0.10, Create Aeronautics bundled 1.3.2, Sable 2.0.6.

The CI downloads their real official released jars via Modrinth Maven (IDs oWaK0Q19/44pLdPGg, LNytGWDc/UjX6dr61, T9PomCSv/fg9dTRz9). Nothing is embedded or copied into our rpmotion jar. The mod requires original mods through neoforge.mods.toml and will fail to load if they are missing.

G1 real-game experiments:
* Place original simulated:swivel_bearing and verify original native BlockEntity via runtime identity.
* Invoke the original BlockEntity's public assemble() to create native Sable physical sublevel from an actual Minecraft block.
* Assert its real UUID and native swivel plate; no fake animations or synthetic bearing.
* Verify RPMotion registers ZERO replacement blocks; JUnit checks motion intent validation.

Use: bash mod/gradlew -p mods/rpmotion inspectInstalledMods build runGameTestServer --no-daemon --console plain

G1 does not claim proper real shaft-power load, motor torque, stable 12-joint gait or four-legged autonomous standing. Those are G2–G4 and must have real physical GameTests.
