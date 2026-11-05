package dev.minecraftempires.common.civilization;

import dev.minecraftempires.MinecraftEmpires;
import dev.minecraftempires.common.building.BlueprintCatalog;
import dev.minecraftempires.common.building.BlueprintDefinition;
import dev.minecraftempires.common.building.BlueprintTemplate;
import dev.minecraftempires.common.building.BuildingPlacementService;
import dev.minecraftempires.common.building.BuildingRecord;
import dev.minecraftempires.common.building.WorldSyncState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * M0-012 integration coverage for persistent civilization records and read-only validation.
 *
 * <p>Data tests use isolated in-memory SavedData. The physical placement test mutates only its
 * dedicated GameTest structure in the separate run-gametest world, never the player's normal save.</p>
 */
@GameTestHolder(MinecraftEmpires.MOD_ID)
@PrefixGameTestTemplate(false)
public final class M0IntegrationGameTests {
    private static final ResourceLocation TEST_SPECIES = new ResourceLocation("empires", "bearfolk");
    private static final ResourceLocation OVERWORLD = new ResourceLocation("minecraft", "overworld");

    private M0IntegrationGameTests() {
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m0_012",
            timeoutTicks = 20
    )
    public static void savedDataRoundTripPreservesWorldAndBuilding(final GameTestHelper helper) {
        final Fixture fixture = createFixture(1, 1, false);
        final BuildingRecord originalBuilding = fixture.data().buildings().get(0);
        final EmpiresWorldData loaded = EmpiresWorldData.load(fixture.data().save(new CompoundTag()));
        final WorldDataValidator.Report report = WorldDataValidator.validate(loaded);

        helper.assertTrue(!loaded.isReadOnly(), "Round-tripped SavedData unexpectedly became read-only");
        helper.assertTrue(loaded.worldId().equals(fixture.data().worldId()), "World UUID changed during SavedData round trip");
        helper.assertTrue(loaded.factionCount() == 1, "Faction count changed during SavedData round trip");
        helper.assertTrue(loaded.settlementCount() == 1, "Settlement count changed during SavedData round trip");
        helper.assertTrue(loaded.citizenCount() == 1, "Citizen count changed during SavedData round trip");
        helper.assertTrue(loaded.buildingCount() == 1, "Building count changed during SavedData round trip");
        helper.assertTrue(loaded.findBuilding(originalBuilding.id()).isPresent(), "Building UUID was not preserved");
        helper.assertTrue(loaded.findBuilding(originalBuilding.id()).orElseThrow().placementCursor() == 25,
                "Building placement cursor was not preserved");
        helper.assertTrue(report.passed() && report.warningCount() == 0,
                "Round-tripped world data did not validate cleanly: errors=" + report.errorCount()
                        + ", warnings=" + report.warningCount());
        helper.succeed();
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m0_012",
            timeoutTicks = 20
    )
    public static void pendingPlacementCursorSurvivesSavedDataRoundTrip(final GameTestHelper helper) {
        final Fixture fixture = createFixture(1, 1, false);
        final BuildingRecord original = fixture.data().buildings().get(0);
        final BuildingRecord pending = original.withPlacementCursor(8, WorldSyncState.PENDING);
        fixture.data().putBuilding(pending);

        final EmpiresWorldData loaded = EmpiresWorldData.load(fixture.data().save(new CompoundTag()));
        final BuildingRecord restored = loaded.findBuilding(original.id()).orElseThrow();
        final WorldDataValidator.Report report = WorldDataValidator.validate(loaded);

        helper.assertTrue(restored.placementCursor() == 8,
                "Pending placement cursor was not preserved through SavedData serialization");
        helper.assertTrue(restored.worldSyncState() == WorldSyncState.PENDING,
                "Pending world sync state was not preserved through SavedData serialization");
        helper.assertTrue(restored.state() == original.state(),
                "Logical building lifecycle state changed during placement persistence");
        helper.assertTrue(report.passed() && report.warningCount() == 0,
                "Pending placement data failed consistency validation");
        helper.succeed();
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m0_012",
            timeoutTicks = 20
    )
    public static void validatorReportsPopulationCacheMismatch(final GameTestHelper helper) {
        final Fixture fixture = createFixture(0, 0, false);
        final WorldDataValidator.Report report = WorldDataValidator.validate(fixture.data());

        helper.assertTrue(report.passed(), "Population cache mismatch should be a warning, not a structural error");
        helper.assertTrue(report.errorCount() == 0, "Unexpected validation errors were reported");
        helper.assertTrue(report.warningCount() == 1,
                "Expected exactly one population cache warning, got " + report.warningCount());
        helper.succeed();
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m0_012",
            timeoutTicks = 20
    )
    public static void validatorDetectsOverlappingBuildings(final GameTestHelper helper) {
        final Fixture fixture = createFixture(1, 2, true);
        final WorldDataValidator.Report report = WorldDataValidator.validate(fixture.data());

        helper.assertTrue(!report.passed(), "Overlapping buildings were not rejected by validation");
        helper.assertTrue(report.errorCount() == 1,
                "Expected one overlap error, got " + report.errorCount());
        helper.succeed();
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m0_012",
            timeoutTicks = 20
    )
    public static void futureSchemaIsPreservedReadOnly(final GameTestHelper helper) {
        final CompoundTag futureTag = new CompoundTag();
        final java.util.UUID worldId = java.util.UUID.randomUUID();
        futureTag.putInt("schema_version", EmpiresWorldData.CURRENT_SCHEMA_VERSION + 1);
        futureTag.putUUID("world_id", worldId);
        futureTag.putString("forward_compatibility_sentinel", "preserve-me");

        final EmpiresWorldData loaded = EmpiresWorldData.load(futureTag);
        final CompoundTag savedAgain = loaded.save(new CompoundTag());
        final WorldDataValidator.Report report = WorldDataValidator.validate(loaded);

        helper.assertTrue(loaded.isReadOnly(), "Future schema data must be read-only");
        helper.assertTrue(loaded.worldId().equals(worldId), "Future-schema world UUID was not preserved");
        helper.assertTrue(savedAgain.getInt("schema_version") == EmpiresWorldData.CURRENT_SCHEMA_VERSION + 1,
                "Future schema version was overwritten");
        helper.assertTrue("preserve-me".equals(savedAgain.getString("forward_compatibility_sentinel")),
                "Unknown future data was not preserved");
        helper.assertTrue(!report.passed(), "Validator must report read-only world data as unavailable/error");
        helper.succeed();
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m0_012",
            timeoutTicks = 20
    )
    public static void officialWarehouseTemplateLoadsAndMatchesChecksum(final GameTestHelper helper) {
        final BlueprintDefinition blueprint = BlueprintCatalog.find(BlueprintCatalog.WAREHOUSE_PROTOTYPE_ID)
                .orElseThrow();
        final BlueprintTemplate template = BlueprintTemplate.load(
                helper.getLevel().getServer().getResourceManager(), blueprint);

        helper.assertTrue(template.sizeX() == 3 && template.sizeY() == 3 && template.sizeZ() == 3,
                "Warehouse template dimensions should remain 3x3x3");
        helper.assertTrue(template.blocks().size() == 25,
                "Warehouse template should contain 25 placed block entries");
        helper.assertTrue(template.checksum().equals(blueprint.templateContentChecksum()),
                "Warehouse JSON content checksum does not match the blueprint catalog");
        helper.succeed();
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m0_012",
            timeoutTicks = 40
    )
    public static void placementAdapterPlacesTemplateAndRejectsReplay(final GameTestHelper helper) {
        final var level = helper.getLevel();
        final BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }

        final EmpiresWorldData testData = new EmpiresWorldData();
        final CivilizationService service = new CivilizationService(
                new SavedDataCivilizationRepository(testData));
        final FactionRecord faction = service.createFaction(
                "M0 Placement Test", TEST_SPECIES, ControllerType.AI, level.getGameTime());
        final SettlementRecord settlement = service.createSettlement(
                faction.id(), level.dimension().location(), origin, level.getGameTime());
        final BuildingRecord planned = service.planBuilding(
                settlement.id(), BlueprintCatalog.WAREHOUSE_PROTOTYPE_ID,
                level.dimension().location(), origin, 0, level.getGameTime());

        BuildingPlacementService.beginPlacement(level, service, planned.id());
        BuildingRecord current = service.findBuilding(planned.id()).orElseThrow();
        helper.assertTrue(current.worldSyncState() == WorldSyncState.PENDING,
                "Placement should enter PENDING before any block is written");

        int processCalls = 0;
        while (current.placementCursor() < 25 && processCalls < 32) {
            BuildingPlacementService.processPending(level.getServer(), testData, service);
            current = service.findBuilding(planned.id()).orElseThrow();
            processCalls++;
        }

        helper.assertTrue(current.placementCursor() == 25,
                "Placement did not complete 25 template entries; cursor=" + current.placementCursor());
        helper.assertTrue(current.worldSyncState() == WorldSyncState.SYNCED,
                "Placement did not transition to SYNCED");
        helper.assertTrue(current.state() == dev.minecraftempires.common.building.BuildingState.PLANNED,
                "M0-010 placement must not mark the logical building as constructed/ACTIVE");
        helper.assertTrue(level.getBlockState(origin).is(Blocks.COBBLESTONE),
                "Expected cobblestone at the warehouse floor origin");
        helper.assertTrue(level.getBlockState(origin.offset(0, 1, 0)).is(Blocks.OAK_PLANKS),
                "Expected an oak-plank wall block");
        helper.assertTrue(level.getBlockState(origin.offset(1, 1, 0)).isAir(),
                "Expected the centered entrance to remain open");

        boolean replayRejected = false;
        try {
            BuildingPlacementService.beginPlacement(level, service, planned.id());
        } catch (IllegalStateException expected) {
            replayRejected = true;
        }
        helper.assertTrue(replayRejected, "A completed placement must reject a replay attempt");
        helper.succeed();
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m0_012",
            timeoutTicks = 20
    )
    public static void validatorDetectsDuplicateCitizenEntityLinks(final GameTestHelper helper) {
        final Fixture fixture = createFixture(2, 0, false);
        final java.util.UUID sharedEntityId = java.util.UUID.randomUUID();
        final CitizenRecord first = fixture.data().citizens().get(0);
        fixture.data().putCitizen(first.withEntityUuid(sharedEntityId));
        fixture.data().putCitizen(new CitizenRecord(
                java.util.UUID.randomUUID(),
                first.ownerFactionId(),
                first.homeSettlementId(),
                "M0 Duplicate Citizen",
                first.speciesId(),
                first.professionId(),
                CitizenStatus.ACTIVE,
                sharedEntityId,
                0L,
                CitizenRecord.CURRENT_SCHEMA_VERSION
        ));

        final WorldDataValidator.Report report = WorldDataValidator.validate(fixture.data());
        helper.assertTrue(!report.passed(), "Duplicate entity UUID links were not rejected");
        helper.assertTrue(report.errorCount() == 1,
                "Expected one duplicate entity UUID error, got " + report.errorCount());
        helper.succeed();
    }

    private static Fixture createFixture(
            final int populationCache,
            final int buildingCount,
            final boolean overlapBuildings
    ) {
        final EmpiresWorldData data = new EmpiresWorldData();
        final FactionRecord faction = FactionRecord.create("M0 Test Faction", TEST_SPECIES,
                ControllerType.AI, 0L).withPopulationCountCache(populationCache);
        data.putFaction(faction);

        final SettlementRecord settlement = SettlementRecord.create(
                faction.id(), OVERWORLD, BlockPos.ZERO, 0L);
        data.putSettlement(settlement);

        final CitizenRecord citizen = CitizenRecord.create(
                faction.id(), settlement.id(), "M0 Test Citizen", TEST_SPECIES, 0L);
        data.putCitizen(citizen);

        final var blueprint = BlueprintCatalog.find(BlueprintCatalog.WAREHOUSE_PROTOTYPE_ID).orElseThrow();
        for (int index = 0; index < buildingCount; index++) {
            final int x = overlapBuildings ? 10 : 10 + (index * 5);
            BuildingRecord building = BuildingRecord.planned(
                    blueprint, faction.id(), settlement.id(), OVERWORLD, new BlockPos(x, 64, 10), 0, 0L);
            if (index == 0 && buildingCount == 1) {
                building = building.withPlacementCursor(25, WorldSyncState.SYNCED);
            }
            data.putBuilding(building);
        }
        return new Fixture(data);
    }

    private record Fixture(EmpiresWorldData data) {
    }
}
