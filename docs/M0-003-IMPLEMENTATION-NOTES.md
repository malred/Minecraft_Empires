# M0-003 implementation notes

## Forge 1.20.1 API choice

The global registry is attached through `server.overworld().getDataStorage().computeIfAbsent(load, create, DATA_NAME)`. The data type extends `SavedData`, implements `save(CompoundTag)`, and calls `setDirty()` on every successful mutation. See the 1.20.1 `DimensionDataStorage` and `SavedData` API references in the project reference list.

## Data invariants

- One global registry per save, always stored in the Overworld data storage.
- `worldId` is stable for the lifetime of the save.
- Faction IDs and Settlement IDs are UUIDs.
- Resource IDs and dimension IDs are ResourceLocations.
- Resource quantities are non-negative `long` values.
- A settlement must refer to an existing owner faction.
- A faction capital reference must refer to an active settlement owned by that faction.
- The world registry is the source of truth; command/UI/entity code uses services and repositories.

## Read-only safety

If the saved schema is newer than this code or record validation fails, the loader preserves the original root NBT payload, logs an error and returns a read-only view. Service writes are rejected. This is a fail-closed MVP strategy; a separate backup/repair UI and schema migration chain are not included yet.

## Scope intentionally deferred

- Persistent player UUID ownership and access-control roles.
- Citizen records and entity lifecycle.
- AI strategic simulation.
- Building records and physical structure placement.
- Actual species registry/data-pack validation.
- Network packets and client UI.
