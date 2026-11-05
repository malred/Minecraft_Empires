package dev.minecraftempires.server;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.minecraftempires.MinecraftEmpires;
import dev.minecraftempires.common.building.BlueprintCatalog;
import dev.minecraftempires.common.building.BlueprintDefinition;
import dev.minecraftempires.common.building.BuildingRecord;
import dev.minecraftempires.common.civilization.CivilizationService;
import dev.minecraftempires.common.civilization.EmpiresWorldData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/** OP-only logical blueprint/building commands for M0-009 verification. */
@Mod.EventBusSubscriber(modid = MinecraftEmpires.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BuildingCommands {
    private BuildingCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(final RegisterCommandsEvent event) {
        final var debug = Commands.literal("debug");

        debug.then(Commands.literal("list-blueprints").executes(context -> {
            for (BlueprintDefinition blueprint : BlueprintCatalog.all()) {
                context.getSource().sendSuccess(() -> Component.literal(
                        blueprint.id() + " | category=" + blueprint.category()
                                + " | version=" + blueprint.version()
                                + " | size=" + blueprint.sizeX() + "x" + blueprint.sizeY() + "x" + blueprint.sizeZ()
                                + " | template=" + blueprint.templateId()
                                + " | workPoints=" + blueprint.workPoints()
                                + " | rotations=" + blueprint.allowedRotations()
                                + " | checksum=" + blueprint.templateChecksum()), false);
            }
            return BlueprintCatalog.all().size();
        }));

        debug.then(Commands.literal("building-count").executes(context -> {
            final EmpiresWorldData data = EmpiresWorldData.get(context.getSource().getServer());
            context.getSource().sendSuccess(() -> Component.literal(
                    "Buildings: count=" + data.buildingCount() + ", schema=" + data.schemaVersion()
                            + ", readOnly=" + data.isReadOnly()), false);
            return 1;
        }));

        debug.then(Commands.literal("plan-building")
                .then(Commands.argument("settlement", StringArgumentType.word())
                        .then(Commands.argument("blueprint", ResourceLocationArgument.id())
                                .executes(context -> planBuilding(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "settlement"),
                                        ResourceLocationArgument.getId(context, "blueprint"), 0))
                                .then(Commands.argument("rotation", IntegerArgumentType.integer(0, 270))
                                        .executes(context -> planBuilding(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "settlement"),
                                                ResourceLocationArgument.getId(context, "blueprint"),
                                                IntegerArgumentType.getInteger(context, "rotation")))))));

        debug.then(Commands.literal("list-buildings")
                .then(Commands.argument("settlement", StringArgumentType.word())
                        .executes(context -> listBuildings(context.getSource(),
                                StringArgumentType.getString(context, "settlement")))));

        debug.then(Commands.literal("show-building")
                .then(Commands.argument("building", StringArgumentType.word())
                        .executes(context -> showBuilding(context.getSource(),
                                StringArgumentType.getString(context, "building")))));

        event.getDispatcher().register(Commands.literal("empires")
                .requires(source -> source.hasPermission(2))
                .then(debug));
    }

    private static int planBuilding(
            final CommandSourceStack source,
            final String settlementText,
            final ResourceLocation blueprintId,
            final int rotationDegrees
    ) {
        try {
            final UUID settlementId = UUID.fromString(settlementText);
            final BlockPos origin = BlockPos.containing(source.getPosition());
            final CivilizationService service = CivilizationService.forLevel(source.getLevel());
            final BuildingRecord building = service.planBuilding(
                    settlementId,
                    blueprintId,
                    source.getLevel().dimension().location(),
                    origin,
                    rotationDegrees,
                    source.getLevel().getGameTime()
            );
            source.sendSuccess(() -> Component.literal(
                    "Planned building " + building.id() + " | blueprint=" + building.blueprintId()
                            + " | state=" + building.state() + " | origin=" + building.origin()
                            + " | rotation=" + building.rotationDegrees()
                            + " | progress=" + building.constructionProgress() + "/" + building.workPointsRequired()
                            + " | reservedMaterials=" + building.reservedMaterials().asMap().size()
                            + " | worldSync=" + building.worldSyncState()
                            + " | No blocks placed and no resources deducted."), true);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int listBuildings(final CommandSourceStack source, final String settlementText) {
        try {
            final UUID settlementId = UUID.fromString(settlementText);
            final var buildings = CivilizationService.forLevel(source.getLevel()).buildingsForSettlement(settlementId);
            if (buildings.isEmpty()) {
                source.sendSuccess(() -> Component.literal("No buildings are recorded for settlement " + settlementId), false);
                return 1;
            }
            for (BuildingRecord building : buildings) {
                source.sendSuccess(() -> Component.literal(
                        building.id() + " | " + building.blueprintId() + " | " + building.state()
                                + " | origin=" + building.origin() + " | rotation=" + building.rotationDegrees()
                                + " | progress=" + building.constructionProgress() + "/" + building.workPointsRequired()
                                + " | worldSync=" + building.worldSyncState()
                                + " | placementCursor=" + building.placementCursor()), false);
            }
            return buildings.size();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int showBuilding(final CommandSourceStack source, final String buildingText) {
        try {
            final UUID buildingId = UUID.fromString(buildingText);
            final BuildingRecord building = CivilizationService.forLevel(source.getLevel()).findBuilding(buildingId)
                    .orElseThrow(() -> new IllegalArgumentException("Building does not exist: " + buildingId));
            source.sendSuccess(() -> Component.literal(
                    "Building " + building.id()
                            + " | blueprint=" + building.blueprintId() + "@" + building.blueprintVersion()
                            + " | faction=" + building.ownerFactionId()
                            + " | settlement=" + building.settlementId()
                            + " | dimension=" + building.dimensionId()
                            + " | origin=" + building.origin()
                            + " | rotation=" + building.rotationDegrees()
                            + " | state=" + building.state()
                            + " | progress=" + building.constructionProgress() + "/" + building.workPointsRequired()
                            + " | reservedMaterials=" + building.reservedMaterials().asMap()
                            + " | durability=" + building.durability()
                            + " | worldSync=" + building.worldSyncState()
                            + " | placementCursor=" + building.placementCursor()
                            + " | templateChecksum=" + building.templateChecksum()
                            + " | createdAt=" + building.createdAtGameTime()), false);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }
}
