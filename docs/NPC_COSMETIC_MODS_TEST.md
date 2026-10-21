# Visual mods (NeoForge 1.21.1) — tested

## Actually installed and verified in cloud CI

- [NeoForge graphical test 39568121500](https://github.com/g2066203208-beep/mc-studio/actions/runs/39568121500) **SUCCESS**. Download + SHA verification + launched graphical `./gradlew runClient` with both official mods.
- Actual NeoForge Mods screen explicitly listed **EyeMod 1.1.0** and **3d-Skin-Layers 1.11.4** alongside REME MC Studio. Evidence artifact **12330723021**: `mod/client-cosmetics-mods.png`, original launch logs, version manifest. Third-party JARs not uploaded.
- 3D Skin Layers NeoForge artifact: `skinlayers3d-neoforge-1.11.4-mc1.21.1.jar` (1,905,514 bytes), SHA-512 `541d39edf2781676d9777e3c4a18f179f0b0c04f3ea10d279154996b165e02ae5431100f74f95125ba6df2d7f78aacb9353a02e0c2a436b878e7393788cb0a6b`.
- EyeMod NeoForge artifact: `neoforge_1.21.1_eyemod_1.1.0.jar` (85,425 bytes), SHA-512 `3d6af4a72aa1b6263aa95a0f28ed772be3e70b8f539d63b56300410318a4fb3e820a00fdb92e4d95da3784495932cd034dc4635c90b17a249f18aaf9e89a24e4`.
- Official source: https://modrinth.com/mod/3dskinlayers and https://modrinth.com/mod/eye-mod . We do not redistribute their JARs (3D Skin Layers license explicitly prohibits unauthorized rehosting).
- `scripts/install-appearance-dependencies.py` installs both from official downloads in a GitHub developer test. The Windows `Install-3D-Skin-and-EyeMod.cmd` plus `.ps1` installs onto a user-selected HMCL version's `mods` directory after user explicitly types INSTALL. They do not change saves or unrelated mod JARs and back up old versions.
- The **NPC renderer is `MobRenderer<PlayerNpcEntity, PlayerModel<PlayerNpcEntity>>`**, not `PlayerRenderer`. A Mods list success confirms two mods are installed for player visuals, **not that these mods automatically apply 3D cosmetics to REME NPCs**. Test NPC renderer integration separately; don't falsely claim completion.


## Windows installation genuinely checked (Oct 21 2026)
- [Real Windows runner 39569457048](https://github.com/g2066203208-beep/mc-studio/actions/runs/39569457048) **SUCCESS** after fixing ambiguous filename selection. Actual API downloaded and SHA512-checked both official jars; verified embedded modIds `skinlayers3d` and `eyemod`; tested saving old EyeMod under unusual filename into backup folder and preserved unrelated third-party mod. The test runs in a **simulated HMCL folder**, not the player's real PC.
- Workflow artifact `12330209910` contains ZIP `REME-Visual-Mods-Installer-only.zip` with `Install-3D-Skin-and-EyeMod.cmd` and `.ps1` — scripts only, **no external mod JAR**. The user must fully extract, close the game, double click CMD, choose their current **Minecraft instance game directory**, explicitly type `INSTALL`, and restart HMCL. If missing, do not assume the default .minecraft root due HMCL version isolation.
- [Actual NeoForge graphical test 39568121500](https://github.com/g2066203208-beep/mc-studio/actions/runs/39568121500) SUCCESS. `client-cosmetics-mods.png` visibly lists EyeMod 1.1.0 and 3d-Skin-Layers 1.11.4 alongside REME. The graphical client starts and reaches Mods UI without cosmetic-mod crashes.
- Server GameTests [39569463834](https://github.com/g2066203208-beep/mc-studio/actions/runs/39569463834) and build [39569463750](https://github.com/g2066203208-beep/mc-studio/actions/runs/39569463750) success for final Java/Gradle sources.
- **NOT YET DONE**: visual proof these two mods animate our custom NPC, actual blinking tracked on NPC across frames, or user's HMCL instance + 222-modpack compatibility. Don't claim them.
