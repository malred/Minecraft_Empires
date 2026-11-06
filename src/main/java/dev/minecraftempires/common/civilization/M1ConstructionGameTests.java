package dev.minecraftempires.common.civilization;

import dev.minecraftempires.MinecraftEmpires;
import dev.minecraftempires.common.EmpiresConfig;
import dev.minecraftempires.common.building.BlueprintCatalog;
import dev.minecraftempires.common.building.BlueprintTemplate;
import dev.minecraftempires.common.building.BuildingPlacementService;
import dev.minecraftempires.common.building.BlueprintDefinition;
import dev.minecraftempires.common.building.BuildingRecord;
import dev.minecraftempires.common.building.ConstructionProject;
import dev.minecraftempires.common.building.BuildingState;
import dev.minecraftempires.common.building.WorldSyncState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
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


    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m1_007",
            timeoutTicks = 40
    )
    public static void stagedConstructionPlacesRealBlocksAndResumesAfterConflict(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final Fixture fixture = createFixture(helper, "M1-007 Physical Staging", false);
        final BuildingRecord started = fixture.service().startConstruction(
                fixture.building().id(), level.getGameTime());
        final BlueprintTemplate template = BlueprintTemplate.load(
                level.getServer().getResourceManager(), fixture.blueprint());

        helper.assertTrue(template.blocks().size() == 25,
                "Warehouse integration fixture must contain exactly 25 template blocks");
        setWarehouseGround(level, started.origin());

        // Zero work must not write any structure blocks.
        final int zeroProgressPlaced = BuildingPlacementService.processPending(
                level.getServer(), fixture.data(), fixture.service());
        BuildingRecord current = fixture.service().findBuilding(started.id()).orElseThrow();
        helper.assertTrue(zeroProgressPlaced == 0 && current.placementCursor() == 0
                        && current.worldSyncState() == WorldSyncState.PENDING,
                "A zero-work construction must stay pending at cursor 0");
        assertTemplatePrefix(helper, level, current, template, 0);

        // Half of 100 work points unlocks floor(50 * 25 / 100) = 12 template blocks.
        fixture.service().applyConstructionWork(started.id(), 0L, 50L);
        current = fixture.service().findBuilding(started.id()).orElseThrow();
        final BlockPos conflictPos = current.origin().offset(template.blocks().get(0).position());
        level.setBlock(conflictPos, Blocks.BEDROCK.defaultBlockState(), 3);

        final int conflictPlaced = BuildingPlacementService.processPending(
                level.getServer(), fixture.data(), fixture.service());
        final BuildingRecord paused = fixture.service().findBuilding(started.id()).orElseThrow();
        helper.assertTrue(conflictPlaced == 0
                        && paused.state() == BuildingState.UNDER_CONSTRUCTION
                        && paused.constructionProgress() == 50L
                        && paused.placementCursor() == 0
                        && paused.worldSyncState() == WorldSyncState.NEEDS_REPAIR,
                "A protected-block conflict must pause placement without losing work or advancing the cursor");

        level.setBlock(conflictPos, Blocks.AIR.defaultBlockState(), 3);
        BuildingPlacementService.resumePlacement(level, fixture.service(), started.id());
        final BuildingRecord resumed = fixture.service().findBuilding(started.id()).orElseThrow();
        helper.assertTrue(resumed.state() == BuildingState.UNDER_CONSTRUCTION
                        && resumed.constructionProgress() == 50L
                        && resumed.placementCursor() == 0
                        && resumed.worldSyncState() == WorldSyncState.PENDING,
                "Resuming after clearing a conflict must preserve construction progress and cursor");

        current = processPlacementToCursor(helper, level, fixture, 12, template.blocks().size() + 2);
        helper.assertTrue(current.state() == BuildingState.UNDER_CONSTRUCTION
                        && current.constructionProgress() == 50L
                        && current.worldSyncState() == WorldSyncState.PENDING,
                "Half-complete work must leave a partially placed, non-active building");
        assertTemplatePrefix(helper, level, current, template, 12);

        fixture.service().applyConstructionWork(started.id(), 50L, 50L);
        final BuildingRecord fullyWorked = fixture.service().findBuilding(started.id()).orElseThrow();
        helper.assertTrue(fullyWorked.state() == BuildingState.UNDER_CONSTRUCTION
                        && fullyWorked.constructionProgress() == 100L
                        && fullyWorked.reservedMaterials().isEmpty()
                        && fullyWorked.worldSyncState() == WorldSyncState.PENDING,
                "Full work alone must not activate a building before all world blocks are synchronized");

        current = processPlacementToCursor(helper, level, fixture, 25, template.blocks().size() + 2);
        final BuildingRecord active = fixture.service().findBuilding(started.id()).orElseThrow();
        helper.assertTrue(current.placementCursor() == 25
                        && active.state() == BuildingState.ACTIVE
                        && active.constructionProgress() == 100L
                        && active.reservedMaterials().isEmpty()
                        && active.worldSyncState() == WorldSyncState.SYNCED,
                "The final template block must synchronize and atomically activate the completed building");
        assertTemplatePrefix(helper, level, active, template, 25);

        final var jobs = fixture.data().builderJobsForBuilding(started.id());
        helper.assertTrue(jobs.size() == 1 && jobs.get(0).state() == BuilderJobState.COMPLETED,
                "Activating the staged building must complete its one persisted Builder Job");
        final WorldDataValidator.Report report = WorldDataValidator.validate(fixture.data());
        helper.assertTrue(report.passed() && report.warningCount() == 0,
                "The completed staged project must validate cleanly: errors=" + report.errorCount()
                        + ", warnings=" + report.warningCount());
        helper.succeed();
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m1_007",
            timeoutTicks = 40
    )
    public static void stagedPlacementResumesFromSavedCursorAfterSavedDataReload(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final Fixture fixture = createFixture(helper, "M1-007 Cursor Reload", false);
        final BuildingRecord started = fixture.service().startConstruction(
                fixture.building().id(), level.getGameTime());
        final BlueprintTemplate template = BlueprintTemplate.load(
                level.getServer().getResourceManager(), fixture.blueprint());
        setWarehouseGround(level, started.origin());
        fixture.service().applyConstructionWork(started.id(), 0L, 50L);

        BuildingPlacementService.processPending(level.getServer(), fixture.data(), fixture.service());
        final BuildingRecord beforeReload = fixture.service().findBuilding(started.id()).orElseThrow();
        final int savedCursor = beforeReload.placementCursor();
        helper.assertTrue(savedCursor > 0 && savedCursor <= 12
                        && beforeReload.constructionProgress() == 50L
                        && beforeReload.worldSyncState() == WorldSyncState.PENDING,
                "The first construction batch must persist a partial cursor within the 50% work target");
        assertTemplatePrefix(helper, level, beforeReload, template, savedCursor);

        // Recreate the authoritative store and application service from the serialized SavedData.
        final EmpiresWorldData restoredData = EmpiresWorldData.load(fixture.data().save(new CompoundTag()));
        final CivilizationService restoredService = new CivilizationService(
                new SavedDataCivilizationRepository(restoredData));
        final Fixture restoredFixture = new Fixture(
                restoredData, restoredService, fixture.faction(), fixture.blueprint(), fixture.building());
        final BuildingRecord restoredBuilding = restoredService.findBuilding(started.id()).orElseThrow();
        helper.assertTrue(restoredBuilding.placementCursor() == savedCursor
                        && restoredBuilding.constructionProgress() == 50L
                        && restoredBuilding.worldSyncState() == WorldSyncState.PENDING,
                "SavedData reload must preserve construction progress and placement cursor");
        helper.assertTrue(restoredData.builderJobsForBuilding(started.id()).size() == 1
                        && restoredData.builderJobsForBuilding(started.id()).get(0).state() == BuilderJobState.AVAILABLE,
                "SavedData reload must preserve the active construction's Builder Job");

        BuildingRecord current = processPlacementToCursor(
                helper, level, restoredFixture, 12, template.blocks().size() + 2);
        helper.assertTrue(current.placementCursor() == 12
                        && current.constructionProgress() == 50L
                        && current.state() == BuildingState.UNDER_CONSTRUCTION,
                "Placement after SavedData reload must continue from the saved cursor, not restart");
        assertTemplatePrefix(helper, level, current, template, 12);

        restoredService.applyConstructionWork(started.id(), 50L, 50L);
        current = processPlacementToCursor(
                helper, level, restoredFixture, 25, template.blocks().size() + 2);
        final BuildingRecord active = restoredService.findBuilding(started.id()).orElseThrow();
        helper.assertTrue(current.placementCursor() == 25
                        && active.state() == BuildingState.ACTIVE
                        && active.worldSyncState() == WorldSyncState.SYNCED
                        && active.constructionProgress() == 100L,
                "Reloaded construction must finish all template blocks and activate normally");
        assertTemplatePrefix(helper, level, active, template, 25);
        helper.succeed();
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m1_007",
            timeoutTicks = 20
    )
    public static void pendingConstructionDoesNotForceLoadRemoteChunks(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final EmpiresWorldData data = new EmpiresWorldData();
        final CivilizationService service = new CivilizationService(new SavedDataCivilizationRepository(data));
        final FactionRecord faction = service.createFaction(
                "M1-007 Unloaded Chunk", ResourceLocation.fromNamespaceAndPath("empires", "bearfolk"), ControllerType.AI,
                level.getGameTime());
        final BlockPos remoteOrigin = new BlockPos(4096, 64, 4096);
        final SettlementRecord settlement = service.createSettlement(
                faction.id(), level.dimension().location(), remoteOrigin, level.getGameTime());
        final BlueprintDefinition blueprint = BlueprintCatalog.find(BlueprintCatalog.WAREHOUSE_PROTOTYPE_ID)
                .orElseThrow();
        service.setResources(faction.id(), blueprint.materials());
        final BuildingRecord planned = service.planBuilding(settlement.id(), blueprint.id(),
                level.dimension().location(), remoteOrigin, 0, level.getGameTime());
        final BuildingRecord reserved = service.reserveBuildingMaterials(planned.id());
        service.startConstruction(reserved.id(), level.getGameTime());
        service.applyConstructionWork(planned.id(), 0L, 50L);

        final BlockPos remoteSupport = remoteOrigin.below();
        final boolean initiallyUnloaded = !level.hasChunkAt(remoteSupport);
        final int placed = BuildingPlacementService.processPending(level.getServer(), data, service);
        final BuildingRecord waiting = service.findBuilding(planned.id()).orElseThrow();

        helper.assertTrue(initiallyUnloaded,
                "Remote-chunk fixture must begin outside the currently loaded test area");
        helper.assertTrue(placed == 0 && !level.hasChunkAt(remoteSupport)
                        && waiting.state() == BuildingState.UNDER_CONSTRUCTION
                        && waiting.constructionProgress() == 50L
                        && waiting.placementCursor() == 0
                        && waiting.worldSyncState() == WorldSyncState.PENDING,
                "Pending construction must pause without force-loading an unloaded target chunk");
        helper.succeed();
    }

    @GameTest(template = "gametest_empty_5x5x5", templateNamespace = "empires", batch = "empires.m1_008a", timeoutTicks = 20)
    public static void cancellingPlannedBuildingDoesNotCreditInventory(final GameTestHelper helper) {
        final Fixture fixture = createUnreservedFixture(helper, "M1-008A Planned Cancel");
        final ResourceLedger before = fixture.service().findFaction(fixture.faction().id()).orElseThrow().resources();
        final BuildingRecord cancelled = fixture.service().cancelBuilding(fixture.building().id(), helper.getLevel().getGameTime());
        final ResourceLedger after = fixture.service().findFaction(fixture.faction().id()).orElseThrow().resources();
        final BuildingRecord retried = fixture.service().cancelBuilding(fixture.building().id(), helper.getLevel().getGameTime());
        helper.assertTrue(cancelled.state() == BuildingState.CANCELLED && cancelled.reservedMaterials().isEmpty()
                        && before.asMap().equals(after.asMap()) && retried.state() == BuildingState.CANCELLED,
                "Cancelling a PLANNED building must be idempotent and must not mint resources");
        helper.succeed();
    }

    @GameTest(template = "gametest_empty_5x5x5", templateNamespace = "empires", batch = "empires.m1_008a", timeoutTicks = 20)
    public static void cancellingReservedBuildingRefundsFullEscrowOnce(final GameTestHelper helper) {
        final Fixture fixture = createFixture(helper, "M1-008A Reserved Cancel", false);
        final BuildingRecord cancelled = fixture.service().cancelBuilding(fixture.building().id(), helper.getLevel().getGameTime());
        final ResourceLedger after = fixture.service().findFaction(fixture.faction().id()).orElseThrow().resources();
        final BuildingRecord retried = fixture.service().cancelBuilding(fixture.building().id(), helper.getLevel().getGameTime());
        final ResourceLedger afterRetry = fixture.service().findFaction(fixture.faction().id()).orElseThrow().resources();
        helper.assertTrue(cancelled.state() == BuildingState.CANCELLED && cancelled.reservedMaterials().isEmpty()
                        && after.asMap().equals(fixture.blueprint().materials().asMap())
                        && afterRetry.asMap().equals(after.asMap()) && retried.state() == BuildingState.CANCELLED,
                "Cancelling RESERVED must refund full escrow exactly once");
        helper.succeed();
    }

    @GameTest(template = "gametest_empty_5x5x5", templateNamespace = "empires", batch = "empires.m1_008a", timeoutTicks = 20)
    public static void cancellingConstructionRefundsOnlyRemainingEscrowAndReleasesBuilder(final GameTestHelper helper) {
        final Fixture fixture = createFixture(helper, "M1-008A Construction Cancel", true);
        fixture.service().startConstruction(fixture.building().id(), helper.getLevel().getGameTime());
        final BuilderJobRecord job = fixture.service().builderJobsForBuilding(fixture.building().id()).get(0);
        final CitizenRecord builder = fixture.service().assignBuilder(
                fixture.service().createCitizen(fixture.faction().id(), "Cancellation Builder", helper.getLevel().getGameTime()).id());
        fixture.service().claimBuilderJob(job.id(), builder.id(), helper.getLevel().getGameTime());
        fixture.service().startBuilderJob(job.id(), builder.id(), helper.getLevel().getGameTime());
        fixture.service().applyConstructionWork(fixture.building().id(), 0L, 50L);
        final BuildingRecord beforeCancel = fixture.service().findBuilding(fixture.building().id()).orElseThrow();
        final ResourceLedger expectedRefund = ConstructionProject.remainingEscrow(fixture.blueprint().materials(), 50L,
                fixture.blueprint().workPoints());
        final BuildingRecord cancelled = fixture.service().cancelBuilding(fixture.building().id(), helper.getLevel().getGameTime());
        final ResourceLedger refunded = fixture.service().findFaction(fixture.faction().id()).orElseThrow().resources();
        final BuilderJobRecord cancelledJob = fixture.service().findBuilderJob(job.id()).orElseThrow();
        final BuildingRecord retry = fixture.service().cancelBuilding(fixture.building().id(), helper.getLevel().getGameTime());
        final ResourceLedger afterRetry = fixture.service().findFaction(fixture.faction().id()).orElseThrow().resources();
        final WorldDataValidator.Report report = WorldDataValidator.validate(fixture.data());
        helper.assertTrue(cancelled.state() == BuildingState.CANCELLED && cancelled.constructionProgress() == 50L
                        && cancelled.placementCursor() == beforeCancel.placementCursor()
                        && cancelled.worldSyncState() == beforeCancel.worldSyncState()
                        && cancelled.reservedMaterials().isEmpty(),
                "Cancellation must preserve progress and world placement metadata while clearing escrow");
        helper.assertTrue(refunded.asMap().equals(expectedRefund.asMap())
                        && cancelledJob.state() == BuilderJobState.CANCELLED && cancelledJob.assignedCitizenId() == null
                        && retry.state() == BuildingState.CANCELLED && afterRetry.asMap().equals(refunded.asMap()),
                "Cancellation must refund only remaining escrow, release the worker and never refund twice");
        helper.assertTrue(report.passed() && report.errorCount() == 0L,
                "Cancelled building and builder job must pass world data validation");
        helper.succeed();
    }

    @GameTest(template = "gametest_empty_5x5x5", templateNamespace = "empires", batch = "empires.m1_008a", timeoutTicks = 20)
    public static void cancelledPendingConstructionDoesNotPlaceMoreBlocks(final GameTestHelper helper) {
        final Fixture fixture = createFixture(helper, "M1-008A Stop Pending Placement", false);
        setWarehouseGround(helper.getLevel(), fixture.building().origin());
        fixture.service().startConstruction(fixture.building().id(), helper.getLevel().getGameTime());
        fixture.service().applyConstructionWork(fixture.building().id(), 0L, 50L);
        final BuildingRecord cancelled = fixture.service().cancelBuilding(
                fixture.building().id(), helper.getLevel().getGameTime());
        final int placed = BuildingPlacementService.processPending(
                helper.getLevel().getServer(), fixture.data(), fixture.service());
        final BuildingRecord afterTick = fixture.service().findBuilding(fixture.building().id()).orElseThrow();
        helper.assertTrue(cancelled.state() == BuildingState.CANCELLED && placed == 0
                        && afterTick.placementCursor() == 0
                        && afterTick.worldSyncState() == WorldSyncState.PENDING,
                "A cancelled PENDING project must not place additional blocks on later server ticks");
        helper.assertTrue(helper.getLevel().getBlockState(fixture.building().origin()).isAir(),
                "Cancellation must leave the world untouched rather than placing or deleting blocks");
        helper.succeed();
    }

    private static void setWarehouseGround(final ServerLevel level, final BlockPos origin) {
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
    }

    private static BuildingRecord processPlacementToCursor(
            final GameTestHelper helper,
            final ServerLevel level,
            final Fixture fixture,
            final int targetCursor,
            final int maxCalls
    ) {
        BuildingRecord current = fixture.service().findBuilding(fixture.building().id()).orElseThrow();
        int processCalls = 0;
        while (current.placementCursor() < targetCursor && processCalls < maxCalls) {
            final int previousCursor = current.placementCursor();
            final int advanced = BuildingPlacementService.processPending(
                    level.getServer(), fixture.data(), fixture.service());
            current = fixture.service().findBuilding(fixture.building().id()).orElseThrow();
            final int cursorDelta = current.placementCursor() - previousCursor;
            helper.assertTrue(cursorDelta >= 0 && cursorDelta == advanced
                            && advanced <= EmpiresConfig.BUILDING_PLACEMENT_BLOCKS_PER_TICK.get(),
                    "Each placement batch must advance only the persisted cursor and respect its block budget");
            helper.assertTrue(current.placementCursor() <= targetCursor,
                    "Placement must never exceed the block prefix unlocked by construction progress");
            processCalls++;
        }
        helper.assertTrue(current.placementCursor() == targetCursor,
                "Placement did not reach expected cursor " + targetCursor + "; actual="
                        + current.placementCursor() + ", calls=" + processCalls);
        return current;
    }

    private static void assertTemplatePrefix(
            final GameTestHelper helper,
            final ServerLevel level,
            final BuildingRecord building,
            final BlueprintTemplate template,
            final int expectedCursor
    ) {
        for (int index = 0; index < template.blocks().size(); index++) {
            final BlueprintTemplate.TemplateBlock entry = template.blocks().get(index);
            final BlockPos target = building.origin().offset(entry.position());
            if (index < expectedCursor) {
                final var expectedState = BuiltInRegistries.BLOCK.getOptional(entry.blockId())
                        .orElseThrow(() -> new IllegalStateException("Unknown template block " + entry.blockId()))
                        .defaultBlockState();
                helper.assertTrue(level.getBlockState(target).equals(expectedState),
                        "Expected template block " + entry.blockId() + " at " + target + " (index " + index + ")");
            } else {
                helper.assertTrue(level.getBlockState(target).isAir(),
                        "Template block after cursor must remain absent at " + target + " (index " + index + ")");
            }
        }
    }

    private static Fixture createUnreservedFixture(
            final GameTestHelper helper,
            final String factionName
    ) {
        final var level = helper.getLevel();
        final EmpiresWorldData data = new EmpiresWorldData();
        final CivilizationService service = new CivilizationService(new SavedDataCivilizationRepository(data));
        final FactionRecord faction = service.createFaction(
                factionName, ResourceLocation.fromNamespaceAndPath("empires", "bearfolk"), ControllerType.AI, level.getGameTime());
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
                factionName, ResourceLocation.fromNamespaceAndPath("empires", "bearfolk"), ControllerType.AI, level.getGameTime());
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
