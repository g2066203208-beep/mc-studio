package dev.reme.worldseam;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;

/** Server-thread snapshot transmission; no stale stack across saved worlds. */
public final class SeamStackSync {
    private static MinecraftServer active;
    private static final Map<UUID,Long> lastRevision=new HashMap<>();
    private SeamStackSync() {}
    public static void tick(MinecraftServer server, ServerPlayer player) {
        if(active!=server){active=server;lastRevision.clear();}
        SeamSettings settings=SeamSettings.get(server);
        long version=settings.stackRevision();
        if(lastRevision.getOrDefault(player.getUUID(),-1L)==version)return;
        var list=new ArrayList<SeamStackSyncPayload.DimensionTypeRef>();
        for(SeamStack.Entry e:settings.stack().entries()) {
            var level=server.getLevel(
                net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION,
                    net.minecraft.resources.ResourceLocation.parse(e.id())));
            if(level!=null) {
                // DimensionType registry key can differ from Level ID.
                var key=level.dimensionTypeRegistration().unwrapKey();
                list.add(new SeamStackSyncPayload.DimensionTypeRef(e.id(),
                    key.map(k->k.location().toString()).orElse(e.id()),
                    level.getMinBuildHeight(),level.getMaxBuildHeight()));
            }
        }
        PacketDistributor.sendToPlayer(player,
            new SeamStackSyncPayload(settings.stack(),version,List.copyOf(list)));
        lastRevision.put(player.getUUID(),version);
    }
}
