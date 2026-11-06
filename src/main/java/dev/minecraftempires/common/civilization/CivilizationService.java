package dev.minecraftempires.common.civilization;

import dev.minecraftempires.common.building.BlueprintCatalog;
import dev.minecraftempires.common.building.BlueprintDefinition;
import dev.minecraftempires.common.building.BuildingRecord;
import dev.minecraftempires.common.building.BuildingState;
import dev.minecraftempires.common.building.ConstructionProject;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Server-side application service. Gameplay commands should use this instead of mutating SavedData. */
public final class CivilizationService {
    private final CivilizationRepository repository;
    private final EmpiresWorldData worldData;

    public CivilizationService(final SavedDataCivilizationRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.worldData = repository.worldData();
    }

    public static CivilizationService forLevel(final ServerLevel level) {
        return new CivilizationService(SavedDataCivilizationRepository.forServer(level.getServer()));
    }

    public EmpiresWorldData worldData() { return worldData; }
    public List<FactionRecord> factions() { return repository.factions(); }
    public Optional<FactionRecord> findFaction(final UUID id) { return repository.findFaction(id); }
    public Optional<SettlementRecord> findSettlement(final UUID id) { return repository.findSettlement(id); }
    public List<CitizenRecord> citizens() { return repository.citizens(); }
    public List<CitizenRecord> citizensForFaction(final UUID factionId) {
        if (repository.findFaction(factionId).isEmpty()) {
            throw new IllegalArgumentException("Faction does not exist: " + factionId);
        }
        return repository.citizensForFaction(factionId);
    }
    public Optional<CitizenRecord> findCitizen(final UUID id) { return repository.findCitizen(id); }

    public List<BuilderJobRecord> builderJobs() { return repository.builderJobs(); }
    public Optional<BuilderJobRecord> findBuilderJob(final UUID id) { return repository.findBuilderJob(id); }
    public List<BuilderJobRecord> builderJobsForBuilding(final UUID buildingId) {
        if (repository.findBuilding(buildingId).isEmpty()) {
            throw new IllegalArgumentException("Building does not exist: " + buildingId);
        }
        return repository.builderJobsForBuilding(buildingId);
    }

    public List<BuildingRecord> buildings() { return repository.buildings(); }

    public List<BuildingRecord> buildingsForSettlement(final UUID settlementId) {
        if (repository.findSettlement(settlementId).isEmpty()) {
            throw new IllegalArgumentException("Settlement does not exist: " + settlementId);
        }
        return repository.buildingsForSettlement(settlementId);
    }

    public Optional<BuildingRecord> findBuilding(final UUID id) {
        return repository.findBuilding(id);
    }

    /** Persists a monotonic, idempotent world-placement update for an existing building. */
    public void updateBuildingPlacement(final BuildingRecord updated) {
        worldData.ensureWritable();
        final BuildingRecord previous = repository.findBuilding(updated.id())
                .orElseThrow(() -> new IllegalArgumentException("Building does not exist: " + updated.id()));
        if (updated.placementCursor() < previous.placementCursor()) {
            throw new IllegalArgumentException("Building placement cursor cannot move backwards: " + updated.id());
        }
        if (previous.worldSyncState() == dev.minecraftempires.common.building.WorldSyncState.SYNCED
                && updated.worldSyncState() != dev.minecraftempires.common.building.WorldSyncState.SYNCED) {
            throw new IllegalStateException("A synced building cannot silently return to placement: " + updated.id());
        }
        final boolean validTransition = switch (previous.worldSyncState()) {
            case NOT_PLACED -> updated.worldSyncState() == dev.minecraftempires.common.building.WorldSyncState.PENDING;
            case PENDING -> updated.worldSyncState() == dev.minecraftempires.common.building.WorldSyncState.PENDING
                    || updated.worldSyncState() == dev.minecraftempires.common.building.WorldSyncState.SYNCED
                    || updated.worldSyncState() == dev.minecraftempires.common.building.WorldSyncState.NEEDS_REPAIR;
            case NEEDS_REPAIR -> updated.worldSyncState() == dev.minecraftempires.common.building.WorldSyncState.NEEDS_REPAIR
                    || updated.worldSyncState() == dev.minecraftempires.common.building.WorldSyncState.PENDING;
            case SYNCED -> updated.worldSyncState() == dev.minecraftempires.common.building.WorldSyncState.SYNCED;
            case OUT_OF_SYNC -> updated.worldSyncState() == dev.minecraftempires.common.building.WorldSyncState.NEEDS_REPAIR;
        };
        if (!validTransition) {
            throw new IllegalStateException("Invalid world placement transition for building " + updated.id()
                    + ": " + previous.worldSyncState() + " -> " + updated.worldSyncState());
        }
        repository.saveBuilding(updated);
    }

    /**
     * Atomically moves a blueprint's full material cost from faction availability into
     * the building's persisted escrow. The stable building UUID is the idempotency key:
     * repeating this operation never debits the faction twice.
     */
    public BuildingRecord reserveBuildingMaterials(final UUID buildingId) {
        worldData.ensureWritable();
        final BuildingRecord building = repository.findBuilding(buildingId)
                .orElseThrow(() -> new IllegalArgumentException("Building does not exist: " + buildingId));

        switch (building.state()) {
            case RESERVED, UNDER_CONSTRUCTION, ACTIVE, DAMAGED, REPAIRING, RUINED -> {
                // A retry after the reservation transaction has committed is a read-only no-op.
                return building;
            }
            case PLANNED -> {
                // Continue below and perform the one permitted reservation transition.
            }
            case NEEDS_REPAIR, DEMOLISHED -> throw new IllegalStateException(
                    "Cannot reserve materials for a building in state " + building.state() + ": " + buildingId);
        }

        if (!building.reservedMaterials().isEmpty()) {
            throw new IllegalStateException("PLANNED building already contains reserved materials: " + buildingId);
        }
        final FactionRecord faction = repository.findFaction(building.ownerFactionId())
                .orElseThrow(() -> new IllegalStateException("Building owner faction is missing: "
                        + building.ownerFactionId()));
        if (faction.status() != FactionStatus.ACTIVE) {
            throw new IllegalStateException("Cannot reserve materials for an inactive faction: " + faction.id());
        }

        final BlueprintDefinition blueprint = BlueprintCatalog.find(building.blueprintId())
                .orElseThrow(() -> new IllegalStateException("Building references unknown blueprint: "
                        + building.blueprintId()));
        if (blueprint.version() != building.blueprintVersion()) {
            throw new IllegalStateException("Cannot reserve materials for outdated blueprint " + building.blueprintId()
                    + ": savedVersion=" + building.blueprintVersion() + ", catalogVersion=" + blueprint.version());
        }
        if (!blueprint.templateChecksum().equals(building.templateChecksum())) {
            throw new IllegalStateException("Cannot reserve materials because blueprint manifest checksum changed: "
                    + building.blueprintId());
        }

        final ResourceLedger cost = blueprint.materials();
        final ResourceLedger remainingResources;
        try {
            remainingResources = faction.resources().minus(cost);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Cannot reserve materials for building " + buildingId
                    + ": " + exception.getMessage(), exception);
        }

        final BuildingRecord reserved = building.withReservedMaterials(cost)
                .withState(BuildingState.RESERVED, building.worldSyncState());
        // The repository publishes both immutable replacements through one SavedData mutation.

        repository.saveBuildingReservation(faction.withResources(remainingResources), reserved);
        return reserved;
    }

    /** Moves a fully reserved project into the work phase. Repeating start is a read-only no-op. */
    public BuildingRecord startConstruction(final UUID buildingId) {
        worldData.ensureWritable();
        final BuildingRecord building = repository.findBuilding(buildingId)
                .orElseThrow(() -> new IllegalArgumentException("Building does not exist: " + buildingId));
        if (building.state() == BuildingState.UNDER_CONSTRUCTION) {
            ensureBuilderJob(building, Math.max(worldData.simulationClock(), building.createdAtGameTime()));
            return building;
        }
        if (building.state() == BuildingState.ACTIVE) {
            return building;
        }
        final BlueprintDefinition blueprint = BlueprintCatalog.find(building.blueprintId())
                .orElseThrow(() -> new IllegalStateException("Building references unknown blueprint: "
                        + building.blueprintId()));
        final BuildingRecord started = ConstructionProject.start(building, blueprint);
        repository.saveConstructionUpdate(building, started);
        return started;
    }

    /**
     * Applies one construction work batch guarded by expectedProgress. Once progress moves,
     * old tokens are rejected; callers must read the latest progress before submitting more work.
     */
    public BuildingRecord applyConstructionWork(
            final UUID buildingId,
            final long expectedProgress,
            final long workPoints
    ) {
        worldData.ensureWritable();
        final BuildingRecord building = repository.findBuilding(buildingId)
                .orElseThrow(() -> new IllegalArgumentException("Building does not exist: " + buildingId));
        final BlueprintDefinition blueprint = BlueprintCatalog.find(building.blueprintId())
                .orElseThrow(() -> new IllegalStateException("Building references unknown blueprint: "
                        + building.blueprintId()));
        final BuildingRecord updated = ConstructionProject.applyWork(
                building, blueprint, expectedProgress, workPoints);
        if (updated == building) {
            return building;
        }
        repository.saveConstructionUpdate(building, updated);
        return updated;
    }

    /** Promotes a fully worked project after its world representation has become SYNCED. */
    public BuildingRecord activateCompletedBuilding(final UUID buildingId) {
        worldData.ensureWritable();
        final BuildingRecord building = repository.findBuilding(buildingId)
                .orElseThrow(() -> new IllegalArgumentException("Building does not exist: " + buildingId));
        if (building.state() == BuildingState.ACTIVE) {
            return building;
        }
        final BlueprintDefinition blueprint = BlueprintCatalog.find(building.blueprintId())
                .orElseThrow(() -> new IllegalStateException("Building references unknown blueprint: "
                        + building.blueprintId()));
        final BuildingRecord activated = ConstructionProject.activate(building, blueprint);
        repository.saveConstructionUpdate(building, activated);
        return activated;
    }

    public FactionRecord createFaction(
            final String displayName,
            final ResourceLocation speciesId,
            final ControllerType controllerType,
            final long gameTime
    ) {
        worldData.ensureWritable();
        final FactionRecord record = FactionRecord.create(displayName, speciesId, controllerType, gameTime);
        repository.saveFaction(record);
        return record;
    }

    public SettlementRecord createSettlement(
            final UUID ownerFactionId,
            final ResourceLocation dimensionId,
            final BlockPos anchor,
            final long gameTime
    ) {
        worldData.ensureWritable();
        if (repository.findFaction(ownerFactionId).isEmpty()) {
            throw new IllegalArgumentException("Owner faction does not exist: " + ownerFactionId);
        }
        final SettlementRecord record = SettlementRecord.create(ownerFactionId, dimensionId, anchor, gameTime);
        repository.saveSettlement(record);
        return record;
    }

    public void setCapitalSettlement(final UUID factionId, final UUID settlementId) {
        worldData.ensureWritable();
        final FactionRecord faction = repository.findFaction(factionId)
                .orElseThrow(() -> new IllegalArgumentException("Faction does not exist: " + factionId));
        final SettlementRecord settlement = repository.findSettlement(settlementId)
                .orElseThrow(() -> new IllegalArgumentException("Settlement does not exist: " + settlementId));
        if (!settlement.ownerFactionId().equals(factionId)) {
            throw new IllegalArgumentException("Capital settlement must belong to the faction");
        }
        if (settlement.status() != SettlementStatus.ACTIVE) {
            throw new IllegalArgumentException("Capital settlement must be active");
        }
        if (faction.capitalSettlementId() != null
                && !faction.capitalSettlementId().equals(settlementId)) {
            throw new IllegalStateException("Faction capital is already bound to settlement "
                    + faction.capitalSettlementId() + "; explicit capital relocation is not supported");
        }
        if (settlementId.equals(faction.capitalSettlementId())) {
            return;
        }
        for (BuildingRecord building : repository.buildings()) {
            if (building.state() == BuildingState.DEMOLISHED
                    || !building.ownerFactionId().equals(factionId)) {
                continue;
            }
            final BlueprintDefinition blueprint = BlueprintCatalog.find(building.blueprintId()).orElse(null);
            if (blueprint != null
                    && blueprint.category() == dev.minecraftempires.common.building.BuildingCategory.HEADQUARTERS
                    && !building.settlementId().equals(settlementId)) {
                throw new IllegalStateException("Capital settlement cannot change while headquarters "
                        + building.id() + " is reserved in settlement " + building.settlementId());
            }
        }
        repository.saveFaction(faction.withCapitalSettlement(settlementId));
    }

    /** Promotes an active logical citizen to the builder profession. */
    public CitizenRecord assignBuilder(final UUID citizenId) {
        worldData.ensureWritable();
        final CitizenRecord citizen = repository.findCitizen(citizenId)
                .orElseThrow(() -> new IllegalArgumentException("Citizen does not exist: " + citizenId));
        if (citizen.status() != CitizenStatus.ACTIVE) {
            throw new IllegalArgumentException("Only ACTIVE citizens can become builders: " + citizenId);
        }
        if (citizen.professionId().equals(BuilderJobRecord.BUILDER_PROFESSION_ID)) {
            return citizen;
        }
        for (BuilderJobRecord job : repository.builderJobs()) {
            if (job.isActive() && citizenId.equals(job.assignedCitizenId())) {
                throw new IllegalStateException("Release active builder job " + job.id()
                        + " before changing this citizen's profession");
            }
        }
        final CitizenRecord updated = citizen.withProfession(BuilderJobRecord.BUILDER_PROFESSION_ID);
        repository.saveCitizen(updated);
        return updated;
    }

    /**
     * Ensures that an in-progress construction project has its one durable builder job.
     * This is also the recovery path for a project migrated from a pre-job save.
     */
    public BuilderJobRecord createBuilderJob(final UUID buildingId, final int priority, final long gameTime) {
        worldData.ensureWritable();
        final BuildingRecord building = repository.findBuilding(buildingId)
                .orElseThrow(() -> new IllegalArgumentException("Building does not exist: " + buildingId));
        if (building.state() != BuildingState.UNDER_CONSTRUCTION) {
            throw new IllegalStateException("Builder jobs require an UNDER_CONSTRUCTION building: " + buildingId);
        }
        for (BuilderJobRecord existing : repository.builderJobsForBuilding(buildingId)) {
            if (existing.isActive()) {
                if (existing.priority() != priority) {
                    return existing;
                }
                return existing;
            }
        }
        final BuilderJobRecord created = BuilderJobRecord.forBuilding(building, priority, gameTime);
        repository.saveBuilderJob(null, created);
        return created;
    }

    private BuilderJobRecord ensureBuilderJob(final BuildingRecord building, final long gameTime) {
        for (BuilderJobRecord existing : repository.builderJobsForBuilding(building.id())) {
            if (existing.isActive()) {
                return existing;
            }
        }
        final BuilderJobRecord created = BuilderJobRecord.forBuilding(
                building, BuilderJobRecord.DEFAULT_PRIORITY, gameTime);
        repository.saveBuilderJob(null, created);
        return created;
    }

    /** Claims an AVAILABLE job for an eligible builder; one citizen can hold only one active job. */
    public BuilderJobRecord claimBuilderJob(
            final UUID jobId,
            final UUID citizenId,
            final long gameTime
    ) {
        worldData.ensureWritable();
        final BuilderJobRecord job = repository.findBuilderJob(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Builder job does not exist: " + jobId));
        final BuildingRecord building = repository.findBuilding(job.buildingId())
                .orElseThrow(() -> new IllegalStateException("Builder job target building is missing: " + job.buildingId()));
        if (building.state() != BuildingState.UNDER_CONSTRUCTION) {
            throw new IllegalStateException("Builder job target is not UNDER_CONSTRUCTION: " + building.id());
        }
        final CitizenRecord citizen = repository.findCitizen(citizenId)
                .orElseThrow(() -> new IllegalArgumentException("Citizen does not exist: " + citizenId));
        if (citizen.status() != CitizenStatus.ACTIVE
                || !citizen.ownerFactionId().equals(job.ownerFactionId())
                || !citizen.professionId().equals(BuilderJobRecord.BUILDER_PROFESSION_ID)
                || (citizen.homeSettlementId() != null
                && !citizen.homeSettlementId().equals(job.settlementId()))) {
            throw new IllegalArgumentException("Assigned citizen must be an ACTIVE builder of the owning faction "
                    + "and settlement: " + citizenId);
        }
        for (BuilderJobRecord other : repository.builderJobs()) {
            if (other.isActive() && citizenId.equals(other.assignedCitizenId())) {
                throw new IllegalStateException("Citizen already has active builder job " + other.id());
            }
        }
        final BuilderJobRecord claimed = job.claim(citizenId, gameTime);
        repository.saveBuilderJob(job, claimed);
        return claimed;
    }

    public BuilderJobRecord startBuilderJob(
            final UUID jobId,
            final UUID citizenId,
            final long gameTime
    ) {
        worldData.ensureWritable();
        final BuilderJobRecord job = requireAssignedBuilderJob(jobId, citizenId);
        if (job.state() == BuilderJobState.WORKING) {
            return job;
        }
        final BuilderJobRecord working = job.startWorking(gameTime);
        repository.saveBuilderJob(job, working);
        return working;
    }

    public BuilderJobRecord releaseBuilderJob(
            final UUID jobId,
            final UUID citizenId,
            final long gameTime
    ) {
        worldData.ensureWritable();
        final BuilderJobRecord job = requireAssignedBuilderJob(jobId, citizenId);
        final BuilderJobRecord released = job.release(gameTime);
        repository.saveBuilderJob(job, released);
        return released;
    }

    public BuilderJobRecord blockBuilderJob(
            final UUID jobId,
            final UUID citizenId,
            final long gameTime
    ) {
        worldData.ensureWritable();
        final BuilderJobRecord job = requireAssignedBuilderJob(jobId, citizenId);
        final BuilderJobRecord blocked = job.block(gameTime);
        repository.saveBuilderJob(job, blocked);
        return blocked;
    }

    public BuilderJobRecord retryBuilderJob(final UUID jobId, final long gameTime) {
        worldData.ensureWritable();
        final BuilderJobRecord job = repository.findBuilderJob(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Builder job does not exist: " + jobId));
        final BuilderJobRecord retried = job.retry(gameTime);
        repository.saveBuilderJob(job, retried);
        return retried;
    }

    /** A job can complete only after the associated construction aggregate is ACTIVE. */
    public BuilderJobRecord completeBuilderJob(final UUID jobId, final long gameTime) {
        worldData.ensureWritable();
        final BuilderJobRecord job = repository.findBuilderJob(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Builder job does not exist: " + jobId));
        final BuildingRecord building = repository.findBuilding(job.buildingId())
                .orElseThrow(() -> new IllegalStateException("Builder job target building is missing: " + job.buildingId()));
        if (building.state() != BuildingState.ACTIVE) {
            throw new IllegalStateException("Builder job cannot complete before its building is ACTIVE: "
                    + building.id());
        }
        if (job.state() == BuilderJobState.COMPLETED) {
            return job;
        }
        final BuilderJobRecord completed = job.complete(gameTime);
        repository.saveBuilderJob(job, completed);
        return completed;
    }

    private BuilderJobRecord requireAssignedBuilderJob(final UUID jobId, final UUID citizenId) {
        final BuilderJobRecord job = repository.findBuilderJob(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Builder job does not exist: " + jobId));
        if (!citizenId.equals(job.assignedCitizenId())) {
            throw new IllegalStateException("Citizen is not the assigned worker for builder job " + jobId);
        }
        return job;
    }

    /** Creates a logical citizen; it deliberately does not spawn a Minecraft entity. */
    public CitizenRecord createCitizen(final UUID factionId, final String displayName, final long gameTime) {
        worldData.ensureWritable();
        final FactionRecord faction = repository.findFaction(factionId)
                .orElseThrow(() -> new IllegalArgumentException("Faction does not exist: " + factionId));
        if (faction.status() != FactionStatus.ACTIVE) {
            throw new IllegalArgumentException("Cannot add citizens to an inactive faction");
        }

        UUID homeSettlementId = faction.capitalSettlementId();
        if (homeSettlementId != null) {
            final SettlementRecord home = repository.findSettlement(homeSettlementId)
                    .orElseThrow(() -> new IllegalStateException("Faction capital settlement is missing: " + homeSettlementId));
            if (!home.ownerFactionId().equals(factionId) || home.status() != SettlementStatus.ACTIVE) {
                throw new IllegalStateException("Faction capital settlement is not a valid active home");
            }
        }

        final CitizenRecord citizen = CitizenRecord.create(
                factionId, homeSettlementId, displayName, faction.speciesId(), gameTime);
        repository.saveCitizen(citizen);
        refreshPopulationCache(factionId);
        return citizen;
    }

    /**
     * Plans a logical building at an anchor position. This milestone does not inspect or modify
     * world blocks, load chunks, reserve materials, or advance construction progress.
     */
    public BuildingRecord planBuilding(
            final UUID settlementId,
            final ResourceLocation blueprintId,
            final ResourceLocation dimensionId,
            final BlockPos origin,
            final int rotationDegrees,
            final long gameTime
    ) {
        worldData.ensureWritable();
        final SettlementRecord settlement = repository.findSettlement(settlementId)
                .orElseThrow(() -> new IllegalArgumentException("Settlement does not exist: " + settlementId));
        if (settlement.status() != SettlementStatus.ACTIVE) {
            throw new IllegalArgumentException("Cannot plan a building in an inactive settlement");
        }
        if (!settlement.dimensionId().equals(dimensionId)) {
            throw new IllegalArgumentException("Building must be planned in the settlement dimension " + settlement.dimensionId());
        }
        final FactionRecord faction = repository.findFaction(settlement.ownerFactionId())
                .orElseThrow(() -> new IllegalStateException("Settlement owner faction is missing: " + settlement.ownerFactionId()));
        if (faction.status() != FactionStatus.ACTIVE) {
            throw new IllegalArgumentException("Cannot plan a building for an inactive faction");
        }
        final BlueprintDefinition blueprint = BlueprintCatalog.find(blueprintId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown blueprint: " + blueprintId));
        if (!blueprint.allowsRotation(rotationDegrees)) {
            throw new IllegalArgumentException("Blueprint " + blueprintId + " does not allow rotation " + rotationDegrees);
        }
        if (blueprint.category() == dev.minecraftempires.common.building.BuildingCategory.HEADQUARTERS) {
            if (faction.capitalSettlementId() != null
                    && !faction.capitalSettlementId().equals(settlement.id())) {
                throw new IllegalArgumentException("Headquarters must be planned in the faction's capital settlement "
                        + faction.capitalSettlementId());
            }
            for (BuildingRecord existing : repository.buildings()) {
                if (existing.state() == BuildingState.DEMOLISHED
                        || !existing.ownerFactionId().equals(faction.id())) {
                    continue;
                }
                final BlueprintDefinition existingBlueprint = BlueprintCatalog.find(existing.blueprintId()).orElse(null);
                if (existingBlueprint != null
                        && existingBlueprint.category()
                        == dev.minecraftempires.common.building.BuildingCategory.HEADQUARTERS) {
                    throw new IllegalArgumentException("Faction " + faction.id()
                            + " already has a non-demolished headquarters building " + existing.id()
                            + " (state=" + existing.state() + ")");
                }
            }
        }

        final int newSizeX = blueprint.rotatedSizeX(rotationDegrees);
        final int newSizeY = blueprint.sizeY();
        final int newSizeZ = blueprint.rotatedSizeZ(rotationDegrees);
        for (BuildingRecord existing : repository.buildings()) {
            if (existing.state() == BuildingState.DEMOLISHED
                    || !existing.dimensionId().equals(dimensionId)) {
                continue;
            }
            final BlueprintDefinition existingBlueprint = BlueprintCatalog.find(existing.blueprintId())
                    .orElseThrow(() -> new IllegalStateException("Cannot safely validate overlap with unknown blueprint "
                            + existing.blueprintId() + " on building " + existing.id()));
            if (existing.blueprintVersion() != existingBlueprint.version()) {
                throw new IllegalStateException("Cannot safely validate overlap with outdated blueprint version on building "
                        + existing.id() + "; expected " + existingBlueprint.version() + ", found " + existing.blueprintVersion());
            }
            final int existingSizeX = existingBlueprint.rotatedSizeX(existing.rotationDegrees());
            final int existingSizeY = existingBlueprint.sizeY();
            final int existingSizeZ = existingBlueprint.rotatedSizeZ(existing.rotationDegrees());
            if (intersects(origin, newSizeX, newSizeY, newSizeZ,
                    existing.origin(), existingSizeX, existingSizeY, existingSizeZ)) {
                throw new IllegalArgumentException("Building footprint overlaps existing building " + existing.id());
            }
        }

        final BuildingRecord record = BuildingRecord.planned(blueprint, faction.id(), settlement.id(),
                dimensionId, origin, rotationDegrees, gameTime);
        repository.saveBuilding(record);
        return record;
    }

    private static boolean intersects(
            final BlockPos first,
            final int firstX,
            final int firstY,
            final int firstZ,
            final BlockPos second,
            final int secondX,
            final int secondY,
            final int secondZ
    ) {
        return first.getX() < second.getX() + secondX && second.getX() < first.getX() + firstX
                && first.getY() < second.getY() + secondY && second.getY() < first.getY() + firstY
                && first.getZ() < second.getZ() + secondZ && second.getZ() < first.getZ() + firstZ;
    }

    /**
     * Binds a logical citizen to exactly one persistent Minecraft entity UUID. This is called
     * when the entity joins a server level, including when it is restored from a saved chunk.
     */
    public void bindCitizenEntity(final UUID citizenId, final UUID entityUuid) {
        final CitizenRecord citizen = repository.findCitizen(citizenId)
                .orElseThrow(() -> new IllegalArgumentException("Citizen does not exist: " + citizenId));
        if (citizen.status() != CitizenStatus.ACTIVE) {
            throw new IllegalStateException("Cannot bind an entity to a non-active citizen");
        }
        if (citizen.entityUuid() != null && !citizen.entityUuid().equals(entityUuid)) {
            throw new IllegalStateException("Citizen is already linked to a different entity: " + citizen.entityUuid());
        }
        // A matching link is a read-only no-op, allowing an already-linked entity to load
        // even when the world data has been opened in forward-schema read-only mode.
        if (entityUuid.equals(citizen.entityUuid())) {
            return;
        }
        worldData.ensureWritable();
        repository.saveCitizen(citizen.withEntityUuid(entityUuid));
    }

    /** Clears the link only when the caller presents the exact currently linked entity UUID. */
    public void clearCitizenEntity(final UUID citizenId, final UUID expectedEntityUuid) {
        worldData.ensureWritable();
        final CitizenRecord citizen = repository.findCitizen(citizenId)
                .orElseThrow(() -> new IllegalArgumentException("Citizen does not exist: " + citizenId));
        if (citizen.entityUuid() == null || !citizen.entityUuid().equals(expectedEntityUuid)) {
            throw new IllegalStateException("Citizen entity link changed; refusing to clear a different entity");
        }
        repository.saveCitizen(citizen.withEntityUuid(null));
    }

    /** Marks a linked citizen as deceased and refreshes the owning faction's population cache. */
    public void markCitizenDeceased(final UUID citizenId, final UUID expectedEntityUuid) {
        worldData.ensureWritable();
        final CitizenRecord citizen = repository.findCitizen(citizenId)
                .orElseThrow(() -> new IllegalArgumentException("Citizen does not exist: " + citizenId));
        if (citizen.status() != CitizenStatus.ACTIVE) {
            return;
        }
        if (citizen.entityUuid() != null && !citizen.entityUuid().equals(expectedEntityUuid)) {
            throw new IllegalStateException("Citizen death event came from an unlinked entity");
        }
        repository.saveCitizen(citizen.withEntityUuid(null).withStatus(CitizenStatus.DECEASED));
        refreshPopulationCache(citizen.ownerFactionId());
    }

    private void refreshPopulationCache(final UUID factionId) {
        final FactionRecord faction = repository.findFaction(factionId)
                .orElseThrow(() -> new IllegalStateException("Citizen owner faction is missing: " + factionId));
        final int activePopulation = (int) repository.citizensForFaction(factionId).stream()
                .filter(record -> record.status() == CitizenStatus.ACTIVE)
                .count();
        repository.saveFaction(faction.withPopulationCountCache(activePopulation));
    }

    public void setResourceAmount(final UUID factionId, final ResourceLocation resourceId, final long amount) {
        worldData.ensureWritable();
        final FactionRecord faction = repository.findFaction(factionId)
                .orElseThrow(() -> new IllegalArgumentException("Faction does not exist: " + factionId));
        repository.saveFaction(faction.withResources(faction.resources().withAmount(resourceId, amount)));
    }

    public void setResources(final UUID factionId, final ResourceLedger resources) {
        worldData.ensureWritable();
        final FactionRecord faction = repository.findFaction(factionId)
                .orElseThrow(() -> new IllegalArgumentException("Faction does not exist: " + factionId));
        repository.saveFaction(faction.withResources(resources));
    }

    public void advanceSimulationClock(final long gameTime) {
        worldData.setSimulationClock(gameTime);
    }
}
