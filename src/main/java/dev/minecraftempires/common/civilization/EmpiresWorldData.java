package dev.minecraftempires.common.civilization;

import dev.minecraftempires.MinecraftEmpires;
import dev.minecraftempires.common.building.BlueprintCatalog;
import dev.minecraftempires.common.building.BlueprintDefinition;
import dev.minecraftempires.common.building.ConstructionProject;
import dev.minecraftempires.common.building.BuildingRecord;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Authoritative world-wide civilization registry. It is always attached to the Overworld,
 * so Nether/End access resolves to the same registry instead of creating dimension copies.
 */
public final class EmpiresWorldData extends SavedData {
    public static final String DATA_NAME = "minecraft_empires_world";
    public static final int CURRENT_SCHEMA_VERSION = 4;
    private static final int MIN_SUPPORTED_SCHEMA_VERSION = 1;

    private final UUID worldId;
    private final int sourceSchemaVersion;
    private final Map<UUID, FactionRecord> factions = new LinkedHashMap<>();
    private final Map<UUID, SettlementRecord> settlements = new LinkedHashMap<>();
    private final Map<UUID, CitizenRecord> citizens = new LinkedHashMap<>();
    private final Map<UUID, BuildingRecord> buildings = new LinkedHashMap<>();
    private final Map<UUID, BuilderJobRecord> builderJobs = new LinkedHashMap<>();
    private long simulationClock;
    private boolean readOnly;
    private String readOnlyReason = "";
    private CompoundTag preservedRawTag;

    /** Constructor used only by DimensionDataStorage for a brand-new world record. */
    public EmpiresWorldData() {
        this(UUID.randomUUID(), CURRENT_SCHEMA_VERSION, true);
    }

    private EmpiresWorldData(final UUID worldId, final int sourceSchemaVersion, final boolean markDirty) {
        this.worldId = worldId;
        this.sourceSchemaVersion = sourceSchemaVersion;
        if (markDirty) {
            setDirty();
        }
    }

    public static EmpiresWorldData get(final MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                EmpiresWorldData::load,
                EmpiresWorldData::new,
                DATA_NAME
        );
    }

    public static EmpiresWorldData get(final ServerLevel anyLevel) {
        return get(anyLevel.getServer());
    }

    public UUID worldId() { return worldId; }
    public long simulationClock() { return simulationClock; }
    public int factionCount() { return factions.size(); }
    public int settlementCount() { return settlements.size(); }
    public int citizenCount() { return citizens.size(); }
    public int buildingCount() { return buildings.size(); }
    public int builderJobCount() { return builderJobs.size(); }
    public int schemaVersion() { return sourceSchemaVersion; }
    public boolean isReadOnly() { return readOnly; }
    public String readOnlyReason() { return readOnlyReason; }

    public Optional<FactionRecord> findFaction(final UUID id) {
        return Optional.ofNullable(factions.get(id));
    }

    public List<FactionRecord> factions() {
        return List.copyOf(factions.values());
    }

    public Optional<SettlementRecord> findSettlement(final UUID id) {
        return Optional.ofNullable(settlements.get(id));
    }

    public List<SettlementRecord> settlements() {
        return List.copyOf(settlements.values());
    }

    public Optional<CitizenRecord> findCitizen(final UUID id) {
        return Optional.ofNullable(citizens.get(id));
    }

    public List<CitizenRecord> citizens() {
        return List.copyOf(citizens.values());
    }

    public List<CitizenRecord> citizensForFaction(final UUID factionId) {
        return citizens.values().stream()
                .filter(citizen -> citizen.ownerFactionId().equals(factionId))
                .collect(Collectors.toUnmodifiableList());
    }

    public Optional<BuildingRecord> findBuilding(final UUID id) {
        return Optional.ofNullable(buildings.get(id));
    }

    public List<BuildingRecord> buildings() {
        return List.copyOf(buildings.values());
    }

    public List<BuildingRecord> buildingsForSettlement(final UUID settlementId) {
        return buildings.values().stream()
                .filter(building -> building.settlementId().equals(settlementId))
                .collect(Collectors.toUnmodifiableList());
    }

    public Optional<BuilderJobRecord> findBuilderJob(final UUID id) {
        return Optional.ofNullable(builderJobs.get(id));
    }

    public List<BuilderJobRecord> builderJobs() {
        return List.copyOf(builderJobs.values());
    }

    public List<BuilderJobRecord> builderJobsForBuilding(final UUID buildingId) {
        return builderJobs.values().stream()
                .filter(job -> job.buildingId().equals(buildingId))
                .collect(Collectors.toUnmodifiableList());
    }

    void putFaction(final FactionRecord record) {
        ensureWritable();
        factions.put(record.id(), record);
        setDirty();
    }

    void putSettlement(final SettlementRecord record) {
        ensureWritable();
        if (!factions.containsKey(record.ownerFactionId())) {
            throw new IllegalArgumentException("Settlement owner faction does not exist: " + record.ownerFactionId());
        }
        for (UUID indexedBuildingId : record.buildingIds()) {
            final BuildingRecord building = buildings.get(indexedBuildingId);
            if (building == null || !building.settlementId().equals(record.id())) {
                throw new IllegalArgumentException("Settlement building index contains an unknown or foreign building: " + indexedBuildingId);
            }
        }
        settlements.put(record.id(), record);
        setDirty();
    }

    void putCitizen(final CitizenRecord record) {
        ensureWritable();
        if (!factions.containsKey(record.ownerFactionId())) {
            throw new IllegalArgumentException("Citizen owner faction does not exist: " + record.ownerFactionId());
        }
        if (record.homeSettlementId() != null) {
            final SettlementRecord home = settlements.get(record.homeSettlementId());
            if (home == null) {
                throw new IllegalArgumentException("Citizen home settlement does not exist: " + record.homeSettlementId());
            }
            if (!home.ownerFactionId().equals(record.ownerFactionId())) {
                throw new IllegalArgumentException("Citizen home settlement must belong to the same faction");
            }
        }
        for (BuilderJobRecord job : builderJobs.values()) {
            if (job.isActive() && record.id().equals(job.assignedCitizenId())
                    && (record.status() != CitizenStatus.ACTIVE
                    || !record.ownerFactionId().equals(job.ownerFactionId())
                    || !record.professionId().equals(BuilderJobRecord.BUILDER_PROFESSION_ID)
                    || (record.homeSettlementId() != null
                    && !record.homeSettlementId().equals(job.settlementId())))) {
                throw new IllegalStateException("Citizen is assigned to active builder job " + job.id()
                        + "; release that job before changing status or profession");
            }
        }
        citizens.put(record.id(), record);
        setDirty();
    }

    /** Persists a compare-and-set builder-job transition after validating ownership and worker eligibility. */
    void putBuilderJob(final BuilderJobRecord expected, final BuilderJobRecord updated) {
        ensureWritable();
        final BuilderJobRecord current = builderJobs.get(updated.id());
        if (expected == null) {
            if (current != null) {
                throw new IllegalStateException("Builder job already exists: " + updated.id());
            }
            final BuildingRecord building = buildings.get(updated.buildingId());
            if (building == null
                    || !updated.id().equals(BuilderJobRecord.forBuilding(
                    building, updated.priority(), updated.createdAtGameTime()).id())
                    || updated.state() != BuilderJobState.AVAILABLE
                    || updated.assignedCitizenId() != null
                    || updated.retryCount() != 0) {
                throw new IllegalArgumentException("A new builder job must be the stable AVAILABLE job for its building: "
                        + updated.id());
            }
        } else {
            if (current == null || !current.sameSnapshot(expected)) {
                throw new IllegalStateException("Builder job changed before update; reload its latest state: "
                        + updated.id());
            }
            if (!current.buildingId().equals(updated.buildingId())
                    || !current.ownerFactionId().equals(updated.ownerFactionId())
                    || !current.settlementId().equals(updated.settlementId())
                    || current.priority() != updated.priority()
                    || current.createdAtGameTime() != updated.createdAtGameTime()
                    || current.schemaVersion() != updated.schemaVersion()) {
                throw new IllegalArgumentException("Builder job update cannot change its identity or priority: "
                        + updated.id());
            }
            if (updated.updatedAtGameTime() < current.updatedAtGameTime()
                    || updated.retryCount() < current.retryCount()) {
                throw new IllegalArgumentException("Builder job timestamps and retry count cannot move backwards: "
                        + updated.id());
            }
            final boolean workerRemainsAssigned = current.state().requiresAssignedCitizen()
                    && updated.state().requiresAssignedCitizen();
            if (workerRemainsAssigned
                    && !java.util.Objects.equals(current.assignedCitizenId(), updated.assignedCitizenId())) {
                throw new IllegalArgumentException("A claimed builder job cannot switch workers without release: "
                        + updated.id());
            }
            final boolean releasingWorker = current.state().requiresAssignedCitizen()
                    && (updated.state() == BuilderJobState.AVAILABLE || updated.state() == BuilderJobState.BLOCKED);
            final int expectedRetryCount = releasingWorker
                    ? Math.addExact(current.retryCount(), 1) : current.retryCount();
            if (updated.retryCount() != expectedRetryCount) {
                throw new IllegalArgumentException("Builder job retry count is inconsistent with its transition: "
                        + updated.id());
            }
            if (!isAllowedBuilderJobTransition(current.state(), updated.state())) {
                throw new IllegalStateException("Invalid builder job transition: " + current.state()
                        + " -> " + updated.state() + " for " + updated.id());
            }
        }
        validateBuilderJobForWrite(updated);
        builderJobs.put(updated.id(), updated);
        setDirty();
    }

    private void validateBuilderJobForWrite(final BuilderJobRecord job) {
        final BuildingRecord building = buildings.get(job.buildingId());
        if (building == null || !building.ownerFactionId().equals(job.ownerFactionId())
                || !building.settlementId().equals(job.settlementId())) {
            throw new IllegalArgumentException("Builder job references a missing or foreign building: "
                    + job.buildingId());
        }
        if (job.isActive() && building.state() != dev.minecraftempires.common.building.BuildingState.UNDER_CONSTRUCTION) {
            throw new IllegalStateException("Active builder jobs require an UNDER_CONSTRUCTION building: "
                    + building.id());
        }
        if (job.state() == BuilderJobState.COMPLETED
                && building.state() != dev.minecraftempires.common.building.BuildingState.ACTIVE) {
            throw new IllegalStateException("Completed builder jobs require an ACTIVE building: " + building.id());
        }
        if (job.state().requiresAssignedCitizen()) {
            final CitizenRecord citizen = citizens.get(job.assignedCitizenId());
            if (citizen == null || citizen.status() != CitizenStatus.ACTIVE
                    || !citizen.ownerFactionId().equals(job.ownerFactionId())
                    || !citizen.professionId().equals(BuilderJobRecord.BUILDER_PROFESSION_ID)
                    || (citizen.homeSettlementId() != null
                    && !citizen.homeSettlementId().equals(job.settlementId()))) {
                throw new IllegalArgumentException("Builder job worker must be an active builder of the owning faction "
                        + "and settlement: " + job.assignedCitizenId());
            }
            for (BuilderJobRecord other : builderJobs.values()) {
                if (!other.id().equals(job.id()) && other.isActive()
                        && job.assignedCitizenId().equals(other.assignedCitizenId())) {
                    throw new IllegalStateException("Citizen " + job.assignedCitizenId()
                            + " already owns active builder job " + other.id());
                }
            }
        }
        if (job.isActive()) {
            for (BuilderJobRecord other : builderJobs.values()) {
                if (!other.id().equals(job.id()) && other.isActive()
                        && job.buildingId().equals(other.buildingId())) {
                    throw new IllegalStateException("Building already has active builder job " + other.id());
                }
            }
        }
    }

    private static boolean isAllowedBuilderJobTransition(
            final BuilderJobState from,
            final BuilderJobState to
    ) {
        if (from == to) {
            return true;
        }
        return switch (from) {
            case AVAILABLE -> to == BuilderJobState.CLAIMED || to == BuilderJobState.COMPLETED;
            case CLAIMED -> to == BuilderJobState.TRAVELLING || to == BuilderJobState.WORKING
                    || to == BuilderJobState.AVAILABLE || to == BuilderJobState.BLOCKED
                    || to == BuilderJobState.COMPLETED;
            case TRAVELLING -> to == BuilderJobState.WORKING || to == BuilderJobState.AVAILABLE
                    || to == BuilderJobState.BLOCKED || to == BuilderJobState.COMPLETED;
            case WORKING -> to == BuilderJobState.AVAILABLE || to == BuilderJobState.BLOCKED
                    || to == BuilderJobState.COMPLETED;
            case BLOCKED -> to == BuilderJobState.AVAILABLE || to == BuilderJobState.COMPLETED;
            case COMPLETED, FAILED, CANCELLED -> false;
        };
    }

    /** Atomically updates the building map and its owning settlement's reverse index. */
    void putBuilding(final BuildingRecord record) {
        ensureWritable();
        if (!factions.containsKey(record.ownerFactionId())) {
            throw new IllegalArgumentException("Building owner faction does not exist: " + record.ownerFactionId());
        }
        final SettlementRecord settlement = settlements.get(record.settlementId());
        if (settlement == null) {
            throw new IllegalArgumentException("Building settlement does not exist: " + record.settlementId());
        }
        if (!settlement.ownerFactionId().equals(record.ownerFactionId())) {
            throw new IllegalArgumentException("Building owner faction must match its settlement owner");
        }
        if (!settlement.dimensionId().equals(record.dimensionId())) {
            throw new IllegalArgumentException("Building dimension must match its settlement dimension");
        }

        final BuildingRecord previous = buildings.get(record.id());
        if (previous != null && (!previous.ownerFactionId().equals(record.ownerFactionId())
                || !previous.settlementId().equals(record.settlementId())
                || !previous.blueprintId().equals(record.blueprintId())
                || previous.blueprintVersion() != record.blueprintVersion()
                || !previous.dimensionId().equals(record.dimensionId())
                || !previous.origin().equals(record.origin())
                || previous.rotationDegrees() != record.rotationDegrees())) {
            throw new IllegalArgumentException("A persisted building cannot change its identity, owner, blueprint or placement: " + record.id());
        }

        // Build both replacement values before publishing either one; all calls run on the server thread.
        final SettlementRecord indexedSettlement = settlement.withBuildingId(record.id());
        buildings.put(record.id(), record);
        settlements.put(settlement.id(), indexedSettlement);
        setDirty();
    }

    /**
     * Publishes a faction inventory debit and its building escrow as one SavedData mutation.
     * All replacement values are validated and prepared before either map is changed.
     */
    void putBuildingReservation(final FactionRecord updatedFaction, final BuildingRecord reservedBuilding) {
        ensureWritable();

        final FactionRecord currentFaction = factions.get(updatedFaction.id());
        if (currentFaction == null) {
            throw new IllegalArgumentException("Reservation faction does not exist: " + updatedFaction.id());
        }
        final BuildingRecord currentBuilding = buildings.get(reservedBuilding.id());
        if (currentBuilding == null) {
            throw new IllegalArgumentException("Reservation building does not exist: " + reservedBuilding.id());
        }
        if (!currentBuilding.ownerFactionId().equals(updatedFaction.id())
                || !reservedBuilding.ownerFactionId().equals(updatedFaction.id())) {
            throw new IllegalArgumentException("Reservation faction does not own building " + reservedBuilding.id());
        }
        if (currentBuilding.state() != dev.minecraftempires.common.building.BuildingState.PLANNED
                || reservedBuilding.state() != dev.minecraftempires.common.building.BuildingState.RESERVED) {
            throw new IllegalStateException("Atomic material reservation requires PLANNED -> RESERVED transition");
        }
        final BlueprintDefinition blueprint = BlueprintCatalog.find(currentBuilding.blueprintId())
                .orElseThrow(() -> new IllegalStateException("Reservation references unknown blueprint: "
                        + currentBuilding.blueprintId()));
        if (currentBuilding.blueprintVersion() != blueprint.version()
                || reservedBuilding.blueprintVersion() != blueprint.version()) {
            throw new IllegalStateException("Reservation blueprint version does not match the catalog: "
                    + currentBuilding.blueprintId());
        }
        if (!reservedBuilding.reservedMaterials().asMap().equals(blueprint.materials().asMap())) {
            throw new IllegalArgumentException("Building escrow must equal the full blueprint material cost");
        }
        final ResourceLedger expectedRemaining;
        try {
            expectedRemaining = currentFaction.resources().minus(reservedBuilding.reservedMaterials());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Faction inventory cannot cover the requested reservation: "
                    + exception.getMessage(), exception);
        }
        if (!updatedFaction.resources().asMap().equals(expectedRemaining.asMap())) {
            throw new IllegalArgumentException("Atomic reservation faction balance must equal the exact escrow debit");
        }
        if (!currentFaction.displayName().equals(updatedFaction.displayName())
                || !currentFaction.speciesId().equals(updatedFaction.speciesId())
                || currentFaction.controllerType() != updatedFaction.controllerType()
                || currentFaction.status() != updatedFaction.status()
                || !java.util.Objects.equals(currentFaction.capitalSettlementId(), updatedFaction.capitalSettlementId())
                || currentFaction.populationCountCache() != updatedFaction.populationCountCache()
                || currentFaction.createdAtGameTime() != updatedFaction.createdAtGameTime()
                || currentFaction.schemaVersion() != updatedFaction.schemaVersion()) {
            throw new IllegalArgumentException("Material reservation cannot change unrelated faction fields");
        }
        if (!currentBuilding.reservedMaterials().isEmpty()) {
            throw new IllegalStateException("PLANNED building already contains reserved materials: " + currentBuilding.id());
        }
        if (!currentBuilding.settlementId().equals(reservedBuilding.settlementId())
                || !currentBuilding.blueprintId().equals(reservedBuilding.blueprintId())
                || currentBuilding.blueprintVersion() != reservedBuilding.blueprintVersion()
                || !currentBuilding.dimensionId().equals(reservedBuilding.dimensionId())
                || !currentBuilding.origin().equals(reservedBuilding.origin())
                || currentBuilding.rotationDegrees() != reservedBuilding.rotationDegrees()
                || currentBuilding.placementCursor() != reservedBuilding.placementCursor()
                || currentBuilding.worldSyncState() != reservedBuilding.worldSyncState()
                || currentBuilding.constructionProgress() != reservedBuilding.constructionProgress()
                || currentBuilding.workPointsRequired() != reservedBuilding.workPointsRequired()
                || currentBuilding.durability() != reservedBuilding.durability()
                || currentBuilding.createdAtGameTime() != reservedBuilding.createdAtGameTime()
                || currentBuilding.schemaVersion() != reservedBuilding.schemaVersion()
                || !currentBuilding.templateChecksum().equals(reservedBuilding.templateChecksum())
                || !currentBuilding.templateChecksum().equals(blueprint.templateChecksum())) {
            throw new IllegalArgumentException("Reservation cannot change the building identity or placement: "
                    + reservedBuilding.id());
        }

        final SettlementRecord settlement = settlements.get(currentBuilding.settlementId());
        if (settlement == null
                || !settlement.ownerFactionId().equals(updatedFaction.id())
                || !settlement.dimensionId().equals(currentBuilding.dimensionId())) {
            throw new IllegalStateException("Reservation building has an invalid owning settlement: "
                    + currentBuilding.id());
        }
        final SettlementRecord indexedSettlement = settlement.withBuildingId(currentBuilding.id());

        // Single server-thread publication: readers never observe a debit without its matching escrow.
        factions.put(updatedFaction.id(), updatedFaction);
        buildings.put(reservedBuilding.id(), reservedBuilding);
        settlements.put(settlement.id(), indexedSettlement);
        setDirty();
    }

    /**
     * Compare-and-set write for construction project progress. The faction inventory
     * is not touched here: reserved materials are consumed only by reducing the
     * project's escrow in the same building-record mutation.
     */
    void putConstructionUpdate(final BuildingRecord expectedCurrent, final BuildingRecord updatedBuilding) {
        putConstructionUpdate(expectedCurrent, updatedBuilding, null);
    }

    void putConstructionStart(
            final BuildingRecord expectedCurrent,
            final BuildingRecord updatedBuilding,
            final BuilderJobRecord newBuilderJob
    ) {
        if (newBuilderJob == null) {
            throw new IllegalArgumentException("Construction start requires a builder job");
        }
        putConstructionUpdate(expectedCurrent, updatedBuilding, newBuilderJob);
    }

    private void putConstructionUpdate(
            final BuildingRecord expectedCurrent,
            final BuildingRecord updatedBuilding,
            final BuilderJobRecord newBuilderJob
    ) {
        ensureWritable();
        final BuildingRecord current = buildings.get(expectedCurrent.id());
        if (current == null) {
            throw new IllegalArgumentException("Construction building does not exist: " + expectedCurrent.id());
        }
        if (!sameConstructionSnapshot(current, expectedCurrent)) {
            throw new IllegalStateException("Construction project changed before update; reload its latest progress: "
                    + current.id());
        }
        if (!sameBuildingIdentity(current, updatedBuilding)) {
            throw new IllegalArgumentException("Construction update cannot change building identity or placement: "
                    + current.id());
        }

        final BlueprintDefinition blueprint = BlueprintCatalog.find(current.blueprintId())
                .orElseThrow(() -> new IllegalStateException("Construction references unknown blueprint: "
                        + current.blueprintId()));
        if (current.blueprintVersion() != blueprint.version()
                || current.workPointsRequired() != blueprint.workPoints()
                || !current.templateChecksum().equals(blueprint.templateChecksum())) {
            throw new IllegalStateException("Construction blueprint definition does not match building "
                    + current.id());
        }

        if (current.state() == dev.minecraftempires.common.building.BuildingState.RESERVED) {
            if (updatedBuilding.state() != dev.minecraftempires.common.building.BuildingState.UNDER_CONSTRUCTION
                    || current.constructionProgress() != 0L
                    || updatedBuilding.constructionProgress() != 0L
                    || !current.reservedMaterials().asMap().equals(blueprint.materials().asMap())
                    || !updatedBuilding.reservedMaterials().asMap().equals(current.reservedMaterials().asMap())
                    || (current.worldSyncState() != dev.minecraftempires.common.building.WorldSyncState.NOT_PLACED
                    && current.worldSyncState() != dev.minecraftempires.common.building.WorldSyncState.SYNCED)
                    || (current.worldSyncState() == dev.minecraftempires.common.building.WorldSyncState.NOT_PLACED
                    && updatedBuilding.worldSyncState() != dev.minecraftempires.common.building.WorldSyncState.PENDING)
                    || (current.worldSyncState() == dev.minecraftempires.common.building.WorldSyncState.SYNCED
                    && updatedBuilding.worldSyncState() != dev.minecraftempires.common.building.WorldSyncState.SYNCED)) {
                throw new IllegalStateException("Invalid RESERVED -> UNDER_CONSTRUCTION transition for building "
                        + current.id());
            }
        } else if (current.state() == dev.minecraftempires.common.building.BuildingState.UNDER_CONSTRUCTION) {
            if (updatedBuilding.state() != dev.minecraftempires.common.building.BuildingState.UNDER_CONSTRUCTION
                    && updatedBuilding.state() != dev.minecraftempires.common.building.BuildingState.ACTIVE) {
                throw new IllegalStateException("Invalid construction state transition for building " + current.id()
                        + ": UNDER_CONSTRUCTION -> " + updatedBuilding.state());
            }
            if (updatedBuilding.constructionProgress() < current.constructionProgress()
                    || updatedBuilding.constructionProgress() > current.workPointsRequired()
                    || updatedBuilding.placementCursor() != current.placementCursor()
                    || (current.worldSyncState() != updatedBuilding.worldSyncState()
                    && !(current.worldSyncState() == dev.minecraftempires.common.building.WorldSyncState.NOT_PLACED
                    && updatedBuilding.worldSyncState() == dev.minecraftempires.common.building.WorldSyncState.PENDING))) {
                throw new IllegalArgumentException("Construction progress must be monotonic and within project bounds: "
                        + current.id());
            }
            final ResourceLedger currentExpectedEscrow = ConstructionProject.remainingEscrow(
                    blueprint.materials(), current.constructionProgress(), current.workPointsRequired());
            if (!current.reservedMaterials().asMap().equals(currentExpectedEscrow.asMap())) {
                throw new IllegalStateException("Current construction escrow does not match its progress: "
                        + current.id());
            }
            final ResourceLedger updatedExpectedEscrow = ConstructionProject.remainingEscrow(
                    blueprint.materials(), updatedBuilding.constructionProgress(), updatedBuilding.workPointsRequired());
            if (!updatedBuilding.reservedMaterials().asMap().equals(updatedExpectedEscrow.asMap())) {
                throw new IllegalArgumentException("Updated construction escrow does not match cumulative work: "
                        + current.id());
            }

            final boolean completeAndSynced = updatedBuilding.constructionProgress()
                    == updatedBuilding.workPointsRequired()
                    && updatedBuilding.worldSyncState()
                    == dev.minecraftempires.common.building.WorldSyncState.SYNCED;
            if (updatedBuilding.state() == dev.minecraftempires.common.building.BuildingState.ACTIVE
                    && !completeAndSynced) {
                throw new IllegalStateException("ACTIVE requires complete work, empty escrow, and SYNCED blocks: "
                        + current.id());
            }
            if (updatedBuilding.state() == dev.minecraftempires.common.building.BuildingState.UNDER_CONSTRUCTION
                    && completeAndSynced) {
                throw new IllegalStateException("A completed, SYNCED project must transition to ACTIVE: "
                        + current.id());
            }
            if (updatedBuilding.state() == current.state()
                    && updatedBuilding.constructionProgress() == current.constructionProgress()) {
                throw new IllegalArgumentException("Construction update contains no progress or state change: "
                        + current.id());
            }
        } else {
            throw new IllegalStateException("Construction updates are not allowed from state "
                    + current.state() + ": " + current.id());
        }

        final SettlementRecord settlement = settlements.get(current.settlementId());
        if (settlement == null || !settlement.ownerFactionId().equals(current.ownerFactionId())
                || !settlement.dimensionId().equals(current.dimensionId())) {
            throw new IllegalStateException("Construction building has an invalid owning settlement: " + current.id());
        }

        BuilderJobRecord createdJob = null;
        final boolean startingConstruction = current.state()
                == dev.minecraftempires.common.building.BuildingState.RESERVED
                && updatedBuilding.state()
                == dev.minecraftempires.common.building.BuildingState.UNDER_CONSTRUCTION;
        if (startingConstruction) {
            if (newBuilderJob == null
                    || !newBuilderJob.id().equals(BuilderJobRecord.forBuilding(
                    updatedBuilding, newBuilderJob.priority(), newBuilderJob.createdAtGameTime()).id())
                    || !newBuilderJob.buildingId().equals(updatedBuilding.id())
                    || !newBuilderJob.ownerFactionId().equals(updatedBuilding.ownerFactionId())
                    || !newBuilderJob.settlementId().equals(updatedBuilding.settlementId())
                    || newBuilderJob.state() != BuilderJobState.AVAILABLE
                    || newBuilderJob.assignedCitizenId() != null
                    || newBuilderJob.retryCount() != 0
                    || newBuilderJob.createdAtGameTime() != newBuilderJob.updatedAtGameTime()) {
                throw new IllegalArgumentException("Construction start must atomically create its matching AVAILABLE builder job");
            }
            createdJob = newBuilderJob;
            if (builderJobs.containsKey(createdJob.id())) {
                throw new IllegalStateException("Stable builder job ID is already in use: " + createdJob.id());
            }
            for (BuilderJobRecord existing : builderJobs.values()) {
                if (existing.isActive() && existing.buildingId().equals(updatedBuilding.id())) {
                    throw new IllegalStateException("Building already has an active builder job: " + existing.id());
                }
            }
        } else if (newBuilderJob != null) {
            throw new IllegalArgumentException("A builder job can only be created by RESERVED -> UNDER_CONSTRUCTION");
        }
        BuilderJobRecord completedJob = null;
        if (updatedBuilding.state() == dev.minecraftempires.common.building.BuildingState.ACTIVE) {
            for (BuilderJobRecord existing : builderJobs.values()) {
                if (existing.isActive() && existing.buildingId().equals(updatedBuilding.id())) {
                    if (completedJob != null) {
                        throw new IllegalStateException("Building has multiple active builder jobs: "
                                + updatedBuilding.id());
                    }
                    completedJob = existing.complete(Math.max(simulationClock, existing.updatedAtGameTime()));
                }
            }
        }

        // Publish the building, settlement index and corresponding builder-job lifecycle together.
        buildings.put(updatedBuilding.id(), updatedBuilding);
        settlements.put(settlement.id(), settlement.withBuildingId(updatedBuilding.id()));
        if (createdJob != null) {
            builderJobs.put(createdJob.id(), createdJob);
        }
        if (completedJob != null) {
            builderJobs.put(completedJob.id(), completedJob);
        }
        setDirty();
    }

    private void validateBuilderJobStore() {
        final Map<UUID, UUID> activeJobsByBuilding = new LinkedHashMap<>();
        final Map<UUID, UUID> activeJobsByCitizen = new LinkedHashMap<>();
        for (BuilderJobRecord job : builderJobs.values()) {
            final BuildingRecord building = buildings.get(job.buildingId());
            if (building == null || !building.ownerFactionId().equals(job.ownerFactionId())
                    || !building.settlementId().equals(job.settlementId())) {
                throw new IllegalArgumentException("Builder job " + job.id()
                        + " references a missing or foreign building " + job.buildingId());
            }
            if (job.isActive()) {
                if (building.state() != dev.minecraftempires.common.building.BuildingState.UNDER_CONSTRUCTION) {
                    throw new IllegalArgumentException("Active builder job " + job.id()
                            + " targets a building that is not UNDER_CONSTRUCTION");
                }
                final UUID previous = activeJobsByBuilding.putIfAbsent(job.buildingId(), job.id());
                if (previous != null) {
                    throw new IllegalArgumentException("Building " + job.buildingId()
                            + " has multiple active builder jobs " + previous + " and " + job.id());
                }
            }

            if (job.state().requiresAssignedCitizen()) {
                final CitizenRecord citizen = citizens.get(job.assignedCitizenId());
                if (citizen == null || citizen.status() != CitizenStatus.ACTIVE
                        || !citizen.ownerFactionId().equals(job.ownerFactionId())
                        || !citizen.professionId().equals(BuilderJobRecord.BUILDER_PROFESSION_ID)
                        || (citizen.homeSettlementId() != null
                        && !citizen.homeSettlementId().equals(job.settlementId()))) {
                    throw new IllegalArgumentException("Builder job " + job.id()
                            + " has an ineligible assigned citizen " + job.assignedCitizenId());
                }
                final UUID previous = activeJobsByCitizen.putIfAbsent(job.assignedCitizenId(), job.id());
                if (previous != null) {
                    throw new IllegalArgumentException("Citizen " + job.assignedCitizenId()
                            + " is assigned to multiple active builder jobs " + previous + " and " + job.id());
                }
            }
        }
        for (BuildingRecord building : buildings.values()) {
            if (building.state() == dev.minecraftempires.common.building.BuildingState.UNDER_CONSTRUCTION
                    && !activeJobsByBuilding.containsKey(building.id())) {
                throw new IllegalArgumentException("UNDER_CONSTRUCTION building " + building.id()
                        + " has no active builder job");
            }
        }
    }

    private static boolean sameConstructionSnapshot(
            final BuildingRecord current,
            final BuildingRecord expected
    ) {
        return sameBuildingIdentity(current, expected)
                && current.state() == expected.state()
                && current.worldSyncState() == expected.worldSyncState()
                && current.constructionProgress() == expected.constructionProgress()
                && current.reservedMaterials().asMap().equals(expected.reservedMaterials().asMap());
    }

    private static boolean sameBuildingIdentity(
            final BuildingRecord current,
            final BuildingRecord candidate
    ) {
        return current.id().equals(candidate.id())
                && current.blueprintId().equals(candidate.blueprintId())
                && current.blueprintVersion() == candidate.blueprintVersion()
                && current.ownerFactionId().equals(candidate.ownerFactionId())
                && current.settlementId().equals(candidate.settlementId())
                && current.dimensionId().equals(candidate.dimensionId())
                && current.origin().equals(candidate.origin())
                && current.rotationDegrees() == candidate.rotationDegrees()
                && current.placementCursor() == candidate.placementCursor()
                && current.workPointsRequired() == candidate.workPointsRequired()
                && current.durability() == candidate.durability()
                && current.templateChecksum().equals(candidate.templateChecksum())
                && current.createdAtGameTime() == candidate.createdAtGameTime()
                && current.schemaVersion() == candidate.schemaVersion();
    }

    void setSimulationClock(final long gameTime) {
        ensureWritable();
        if (gameTime < simulationClock) {
            throw new IllegalArgumentException("Simulation clock cannot move backwards");
        }
        if (gameTime != simulationClock) {
            simulationClock = gameTime;
            setDirty();
        }
    }

    void ensureWritable() {
        if (readOnly) {
            throw new IllegalStateException("Minecraft Empires world data is read-only: " + readOnlyReason);
        }
    }

    @Override
    public CompoundTag save(final CompoundTag ignored) {
        if (readOnly && preservedRawTag != null) {
            return preservedRawTag.copy();
        }

        final CompoundTag tag = new CompoundTag();
        tag.putInt("schema_version", CURRENT_SCHEMA_VERSION);
        tag.putUUID("world_id", worldId);
        tag.putLong("simulation_clock", simulationClock);

        final ListTag factionTags = new ListTag();
        final ListTag factionIds = new ListTag();
        for (FactionRecord faction : factions.values()) {
            factionTags.add(faction.save());
            factionIds.add(StringTag.valueOf(faction.id().toString()));
        }
        tag.put("factions", factionTags);
        tag.put("faction_ids", factionIds);

        final ListTag settlementTags = new ListTag();
        for (SettlementRecord settlement : settlements.values()) {
            settlementTags.add(settlement.save());
        }
        tag.put("settlements", settlementTags);

        final ListTag citizenTags = new ListTag();
        for (CitizenRecord citizen : citizens.values()) {
            citizenTags.add(citizen.save());
        }
        tag.put("citizens", citizenTags);

        final ListTag buildingTags = new ListTag();
        for (BuildingRecord building : buildings.values()) {
            buildingTags.add(building.save());
        }
        tag.put("buildings", buildingTags);

        final ListTag builderJobTags = new ListTag();
        for (BuilderJobRecord job : builderJobs.values()) {
            builderJobTags.add(job.save());
        }
        tag.put("builder_jobs", builderJobTags);
        return tag;
    }

    public static EmpiresWorldData load(final CompoundTag tag) {
        final int loadedSchema = tag.getInt("schema_version");
        if (loadedSchema > CURRENT_SCHEMA_VERSION) {
            return readOnlyFallback(tag, "Saved data schema " + loadedSchema
                    + " is newer than this mod supports (" + CURRENT_SCHEMA_VERSION + ")");
        }
        if (loadedSchema < MIN_SUPPORTED_SCHEMA_VERSION || loadedSchema > CURRENT_SCHEMA_VERSION) {
            return readOnlyFallback(tag, "Unsupported or missing saved data schema: " + loadedSchema);
        }

        try {
            if (!tag.hasUUID("world_id")) {
                throw new IllegalArgumentException("Missing world_id");
            }
            final EmpiresWorldData data = new EmpiresWorldData(tag.getUUID("world_id"),
                    CURRENT_SCHEMA_VERSION, false);
            data.simulationClock = tag.getLong("simulation_clock");
            if (data.simulationClock < 0L) {
                throw new IllegalArgumentException("Negative simulation_clock");
            }

            final ListTag factionTags = tag.getList("factions", Tag.TAG_COMPOUND);
            for (int index = 0; index < factionTags.size(); index++) {
                final FactionRecord record = FactionRecord.load(factionTags.getCompound(index));
                if (data.factions.putIfAbsent(record.id(), record) != null) {
                    throw new IllegalArgumentException("Duplicate faction id: " + record.id());
                }
            }

            final ListTag factionIdTags = tag.getList("faction_ids", Tag.TAG_STRING);
            final List<UUID> indexedIds = new ArrayList<>();
            for (int index = 0; index < factionIdTags.size(); index++) {
                indexedIds.add(UUID.fromString(factionIdTags.getString(index)));
            }
            final Set<UUID> indexedIdSet = new LinkedHashSet<>(indexedIds);
            if (indexedIds.size() != data.factions.size()
                    || indexedIdSet.size() != indexedIds.size()
                    || !indexedIdSet.equals(data.factions.keySet())) {
                throw new IllegalArgumentException("Faction ID index does not match faction records");
            }

            final ListTag settlementTags = tag.getList("settlements", Tag.TAG_COMPOUND);
            for (int index = 0; index < settlementTags.size(); index++) {
                final SettlementRecord record = SettlementRecord.load(settlementTags.getCompound(index));
                if (!data.factions.containsKey(record.ownerFactionId())) {
                    throw new IllegalArgumentException("Settlement references missing faction: " + record.ownerFactionId());
                }
                if (data.settlements.putIfAbsent(record.id(), record) != null) {
                    throw new IllegalArgumentException("Duplicate settlement id: " + record.id());
                }
            }

            for (FactionRecord faction : data.factions.values()) {
                if (faction.capitalSettlementId() != null) {
                    final SettlementRecord capital = data.settlements.get(faction.capitalSettlementId());
                    if (capital == null || !capital.ownerFactionId().equals(faction.id())) {
                        throw new IllegalArgumentException("Invalid capital settlement reference for faction " + faction.id());
                    }
                }
            }

            if (loadedSchema >= 2) {
                if (!tag.contains("citizens", Tag.TAG_LIST)) {
                    throw new IllegalArgumentException("Schema 2+ data is missing citizens list");
                }
                final ListTag citizenTags = tag.getList("citizens", Tag.TAG_COMPOUND);
                for (int index = 0; index < citizenTags.size(); index++) {
                    final CitizenRecord record = CitizenRecord.load(citizenTags.getCompound(index));
                    if (!data.factions.containsKey(record.ownerFactionId())) {
                        throw new IllegalArgumentException("Citizen references missing faction: " + record.ownerFactionId());
                    }
                    if (record.homeSettlementId() != null) {
                        final SettlementRecord home = data.settlements.get(record.homeSettlementId());
                        if (home == null || !home.ownerFactionId().equals(record.ownerFactionId())) {
                            throw new IllegalArgumentException("Citizen references invalid home settlement: " + record.homeSettlementId());
                        }
                    }
                    if (data.citizens.putIfAbsent(record.id(), record) != null) {
                        throw new IllegalArgumentException("Duplicate citizen id: " + record.id());
                    }
                }
            }

            if (loadedSchema >= 3) {
                if (!tag.contains("buildings", Tag.TAG_LIST)) {
                    throw new IllegalArgumentException("Schema 3 data is missing buildings list");
                }
                final ListTag buildingTags = tag.getList("buildings", Tag.TAG_COMPOUND);
                for (int index = 0; index < buildingTags.size(); index++) {
                    final CompoundTag buildingTag = buildingTags.getCompound(index);
                    final int savedBuildingSchema = buildingTag.getInt("schema_version");
                    final BuildingRecord record = BuildingRecord.load(buildingTag);
                    if (savedBuildingSchema < BuildingRecord.CURRENT_SCHEMA_VERSION) {
                        data.setDirty();
                        MinecraftEmpires.LOGGER.info("Migrated building record {} from schema {} to schema {}.",
                                record.id(), savedBuildingSchema, BuildingRecord.CURRENT_SCHEMA_VERSION);
                    }
                    if (!data.factions.containsKey(record.ownerFactionId())) {
                        throw new IllegalArgumentException("Building references missing faction: " + record.ownerFactionId());
                    }
                    final SettlementRecord settlement = data.settlements.get(record.settlementId());
                    if (settlement == null || !settlement.ownerFactionId().equals(record.ownerFactionId())
                            || !settlement.dimensionId().equals(record.dimensionId())) {
                        throw new IllegalArgumentException("Building references an invalid settlement: " + record.settlementId());
                    }
                    if (data.buildings.putIfAbsent(record.id(), record) != null) {
                        throw new IllegalArgumentException("Duplicate building id: " + record.id());
                    }
                }
            } else {
                // A pre-schema-3 save must not contain building rows that this version cannot safely interpret.
                if (tag.contains("buildings", Tag.TAG_LIST)
                        && !tag.getList("buildings", Tag.TAG_COMPOUND).isEmpty()) {
                    throw new IllegalArgumentException("Pre-schema-3 data unexpectedly contains building records");
                }
            }

            // Verify both directions of the Settlement <-> Building index before allowing writes.
            for (SettlementRecord settlement : data.settlements.values()) {
                for (UUID buildingId : settlement.buildingIds()) {
                    final BuildingRecord building = data.buildings.get(buildingId);
                    if (building == null || !building.settlementId().equals(settlement.id())) {
                        throw new IllegalArgumentException("Settlement building index references a missing or foreign building: " + buildingId);
                    }
                }
            }
            for (BuildingRecord building : data.buildings.values()) {
                final SettlementRecord settlement = data.settlements.get(building.settlementId());
                if (settlement == null || !settlement.buildingIds().contains(building.id())) {
                    throw new IllegalArgumentException("Building is missing from its settlement reverse index: " + building.id());
                }
            }

            if (loadedSchema >= 4) {
                if (!tag.contains("builder_jobs", Tag.TAG_LIST)) {
                    throw new IllegalArgumentException("Schema 4 data is missing builder_jobs list");
                }
                final ListTag builderJobTags = tag.getList("builder_jobs", Tag.TAG_COMPOUND);
                for (int index = 0; index < builderJobTags.size(); index++) {
                    final BuilderJobRecord record = BuilderJobRecord.load(builderJobTags.getCompound(index));
                    if (data.builderJobs.putIfAbsent(record.id(), record) != null) {
                        throw new IllegalArgumentException("Duplicate builder job id: " + record.id());
                    }
                }
                data.validateBuilderJobStore();
            } else {
                // Schema 3 had durable construction projects but no job table. Recreate one
                // deterministic AVAILABLE job for every in-progress project without touching
                // citizens, buildings, faction inventory, or world blocks.
                for (BuildingRecord building : data.buildings.values()) {
                    if (building.state() == dev.minecraftempires.common.building.BuildingState.UNDER_CONSTRUCTION) {
                        final BuilderJobRecord job = BuilderJobRecord.forBuilding(building,
                                BuilderJobRecord.DEFAULT_PRIORITY,
                                Math.max(data.simulationClock, building.createdAtGameTime()));
                        data.builderJobs.put(job.id(), job);
                    }
                }
                if (!data.builderJobs.isEmpty()) {
                    data.setDirty();
                }
            }
            data.validateBuilderJobStore();

            if (loadedSchema < CURRENT_SCHEMA_VERSION) {
                data.setDirty();
                MinecraftEmpires.LOGGER.info("Migrated Minecraft Empires world data from schema {} to schema {}.",
                        loadedSchema, CURRENT_SCHEMA_VERSION);
            }
            return data;
        } catch (RuntimeException exception) {
            MinecraftEmpires.LOGGER.error("Unable to safely read Minecraft Empires world data. Preserving the original tag and disabling writes.", exception);
            return readOnlyFallback(tag, "Saved data validation failed: " + exception.getMessage());
        }
    }

    private static EmpiresWorldData readOnlyFallback(final CompoundTag rawTag, final String reason) {
        final UUID preservedWorldId = rawTag.hasUUID("world_id") ? rawTag.getUUID("world_id") : UUID.randomUUID();
        final int preservedSchema = rawTag.getInt("schema_version");
        final EmpiresWorldData data = new EmpiresWorldData(preservedWorldId, preservedSchema, false);
        data.readOnly = true;
        data.readOnlyReason = reason;
        data.preservedRawTag = rawTag.copy();
        MinecraftEmpires.LOGGER.error("Minecraft Empires data is read-only: {}. No new civilization data will be written to this save.", reason);
        return data;
    }
}
