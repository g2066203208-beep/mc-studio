#!/usr/bin/env python3
"""Install genuine *released* NeoForge 1.21.1 jars into CI-only run/mods.

Do not ship, vendor, repackage or redistribute third-party binaries.
All three primary versions are pinned by official Modrinth version IDs;
the API hashes of EVERY downloaded file are checked before use.
"""
import hashlib
import json
import pathlib
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

API = "https://api.modrinth.com/v2"
GAME = "1.21.1"
LOADER = "neoforge"
PINNED = {
    "Sable 2.0.6": "fg9dTRz9",
    "Create 6.0.10": "UjX6dr61",
    "Create Aeronautics 1.3.2": "44pLdPGg",
}
MAX_MODS = 28


def get(path):
    url = API + path
    last = None
    for attempt in range(4):
        try:
            req = urllib.request.Request(url, headers={
                "User-Agent": "WorldSeam-Real-Mod-Integration-CI/0.13 (GitHub Actions)",
                "Accept": "application/json",
            })
            with urllib.request.urlopen(req, timeout=30) as response:
                return json.loads(response.read())
        except (OSError, ValueError) as exc:
            last = exc
            time.sleep(2 * (attempt + 1))
    raise RuntimeError(f"Modrinth metadata fetch failed: {url}: {last}")


def compatible(version):
    return GAME in version["game_versions"] and LOADER in version["loaders"]


def pick_project_release(project):
    q = urllib.parse.quote(json.dumps([LOADER]), safe="")
    g = urllib.parse.quote(json.dumps([GAME]), safe="")
    versions = get(f"/project/{urllib.parse.quote(project)}/version?loaders={q}&game_versions={g}")
    candidates = [v for v in versions if compatible(v) and v["version_type"] == "release"]
    if not candidates:
        raise RuntimeError(f"No pinned-compatible NeoForge 1.21.1 release for required project {project}")
    return max(candidates, key=lambda v: v["date_published"])


def select_primary(version):
    files = [f for f in version["files"] if f["filename"].endswith(".jar")
             and not re.search(r"(sources|dev|javadoc)", f["filename"], re.I)]
    if len(files) == 0:
        raise RuntimeError("No real binary mod JAR in " + version["id"])
    return next((f for f in files if f.get("primary")), files[0])


def install(out):
    out.mkdir(parents=True, exist_ok=True)
    all_versions = [get("/version/" + ident) for ident in PINNED.values()]
    project_set = {v["project_id"] for v in all_versions}
    version_set = {v["id"] for v in all_versions}
    manifest = []
    index = 0
    while index < len(all_versions):
        if len(all_versions) > MAX_MODS:
            raise RuntimeError("Unbounded third-party dependency graph")
        version = all_versions[index]
        index += 1
        if not compatible(version):
            raise RuntimeError("Wrong loader or game version: " + version["id"])
        asset = select_primary(version)
        source = urllib.parse.urlparse(asset["url"])
        if source.scheme != "https" or not (
                source.hostname == "modrinth.com" or
                (source.hostname or "").endswith(".modrinth.com")):
            raise RuntimeError("Non-Modrinth binary source rejected: " + asset["url"])
        sha256 = asset.get("hashes", {}).get("sha256")
        if not sha256 or not re.fullmatch(r"[a-f0-9]{64}", sha256):
            raise RuntimeError("No official sha256 provided for " + asset["filename"])
        name = str(index).zfill(2) + "-" + re.sub(r"[^a-zA-Z0-9_.-]", "_", asset["filename"])
        dest = out / name
        req = urllib.request.Request(asset["url"], headers={
            "User-Agent": "WorldSeam-Real-Mod-Integration-CI/0.13"})
        h = hashlib.sha256()
        with urllib.request.urlopen(req, timeout=90) as remote, dest.open("wb") as local:
            while True:
                chunk = remote.read(1024 * 1024)
                if not chunk:
                    break
                local.write(chunk)
                h.update(chunk)
                if local.tell() > 60 * 1024 * 1024:
                    raise RuntimeError("Oversize untrusted mod jar: " + name)
        if h.hexdigest() != sha256:
            dest.unlink(missing_ok=True)
            raise RuntimeError("Downloaded mod checksum mismatch: " + name)
        manifest.append({
            "version_id": version["id"], "project_id": version["project_id"],
            "title": version["name"], "filename": name, "sha256": sha256,
        })
        print("WORLDSEAM_REAL_RELEASE_VERIFIED", version["name"], name, sha256, flush=True)
        for dep in version.get("dependencies", []):
            if dep.get("dependency_type") != "required":
                continue
            project = dep.get("project_id")
            if project and project in project_set:
                continue
            ident = dep.get("version_id")
            dependency = get("/version/" + ident) if ident else (
                pick_project_release(project) if project else None)
            if dependency is None:
                raise RuntimeError("Unresolvable required mod dependency")
            if dependency["id"] not in version_set:
                all_versions.append(dependency)
                version_set.add(dependency["id"])
                project_set.add(dependency["project_id"])
    (out.parent / "real-mod-versions.json").write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False) + "\n")
    print(f"WORLDSEAM_REAL_MODS_INSTALLED: {len(manifest)} genuine verified NeoForge jars")


if __name__ == "__main__":
    install(pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "run/mods"))
