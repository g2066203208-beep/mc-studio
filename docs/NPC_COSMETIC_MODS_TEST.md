# Player cosmetics trial: 3D Skin Layers + DynamicEyes EyeMod

Scope: Minecraft Java 1.21.1 / NeoForge 21.1.248, REME NPC v0.5.4 branch.

- Official pages: https://modrinth.com/mod/3dskinlayers and https://modrinth.com/mod/eye-mod
- 3D Skin Layers by tr7zw, **tr7zw Protective License**: author permits Modrinth/CurseForge hosted modpacks but says **do not redistribute JAR files elsewhere**. This repo / downloaded release / GitHub artifact MUST NOT include this third-party JAR.
- EyeMod by aKyBeFf: Modrinth labels project MIT, CurseForge labels All Rights Reserved. To avoid conflicting license assumptions we also do **not redistribute EyeMod's JAR**, only provide script that downloads from its official Modrinth CDN.
- Tested game/loader filter strictly 1.21.1 + neoforge. Pin official published releases: Skin Layers 1.11.4, EyeMod 1.1.0; actual version IDs, filenames, hashes are always queried at install time and SHA512/SHA1 verified before copying.
- Installer script: `python3 scripts/install-appearance-dependencies.py --mods-dir mod/run/mods --report visual-dependencies.json` for GitHub dev launcher. To install in a **user-controlled** HMCL test instance with Python available, change the path to that instance's actual `mods` directory. Must close Minecraft first. No claims of direct access to the user's PC.
- In CI `runClient` under Xvfb then click official NeoForge Mods page and capture true game screenshots and log. Third-party mod JARs remain inside the CI working directory and are never uploaded as artifact.
- **Important limitation**: 3D Skin Layers and EyeMod are *player renderers/skin cosmetics*, not guaranteed to apply to the custom `PlayerNpcEntity` that currently uses `MobRenderer<PlayerNpcEntity, PlayerModel<PlayerNpcEntity>>`. Loading these two mods is step 1. Do NOT claim NPC 3D outer layers or animated eyes until actual custom-NPC visual integration and screenshots prove them.
