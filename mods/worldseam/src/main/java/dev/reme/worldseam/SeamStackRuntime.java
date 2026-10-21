package dev.reme.worldseam;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import java.util.List;

/** One authoritative per-save runtime routing source. */
public final class SeamStackRuntime {
    private SeamStackRuntime() {}
    public static SeamStack stack(MinecraftServer server) {
        return SeamSettings.get(server).stack();
    }
    public static List<SeamGraph.Link> from(MinecraftServer server,String id) {
        return stack(server).from(id);
    }
    public static int bottom(ServerLevel level) {
        return stack(level.getServer()).boundary(
            level.dimension().location().toString(),SeamGraph.EdgeSide.BOTTOM,
            level.getMinBuildHeight(),level.getMaxBuildHeight());
    }
    public static int top(ServerLevel level) {
        return stack(level.getServer()).boundary(
            level.dimension().location().toString(),SeamGraph.EdgeSide.TOP,
            level.getMinBuildHeight(),level.getMaxBuildHeight());
    }
    public static int boundary(ServerLevel level,SeamGraph.EdgeSide side) {
        return stack(level.getServer()).boundary(
            level.dimension().location().toString(),side,
            level.getMinBuildHeight(),level.getMaxBuildHeight());
    }
}
