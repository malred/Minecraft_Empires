package dev.minecraftempires.common.civilization;

import dev.minecraftempires.common.building.BuildingRecord;
import dev.minecraftempires.common.building.BuildingState;
import net.minecraft.nbt.CompoundTag;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Durable work assignment for one construction project. It contains logical IDs only;
 * it never assumes that the citizen entity or the target chunk is currently loaded.
 */
public final class BuilderJobRecord {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int DEFAULT_PRIORITY = 50;
    public static final int MIN_PRIORITY = 0;
    public static final int MAX_PRIORITY = 100;

    private final UUID id;
    private final UUID buildingId;
    private final UUID ownerFactionId;
    private final UUID settlementId;
    private final UUID assignedCitizenId;
    private final BuilderJobState state;
    private final int priority;
    private final int retryCount;
    private final long createdAtGameTime;
    private final long updatedAtGameTime;
    private final int schemaVersion;

    public BuilderJobRecord(
            final UUID id,
            final UUID buildingId,
            final UUID ownerFactionId,
            final UUID settlementId,
            final UUID assignedCitizenId,
            final BuilderJobState state,
            final int priority,
            final int retryCount,
            final long createdAtGameTime,
            final long updatedAtGameTime,
            final int schemaVersion
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.buildingId = Objects.requireNonNull(buildingId, "buildingId");
        this.ownerFactionId = Objects.requireNonNull(ownerFactionId, "ownerFactionId");
        this.settlementId = Objects.requireNonNull(settlementId, "settlementId");
        this.state = Objects.requireNonNull(state, "state");
        if (priority < MIN_PRIORITY || priority > MAX_PRIORITY) {
            throw new IllegalArgumentException("Builder job priority must be between "
                    + MIN_PRIORITY + " and " + MAX_PRIORITY);
        }
        if (retryCount < 0 || createdAtGameTime < 0L || updatedAtGameTime < createdAtGameTime) {
            throw new IllegalArgumentException("Invalid builder job retry count or timestamp");
        }
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported builder job schema version: " + schemaVersion);
        }
        if (state.requiresAssignedCitizen() && assignedCitizenId == null) {
            throw new IllegalArgumentException(state + " builder job must have an assigned citizen");
        }
        if ((state == BuilderJobState.AVAILABLE || state == BuilderJobState.BLOCKED)
                && assignedCitizenId != null) {
            throw new IllegalArgumentException(state + " builder job cannot retain an assigned citizen");
        }
        this.assignedCitizenId = assignedCitizenId;
        this.priority = priority;
        this.retryCount = retryCount;
        this.createdAtGameTime = createdAtGameTime;
        this.updatedAtGameTime = updatedAtGameTime;
        this.schemaVersion = schemaVersion;
    }

    public static BuilderJobRecord forBuilding(
            final BuildingRecord building,
            final int priority,
            final long gameTime
    ) {
        Objects.requireNonNull(building, "building");
        if (building.state() != BuildingState.UNDER_CONSTRUCTION) {
            throw new IllegalArgumentException("Builder jobs require an UNDER_CONSTRUCTION building: "
                    + building.id());
        }
        // The building UUID is stable, so migration and retries cannot create duplicate jobs.
        final UUID jobId = UUID.nameUUIDFromBytes(
                ("minecraft_empires:builder_job:" + building.id()).getBytes(StandardCharsets.UTF_8));
        return new BuilderJobRecord(jobId, building.id(), building.ownerFactionId(), building.settlementId(),
                null, BuilderJobState.AVAILABLE, priority, 0, gameTime, gameTime, CURRENT_SCHEMA_VERSION);
    }

    public UUID id() { return id; }
    public UUID buildingId() { return buildingId; }
    public UUID ownerFactionId() { return ownerFactionId; }
    public UUID settlementId() { return settlementId; }
    public UUID assignedCitizenId() { return assignedCitizenId; }
    public BuilderJobState state() { return state; }
    public int priority() { return priority; }
    public int retryCount() { return retryCount; }
    public long createdAtGameTime() { return createdAtGameTime; }
    public long updatedAtGameTime() { return updatedAtGameTime; }
    public int schemaVersion() { return schemaVersion; }

    public boolean isActive() { return state.isActive(); }

    public BuilderJobRecord claim(final UUID citizenId, final long gameTime) {
        if (state != BuilderJobState.AVAILABLE) {
            throw new IllegalStateException("Only AVAILABLE builder jobs can be claimed: " + id);
        }
        return copy(Objects.requireNonNull(citizenId, "citizenId"), BuilderJobState.CLAIMED,
                retryCount, gameTime);
    }

    public BuilderJobRecord startWorking(final long gameTime) {
        if (state != BuilderJobState.CLAIMED) {
            throw new IllegalStateException("Only CLAIMED builder jobs can start work: " + id);
        }
        return copy(assignedCitizenId, BuilderJobState.WORKING, retryCount, gameTime);
    }

    public BuilderJobRecord release(final long gameTime) {
        if (state != BuilderJobState.CLAIMED && state != BuilderJobState.TRAVELLING
                && state != BuilderJobState.WORKING) {
            throw new IllegalStateException("Only assigned builder jobs can be released: " + id);
        }
        return copy(null, BuilderJobState.AVAILABLE, Math.addExact(retryCount, 1), gameTime);
    }

    public BuilderJobRecord block(final long gameTime) {
        if (state != BuilderJobState.CLAIMED && state != BuilderJobState.TRAVELLING
                && state != BuilderJobState.WORKING) {
            throw new IllegalStateException("Only assigned builder jobs can be blocked: " + id);
        }
        return copy(null, BuilderJobState.BLOCKED, Math.addExact(retryCount, 1), gameTime);
    }

    public BuilderJobRecord retry(final long gameTime) {
        if (state != BuilderJobState.BLOCKED) {
            throw new IllegalStateException("Only BLOCKED builder jobs can be retried: " + id);
        }
        return copy(null, BuilderJobState.AVAILABLE, retryCount, gameTime);
    }

    public BuilderJobRecord complete(final long gameTime) {
        if (!state.isActive()) {
            if (state == BuilderJobState.COMPLETED) {
                return this;
            }
            throw new IllegalStateException("Terminal builder job cannot be completed: " + id);
        }
        return copy(assignedCitizenId, BuilderJobState.COMPLETED, retryCount, gameTime);
    }

    public boolean sameSnapshot(final BuilderJobRecord other) {
        return other != null
                && id.equals(other.id)
                && buildingId.equals(other.buildingId)
                && ownerFactionId.equals(other.ownerFactionId)
                && settlementId.equals(other.settlementId)
                && Objects.equals(assignedCitizenId, other.assignedCitizenId)
                && state == other.state
                && priority == other.priority
                && retryCount == other.retryCount
                && createdAtGameTime == other.createdAtGameTime
                && updatedAtGameTime == other.updatedAtGameTime
                && schemaVersion == other.schemaVersion;
    }

    private BuilderJobRecord copy(
            final UUID newAssignedCitizenId,
            final BuilderJobState newState,
            final int newRetryCount,
            final long gameTime
    ) {
        if (gameTime < updatedAtGameTime) {
            throw new IllegalArgumentException("Builder job timestamp cannot move backwards: " + id);
        }
        return new BuilderJobRecord(id, buildingId, ownerFactionId, settlementId, newAssignedCitizenId,
                newState, priority, newRetryCount, createdAtGameTime, gameTime, CURRENT_SCHEMA_VERSION);
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putUUID("building_id", buildingId);
        tag.putUUID("owner_faction_id", ownerFactionId);
        tag.putUUID("settlement_id", settlementId);
        if (assignedCitizenId != null) {
            tag.putUUID("assigned_citizen_id", assignedCitizenId);
        }
        tag.putString("state", state.name());
        tag.putInt("priority", priority);
        tag.putInt("retry_count", retryCount);
        tag.putLong("created_at_game_time", createdAtGameTime);
        tag.putLong("updated_at_game_time", updatedAtGameTime);
        tag.putInt("schema_version", schemaVersion);
        return tag;
    }

    public static BuilderJobRecord load(final CompoundTag tag) {
        if (!tag.hasUUID("id") || !tag.hasUUID("building_id")
                || !tag.hasUUID("owner_faction_id") || !tag.hasUUID("settlement_id")) {
            throw new IllegalArgumentException("Builder job is missing a required UUID");
        }
        final UUID assignedCitizen = tag.hasUUID("assigned_citizen_id")
                ? tag.getUUID("assigned_citizen_id") : null;
        return new BuilderJobRecord(tag.getUUID("id"), tag.getUUID("building_id"),
                tag.getUUID("owner_faction_id"), tag.getUUID("settlement_id"), assignedCitizen,
                BuilderJobState.valueOf(tag.getString("state")), tag.getInt("priority"),
                tag.getInt("retry_count"), tag.getLong("created_at_game_time"),
                tag.getLong("updated_at_game_time"), tag.getInt("schema_version"));
    }
}
