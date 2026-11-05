package dev.minecraftempires.common.civilization;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable logical settlement record; it does not imply that its chunks are loaded. */
public final class SettlementRecord {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private final UUID id;
    private final UUID ownerFactionId;
    private final ResourceLocation dimensionId;
    private final BlockPos anchorPosition;
    private final SettlementStatus status;
    private final Set<UUID> buildingIds;
    private final long createdAtGameTime;
    private final int schemaVersion;

    public SettlementRecord(
            final UUID id,
            final UUID ownerFactionId,
            final ResourceLocation dimensionId,
            final BlockPos anchorPosition,
            final SettlementStatus status,
            final Set<UUID> buildingIds,
            final long createdAtGameTime,
            final int schemaVersion
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.ownerFactionId = Objects.requireNonNull(ownerFactionId, "ownerFactionId");
        this.dimensionId = Objects.requireNonNull(dimensionId, "dimensionId");
        this.anchorPosition = Objects.requireNonNull(anchorPosition, "anchorPosition").immutable();
        this.status = Objects.requireNonNull(status, "status");
        final Set<UUID> copiedBuildingIds = new LinkedHashSet<>(Objects.requireNonNull(buildingIds, "buildingIds"));
        if (copiedBuildingIds.contains(null)) {
            throw new IllegalArgumentException("Settlement building ID index cannot contain null");
        }
        this.buildingIds = Collections.unmodifiableSet(copiedBuildingIds);
        if (createdAtGameTime < 0L || schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Invalid settlement timestamp or unsupported schema version: " + schemaVersion);
        }
        this.createdAtGameTime = createdAtGameTime;
        this.schemaVersion = schemaVersion;
    }

    public static SettlementRecord create(
            final UUID ownerFactionId,
            final ResourceLocation dimensionId,
            final BlockPos anchorPosition,
            final long gameTime
    ) {
        return new SettlementRecord(UUID.randomUUID(), ownerFactionId, dimensionId,
                anchorPosition, SettlementStatus.ACTIVE, Set.of(), gameTime, CURRENT_SCHEMA_VERSION);
    }

    public UUID id() { return id; }
    public UUID ownerFactionId() { return ownerFactionId; }
    public ResourceLocation dimensionId() { return dimensionId; }
    public BlockPos anchorPosition() { return anchorPosition; }
    public SettlementStatus status() { return status; }
    public Set<UUID> buildingIds() { return buildingIds; }
    public long createdAtGameTime() { return createdAtGameTime; }
    public int schemaVersion() { return schemaVersion; }

    public SettlementRecord withStatus(final SettlementStatus newStatus) {
        return new SettlementRecord(id, ownerFactionId, dimensionId, anchorPosition,
                newStatus, buildingIds, createdAtGameTime, CURRENT_SCHEMA_VERSION);
    }

    public SettlementRecord withBuildingId(final UUID buildingId) {
        Objects.requireNonNull(buildingId, "buildingId");
        if (buildingIds.contains(buildingId)) {
            return this;
        }
        final Set<UUID> updated = new LinkedHashSet<>(buildingIds);
        updated.add(buildingId);
        return new SettlementRecord(id, ownerFactionId, dimensionId, anchorPosition,
                status, updated, createdAtGameTime, CURRENT_SCHEMA_VERSION);
    }

    public SettlementRecord withoutBuildingId(final UUID buildingId) {
        if (!buildingIds.contains(buildingId)) {
            return this;
        }
        final Set<UUID> updated = new LinkedHashSet<>(buildingIds);
        updated.remove(buildingId);
        return new SettlementRecord(id, ownerFactionId, dimensionId, anchorPosition,
                status, updated, createdAtGameTime, CURRENT_SCHEMA_VERSION);
    }

    CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putUUID("owner_faction_id", ownerFactionId);
        tag.putString("dimension_id", dimensionId.toString());
        tag.putLong("anchor_position", anchorPosition.asLong());
        tag.putString("status", status.name());
        final ListTag buildings = new ListTag();
        for (UUID buildingId : buildingIds) {
            buildings.add(StringTag.valueOf(buildingId.toString()));
        }
        tag.put("building_ids", buildings);
        tag.putLong("created_at_game_time", createdAtGameTime);
        tag.putInt("schema_version", schemaVersion);
        return tag;
    }

    static SettlementRecord load(final CompoundTag tag) {
        if (!tag.hasUUID("id") || !tag.hasUUID("owner_faction_id")) {
            throw new IllegalArgumentException("Settlement record is missing an id");
        }
        final ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension_id"));
        if (dimension == null) {
            throw new IllegalArgumentException("Settlement record has invalid dimension_id");
        }
        final Set<UUID> buildings = new LinkedHashSet<>();
        final ListTag buildingTags = tag.getList("building_ids", Tag.TAG_STRING);
        for (int index = 0; index < buildingTags.size(); index++) {
            final UUID buildingId = UUID.fromString(buildingTags.getString(index));
            if (!buildings.add(buildingId)) {
                throw new IllegalArgumentException("Duplicate building ID in settlement index: " + buildingId);
            }
        }
        return new SettlementRecord(
                tag.getUUID("id"), tag.getUUID("owner_faction_id"), dimension,
                BlockPos.of(tag.getLong("anchor_position")),
                SettlementStatus.valueOf(tag.getString("status")), buildings,
                tag.getLong("created_at_game_time"), tag.getInt("schema_version")
        );
    }
}
