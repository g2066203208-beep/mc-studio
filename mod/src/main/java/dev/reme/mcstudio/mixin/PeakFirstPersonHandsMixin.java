package dev.reme.mcstudio.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.reme.mcstudio.climb.PeakVisuals;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import com.mojang.math.Axis;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The vanilla renderer usually skips an empty offhand. While climbing with
 * empty hands, render that existing skin-mapped vanilla arm as well.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class PeakFirstPersonHandsMixin {
    @Unique private boolean peak$renderedOffhand;

    @Shadow
    private void renderPlayerArm(PoseStack poseStack, MultiBufferSource buffer, int light,
                                 float equipProgress, float swingProgress, HumanoidArm side) {
        throw new AssertionError();
    }

    @Inject(method="renderHandsWithItems", at=@At("HEAD"))
    private void peak$begin(float partialTick, PoseStack poseStack,
                            MultiBufferSource.BufferSource buffer, LocalPlayer player, int light,
                            CallbackInfo ci) {
        peak$renderedOffhand = false;
    }

    @Inject(method="renderArmWithItem", at=@At("HEAD"))
    private void peak$noteOffhand(AbstractClientPlayer player, float partialTick, float pitch,
                                  InteractionHand hand, float swingProgress, ItemStack stack,
                                  float equipProgress, PoseStack poseStack,
                                  MultiBufferSource buffer, int light, CallbackInfo ci) {
        if (hand == InteractionHand.OFF_HAND) peak$renderedOffhand = true;
    }

    @Inject(method="renderHandsWithItems", at=@At("TAIL"))
    private void peak$drawMissingHand(float partialTick, PoseStack poseStack,
                                     MultiBufferSource.BufferSource buffer, LocalPlayer player, int light,
                                     CallbackInfo ci) {
        if (peak$renderedOffhand || !player.getOffhandItem().isEmpty()
                || !player.getMainHandItem().isEmpty()) return;
        PeakVisuals.Pose pose = PeakVisuals.pose(player);
        if (pose == null || !pose.climbing()) return;
        poseStack.pushPose();
        float stride = (float)Math.sin(pose.phase() * 7f + Math.PI);
        poseStack.translate(-0.19, 0.10 + 0.12 * stride, -0.66 - 0.13 * stride);
        poseStack.mulPose(Axis.XP.rotationDegrees(-65 - 16 * stride));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-13));
        renderPlayerArm(poseStack, buffer, light, 0, 0, player.getMainArm().getOpposite());
        poseStack.popPose();
    }
}
