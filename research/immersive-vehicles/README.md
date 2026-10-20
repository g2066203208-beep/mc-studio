# Immersive Vehicles 1.21.1 actual JAR inspection

Downloaded the **official core mod** and **official content pack** from Modrinth release binaries at CI run time; verified checksums. Extracted the Ford Mustang 1969 OBJ and red PNG texture from inside the **official pack JAR**; rendered the genuine mesh in three views.

- Screenshot: `mustang_jar_model_inspection.png`
- Reproduction: `python3 research/immersive-vehicles/inspect_official_pack.py` (requires Pillow and network access)
- Source metadata and SHA256: `jar_inspection.json`
- Important: These are **offline mesh previews, not running-Minecraft or Blender GUI screenshots**. Removable wheels, seats, and other separately mounted content parts are not assembled by this renderer.
- Copyright: Original models, textures and jars are the creators' assets; binaries and original assets are **not republished** here.
