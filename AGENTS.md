# Minecraft workspace instructions

- Respond to this user in Chinese. Own implementation, debugging, version management, research and validation; minimize manual technical steps for the user.
- Read README.md, docs/STATUS.md and docs/HANDOFF.md before modifying a project. Read local/environment.json only when present; cloud checkouts do not have it.
- GitHub is the shared source of truth once the dedicated remote is connected. Keep code, research, task state and validation evidence in the repository so desktop and web chats can continue each other's work.
- In cloud/Linux environments use Java 21 and `bash scripts/build.sh`; never require the user's Windows paths or game installation for compilation.
- Before editing inspect the current branch, HEAD and worktree. Pull/fetch only when Git authentication is available; do not overwrite another chat's uncommitted changes. Use a task branch, update docs/HANDOFF.md and deliver a commit/PR with validation results.
- Default provisional baseline: Minecraft 1.21.1, NeoForge 21.1.248, Java 21. Do not silently switch loaders or upgrade the pack to the latest release.
- Use the checked-in Gradle wrapper and explicitly selected JDK. Keep machine-specific paths in ignored local/ configuration.
- Never treat the discovered instance path as proof of the launcher's active game directory. Verify isolation and logs before deployment.
- Do not use real saves for destructive or experimental tests. Back up affected files and saves before upgrades; use separate development and integration instances.
- Prefer configuration, data packs or KubeJS for appropriate pack changes; use Java add-ons when API integration is required; modify upstream only with a concrete reason.
- Record upstream repository, commit, target branch, license and attribution before reusing source. Preserve original license obligations.
- Keep source, configuration authored for the project, documentation and CI in Git. Exclude credentials, account databases, complete third-party modpacks, world saves, raw logs and downloaded Minecraft artifacts.
- A successful build is not runtime validation. Report separate build, client, dedicated server, full-pack compatibility and performance results, with explicit untested items.
- Use feature/fix branches and small meaningful commits. Do not push to an unrelated existing repository. The dedicated private remote is https://github.com/g2066203208-beep/mc-studio.
- Performance work requires reproducible before/after measurements under comparable workloads.
- No auto-update or scheduled monitoring has been configured. Do not claim unattended background work.

## Vehicle modeling Skill

- When the user requests a model inspired by Immersive Vehicles, Blockbench/MTS vehicle construction, a motorcycle, UV atlas, functional vehicle parts, or a real 3D web preview, load `skills/immersive-vehicles-modeling/SKILL.md` first, then the relevant references.
- Inspect *real OBJ, PNG, and vehicle JSON* and distinguish source-repo assets from actual released JARs. Never pass off generated illustrations or flat screenshots as rotatable 3D models.
- Run the bundled OBJ inspector and unit tests before claiming the model or new skill validated. Keep unverified game-runtime facts clearly marked.
