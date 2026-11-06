package dev.minecraftempires.registry;

import dev.minecraftempires.MinecraftEmpires;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registered physical markers used by managed Empire buildings.
 *
 * <p>The headquarters core intentionally has no BlockItem. It is materialized only by the
 * server-authoritative blueprint placement adapter; players cannot bypass building ownership
 * and uniqueness checks by placing the marker as an ordinary block.</p>
 */
public final class ModBlocks {
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MinecraftEmpires.MOD_ID);

    public static final RegistryObject<Block> HEADQUARTERS_CORE = BLOCKS.register(
            "headquarters_core",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.CHISELED_DEEPSLATE)
                    .strength(5.0F, 1200.0F)
                    .requiresCorrectToolForDrops())
    );

    private ModBlocks() {
    }

    public static void register(final IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
