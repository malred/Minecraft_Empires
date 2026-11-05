# M0-010 — Block Placement Adapter

## Scope

M0-010 proves the path `BlueprintDefinition → BlueprintTemplate → BuildingRecord → real blocks`. It does not implement M1 resource escrow, worker jobs, or construction progress.

## Template format

Official template resource:

`data/empires/structures/warehouse_prototype.json`

The file declares schema version, ResourceLocation ID, 3D size, a block-ID palette, local block coordinates, palette indexes, and a SHA-256 checksum. Template validation rejects mismatched IDs/dimensions, duplicate coordinates, out-of-bounds entries, invalid palette indexes, excessive volume, and checksum mismatch. Only block IDs/default states are supported in this milestone; no arbitrary BlockEntity NBT is deserialized.

## Placement flow

1. OP invokes `/empires debug place-building <buildingUUID>`.
2. Service verifies the record is `PLANNED / NOT_PLACED`, dimension and blueprint version match, and the saved blueprint manifest checksum matches the current definition.
3. It loads and validates the template, then preflights all target cells. All target/support chunks must already be loaded; every target must be replaceable under the blueprint policy; height, world-border, fluid, BlockEntity, and solid-ground rules are checked.
4. The record changes to `worldSyncState=PENDING`, with cursor 0. This change is saved before any block is placed.
5. A server END-tick scheduler places up to `buildingPlacementBlocksPerTick` blocks per tick. Cursor advances only after the desired block is present.
6. A replayed position already containing the desired state is treated as complete, making crash replay idempotent.
7. When the cursor reaches the template block count, the record becomes `worldSyncState=SYNCED`. The logical building remains `PLANNED`; construction completion and material escrow are later milestones.

## Safe world access

- Uses `hasChunkAt` before `getBlockState`, `getBlockEntity`, or `setBlock`.
- Does not call `getChunk`, request a ChunkTicket, or force-load any chunk.
- If a pending target chunk is no longer loaded, processing pauses without mutating that batch.
- A non-replaceable/protected obstruction or failed setBlock moves the record to `NEEDS_REPAIR`; the adapter does not overwrite it.
- `/empires debug resume-building-placement <buildingUUID>` can resume a repairable placement after the user clears the obstruction and reloads all required chunks.

## Persistence

`BuildingRecord` nested schema increments from 1 to 2 to persist `placement_cursor`. Schema-1 records load with cursor 0. `EmpiresWorldData` root schema stays 3, and it marks the SavedData dirty when a nested record migration is detected.

## Current limits

- One official template only.
- JSON palette supports block IDs/default states, not block properties or BlockEntity NBT.
- Only the current loaded-dimension placement path is implemented.
- M0-010 does not consume/reserve materials and does not advance construction work points.
- Player edits after successful placement are not automatically repaired or overwritten.
