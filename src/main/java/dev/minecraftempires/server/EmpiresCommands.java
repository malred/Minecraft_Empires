package dev.minecraftempires.server;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.minecraftempires.MinecraftEmpires;
import dev.minecraftempires.common.civilization.CitizenRecord;
import dev.minecraftempires.common.civilization.CivilizationService;
import dev.minecraftempires.common.civilization.ControllerType;
import dev.minecraftempires.common.civilization.EmpiresWorldData;
import dev.minecraftempires.common.civilization.FactionRecord;
import dev.minecraftempires.common.civilization.ResourceLedger;
import dev.minecraftempires.common.civilization.WorldDataValidator;
import dev.minecraftempires.common.civilization.SettlementRecord;
import dev.minecraftempires.common.entity.CitizenEntity;
import dev.minecraftempires.registry.ModEntities;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;

/** OP-only development commands for verifying civilization data and citizen entity lifecycle. */
@Mod.EventBusSubscriber(modid = MinecraftEmpires.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EmpiresCommands {
    private EmpiresCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(final RegisterCommandsEvent event) {
        final var root = Commands.literal("empires").requires(source -> source.hasPermission(2));
        final var debug = Commands.literal("debug");

        debug.then(Commands.literal("validate").executes(context -> validateWorldData(context.getSource())));

        debug.then(Commands.literal("status").executes(context -> {
            final EmpiresWorldData data = EmpiresWorldData.get(context.getSource().getServer());
            context.getSource().sendSuccess(() -> Component.literal(
                    "Minecraft Empires: schema=" + data.schemaVersion()
                            + ", worldId=" + data.worldId()
                            + ", factions=" + data.factionCount()
                            + ", settlements=" + data.settlementCount()
                            + ", citizens=" + data.citizenCount()
                            + ", simulationClock=" + data.simulationClock()
                            + ", readOnly=" + data.isReadOnly()
                            + (data.isReadOnly() ? ", reason=" + data.readOnlyReason() : "")
            ), false);
            return 1;
        }));

        debug.then(Commands.literal("create-faction")
                .then(Commands.argument("name", StringArgumentType.string())
                        .then(Commands.argument("species", ResourceLocationArgument.id())
                                .executes(context -> createFaction(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name"),
                                        ResourceLocationArgument.getId(context, "species"))))));

        debug.then(Commands.literal("create-settlement")
                .then(Commands.argument("faction", StringArgumentType.word())
                        .executes(context -> createSettlement(
                                context.getSource(), StringArgumentType.getString(context, "faction")))));

        debug.then(Commands.literal("set-capital")
                .then(Commands.argument("faction", StringArgumentType.word())
                        .then(Commands.argument("settlement", StringArgumentType.word())
                                .executes(context -> setCapital(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "faction"),
                                        StringArgumentType.getString(context, "settlement"))))));

        debug.then(Commands.literal("seed-resources")
                .then(Commands.argument("faction", StringArgumentType.word())
                        .executes(context -> seedResources(
                                context.getSource(), StringArgumentType.getString(context, "faction")))));

        debug.then(Commands.literal("list-factions").executes(context -> {
            final CivilizationService service = CivilizationService.forLevel(context.getSource().getLevel());
            final var factions = service.factions();
            if (factions.isEmpty()) {
                context.getSource().sendSuccess(() -> Component.literal("No factions have been created."), false);
                return 1;
            }
            for (FactionRecord faction : factions) {
                context.getSource().sendSuccess(() -> Component.literal(
                        faction.displayName() + " | " + faction.id() + " | "
                                + faction.speciesId() + " | " + faction.controllerType()
                                + " | population=" + faction.populationCountCache()
                                + " | capital=" + faction.capitalSettlementId()
                ), false);
            }
            return factions.size();
        }));

        debug.then(Commands.literal("create-citizen")
                .then(Commands.argument("faction", StringArgumentType.word())
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(context -> createCitizen(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "faction"),
                                        StringArgumentType.getString(context, "name"))))));

        debug.then(Commands.literal("list-citizens")
                .executes(context -> listCitizens(context.getSource(), null))
                .then(Commands.argument("faction", StringArgumentType.word())
                        .executes(context -> listCitizens(
                                context.getSource(), StringArgumentType.getString(context, "faction")))));

        debug.then(Commands.literal("show-resources")
                .then(Commands.argument("faction", StringArgumentType.word())
                        .executes(context -> showResources(
                                context.getSource(), StringArgumentType.getString(context, "faction")))));

        debug.then(Commands.literal("spawn-citizen")
                .then(Commands.argument("citizen", StringArgumentType.word())
                        .executes(context -> spawnCitizen(
                                context.getSource(), StringArgumentType.getString(context, "citizen")))));

        debug.then(Commands.literal("citizen-entity")
                .then(Commands.argument("citizen", StringArgumentType.word())
                        .executes(context -> inspectCitizenEntity(
                                context.getSource(), StringArgumentType.getString(context, "citizen")))));

        debug.then(Commands.literal("despawn-citizen")
                .then(Commands.argument("citizen", StringArgumentType.word())
                        .executes(context -> despawnCitizen(
                                context.getSource(), StringArgumentType.getString(context, "citizen")))));

        root.then(debug);
        event.getDispatcher().register(root);
    }

    private static int createFaction(final CommandSourceStack source, final String name, final ResourceLocation speciesId) {
        try {
            final CivilizationService service = CivilizationService.forLevel(source.getLevel());
            final FactionRecord faction = service.createFaction(name, speciesId,
                    ControllerType.AI, source.getLevel().getGameTime());
            source.sendSuccess(() -> Component.literal(
                    "Created test faction '" + faction.displayName() + "' with ID " + faction.id()
                            + " and species " + faction.speciesId()
            ), true);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int createSettlement(final CommandSourceStack source, final String factionText) {
        try {
            final UUID factionId = UUID.fromString(factionText);
            final CivilizationService service = CivilizationService.forLevel(source.getLevel());
            final BlockPos anchor = BlockPos.containing(
                    source.getPosition().x, source.getPosition().y, source.getPosition().z);
            final SettlementRecord settlement = service.createSettlement(
                    factionId, source.getLevel().dimension().location(), anchor,
                    source.getLevel().getGameTime());
            source.sendSuccess(() -> Component.literal(
                    "Created settlement " + settlement.id() + " for faction " + factionId
                            + " at " + settlement.dimensionId() + " " + anchor
            ), true);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int setCapital(final CommandSourceStack source, final String factionText, final String settlementText) {
        try {
            final UUID factionId = UUID.fromString(factionText);
            final UUID settlementId = UUID.fromString(settlementText);
            CivilizationService.forLevel(source.getLevel()).setCapitalSettlement(factionId, settlementId);
            source.sendSuccess(() -> Component.literal("Capital settlement updated for faction " + factionId), true);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int seedResources(final CommandSourceStack source, final String factionText) {
        try {
            final UUID factionId = UUID.fromString(factionText);
            final CivilizationService service = CivilizationService.forLevel(source.getLevel());
            FactionRecord faction = service.findFaction(factionId)
                    .orElseThrow(() -> new IllegalArgumentException("Faction does not exist: " + factionId));
            ResourceLedger ledger = faction.resources()
                    .withAmount(new ResourceLocation(MinecraftEmpires.MOD_ID, "wood"), 128L)
                    .withAmount(new ResourceLocation(MinecraftEmpires.MOD_ID, "stone"), 64L)
                    .withAmount(new ResourceLocation(MinecraftEmpires.MOD_ID, "food"), 32L);
            service.setResources(factionId, ledger);
            source.sendSuccess(() -> Component.literal("Seeded test resources for faction " + factionId
                    + ": wood=128, stone=64, food=32"), true);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int createCitizen(final CommandSourceStack source, final String factionText, final String name) {
        try {
            final UUID factionId = UUID.fromString(factionText);
            final CivilizationService service = CivilizationService.forLevel(source.getLevel());
            final CitizenRecord citizen = service.createCitizen(factionId, name, source.getLevel().getGameTime());
            source.sendSuccess(() -> Component.literal(
                    "Created logical citizen '" + citizen.displayName() + "' with ID " + citizen.id()
                            + ", species=" + citizen.speciesId()
                            + ", profession=" + citizen.professionId()
                            + ", home=" + citizen.homeSettlementId()
            ), true);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int listCitizens(final CommandSourceStack source, final String factionText) {
        try {
            final CivilizationService service = CivilizationService.forLevel(source.getLevel());
            final var citizens = factionText == null
                    ? service.citizens()
                    : service.citizensForFaction(UUID.fromString(factionText));
            if (citizens.isEmpty()) {
                source.sendSuccess(() -> Component.literal("No citizens have been created."), false);
                return 1;
            }
            for (CitizenRecord citizen : citizens) {
                source.sendSuccess(() -> Component.literal(
                        citizen.displayName() + " | " + citizen.id()
                                + " | faction=" + citizen.ownerFactionId()
                                + " | species=" + citizen.speciesId()
                                + " | profession=" + citizen.professionId()
                                + " | status=" + citizen.status()
                                + " | home=" + citizen.homeSettlementId()
                                + " | entity=" + (citizen.entityUuid() == null ? "none" : citizen.entityUuid())
                ), false);
            }
            return citizens.size();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int showResources(final CommandSourceStack source, final String factionText) {
        try {
            final UUID factionId = UUID.fromString(factionText);
            final CivilizationService service = CivilizationService.forLevel(source.getLevel());
            final FactionRecord faction = service.findFaction(factionId)
                    .orElseThrow(() -> new IllegalArgumentException("Faction does not exist: " + factionId));
            if (faction.resources().asMap().isEmpty()) {
                source.sendSuccess(() -> Component.literal("Faction has no stored resources."), false);
                return 1;
            }
            for (Map.Entry<ResourceLocation, Long> entry : faction.resources().asMap().entrySet()) {
                source.sendSuccess(() -> Component.literal(entry.getKey() + " = " + entry.getValue()), false);
            }
            return faction.resources().asMap().size();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int spawnCitizen(final CommandSourceStack source, final String citizenText) {
        try {
            if (!(source.getEntity() instanceof ServerPlayer player)) {
                throw new IllegalArgumentException("Run spawn-citizen as a player so the spawn position is known.");
            }
            final UUID citizenId = UUID.fromString(citizenText);
            final CivilizationService service = CivilizationService.forLevel(player.serverLevel());
            final CitizenRecord citizen = service.findCitizen(citizenId)
                    .orElseThrow(() -> new IllegalArgumentException("Citizen does not exist: " + citizenId));
            if (citizen.status() != dev.minecraftempires.common.civilization.CitizenStatus.ACTIVE) {
                throw new IllegalArgumentException("Citizen is not active: " + citizen.status());
            }
            if (citizen.entityUuid() != null) {
                final Entity linked = findLoadedEntity(source, citizen.entityUuid());
                if (linked instanceof CitizenEntity) {
                    throw new IllegalArgumentException("Citizen is already spawned. entity=" + citizen.entityUuid());
                }
                throw new IllegalArgumentException("Citizen already has a persistent entity link (" + citizen.entityUuid()
                        + "), but it is not currently loaded. Refusing to spawn a duplicate. Return to its chunk first.");
            }

            final ServerLevel level = player.serverLevel();
            final CitizenEntity entity = ModEntities.CITIZEN.get().create(level);
            if (entity == null) {
                throw new IllegalStateException("Could not create CitizenEntity from the registered entity type");
            }
            entity.applyCitizenRecord(citizen);
            final BlockPos spawnPos = player.blockPosition().relative(player.getDirection().getClockWise());
            entity.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D,
                    player.getYRot(), 0.0F);

            if (!level.addFreshEntity(entity)) {
                throw new IllegalStateException("Minecraft refused to add the citizen entity to the world");
            }
            final CitizenRecord updated = service.findCitizen(citizenId).orElseThrow();
            if (updated.entityUuid() == null || !updated.entityUuid().equals(entity.getUUID())) {
                entity.discard();
                throw new IllegalStateException("Entity joined, but its logical entity link was not saved");
            }
            source.sendSuccess(() -> Component.literal("Spawned citizen '" + citizen.displayName() + "' at "
                    + spawnPos + " with entity UUID " + entity.getUUID()), true);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int inspectCitizenEntity(final CommandSourceStack source, final String citizenText) {
        try {
            final UUID citizenId = UUID.fromString(citizenText);
            final CivilizationService service = CivilizationService.forLevel(source.getLevel());
            final CitizenRecord citizen = service.findCitizen(citizenId)
                    .orElseThrow(() -> new IllegalArgumentException("Citizen does not exist: " + citizenId));
            if (citizen.entityUuid() == null) {
                source.sendSuccess(() -> Component.literal("Citizen " + citizenId + " has no linked Minecraft entity."), false);
                return 1;
            }
            final Entity entity = findLoadedEntity(source, citizen.entityUuid());
            if (entity instanceof CitizenEntity citizenEntity) {
                source.sendSuccess(() -> Component.literal("Citizen entity is LOADED: citizen=" + citizenId
                        + ", entity=" + citizen.entityUuid() + ", dimension="
                        + entity.level().dimension().location() + ", position=" + entity.blockPosition()
                        + ", name=" + citizenEntity.getCitizenDisplayName()), false);
            } else {
                source.sendSuccess(() -> Component.literal("Citizen entity is LINKED but not currently loaded: citizen="
                        + citizenId + ", entity=" + citizen.entityUuid()
                        + ". It may be stored in an unloaded chunk; do not spawn another entity."), false);
            }
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int despawnCitizen(final CommandSourceStack source, final String citizenText) {
        try {
            final UUID citizenId = UUID.fromString(citizenText);
            final CivilizationService service = CivilizationService.forLevel(source.getLevel());
            final CitizenRecord citizen = service.findCitizen(citizenId)
                    .orElseThrow(() -> new IllegalArgumentException("Citizen does not exist: " + citizenId));
            if (citizen.entityUuid() == null) {
                throw new IllegalArgumentException("Citizen has no linked entity to despawn");
            }
            final Entity entity = findLoadedEntity(source, citizen.entityUuid());
            if (!(entity instanceof CitizenEntity)) {
                throw new IllegalArgumentException("Citizen entity is not currently loaded. Move near it before using despawn-citizen.");
            }
            service.clearCitizenEntity(citizenId, entity.getUUID());
            entity.discard();
            source.sendSuccess(() -> Component.literal("Despawned citizen entity " + entity.getUUID()
                    + "; logical citizen " + citizenId + " remains ACTIVE and can be spawned again."), true);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }


    private static int validateWorldData(final CommandSourceStack source) {
        final EmpiresWorldData data = EmpiresWorldData.get(source.getServer());
        final WorldDataValidator.Report report = WorldDataValidator.validate(data);
        source.sendSuccess(() -> Component.literal(
                "World validation: " + (report.passed() ? "PASS" : "FAIL")
                        + " | factions=" + report.factionsChecked()
                        + ", settlements=" + report.settlementsChecked()
                        + ", citizens=" + report.citizensChecked()
                        + ", buildings=" + report.buildingsChecked()
                        + ", errors=" + report.errorCount()
                        + ", warnings=" + report.warningCount()
        ), false);
        final int limit = Math.min(report.issues().size(), 12);
        for (int index = 0; index < limit; index++) {
            final WorldDataValidator.Issue issue = report.issues().get(index);
            source.sendSuccess(() -> Component.literal(
                    "[" + issue.severity() + "] " + issue.message()
            ), false);
        }
        if (report.issues().size() > limit) {
            source.sendSuccess(() -> Component.literal(
                    "... and " + (report.issues().size() - limit)
                            + " more issue(s). See server log only after a future verbose-report option is added."
            ), false);
        }
        return report.passed() ? 1 : 0;
    }

    private static Entity findLoadedEntity(final CommandSourceStack source, final UUID entityUuid) {
        for (ServerLevel level : source.getServer().getAllLevels()) {
            final Entity entity = level.getEntity(entityUuid);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }
}
