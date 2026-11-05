# M0-006 / M0-007 — Citizen Entity and Lifecycle Specification

## Goal

Create a real Minecraft entity for an already-persisted logical citizen while keeping the logical citizen record authoritative. Verify entity uniqueness, chunk persistence, death handling, and explicit hibernate/respawn.

## Data ownership

`CitizenRecord` remains the authoritative identity record. It now carries an optional `entityUuid` field. This field means "the entity UUID reserved for this citizen", not "currently loaded".

`CitizenEntity` carries the logical `citizenId`, `factionId`, `displayName`, and `speciesId` in synchronized entity data and entity NBT. Its vanilla chunk persistence is responsible for preserving the in-world entity.

## State rules

- `CitizenStatus.ACTIVE` means the logical citizen is alive and eligible for an entity.
- `entityUuid == null` means no entity is currently linked.
- `entityUuid != null` means exactly one entity UUID is reserved, even if the entity's chunk is unloaded.
- EntityJoinLevelEvent binds a new entity if no link exists, accepts an entity whose UUID matches the saved link, and rejects any duplicate/mismatched entity.
- EntityLeaveLevelEvent is intentionally not used to clear the link because it fires for ordinary chunk unloads as well as other level transitions.
- LivingDeathEvent changes status to DECEASED, clears the link, and updates the faction population cache.
- Debug despawn clears only the link and discards a loaded entity, leaving the logical citizen ACTIVE.

## Registration and rendering

`ModEntities` uses Forge DeferredRegister. The entity is `MobCategory.MISC`, so it is not part of natural mob spawning. It extends PathfinderMob and has a small attribute set. The initial renderer uses the vanilla villager model and texture to avoid adding a rendering dependency. The appearance is intentionally temporary; species-specific models and animations are a later milestone.

## Important duplicate-prevention behavior

A spawn command never creates an entity when `entityUuid` is already non-null. If that UUID is not present among currently loaded entities, the command reports that the linked entity may be in an unloaded chunk and refuses to spawn another. This favors avoiding duplicates over automatically repairing a potentially valid unloaded entity.

## Persistence compatibility

The root SavedData schema stays at 2. Existing CitizenRecord NBT has no `entity_uuid`; the loader treats the field as optional and initializes it to null. New saves include it once an entity is linked. No destructive migration is required.

## Test coverage

1. Spawn one logical citizen.
2. Confirm entity link and visible model.
3. Attempt duplicate spawn and confirm refusal.
4. Teleport away and verify the entity becomes unloaded but remains linked.
5. Return and verify the same entity UUID reloads.
6. Explicitly despawn and respawn; logical citizen UUID remains stable while entity UUID changes.
7. Save/restart and verify both logical and entity UUID bindings persist.
8. Optional future test: kill a temporary test citizen and verify DECEASED status and population decrement.
