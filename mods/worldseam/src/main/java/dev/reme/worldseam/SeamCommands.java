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
                .then(Commands.literal("explosions")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.literal("enable").executes(ctx -> explosions(ctx,true)))
                    .then(Commands.literal("disable").executes(ctx -> explosions(ctx,false))))
                .then(Commands.literal("terrain")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.literal("enable").executes(ctx -> terrain(ctx,true)))
                    .then(Commands.literal("disable").executes(ctx -> terrain(ctx,false))))
                .then(Commands.literal("ships")
                    .then(Commands.literal("status").executes(SeamCommands::shipsStatus)))
                .then(Commands.literal("probe").executes(SeamCommands::probe))
                .then(Commands.literal("trace").executes(SeamCommands::trace)));
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
                + " | cross-seam explosions: " + (config.crossSeamExplosions() ? "ON" : "OFF")
                + " | dimension edges: " + config.stack().links().size()
                + " | stack layers: " + config.stack().entries().size() + " | rendering: near-border snapshot prototype (not full world renderer)"
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

    private static int explosions(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        var src=ctx.getSource();
        SeamSettings.get(src.getServer()).setCrossSeamExplosions(enabled);
        src.sendSuccess(() -> Component.literal(
            "WorldSeam native cross-seam blasts " + (enabled ? "ENABLED" : "DISABLED")
            + ". Only sealed-bedrock-free openings; both worlds must be loaded."
            + " Destructive across dimensions. Back up the test world first."
        ),true);
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

    /**
     * Real runtime status, not just a claim that the mod is installed.
     * Does not force-load remote chunks or edit terrain.
     */
    private static int trace(CommandContext<CommandSourceStack> ctx)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var src=ctx.getSource();
        var player=src.getPlayerOrException();
        var level=player.serverLevel();
        var cfg=SeamSettings.get(src.getServer());
        String name=level.dimension().location().toString();
        int min=SeamStackRuntime.bottom(level),top=SeamStackRuntime.top(level);
        src.sendSuccess(() -> Component.literal(
            "[WorldSeam] enabled="+cfg.enabled()+
            ", smooth="+cfg.experimentalNoRespawn()+
            ", carveNew="+cfg.carveNewChunks()+
            ", dimension="+name+
            ", y="+String.format(java.util.Locale.ROOT,"%.2f",player.getY())+
            ", build=[%d,%d)".formatted(min,level.getMaxBuildHeight())
        ),false);
        for(var link:SeamStackRuntime.from(src.getServer(),name)) {
            double boundary=SeamGraph.boundaryY(link.exit(),min,top);
            double distance=Math.abs(player.getY()-boundary);
            var dest=src.getServer().getLevel(SeamServer.dimensionKey(link.target()));
            int chunks=0;
            if(dest!=null) {
                int cx=Math.floorDiv(player.blockPosition().getX(),16);
                int cz=Math.floorDiv(player.blockPosition().getZ(),16);
                for(int dx=-1;dx<=1;dx++)
                    for(int dz=-1;dz<=1;dz++)
                        if(dest.hasChunk(cx+dx,cz+dz))chunks++;
            }
            boolean ready=SeamReadiness.isReady(player.getUUID(),name,
                link.target(),src.getServer().overworld().getGameTime());
            final int loaded=chunks;
            src.sendSuccess(() -> Component.literal(
                "[WorldSeam] "+link.exit()+" edge Y="+boundary+
                " -> "+link.target()+" "+link.entry()+
                ", distance="+String.format(java.util.Locale.ROOT,"%.2f",distance)+
                ", remote server chunks="+loaded+"/9"+
                ", readiness="+ready+
                ", within chunk preload radius="+(distance<=64)+
                ", within near preview radius="+(distance<=32)
            ),false);
        }
        return 1;
    }

    private static int shipsStatus(CommandContext<CommandSourceStack> ctx) {
        var src=ctx.getSource();
        var s=SeamSableObserver.snapshot();
        String error=SeamSableObserver.problem();
        src.sendSuccess(() -> Component.literal(
            "[WorldSeam ships] Sable installed="+s.installed()+
            ", API compatible="+s.compatible()+
            ", loaded native hulls="+s.liveBodies()+
            ", straddling two charts="+s.straddlingBodies()+
            ", remote ship chunk tickets="+SeamShipResidency.active()+
            ", ticketAdds="+SeamShipResidency.added()+
            ", ticketReleases="+SeamShipResidency.removed()+
            ", rejectedByLimit="+SeamShipResidency.rejected()+
            ", remoteVoxelCandidates(BROAD_PHASE_ONLY)="+
                SeamSableObserver.remoteCollisionCandidates()+
            ", remoteVoxelsNotLoaded="+
                SeamSableObserver.remoteUnloadedBlocks()+
            ", remoteProbesTruncated="+
                SeamSableObserver.remoteProbesTruncated()+
            ", physical image-collider bridge=NOT IMPLEMENTED"+
            ", sample="+s.sampleShip()+
            (error.isBlank()?"":", probe error="+error)
        ),false);
        return 1;
    }

    private static int probe(CommandContext<CommandSourceStack> ctx) {
        var src = ctx.getSource();
        if (!(src.getLevel() instanceof ServerLevel level)) {
            return 0;
        }
        var connections = SeamStackRuntime.from(src.getServer(),level.dimension().location().toString());
        for (SeamGraph.Link link : connections) {
            double sourceY = SeamGraph.boundaryY(link.exit(),
                SeamStackRuntime.bottom(level), SeamStackRuntime.top(level));
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
