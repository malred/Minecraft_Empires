package dev.minecraftempires.common.civilization;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

/** Immutable persistent record for one player, AI, or neutral civilization. */
public final class FactionRecord {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private final UUID id;
    private final String displayName;
    private final ResourceLocation speciesId;
    private final ControllerType controllerType;
    private final FactionStatus status;
    private final UUID capitalSettlementId;
    private final ResourceLedger resources;
    private final int populationCountCache;
    private final long createdAtGameTime;
    private final int schemaVersion;

    public FactionRecord(
            final UUID id,
            final String displayName,
            final ResourceLocation speciesId,
            final ControllerType controllerType,
            final FactionStatus status,
            final UUID capitalSettlementId,
            final ResourceLedger resources,
            final int populationCountCache,
            final long createdAtGameTime,
            final int schemaVersion
    ) {
        this.id = Objects.requireNonNull(id, "id");
        final String normalizedName = Objects.requireNonNull(displayName, "displayName").trim();
        if (normalizedName.isEmpty() || normalizedName.length() > 48) {
            throw new IllegalArgumentException("Faction name must contain 1-48 characters");
        }
        this.displayName = normalizedName;
        this.speciesId = Objects.requireNonNull(speciesId, "speciesId");
        this.controllerType = Objects.requireNonNull(controllerType, "controllerType");
        this.status = Objects.requireNonNull(status, "status");
        this.capitalSettlementId = capitalSettlementId;
        this.resources = Objects.requireNonNull(resources, "resources");
        if (populationCountCache < 0) {
            throw new IllegalArgumentException("Population cache cannot be negative");
        }
        if (createdAtGameTime < 0L) {
            throw new IllegalArgumentException("Creation game time cannot be negative");
        }
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported faction schema version: " + schemaVersion);
        }
        this.populationCountCache = populationCountCache;
        this.createdAtGameTime = createdAtGameTime;
        this.schemaVersion = schemaVersion;
    }

    public static FactionRecord create(
            final String name,
            final ResourceLocation speciesId,
            final ControllerType controllerType,
            final long gameTime
    ) {
        return new FactionRecord(UUID.randomUUID(), name, speciesId, controllerType,
                FactionStatus.ACTIVE, null, ResourceLedger.empty(), 0, gameTime, CURRENT_SCHEMA_VERSION);
    }

    public UUID id() { return id; }
    public String displayName() { return displayName; }
    public ResourceLocation speciesId() { return speciesId; }
    public ControllerType controllerType() { return controllerType; }
    public FactionStatus status() { return status; }
    public UUID capitalSettlementId() { return capitalSettlementId; }
    public ResourceLedger resources() { return resources; }
    public int populationCountCache() { return populationCountCache; }
    public long createdAtGameTime() { return createdAtGameTime; }
    public int schemaVersion() { return schemaVersion; }

    public FactionRecord withCapitalSettlement(final UUID settlementId) {
        return copy(status, settlementId, resources, populationCountCache);
    }

    public FactionRecord withStatus(final FactionStatus newStatus) {
        return copy(newStatus, capitalSettlementId, resources, populationCountCache);
    }

    public FactionRecord withResources(final ResourceLedger newResources) {
        return copy(status, capitalSettlementId, newResources, populationCountCache);
    }

    public FactionRecord withPopulationCountCache(final int count) {
        return copy(status, capitalSettlementId, resources, count);
    }

    private FactionRecord copy(
            final FactionStatus newStatus,
            final UUID newCapitalSettlementId,
            final ResourceLedger newResources,
            final int newPopulationCountCache
    ) {
        return new FactionRecord(id, displayName, speciesId, controllerType, newStatus,
                newCapitalSettlementId, newResources, newPopulationCountCache,
                createdAtGameTime, CURRENT_SCHEMA_VERSION);
    }

    CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("display_name", displayName);
        tag.putString("species_id", speciesId.toString());
        tag.putString("controller_type", controllerType.name());
        tag.putString("status", status.name());
        if (capitalSettlementId != null) {
            tag.putUUID("capital_settlement_id", capitalSettlementId);
        }
        tag.put("resources", resources.save());
        tag.putInt("population_count_cache", populationCountCache);
        tag.putLong("created_at_game_time", createdAtGameTime);
        tag.putInt("schema_version", schemaVersion);
        return tag;
    }

    static FactionRecord load(final CompoundTag tag) {
        if (!tag.hasUUID("id")) {
            throw new IllegalArgumentException("Faction record is missing id");
        }
        final ResourceLocation species = ResourceLocation.tryParse(tag.getString("species_id"));
        if (species == null) {
            throw new IllegalArgumentException("Faction record has invalid species_id");
        }
        final UUID capitalId = tag.hasUUID("capital_settlement_id")
                ? tag.getUUID("capital_settlement_id") : null;
        return new FactionRecord(
                tag.getUUID("id"),
                tag.getString("display_name"),
                species,
                ControllerType.valueOf(tag.getString("controller_type")),
                FactionStatus.valueOf(tag.getString("status")),
                capitalId,
                ResourceLedger.load(tag.getCompound("resources")),
                tag.getInt("population_count_cache"),
                tag.getLong("created_at_game_time"),
                tag.getInt("schema_version")
        );
    }
}
