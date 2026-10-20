package dev.reme.mcstudio.climb;

import dev.reme.mcstudio.StudioMod;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** PEAK: HOLD LMB near a solid face, use WASD, Shift=lunge, release LMB to fall. */
@EventBusSubscriber(modid = StudioMod.MOD_ID, value = Dist.CLIENT)
public final class ClientClimbControls {
    private static boolean grabbing;
    private static int sequence;
    private static long lastSendTick = Long.MIN_VALUE;
    private ClientClimbControls() {}

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        // No mandatory mode key: normal Minecraft controls remain as they are.
    }

    @SubscribeEvent
    public static void mouse(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) return;
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        if (event.getAction() == GLFW.GLFW_RELEASE && grabbing) {
            grabbing = false;
            send(false);
            mc.options.keyAttack.setDown(false);
            event.setCanceled(true);
        } else if (event.getAction() == GLFW.GLFW_PRESS && !grabbing) {
            if (!mc.player.getMainHandItem().isEmpty()) return;
            if (!PeakGripRules.mayGrab(mc.player, SurfaceProbe.trace(mc.player))) return;
            grabbing = true;
            send(true);
            mc.options.keyAttack.setDown(false);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) { grabbing = false; return; }
        boolean lostFocus = GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(), GLFW.GLFW_FOCUSED) == GLFW.GLFW_FALSE;
        if (grabbing && (mc.screen != null || lostFocus || !mc.player.isAlive()
                || mc.player.isPassenger() || mc.player.getAbilities().flying
                || !GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT)
                     == GLFW.GLFW_PRESS)) {
            grabbing = false;
            send(false);
        }
        if (grabbing && mc.screen == null) {
            mc.options.keyAttack.setDown(false);
            if (mc.level.getGameTime() != lastSendTick) {
                send(true);
                lastSendTick = mc.level.getGameTime();
            }
        }
    }

    private static void send(boolean pressed) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        float f = pressed ? (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0) : 0;
        float s = pressed ? (mc.options.keyRight.isDown() ? 1 : 0) - (mc.options.keyLeft.isDown() ? 1 : 0) : 0;
        boolean lunge = pressed && mc.options.keyShift.isDown();
        int n = ++sequence;
        ClimbRuntime.accept(mc.player, n, -1, pressed, lunge, f, s);
        PacketDistributor.sendToServer(new ClimbNetwork.Intent(n, -1, pressed, lunge, f, s));
    }
}
