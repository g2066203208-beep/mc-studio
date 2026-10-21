# Scope instructions — Seamless Dimensions only

This directory is owned by the seamless-dimensions effort. Its purpose is visual and physical continuity across vertically stacked Minecraft dimensions.

- Work only under `projects/seamless-dimensions/**` and a dedicated `.github/workflows/seamless-dimensions-*.yml` when absolutely necessary.
- Do not edit `mod/**`, `viewer/rpms/**`, `viewer/**`, existing Create/Aeronautics/Sable code, or NPC code. Do not reuse their artifact names, source packages, mod ID, screenshot evidence, or test reports.
- Build an independent NeoForge Gradle project with unique mod ID and package. Shared version baseline: Minecraft 1.21.1, NeoForge 21.1.248, Java 21. Verify mod dependencies independently.
- Before each PR, list changed paths and reject unrelated paths; collect build, GameTest, real-client rendering, neighboring-dimension chunk streaming and performance evidence separately.
- Treat cross-dimension movement and cross-dimension *rendering* as distinct deliverables. Never claim the immersive rendering or chunk lifecycle bug fixed without actual client evidence.
- Never touch user worlds/saves or install into a real launcher profile without explicit scoped backup and verification.
- Report current limitations plainly. Do not claim success based on conceptual Three.js visualization or other projects' GameTests.
