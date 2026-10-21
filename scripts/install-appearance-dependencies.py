#!/usr/bin/env python3
"""Install client-only visual mods from official Modrinth CDN into a local test instance.

No third-party JAR is copied into this repository, uploaded as a CI artifact, or bundled
with REME. All downloaded hashes are checked against Modrinth's authoritative metadata.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import sys
import urllib.parse
import urllib.request
import zipfile

MC = "1.21.1"
LOADER = "neoforge"
MODS = (
    ("3D Skin Layers", "3dskinlayers", "1.11.4", "skinlayers"),
    ("DynamicEyes (EyeMod)", "eye-mod", "1.1.0", "eyemod"),
)
HEADERS = {"User-Agent": "REME-Minecraft-NPC-visual-mod-compatibility-test/1.0 (GitHub)"}


def get_bytes(url):
    req = urllib.request.Request(url, headers=HEADERS)
    with urllib.request.urlopen(req, timeout=90) as response:
        return response.read()


def resolve(project, release):
    qs = urllib.parse.urlencode({
        "loaders": json.dumps([LOADER]),
        "game_versions": json.dumps([MC]),
    })
    url = f"https://api.modrinth.com/v2/project/{project}/version?{qs}"
    versions = json.loads(get_bytes(url))
    candidates = [v for v in versions
                  if v.get("version_number", "").split("+")[0] == release
                  and v.get("version_type") == "release"
                  and MC in v.get("game_versions", [])
                  and LOADER in v.get("loaders", [])]
    if not candidates:
        raise RuntimeError(f"No verified {MC}/{LOADER} version {release} for {project}")
    version = candidates[0]
    required = [d for d in version.get("dependencies", [])
                if d.get("dependency_type") == "required"]
    if required:
        raise RuntimeError(
            f"{project} requires extra dependencies; resolve explicitly before test: {required}"
        )
    files = [f for f in version["files"] if f["filename"].endswith(".jar")]
    preferred = [f for f in files if f.get("primary")]
    if len(preferred) == 1:
        f = preferred[0]
    elif len(files) == 1:
        f = files[0]
    else:
        raise RuntimeError(f"Ambiguous jar candidates for {project}")
    parsed = urllib.parse.urlparse(f["url"])
    if parsed.scheme != "https" or parsed.hostname not in (
        "cdn.modrinth.com", "cdn-raw.modrinth.com", "github.com",
        "objects.githubusercontent.com",
    ):
        raise RuntimeError(f"Refusing non-official download URL {parsed.hostname}")
    return version, f


def install(target, name, project, release, stem):
    version, f = resolve(project, release)
    filename = f["filename"]
    dest = target / filename
    if "/" in filename or "\\" in filename:
        raise RuntimeError(f"Untrusted filename: {filename}")
    payload = get_bytes(f["url"])
    if "size" in f and len(payload) != f["size"]:
        raise RuntimeError(f"Wrong size for {filename}")
    for algorithm in ("sha512", "sha1"):
        expect = f.get("hashes", {}).get(algorithm)
        if not expect:
            raise RuntimeError(f"Modrinth missing {algorithm} hash for {filename}")
        actual = hashlib.new(algorithm, payload).hexdigest()
        if actual != expect:
            raise RuntimeError(f"Hash mismatch for {filename}: {algorithm}")
    # Don't install an HTML error page masquerading as a JAR.
    import io
    with zipfile.ZipFile(io.BytesIO(payload)) as jar:
        names = set(jar.namelist())
        if not ("META-INF/neoforge.mods.toml" in names or
                "META-INF/mods.toml" in names or
                "META-INF/jarjar/metadata.json" in names):
            raise RuntimeError(f"Missing NeoForge metadata: {filename}")
    # Conservative: no deletion of other mods. Fail on an existing differently
    # named JAR from the same cosmetic mod rather than creating a duplicate.
    matches = [x for x in target.glob("*.jar") if stem in x.name.lower()]
    unexpected = [x.name for x in matches if x.name != filename]
    if unexpected:
        raise RuntimeError(f"Remove or back up duplicate versions first: {unexpected}")
    target.mkdir(parents=True, exist_ok=True)
    tmp = target / (filename + ".partial")
    tmp.write_bytes(payload)
    tmp.replace(dest)
    summary = {
        "name": name,
        "version": version["version_number"],
        "project": project,
        "file": filename,
        "sha512": hashlib.sha512(payload).hexdigest(),
        "size": len(payload),
        "source": f"https://modrinth.com/mod/{project}",
    }
    print("INSTALLED_VISUAL_MOD " + json.dumps(summary, ensure_ascii=False))
    return summary


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--mods-dir", required=True)
    ap.add_argument("--report", default="")
    args = ap.parse_args()
    target = Path(args.mods_dir).expanduser().resolve()
    print(f"TEST_INSTANCE_MODS_DIR={target}")
    installed = [install(target, *mod) for mod in MODS]
    if args.report:
        Path(args.report).write_text(json.dumps(installed, indent=2, ensure_ascii=False))
    print(f"VISUAL_MODS_VERIFIED_COUNT={len(installed)}")


if __name__ == "__main__":
    main()
