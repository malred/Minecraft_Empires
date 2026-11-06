package dev.minecraftempires.common.civilization;

/** Lifecycle of a persisted builder job. Pathfinding and travel execution are added in a later milestone. */
public enum BuilderJobState {
    AVAILABLE,
    CLAIMED,
    TRAVELLING,
    WORKING,
    BLOCKED,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    public boolean isActive() {
        return !isTerminal();
    }

    public boolean requiresAssignedCitizen() {
        return this == CLAIMED || this == TRAVELLING || this == WORKING;
    }
}
