# v0.5.1 runtime failure investigation
- User reports v0.5 does nothing in actual client. Previous v0.5 evidence was GitHub compile and 7 automated server scenarios; **never a human client session**.
- Found natural placement predicate excluded CHUNK_GENERATION, which blocks passive resident candidates in newly generated world chunks. Now allow NATURAL + CHUNK_GENERATION with surface checks.
- Increase ultra-rare biome spawn weight 1 -> 12; retain small local cap for NATURAL. Chunk-generation spread requires separate long-world population cap validation.
- Navigation cooldown previous implementation returned true even when no path and deferred path attempts; now skips replan only if navigation already has an active path.
- New real-server tests assert biome modifier applied to plains, allow chunk-generation reason, and direct navigation moves an NPC across a constructed test lane.
- Demo NPCs still require actual farmland and crafting materials, so a demo spawn is NOT a ready-made working village.
- Added GitHub Actions virtual-display client startup smoke test attempt. Successful client initialization only proves startup, not normal gameplay or modpack compatibility.
