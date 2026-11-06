package dev.minecraftempires;

import com.mojang.logging.LogUtils;
import dev.minecraftempires.common.EmpiresConfig;
import dev.minecraftempires.registry.ModBlocks;
import dev.minecraftempires.registry.ModEntities;
import dev.minecraftempires.registry.ModItems;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/** Main Forge entry point for Minecraft Empires. */
@Mod(MinecraftEmpires.MOD_ID)
public final class MinecraftEmpires {
    public static final String MOD_ID = "empires";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MinecraftEmpires() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::onCommonSetup);
        ModEntities.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        modEventBus.addListener(ModItems::addCreative);
        modEventBus.addListener(ModEntities::onEntityAttributeCreation);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, EmpiresConfig.SPEC);
        LOGGER.info("Minecraft Empires mod entry point initialized.");
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> LOGGER.info("Minecraft Empires common setup complete. Citizen entity registration is active."));
    }
}
