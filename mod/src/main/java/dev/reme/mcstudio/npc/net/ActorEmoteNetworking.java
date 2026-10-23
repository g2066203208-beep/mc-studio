package dev.reme.mcstudio.npc.net;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.client.ClientActorEmoteHandler;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Vanilla-safe NeoForge 1.21.1 play-phase server-to-clients animation sync.
 * Never sends a gameplay teleport or changes player navigation.
 */
@EventBusSubscriber(modid=StudioMod.MOD_ID,bus=EventBusSubscriber.Bus.MOD)
public final class ActorEmoteNetworking {
    private ActorEmoteNetworking() {}
    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
            ActorEmotePacket.TYPE,
            ActorEmotePacket.STREAM_CODEC,
            ClientActorEmoteHandler::handle);
    }
}
