# Minecraft Empires — M0-005 CitizenRecord

Target: the already working Minecraft Empires project created from the v0.1.2 Starter, with the M0-003 persistence patch and command-parser hotfix v0.1.1 applied.

Runtime baseline: Minecraft 1.20.1, Forge 47.4.28, Java 17.

## What this patch adds

- `CitizenRecord`: a persistent logical citizen identity independent of any Minecraft entity.
- `CitizenStatus`: logical citizen lifecycle states (`ACTIVE`, `MISSING`, `DECEASED`, `RETIRED`). These are not entity-loaded states.
- Optional home-settlement binding. New citizens are assigned to their faction's active capital when one exists.
- Species is copied from the owning faction at creation time.
- Initial profession is `empires:unassigned`; profession behavior and jobs are later milestones.
- Repository/service APIs for creating and listing citizens.
- Debug commands: `create-citizen`, `list-citizens`, and `show-resources`.
- `/empires debug status` now includes the citizen count; faction listing includes the population cache.
- World SavedData schema migration from schema 1 to schema 2. Existing schema 1 worlds retain their factions, settlements, capital references, resources, world UUID, and simulation clock; citizen list starts empty. The migrated payload is marked dirty so it will be rewritten as schema 2 on save.

## Important scope boundary

This milestone creates logical records only. It does **not** spawn NPC entities, add models, implement pathfinding, jobs, needs, work AI, or entity sleep/wake. Those belong to later milestones. A citizen record remains present even if no Minecraft entity exists.

## Before applying

1. Stop Minecraft normally.
2. Make a backup of the test world's `saves/<world name>` folder.
3. Confirm the existing project already contains the tested M0-003 files and command-parser hotfix.
4. Extract this ZIP into the project root (the folder containing `build.gradle`), preserving the `src/` folder structure and allowing file replacement.

This patch replaces `EmpiresWorldData`, `CivilizationRepository`, `SavedDataCivilizationRepository`, `CivilizationService`, and `EmpiresCommands`, and adds `CitizenRecord` and `CitizenStatus`.

Do not re-apply the older M0-003 patch after this patch, because it would overwrite the new schema and citizen code.

## Build and launch

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
```

## Migration and citizen test

Use the existing test world that contains `Test Wolves`. Its known IDs from the previous test are:

- Faction: `c2a593a0-ac36-4f90-8bbf-dd0cb5660a32`
- Capital settlement: `63bc0400-5ea9-4a37-a138-17ca8eea8c2b`

If your world has different IDs, use the IDs from `/empires debug list-factions` and the previous test output instead.

On first load after this patch, the world should migrate from schema 1 to schema 2. The server log should contain a message similar to `Migrated Minecraft Empires world data from schema 1 to schema 2.`

First check migration and pre-existing resources:

```mcfunction
/empires debug status
/empires debug list-factions
/empires debug show-resources c2a593a0-ac36-4f90-8bbf-dd0cb5660a32
```

Expected before creating citizens:

- `schema=2`
- `factions=1`
- `settlements=1`
- `citizens=0`
- `readOnly=false`
- The Test Wolves faction and capital settlement UUID remain unchanged.
- Resource balances are `empires:wood = 128`, `empires:stone = 64`, `empires:food = 32`.

Create three logical citizens:

```mcfunction
/empires debug create-citizen c2a593a0-ac36-4f90-8bbf-dd0cb5660a32 "Aru"
/empires debug create-citizen c2a593a0-ac36-4f90-8bbf-dd0cb5660a32 "Runa"
/empires debug create-citizen c2a593a0-ac36-4f90-8bbf-dd0cb5660a32 "Fen"
```

List the citizens and check the faction population cache:

```mcfunction
/empires debug list-citizens c2a593a0-ac36-4f90-8bbf-dd0cb5660a32
/empires debug list-factions
/empires debug status
```

Expected:

- `citizens=3`
- Each citizen has a unique UUID.
- Each citizen has `species=empires:wolfkin`.
- Each citizen has `profession=empires:unassigned` and `status=ACTIVE`.
- Each citizen's `home` is `63bc0400-5ea9-4a37-a138-17ca8eea8c2b`.
- Test Wolves shows `population=3`.
- No NPC entities should be spawned by this command in M0-005.

## Persistence test

1. Save and quit normally.
2. Close the client fully.
3. Run `.\gradlew.bat runClient` again and reopen the same world.
4. Run:

```mcfunction
/empires debug status
/empires debug list-factions
/empires debug list-citizens c2a593a0-ac36-4f90-8bbf-dd0cb5660a32
/empires debug show-resources c2a593a0-ac36-4f90-8bbf-dd0cb5660a32
```

Expected: schema 2, same world/faction/settlement/citizen UUIDs, three citizens, population cache 3, and the resource balances remain unchanged.

## Stop conditions

If `readOnly=true`, stop testing and do not run any create/write command. Save a copy of `logs/latest.log` and report the error. Do not delete the world's `data/minecraft_empires_world.dat` file.

If the build fails, send the first compiler error and its surrounding lines. If the game crashes, send `logs/latest.log` and the crash report.

## Known limitations

- Citizens are stored globally in the Overworld SavedData registry; they are not entities yet.
- No resident cap or housing check is enforced yet.
- Citizen names are not required to be unique.
- Profession and species IDs are stored as ResourceLocations; the species registry and profession registry are later milestones.
- Population cache currently counts citizens with status `ACTIVE`; future status-changing service methods must keep this cache synchronized.
- Schema 1 to 2 migration is implemented; migration from unknown or future schemas remains read-only by design.
