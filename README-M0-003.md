# Minecraft Empires — M0-003 / M0-004 Persistence Patch

Target: the generated `MinecraftEmpires` project root (the folder containing `build.gradle`, `gradlew.bat`, and `src/`).

## What this patch adds

- Overworld-scoped `EmpiresWorldData extends SavedData`.
- Stable world UUID, schema version, simulation clock, faction and settlement records.
- Immutable `FactionRecord`, `SettlementRecord`, and `ResourceLedger`.
- Repository and service boundary so commands do not mutate SavedData directly.
- OP-only debug commands for status, test faction/settlement creation, capital binding, resource seeding, and listing factions.
- Server-start initialization of the Overworld registry.
- Read-only preservation behavior for unsupported/corrupt schema data; it logs an error and refuses writes rather than silently replacing the saved payload.

This patch intentionally does not add gameplay entities, blocks, NPCs, AI simulation, or client UI.

## Apply

1. Back up the generated project folder.
2. Extract this ZIP directly into the generated project root (the folder with `build.gradle`).
3. Allow `ServerLifecycleEvents.java` to be overwritten.
4. Run:

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
```

## In-game persistence test

Open a new test world with cheats enabled. Run:

```mcfunction
/empires debug status
/empires debug create-faction "Test Wolves" empires:wolfkin
/empires debug create-settlement <FACTION_UUID>
/empires debug set-capital <FACTION_UUID> <SETTLEMENT_UUID>
/empires debug seed-resources <FACTION_UUID>
/empires debug list-factions
```

Use the UUIDs printed by the preceding commands. Write down the faction UUID, settlement UUID and world UUID. Save and quit normally. Start `runClient` again, open the same world, then run:

```mcfunction
/empires debug status
/empires debug list-factions
```

Expected: the same world UUID, faction UUID, settlement UUID, capital reference, resource balances, name, species and controller type are present after restart; the faction count is not incremented by merely reopening the world.

## Additional checks

- Run `/empires debug status` from the Overworld and from the Nether; `worldId` and faction count must match.
- Run `/empires debug create-faction "Test Bears" empires:bearfolk`; the new faction should appear exactly once.
- Run `/empires debug seed-resources <FACTION_UUID>` and verify the test resource balances survive restart (the command resets those three balances to wood=128, stone=64, food=32).
- Try to assign a settlement belonging to a different faction as capital; the service must reject it.
- Negative resource amounts are rejected by `ResourceLedger`; the current patch does not expose arbitrary negative ledger mutation through commands.
- Dedicated-server verification is still required before calling this milestone complete:

```powershell
.\gradlew.bat runServer
```

## Known boundary

The test command creates AI-controlled test factions only. Player ownership/permission binding, species registry validation, faction deletion, migrations from future schema versions, and settlement gameplay are later tasks. A test faction is not yet an AI that builds or fights.
