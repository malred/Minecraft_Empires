# Changelog — M0-009 Blueprint / BuildingRecord v0.1.0

- Added the immutable `BlueprintDefinition` model and built-in `BlueprintCatalog`.
- Added one starter 3x3x3 warehouse prototype blueprint, including material manifest, construction work points, placement flags, allowed rotations and a SHA-256 blueprint-manifest checksum.
- Added `BuildingRecord`, lifecycle states and world synchronization states.
- Upgraded `EmpiresWorldData` schema 2 → 3 with building NBT persistence and cross-reference validation.
- Added repository/service accessors and an atomic Settlement building-ID reverse index update.
- Added OP-only blueprint/building diagnostic commands.
- Added a combined M0-009 acceptance guide with M0-008 remote simulation regression checks.
- No actual block placement, worker assignment, material reservation/debit, or production behavior is introduced.
