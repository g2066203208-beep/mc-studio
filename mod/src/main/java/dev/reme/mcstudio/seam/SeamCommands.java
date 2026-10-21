package dev.reme.mcstudio.seam;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Explicit per-save control; never enable boundary teleport automatically. */
public final class SeamCommands {
    private SeamCommands() {}

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> commands = event.getDispatcher();
        commands.register(Commands.literal("reme")
            .then(Commands.literal("seam")
                .then(Commands.literal("status").executes(SeamCommands::status))
                .then(Commands.literal("enable")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> toggle(ctx, true)))
                .then(Commands.literal("disable")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> toggle(ctx, false)))
                .then(Commands.literal("probe").executes(SeamCommands::probe))));
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        var src = ctx.getSource();
        boolean enabled = SeamSettings.get(src.getServer()).enabled();
        src.sendSuccess(() -> Component.literal(
            "REME Native Seam: " + (enabled ? "ON" : "OFF")
                + " | dimension edges: " + SeamGraph.LINKS.size()
                + " | rendering: near-border snapshot prototype (not full world renderer)"
        ), false);
        return 1;
    }

    private static int toggle(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        var src = ctx.getSource();
        SeamSettings.get(src.getServer()).setEnabled(enabled);
        src.sendSuccess(() -> Component.literal(
            "REME Native Seam " + (enabled ? "enabled" : "disabled")
                + " for this saved world. Backup before experimental travel."
        ), true);
        return 1;
    }

    private static int probe(CommandContext<CommandSourceStack> ctx) {
        var src = ctx.getSource();
        if (!(src.getLevel() instanceof ServerLevel level)) {
            return 0;
        }
        var connections = SeamGraph.from(level.dimension().location().toString());
        for (SeamGraph.Link link : connections) {
            double sourceY = SeamGraph.boundaryY(link.exit(),
                level.getMinBuildHeight(), level.getMaxBuildHeight());
            src.sendSuccess(() -> Component.literal("Seam: " + link.exit()
                + " Y=" + sourceY + " -> " + link.target()
                + " " + link.entry()), false);
        }
        if (connections.isEmpty()) {
            src.sendFailure(Component.literal("This dimension has no vertical seams."));
            return 0;
        }
        return 1;
    }
}
