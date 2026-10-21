#!/usr/bin/env python3
"""Pure source/package checks: does not pretend to run Windows PowerShell or HMCL."""
from pathlib import Path
import zipfile
import re
import sys

root = Path(__file__).resolve().parents[1]
ps = (root / "packaging" / "Install-REME-NPC.ps1").read_text(encoding="utf8")
cmd = (root / "packaging" / "Install-REME-NPC.cmd").read_text(encoding="utf8")
assert 'Read-ModId' in ps and 'META-INF/neoforge.mods.toml' in ps
assert 'REME-backup-' in ps and 'Move-Item' in ps and 'Get-FileHash' in ps
assert '"INSTALL"' in ps and 'Install-REME-NPC.ps1' in cmd
assert "-STA" in cmd and "NoProfile" in cmd
assert "if ($duplicates.Count -ne 1)" in ps
if len(sys.argv) > 1:
    jar = Path(sys.argv[1])
    with zipfile.ZipFile(jar) as f:
        mods = f.read("META-INF/neoforge.mods.toml").decode()
        assert re.search(r'(?ms)\[\[mods\]\]\s*modId\s*=\s*"reme_studio"', mods)
        assert 'NpcCommands.class' in " ".join(f.namelist())
print("Installer static checks pass; Windows GUI installation still requires Windows/HMCL acceptance.")
