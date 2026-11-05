package dev.minecraftempires.server;

import dev.minecraftempires.MinecraftEmpires;
import dev.minecraftempires.common.building.BuildingPlacementService;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Resumable server-thread scheduler for bounded, already-loaded blueprint placement. */
@Mod.EventBusSubscriber(modid = MinecraftEmpires.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BuildingPlacementScheduler {
    private BuildingPlacementScheduler() {
    }

    @SubscribeEvent
    public static void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        BuildingPlacementService.processPending(event.getServer());
    }
}
