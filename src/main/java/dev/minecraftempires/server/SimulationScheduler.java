package dev.minecraftempires.server;

import dev.minecraftempires.MinecraftEmpires;
import dev.minecraftempires.common.EmpiresConfig;
import dev.minecraftempires.common.civilization.CivilizationService;
import dev.minecraftempires.common.civilization.ControllerType;
import dev.minecraftempires.common.civilization.EmpiresWorldData;
import dev.minecraftempires.common.civilization.FactionRecord;
import dev.minecraftempires.common.civilization.FactionStatus;
import dev.minecraftempires.common.civilization.ResourceLedger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M0-008 data-only simulation proof.
 *
 * IMPORTANT: This class intentionally never queries chunks, block states, entities, or tickets.
 * It reads only world game time and persisted civilization records. All work runs on the
 * logical server thread. The temporary wood output is a test signal, not the final economy.
 */
@Mod.EventBusSubscriber(modid = MinecraftEmpires.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SimulationScheduler {
    private static final ResourceLocation TEST_WOOD = new ResourceLocation(MinecraftEmpires.MOD_ID, "wood");

    private static long completedCycles;
    private static long lastBatchGameTime = -1L;
    private static int lastProcessedFactionCount;
    private static long lastBudgetWarningGameTime = Long.MIN_VALUE;

    private SimulationScheduler() {
    }

    @SubscribeEvent
    public static void onServerStarted(final ServerStartedEvent event) {
        completedCycles = 0L;
        lastBatchGameTime = -1L;
        lastProcessedFactionCount = 0;
        lastBudgetWarningGameTime = Long.MIN_VALUE;

        final MinecraftServer server = event.getServer();
        final EmpiresWorldData data = EmpiresWorldData.get(server);
        if (data.isReadOnly()) {
            MinecraftEmpires.LOGGER.warn("Abstract simulation is disabled because civilization data is read-only: {}",
                    data.readOnlyReason());
            return;
        }

        // Establish a fresh baseline every server start. Game time does not advance while the
        // server is stopped, and this intentionally discards a partial interval at shutdown.
        // Therefore M0-008 never grants offline production.
        final long now = server.overworld().getGameTime();
        if (now > data.simulationClock()) {
            CivilizationService.forLevel(server.overworld()).advanceSimulationClock(now);
        }
        MinecraftEmpires.LOGGER.info(
                "Minecraft Empires abstract simulation ready. enabled={}, gameTime={}, baseline={}, intervalTicks={}, budget={}",
                EmpiresConfig.SIMULATION_ENABLED.get(), now,
                EmpiresWorldData.get(server).simulationClock(),
                EmpiresConfig.SIMULATION_INTERVAL_TICKS.get(),
                EmpiresConfig.SIMULATION_BUDGET_PER_TICK.get());
    }

    @SubscribeEvent
    public static void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        final MinecraftServer server = event.getServer();
        final EmpiresWorldData data = EmpiresWorldData.get(server);
        if (data.isReadOnly()) {
            return;
        }

        final long now = server.overworld().getGameTime();
        final long elapsed = now - data.simulationClock();
        if (elapsed < 0L) {
            // A world-time rollback must never produce negative or duplicated resources.
            return;
        }

        final int interval = EmpiresConfig.SIMULATION_INTERVAL_TICKS.get();
        if (!EmpiresConfig.SIMULATION_ENABLED.get()) {
            // Keep the clock close to current time while disabled, so enabling the feature
            // later in the same session cannot cause a large catch-up payment.
            if (elapsed >= interval) {
                CivilizationService.forLevel(server.overworld()).advanceSimulationClock(now);
            }
            return;
        }

        if (elapsed < interval) {
            return;
        }

        final long cycles = elapsed / interval;
        final long processedTicks;
        final long woodDelta;
        try {
            processedTicks = Math.multiplyExact(cycles, (long) interval);
            woodDelta = Math.multiplyExact(cycles,
                    (long) EmpiresConfig.SIMULATION_TEST_WOOD_PER_CYCLE.get());
        } catch (ArithmeticException exception) {
            MinecraftEmpires.LOGGER.error("Abstract simulation amount overflow; leaving the clock unchanged.", exception);
            return;
        }

        final List<FactionRecord> activeAiFactions = data.factions().stream()
                .filter(faction -> faction.controllerType() == ControllerType.AI)
                .filter(faction -> faction.status() == FactionStatus.ACTIVE)
                .toList();

        final int budget = EmpiresConfig.SIMULATION_BUDGET_PER_TICK.get();
        if (activeAiFactions.size() > budget) {
            if (lastBudgetWarningGameTime == Long.MIN_VALUE
                    || now - lastBudgetWarningGameTime >= interval) {
                MinecraftEmpires.LOGGER.warn(
                        "Abstract simulation skipped: {} active AI factions exceed the configured batch budget {}. Increase empires.general.simulationBudgetPerTick.",
                        activeAiFactions.size(), budget);
                lastBudgetWarningGameTime = now;
            }
            return;
        }

        final long processedThrough;
        try {
            processedThrough = Math.addExact(data.simulationClock(), processedTicks);
        } catch (ArithmeticException exception) {
            MinecraftEmpires.LOGGER.error("Abstract simulation clock overflow; leaving all data unchanged.", exception);
            return;
        }

        final CivilizationService service = CivilizationService.forLevel(server.overworld());
        final Map<UUID, ResourceLedger> plannedLedgers = new LinkedHashMap<>();
        try {
            // Prepare all changes before committing any faction update.
            if (woodDelta > 0L) {
                for (FactionRecord faction : activeAiFactions) {
                    plannedLedgers.put(faction.id(), faction.resources().plus(TEST_WOOD, woodDelta));
                }
            }
        } catch (IllegalArgumentException exception) {
            MinecraftEmpires.LOGGER.error("Abstract simulation could not prepare resource updates; clock unchanged.", exception);
            return;
        }

        try {
            for (Map.Entry<UUID, ResourceLedger> update : plannedLedgers.entrySet()) {
                service.setResources(update.getKey(), update.getValue());
            }
            service.advanceSimulationClock(processedThrough);
            completedCycles = saturatedAdd(completedCycles, cycles);
            lastBatchGameTime = processedThrough;
            lastProcessedFactionCount = activeAiFactions.size();

            if (EmpiresConfig.DEBUG_LOGGING.get()) {
                MinecraftEmpires.LOGGER.info(
                        "Abstract simulation batch completed: cycles={}, factions={}, woodPerFaction={}, processedThrough={}. No chunk or entity access performed.",
                        cycles, activeAiFactions.size(), woodDelta, processedThrough);
            }
        } catch (RuntimeException exception) {
            MinecraftEmpires.LOGGER.error(
                    "Abstract simulation batch failed. Check the world data before continuing; clock may not have advanced.", exception);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(final ServerStoppedEvent event) {
        MinecraftEmpires.LOGGER.info("Minecraft Empires abstract simulation stopped. completedCycles={}, lastBatchGameTime={}, lastProcessedFactions={}",
                completedCycles, lastBatchGameTime, lastProcessedFactionCount);
    }

    public static String describeStatus(final MinecraftServer server) {
        final EmpiresWorldData data = EmpiresWorldData.get(server);
        final long now = server.overworld().getGameTime();
        final long elapsed = Math.max(0L, now - data.simulationClock());
        final int interval = EmpiresConfig.SIMULATION_INTERVAL_TICKS.get();
        final long nextDueIn = Math.max(0L, interval - elapsed);
        final long activeAi = data.factions().stream()
                .filter(faction -> faction.controllerType() == ControllerType.AI)
                .filter(faction -> faction.status() == FactionStatus.ACTIVE)
                .count();
        return "Simulation: enabled=" + EmpiresConfig.SIMULATION_ENABLED.get()
                + ", gameTime=" + now
                + ", lastProcessedGameTime=" + data.simulationClock()
                + ", intervalTicks=" + interval
                + ", nextBatchIn=" + nextDueIn
                + ", activeAiFactions=" + activeAi
                + ", budget=" + EmpiresConfig.SIMULATION_BUDGET_PER_TICK.get()
                + ", testWoodPerCycle=" + EmpiresConfig.SIMULATION_TEST_WOOD_PER_CYCLE.get()
                + ", completedCyclesThisSession=" + completedCycles
                + ", lastBatchGameTime=" + lastBatchGameTime
                + ", lastProcessedFactions=" + lastProcessedFactionCount
                + ", worldAccess=DATA_ONLY"
                + ", readOnly=" + data.isReadOnly();
    }

    private static long saturatedAdd(final long left, final long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }
}
