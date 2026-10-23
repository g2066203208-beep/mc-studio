package dev.reme.worldseam;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

/** Foreign-dimension tracking from actual server entity instances.
 * No synthetic entity is spawned on the server. Never copies Rapier bodies.
 * Vanilla entity metadata/equipment and Sable sublevels are NOT yet relayed. */
final class SeamRemoteEntitySender {
    private static final Logger LOG=LogUtils.getLogger();
    private SeamRemoteEntitySender(){}
    static void tick(ServerPlayer viewer,String source,SeamGraph.Link link,
                     ServerLevel target,long tick) {
        if(tick%5!=0)return;
        double boundary=SeamStackRuntime.boundary(target,link.entry());
        boolean top=link.entry()==SeamGraph.EdgeSide.TOP;
        double minY=top?boundary-96:boundary;
        double maxY=top?boundary:boundary+96;
        double x=viewer.getX(),z=viewer.getZ();
        if(!target.hasChunk(Math.floorDiv((int)Math.floor(x),16),
                            Math.floorDiv((int)Math.floor(z),16)))return;
        var box=new AABB(x-48,minY,z-48,x+48,maxY,z+48);
        var found=target.getEntities((Entity)null,box,
            e->e.isAlive() && !(e instanceof ServerPlayer));
        var snapshots=new ArrayList<SeamRemoteEntityPayload.Snapshot>();
        for(Entity e:found) {
            var id=BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
            // Do not fabricate incomplete modded entity or Sable sublevel
            // tracking from an ordinary vanilla spawn snapshot.
            if(id==null||!"minecraft".equals(id.getNamespace())||
               !SeamRemoteEntityWindow.contains(e.getX(),e.getZ(),
                   e.getBoundingBox().minY,e.getBoundingBox().maxY,
                   x,z,boundary,top))continue;
            snapshots.add(new SeamRemoteEntityPayload.Snapshot(
                e.getId(),e.getUUID(),id,e.getX(),e.getY(),e.getZ(),
                e.getYRot(),e.getXRot(),e.isNoGravity()));
            if(snapshots.size()==SeamRemoteEntityPayload.MAX_ENTITIES)break;
        }
        PacketDistributor.sendToPlayer(viewer,
            new SeamRemoteEntityPayload(source,link.target(),snapshots));
        if(tick%100==0 && !snapshots.isEmpty())
            LOG.info("WORLDSEAM_NATIVE_REMOTE_ENTITY_SERVER: source={}, target={}, actualEntities={}, sampleUUID={}",
                source,link.target(),snapshots.size(),snapshots.get(0).uuid());
    }
}
