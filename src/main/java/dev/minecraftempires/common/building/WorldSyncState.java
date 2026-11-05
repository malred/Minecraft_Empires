package dev.minecraftempires.common.building;

/** Relationship between the authoritative logical record and its future block representation. */
public enum WorldSyncState {
    NOT_PLACED,
    PENDING,
    SYNCED,
    OUT_OF_SYNC,
    NEEDS_REPAIR
}
