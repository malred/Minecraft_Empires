package dev.minecraftempires.server;

import dev.minecraftempires.MinecraftEmpires;
import dev.minecraftempires.common.civilization.EmpiresWorldData;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Dedicated/server lifecycle hooks. This class must remain client-API free. */
@Mod.EventBusSubscriber(modid = MinecraftEmpires.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServerLifecycleEvents {
    private ServerLifecycleEvents() {
    }

    @SubscribeEvent
    public static void onServerStarted(final ServerStartedEvent event) {
        final EmpiresWorldData data = EmpiresWorldData.get(event.getServer());
        MinecraftEmpires.LOGGER.info(
                "Minecraft Empires server lifecycle connected. worldId={}, factions={}, schema={}, readOnly={}",
                data.worldId(), data.factionCount(), data.schemaVersion(), data.isReadOnly()
        );
    }

    @SubscribeEvent
    public static void onServerStopped(final ServerStoppedEvent event) {
        MinecraftEmpires.LOGGER.info("Minecraft Empires server lifecycle stopped.");
    }
}
