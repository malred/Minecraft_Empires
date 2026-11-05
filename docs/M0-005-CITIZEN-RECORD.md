# M0-005 — Logical Citizen Records

## Goal
Establish persistent citizen identity before any NPC entity, pathfinding, work AI, or rendering is introduced.

## Invariants
1. A citizen exists as data independently of any loaded or unloaded Minecraft entity.
2. Every citizen belongs to exactly one existing faction.
3. A citizen's optional home settlement must exist and belong to the same faction.
4. Species is copied from the faction at citizen creation; later faction edits do not silently rewrite citizen identity.
5. A citizen UUID is stable across save/reload.
6. World persistence is server-authoritative and stored once in Overworld SavedData.
7. Unknown or malformed saved data must not be silently overwritten.

## Record schema

- `id`: UUID, immutable
- `owner_faction_id`: UUID, immutable ownership reference
- `home_settlement_id`: optional UUID
- `display_name`: trimmed, 1–32 characters
- `species_id`: ResourceLocation
- `profession_id`: ResourceLocation; initially `empires:unassigned`
- `status`: `ACTIVE`, `MISSING`, `DECEASED`, `RETIRED`
- `created_at_game_time`: non-negative world game time
- `schema_version`: citizen record schema 1

## World schema migration

World registry schema advances from 1 to 2. Schema 1 contains factions and settlements but no citizens. Loading schema 1 builds the same records, starts with an empty citizen map, marks SavedData dirty, and writes schema 2 at the next normal save. Schema versions greater than 2 or malformed data are preserved read-only.

## Population cache

`FactionRecord.populationCountCache` is updated when a logical citizen is created. It counts `ACTIVE` citizen records. Later service operations that change citizen status or faction ownership must update this cache in the same server-thread operation.

## Entity boundary

No entity UUID is stored in this milestone. M0-006/007 will add a separate runtime entity lifecycle/registry that maps a logical Citizen UUID to a currently loaded Minecraft entity UUID. Entity absence must never delete the CitizenRecord.
