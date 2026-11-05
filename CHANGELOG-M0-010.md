# Changelog — M0-010

## v0.1.0
- Added data-pack JSON blueprint template loading and SHA-256 integrity validation.
- Added a 3×3×3 warehouse prototype structure resource (25 blocks).
- Added loaded-chunk-only, server-authoritative block placement with a configurable per-tick budget.
- Added persistent placement cursor and nested BuildingRecord schema 1 → 2 migration.
- Added placement, resume, and status debug commands.
- Added preflight checks for dimension, build height, world border, loaded chunks, solid ground, fluids, block entities, and protected/non-replaceable blocks.
- Placement pauses on unloaded chunks and enters NEEDS_REPAIR on unsafe conflicts; no automatic force-loading or overwrite of protected blocks.
- Preserved world SavedData schema 3 and M0-008 simulation behavior.
