# Immersive Vehicles · Real 3D Model Viewer

This is a working, browser-side **Three.js OBJ viewer** for the official Minecraft Immersive Vehicles content-pack Ford Mustang 1969.

## Open

- Planned GitHub Pages URL: https://g2066203208-beep.github.io/mc-studio/
- Source HTML: https://github.com/g2066203208-beep/mc-studio/blob/main/viewer/index.html
- Deployment workflow: https://github.com/g2066203208-beep/mc-studio/actions/workflows/deploy-viewer.yml

**GitHub Pages activation may still be necessary.** Go to repository **Settings → Pages → Build and deployment → Source: GitHub Actions**. In a private GitHub repository, GitHub Pages may require a paid GitHub plan. Do not change the repo to public without the owner's approval.

## Real 3D sources

1. **Default online view**: Load the OBJ and texture **directly from the original creator's public repository** (`DonBruce64/MTSOfficialPack`). Three.js `OBJLoader` parses the actual triangle/quad geometry in the browser; the vehicle can be orbited, zoomed and inspected.
2. **Official release V29 via browser**: Click "在线打开真正的 V29 JAR". The browser downloads the actual official content pack release (approximately 71 MB) from Modrinth and JSZip extracts the OBJ and the 512 × 512 vehicle texture; no model files or whole JAR are mirrored on this site.
3. **Local JAR**: Import `MTS Official Pack-1.21.1-V29.jar` to inspect a known local version, entirely in-browser and without uploading files.

Features: full 3D orbit camera, front / right / top / three-quarter views, selectable named parts, per-part visibility toggle, overlay hiding, paint variants, textured/wireframe toggle, screenshot export, responsive mobile support.

## Technical and copyright notes

- All model and texture credits belong to their respective creators. This repo hosts viewer **code**; it does not republish official vehicles or game-pack binaries.
- The default online source is a public development repository version, not necessarily byte-identical to V29. Use the online-JAR or local-JAR mode for exact release model inspection.
- Loose wheels, seats, and other vehicle-part models are attached at runtime by the game; this standalone body-OBJ viewer does **not** falsely display them as though they were in the body OBJ.
- This is real WebGL 3D model inspection, but **not** a screenshot of a running Minecraft client.
- Three.js and JSZip load from jsDelivr CDN; internet access is required to launch viewer.

## Deployment verification

GitHub Pages deployment re-triggered after repository visibility / Pages settings were updated (2026-10-21). Check the [deployment workflow](https://github.com/g2066203208-beep/mc-studio/actions/workflows/deploy-viewer.yml) and its green success indicator before treating the public URL as live.
