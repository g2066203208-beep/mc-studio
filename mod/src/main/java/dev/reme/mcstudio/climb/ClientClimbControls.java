package dev.reme.mcstudio.climb;

import com.mojang.blaze3d.platform.InputConstants;
import dev.reme.mcstudio.StudioMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * G toggles climb mode. Only within climb mode do left/right mouse events
 * replace vanilla attack/use. No global remapping of normal Minecraft controls.
 */
@EventBusSubscriber(modid = StudioMod.MOD_ID, value = Dist.CLIENT)
public final class ClientClimbControls {
    private static final KeyMapping MODE = new KeyMapping("key.reme_studio.climb",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.reme_studio");
    private static boolean mode;
    private static boolean left;
    private static boolean right;
    private static int sequence = 0;

    private ClientClimbControls() {}

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(MODE);
    }

    @SubscribeEvent
    public static void mouse(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!mode || mc.player == null || mc.screen != null) return;
        int button = event.getButton();
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return;
        boolean down = event.getAction() != GLFW.GLFW_RELEASE;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (down != left) { left = down; send(0, down); }
        } else if (down != right) { right = down; send(1, down); }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) {
            if (mode) { mode = false; left = false; right = false; send(-1, false); }
            return;
        }
        while (MODE.consumeClick()) {
            mode = !mode;
            left = false;
            right = false;
            send(-1, false);
            mc.player.displayClientMessage(Component.literal(
                    mode ? "Sable Hands: CLIMB (L/R grip, G exits)" : "Sable Hands: normal controls"), true);
        }
        if (mode) {
            mc.options.keyAttack.setDown(false);
            mc.options.keyUse.setDown(false);
            send(-1, false); // heartbeats, movement inputs and server timeout protection
        }
    }

    private static void send(int hand, boolean down) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        float f = mode ? (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0) : 0;
        float s = mode ? (mc.options.keyRight.isDown() ? 1 : 0) - (mc.options.keyLeft.isDown() ? 1 : 0) : 0;
        int id = ++sequence;
        ClimbRuntime.accept(mc.player, id, hand, down, mode, f, s);
        PacketDistributor.sendToServer(new ClimbNetwork.Intent(id, hand, down, mode, f, s));
    }
}
