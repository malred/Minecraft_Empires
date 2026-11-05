# Patch manifest — M0-010 Block Placement Adapter v0.1.0

Base: M0-009 Blueprint / BuildingRecord patch already applied and verified by the project owner.
Target: Minecraft 1.20.1 / Forge 47.4.28 / Java 17.

## Added

- `src/main/java/dev/minecraftempires/common/building/BlueprintTemplate.java`
- `src/main/java/dev/minecraftempires/common/building/BuildingPlacementService.java`
- `src/main/java/dev/minecraftempires/server/BuildingPlacementCommands.java`
- `src/main/java/dev/minecraftempires/server/BuildingPlacementScheduler.java`
- `src/main/resources/data/empires/structures/warehouse_prototype.json`
- `docs/M0-010-BLOCK-PLACEMENT-ADAPTER.md`
- `docs/M0-008-009-010-COMBINED-ACCEPTANCE.md`
- `prompts/M0-010-BLOCK-PLACEMENT-ADAPTER.md`
- `README-M0-010.md`, `CHANGELOG-M0-010.md`, `TEST-STEPS-ZH.md`

## Modified (M0-009 source files)

- `src/main/java/dev/minecraftempires/common/EmpiresConfig.java` — adds `buildingPlacementBlocksPerTick` (default 8).
- `src/main/java/dev/minecraftempires/common/building/BlueprintDefinition.java` — adds optional expected physical template checksum without changing the existing blueprint manifest checksum.
- `src/main/java/dev/minecraftempires/common/building/BlueprintCatalog.java` — pins the warehouse template content checksum.
- `src/main/java/dev/minecraftempires/common/building/BuildingRecord.java` — internal schema 1 → 2 and persisted placement cursor; old records remain readable.
- `src/main/java/dev/minecraftempires/common/civilization/CivilizationService.java` — adds validated monotonic placement updates.
- `src/main/java/dev/minecraftempires/common/civilization/EmpiresWorldData.java` — marks root SavedData dirty when nested building records migrate from schema 1 to 2; root schema remains 3.
- `src/main/java/dev/minecraftempires/server/BuildingCommands.java` — exposes placement cursor in building diagnostics.

## Persistence / compatibility

- World SavedData `CURRENT_SCHEMA_VERSION` remains 3; no root migration is performed.
- Existing M0-009 BuildingRecord schema 1 rows load as schema 2 with `placement_cursor=0`.
- Existing planned building records remain `PLANNED / NOT_PLACED`; this patch does not automatically place them.
- Existing Faction, Settlement, Citizen, ResourceLedger, simulation clock, and M0-008 scheduler data are preserved.
- No third-party dependency is added.

## Execution notes

- Placement runs on the logical server thread only.
- `LevelReader.hasChunkAt` is checked before reading or changing target positions; no `getChunk`, chunk ticket, or force-load API is used.
- Placement budget is global per server tick, default 8 template blocks.
- An unloaded target chunk pauses a pending placement. Unsafe obstruction/template errors set `NEEDS_REPAIR` and require an explicit resume after correction.
- Material reservation, consumption, workers, and construction progress are intentionally out of scope.

## Verification status

ZIP integrity and static source checks are performed before delivery. The developer must run `gradlew.bat build`, launch the actual Forge client/server, verify the physical structure, and complete the combined M0-008 regression checklist. No client/server success is claimed by this patch author.
