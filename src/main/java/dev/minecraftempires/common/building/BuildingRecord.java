package dev.minecraftempires.common.building;

import dev.minecraftempires.common.civilization.ResourceLedger;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Persistent logical building instance. It does not imply that any chunks or blocks are loaded. */
public final class BuildingRecord {
    public static final int CURRENT_SCHEMA_VERSION = 2;

    private final UUID id;
    private final ResourceLocation blueprintId;
    private final int blueprintVersion;
    private final UUID ownerFactionId;
    private final UUID settlementId;
    private final ResourceLocation dimensionId;
    private final BlockPos origin;
    private final int rotationDegrees;
    private final int placementCursor;
    private final BuildingState state;
    private final long constructionProgress;
    private final long workPointsRequired;
    private final ResourceLedger reservedMaterials;
    private final long durability;
    private final WorldSyncState worldSyncState;
    private final String templateChecksum;
    private final long createdAtGameTime;
    private final int schemaVersion;

    public BuildingRecord(
            final UUID id,
            final ResourceLocation blueprintId,
            final int blueprintVersion,
            final UUID ownerFactionId,
            final UUID settlementId,
            final ResourceLocation dimensionId,
            final BlockPos origin,
            final int rotationDegrees,
            final BuildingState state,
            final long constructionProgress,
            final long workPointsRequired,
            final ResourceLedger reservedMaterials,
            final long durability,
            final WorldSyncState worldSyncState,
            final String templateChecksum,
            final long createdAtGameTime,
            final int schemaVersion
    ) {
        this(id, blueprintId, blueprintVersion, ownerFactionId, settlementId, dimensionId, origin,
                rotationDegrees, state, constructionProgress, workPointsRequired, reservedMaterials,
                durability, worldSyncState, templateChecksum, createdAtGameTime,
                normalizeLegacySchema(schemaVersion), 0);
    }

    private BuildingRecord(
            final UUID id,
            final ResourceLocation blueprintId,
            final int blueprintVersion,
            final UUID ownerFactionId,
            final UUID settlementId,
            final ResourceLocation dimensionId,
            final BlockPos origin,
            final int rotationDegrees,
            final BuildingState state,
            final long constructionProgress,
            final long workPointsRequired,
            final ResourceLedger reservedMaterials,
            final long durability,
            final WorldSyncState worldSyncState,
            final String templateChecksum,
            final long createdAtGameTime,
            final int schemaVersion,
            final int placementCursor
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.blueprintId = Objects.requireNonNull(blueprintId, "blueprintId");
        this.ownerFactionId = Objects.requireNonNull(ownerFactionId, "ownerFactionId");
        this.settlementId = Objects.requireNonNull(settlementId, "settlementId");
        this.dimensionId = Objects.requireNonNull(dimensionId, "dimensionId");
        this.origin = Objects.requireNonNull(origin, "origin").immutable();
        this.state = Objects.requireNonNull(state, "state");
        this.reservedMaterials = Objects.requireNonNull(reservedMaterials, "reservedMaterials");
        this.worldSyncState = Objects.requireNonNull(worldSyncState, "worldSyncState");
        final String checksum = Objects.requireNonNull(templateChecksum, "templateChecksum").trim().toLowerCase();
        if (blueprintVersion < 1 || (rotationDegrees != 0 && rotationDegrees != 90
                && rotationDegrees != 180 && rotationDegrees != 270)) {
            throw new IllegalArgumentException("Invalid blueprint version or rotation for building " + id);
        }
        if (workPointsRequired < 1L || constructionProgress < 0L
                || constructionProgress > workPointsRequired || durability < 0L || createdAtGameTime < 0L) {
            throw new IllegalArgumentException("Invalid progress, durability or timestamp for building " + id);
        }
        if (!checksum.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Building template checksum must be a 64-character SHA-256 hex string");
        }
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported building schema version: " + schemaVersion);
        }
        if (placementCursor < 0 || placementCursor > BlueprintDefinition.MAX_BLOCK_VOLUME) {
            throw new IllegalArgumentException("Invalid building placement cursor: " + placementCursor);
        }
        this.blueprintVersion = blueprintVersion;
        this.rotationDegrees = rotationDegrees;
        this.placementCursor = placementCursor;
        this.constructionProgress = constructionProgress;
        this.workPointsRequired = workPointsRequired;
        this.durability = durability;
        this.templateChecksum = checksum;
        this.createdAtGameTime = createdAtGameTime;
        this.schemaVersion = schemaVersion;
    }

    public static BuildingRecord planned(
            final BlueprintDefinition blueprint,
            final UUID ownerFactionId,
            final UUID settlementId,
            final ResourceLocation dimensionId,
            final BlockPos origin,
            final int rotationDegrees,
            final long gameTime
    ) {
        if (!blueprint.allowsRotation(rotationDegrees)) {
            throw new IllegalArgumentException("Rotation is not allowed by blueprint " + blueprint.id());
        }
        return new BuildingRecord(
                UUID.randomUUID(), blueprint.id(), blueprint.version(), ownerFactionId, settlementId,
                dimensionId, origin, rotationDegrees, BuildingState.PLANNED, 0L, blueprint.workPoints(),
                ResourceLedger.empty(), 0L, WorldSyncState.NOT_PLACED, blueprint.templateChecksum(),
                gameTime, CURRENT_SCHEMA_VERSION
        );
    }

    public UUID id() { return id; }
    public ResourceLocation blueprintId() { return blueprintId; }
    public int blueprintVersion() { return blueprintVersion; }
    public UUID ownerFactionId() { return ownerFactionId; }
    public UUID settlementId() { return settlementId; }
    public ResourceLocation dimensionId() { return dimensionId; }
    public BlockPos origin() { return origin; }
    public int rotationDegrees() { return rotationDegrees; }
    public int placementCursor() { return placementCursor; }
    public BuildingState state() { return state; }
    public long constructionProgress() { return constructionProgress; }
    public long workPointsRequired() { return workPointsRequired; }
    public ResourceLedger reservedMaterials() { return reservedMaterials; }
    public long durability() { return durability; }
    public WorldSyncState worldSyncState() { return worldSyncState; }
    public String templateChecksum() { return templateChecksum; }
    public long createdAtGameTime() { return createdAtGameTime; }
    public int schemaVersion() { return schemaVersion; }

    public BuildingRecord withProgress(final long progress, final ResourceLedger newReservedMaterials) {
        return copy(state, progress, newReservedMaterials, durability, worldSyncState);
    }

    /** Updates cumulative construction progress and its matching remaining material escrow. */
    public BuildingRecord withConstructionProgress(
            final long progress,
            final ResourceLedger newReservedMaterials
    ) {
        if (progress < constructionProgress) {
            throw new IllegalArgumentException("Construction progress cannot move backwards for building " + id);
        }
        return copy(state, progress, newReservedMaterials, durability, worldSyncState);
    }

    public BuildingRecord withReservedMaterials(final ResourceLedger newReservedMaterials) {
        return copy(state, constructionProgress, newReservedMaterials, durability, worldSyncState);
    }

    public BuildingRecord withState(final BuildingState newState, final WorldSyncState newWorldSyncState) {
        return copy(newState, constructionProgress, reservedMaterials, durability, newWorldSyncState);
    }

    public BuildingRecord withDurability(final long newDurability) {
        return copy(state, constructionProgress, reservedMaterials, newDurability, worldSyncState);
    }

    /** Advances the persisted idempotent template-placement cursor. */
    public BuildingRecord withPlacementCursor(final int newCursor, final WorldSyncState newWorldSyncState) {
        return new BuildingRecord(id, blueprintId, blueprintVersion, ownerFactionId, settlementId,
                dimensionId, origin, rotationDegrees, state, constructionProgress, workPointsRequired,
                reservedMaterials, durability, newWorldSyncState, templateChecksum,
                createdAtGameTime, CURRENT_SCHEMA_VERSION, newCursor);
    }

    private BuildingRecord copy(
            final BuildingState newState,
            final long newProgress,
            final ResourceLedger newReservedMaterials,
            final long newDurability,
            final WorldSyncState newWorldSyncState
    ) {
        return new BuildingRecord(id, blueprintId, blueprintVersion, ownerFactionId, settlementId,
                dimensionId, origin, rotationDegrees, newState, newProgress, workPointsRequired,
                newReservedMaterials, newDurability, newWorldSyncState, templateChecksum,
                createdAtGameTime, CURRENT_SCHEMA_VERSION, placementCursor);
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("blueprint_id", blueprintId.toString());
        tag.putInt("blueprint_version", blueprintVersion);
        tag.putUUID("owner_faction_id", ownerFactionId);
        tag.putUUID("settlement_id", settlementId);
        tag.putString("dimension_id", dimensionId.toString());
        tag.putLong("origin", origin.asLong());
        tag.putInt("rotation_degrees", rotationDegrees);
        tag.putInt("placement_cursor", placementCursor);
        tag.putString("state", state.name());
        tag.putLong("construction_progress", constructionProgress);
        tag.putLong("work_points_required", workPointsRequired);
        tag.put("reserved_materials", saveLedger(reservedMaterials));
        tag.putLong("durability", durability);
        tag.putString("world_sync_state", worldSyncState.name());
        tag.putString("template_checksum", templateChecksum);
        tag.putLong("created_at_game_time", createdAtGameTime);
        tag.putInt("schema_version", schemaVersion);
        return tag;
    }

    public static BuildingRecord load(final CompoundTag tag) {
        if (!tag.hasUUID("id") || !tag.hasUUID("owner_faction_id") || !tag.hasUUID("settlement_id")) {
            throw new IllegalArgumentException("Building record is missing a required UUID");
        }
        final ResourceLocation blueprintId = ResourceLocation.tryParse(tag.getString("blueprint_id"));
        final ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString("dimension_id"));
        if (blueprintId == null || dimensionId == null) {
            throw new IllegalArgumentException("Building record has an invalid blueprint or dimension ID");
        }
        final int savedSchema = tag.getInt("schema_version");
        if (savedSchema < 1 || savedSchema > CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported building schema version: " + savedSchema);
        }
        final int placementCursor = tag.contains("placement_cursor", Tag.TAG_INT)
                ? tag.getInt("placement_cursor") : 0;
        return new BuildingRecord(
                tag.getUUID("id"), blueprintId, tag.getInt("blueprint_version"),
                tag.getUUID("owner_faction_id"), tag.getUUID("settlement_id"), dimensionId,
                BlockPos.of(tag.getLong("origin")), tag.getInt("rotation_degrees"),
                BuildingState.valueOf(tag.getString("state")), tag.getLong("construction_progress"),
                tag.getLong("work_points_required"), loadLedger(tag.getCompound("reserved_materials")),
                tag.getLong("durability"), WorldSyncState.valueOf(tag.getString("world_sync_state")),
                tag.getString("template_checksum"), tag.getLong("created_at_game_time"),
                CURRENT_SCHEMA_VERSION, placementCursor
        );
    }

    private static int normalizeLegacySchema(final int schemaVersion) {
        if (schemaVersion < 1 || schemaVersion > CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported building schema version: " + schemaVersion);
        }
        return CURRENT_SCHEMA_VERSION;
    }

    private static CompoundTag saveLedger(final ResourceLedger ledger) {
        final CompoundTag tag = new CompoundTag();
        final ListTag entries = new ListTag();
        ledger.asMap().forEach((resourceId, amount) -> {
            final CompoundTag entry = new CompoundTag();
            entry.putString("id", resourceId.toString());
            entry.putLong("amount", amount);
            entries.add(entry);
        });
        tag.put("entries", entries);
        return tag;
    }

    private static ResourceLedger loadLedger(final CompoundTag tag) {
        final Map<ResourceLocation, Long> amounts = new LinkedHashMap<>();
        final ListTag entries = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size(); index++) {
            final CompoundTag entry = entries.getCompound(index);
            final ResourceLocation resourceId = ResourceLocation.tryParse(entry.getString("id"));
            final long amount = entry.getLong("amount");
            if (resourceId == null || amount < 0L || amounts.putIfAbsent(resourceId, amount) != null) {
                throw new IllegalArgumentException("Invalid or duplicate reserved material entry at index " + index);
            }
        }
        return ResourceLedger.of(amounts);
    }
}
