package dev.reme.mcstudio.seam;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Versioned protocol. Client cache changes must run on the render/game
 * thread, not on an arbitrary network callback thread.
 */
public final class SeamNetwork {
    private SeamNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("2").playToClient(
            SeamPreviewPayload.TYPE,
            SeamPreviewPayload.CODEC,
            (message, context) ->
                context.enqueueWork(() -> SeamPreviewClient.accept(message))
        );
    }
}
