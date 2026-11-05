package dev.minecraftempires.common.building;

/** Block replacement policy declared by a blueprint; enforced by the placement adapter in M0-010. */
public enum ProtectedBlocksPolicy {
    NEVER_REPLACE,
    REPLACE_REPLACEABLE_ONLY
}
