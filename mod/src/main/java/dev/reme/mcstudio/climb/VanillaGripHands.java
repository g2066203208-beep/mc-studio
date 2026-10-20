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
 * Animates actual vanilla Steve/Alex hands: extend toward the forward wall;
 * alternating reach comes from actual held-grip movement phase.
 */
@EventBusSubscriber(modid = StudioMod.MOD_ID, value = Dist.CLIENT)
public final class VanillaGripHands {
    private VanillaGripHands() {}

    @SubscribeEvent
    public static void onRender(RenderHandEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        PeakVisuals.Pose state = PeakVisuals.pose(mc.player);
        if (state == null || !state.climbing()) return;

        boolean mainLeft = mc.options.mainHand().get() == HumanoidArm.LEFT;
        boolean left = (e.getHand() == InteractionHand.MAIN_HAND) == mainLeft;
        float stride = (float) Math.sin(state.phase() * 7f + (left ? Math.PI : 0));
        float lift = 0.5f + stride * 0.5f;
        float sign = left ? -1 : 1;
        e.getPoseStack().translate(-sign * (0.14 + 0.14 * lift), 0.03 + 0.18 * lift, -0.63 - 0.19 * lift);
        e.getPoseStack().mulPose(Axis.XP.rotationDegrees(-55 - 29 * lift));
        e.getPoseStack().mulPose(Axis.YP.rotationDegrees(sign * 16));
        e.getPoseStack().mulPose(Axis.ZP.rotationDegrees(-sign * (12 + 8 * lift)));
    }
}
