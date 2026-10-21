package dev.reme.worldseam;

import net.neoforged.neoforge.event.server.ServerStartingEvent;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One-shot transfer from the Create World GUI to the integrated server.
 * No global default rewrite, no changes to unrelated saved worlds or
 * dedicated servers. Mixin on CreateWorldScreen.onCreate arms it only on
 * the ACTUAL Create World button, not on returning from the editor.
 */
public final class SeamPendingWorldPreset {
    private static final AtomicReference<SeamStack> pending=new AtomicReference<>();
    private SeamPendingWorldPreset() {}
    public static void queue(SeamStack preset) { pending.set(preset); }
    public static void apply(ServerStartingEvent e) {
        SeamStack requested=pending.getAndSet(null);
        if(requested==null || e.getServer().isDedicatedServer())return;
        SeamSettings settings=SeamSettings.get(e.getServer());
        if(settings.stackChosenByUser())return;
        try {
            settings.setStack(requested,e.getServer());
            settings.setEnabled(true);
            settings.setCarveNewChunks(true);
            com.mojang.logging.LogUtils.getLogger().info(
                "WorldSeam applied Create World stack: {}",requested.entries());
        } catch(IllegalArgumentException ex) {
            settings.setEnabled(false);
            com.mojang.logging.LogUtils.getLogger().error(
                "Rejected dimension stack in new save: {}",ex.getMessage());
        }
    }
}
