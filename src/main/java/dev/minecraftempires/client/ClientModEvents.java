package dev.minecraftempires.client;

import dev.minecraftempires.MinecraftEmpires;
import dev.minecraftempires.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client-only lifecycle hooks. All client API references remain in this package. */
@Mod.EventBusSubscriber(
        modid = MinecraftEmpires.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public final class ClientModEvents {
    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void onClientSetup(final FMLClientSetupEvent event) {
        MinecraftEmpires.LOGGER.info("Minecraft Empires client setup complete.");
    }

    @SubscribeEvent
    public static void onRegisterRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CITIZEN.get(), CitizenRenderer::new);
    }
}
