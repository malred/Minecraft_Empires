# Patch manifest — M0-009 Blueprint / BuildingRecord v0.1.0

Base: `malred/Minecraft_Empires` `main` at `eb6a4562eae22f435ae36a86c6ac3dfbab49f6a1`.
Target: Minecraft 1.20.1 / Forge 47.4.28 / Java 17.

## Added Java files

- `src/main/java/dev/minecraftempires/common/building/BuildingCategory.java`
- `src/main/java/dev/minecraftempires/common/building/BuildingState.java`
- `src/main/java/dev/minecraftempires/common/building/WorldSyncState.java`
- `src/main/java/dev/minecraftempires/common/building/ProtectedBlocksPolicy.java`
- `src/main/java/dev/minecraftempires/common/building/BlueprintDefinition.java`
- `src/main/java/dev/minecraftempires/common/building/BlueprintCatalog.java`
- `src/main/java/dev/minecraftempires/common/building/BuildingRecord.java`
- `src/main/java/dev/minecraftempires/server/BuildingCommands.java`

## Replaced Java files

- `src/main/java/dev/minecraftempires/common/civilization/EmpiresWorldData.java`
- `src/main/java/dev/minecraftempires/common/civilization/SettlementRecord.java`
- `src/main/java/dev/minecraftempires/common/civilization/CivilizationRepository.java`
- `src/main/java/dev/minecraftempires/common/civilization/SavedDataCivilizationRepository.java`
- `src/main/java/dev/minecraftempires/common/civilization/CivilizationService.java`

## Added docs

- `README-M0-009.md`
- `CHANGELOG-M0-009.md`
- `PATCH-MANIFEST-M0-009.md`
- `TEST-STEPS-ZH.md`
- `docs/M0-009-BLUEPRINT-BUILDING-RECORD.md`
- `docs/M0-008-009-COMBINED-ACCEPTANCE.md`
- `prompts/M0-009-BLUEPRINT-BUILDING-RECORD.md`

No build.gradle, Gradle wrapper, mod entry point, citizen entity, existing command class, simulation scheduler, or config file is replaced.
