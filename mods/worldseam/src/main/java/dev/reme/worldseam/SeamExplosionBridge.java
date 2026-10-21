package dev.reme.worldseam;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import org.slf4j.Logger;

/**
 * Opt-in server-authoritative cross-seam blasts. A sphere that intersects an
 * open vertical seam affects both native ServerLevels, including block damage
 * and entities. We call the destination's VANILLA explosion implementation
 * rather than copying blocks or synthetically applying damage.
 *
 * This is not a complete cross-world physics solver: ray travel through
 * adjacent solid obstacles and ownership attribution from custom mod
 * explosives still require specialized hooks.
 */
public final class SeamExplosionBridge {
    private static final Logger LOG = LogUtils.getLogger();
    private static boolean forwarding;

    private SeamExplosionBridge() {}

    public static void onDetonate(ExplosionEvent.Detonate event) {
        if (forwarding || !(event.getLevel() instanceof ServerLevel source)) return;
        MinecraftServer server=source.getServer();
        if (server==null) return;
        SeamSettings cfg=SeamSettings.get(server);
        if (!cfg.enabled() || !cfg.crossSeamExplosions()) return;

        Explosion explosion=event.getExplosion();
        Vec3 center=explosion.center();
        float radius=explosion.radius();
        if (!Float.isFinite(radius) || radius<=0 ||
            radius>SeamBlastGeometry.MAX_RADIUS) {
            // Oversized blasts require a separate bounded simulation
            // instead of silently claiming arbitrary mod TNT compatibility.
            return;
        }
        int centerX=(int)Math.floor(center.x), centerZ=(int)Math.floor(center.z);
        String sourceId=source.dimension().location().toString();
        for(SeamGraph.Link link:SeamStackRuntime.from(server,sourceId)) {
            int fromY=SeamStackRuntime.boundary(source,link.exit());
            ServerLevel target=server.getLevel(SeamServer.dimensionKey(link.target()));
            if(target==null || target==source)continue;
            int toY=SeamStackRuntime.boundary(target,link.entry());
            SeamBlastGeometry.Plan plan=SeamBlastGeometry.map(
                center.y,fromY,toY,radius);
            if(plan==null) continue;

            // A bedrock-sealed seam must not leak a blast to a different
            // dimension. The source-side contact cell must be open.
            int exposedY=link.exit()==SeamGraph.EdgeSide.TOP?fromY-1:fromY;
            BlockPos opening=new BlockPos(centerX,exposedY,centerZ);
            if(!source.hasChunk(centerX>>4,centerZ>>4) ||
                !source.getBlockState(opening).isAir())continue;

            // A new explosion may examine up to one neighboring chunk on
            // each side in X/Z. Never synchronously generate remote chunks
            // just because somebody exploded TNT in an unloaded dimension.
            if(!loadedWindow(target,centerX>>4,centerZ>>4))continue;
            int safeY=(int)Math.max(target.getMinBuildHeight(),
                Math.min(target.getMaxBuildHeight()-1,Math.floor(plan.mappedY())));
            if(!target.getWorldBorder().isWithinBounds(
                    new BlockPos(centerX,safeY,centerZ)))continue;

            // Preserve the source's non-destructive explosions, and the
            // destination world's TNT/mob griefing rules. The original
            // source entity belongs to a DIFFERENT ServerLevel, so it must
            // never be passed as an entity reference to target.explode.
            Level.ExplosionInteraction interaction;
            if(!explosion.interactsWithBlocks())
                interaction=Level.ExplosionInteraction.NONE;
            else if(explosion.getDirectSourceEntity() instanceof PrimedTnt)
                interaction=Level.ExplosionInteraction.TNT;
            else if(explosion.getDirectSourceEntity() instanceof Mob)
                interaction=Level.ExplosionInteraction.MOB;
            else
                interaction=Level.ExplosionInteraction.BLOCK;
            forwarding=true;
            try {
                target.explode(null,center.x,plan.mappedY(),center.z,
                    plan.radius(),false,interaction);
                LOG.info("WORLDSEAM_CROSS_BLAST: {} -> {}, center=({}, {}, {}), mappedY={}, radius={}",
                    sourceId,link.target(),center.x,center.y,center.z,
                    plan.mappedY(),plan.radius());
            } finally {
                forwarding=false;
            }
        }
    }

    private static boolean loadedWindow(ServerLevel world,int cx,int cz) {
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)
            if(!world.hasChunk(cx+x,cz+z))return false;
        return true;
    }
}
