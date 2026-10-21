package dev.reme.worldseam;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import java.util.Map;
import java.util.WeakHashMap;

/** Inject a real editor button into vanilla/NeoForge Create World. */
public final class SeamWorldCreation {
    private static final Map<CreateWorldScreen,SeamStack> configured=new WeakHashMap<>();
    private SeamWorldCreation() {}

    public static void install() {
        NeoForge.EVENT_BUS.addListener(SeamWorldCreation::onScreenInit);
    }

    private static void onScreenInit(ScreenEvent.Init.Post e) {
        if (!(e.getScreen() instanceof CreateWorldScreen screen))return;
        e.addListener(Button.builder(net.minecraft.network.chat.Component.literal("WorldSeam: Stack"),
            button -> Minecraft.getInstance().setScreen(
                new SeamStackScreen(screen,configured.getOrDefault(screen,SeamStack.defaults()))
            )).bounds(screen.width/2-96,screen.height-64,192,20).build());
    }

    public static void save(CreateWorldScreen screen,SeamStack stack) {
        configured.put(screen,stack);
    }

    /** Called only from actual CreateWorldScreen.onCreate, not menu browsing. */
    public static void onActualCreate(CreateWorldScreen screen) {
        SeamPendingWorldPreset.queue(configured.remove(screen));
    }
}
