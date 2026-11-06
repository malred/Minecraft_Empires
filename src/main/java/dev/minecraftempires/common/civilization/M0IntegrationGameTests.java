package dev.minecraftempires.common.civilization;

import dev.minecraftempires.MinecraftEmpires;
import dev.minecraftempires.registry.ModBlocks;
import dev.minecraftempires.registry.ModItems;
import dev.minecraftempires.common.building.BlueprintCatalog;
import dev.minecraftempires.common.building.BlueprintDefinition;
import dev.minecraftempires.common.building.BlueprintTemplate;
import dev.minecraftempires.common.building.BuildingCategory;
import dev.minecraftempires.common.building.BuildingPlacementService;
import dev.minecraftempires.common.building.BuildingRecord;
import dev.minecraftempires.common.building.WorldSyncState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
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

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m1_001",
            timeoutTicks = 20
    )
    public static void headquartersContentIsRegisteredAndTemplateValid(final GameTestHelper helper) {
        final BlueprintDefinition blueprint = BlueprintCatalog.find(BlueprintCatalog.HEADQUARTERS_HALL_ID)
                .orElseThrow();
        final BlueprintTemplate template = BlueprintTemplate.load(
                helper.getLevel().getServer().getResourceManager(), blueprint);

        helper.assertTrue(blueprint.category() == BuildingCategory.HEADQUARTERS,
                "Headquarters blueprint must use the HEADQUARTERS category");
        helper.assertTrue(blueprint.sizeX() == 7 && blueprint.sizeY() == 8 && blueprint.sizeZ() == 7,
                "Headquarters hall dimensions should remain 7x8x7");
        helper.assertTrue(template.blocks().size() == 204,
                "Headquarters hall template should contain 204 block entries");
        helper.assertTrue(template.checksum().equals(blueprint.templateContentChecksum()),
                "Headquarters hall template checksum does not match its catalog definition");
        helper.assertTrue(BuiltInRegistries.BLOCK.getKey(ModBlocks.HEADQUARTERS_CORE.get())
                        .equals(new ResourceLocation("empires", "headquarters_core")),
                "Headquarters core block was not registered under the expected ID");
        helper.assertTrue(BuiltInRegistries.ITEM.getKey(ModItems.HEADQUARTERS_BLUEPRINT.get())
                        .equals(new ResourceLocation("empires", "headquarters_blueprint")),
                "Headquarters blueprint item was not registered under the expected ID");
        helper.assertTrue(template.blocks().stream().anyMatch(block ->
                        block.blockId().equals(new ResourceLocation("empires", "headquarters_core"))),
                "Headquarters template must include its registered core marker");
        helper.succeed();
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m1_002",
            timeoutTicks = 20
    )
    public static void factionCannotPlanDuplicateHeadquarters(final GameTestHelper helper) {
        final EmpiresWorldData data = new EmpiresWorldData();
        final CivilizationService service = new CivilizationService(new SavedDataCivilizationRepository(data));
        final FactionRecord faction = service.createFaction(
                "M1 HQ Unique", TEST_SPECIES, ControllerType.AI, 0L);
        final SettlementRecord settlement = service.createSettlement(
                faction.id(), OVERWORLD, BlockPos.ZERO, 0L);
        service.planBuilding(settlement.id(), BlueprintCatalog.HEADQUARTERS_HALL_ID,
                OVERWORLD, new BlockPos(10, 64, 10), 0, 0L);

        boolean duplicateRejected = false;
        try {
            service.planBuilding(settlement.id(), BlueprintCatalog.HEADQUARTERS_HALL_ID,
                    OVERWORLD, new BlockPos(30, 64, 30), 0, 1L);
        } catch (IllegalArgumentException expected) {
            duplicateRejected = expected.getMessage().contains("already has a non-demolished headquarters");
        }
        helper.assertTrue(duplicateRejected,
                "A faction must not be allowed to plan a second non-demolished headquarters");
        helper.assertTrue(service.buildings().size() == 1,
                "Rejected duplicate headquarters planning must not persist a second record");

        final BuildingRecord first = service.buildings().get(0);
        data.putBuilding(first.withState(dev.minecraftempires.common.building.BuildingState.DEMOLISHED,
                first.worldSyncState()));
        service.planBuilding(settlement.id(), BlueprintCatalog.HEADQUARTERS_HALL_ID,
                OVERWORLD, new BlockPos(30, 64, 30), 0, 2L);
        helper.assertTrue(service.buildings().size() == 2
                        && service.buildings().stream().filter(building ->
                        building.state() != dev.minecraftempires.common.building.BuildingState.DEMOLISHED).count() == 1,
                "An explicitly demolished headquarters should release the faction's unique slot");
        helper.succeed();
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m1_002",
            timeoutTicks = 20
    )
    public static void differentFactionsCanEachPlanHeadquarters(final GameTestHelper helper) {
        final EmpiresWorldData data = new EmpiresWorldData();
        final CivilizationService service = new CivilizationService(new SavedDataCivilizationRepository(data));
        final FactionRecord firstFaction = service.createFaction(
                "M1 HQ Faction A", TEST_SPECIES, ControllerType.AI, 0L);
        final FactionRecord secondFaction = service.createFaction(
                "M1 HQ Faction B", TEST_SPECIES, ControllerType.AI, 0L);
        final SettlementRecord firstSettlement = service.createSettlement(
                firstFaction.id(), OVERWORLD, BlockPos.ZERO, 0L);
        final SettlementRecord secondSettlement = service.createSettlement(
                secondFaction.id(), OVERWORLD, BlockPos.ZERO, 0L);

        service.planBuilding(firstSettlement.id(), BlueprintCatalog.HEADQUARTERS_HALL_ID,
                OVERWORLD, new BlockPos(10, 64, 10), 0, 0L);
        service.planBuilding(secondSettlement.id(), BlueprintCatalog.HEADQUARTERS_HALL_ID,
                OVERWORLD, new BlockPos(30, 64, 30), 0, 0L);

        helper.assertTrue(service.buildings().size() == 2,
                "Different factions should be allowed to plan their own headquarters");
        helper.assertTrue(service.buildings().stream()
                        .map(BuildingRecord::ownerFactionId).distinct().count() == 2,
                "Each headquarters should belong to a different faction");
        helper.succeed();
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m1_002",
            timeoutTicks = 20
    )
    public static void capitalSettlementCannotBeSilentlyReassigned(final GameTestHelper helper) {
        final EmpiresWorldData data = new EmpiresWorldData();
        final CivilizationService service = new CivilizationService(new SavedDataCivilizationRepository(data));
        final FactionRecord faction = service.createFaction(
                "M1 Capital Lock", TEST_SPECIES, ControllerType.AI, 0L);
        final SettlementRecord first = service.createSettlement(
                faction.id(), OVERWORLD, BlockPos.ZERO, 0L);
        final SettlementRecord second = service.createSettlement(
                faction.id(), OVERWORLD, new BlockPos(100, 64, 100), 0L);
        service.setCapitalSettlement(faction.id(), first.id());
        service.setCapitalSettlement(faction.id(), first.id());

        boolean reassignmentRejected = false;
        try {
            service.setCapitalSettlement(faction.id(), second.id());
        } catch (IllegalStateException expected) {
            reassignmentRejected = true;
        }
        helper.assertTrue(reassignmentRejected,
                "Capital settlement must not be silently reassigned to another settlement");
        helper.assertTrue(service.findFaction(faction.id()).orElseThrow()
                        .capitalSettlementId().equals(first.id()),
                "Rejected capital reassignment must preserve the original capital");
        helper.succeed();
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m1_002",
            timeoutTicks = 20
    )
    public static void capitalMustMatchExistingHeadquartersSettlement(final GameTestHelper helper) {
        final EmpiresWorldData data = new EmpiresWorldData();
        final CivilizationService service = new CivilizationService(new SavedDataCivilizationRepository(data));
        final FactionRecord faction = service.createFaction(
                "M1 HQ Capital Match", TEST_SPECIES, ControllerType.AI, 0L);
        final SettlementRecord headquartersSettlement = service.createSettlement(
                faction.id(), OVERWORLD, BlockPos.ZERO, 0L);
        final SettlementRecord otherSettlement = service.createSettlement(
                faction.id(), OVERWORLD, new BlockPos(100, 64, 100), 0L);
        service.planBuilding(headquartersSettlement.id(), BlueprintCatalog.HEADQUARTERS_HALL_ID,
                OVERWORLD, new BlockPos(10, 64, 10), 0, 0L);

        boolean mismatchRejected = false;
        try {
            service.setCapitalSettlement(faction.id(), otherSettlement.id());
        } catch (IllegalStateException expected) {
            mismatchRejected = true;
        }
        helper.assertTrue(mismatchRejected,
                "Capital settlement must match the settlement holding the non-demolished headquarters");
        helper.assertTrue(service.findFaction(faction.id()).orElseThrow().capitalSettlementId() == null,
                "Rejected capital binding must leave the faction's capital unchanged");
        helper.succeed();
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m1_002",
            timeoutTicks = 20
    )
    public static void validatorDetectsDuplicateFactionHeadquarters(final GameTestHelper helper) {
        final EmpiresWorldData data = new EmpiresWorldData();
        final CivilizationService service = new CivilizationService(new SavedDataCivilizationRepository(data));
        final FactionRecord faction = service.createFaction(
                "M1 HQ Validator", TEST_SPECIES, ControllerType.AI, 0L);
        final SettlementRecord settlement = service.createSettlement(
                faction.id(), OVERWORLD, BlockPos.ZERO, 0L);
        final BlueprintDefinition headquarters = BlueprintCatalog.find(
                BlueprintCatalog.HEADQUARTERS_HALL_ID).orElseThrow();
        final BuildingRecord first = BuildingRecord.planned(headquarters, faction.id(), settlement.id(),
                OVERWORLD, new BlockPos(10, 64, 10), 0, 0L);
        final BuildingRecord second = BuildingRecord.planned(headquarters, faction.id(), settlement.id(),
                OVERWORLD, new BlockPos(30, 64, 30), 0, 0L);
        data.putBuilding(first);
        data.putBuilding(second);

        final WorldDataValidator.Report report = WorldDataValidator.validate(data);
        helper.assertTrue(!report.passed(),
                "World validator must reject multiple non-demolished headquarters for one faction");
        helper.assertTrue(report.errorCount() == 1,
                "Expected one duplicate headquarters error, got " + report.errorCount());
        helper.succeed();
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m1_003",
            timeoutTicks = 20
    )
    public static void placementPrecheckRejectsUnsupportedGroundWithoutRecord(final GameTestHelper helper) {
        final PlacementFixture fixture = createPlacementFixture(helper, "M1-003 Ground");
        final BlockPos origin = fixture.origin();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                fixture.level().setBlock(origin.offset(x, -1, z), Blocks.AIR.defaultBlockState(), 3);
            }
        }

        boolean rejected = false;
        try {
            BuildingPlacementService.planBuilding(fixture.level(), fixture.service(), fixture.faction().id(),
                    fixture.settlement().id(), BlueprintCatalog.WAREHOUSE_PROTOTYPE_ID,
                    origin, 0, fixture.level().getGameTime());
        } catch (IllegalArgumentException expected) {
            rejected = expected.getMessage().contains("Solid ground is required");
        }
        helper.assertTrue(rejected, "Precheck must reject an unsupported building footprint");
        helper.assertTrue(fixture.service().buildings().isEmpty(),
                "Unsupported terrain must not create a BuildingRecord");
        helper.assertTrue(fixture.level().getBlockState(origin).isAir(),
                "Failed terrain precheck must not modify target blocks");
        helper.succeed();
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m1_003_border",
            timeoutTicks = 20
    )
    public static void placementPrecheckRejectsFootprintOutsideWorldBorder(final GameTestHelper helper) {
        final PlacementFixture fixture = createPlacementFixture(helper, "M1-003 Border");
        setWarehouseGround(fixture);
        final var border = fixture.level().getWorldBorder();
        final double oldCenterX = border.getCenterX();
        final double oldCenterZ = border.getCenterZ();
        final double oldSize = border.getSize();

        boolean rejected = false;
        try {
            border.setCenter(fixture.origin().getX() + 0.5D, fixture.origin().getZ() + 0.5D);
            border.setSize(2.0D);
            BuildingPlacementService.planBuilding(fixture.level(), fixture.service(), fixture.faction().id(),
                    fixture.settlement().id(), BlueprintCatalog.WAREHOUSE_PROTOTYPE_ID,
                    fixture.origin(), 0, fixture.level().getGameTime());
        } catch (IllegalArgumentException expected) {
            rejected = expected.getMessage().contains("world border");
        } finally {
            border.setSize(oldSize);
            border.setCenter(oldCenterX, oldCenterZ);
        }

        helper.assertTrue(rejected,
                "Precheck must reject a footprint crossing the world border, including empty template cells");
        helper.assertTrue(fixture.service().buildings().isEmpty(),
                "World-border rejection must not create a BuildingRecord");
        helper.succeed();
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m1_003",
            timeoutTicks = 20
    )
    public static void placementPrecheckRejectsVolumeOutsideBuildHeight(final GameTestHelper helper) {
        final PlacementFixture fixture = createPlacementFixture(helper, "M1-003 Height");
        final BlockPos origin = new BlockPos(fixture.origin().getX(),
                fixture.level().getMinBuildHeight() + fixture.level().getHeight() - 2,
                fixture.origin().getZ());

        boolean rejected = false;
        try {
            BuildingPlacementService.planBuilding(fixture.level(), fixture.service(), fixture.faction().id(),
                    fixture.settlement().id(), BlueprintCatalog.WAREHOUSE_PROTOTYPE_ID,
                    origin, 0, fixture.level().getGameTime());
        } catch (IllegalArgumentException expected) {
            rejected = expected.getMessage().contains("exceeds build height");
        }
        helper.assertTrue(rejected, "Precheck must reject a blueprint volume extending above build height");
        helper.assertTrue(fixture.service().buildings().isEmpty(),
                "Build-height rejection must not create a BuildingRecord");
        helper.succeed();
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m1_003",
            timeoutTicks = 20
    )
    public static void placementPrecheckRejectsProtectedBlocksWithoutOverwriting(final GameTestHelper helper) {
        final PlacementFixture fixture = createPlacementFixture(helper, "M1-003 Protected");
        setWarehouseGround(fixture);
        fixture.level().setBlock(fixture.origin(), Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);

        boolean rejected = false;
        try {
            BuildingPlacementService.planBuilding(fixture.level(), fixture.service(), fixture.faction().id(),
                    fixture.settlement().id(), BlueprintCatalog.WAREHOUSE_PROTOTYPE_ID,
                    fixture.origin(), 0, fixture.level().getGameTime());
        } catch (IllegalArgumentException expected) {
            rejected = expected.getMessage().contains("non-replaceable block");
        }
        helper.assertTrue(rejected, "Precheck must reject a protected/non-replaceable target block");
        helper.assertTrue(fixture.service().buildings().isEmpty(),
                "Protected-block rejection must not create a BuildingRecord");
        helper.assertTrue(fixture.level().getBlockState(fixture.origin()).is(Blocks.DIAMOND_BLOCK),
                "Precheck must never overwrite the protected block");
        helper.succeed();
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m1_003",
            timeoutTicks = 20
    )
    public static void placementPrecheckRejectsMismatchedFactionOwnership(final GameTestHelper helper) {
        final PlacementFixture fixture = createPlacementFixture(helper, "M1-003 Owner");
        final FactionRecord otherFaction = fixture.service().createFaction(
                "M1-003 Other Owner", TEST_SPECIES, ControllerType.AI, fixture.level().getGameTime());
        setWarehouseGround(fixture);

        boolean rejected = false;
        try {
            BuildingPlacementService.planBuilding(fixture.level(), fixture.service(), otherFaction.id(),
                    fixture.settlement().id(), BlueprintCatalog.WAREHOUSE_PROTOTYPE_ID,
                    fixture.origin(), 0, fixture.level().getGameTime());
        } catch (IllegalArgumentException expected) {
            rejected = expected.getMessage().contains("does not belong to faction");
        }
        helper.assertTrue(rejected, "Precheck must verify the requested faction owns the target settlement");
        helper.assertTrue(fixture.service().buildings().isEmpty(),
                "Faction ownership rejection must not create a BuildingRecord");
        helper.succeed();
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m1_003",
            timeoutTicks = 20
    )
    public static void placementPrecheckRejectsRecordedFootprintOverlap(final GameTestHelper helper) {
        final PlacementFixture fixture = createPlacementFixture(helper, "M1-003 Overlap");
        setWarehouseGround(fixture);
        fixture.service().planBuilding(fixture.settlement().id(), BlueprintCatalog.WAREHOUSE_PROTOTYPE_ID,
                fixture.level().dimension().location(), fixture.origin(), 0, fixture.level().getGameTime());

        boolean rejected = false;
        try {
            BuildingPlacementService.planBuilding(fixture.level(), fixture.service(), fixture.faction().id(),
                    fixture.settlement().id(), BlueprintCatalog.WAREHOUSE_PROTOTYPE_ID,
                    fixture.origin(), 0, fixture.level().getGameTime());
        } catch (IllegalArgumentException expected) {
            rejected = expected.getMessage().contains("overlaps existing building");
        }
        helper.assertTrue(rejected, "Precheck must reject a footprint overlapping an existing logical building");
        helper.assertTrue(fixture.service().buildings().size() == 1,
                "Overlap rejection must leave the existing record untouched and create no second record");
        helper.succeed();
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m1_003",
            timeoutTicks = 20
    )
    public static void placementPrecheckDoesNotForceLoadRemoteChunks(final GameTestHelper helper) {
        final PlacementFixture fixture = createPlacementFixture(helper, "M1-003 Chunks");
        final BlockPos remoteOrigin = new BlockPos(4096, 64, 4096);
        helper.assertTrue(!fixture.level().hasChunkAt(remoteOrigin.below()),
                "Test setup expected the remote support chunk to be unloaded");

        boolean rejected = false;
        try {
            BuildingPlacementService.planBuilding(fixture.level(), fixture.service(), fixture.faction().id(),
                    fixture.settlement().id(), BlueprintCatalog.WAREHOUSE_PROTOTYPE_ID,
                    remoteOrigin, 0, fixture.level().getGameTime());
        } catch (IllegalArgumentException expected) {
            rejected = expected.getMessage().contains("already loaded");
        }
        helper.assertTrue(rejected, "Precheck must pause/reject instead of force-loading target chunks");
        helper.assertTrue(!fixture.level().hasChunkAt(remoteOrigin.below()),
                "Precheck unexpectedly force-loaded a remote support chunk");
        helper.assertTrue(fixture.service().buildings().isEmpty(),
                "Unloaded-chunk rejection must not create a BuildingRecord");
        helper.succeed();
    }

    @GameTest(
            template = "gametest_empty_5x5x5",
            templateNamespace = "empires",
            batch = "empires.m1_003",
            timeoutTicks = 20
    )
    public static void successfulPlacementPrecheckCreatesRecordWithoutChangingBlocks(final GameTestHelper helper) {
        final PlacementFixture fixture = createPlacementFixture(helper, "M1-003 Valid");
        setWarehouseGround(fixture);
        helper.assertTrue(fixture.level().getBlockState(fixture.origin()).isAir(),
                "Test setup expects the warehouse origin block to be empty");

        final BuildingPlacementService.PlanResult result = BuildingPlacementService.planBuilding(
                fixture.level(), fixture.service(), fixture.faction().id(), fixture.settlement().id(),
                BlueprintCatalog.WAREHOUSE_PROTOTYPE_ID, fixture.origin(), 0, fixture.level().getGameTime());

        helper.assertTrue(result.totalBlocks() == 25, "Warehouse precheck should validate all 25 template entries");
        helper.assertTrue(result.building().state() == dev.minecraftempires.common.building.BuildingState.PLANNED
                        && result.building().worldSyncState() == WorldSyncState.NOT_PLACED,
                "Successful precheck should persist a logical PLANNED/NOT_PLACED record");
        helper.assertTrue(fixture.service().buildings().size() == 1,
                "Successful precheck should create exactly one building record");
        helper.assertTrue(fixture.level().getBlockState(fixture.origin()).isAir(),
                "Planning/precheck must not place any world blocks");
        helper.succeed();
    }

    private static PlacementFixture createPlacementFixture(final GameTestHelper helper, final String factionName) {
        final var level = helper.getLevel();
        final EmpiresWorldData data = new EmpiresWorldData();
        final CivilizationService service = new CivilizationService(new SavedDataCivilizationRepository(data));
        final FactionRecord faction = service.createFaction(
                factionName, TEST_SPECIES, ControllerType.AI, level.getGameTime());
        final BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        final SettlementRecord settlement = service.createSettlement(
                faction.id(), level.dimension().location(), origin, level.getGameTime());
        return new PlacementFixture(level, data, service, faction, settlement, origin);
    }

    private static void setWarehouseGround(final PlacementFixture fixture) {
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                fixture.level().setBlock(fixture.origin().offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
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

    private record PlacementFixture(
            net.minecraft.server.level.ServerLevel level,
            EmpiresWorldData data,
            CivilizationService service,
            FactionRecord faction,
            SettlementRecord settlement,
            BlockPos origin
    ) {
    }

    private record Fixture(EmpiresWorldData data) {
    }
}
