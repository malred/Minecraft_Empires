# Minecraft Empires — M0-009 Blueprint / BuildingRecord v0.1.0

This is an incremental source patch for the public repository baseline:

- Repository: `malred/Minecraft_Empires`
- Branch: `main`
- Base commit: `eb6a4562eae22f435ae36a86c6ac3dfbab49f6a1`
- Minecraft 1.20.1 / Forge 47.4.28 / Java 17

## Scope

M0-009 introduces the first immutable official blueprint definition/catalog and persistent logical building records. It upgrades `EmpiresWorldData` from schema 2 to schema 3 and adds server-only OP debug commands.

The starter catalog contains one 3x3x3 warehouse prototype (`empires:warehouse_prototype`). Its template resource ID is declared now; the actual structure-template asset and world block placement are intentionally part of M0-010.

## Safety / behavior

- Schema 1 and schema 2 worlds migrate to schema 3; Schema 1 first receives the existing empty-citizen migration, then schema 3.
- Existing Faction, Settlement, Citizen, resource ledgers and `simulationClock` values are retained.
- A malformed or inconsistent building reference causes the existing read-only preservation path to engage; the original NBT payload is kept instead of being overwritten.
- Building plans are logical records only. M0-009 does not read chunks, load chunks, query blocks, place blocks, spawn entities, reserve materials, deduct resources, or advance construction progress.
- Planning validates active faction/settlement, dimension, blueprint/rotation, and 3D overlap with other known blueprint instances.
- Settlement `building_ids` and the world building map are updated in one server-thread operation.
- `/empires debug simulation` and the M0-008 scheduler are not replaced by this patch.

## Apply

1. Close Minecraft and any development server.
2. Back up the project folder and the existing world save.
3. Extract this ZIP into the project root (the folder containing `build.gradle`), preserving the `src` directory structure and allowing replacement of the listed existing Java files.
4. Run `gradlew.bat build` from PowerShell (see `TEST-STEPS-ZH.md` for the exact command).
5. If the build succeeds, run `gradlew.bat runClient` and follow the combined M0-009 + M0-008 regression test.

## Commands added

- `/empires debug list-blueprints`
- `/empires debug building-count`
- `/empires debug plan-building <settlementUUID> <namespace:blueprint_id> [rotationDegrees]`
- `/empires debug list-buildings <settlementUUID>`
- `/empires debug show-building <buildingUUID>`

Use rotation degrees 0, 90, 180 or 270. Omitted rotation defaults to 0.

## Verification status

The patch archive and source structure are checked in this delivery. It has not been compiled against the user's local Forge MDK or launched in Minecraft in this environment. The required build, migration, save/reload, building-plan, and M0-008 runtime checks are NOT RUN until executed by the user.
