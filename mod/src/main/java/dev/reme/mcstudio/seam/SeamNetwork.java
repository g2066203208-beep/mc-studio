package dev.reme.mcstudio.seam;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Own packet protocol; no API from any portal mod. */
public final class SeamNetwork {
    private SeamNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
            SeamPreviewPayload.TYPE,
            SeamPreviewPayload.CODEC,
            (message, context) -> SeamPreviewClient.accept(message)
        );
    }
}
