package dev.minecraftempires.common.civilization;

import dev.minecraftempires.common.building.BlueprintCatalog;
import dev.minecraftempires.common.building.BlueprintDefinition;
import dev.minecraftempires.common.building.BuildingRecord;
import dev.minecraftempires.common.building.ConstructionProject;
import dev.minecraftempires.common.building.BuildingState;
import dev.minecraftempires.common.building.WorldSyncState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Read-only consistency checks for the authoritative world civilization data.
 *
 * <p>This validator deliberately does not inspect Minecraft levels, chunks, entities, or blocks.
 * It only checks relationships between already-loaded logical records.</p>
 */
public final class WorldDataValidator {
    private WorldDataValidator() {
    }

    public static Report validate(final EmpiresWorldData data) {
        Objects.requireNonNull(data, "data");
        final List<Issue> issues = new ArrayList<>();
        if (data.isReadOnly()) {
            issues.add(new Issue(Severity.ERROR,
                    "World data is READ_ONLY; record-level validation is unavailable. Preserve the save and inspect the read-only reason before attempting recovery."));
            return new Report(data.factionCount(), data.settlementCount(), data.citizenCount(),
                    data.buildingCount(), issues);
        }
        if (data.schemaVersion() != EmpiresWorldData.CURRENT_SCHEMA_VERSION) {
            issues.add(new Issue(Severity.ERROR, "Unexpected world schema version "
                    + data.schemaVersion() + "; supported version is " + EmpiresWorldData.CURRENT_SCHEMA_VERSION + "."));
        }

        final Map<UUID, FactionRecord> factions = new LinkedHashMap<>();
        final Map<UUID, SettlementRecord> settlements = new LinkedHashMap<>();
        final Map<UUID, CitizenRecord> citizens = new LinkedHashMap<>();
        final Map<UUID, BuildingRecord> buildings = new LinkedHashMap<>();
        final Map<UUID, BuilderJobRecord> builderJobs = new LinkedHashMap<>();
        for (FactionRecord faction : data.factions()) {
            factions.put(faction.id(), faction);
        }
        for (SettlementRecord settlement : data.settlements()) {
            settlements.put(settlement.id(), settlement);
        }
        for (CitizenRecord citizen : data.citizens()) {
            citizens.put(citizen.id(), citizen);
        }
        for (BuildingRecord building : data.buildings()) {
            buildings.put(building.id(), building);
        }
        for (BuilderJobRecord job : data.builderJobs()) {
            builderJobs.put(job.id(), job);
        }

        validateFactions(factions, settlements, issues);
        validateSettlements(factions, settlements, buildings, issues);
        validateCitizens(factions, settlements, citizens, issues);
        validateBuildings(factions, settlements, buildings, issues);
        validateBuildingOverlaps(buildings, issues);
        validateBuilderJobs(builderJobs, buildings, citizens, issues);

        return new Report(factions.size(), settlements.size(), citizens.size(), buildings.size(), issues);
    }

    private static void validateFactions(
            final Map<UUID, FactionRecord> factions,
            final Map<UUID, SettlementRecord> settlements,
            final List<Issue> issues
    ) {
        for (FactionRecord faction : factions.values()) {
            final UUID capitalId = faction.capitalSettlementId();
            if (capitalId == null) {
                continue;
            }
            final SettlementRecord capital = settlements.get(capitalId);
            if (capital == null) {
                issues.add(new Issue(Severity.ERROR, "Faction " + faction.id()
                        + " references missing capital settlement " + capitalId + "."));
            } else if (!capital.ownerFactionId().equals(faction.id())) {
                issues.add(new Issue(Severity.ERROR, "Faction " + faction.id()
                        + " references a capital owned by another faction: " + capitalId + "."));
            } else if (capital.status() != SettlementStatus.ACTIVE) {
                issues.add(new Issue(Severity.WARNING, "Faction " + faction.id()
                        + " capital settlement " + capitalId + " is " + capital.status() + "."));
            }
        }
    }

    private static void validateSettlements(
            final Map<UUID, FactionRecord> factions,
            final Map<UUID, SettlementRecord> settlements,
            final Map<UUID, BuildingRecord> buildings,
            final List<Issue> issues
    ) {
        for (SettlementRecord settlement : settlements.values()) {
            if (!factions.containsKey(settlement.ownerFactionId())) {
                issues.add(new Issue(Severity.ERROR, "Settlement " + settlement.id()
                        + " references missing owner faction " + settlement.ownerFactionId() + "."));
            }
            for (UUID buildingId : settlement.buildingIds()) {
                final BuildingRecord building = buildings.get(buildingId);
                if (building == null) {
                    issues.add(new Issue(Severity.ERROR, "Settlement " + settlement.id()
                            + " indexes missing building " + buildingId + "."));
                } else if (!building.settlementId().equals(settlement.id())) {
                    issues.add(new Issue(Severity.ERROR, "Settlement " + settlement.id()
                            + " indexes building " + buildingId + " whose settlementId is "
                            + building.settlementId() + "."));
                }
            }
        }
    }

    private static void validateCitizens(
            final Map<UUID, FactionRecord> factions,
            final Map<UUID, SettlementRecord> settlements,
            final Map<UUID, CitizenRecord> citizens,
            final List<Issue> issues
    ) {
        final Map<UUID, Integer> activePopulation = new HashMap<>();
        final Map<UUID, UUID> entityOwners = new HashMap<>();
        for (CitizenRecord citizen : citizens.values()) {
            if (!factions.containsKey(citizen.ownerFactionId())) {
                issues.add(new Issue(Severity.ERROR, "Citizen " + citizen.id()
                        + " references missing owner faction " + citizen.ownerFactionId() + "."));
            }
            if (citizen.status() == CitizenStatus.ACTIVE) {
                activePopulation.merge(citizen.ownerFactionId(), 1, Integer::sum);
            }
            if (citizen.homeSettlementId() != null) {
                final SettlementRecord home = settlements.get(citizen.homeSettlementId());
                if (home == null) {
                    issues.add(new Issue(Severity.ERROR, "Citizen " + citizen.id()
                            + " references missing home settlement " + citizen.homeSettlementId() + "."));
                } else if (!home.ownerFactionId().equals(citizen.ownerFactionId())) {
                    issues.add(new Issue(Severity.ERROR, "Citizen " + citizen.id()
                            + " home settlement belongs to another faction: " + citizen.homeSettlementId() + "."));
                }
            }
            if (citizen.entityUuid() != null) {
                final UUID previousCitizen = entityOwners.putIfAbsent(citizen.entityUuid(), citizen.id());
                if (previousCitizen != null && !previousCitizen.equals(citizen.id())) {
                    issues.add(new Issue(Severity.ERROR, "Citizens " + previousCitizen + " and "
                            + citizen.id() + " share Minecraft entity UUID " + citizen.entityUuid() + "."));
                }
            }
        }

        for (FactionRecord faction : factions.values()) {
            final int actual = activePopulation.getOrDefault(faction.id(), 0);
            if (faction.populationCountCache() != actual) {
                issues.add(new Issue(Severity.WARNING, "Faction " + faction.id()
                        + " populationCountCache=" + faction.populationCountCache()
                        + " but ACTIVE CitizenRecords=" + actual + "."));
            }
        }
    }

    private static void validateBuildings(
            final Map<UUID, FactionRecord> factions,
            final Map<UUID, SettlementRecord> settlements,
            final Map<UUID, BuildingRecord> buildings,
            final List<Issue> issues
    ) {
        final Map<UUID, UUID> headquartersByFaction = new HashMap<>();
        for (BuildingRecord building : buildings.values()) {
            if (!factions.containsKey(building.ownerFactionId())) {
                issues.add(new Issue(Severity.ERROR, "Building " + building.id()
                        + " references missing owner faction " + building.ownerFactionId() + "."));
            }
            final SettlementRecord settlement = settlements.get(building.settlementId());
            if (settlement == null) {
                issues.add(new Issue(Severity.ERROR, "Building " + building.id()
                        + " references missing settlement " + building.settlementId() + "."));
            } else {
                if (!settlement.ownerFactionId().equals(building.ownerFactionId())) {
                    issues.add(new Issue(Severity.ERROR, "Building " + building.id()
                            + " owner faction differs from settlement " + settlement.id() + "."));
                }
                if (!settlement.dimensionId().equals(building.dimensionId())) {
                    issues.add(new Issue(Severity.ERROR, "Building " + building.id()
                            + " dimension differs from settlement " + settlement.id() + "."));
                }
                if (!settlement.buildingIds().contains(building.id())) {
                    issues.add(new Issue(Severity.ERROR, "Building " + building.id()
                            + " is missing from settlement " + settlement.id() + " reverse index."));
                }
            }

            final BlueprintDefinition blueprint = BlueprintCatalog.find(building.blueprintId()).orElse(null);
            if (blueprint == null) {
                issues.add(new Issue(Severity.ERROR, "Building " + building.id()
                        + " references unknown blueprint " + building.blueprintId() + "."));
                continue;
            }
            if (building.workPointsRequired() != blueprint.workPoints()) {
                issues.add(new Issue(Severity.ERROR, "Building " + building.id()
                        + " workPointsRequired=" + building.workPointsRequired()
                        + " but blueprint requires " + blueprint.workPoints() + "."));
            }
            switch (building.state()) {
                case PLANNED -> {
                    if (!building.reservedMaterials().isEmpty()) {
                        issues.add(new Issue(Severity.ERROR, "PLANNED building " + building.id()
                                + " contains reserved materials before its reservation transaction."));
                    }
                    if (building.constructionProgress() != 0L) {
                        issues.add(new Issue(Severity.ERROR, "PLANNED building " + building.id()
                                + " has construction progress before reservation."));
                    }
                }
                case RESERVED -> {
                    if (!building.reservedMaterials().asMap().equals(blueprint.materials().asMap())) {
                        issues.add(new Issue(Severity.ERROR, "RESERVED building " + building.id()
                                + " escrow does not match the full blueprint material cost; expected="
                                + blueprint.materials().asMap() + ", actual=" + building.reservedMaterials().asMap() + "."));
                    }
                    if (building.constructionProgress() != 0L) {
                        issues.add(new Issue(Severity.ERROR, "RESERVED building " + building.id()
                                + " has construction progress before work starts."));
                    }
                }
                case UNDER_CONSTRUCTION -> {
                    if (building.workPointsRequired() == blueprint.workPoints()) {
                        final ResourceLedger expectedEscrow = ConstructionProject.remainingEscrow(
                                blueprint.materials(), building.constructionProgress(), building.workPointsRequired());
                        if (!building.reservedMaterials().asMap().equals(expectedEscrow.asMap())) {
                            issues.add(new Issue(Severity.ERROR, "UNDER_CONSTRUCTION building " + building.id()
                                    + " escrow does not match cumulative work; expected="
                                    + expectedEscrow.asMap() + ", actual=" + building.reservedMaterials().asMap() + "."));
                        }
                    }
                    if (building.constructionProgress() == building.workPointsRequired()
                            && building.worldSyncState() == WorldSyncState.SYNCED) {
                        issues.add(new Issue(Severity.WARNING, "Building " + building.id()
                                + " has completed work and SYNCED blocks but is awaiting ACTIVE transition."));
                    }
                }
                case ACTIVE -> {
                    if (building.constructionProgress() != building.workPointsRequired()
                            || !building.reservedMaterials().isEmpty()
                            || building.worldSyncState() != WorldSyncState.SYNCED) {
                        issues.add(new Issue(Severity.ERROR, "ACTIVE building " + building.id()
                                + " must have complete work, empty escrow, and SYNCED world blocks."));
                    }
                }
                default -> {
                    // Repair/damage/demolition lifecycle validation is handled by later M1 tasks.
                }
            }
            if (blueprint.category() == dev.minecraftempires.common.building.BuildingCategory.HEADQUARTERS
                    && building.state() != BuildingState.DEMOLISHED) {
                final UUID previousHeadquarters = headquartersByFaction.putIfAbsent(
                        building.ownerFactionId(), building.id());
                if (previousHeadquarters != null && !previousHeadquarters.equals(building.id())) {
                    issues.add(new Issue(Severity.ERROR, "Faction " + building.ownerFactionId()
                            + " has multiple non-demolished headquarters buildings "
                            + previousHeadquarters + " and " + building.id() + "."));
                }
            }
            if (building.blueprintVersion() != blueprint.version()) {
                issues.add(new Issue(Severity.ERROR, "Building " + building.id()
                        + " uses blueprint version " + building.blueprintVersion()
                        + " but catalog version is " + blueprint.version() + "."));
            }
            if (!building.templateChecksum().equals(blueprint.templateChecksum())) {
                issues.add(new Issue(Severity.ERROR, "Building " + building.id()
                        + " manifest checksum does not match blueprint " + blueprint.id() + "."));
            }
            if (!blueprint.allowsRotation(building.rotationDegrees())) {
                issues.add(new Issue(Severity.ERROR, "Building " + building.id()
                        + " uses unsupported rotation " + building.rotationDegrees() + "."));
            }
            final long maxCursor = (long) blueprint.sizeX() * blueprint.sizeY() * blueprint.sizeZ();
            if (building.placementCursor() > maxCursor) {
                issues.add(new Issue(Severity.ERROR, "Building " + building.id()
                        + " placement cursor " + building.placementCursor()
                        + " exceeds blueprint volume " + maxCursor + "."));
            }
            if (building.worldSyncState() == WorldSyncState.NOT_PLACED && building.placementCursor() != 0) {
                issues.add(new Issue(Severity.ERROR, "Building " + building.id()
                        + " is NOT_PLACED but has placement cursor " + building.placementCursor() + "."));
            }
            if (building.worldSyncState() == WorldSyncState.SYNCED && building.placementCursor() == 0) {
                issues.add(new Issue(Severity.WARNING, "Building " + building.id()
                        + " is SYNCED with a zero placement cursor; verify its template and saved record."));
            }
            if (building.state() == BuildingState.NEEDS_REPAIR
                    && building.worldSyncState() != WorldSyncState.NEEDS_REPAIR) {
                issues.add(new Issue(Severity.WARNING, "Building " + building.id()
                        + " is NEEDS_REPAIR but worldSyncState is " + building.worldSyncState() + "."));
            }
        }
    }

    private static void validateBuilderJobs(
            final Map<UUID, BuilderJobRecord> jobs,
            final Map<UUID, BuildingRecord> buildings,
            final Map<UUID, CitizenRecord> citizens,
            final List<Issue> issues
    ) {
        final Map<UUID, UUID> activeJobByBuilding = new HashMap<>();
        final Map<UUID, UUID> activeJobByCitizen = new HashMap<>();
        for (BuilderJobRecord job : jobs.values()) {
            final BuildingRecord building = buildings.get(job.buildingId());
            if (building == null) {
                issues.add(new Issue(Severity.ERROR, "Builder job " + job.id()
                        + " references missing building " + job.buildingId() + "."));
                continue;
            }
            if (!building.ownerFactionId().equals(job.ownerFactionId())
                    || !building.settlementId().equals(job.settlementId())) {
                issues.add(new Issue(Severity.ERROR, "Builder job " + job.id()
                        + " owner/settlement does not match building " + building.id() + "."));
            }
            if (job.isActive()) {
                if (building.state() != BuildingState.UNDER_CONSTRUCTION) {
                    issues.add(new Issue(Severity.ERROR, "Active builder job " + job.id()
                            + " targets building " + building.id() + " in state " + building.state() + "."));
                }
                final UUID previous = activeJobByBuilding.putIfAbsent(job.buildingId(), job.id());
                if (previous != null) {
                    issues.add(new Issue(Severity.ERROR, "Building " + job.buildingId()
                            + " has multiple active builder jobs " + previous + " and " + job.id() + "."));
                }
            }
            if (job.state().requiresAssignedCitizen()) {
                final CitizenRecord citizen = citizens.get(job.assignedCitizenId());
                if (citizen == null || citizen.status() != CitizenStatus.ACTIVE
                        || !citizen.ownerFactionId().equals(job.ownerFactionId())
                        || !citizen.professionId().equals(BuilderJobRecord.BUILDER_PROFESSION_ID)
                        || (citizen.homeSettlementId() != null
                        && !citizen.homeSettlementId().equals(job.settlementId()))) {
                    issues.add(new Issue(Severity.ERROR, "Builder job " + job.id()
                            + " has ineligible assigned citizen " + job.assignedCitizenId() + "."));
                }
                final UUID previous = activeJobByCitizen.putIfAbsent(job.assignedCitizenId(), job.id());
                if (previous != null) {
                    issues.add(new Issue(Severity.ERROR, "Citizen " + job.assignedCitizenId()
                            + " is assigned to multiple active builder jobs " + previous + " and " + job.id() + "."));
                }
            }
        }
        for (BuildingRecord building : buildings.values()) {
            if (building.state() == BuildingState.UNDER_CONSTRUCTION
                    && !activeJobByBuilding.containsKey(building.id())) {
                issues.add(new Issue(Severity.ERROR, "UNDER_CONSTRUCTION building " + building.id()
                        + " has no active builder job."));
            }
        }
    }

    private static void validateBuildingOverlaps(
            final Map<UUID, BuildingRecord> buildings,
            final List<Issue> issues
    ) {
        final List<BuildingRecord> candidates = buildings.values().stream()
                .filter(building -> building.state() != BuildingState.DEMOLISHED)
                .toList();
        for (int firstIndex = 0; firstIndex < candidates.size(); firstIndex++) {
            final BuildingRecord first = candidates.get(firstIndex);
            final BlueprintDefinition firstBlueprint = BlueprintCatalog.find(first.blueprintId()).orElse(null);
            if (firstBlueprint == null || !firstBlueprint.allowsRotation(first.rotationDegrees())) {
                continue;
            }
            final int firstX = firstBlueprint.rotatedSizeX(first.rotationDegrees());
            final int firstY = firstBlueprint.sizeY();
            final int firstZ = firstBlueprint.rotatedSizeZ(first.rotationDegrees());
            for (int secondIndex = firstIndex + 1; secondIndex < candidates.size(); secondIndex++) {
                final BuildingRecord second = candidates.get(secondIndex);
                if (!first.dimensionId().equals(second.dimensionId())) {
                    continue;
                }
                final BlueprintDefinition secondBlueprint = BlueprintCatalog.find(second.blueprintId()).orElse(null);
                if (secondBlueprint == null || !secondBlueprint.allowsRotation(second.rotationDegrees())) {
                    continue;
                }
                if (intersects(first.origin(), firstX, firstY, firstZ,
                        second.origin(), secondBlueprint.rotatedSizeX(second.rotationDegrees()),
                        secondBlueprint.sizeY(), secondBlueprint.rotatedSizeZ(second.rotationDegrees()))) {
                    issues.add(new Issue(Severity.ERROR, "Buildings " + first.id() + " and " + second.id()
                            + " overlap in " + first.dimensionId() + "."));
                }
            }
        }
    }

    private static boolean intersects(
            final net.minecraft.core.BlockPos first,
            final int firstX,
            final int firstY,
            final int firstZ,
            final net.minecraft.core.BlockPos second,
            final int secondX,
            final int secondY,
            final int secondZ
    ) {
        final long ax = first.getX();
        final long ay = first.getY();
        final long az = first.getZ();
        final long bx = second.getX();
        final long by = second.getY();
        final long bz = second.getZ();
        return ax < bx + secondX && bx < ax + firstX
                && ay < by + secondY && by < ay + firstY
                && az < bz + secondZ && bz < az + firstZ;
    }

    public enum Severity {
        ERROR,
        WARNING
    }

    public record Issue(Severity severity, String message) {
    }

    public record Report(
            int factionsChecked,
            int settlementsChecked,
            int citizensChecked,
            int buildingsChecked,
            List<Issue> issues
    ) {
        public Report {
            issues = List.copyOf(issues);
        }

        public long errorCount() {
            return issues.stream().filter(issue -> issue.severity() == Severity.ERROR).count();
        }

        public long warningCount() {
            return issues.stream().filter(issue -> issue.severity() == Severity.WARNING).count();
        }

        public boolean passed() {
            return errorCount() == 0;
        }
    }
}
