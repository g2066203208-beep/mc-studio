package dev.reme.mcstudio.climb;

import com.mojang.math.Axis;
import dev.reme.mcstudio.StudioMod;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/**
 * Only transforms Minecraft's existing blocky hands when they are already
 * rendered. No new models, textures or animation dependency.
 *
 * Vanilla may omit rendering an empty offhand. This class does not attempt
 * to replace the renderer or force offhand draw calls.
 */
@EventBusSubscriber(modid = StudioMod.MOD_ID, value = Dist.CLIENT)
public final class VanillaGripHands {
    private static float leftBlend;
    private static float rightBlend;
    private VanillaGripHands() {}

    @SubscribeEvent
    public static void onRender(RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            leftBlend = 0;
            rightBlend = 0;
            return;
        }

        boolean mainHandLeft = minecraft.options.mainHand().get() == HumanoidArm.LEFT;
        boolean leftHand = (event.getHand() == InteractionHand.MAIN_HAND) == mainHandLeft;
        ClimbState state = ClimbState.of(minecraft.player);
        boolean hasGrip = state.enabled && (leftHand ? state.left != null : state.right != null);
        float previous = leftHand ? leftBlend : rightBlend;
        float blend = previous + (hasGrip ? 1.0f - previous : -previous) * 0.28f;
        if (leftHand) leftBlend = blend;
        else rightBlend = blend;
        if (blend <= 0.005f) return;

        double mirror = leftHand ? -1 : 1;
        event.getPoseStack().translate(mirror * 0.07 * blend, 0.17 * blend, -0.20 * blend);
        event.getPoseStack().mulPose(Axis.XP.rotationDegrees(-25.0f * blend));
        event.getPoseStack().mulPose(Axis.ZP.rotationDegrees((float) (-mirror * 9.0 * blend)));
    }
}
