package dev.minecraftempires.common;

import net.minecraftforge.common.ForgeConfigSpec;

/** Common, server-authoritative configuration for Minecraft Empires. */
public final class EmpiresConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue DEBUG_LOGGING;
    public static final ForgeConfigSpec.BooleanValue SIMULATION_ENABLED;
    public static final ForgeConfigSpec.IntValue SIMULATION_BUDGET_PER_TICK;
    public static final ForgeConfigSpec.IntValue SIMULATION_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue SIMULATION_TEST_WOOD_PER_CYCLE;
    public static final ForgeConfigSpec.IntValue BUILDING_PLACEMENT_BLOCKS_PER_TICK;
    public static final ForgeConfigSpec SPEC;

    static {
        BUILDER.push("general");

        DEBUG_LOGGING = BUILDER
                .comment("Enable additional Minecraft Empires diagnostic logging.")
                .define("debugLogging", false);

        SIMULATION_ENABLED = BUILDER
                .comment("Enable server-side abstract civilization simulation. This simulation never loads remote chunks.")
                .define("simulationEnabled", true);

        SIMULATION_BUDGET_PER_TICK = BUILDER
                .comment("Maximum active AI factions processed in one simulation batch. Increase this for worlds with more AI factions.")
                .defineInRange("simulationBudgetPerTick", 32, 1, 4096);

        SIMULATION_INTERVAL_TICKS = BUILDER
                .comment("Minecraft game ticks between abstract simulation batches. 100 ticks is about five seconds at 20 TPS.")
                .defineInRange("simulationIntervalTicks", 100, 20, 12000);

        SIMULATION_TEST_WOOD_PER_CYCLE = BUILDER
                .comment("TEMPORARY M0-008 validation output: add this many empires:wood to each active AI faction per simulation cycle. Set to 0 to disable the test output; later building production will replace it.")
                .defineInRange("simulationTestWoodPerCycle", 1, 0, 1000000);

        BUILDING_PLACEMENT_BLOCKS_PER_TICK = BUILDER
                .comment("Maximum structure-template blocks placed by Minecraft Empires per server tick. Placement pauses when target chunks are not already loaded.")
                .defineInRange("buildingPlacementBlocksPerTick", 8, 1, 4096);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private EmpiresConfig() {
    }
}
