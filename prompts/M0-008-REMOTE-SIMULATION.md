# AI implementation context — M0-008 remote simulation

Target: Minecraft 1.20.1, Forge 47.4.28, Java 17.

Implement/maintain a server-thread-only abstract simulation scheduler. It must use persisted civilization records and Overworld game time only. Never load or query remote chunks, entities, block states, or chunk tickets for ordinary simulation.

Use the existing `EmpiresWorldData.simulationClock` field; do not bump the world schema for this milestone. Baseline the clock at server start so stopping the server does not produce offline income. Apply a deterministic test output to active AI factions at a configurable interval, with overflow checks and a bounded all-or-nothing batch budget. Expose `/empires debug simulation` for diagnostics.

Do not claim the milestone is tested until a developer has run `gradlew build`, tested in a real 1.20.1 Forge client/server, verified resource growth at an unloaded settlement, checked `/forceload query`, and verified no offline catch-up.
