package dev.reme.worldseam;

import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

/** Real foreign ServerLevel entity snapshots -> native client-side Entity
 * instances inside the actual second ClientLevel. Client render mirrors are
 * NOT new server entities, physics bodies, or fake mesh animations.
 * Limited vanilla tracking: metadata, equipment, mounts, modded entities,
 * Sable sublevels and full vanilla packet semantics remain unimplemented. */
final class SeamRemoteEntityClient {
    private static final Logger LOG=LogUtils.getLogger();
    private static ClientLevel owner;
    private static String scope="";
    private static final Map<Integer,UUID> tracked=new HashMap<>();
    private static long created,updated,rejected;
    private SeamRemoteEntityClient(){}

    static void accept(SeamRemoteEntityPayload packet) {
        ClientLevel remote=SeamClientWorldPool.remoteFor(
            packet.source(),packet.target());
        if(remote==null)return;
        String next=packet.source()+">"+packet.target();
        if(remote!=owner||!next.equals(scope)){
            reset();
            owner=remote;
            scope=next;
        }
        var seen=new HashSet<Integer>();
        for(var s:packet.entities()){
            if(s.id()<0||!Double.isFinite(s.x())||
               !Double.isFinite(s.y())||!Double.isFinite(s.z())||
               !Float.isFinite(s.yaw())||!Float.isFinite(s.pitch())||
               s.y()<remote.getMinBuildHeight()-8||
               s.y()>remote.getMaxBuildHeight()+8||
               !"minecraft".equals(s.type().getNamespace())||
               !remote.getChunkSource().hasChunk(
                   Math.floorDiv((int)Math.floor(s.x()),16),
                   Math.floorDiv((int)Math.floor(s.z()),16))){
                rejected++;continue;
            }
            Entity e=remote.getEntity(s.id());
            UUID existing=tracked.get(s.id());
            if(existing!=null&&!existing.equals(s.uuid())){
                // Never remove an entity we did not create for this scope.
                if(e!=null&&e.getUUID().equals(existing))
                    remote.removeEntity(s.id(),Entity.RemovalReason.DISCARDED);
                tracked.remove(s.id());
                e=remote.getEntity(s.id());
            }
            if(e!=null && !s.uuid().equals(e.getUUID())){
                rejected++;continue; // vanilla tracking owns this ID
            }
            if(e==null){
                var type=BuiltInRegistries.ENTITY_TYPE.getOptional(s.type())
                    .orElse(null);
                if(type==null){rejected++;continue;}
                e=type.create(remote);
                if(e==null){rejected++;continue;}
                e.setId(s.id());
                e.setUUID(s.uuid());
                e.moveTo(s.x(),s.y(),s.z(),s.yaw(),s.pitch());
                e.setNoGravity(s.noGravity());
                remote.addEntity(e);
                tracked.put(s.id(),s.uuid());
                created++;
            } else if(!s.uuid().equals(tracked.get(s.id()))){
                // Existing native client entity was not ours to mutate.
                rejected++;continue;
            } else {
                e.xo=e.getX();e.yo=e.getY();e.zo=e.getZ();
                e.setPos(s.x(),s.y(),s.z());
                e.setYRot(s.yaw());e.setXRot(s.pitch());
                e.setNoGravity(s.noGravity());
                updated++;
            }
            seen.add(s.id());
        }
        for(var it=tracked.entrySet().iterator();it.hasNext();){
            var item=it.next();
            if(seen.contains(item.getKey()))continue;
            Entity e=remote.getEntity(item.getKey());
            if(e!=null&&e.getUUID().equals(item.getValue()))
                remote.removeEntity(item.getKey(),Entity.RemovalReason.DISCARDED);
            it.remove();
        }
        if(created>0 && created%16==1)
            LOG.info("WORLDSEAM_NATIVE_REMOTE_ENTITY_CLIENT: realSecondClientLevel={}, tracked={}, created={}, updated={}, rejected={}",
                packet.target(),tracked.size(),created,updated,rejected);
    }
    /**
     * Retire ONLY our client-side snapshot mirrors before a foreign
     * ClientLevel becomes the active vanilla world. The server will then
     * supply its authoritative normal entity spawn/tracking packets.
     * Leaving these mirrors in the promoted world makes vanilla's real
     * spawn of the SAME UUID conflict with a stale snapshot entity ID;
     * 240-handoff logs demonstrated repeated Duplicate entity UUID warnings.
     *
     * This never removes an entity unless both our tracked ID and UUID
     * still match. It never changes a ServerLevel, Sable rigid body, or
     * another mod's client entity.
     */
    static void retireBeforePromotion(ClientLevel promoted) {
        if(owner!=promoted)return;
        int retired=0;
        for(var item:tracked.entrySet()){
            Entity e=promoted.getEntity(item.getKey());
            if(e!=null && e.getUUID().equals(item.getValue())){
                promoted.removeEntity(item.getKey(),Entity.RemovalReason.DISCARDED);
                retired++;
            }
        }
        tracked.clear();
        owner=null;
        scope="";
        if(retired>0)
            LOG.info("WORLDSEAM_REMOTE_ENTITY_PROMOTION: retiredMirrors={}, nativeVanillaTrackingPending=true, clonedServerEntities=0",
                retired);
    }

    static void reset(){
        // A previously remote world can become the active world at native
        // handoff. Do not destroy its entities after promotion.
        if(owner!=null&&owner!=Minecraft.getInstance().level){
            for(var item:tracked.entrySet()){
                Entity e=owner.getEntity(item.getKey());
                if(e!=null&&e.getUUID().equals(item.getValue()))
                    owner.removeEntity(item.getKey(),Entity.RemovalReason.DISCARDED);
            }
        }
        tracked.clear();owner=null;scope="";
    }
    static int tracked(){return tracked.size();}
}
