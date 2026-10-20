package dev.reme.mcstudio.mixin;

import dev.reme.mcstudio.climb.PeakVisuals;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Third-person PEAK arm-over-arm animation on the vanilla player model.
 * No extra geometry, skeleton library or copyrighted game assets.
 * Inspired by ParCool's setupAnim-tail approach, implemented independently.
 */
@Mixin(PlayerModel.class)
public abstract class PeakPlayerModelMixin {
    @Inject(method="setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at=@At("TAIL"))
    private void peak$animate(LivingEntity entity, float limbSwing, float limbSwingAmount,
                              float ageInTicks, float headYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof Player player)) return;
        PeakVisuals.Pose p = PeakVisuals.pose(player);
        if (p == null || !p.climbing()) return;

        PlayerModel<?> model = (PlayerModel<?>)(Object)this;
        float stride = (float)Math.sin(p.phase() * 7f);
        float leftLift = Math.max(0f, stride);
        float rightLift = Math.max(0f, -stride);
        model.leftArm.xRot = -2.55f + 0.55f * leftLift;
        model.rightArm.xRot = -2.55f + 0.55f * rightLift;
        model.leftArm.zRot = -0.18f - 0.13f * leftLift;
        model.rightArm.zRot = 0.18f + 0.13f * rightLift;
        model.leftLeg.xRot = 0.24f + 0.29f * rightLift;
        model.rightLeg.xRot = 0.24f + 0.29f * leftLift;
        model.body.xRot = 0.18f;
        model.jacket.copyFrom(model.body);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftPants.copyFrom(model.leftLeg);
        model.rightPants.copyFrom(model.rightLeg);
    }
}
