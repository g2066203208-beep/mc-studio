package dev.reme.mcstudio.climb;

import com.mojang.blaze3d.platform.InputConstants;
import dev.reme.mcstudio.StudioMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** V2: click once to grip, click again to release; G = emergency release. */
@EventBusSubscriber(modid = StudioMod.MOD_ID, value = Dist.CLIENT)
public final class ClientClimbControls {
    private static final KeyMapping RELEASE = new KeyMapping("key.reme_studio.release",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.reme_studio");
    private static int sequence;
    private static boolean leftUsingItem;

    private ClientClimbControls() {}
    public static void registerKeys(RegisterKeyMappingsEvent event) { event.register(RELEASE); }

    @SubscribeEvent
    public static void mouse(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.level == null) return;
        int button = event.getButton();
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return;
        ClimbState state = ClimbState.of(mc.player);
        boolean pressed = event.getAction() == GLFW.GLFW_PRESS;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (event.getAction() == GLFW.GLFW_RELEASE && leftUsingItem) {
                leftUsingItem = false;
                sendItem(false);
                event.setCanceled(true);
                return;
            }
            if (!pressed) return;
            if (state.right != null && state.left == null && !mc.player.getOffhandItem().isEmpty()) {
                leftUsingItem = true;
                sendItem(true);
                mc.options.keyAttack.setDown(false);
                event.setCanceled(true);
                return;
            }
            if (state.left != null) {
                toggle(0);
                mc.options.keyAttack.setDown(false);
                event.setCanceled(true);
                return;
            }
            if (mc.player.getOffhandItem().isEmpty() && canGrip(mc, SurfaceProbe.trace(mc.player))) {
                toggle(0);
                mc.options.keyAttack.setDown(false);
                event.setCanceled(true);
            }
        } else if (pressed) {
            if (state.right != null) {
                toggle(1);
                mc.options.keyUse.setDown(false);
                event.setCanceled(true);
                return;
            }
            if (mc.player.getMainHandItem().isEmpty() && canGrip(mc, SurfaceProbe.trace(mc.player))) {
                toggle(1);
                mc.options.keyUse.setDown(false);
                event.setCanceled(true);
            }
        }
    }

    // Grab the floor only deliberately: crouched or already airborne.
    private static boolean canGrip(Minecraft mc, GripAnchor anchor) {
        if (anchor == null) return false;
        GripAnchor.Contact c = anchor.resolve(mc.level);
        return c != null && (c.normal().y < 0.65 || mc.player.isShiftKeyDown() || !mc.player.onGround());
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) { leftUsingItem = false; return; }
        if (leftUsingItem && (mc.screen != null || !mc.player.isAlive()
                || GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(), GLFW.GLFW_FOCUSED) == GLFW.GLFW_FALSE)) {
            leftUsingItem = false;
            sendItem(false);
        }
        if (mc.screen != null) return;
        while (RELEASE.consumeClick()) {
            if (leftUsingItem) { leftUsingItem = false; sendItem(false); }
            int id = ++sequence;
            ClimbRuntime.accept(mc.player, id, -1, false, false, 0, 0);
            PacketDistributor.sendToServer(new ClimbNetwork.Intent(id, -1, false, false, 0, 0));
            mc.player.displayClientMessage(Component.translatable("message.reme_studio.released"), true);
        }
        ClimbState state = ClimbState.of(mc.player);
        if (state.gripping() || leftUsingItem) {
            if (state.right != null) mc.options.keyUse.setDown(false);
            if (leftUsingItem) mc.options.keyAttack.setDown(false);
            sendGrip(-1, false, true);
        }
    }

    private static void toggle(int hand) { sendGrip(hand, true, true); }

    private static void sendGrip(int hand, boolean down, boolean mode) {
        Minecraft mc = Minecraft.getInstance();
        int id = ++sequence;
        float forward = (mc.options.keyUp.isDown()?1:0)-(mc.options.keyDown.isDown()?1:0);
        float strafe = (mc.options.keyRight.isDown()?1:0)-(mc.options.keyLeft.isDown()?1:0);
        ClimbRuntime.accept(mc.player, id, hand, down, mode, forward, strafe);
        PacketDistributor.sendToServer(new ClimbNetwork.Intent(id, hand, down, mode, forward, strafe));
    }

    private static void sendItem(boolean down) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (down && !mc.player.getOffhandItem().isEmpty()) {
            mc.player.startUsingItem(InteractionHand.OFF_HAND);
        } else if (!down && mc.player.isUsingItem() && mc.player.getUsedItemHand() == InteractionHand.OFF_HAND) {
            mc.player.releaseUsingItem();
        }
        PacketDistributor.sendToServer(new ClimbNetwork.OffhandIntent(++sequence, down));
    }
}
