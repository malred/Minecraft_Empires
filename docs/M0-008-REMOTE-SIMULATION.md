# M0-008 — Data-only remote simulation proof

## Purpose

Prove that AI faction economy can advance while the faction's settlement chunks are unloaded. This milestone is intentionally narrow: it is a scheduler and persistence proof, not the final economy or AI planner.

## Implementation

- Runs only from `TickEvent.ServerTickEvent` on `Phase.END` (logical server thread).
- Reads the Overworld game time and persisted `EmpiresWorldData` faction records.
- Uses the already-persisted `simulationClock` as the last successfully processed world-time boundary.
- At server start, moves the baseline forward to current game time. A partial interval at shutdown is discarded; there is no offline production.
- Every configured interval, adds a temporary diagnostic amount of `empires:wood` to active AI factions.
- Prepares resource ledgers before committing updates.
- Refuses a batch if active AI faction count exceeds the configured batch budget; it does not silently skip factions or advance the clock.
- Does not query `Level#getChunk`, `getChunkSource`, block states, entities, or chunk tickets.
- Does not change SavedData schema. `simulationClock` is already persisted in schema 2.

## Temporary test output

`simulationTestWoodPerCycle` defaults to 1. It is deliberately a visible test signal, not the final production model. Set it to 0 to disable the diagnostic resource increment. Later, building/worker production will replace this output.

## Configuration

- `simulationEnabled`: master switch.
- `simulationIntervalTicks`: default 100 game ticks (about five seconds at 20 TPS).
- `simulationBudgetPerTick`: maximum active AI factions in one batch; default 32.
- `simulationTestWoodPerCycle`: temporary wood increment per batch.

## Diagnostic command

`/empires debug simulation`

Reports the current game time, persisted simulation clock, time until the next batch, active AI faction count, budget, test output and the explicit `worldAccess=DATA_ONLY` marker.

## Acceptance tests

1. Back up the test world.
2. At a remote location (for example x=2000,z=2000), create a fresh AI faction, create its settlement there, set it as capital, and seed its resources.
3. Move the player at least 3000 blocks away (for example x=5000,z=5000).
4. Wait at least 150 game ticks while the integrated server remains active.
5. Run `/empires debug simulation`; verify the simulation clock advanced and at least one batch completed.
6. Run `/empires debug show-resources <remoteFactionUUID>`; verify `empires:wood` increased by at least one per completed cycle.
7. Run `/forceload query 2000 2000` from the distant location; verify the simulation did not add a force-loaded chunk ticket.
8. Return to the remote settlement and verify its world chunks can load normally.
9. Save and quit, relaunch, and verify no resources were granted for time spent with the server stopped. The simulation clock should be baselined at server start.
10. Set `simulationTestWoodPerCycle=0` in the config and restart; verify the simulation clock advances but wood no longer increases.

## Known limits

- The first implementation processes all active AI factions as one batch. If their count exceeds the configured budget, it intentionally pauses rather than partially applying updates. Raise the budget for the test or a later sharded scheduler will replace this restriction.
- The temporary wood output is only a scheduler signal. It is not the final worker/building economy.
- The scheduler does not yet simulate residents, construction projects, logistics, military units, or diplomacy.
