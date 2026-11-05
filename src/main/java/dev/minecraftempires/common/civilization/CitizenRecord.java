package dev.minecraftempires.common.civilization;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

/**
 * Persistent logical identity of one citizen. This record is authoritative even when no
 * Minecraft entity is currently loaded or spawned for the citizen.
 *
 * The optional entityUuid is an identity link, not a loaded-state flag. A linked entity may
 * legitimately be stored in an unloaded chunk. Therefore callers must never spawn a second
 * entity merely because the linked entity is not currently loaded.
 */
public final class CitizenRecord {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final ResourceLocation UNASSIGNED_PROFESSION = new ResourceLocation("empires", "unassigned");

    private final UUID id;
    private final UUID ownerFactionId;
    private final UUID homeSettlementId;
    private final String displayName;
    private final ResourceLocation speciesId;
    private final ResourceLocation professionId;
    private final CitizenStatus status;
    private final UUID entityUuid;
    private final long createdAtGameTime;
    private final int schemaVersion;

    public CitizenRecord(
            final UUID id,
            final UUID ownerFactionId,
            final UUID homeSettlementId,
            final String displayName,
            final ResourceLocation speciesId,
            final ResourceLocation professionId,
            final CitizenStatus status,
            final UUID entityUuid,
            final long createdAtGameTime,
            final int schemaVersion
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.ownerFactionId = Objects.requireNonNull(ownerFactionId, "ownerFactionId");
        this.homeSettlementId = homeSettlementId;
        final String normalizedName = Objects.requireNonNull(displayName, "displayName").trim();
        if (normalizedName.isEmpty() || normalizedName.length() > 32) {
            throw new IllegalArgumentException("Citizen name must contain 1-32 characters");
        }
        this.displayName = normalizedName;
        this.speciesId = Objects.requireNonNull(speciesId, "speciesId");
        this.professionId = Objects.requireNonNull(professionId, "professionId");
        this.status = Objects.requireNonNull(status, "status");
        if (createdAtGameTime < 0L) {
            throw new IllegalArgumentException("Citizen creation game time cannot be negative");
        }
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported citizen schema version: " + schemaVersion);
        }
        if (status != CitizenStatus.ACTIVE && entityUuid != null) {
            throw new IllegalArgumentException("Only active citizens may have a linked Minecraft entity");
        }
        this.entityUuid = entityUuid;
        this.createdAtGameTime = createdAtGameTime;
        this.schemaVersion = schemaVersion;
    }

    public static CitizenRecord create(
            final UUID ownerFactionId,
            final UUID homeSettlementId,
            final String displayName,
            final ResourceLocation speciesId,
            final long gameTime
    ) {
        return new CitizenRecord(UUID.randomUUID(), ownerFactionId, homeSettlementId, displayName,
                speciesId, UNASSIGNED_PROFESSION, CitizenStatus.ACTIVE, null, gameTime, CURRENT_SCHEMA_VERSION);
    }

    public UUID id() { return id; }
    public UUID ownerFactionId() { return ownerFactionId; }
    public UUID homeSettlementId() { return homeSettlementId; }
    public String displayName() { return displayName; }
    public ResourceLocation speciesId() { return speciesId; }
    public ResourceLocation professionId() { return professionId; }
    public CitizenStatus status() { return status; }
    /** Null means no entity has been linked; non-null does not imply that the entity is currently loaded. */
    public UUID entityUuid() { return entityUuid; }
    public long createdAtGameTime() { return createdAtGameTime; }
    public int schemaVersion() { return schemaVersion; }

    public CitizenRecord withHomeSettlement(final UUID settlementId) {
        return copy(settlementId, professionId, status, entityUuid);
    }

    public CitizenRecord withProfession(final ResourceLocation newProfessionId) {
        return copy(homeSettlementId, newProfessionId, status, entityUuid);
    }

    public CitizenRecord withStatus(final CitizenStatus newStatus) {
        return copy(homeSettlementId, professionId, newStatus,
                newStatus == CitizenStatus.ACTIVE ? entityUuid : null);
    }

    public CitizenRecord withEntityUuid(final UUID newEntityUuid) {
        return copy(homeSettlementId, professionId, status, newEntityUuid);
    }

    private CitizenRecord copy(
            final UUID newHomeSettlementId,
            final ResourceLocation newProfessionId,
            final CitizenStatus newStatus,
            final UUID newEntityUuid
    ) {
        return new CitizenRecord(id, ownerFactionId, newHomeSettlementId, displayName, speciesId,
                newProfessionId, newStatus, newEntityUuid, createdAtGameTime, CURRENT_SCHEMA_VERSION);
    }

    CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putUUID("owner_faction_id", ownerFactionId);
        if (homeSettlementId != null) {
            tag.putUUID("home_settlement_id", homeSettlementId);
        }
        tag.putString("display_name", displayName);
        tag.putString("species_id", speciesId.toString());
        tag.putString("profession_id", professionId.toString());
        tag.putString("status", status.name());
        if (entityUuid != null) {
            tag.putUUID("entity_uuid", entityUuid);
        }
        tag.putLong("created_at_game_time", createdAtGameTime);
        tag.putInt("schema_version", schemaVersion);
        return tag;
    }

    static CitizenRecord load(final CompoundTag tag) {
        if (!tag.hasUUID("id") || !tag.hasUUID("owner_faction_id")) {
            throw new IllegalArgumentException("Citizen record is missing id or owner_faction_id");
        }
        final ResourceLocation species = ResourceLocation.tryParse(tag.getString("species_id"));
        final ResourceLocation profession = ResourceLocation.tryParse(tag.getString("profession_id"));
        if (species == null || profession == null) {
            throw new IllegalArgumentException("Citizen record has invalid species_id or profession_id");
        }
        final UUID homeSettlement = tag.hasUUID("home_settlement_id")
                ? tag.getUUID("home_settlement_id") : null;
        // entity_uuid is optional so existing schema-2 citizen records remain readable.
        final UUID entityUuid = tag.hasUUID("entity_uuid") ? tag.getUUID("entity_uuid") : null;
        return new CitizenRecord(
                tag.getUUID("id"), tag.getUUID("owner_faction_id"), homeSettlement,
                tag.getString("display_name"), species, profession,
                CitizenStatus.valueOf(tag.getString("status")), entityUuid,
                tag.getLong("created_at_game_time"), tag.getInt("schema_version")
        );
    }
}
