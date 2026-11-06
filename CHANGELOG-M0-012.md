# Changelog — M0-012 Integration Tests

日期：2026-11-06

## Added

- Added eight Forge GameTests under batch `empires.m0_012`.
- Added a deterministic, isolated 5×5×5 empty GameTest structure at `data/empires/structures/gametest_empty_5x5x5.nbt`.
- Added tests for SavedData NBT round trips, preserved completed and pending placement cursors, warehouse template dimensions/checksum, population-cache warnings, overlapping buildings, duplicate citizen entity UUID links, and forward-schema read-only preservation.
- Added a real GameTest integration path that invokes `BuildingPlacementService`, places the 25-block warehouse template in the GameTest scene, checks SYNCED/cursor/block states, and rejects a second placement attempt.
- Added explicit `CivilizationService` / `EmpiresWorldData` overloads to the placement adapter so tests can use an in-memory repository rather than adding test factions/buildings to the GameTest server's persistent SavedData. Existing production entry points keep using the authoritative Overworld SavedData.
- Configured `runGameTestServer` to use the isolated `run-gametest` working directory.

## Safety / scope

- No third-party dependency added.
- No SavedData schema migration.
- The user's `新的世界2` save and normal `run/` client directory are not used by GameTestServer.
- The physical placement test modifies only its generated GameTest structure in `run-gametest`.
- Process termination mid-write, real chunk unload/reload, malformed data-pack resource reload, and ordinary `runServer` remain separate manual acceptance items.

## Verification

- Source and docs committed to GitHub `main`.
- The gzip checksum of the empty GameTest NBT resource was regenerated and validated during authoring.
- User-side `gradlew.bat build` after M0-012 additions: NOT RUN.
- User-side `gradlew.bat runGameTestServer`: NOT RUN.
- Forge client / Dedicated Server regression after M0-012 additions: NOT RUN.


## User verification update — 2026-11-06

- After adding the M0-012 GameTest suite and placement-service test overloads, user ran `gradlew.bat build`: PASS, BUILD SUCCESSFUL in 38s, 7 actionable tasks.
- Two ResourceLocation(String, String) removal warnings are in the GameTest fixture constants; they are non-fatal. BuildingPlacementService also emits a generic deprecated API note.
- `gradlew.bat runGameTestServer`: NOT RUN yet.


## User verification update — 2026-11-06 (GameTest)

- User ran `gradlew.bat runGameTestServer --console=plain` on Windows 11 / Java 17.0.12 / Forge 47.4.28.
- Forge launched the isolated GameTest server and executed batch `empires.m0_012` with 8 tests.
- Result: `All 8 required tests passed :)`; `BUILD SUCCESSFUL in 1m 5s`; 10 actionable tasks.
- The schema=4 read-only ERROR log is expected from `futureSchemaIsPreservedReadOnly`, which verifies forward-schema data preservation; it is not the user's normal world save.
- First-run server.properties missing-file stack trace and Forge config self-correction logs were non-fatal; GameTestServer started and all tests passed.
- Still NOT RUN: normal save regression after this change, kill-process mid-placement recovery, real chunk unload/reload, malformed template reload, ordinary `runServer` startup/shutdown.
