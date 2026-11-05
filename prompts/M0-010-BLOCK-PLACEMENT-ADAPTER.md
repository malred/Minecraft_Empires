# Agent context — M0-010 Block Placement Adapter

Target: Minecraft 1.20.1 / Forge 47.4.28 / Java 17. Base: verified M0-009 patch.

Implement a server-authoritative adapter from BlueprintDefinition to real blocks. Use the official 3×3×3 warehouse JSON template. Validate resource schema/ID/dimensions/palette indexes/unique coordinates/SHA-256 before any world mutation. Validate building identity/version/manifest checksum, current dimension, build height, world border, already-loaded chunks, solid support, fluid rules, BlockEntity safety, and ProtectedBlocksPolicy.

All world access stays on the logical server thread. Check `hasChunkAt` before touching any position. Never use `getChunk`, tickets, or force-loading. Persist a monotonic cursor in BuildingRecord; process no more than the configured global per-tick block budget. Cursor may advance only after the desired state is present. If a replay sees the expected block already present, treat it as done. Unloaded chunks pause; unsafe conflicts become NEEDS_REPAIR and are never overwritten.

Keep world SavedData schema 3. Migrate nested BuildingRecord schema 1 to 2 with cursor 0 and mark root SavedData dirty. Do not reserve or deduct materials, spawn workers, or claim construction is complete. Keep the building state PLANNED when worldSync becomes SYNCED.

Preserve and regression-test M0-008. Do not alter its temporary wood diagnostic, simulation clock, DATA_ONLY behavior, or no-offline-production policy. Report all tests as PASS/FAIL/NOT RUN and never claim an unrun Forge client/server test passed.
