package dev.reme.worldseam;

import dev.reme.worldseam.mixin.SeamEntityAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Experimental NO-RESPAWN server-side cross-dimensional switch.
 *
 * Call only after valid player-owned ready handshake and destination
 * collision checks. Avoids ServerPlayer.teleportTo because it sends vanilla
 * ClientboundRespawnPacket, which rebuilds the client world.
 */
public final class SeamDirectTransfer {
    private SeamDirectTransfer() {}

    public static void move(ServerPlayer player, ServerLevel target,
                            Vec3 pos, float yaw, float pitch) {
        ServerLevel from = player.serverLevel();
        String original = from.dimension().location().toString();
        String destination = target.dimension().location().toString();

        from.removePlayerImmediately(player, Entity.RemovalReason.CHANGED_DIMENSION);
        ((SeamEntityAccess) player).worldseam$unsetRemoved();
        player.setServerLevel(target);
        player.gameMode.setLevel(target);
        player.setPos(pos.x(),pos.y(),pos.z());
        player.setYRot(yaw);
        player.setXRot(pitch);

        // PACKET ORDER IS CRITICAL: registering the player with the target
        // ServerLevel can itself begin entity tracking, before the explicit
        // ServerChunkCache.move() below. In the 2026-10-25 exact-head 240
        // handoff JVM log, vanilla AddEntity packets were received before
        // WorldSeam retired foreign ClientLevel mirrors at promotion; 66
        // Duplicate entity UUID warnings remained. Send the handoff BEFORE
        // target.addDuringTeleport(), not merely before move(). The client
        // must switch its packet listener to the prewarmed target world before
        // any target-world entity/chunk tracking packet can be enqueued.
        // The server-side player already points to target at this point.
        // PACKET ORDER IS CRITICAL: the new ServerChunkCache may synchronously
        // enqueue normal ClientboundAddEntity/Chunk/Light packets as soon as
        // it begins tracking the player. Those belong to the TARGET world,
        // never the old ClientPacketListener.level. Notify the client FIRST,
        // on the same connection, to switch to the already prewarmed world.
        PacketDistributor.sendToPlayer(player,new SeamHandoffPayload(
            original,destination,pos.x(),pos.y(),pos.z(),yaw,pitch
        ));
        // Register the authoritative player in the destination only AFTER
        // the handoff packet is queued on this same connection. Both the
        // entity manager and ServerChunkCache may start vanilla tracking.
        target.addDuringTeleport(player);
        // Now trigger VANILLA chunk/entity tracking. Reversing these two
        // sends puts freshly spawned Nether entities into the Overworld
        // client registry and can cause disappearing mobs or crashes.
        target.getChunkSource().move(player);
        player.connection.teleport(pos.x(),pos.y(),pos.z(),yaw,pitch);
        player.connection.resetPosition();
        SeamReadiness.invalidate(player.getUUID());
    }
}
