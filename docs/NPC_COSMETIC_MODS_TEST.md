# Visual mods (NeoForge 1.21.1) — tested

## Actually installed and verified in cloud CI

- [NeoForge graphical test 39568121500](https://github.com/g2066203208-beep/mc-studio/actions/runs/39568121500) **SUCCESS**. Download + SHA verification + launched graphical `./gradlew runClient` with both official mods.
- Actual NeoForge Mods screen explicitly listed **EyeMod 1.1.0** and **3d-Skin-Layers 1.11.4** alongside REME MC Studio. Evidence artifact **12330723021**: `mod/client-cosmetics-mods.png`, original launch logs, version manifest. Third-party JARs not uploaded.
- 3D Skin Layers NeoForge artifact: `skinlayers3d-neoforge-1.11.4-mc1.21.1.jar` (1,905,514 bytes), SHA-512 `541d39edf2781676d9777e3c4a18f179f0b0c04f3ea10d279154996b165e02ae5431100f74f95125ba6df2d7f78aacb9353a02e0c2a436b878e7393788cb0a6b`.
- EyeMod NeoForge artifact: `neoforge_1.21.1_eyemod_1.1.0.jar` (85,425 bytes), SHA-512 `3d6af4a72aa1b6263aa95a0f28ed772be3e70b8f539d63b56300410318a4fb3e820a00fdb92e4d95da3784495932cd034dc4635c90b17a249f18aaf9e89a24e4`.
- Official source: https://modrinth.com/mod/3dskinlayers and https://modrinth.com/mod/eye-mod . We do not redistribute their JARs (3D Skin Layers license explicitly prohibits unauthorized rehosting).
- `scripts/install-appearance-dependencies.py` installs both from official downloads in a GitHub developer test. The Windows `Install-3D-Skin-and-EyeMod.cmd` plus `.ps1` installs onto a user-selected HMCL version's `mods` directory after user explicitly types INSTALL. They do not change saves or unrelated mod JARs and back up old versions.
- The **NPC renderer is `MobRenderer<PlayerNpcEntity, PlayerModel<PlayerNpcEntity>>`**, not `PlayerRenderer`. A Mods list success confirms two mods are installed for player visuals, **not that these mods automatically apply 3D cosmetics to REME NPCs**. Test NPC renderer integration separately; don't falsely claim completion.
