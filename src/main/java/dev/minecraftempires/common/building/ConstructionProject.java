package dev.minecraftempires.common.building;

import dev.minecraftempires.common.civilization.ResourceLedger;
import net.minecraft.resources.ResourceLocation;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Pure domain rules for the persisted construction lifecycle.
 *
 * <p>The BuildingRecord remains the persisted aggregate. Work progress and the
 * remaining material escrow are derived together, so a retry cannot consume the
 * same batch twice and no new SavedData schema is required.</p>
 */
public final class ConstructionProject {
    private ConstructionProject() {
    }

    public static BuildingRecord start(
            final BuildingRecord building,
            final BlueprintDefinition blueprint
    ) {
        validateBlueprint(building, blueprint);
        if (building.state() != BuildingState.RESERVED) {
            throw new IllegalStateException("Construction can start only from RESERVED: "
                    + building.id() + " is " + building.state());
        }
        if (building.constructionProgress() != 0L
                || !building.reservedMaterials().asMap().equals(blueprint.materials().asMap())) {
            throw new IllegalStateException("Reserved project has inconsistent initial progress or escrow: "
                    + building.id());
        }
        if (building.worldSyncState() != WorldSyncState.NOT_PLACED
                && building.worldSyncState() != WorldSyncState.SYNCED) {
            throw new IllegalStateException("Construction cannot start while world placement is "
                    + building.worldSyncState() + ": " + building.id());
        }
        return building.withState(BuildingState.UNDER_CONSTRUCTION, building.worldSyncState());
    }

    /**
     * Applies a cumulative work-point batch. expectedProgress is the compare-and-set
     * token; once the persisted progress advances, every submission carrying the old
     * token is rejected. This prevents a retry or competing worker from applying twice.
     */
    public static BuildingRecord applyWork(
            final BuildingRecord building,
            final BlueprintDefinition blueprint,
            final long expectedProgress,
            final long workPoints
    ) {
        validateBlueprint(building, blueprint);
        if (workPoints <= 0L) {
            throw new IllegalArgumentException("Construction work points must be positive");
        }
        if (expectedProgress < 0L || expectedProgress > building.workPointsRequired()) {
            throw new IllegalArgumentException("Expected construction progress is outside the project range");
        }

        final long remainingWork = building.workPointsRequired() - expectedProgress;
        final long targetProgress = expectedProgress + Math.min(workPoints, remainingWork);

        if (building.constructionProgress() != expectedProgress) {
            throw new IllegalStateException("Stale construction work batch for building " + building.id()
                    + ": expectedProgress=" + expectedProgress
                    + ", actualProgress=" + building.constructionProgress()
                    + "; read the latest building progress before submitting new work");
        }

        if (building.state() != BuildingState.UNDER_CONSTRUCTION) {
            throw new IllegalStateException("Construction work requires UNDER_CONSTRUCTION state: "
                    + building.id() + " is " + building.state());
        }
        if (expectedProgress == building.workPointsRequired()) {
            throw new IllegalStateException("Construction work is already complete; activation is pending: " + building.id());
        }
        if (building.worldSyncState() != WorldSyncState.NOT_PLACED
                && building.worldSyncState() != WorldSyncState.SYNCED) {
            throw new IllegalStateException("Construction is paused while world placement is "
                    + building.worldSyncState() + ": " + building.id());
        }

        final ResourceLedger expectedCurrentEscrow = remainingEscrow(
                blueprint.materials(), expectedProgress, building.workPointsRequired());
        if (!building.reservedMaterials().asMap().equals(expectedCurrentEscrow.asMap())) {
            throw new IllegalStateException("Construction escrow does not match persisted progress for building "
                    + building.id());
        }

        final ResourceLedger nextEscrow = remainingEscrow(
                blueprint.materials(), targetProgress, building.workPointsRequired());
        final BuildingRecord progressed = building.withConstructionProgress(targetProgress, nextEscrow);
        if (targetProgress == building.workPointsRequired()
                && building.worldSyncState() == WorldSyncState.SYNCED) {
            return progressed.withState(BuildingState.ACTIVE, building.worldSyncState());
        }
        return progressed;
    }

    /** Activates a completed logical project only after its physical template is synced. */
    public static BuildingRecord activate(final BuildingRecord building, final BlueprintDefinition blueprint) {
        validateBlueprint(building, blueprint);
        if (building.state() == BuildingState.ACTIVE) {
            return building;
        }
        if (building.state() != BuildingState.UNDER_CONSTRUCTION) {
            throw new IllegalStateException("Only an UNDER_CONSTRUCTION project can be activated: "
                    + building.id());
        }
        if (building.constructionProgress() != building.workPointsRequired()
                || !building.reservedMaterials().isEmpty()) {
            throw new IllegalStateException("Construction work and material escrow are not fully settled: "
                    + building.id());
        }
        if (building.worldSyncState() != WorldSyncState.SYNCED) {
            throw new IllegalStateException("Building cannot activate before world blocks are SYNCED: "
                    + building.id() + " is " + building.worldSyncState());
        }
        return building.withState(BuildingState.ACTIVE, WorldSyncState.SYNCED);
    }

    /** Calculates the remaining escrow from total blueprint cost and cumulative work. */
    public static ResourceLedger remainingEscrow(
            final ResourceLedger totalCost,
            final long completedWork,
            final long totalWork
    ) {
        Objects.requireNonNull(totalCost, "totalCost");
        if (totalWork < 1L || completedWork < 0L || completedWork > totalWork) {
            throw new IllegalArgumentException("Invalid construction work fraction: "
                    + completedWork + "/" + totalWork);
        }
        if (completedWork == 0L) {
            return totalCost;
        }
        if (completedWork == totalWork) {
            return ResourceLedger.empty();
        }

        final BigInteger denominator = BigInteger.valueOf(totalWork);
        final BigInteger numeratorWork = BigInteger.valueOf(completedWork);
        final Map<ResourceLocation, Long> remaining = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Long> entry : totalCost.asMap().entrySet()) {
            final long consumed = BigInteger.valueOf(entry.getValue())
                    .multiply(numeratorWork)
                    .divide(denominator)
                    .longValueExact();
            final long amountLeft = entry.getValue() - consumed;
            if (amountLeft > 0L) {
                remaining.put(entry.getKey(), amountLeft);
            }
        }
        return ResourceLedger.of(remaining);
    }

    private static void validateBlueprint(
            final BuildingRecord building,
            final BlueprintDefinition blueprint
    ) {
        Objects.requireNonNull(building, "building");
        Objects.requireNonNull(blueprint, "blueprint");
        if (!building.blueprintId().equals(blueprint.id())
                || building.blueprintVersion() != blueprint.version()
                || !building.templateChecksum().equals(blueprint.templateChecksum())
                || building.workPointsRequired() != blueprint.workPoints()) {
            throw new IllegalStateException("Construction blueprint definition no longer matches building "
                    + building.id());
        }
    }
}
