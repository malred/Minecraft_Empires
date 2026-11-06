package dev.minecraftempires.server;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.minecraftempires.MinecraftEmpires;
import dev.minecraftempires.common.civilization.BuilderJobRecord;
import dev.minecraftempires.common.civilization.CivilizationService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;

/** OP-only builder job diagnostics and lifecycle controls for M1-006. */
@Mod.EventBusSubscriber(modid = MinecraftEmpires.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BuilderJobCommands {
    private BuilderJobCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(final RegisterCommandsEvent event) {
        final var debug = Commands.literal("debug");

        debug.then(Commands.literal("assign-builder")
                .then(Commands.argument("citizen", StringArgumentType.word())
                        .executes(context -> assignBuilder(
                                context.getSource(), StringArgumentType.getString(context, "citizen")))));

        debug.then(Commands.literal("create-builder-job")
                .then(Commands.argument("building", StringArgumentType.word())
                        .executes(context -> createBuilderJob(
                                context.getSource(), StringArgumentType.getString(context, "building"),
                                BuilderJobRecord.DEFAULT_PRIORITY))
                        .then(Commands.argument("priority", IntegerArgumentType.integer(
                                BuilderJobRecord.MIN_PRIORITY, BuilderJobRecord.MAX_PRIORITY))
                                .executes(context -> createBuilderJob(
                                        context.getSource(), StringArgumentType.getString(context, "building"),
                                        IntegerArgumentType.getInteger(context, "priority"))))));

        debug.then(Commands.literal("list-builder-jobs")
                .executes(context -> listBuilderJobs(context.getSource(), null))
                .then(Commands.argument("building", StringArgumentType.word())
                        .executes(context -> listBuilderJobs(
                                context.getSource(), StringArgumentType.getString(context, "building")))));

        debug.then(Commands.literal("claim-builder-job")
                .then(Commands.argument("job", StringArgumentType.word())
                        .then(Commands.argument("citizen", StringArgumentType.word())
                                .executes(context -> claimBuilderJob(context.getSource(),
                                        StringArgumentType.getString(context, "job"),
                                        StringArgumentType.getString(context, "citizen"))))));

        debug.then(Commands.literal("start-builder-job")
                .then(Commands.argument("job", StringArgumentType.word())
                        .then(Commands.argument("citizen", StringArgumentType.word())
                                .executes(context -> startBuilderJob(context.getSource(),
                                        StringArgumentType.getString(context, "job"),
                                        StringArgumentType.getString(context, "citizen"))))));

        debug.then(Commands.literal("release-builder-job")
                .then(Commands.argument("job", StringArgumentType.word())
                        .then(Commands.argument("citizen", StringArgumentType.word())
                                .executes(context -> releaseBuilderJob(context.getSource(),
                                        StringArgumentType.getString(context, "job"),
                                        StringArgumentType.getString(context, "citizen"))))));

        debug.then(Commands.literal("block-builder-job")
                .then(Commands.argument("job", StringArgumentType.word())
                        .then(Commands.argument("citizen", StringArgumentType.word())
                                .executes(context -> blockBuilderJob(context.getSource(),
                                        StringArgumentType.getString(context, "job"),
                                        StringArgumentType.getString(context, "citizen"))))));

        debug.then(Commands.literal("retry-builder-job")
                .then(Commands.argument("job", StringArgumentType.word())
                        .executes(context -> retryBuilderJob(
                                context.getSource(), StringArgumentType.getString(context, "job")))));

        debug.then(Commands.literal("complete-builder-job")
                .then(Commands.argument("job", StringArgumentType.word())
                        .executes(context -> completeBuilderJob(
                                context.getSource(), StringArgumentType.getString(context, "job")))));

        event.getDispatcher().register(Commands.literal("empires")
                .requires(source -> source.hasPermission(2))
                .then(debug));
    }

    private static int assignBuilder(final CommandSourceStack source, final String citizenText) {
        try {
            final UUID citizenId = UUID.fromString(citizenText);
            final var citizen = CivilizationService.forLevel(source.getLevel()).assignBuilder(citizenId);
            source.sendSuccess(() -> Component.literal("Builder profession assigned | citizen="
                    + citizen.id() + " | profession=" + citizen.professionId()), true);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int createBuilderJob(
            final CommandSourceStack source,
            final String buildingText,
            final int priority
    ) {
        try {
            final UUID buildingId = UUID.fromString(buildingText);
            final BuilderJobRecord job = CivilizationService.forLevel(source.getLevel())
                    .createBuilderJob(buildingId, priority, source.getLevel().getGameTime());
            source.sendSuccess(() -> Component.literal("Builder job ready | job=" + job.id()
                    + " | building=" + job.buildingId() + " | state=" + job.state()
                    + " | priority=" + job.priority()), true);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int listBuilderJobs(final CommandSourceStack source, final String buildingText) {
        try {
            final CivilizationService service = CivilizationService.forLevel(source.getLevel());
            final List<BuilderJobRecord> jobs = buildingText == null
                    ? service.builderJobs()
                    : service.builderJobsForBuilding(UUID.fromString(buildingText));
            if (jobs.isEmpty()) {
                source.sendSuccess(() -> Component.literal("No builder jobs found."), false);
                return 1;
            }
            for (BuilderJobRecord job : jobs) {
                source.sendSuccess(() -> Component.literal(job.id()
                        + " | building=" + job.buildingId()
                        + " | faction=" + job.ownerFactionId()
                        + " | state=" + job.state()
                        + " | worker=" + (job.assignedCitizenId() == null ? "none" : job.assignedCitizenId())
                        + " | priority=" + job.priority()
                        + " | retries=" + job.retryCount()), false);
            }
            return jobs.size();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int claimBuilderJob(
            final CommandSourceStack source,
            final String jobText,
            final String citizenText
    ) {
        try {
            final var job = CivilizationService.forLevel(source.getLevel()).claimBuilderJob(
                    UUID.fromString(jobText), UUID.fromString(citizenText), source.getLevel().getGameTime());
            source.sendSuccess(() -> Component.literal("Builder job claimed | job=" + job.id()
                    + " | worker=" + job.assignedCitizenId() + " | state=" + job.state()), true);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int startBuilderJob(
            final CommandSourceStack source,
            final String jobText,
            final String citizenText
    ) {
        try {
            final var job = CivilizationService.forLevel(source.getLevel()).startBuilderJob(
                    UUID.fromString(jobText), UUID.fromString(citizenText), source.getLevel().getGameTime());
            source.sendSuccess(() -> Component.literal("Builder job started | job=" + job.id()
                    + " | state=" + job.state()), true);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int releaseBuilderJob(
            final CommandSourceStack source,
            final String jobText,
            final String citizenText
    ) {
        try {
            final var job = CivilizationService.forLevel(source.getLevel()).releaseBuilderJob(
                    UUID.fromString(jobText), UUID.fromString(citizenText), source.getLevel().getGameTime());
            source.sendSuccess(() -> Component.literal("Builder job released | job=" + job.id()
                    + " | state=" + job.state() + " | retries=" + job.retryCount()), true);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int blockBuilderJob(
            final CommandSourceStack source,
            final String jobText,
            final String citizenText
    ) {
        try {
            final var job = CivilizationService.forLevel(source.getLevel()).blockBuilderJob(
                    UUID.fromString(jobText), UUID.fromString(citizenText), source.getLevel().getGameTime());
            source.sendSuccess(() -> Component.literal("Builder job blocked | job=" + job.id()
                    + " | state=" + job.state() + " | retries=" + job.retryCount()), true);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int retryBuilderJob(final CommandSourceStack source, final String jobText) {
        try {
            final var job = CivilizationService.forLevel(source.getLevel()).retryBuilderJob(
                    UUID.fromString(jobText), source.getLevel().getGameTime());
            source.sendSuccess(() -> Component.literal("Builder job queued again | job=" + job.id()
                    + " | state=" + job.state() + " | retries=" + job.retryCount()), true);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int completeBuilderJob(final CommandSourceStack source, final String jobText) {
        try {
            final var job = CivilizationService.forLevel(source.getLevel()).completeBuilderJob(
                    UUID.fromString(jobText), source.getLevel().getGameTime());
            source.sendSuccess(() -> Component.literal("Builder job completed | job=" + job.id()
                    + " | state=" + job.state()), true);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }
}
