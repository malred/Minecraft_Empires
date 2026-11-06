package dev.minecraftempires.common.building;

/** Persistent lifecycle states. Transitions are performed by server-side services. */
public enum BuildingState {
    PLANNED,
    RESERVED,
    UNDER_CONSTRUCTION,
    ACTIVE,
    DAMAGED,
    REPAIRING,
    RUINED,
    DEMOLISHED,
    CANCELLED,
    NEEDS_REPAIR
}
