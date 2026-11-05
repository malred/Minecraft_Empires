package dev.minecraftempires.common.civilization;

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
    public List<CitizenRecord> citizens() { return repository.citizens(); }
    public List<CitizenRecord> citizensForFaction(final UUID factionId) {
        if (repository.findFaction(factionId).isEmpty()) {
            throw new IllegalArgumentException("Faction does not exist: " + factionId);
        }
        return repository.citizensForFaction(factionId);
    }
    public Optional<CitizenRecord> findCitizen(final UUID id) { return repository.findCitizen(id); }

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
        repository.saveFaction(faction.withCapitalSettlement(settlementId));
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
