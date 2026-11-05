# Minecraft Empires — M0-008 Remote Simulation Patch v0.1.0

This patch implements the first data-only AI simulation proof. It is designed for the M0-003/004/005/006/007 code already installed in your `MinecraftEmpires` project.

## Apply

1. Stop Minecraft completely.
2. Back up the project folder and test world.
3. Extract this ZIP into the project root (the folder containing `build.gradle`).
4. Merge `src` into the existing `src`; allow the supplied `EmpiresConfig.java` to replace the old file.
5. Run:

```powershell
.\gradlew.bat build
```

6. If successful, run:

```powershell
.\gradlew.bat runClient
```

## Test command

```mcfunction
/empires debug simulation
```

The command reports the simulation clock, next batch timing, AI faction count, configured budget, and whether the implementation is data-only.

## Important

- The scheduler does not load chunks or spawn entities.
- Every 100 game ticks by default, each active AI faction receives a temporary `empires:wood` test increment of 1.
- This artificial resource output is only a proof signal and will be replaced by real building/worker production later.
- If active AI factions exceed the configured batch budget, the scheduler pauses the batch rather than partially processing factions. Increase `simulationBudgetPerTick` if needed.
- Server shutdown does not produce offline resources.
- No SavedData schema migration is included.

See `docs/M0-008-REMOTE-SIMULATION.md` for the full acceptance procedure.
