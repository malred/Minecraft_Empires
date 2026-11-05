package dev.minecraftempires.registry;

import dev.minecraftempires.MinecraftEmpires;
import dev.minecraftempires.common.entity.CitizenEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Registry for Minecraft Empires entity types. */
public final class ModEntities {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MinecraftEmpires.MOD_ID);

    public static final RegistryObject<EntityType<CitizenEntity>> CITIZEN = ENTITY_TYPES.register(
            "citizen",
            () -> EntityType.Builder.<CitizenEntity>of(CitizenEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .build("empires:citizen")
    );

    private ModEntities() {
    }

    public static void register(final IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }

    public static void onEntityAttributeCreation(final EntityAttributeCreationEvent event) {
        event.put(CITIZEN.get(), CitizenEntity.createAttributes().build());
    }
}
