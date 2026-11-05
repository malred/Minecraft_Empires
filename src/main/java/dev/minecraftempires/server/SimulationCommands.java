package dev.minecraftempires.server;

import dev.minecraftempires.MinecraftEmpires;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Small diagnostic command for the data-only remote simulation milestone. */
@Mod.EventBusSubscriber(modid = MinecraftEmpires.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SimulationCommands {
    private SimulationCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(final RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("empires")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("debug")
                                .then(Commands.literal("simulation")
                                        .executes(context -> {
                                            final String status = SimulationScheduler.describeStatus(
                                                    context.getSource().getServer());
                                            context.getSource().sendSuccess(() -> Component.literal(status), false);
                                            return 1;
                                        })))
        );
    }
}
