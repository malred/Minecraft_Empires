# Combined acceptance — M0-008 / M0-009 / M0-010

## Required environment

Minecraft 1.20.1, Forge 47.4.28, Java 17. Existing world `新的世界2` is retained. Back up the world and project before applying the patch.

## M0-008 remote simulation regression

1. Load the existing save and run `/empires debug status`; verify `schema=3`, the original world ID, faction/settlement/citizen counts, and `readOnly=false`.
2. Run `/empires debug simulation`; verify `enabled=true`, `intervalTicks=100`, `budget=32`, `activeAiFactions=1`, `worldAccess=DATA_ONLY`, and `readOnly=false` (values may differ if config or faction population changed).
3. Query `/empires debug show-resources c2a593a0-ac36-4f90-8bbf-dd0cb5660a32` and record `empires:wood`.
4. Stay at least 3000 blocks from the remote settlement near x=2000,z=2000 and keep the integrated server running for 150+ game ticks.
5. Run `/empires debug simulation` and resource query again. Confirm the persisted simulation clock advanced and test wood increased according to completed cycles.
6. Run `/forceload query 2000 2000`; the chunk must not be marked force-loaded by the mod.
7. Save and quit completely, relaunch, and re-enter the same world. Confirm startup baseline matches current gameTime and there is no offline resource catch-up.
8. Check M0-008 after M0-010 placement activity as well; the simulation remains DATA_ONLY and adds no force-load tickets.

## M0-009 data regression

1. Run `/empires debug building-count`, `/empires debug status`, `/empires debug list-blueprints`.
2. Confirm existing world-level counts remain intact and the existing M0-009 test building UUID `e28b1b7c-f309-4d26-afb3-6635245fcaaf` remains `PLANNED / NOT_PLACED` with cursor 0. Do not attempt to place it if it is floating above terrain; it was originally planned at y=121 as a data-only test.
3. Confirm the building record remains discoverable after a restart; nested BuildingRecord schema 1 will be upgraded to schema 2 without changing root SavedData schema 3.

## M0-010 physical placement test

1. In the existing world, switch to Creative. Move to a clear, flat natural-ground patch near the remote settlement (for example around x=2015,z=2005), but do not overlap the old planned building at x=2005,z=2005. Land so the player feet position is at the intended floor level.
2. Create a fresh logical building at the player's standing position:

   `/empires debug plan-building cca48d0a-89af-4b8c-b651-b612390baaf3 empires:warehouse_prototype 90`

3. Copy the newly returned Building UUID. Confirm its initial state is `PLANNED`, `worldSync=NOT_PLACED`, cursor 0.
4. Ensure the player remains close enough that the complete 3×3×3 target region is loaded. Run:

   `/empires debug place-building <newBuildingUUID>`

5. Wait about one second. The default placement budget is 8 blocks/tick; the 25-block template should complete in at least 4 server ticks.
6. Query:

   `/empires debug building-placement <newBuildingUUID>`
   `/empires debug show-building <newBuildingUUID>`
   `/empires debug show-resources c2a593a0-ac36-4f90-8bbf-dd0cb5660a32`

7. Expected: `state=PLANNED`, `worldSync=SYNCED`, `placementCursor=25/25`; structure appears as 3×3 cobblestone floor, 7 oak-plank wall blocks with a centered open entrance, and a 3×3 oak-plank roof. No resources are deducted in M0-010.
8. Run `/empires debug place-building <newBuildingUUID>` again. It must refuse because the building is already synced; no duplicate placement occurs.
9. Run `/forceload query 2015 2005`; the mod must not have force-loaded the target chunk.
10. Save, quit, restart, and query the same UUID. Expect `worldSync=SYNCED`, cursor 25/25, and the same physical blocks. The structure must not be placed a second time.

## Optional recovery test

On a separate test building only, start a pending placement and unload the target area before completion. The scheduler should pause, not force-load. Return to the area to let it continue. Do not use the previously verified building for destructive tests.

## Known NOT RUN items unless explicitly executed

- Dedicated server startup.
- Schema-1 root-world fixture migration.
- M0-008 over-budget batch behavior and `simulationTestWoodPerCycle=0` config.
- Placement recovery after process termination mid-batch.
- Datapack reload with a malformed or tampered template.
