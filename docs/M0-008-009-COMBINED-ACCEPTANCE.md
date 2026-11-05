# Combined acceptance — M0-009 data migration + M0-008 regression

All commands are OP-only and must be run in the same existing test world used for M0-008. Record the starting Faction, Settlement, Citizen counts, resource balance and simulation clock before applying the patch.

## A. M0-008 regression after Schema 3 migration

1. Back up the existing world and note the remote AI faction UUID and its remote Settlement UUID.
2. Start the patched client/world. Run `/empires debug status` and `/empires debug building-count`; verify `schema=3`, the original Faction/Settlement/Citizen counts, building `count=0` (when no prior plans exist), and `readOnly=false`.
3. Run `/empires debug show-resources <remoteFactionUUID>` and note `empires:wood`.
4. Run `/empires debug simulation`. Verify `enabled=true`, `activeAiFactions` includes the remote AI faction, `intervalTicks=100` (unless configured otherwise), and `worldAccess=DATA_ONLY`.
5. Wait at least 150 game ticks with the server running. Run `/empires debug simulation` again and verify `completedCyclesThisSession` has increased and `lastProcessedGameTime` / `simulationClock` has advanced.
6. Run `/empires debug show-resources <remoteFactionUUID>`. With the default test output `1`, wood should increase by at least one for each successfully completed simulation cycle.
7. While still far from the remote base, run `/forceload query 2000 2000` (substitute the actual remote base X/Z if different). Verify the M0-008 scheduler did not add a force-loaded chunk.
8. Return to the remote base and verify the area can load normally.
9. Save and quit. Relaunch the same world. Before waiting another 100 ticks, immediately check resources and `/empires debug simulation`: no offline wood should have been granted. Then wait 150 ticks and verify normal in-session production resumes.
10. Optional diagnostic: set `simulationTestWoodPerCycle=0` in `run/config/empires-common.toml`, restart, wait at least 150 ticks, and verify the clock advances while wood does not increase from this temporary test output. Restore the value to `1` afterward if continuing to use the M0-008 proof.

## B. M0-009 blueprint and building-record acceptance

11. Run `/empires debug list-blueprints`. Verify `empires:warehouse_prototype`, 3x3x3 size, template ID, work points, material manifest, rotations and a 64-character checksum are displayed.
12. Run `/empires debug building-count`; expect `count=0` in a world with no M0-009 plans yet.
13. Teleport to the remote Settlement and stand at a clear logical origin that does not overlap other planned buildings. The planning command uses the command execution position as the blueprint minimum corner; it does not place blocks.
14. Record the remote Faction resource balances. Run `/empires debug plan-building <remoteSettlementUUID> empires:warehouse_prototype 90`.
15. Verify the response contains a new Building UUID, `state=PLANNED`, `progress=0/100`, `reservedMaterials=0`, and `worldSync=NOT_PLACED`. Verify no blocks appeared and resources did not decrease.
16. Run `/empires debug list-buildings <remoteSettlementUUID>` and `/empires debug show-building <buildingUUID>`. Verify the building appears and all owner/settlement/dimension/position/rotation/checksum values are correct.
17. Repeat the plan command at the exact same position. It must be rejected because the 3D logical footprints overlap. `building-count` should remain `1`.
18. Save and quit, then relaunch. Verify the same Building UUID, blueprint, position, rotation, status and progress remain. Run `/empires debug status`; factions, settlements, citizens, simulation clock and the M0-008 scheduler must remain intact.

## Expected result

- Schema 3, `readOnly=false`.
- No change to the existing Faction, Settlement or Citizen IDs/counts.
- Simulation clock continuity and in-session M0-008 remote AI wood test still work; no offline catch-up or new force-load.
- One logical building persists and is indexed by its Settlement.
- Planning consumes no resources and modifies no world blocks.

## Test record

For each test, record the ID, world backup name, Forge/Java versions, starting resource amount, actual output, log excerpt and PASS/FAIL/NOT RUN. Do not mark tests PASS until they are actually executed in the Forge client/server.
