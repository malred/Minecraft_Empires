# Patch manifest — M0-008 Remote Simulation v0.1.0

Target baseline: Minecraft 1.20.1 / Forge 47.4.28 / Java 17, with M0-003/004/005/006/007 already applied.

Files:
- `src/main/java/dev/minecraftempires/common/EmpiresConfig.java` — updates simulation settings and adds interval/test-output controls.
- `src/main/java/dev/minecraftempires/server/SimulationScheduler.java` — server-tick data-only simulation scheduler.
- `src/main/java/dev/minecraftempires/server/SimulationCommands.java` — `/empires debug simulation` status command.
- `docs/M0-008-REMOTE-SIMULATION.md` — design and acceptance tests.
- `prompts/M0-008-REMOTE-SIMULATION.md` — coding-agent implementation context.

No existing world data is deleted. No SavedData schema change is made. The existing schema 2 `simulation_clock` is reused.

This patch is additive except for `EmpiresConfig.java`, which must be replaced by the supplied version. Back up the project before applying. Do not mix it with an older pre-M0-005 source tree.
