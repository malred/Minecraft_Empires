# AI coding context — M0-006 / M0-007

Target: Minecraft 1.20.1, Forge 47.4.28, Java 17. Existing project already passed M0-005 tests.

Before changing code, read the patch README and this specification. Preserve the existing world SavedData schema 2, FactionRecord, SettlementRecord, repository interfaces, and resource ledger formats.

Required invariants:
1. CitizenRecord is authoritative; CitizenEntity is only its loaded-world representation.
2. CitizenRecord.entityUuid is optional and backward-compatible. Do not bump the root SavedData schema for this additive field.
3. One ACTIVE CitizenRecord may have at most one linked CitizenEntity UUID.
4. Never clear entityUuid on EntityLeaveLevelEvent: normal chunk unloading must preserve the link.
5. EntityJoinLevelEvent must validate citizen UUID, faction UUID, logical status, and entity UUID uniqueness.
6. Entity death marks the logical citizen DECEASED, clears the link, and updates the faction population cache.
7. Explicit debug despawn clears the link only for a loaded matching CitizenEntity and leaves the logical citizen ACTIVE.
8. A spawn command must refuse to spawn if the record has a non-null entityUuid even if that UUID is not currently loaded.
9. Keep all net.minecraft.client imports in the client package.
10. Do not add GeckoLib or any other third-party dependency in this milestone.

Build and test on Java 17 with `./gradlew build` / `gradlew.bat build`; then run the exact chunk unload/reload and save/restart scenarios in README. Do not claim tests passed unless actually run.
