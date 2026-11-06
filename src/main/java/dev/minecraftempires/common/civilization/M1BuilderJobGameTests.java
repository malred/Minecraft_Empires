package dev.minecraftempires.common.civilization;

import dev.minecraftempires.MinecraftEmpires;
import dev.minecraftempires.common.building.BlueprintCatalog;
import dev.minecraftempires.common.building.BlueprintDefinition;
import dev.minecraftempires.common.building.BuildingRecord;
import dev.minecraftempires.common.building.BuildingState;
import dev.minecraftempires.common.building.WorldSyncState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(MinecraftEmpires.MOD_ID)
@PrefixGameTestTemplate(false)
public final class M1BuilderJobGameTests {
    private M1BuilderJobGameTests() {
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m1_006",
            timeoutTicks = 20
    )
    public static void constructionStartCreatesOneRetrySafeBuilderJob(final GameTestHelper helper) {
        final Fixture fixture = createFixture(helper, "M1-006 Create", false);
        final BuildingRecord started = fixture.service().startConstruction(fixture.building().id());
        final BuilderJobRecord job = onlyJob(fixture, started.id());
        final BuildingRecord retried = fixture.service().startConstruction(started.id());

        helper.assertTrue(started.state() == BuildingState.UNDER_CONSTRUCTION
                        && job.state() == BuilderJobState.AVAILABLE
                        && job.buildingId().equals(started.id()),
                "Starting a construction project must create one AVAILABLE builder job for its building");
        helper.assertTrue(retried.id().equals(started.id())
                        && fixture.service().builderJobsForBuilding(started.id()).size() == 1,
                "Retrying construction start must not create a duplicate builder job");
        helper.succeed();
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m1_006",
            timeoutTicks = 20
    )
    public static void onlyAnActiveBuilderCanClaimAJob(final GameTestHelper helper) {
        final Fixture fixture = createFixture(helper, "M1-006 Claim", false);
        fixture.service().startConstruction(fixture.building().id());
        final BuilderJobRecord job = onlyJob(fixture, fixture.building().id());
        final CitizenRecord citizen = fixture.service().createCitizen(
                fixture.faction().id(), "Builder One", helper.getLevel().getGameTime());

        boolean unassignedRejected = false;
        try {
            fixture.service().claimBuilderJob(job.id(), citizen.id(), helper.getLevel().getGameTime());
        } catch (IllegalArgumentException expected) {
            unassignedRejected = expected.getMessage().contains("ACTIVE builder");
        }
        final CitizenRecord builder = fixture.service().assignBuilder(citizen.id());
        final BuilderJobRecord claimed = fixture.service().claimBuilderJob(
                job.id(), builder.id(), helper.getLevel().getGameTime());

        helper.assertTrue(unassignedRejected, "A citizen without the builder profession must not claim a builder job");
        helper.assertTrue(builder.professionId().equals(BuilderJobRecord.BUILDER_PROFESSION_ID)
                        && claimed.state() == BuilderJobState.CLAIMED
                        && builder.id().equals(claimed.assignedCitizenId()),
                "An ACTIVE builder of the owning faction must be able to claim the job");
        helper.succeed();
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m1_006",
            timeoutTicks = 20
    )
    public static void oneBuilderCannotClaimTwoActiveJobs(final GameTestHelper helper) {
        final Fixture fixture = createFixture(helper, "M1-006 Exclusive Worker", false);
        fixture.service().startConstruction(fixture.building().id());
        final BuilderJobRecord firstJob = onlyJob(fixture, fixture.building().id());
        final CitizenRecord citizen = fixture.service().assignBuilder(
                fixture.service().createCitizen(fixture.faction().id(), "Exclusive Builder",
                        helper.getLevel().getGameTime()).id());

        final ResourceLedger doubledCost = fixture.blueprint().materials().plus(fixture.blueprint().materials());
        fixture.service().setResources(fixture.faction().id(), doubledCost);
        final BlockPos secondOrigin = helper.absolutePos(new BlockPos(9, 1, 1));
        final SettlementRecord secondSettlement = fixture.service().createSettlement(
                fixture.faction().id(), helper.getLevel().dimension().location(), secondOrigin,
                helper.getLevel().getGameTime());
        final BuildingRecord secondPlanned = fixture.service().planBuilding(
                secondSettlement.id(), fixture.blueprint().id(), helper.getLevel().dimension().location(),
                secondOrigin, 0, helper.getLevel().getGameTime());
        final BuildingRecord secondReserved = fixture.service().reserveBuildingMaterials(secondPlanned.id());
        fixture.service().startConstruction(secondReserved.id());
        final BuilderJobRecord secondJob = onlyJob(fixture, secondReserved.id());

        fixture.service().claimBuilderJob(firstJob.id(), citizen.id(), helper.getLevel().getGameTime());
        boolean secondClaimRejected = false;
        try {
            fixture.service().claimBuilderJob(secondJob.id(), citizen.id(), helper.getLevel().getGameTime());
        } catch (IllegalStateException expected) {
            secondClaimRejected = expected.getMessage().contains("already has active builder job");
        }

        helper.assertTrue(secondClaimRejected,
                "An assigned builder must not be able to hold a second active job concurrently");
        helper.assertTrue(fixture.service().findBuilderJob(firstJob.id()).orElseThrow().state()
                        == BuilderJobState.CLAIMED
                        && fixture.service().findBuilderJob(secondJob.id()).orElseThrow().state()
                        == BuilderJobState.AVAILABLE,
                "Rejecting the second claim must leave both job records consistent");
        helper.succeed();
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m1_006",
            timeoutTicks = 20
    )
    public static void onlyTheAssignedBuilderCanStartAClaimedJob(final GameTestHelper helper) {
        final Fixture fixture = createFixture(helper, "M1-006 Start Job", false);
        fixture.service().startConstruction(fixture.building().id());
        final BuilderJobRecord job = onlyJob(fixture, fixture.building().id());
        final CitizenRecord builder = fixture.service().assignBuilder(
                fixture.service().createCitizen(fixture.faction().id(), "Assigned Builder",
                        helper.getLevel().getGameTime()).id());
        fixture.service().claimBuilderJob(job.id(), builder.id(), helper.getLevel().getGameTime());

        boolean wrongWorkerRejected = false;
        try {
            fixture.service().startBuilderJob(job.id(), UUID.randomUUID(), helper.getLevel().getGameTime());
        } catch (IllegalStateException expected) {
            wrongWorkerRejected = expected.getMessage().contains("not the assigned worker");
        }
        final BuilderJobRecord working = fixture.service().startBuilderJob(
                job.id(), builder.id(), helper.getLevel().getGameTime());
        final BuilderJobRecord retry = fixture.service().startBuilderJob(
                job.id(), builder.id(), helper.getLevel().getGameTime());

        helper.assertTrue(wrongWorkerRejected, "An unrelated citizen must not start another builder's job");
        helper.assertTrue(working.state() == BuilderJobState.WORKING
                        && retry.state() == BuilderJobState.WORKING,
                "The assigned worker must move CLAIMED -> WORKING and repeated start must be safe");
        helper.succeed();
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m1_006",
            timeoutTicks = 20
    )
    public static void releasingAJobClearsTheWorkerAndIncrementsRetryCount(final GameTestHelper helper) {
        final Fixture fixture = createFixture(helper, "M1-006 Release", false);
        fixture.service().startConstruction(fixture.building().id());
        final BuilderJobRecord job = onlyJob(fixture, fixture.building().id());
        final CitizenRecord first = fixture.service().assignBuilder(
                fixture.service().createCitizen(fixture.faction().id(), "Builder A",
                        helper.getLevel().getGameTime()).id());
        final CitizenRecord second = fixture.service().assignBuilder(
                fixture.service().createCitizen(fixture.faction().id(), "Builder B",
                        helper.getLevel().getGameTime()).id());
        fixture.service().claimBuilderJob(job.id(), first.id(), helper.getLevel().getGameTime());
        fixture.service().startBuilderJob(job.id(), first.id(), helper.getLevel().getGameTime());

        final BuilderJobRecord released = fixture.service().releaseBuilderJob(
                job.id(), first.id(), helper.getLevel().getGameTime());
        final BuilderJobRecord reassigned = fixture.service().claimBuilderJob(
                job.id(), second.id(), helper.getLevel().getGameTime());

        helper.assertTrue(released.state() == BuilderJobState.AVAILABLE
                        && released.assignedCitizenId() == null
                        && released.retryCount() == 1,
                "Releasing a claimed or working job must clear its worker and record one retry");
        helper.assertTrue(reassigned.assignedCitizenId().equals(second.id())
                        && reassigned.state() == BuilderJobState.CLAIMED,
                "A released job must be available for another eligible builder");
        helper.succeed();
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m1_006",
            timeoutTicks = 20
    )
    public static void blockedJobsCanBeRetriedWithoutLosingTheirHistory(final GameTestHelper helper) {
        final Fixture fixture = createFixture(helper, "M1-006 Block Retry", false);
        fixture.service().startConstruction(fixture.building().id());
        final BuilderJobRecord job = onlyJob(fixture, fixture.building().id());
        final CitizenRecord builder = fixture.service().assignBuilder(
                fixture.service().createCitizen(fixture.faction().id(), "Blocked Builder",
                        helper.getLevel().getGameTime()).id());
        fixture.service().claimBuilderJob(job.id(), builder.id(), helper.getLevel().getGameTime());
        fixture.service().startBuilderJob(job.id(), builder.id(), helper.getLevel().getGameTime());

        final BuilderJobRecord blocked = fixture.service().blockBuilderJob(
                job.id(), builder.id(), helper.getLevel().getGameTime());
        final BuilderJobRecord retried = fixture.service().retryBuilderJob(
                job.id(), helper.getLevel().getGameTime());

        helper.assertTrue(blocked.state() == BuilderJobState.BLOCKED
                        && blocked.assignedCitizenId() == null
                        && blocked.retryCount() == 1,
                "Blocking a job must release its worker and increment retry history");
        helper.assertTrue(retried.state() == BuilderJobState.AVAILABLE
                        && retried.retryCount() == blocked.retryCount(),
                "Retrying a blocked job must return it to AVAILABLE without erasing retry history");
        helper.succeed();
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m1_006",
            timeoutTicks = 20
    )
    public static void builderJobCompletesOnlyWhenItsBuildingBecomesActive(final GameTestHelper helper) {
        final Fixture fixture = createFixture(helper, "M1-006 Complete", true);
        fixture.service().startConstruction(fixture.building().id());
        final BuilderJobRecord job = onlyJob(fixture, fixture.building().id());

        boolean earlyCompletionRejected = false;
        try {
            fixture.service().completeBuilderJob(job.id(), helper.getLevel().getGameTime());
        } catch (IllegalStateException expected) {
            earlyCompletionRejected = expected.getMessage().contains("before its building is ACTIVE");
        }
        final BuildingRecord completed = fixture.service().applyConstructionWork(
                fixture.building().id(), 0L, Long.MAX_VALUE);
        final BuilderJobRecord completedJob = fixture.service().findBuilderJob(job.id()).orElseThrow();

        helper.assertTrue(earlyCompletionRejected,
                "A builder job must not complete while its construction project is still underway");
        helper.assertTrue(completed.state() == BuildingState.ACTIVE
                        && completedJob.state() == BuilderJobState.COMPLETED,
                "Activating the building must atomically complete the associated builder job");
        helper.succeed();
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m1_006",
            timeoutTicks = 20
    )
    public static void schemaThreeMigrationCreatesJobsForInProgressBuildings(final GameTestHelper helper) {
        final Fixture fixture = createFixture(helper, "M1-006 Migration", false);
        fixture.service().startConstruction(fixture.building().id());
        final UUID worldId = fixture.data().worldId();
        final CompoundTag legacy = fixture.data().save(new CompoundTag());
        legacy.putInt("schema_version", 3);
        legacy.remove("builder_jobs");

        final EmpiresWorldData migrated = EmpiresWorldData.load(legacy);
        final BuilderJobRecord migratedJob = migrated.builderJobsForBuilding(fixture.building().id())
                .stream().findFirst().orElse(null);

        helper.assertTrue(!migrated.isReadOnly()
                        && migrated.worldId().equals(worldId)
                        && migrated.schemaVersion() == EmpiresWorldData.CURRENT_SCHEMA_VERSION,
                "Schema 3 saves must migrate to the current schema without changing world identity");
        helper.assertTrue(migratedJob != null && migratedJob.state() == BuilderJobState.AVAILABLE
                        && migrated.builderJobCount() == 1,
                "An existing UNDER_CONSTRUCTION project must receive one AVAILABLE builder job during migration");
        helper.assertTrue(migrated.factionCount() == 1 && migrated.settlementCount() == 1
                        && migrated.citizenCount() == 0 && migrated.buildingCount() == 1,
                "Schema migration must preserve existing faction, settlement and building records");
        helper.succeed();
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m1_006",
            timeoutTicks = 20
    )
    public static void claimedBuilderJobSurvivesSavedDataRoundTripAndValidates(final GameTestHelper helper) {
        final Fixture fixture = createFixture(helper, "M1-006 Round Trip", false);
        fixture.service().startConstruction(fixture.building().id());
        final BuilderJobRecord job = onlyJob(fixture, fixture.building().id());
        final CitizenRecord builder = fixture.service().assignBuilder(
                fixture.service().createCitizen(fixture.faction().id(), "Persistent Builder",
                        helper.getLevel().getGameTime()).id());
        fixture.service().claimBuilderJob(job.id(), builder.id(), helper.getLevel().getGameTime());
        fixture.service().startBuilderJob(job.id(), builder.id(), helper.getLevel().getGameTime());

        final EmpiresWorldData loaded = EmpiresWorldData.load(fixture.data().save(new CompoundTag()));
        final BuilderJobRecord restored = loaded.findBuilderJob(job.id()).orElseThrow();
        final WorldDataValidator.Report report = WorldDataValidator.validate(loaded);

        helper.assertTrue(restored.state() == BuilderJobState.WORKING
                        && builder.id().equals(restored.assignedCitizenId())
                        && restored.buildingId().equals(fixture.building().id()),
                "Builder job state and assignment must persist through a SavedData round trip");
        helper.assertTrue(report.passed() && report.errorCount() == 0L,
                "A valid persisted builder job must pass world data validation");
        helper.succeed();
    }

    private static BuilderJobRecord onlyJob(final Fixture fixture, final UUID buildingId) {
        final var jobs = fixture.service().builderJobsForBuilding(buildingId);
        if (jobs.size() != 1) {
            throw new IllegalStateException("Expected exactly one builder job for " + buildingId + ", found " + jobs.size());
        }
        return jobs.get(0);
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
            final int volume = blueprint.sizeX() * blueprint.sizeY() * blueprint.sizeZ();
            planned = planned.withPlacementCursor(volume, WorldSyncState.SYNCED);
            data.putBuilding(planned);
        }
        final BuildingRecord reserved = service.reserveBuildingMaterials(planned.id());
        return new Fixture(data, service, faction, settlement, blueprint, reserved);
    }

    private record Fixture(
            EmpiresWorldData data,
            CivilizationService service,
            FactionRecord faction,
            SettlementRecord settlement,
            BlueprintDefinition blueprint,
            BuildingRecord building
    ) {
    }
}
