package dev.minecraftempires.common.civilization;

import dev.minecraftempires.MinecraftEmpires;
import dev.minecraftempires.common.building.BlueprintCatalog;
import dev.minecraftempires.common.building.BlueprintDefinition;
import dev.minecraftempires.common.building.BuildingRecord;
import dev.minecraftempires.common.building.ConstructionProject;
import dev.minecraftempires.common.building.BuildingState;
import dev.minecraftempires.common.building.WorldSyncState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MinecraftEmpires.MOD_ID)
@PrefixGameTestTemplate(false)
public final class M1ConstructionGameTests {
    private M1ConstructionGameTests() {
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m1_005",
            timeoutTicks = 20
    )
    public static void constructionStartIsReservedOnlyAndRetrySafe(final GameTestHelper helper) {
        final Fixture fixture = createFixture(helper, "M1-005 Start", false);
        final ResourceLedger balanceAfterReservation = fixture.service().findFaction(fixture.faction().id())
                .orElseThrow().resources();

        final BuildingRecord started = fixture.service().startConstruction(fixture.building().id());
        final BuildingRecord retry = fixture.service().startConstruction(fixture.building().id());
        final ResourceLedger balanceAfterRetry = fixture.service().findFaction(fixture.faction().id())
                .orElseThrow().resources();

        helper.assertTrue(started.state() == BuildingState.UNDER_CONSTRUCTION
                        && started.constructionProgress() == 0L
                        && started.worldSyncState() == WorldSyncState.PENDING,
                "A reserved project must start at zero progress in UNDER_CONSTRUCTION");
        helper.assertTrue(started.reservedMaterials().asMap().equals(fixture.blueprint().materials().asMap()),
                "Starting work must retain the full initial escrow");
        helper.assertTrue(retry.id().equals(started.id()) && retry.state() == BuildingState.UNDER_CONSTRUCTION,
                "Repeating construction start must be a read-only no-op");
        helper.assertTrue(balanceAfterReservation.asMap().equals(balanceAfterRetry.asMap()),
                "Construction start retry must not change faction inventory");
        helper.succeed();
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m1_005",
            timeoutTicks = 20
    )
    public static void constructionCannotStartBeforeMaterialsAreReserved(final GameTestHelper helper) {
        final Fixture fixture = createUnreservedFixture(helper, "M1-005 Unreserved");
        final ResourceLedger balanceBefore = fixture.service().findFaction(fixture.faction().id())
                .orElseThrow().resources();

        boolean rejected = false;
        try {
            fixture.service().startConstruction(fixture.building().id());
        } catch (IllegalStateException expected) {
            rejected = expected.getMessage().contains("only from RESERVED");
        }
        final BuildingRecord unchanged = fixture.service().findBuilding(fixture.building().id()).orElseThrow();
        final ResourceLedger balanceAfter = fixture.service().findFaction(fixture.faction().id())
                .orElseThrow().resources();

        helper.assertTrue(rejected, "A PLANNED project must not start before material reservation");
        helper.assertTrue(unchanged.state() == BuildingState.PLANNED
                        && unchanged.reservedMaterials().isEmpty(),
                "Rejected construction start must leave the PLANNED record untouched");
        helper.assertTrue(balanceBefore.asMap().equals(balanceAfter.asMap()),
                "Rejected construction start must not modify faction inventory");
        helper.succeed();
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m1_005",
            timeoutTicks = 20
    )
    public static void constructionWorkConsumesEscrowAndRejectsDuplicateProgressToken(final GameTestHelper helper) {
        final Fixture fixture = createFixture(helper, "M1-005 Work Escrow", false);
        fixture.service().startConstruction(fixture.building().id());
        final long work = 25L;

        final BuildingRecord first = fixture.service().applyConstructionWork(
                fixture.building().id(), 0L, work);
        boolean duplicateRejected = false;
        try {
            fixture.service().applyConstructionWork(fixture.building().id(), 0L, work);
        } catch (IllegalStateException expected) {
            duplicateRejected = expected.getMessage().contains("Stale construction work batch");
        }
        final BuildingRecord afterDuplicate = fixture.service().findBuilding(fixture.building().id()).orElseThrow();
        final ResourceLedger expectedEscrow = ConstructionProject.remainingEscrow(
                fixture.blueprint().materials(), work, fixture.blueprint().workPoints());

        helper.assertTrue(first.constructionProgress() == work,
                "Construction progress must advance by the accepted work points");
        helper.assertTrue(first.reservedMaterials().asMap().equals(expectedEscrow.asMap()),
                "Escrow must be the deterministic cumulative remainder for the new progress");
        helper.assertTrue(duplicateRejected
                        && afterDuplicate.constructionProgress() == first.constructionProgress()
                        && afterDuplicate.reservedMaterials().asMap().equals(first.reservedMaterials().asMap()),
                "A repeated old progress token must be rejected without consuming escrow twice");
        helper.assertTrue(fixture.service().findFaction(fixture.faction().id()).orElseThrow()
                        .resources().isEmpty(),
                "Work must consume project escrow, not credit or debit faction availability again");
        helper.succeed();
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m1_005",
            timeoutTicks = 20
    )
    public static void staleConstructionBatchCannotDoubleAdvanceProgress(final GameTestHelper helper) {
        final Fixture fixture = createFixture(helper, "M1-005 Stale Batch", false);
        fixture.service().startConstruction(fixture.building().id());
        fixture.service().applyConstructionWork(fixture.building().id(), 0L, 1L);
        final BuildingRecord beforeRetry = fixture.service().findBuilding(fixture.building().id()).orElseThrow();

        boolean rejected = false;
        try {
            fixture.service().applyConstructionWork(fixture.building().id(), 0L, 2L);
        } catch (IllegalStateException expected) {
            rejected = expected.getMessage().contains("Stale construction work batch");
        }
        final BuildingRecord afterRetry = fixture.service().findBuilding(fixture.building().id()).orElseThrow();

        helper.assertTrue(rejected, "A different batch using an old expected progress token must be rejected");
        helper.assertTrue(afterRetry.constructionProgress() == beforeRetry.constructionProgress()
                        && afterRetry.reservedMaterials().asMap().equals(beforeRetry.reservedMaterials().asMap()),
                "Rejected stale work must leave progress and escrow unchanged");
        helper.succeed();
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m1_005",
            timeoutTicks = 20
    )
    public static void constructionWorkSaturatesAtRequiredProgressWithoutOverflow(final GameTestHelper helper) {
        final Fixture fixture = createFixture(helper, "M1-005 Work Clamp", false);
        fixture.service().startConstruction(fixture.building().id());

        final BuildingRecord completed = fixture.service().applyConstructionWork(
                fixture.building().id(), 0L, Long.MAX_VALUE);

        helper.assertTrue(completed.constructionProgress() == completed.workPointsRequired(),
                "Excess work must clamp exactly to the project's required progress");
        helper.assertTrue(completed.reservedMaterials().isEmpty(),
                "All escrow must be consumed at full work progress");
        helper.assertTrue(completed.state() == BuildingState.UNDER_CONSTRUCTION,
                "A project without SYNCED world blocks must not become ACTIVE");
        helper.succeed();
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m1_005",
            timeoutTicks = 20
    )
    public static void completedConstructionWaitsForWorldSyncBeforeActivation(final GameTestHelper helper) {
        final Fixture fixture = createFixture(helper, "M1-005 Await Sync", false);
        fixture.service().startConstruction(fixture.building().id());
        final BuildingRecord completed = fixture.service().applyConstructionWork(
                fixture.building().id(), 0L, Long.MAX_VALUE);

        boolean activationRejected = false;
        try {
            fixture.service().activateCompletedBuilding(fixture.building().id());
        } catch (IllegalStateException expected) {
            activationRejected = expected.getMessage().contains("before world blocks are SYNCED");
        }
        fixture.data().putBuilding(completed.withPlacementCursor(25, WorldSyncState.SYNCED));
        final BuildingRecord activated = fixture.service().activateCompletedBuilding(fixture.building().id());

        helper.assertTrue(activationRejected,
                "A fully worked project must not activate while its world representation is unsynced");
        helper.assertTrue(activated.state() == BuildingState.ACTIVE
                        && activated.constructionProgress() == activated.workPointsRequired()
                        && activated.reservedMaterials().isEmpty()
                        && activated.worldSyncState() == WorldSyncState.SYNCED,
                "A fully worked project must activate after the world representation becomes SYNCED");
        helper.succeed();
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m1_005",
            timeoutTicks = 20
    )
    public static void syncedProjectActivatesWhenFinalWorkBatchCompletes(final GameTestHelper helper) {
        final Fixture fixture = createFixture(helper, "M1-005 Sync Ready", true);
        fixture.service().startConstruction(fixture.building().id());

        final BuildingRecord active = fixture.service().applyConstructionWork(
                fixture.building().id(), 0L, Long.MAX_VALUE);

        helper.assertTrue(active.state() == BuildingState.ACTIVE,
                "A fully worked project with already-SYNCED blocks must activate at completion");
        helper.assertTrue(active.reservedMaterials().isEmpty()
                        && active.constructionProgress() == active.workPointsRequired(),
                "Activation requires complete work and a fully consumed escrow");
        helper.assertTrue(fixture.service().activateCompletedBuilding(active.id()).state() == BuildingState.ACTIVE,
                "Activation retry must be idempotent");
        helper.succeed();
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m1_005",
            timeoutTicks = 20
    )
    public static void constructionProgressAndRemainingEscrowSurviveSavedDataRoundTrip(final GameTestHelper helper) {
        final Fixture fixture = createFixture(helper, "M1-005 Persistence", false);
        fixture.service().startConstruction(fixture.building().id());
        fixture.service().applyConstructionWork(fixture.building().id(), 0L, 37L);

        final EmpiresWorldData restored = EmpiresWorldData.load(fixture.data().save(new CompoundTag()));
        final BuildingRecord restoredBuilding = restored.findBuilding(fixture.building().id()).orElseThrow();
        final WorldDataValidator.Report report = WorldDataValidator.validate(restored);
        final ResourceLedger expected = ConstructionProject.remainingEscrow(
                fixture.blueprint().materials(), 37L, fixture.blueprint().workPoints());

        helper.assertTrue(restoredBuilding.state() == BuildingState.UNDER_CONSTRUCTION
                        && restoredBuilding.constructionProgress() == 37L,
                "Construction state and cumulative progress must survive SavedData serialization");
        helper.assertTrue(restoredBuilding.reservedMaterials().asMap().equals(expected.asMap()),
                "Partially consumed material escrow must survive SavedData serialization");
        helper.assertTrue(report.passed() && report.warningCount() == 0,
                "Restored construction project must validate cleanly: errors=" + report.errorCount()
                        + ", warnings=" + report.warningCount());
        helper.succeed();
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m1_005",
            timeoutTicks = 20
    )
    public static void validatorDetectsConstructionEscrowMismatch(final GameTestHelper helper) {
        final Fixture fixture = createFixture(helper, "M1-005 Escrow Validation", false);
        fixture.service().startConstruction(fixture.building().id());
        fixture.service().applyConstructionWork(fixture.building().id(), 0L, 25L);
        final BuildingRecord current = fixture.service().findBuilding(fixture.building().id()).orElseThrow();
        fixture.data().putBuilding(current.withReservedMaterials(ResourceLedger.empty()));

        final WorldDataValidator.Report report = WorldDataValidator.validate(fixture.data());
        helper.assertTrue(!report.passed() && report.errorCount() == 1,
                "Validator must detect an UNDER_CONSTRUCTION escrow inconsistent with cumulative work");
        helper.succeed();
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m1_007",
            timeoutTicks = 20
    )
    public static void stagedPlacementTargetIsMonotonicAndReachesFullTemplate(final GameTestHelper helper) {
        final int totalBlocks = 204;
        final int zero = dev.minecraftempires.common.building.BuildingPlacementService
                .targetBlockCount(0L, 100L, totalBlocks);
        final int first = dev.minecraftempires.common.building.BuildingPlacementService
                .targetBlockCount(1L, 100L, totalBlocks);
        final int middle = dev.minecraftempires.common.building.BuildingPlacementService
                .targetBlockCount(50L, 100L, totalBlocks);
        final int complete = dev.minecraftempires.common.building.BuildingPlacementService
                .targetBlockCount(100L, 100L, totalBlocks);
        helper.assertTrue(zero == 0 && first <= middle && middle < complete && complete == totalBlocks,
                "Staged placement must map work progress monotonically to a complete template prefix");
        helper.succeed();
    }

    private static Fixture createUnreservedFixture(
            final GameTestHelper helper,
            final String factionName
    ) {
        final var level = helper.getLevel();
        final EmpiresWorldData data = new EmpiresWorldData();
        final CivilizationService service = new CivilizationService(new SavedDataCivilizationRepository(data));
        final FactionRecord faction = service.createFaction(
                factionName, new ResourceLocation("empires", "bearfolk"), ControllerType.AI, level.getGameTime());
        final BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        final SettlementRecord settlement = service.createSettlement(
                faction.id(), level.dimension().location(), origin, level.getGameTime());
        final BlueprintDefinition blueprint = BlueprintCatalog.find(BlueprintCatalog.WAREHOUSE_PROTOTYPE_ID)
                .orElseThrow();
        service.setResources(faction.id(), blueprint.materials());
        final BuildingRecord planned = service.planBuilding(settlement.id(), blueprint.id(),
                level.dimension().location(), origin, 0, level.getGameTime());
        return new Fixture(data, service, faction, blueprint, planned);
    }

    private static Fixture createFixture(
            final GameTestHelper helper,
            final String factionName,
            final boolean worldAlreadySynced
    ) {
        final var level = helper.getLevel();
        final EmpiresWorldData data = new EmpiresWorldData();
        final CivilizationService service = new CivilizationService(new SavedDataCivilizationRepository(data));
        final FactionRecord faction = service.createFaction(
                factionName, new ResourceLocation("empires", "bearfolk"), ControllerType.AI, level.getGameTime());
        final BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        final SettlementRecord settlement = service.createSettlement(
                faction.id(), level.dimension().location(), origin, level.getGameTime());
        final BlueprintDefinition blueprint = BlueprintCatalog.find(BlueprintCatalog.WAREHOUSE_PROTOTYPE_ID)
                .orElseThrow();
        service.setResources(faction.id(), blueprint.materials());
        BuildingRecord planned = service.planBuilding(settlement.id(), blueprint.id(),
                level.dimension().location(), origin, 0, level.getGameTime());
        if (worldAlreadySynced) {
            planned = planned.withPlacementCursor(25, WorldSyncState.SYNCED);
            data.putBuilding(planned);
        }
        final BuildingRecord reserved = service.reserveBuildingMaterials(planned.id());
        return new Fixture(data, service, faction, blueprint, reserved);
    }

    private record Fixture(
            EmpiresWorldData data,
            CivilizationService service,
            FactionRecord faction,
            BlueprintDefinition blueprint,
            BuildingRecord building
    ) {
    }
}
