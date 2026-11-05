package dev.minecraftempires.server;

import dev.minecraftempires.MinecraftEmpires;
import dev.minecraftempires.common.civilization.CitizenRecord;
import dev.minecraftempires.common.civilization.CitizenStatus;
import dev.minecraftempires.common.civilization.CivilizationService;
import dev.minecraftempires.common.entity.CitizenEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/** Keeps the persistent logical citizen and the chunk-persisted entity linked one-to-one. */
@Mod.EventBusSubscriber(modid = MinecraftEmpires.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CitizenEntityEvents {
    private CitizenEntityEvents() {
    }

    @SubscribeEvent
    public static void onCitizenJoinLevel(final EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof CitizenEntity entity)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        final UUID citizenId = entity.getCitizenId();
        final UUID factionId = entity.getFactionId();
        if (citizenId == null || factionId == null) {
            MinecraftEmpires.LOGGER.warn("Rejecting CitizenEntity {} with missing logical identity", entity.getUUID());
            event.setCanceled(true);
            return;
        }

        try {
            final CivilizationService service = CivilizationService.forLevel(level);
            final CitizenRecord citizen = service.findCitizen(citizenId).orElse(null);
            if (citizen == null) {
                MinecraftEmpires.LOGGER.warn("Rejecting CitizenEntity {}: logical citizen {} does not exist",
                        entity.getUUID(), citizenId);
                event.setCanceled(true);
                return;
            }
            if (citizen.status() != CitizenStatus.ACTIVE || !citizen.ownerFactionId().equals(factionId)) {
                MinecraftEmpires.LOGGER.warn("Rejecting CitizenEntity {}: citizen {} is inactive or faction-mismatched",
                        entity.getUUID(), citizenId);
                event.setCanceled(true);
                return;
            }
            if (citizen.entityUuid() != null && !citizen.entityUuid().equals(entity.getUUID())) {
                MinecraftEmpires.LOGGER.warn("Rejecting duplicate CitizenEntity {} for citizen {}; authoritative entity is {}",
                        entity.getUUID(), citizenId, citizen.entityUuid());
                event.setCanceled(true);
                return;
            }
            service.bindCitizenEntity(citizenId, entity.getUUID());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            MinecraftEmpires.LOGGER.error("Could not reconcile CitizenEntity {}: {}",
                    entity.getUUID(), exception.getMessage());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onCitizenDeath(final LivingDeathEvent event) {
        if (!(event.getEntity() instanceof CitizenEntity entity) || entity.level().isClientSide) {
            return;
        }
        final UUID citizenId = entity.getCitizenId();
        if (citizenId == null || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        try {
            CivilizationService.forLevel(level).markCitizenDeceased(citizenId, entity.getUUID());
            MinecraftEmpires.LOGGER.info("Citizen {} ({}) has died", entity.getCitizenDisplayName(), citizenId);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            MinecraftEmpires.LOGGER.error("Could not mark citizen {} as deceased: {}", citizenId, exception.getMessage());
        }
    }
}
