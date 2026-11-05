# M0-009 — Blueprint / BuildingRecord

## Goal

Introduce the first versioned blueprint definition and the persistent logical building instance. This is the data foundation for M0-010's structure-template placement adapter.

## Blueprint model

`BlueprintDefinition` is immutable content metadata: ID, schema/version, category, template resource ID, 3D size, material manifest, construction work points, required professions, rotations, ground/fluid rules, protected-block policy and a deterministic SHA-256 checksum of the versioned manifest.

The catalog currently contains `empires:warehouse_prototype`, a 3x3x3 storage prototype. Its template ID is `empires:structures/warehouse_prototype`; the physical template asset is deliberately supplied with M0-010. The checksum in M0-009 covers the blueprint manifest, not yet the physical NBT structure bytes.

## Building record

`BuildingRecord` persists:

- building UUID, blueprint ID/version and owner Faction UUID;
- Settlement UUID, dimension, origin and rotation;
- lifecycle state and construction progress/work-point requirement;
- reserved-material ledger, durability and world-sync state;
- blueprint-manifest checksum, creation game time and record schema.

A newly planned building starts as `PLANNED`, progress `0/workPoints`, empty reserved materials, durability `0`, and `NOT_PLACED` world-sync state.

## Persistence / migration

`EmpiresWorldData.CURRENT_SCHEMA_VERSION` increases from 2 to 3. Schema 1 still follows the previous implicit Schema 1 → 2 citizen migration, then continues to Schema 3. Schema 2 citizen records, simulation clock, factions, settlements and resource ledgers are read as before. Schema 3 adds a `buildings` list. The loader validates faction/settlement ownership, dimension agreement, duplicate IDs and both directions of the Settlement `building_ids` index.

If a record fails validation, the pre-existing read-only fallback preserves the original raw NBT and disables writes. Unknown newer schemas remain read-only.

## Service boundary

`CivilizationService.planBuilding` is the only creation path used by debug commands. It validates active faction/settlement, the settlement dimension, blueprint ID and allowed rotation. It performs a data-only 3D AABB overlap check against known blueprint definitions. It does not inspect world blocks or chunks.

`EmpiresWorldData.putBuilding` validates references and atomically updates the building map and Settlement reverse index on the server thread. Existing building identity, owner, blueprint and placement fields cannot be mutated by re-saving the same UUID.

## Explicit non-goals

No block placement, chunk access, material reservation/debit, job creation, worker simulation, construction progress, building production, or UI is included. These belong to later milestones.
