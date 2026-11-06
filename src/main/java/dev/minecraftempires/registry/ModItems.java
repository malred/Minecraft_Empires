package dev.minecraftempires.registry;

import dev.minecraftempires.MinecraftEmpires;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Registered player-facing blueprint tokens. */
public final class ModItems {
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MinecraftEmpires.MOD_ID);

    public static final RegistryObject<Item> HEADQUARTERS_BLUEPRINT = ITEMS.register(
            "headquarters_blueprint",
            () -> new Item(new Item.Properties().stacksTo(1))
    );

    private ModItems() {
    }

    public static void register(final IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    public static void addCreative(final BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(HEADQUARTERS_BLUEPRINT.get());
        }
    }
}
