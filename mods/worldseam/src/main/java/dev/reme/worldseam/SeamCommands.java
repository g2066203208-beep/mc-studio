package dev.reme.worldseam;

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
        commands.register(Commands.literal("worldseam")
                .then(Commands.literal("status").executes(SeamCommands::status))
                .then(Commands.literal("enable")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> toggle(ctx, true)))
                .then(Commands.literal("disable")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> toggle(ctx, false)))
                .then(Commands.literal("setup-test")
                    .requires(src -> src.hasPermission(4))
                    .executes(SeamCommands::setupTest))
                .then(Commands.literal("smooth")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.literal("enable").executes(ctx -> smooth(ctx,true)))
                    .then(Commands.literal("disable").executes(ctx -> smooth(ctx,false))))
                .then(Commands.literal("terrain")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.literal("enable").executes(ctx -> terrain(ctx,true)))
                    .then(Commands.literal("disable").executes(ctx -> terrain(ctx,false))))
                .then(Commands.literal("probe").executes(SeamCommands::probe)));
    }

    private static int setupTest(CommandContext<CommandSourceStack> ctx) {
        var src = ctx.getSource();
        var cfg = SeamSettings.get(src.getServer());
        cfg.setEnabled(true); // also arms the no-respawn path
        cfg.setExperimentalNoRespawn(true);
        cfg.setCarveNewChunks(true);
        int x = net.minecraft.util.Mth.floor(src.getPosition().x());
        int z = net.minecraft.util.Mth.floor(src.getPosition().z());
        int changed = SeamTerrain.openTestColumn(src.getServer(), x, z);
        src.sendSuccess(() -> Component.literal(
            "WorldSeam TEST SETUP: native no-respawn ON, future seam terrain ON."
            + " Opened 1 existing chunk column at X=" + x + " Z=" + z
            + " in Overworld/Nether; bedrock blocks removed=" + changed
            + ". THIS IS PERMANENT. Use ONLY in a disposable test world."
        ), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        var src = ctx.getSource();
        SeamSettings config = SeamSettings.get(src.getServer());
        boolean enabled = config.enabled();
        src.sendSuccess(() -> Component.literal(
            "WorldSeam: " + (enabled ? "ON" : "OFF")
                + " | experimental no-respawn: " + (config.experimentalNoRespawn() ? "ON" : "OFF")
                + " | carve new chunks: " + (config.carveNewChunks() ? "ON" : "OFF")
                + " | dimension edges: " + SeamGraph.LINKS.size()
                + " | rendering: near-border snapshot prototype (not full world renderer)"
        ), false);
        return 1;
    }

    private static int toggle(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        var src = ctx.getSource();
        SeamSettings.get(src.getServer()).setEnabled(enabled);
        src.sendSuccess(() -> Component.literal(
            "WorldSeam " + (enabled ? "enabled" : "disabled")
                + " for this saved world. Backup before experimental travel."
        ), true);
        return 1;
    }

    private static int smooth(CommandContext<CommandSourceStack> ctx, boolean v) {
        var src = ctx.getSource();
        SeamSettings.get(src.getServer()).setExperimentalNoRespawn(v);
        src.sendSuccess(() -> Component.literal(
            "EXPERIMENTAL WorldSeam direct no-respawn " + (v ? "ON" : "OFF")
            + ". PRELOADING 9 CHUNKS REQUIRED. If unready, seam will block crossing."
            + " Only use on a backed-up test world. Disable if instability occurs."
        ), true);
        return 1;
    }

    private static int terrain(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        var src = ctx.getSource();
        SeamSettings.get(src.getServer()).setCarveNewChunks(enabled);
        src.sendSuccess(() -> Component.literal(
            "New chunk seam terrain " + (enabled ? "enabled" : "disabled")
                + ". Applies only to FUTURE generated chunks while Seam is enabled."
                + " Existing world chunks are untouched; backup the test world first."
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
                level.getMinBuildHeight(), SeamBounds.top(level));
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
