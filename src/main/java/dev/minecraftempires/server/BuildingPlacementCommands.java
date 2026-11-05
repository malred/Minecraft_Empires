package dev.minecraftempires.server;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.minecraftempires.MinecraftEmpires;
import dev.minecraftempires.common.EmpiresConfig;
import dev.minecraftempires.common.building.BlueprintCatalog;
import dev.minecraftempires.common.building.BlueprintDefinition;
import dev.minecraftempires.common.building.BlueprintTemplate;
import dev.minecraftempires.common.building.BuildingPlacementService;
import dev.minecraftempires.common.building.BuildingRecord;
import dev.minecraftempires.common.civilization.CivilizationService;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/** OP-only commands for starting and inspecting the M0-010 block placement adapter. */
@Mod.EventBusSubscriber(modid = MinecraftEmpires.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BuildingPlacementCommands {
    private BuildingPlacementCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(final RegisterCommandsEvent event) {
        final var debug = net.minecraft.commands.Commands.literal("debug");

        debug.then(net.minecraft.commands.Commands.literal("place-building")
                .then(net.minecraft.commands.Commands.argument("building", StringArgumentType.word())
                        .executes(context -> beginPlacement(context.getSource(),
                                StringArgumentType.getString(context, "building")))));

        debug.then(net.minecraft.commands.Commands.literal("resume-building-placement")
                .then(net.minecraft.commands.Commands.argument("building", StringArgumentType.word())
                        .executes(context -> resumePlacement(context.getSource(),
                                StringArgumentType.getString(context, "building")))));

        debug.then(net.minecraft.commands.Commands.literal("building-placement")
                .then(net.minecraft.commands.Commands.argument("building", StringArgumentType.word())
                        .executes(context -> placementStatus(context.getSource(),
                                StringArgumentType.getString(context, "building")))));

        event.getDispatcher().register(net.minecraft.commands.Commands.literal("empires")
                .requires(source -> source.hasPermission(2))
                .then(debug));
    }

    private static int beginPlacement(
            final net.minecraft.commands.CommandSourceStack source,
            final String buildingText
    ) {
        try {
            final UUID buildingId = UUID.fromString(buildingText);
            final BuildingPlacementService.StartResult result = BuildingPlacementService.beginPlacement(
                    source.getLevel(), buildingId);
            final int budget = EmpiresConfig.BUILDING_PLACEMENT_BLOCKS_PER_TICK.get();
            final int estimatedTicks = (result.totalBlocks() + budget - 1) / budget;
            source.sendSuccess(() -> Component.literal(
                    "Placement queued: building=" + buildingId + ", blocks=" + result.totalBlocks()
                            + ", cursor=0, budgetPerTick=" + budget + ", estimatedTicks=" + estimatedTicks
                            + ". No chunks were force-loaded; placement will pause if target chunks unload."), true);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int resumePlacement(
            final net.minecraft.commands.CommandSourceStack source,
            final String buildingText
    ) {
        try {
            final UUID buildingId = UUID.fromString(buildingText);
            final BuildingPlacementService.StartResult result = BuildingPlacementService.resumePlacement(
                    source.getLevel(), buildingId);
            source.sendSuccess(() -> Component.literal(
                    "Placement resumed: building=" + buildingId + ", cursor="
                            + result.building().placementCursor() + "/" + result.totalBlocks()), true);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int placementStatus(
            final net.minecraft.commands.CommandSourceStack source,
            final String buildingText
    ) {
        try {
            final UUID buildingId = UUID.fromString(buildingText);
            final BuildingRecord building = CivilizationService.forLevel(source.getLevel()).findBuilding(buildingId)
                    .orElseThrow(() -> new IllegalArgumentException("Building does not exist: " + buildingId));
            final BlueprintDefinition definition = BlueprintCatalog.find(building.blueprintId())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown blueprint: " + building.blueprintId()));
            final BlueprintTemplate template = BlueprintTemplate.load(source.getServer().getResourceManager(), definition);
            source.sendSuccess(() -> Component.literal(
                    "Building placement: id=" + building.id()
                            + ", state=" + building.state()
                            + ", worldSync=" + building.worldSyncState()
                            + ", cursor=" + building.placementCursor() + "/" + template.blocks().size()
                            + ", templateChecksum=" + template.checksum()), false);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }
}
