#!/usr/bin/env python3
"""Fail builds that omit the actual 1.21.1 NeoForge commands or misdeclare dependencies.

This is a JAR inspection, NOT proof that a particular HMCL instance loaded it.
For real command execution refer to npc-command-client-smoke.yml.
"""
from pathlib import Path
import re
import sys
import zipfile

jar = Path(sys.argv[1])
with zipfile.ZipFile(jar) as z:
    names = set(z.namelist())
    metadata = z.read("META-INF/neoforge.mods.toml").decode("utf-8")
    must_have = {
        "dev/reme/mcstudio/StudioMod.class",
        "dev/reme/mcstudio/npc/NpcCommands.class",
        "dev/reme/mcstudio/npc/NpcItems.class",
        "dev/reme/mcstudio/npc/PlayerNpcEntity.class",
        "data/reme_studio/recipe/npc_spawn_egg.json",
        "assets/reme_studio/lang/zh_cn.json",
    }
    missing = must_have.difference(names)
    assert not missing, f"Required code/resources absent from release JAR: {sorted(missing)}"
    assert re.search(r'(?m)^modId="reme_studio"$', metadata), metadata
    assert 'version="0.7.2-dev"' in metadata, metadata
    assert 'versionRange="[21.1.262,)"' in metadata, metadata
    assert 'versionRange="[1.21.1]"' in metadata, metadata
    assert "spawn_egg" in z.read("data/reme_studio/recipe/npc_spawn_egg.json").decode()
print(f"PASS: JAR packaged real server command class, actual spawn egg and NeoForge 21.1.262+ dependencies: {jar}")
