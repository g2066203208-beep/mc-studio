package dev.reme.mcstudio.npc.client;

import dev.reme.mcstudio.npc.NpcPose;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.neoforged.fml.ModList;

/**
 * Faithful 6-cuboid / 64x64 Steve geometry with additive *NPC-native*
 * expressive acting. No player-only PAL mixin assumed: our residents are
 * PathfinderMob rendered using PlayerModel, not AbstractClientPlayer.
 *
 * Follow vanilla walking/swimming/turning first, then apply small actor poses.
 * Identical pivots for skin's outer layer so hats, sleeves, jacket and pants
 * never detach from their underlay while waving/carrying/work-posing.
 *
 * Entirely visual. Server sends a discrete NpcPose, not each joint frame.
 */
public final class LivingNpcPlayerModel extends PlayerModel<PlayerNpcEntity> {
    public LivingNpcPlayerModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    @Override
    public void setupAnim(PlayerNpcEntity npc, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(npc, limbSwing, limbSwingAmount,
            ageInTicks, netHeadYaw, headPitch);

        NpcPose pose = npc.visualPose();
        // Breathing and glance: intentionally smaller than a single cube face,
        // not rubber limbs or anime proportions.
        float breath = (float) Math.sin(ageInTicks * 0.09F);
        float step = (float) Math.sin(ageInTicks * 0.25F);
        boolean grounded = npc.onGround() && !npc.isPassenger();
        boolean moving = npc.getDeltaMovement().horizontalDistanceSqr() > 0.0025;
        if (grounded && !moving && pose != NpcPose.REST) {
            this.body.xRot += breath * 0.012F;
            this.head.yRot += step * 0.035F;
        }
        // Exact same PlayerAnimator JSON keyframes as real players when present.
        // Vanilla fallback remains playable without the optional library.
        if (!ModList.get().isLoaded("playeranimator")) switch (pose) {
            case SOCIALIZE -> {
                // Brief greeting, soft head nod; retain default head tracking.
                this.rightArm.xRot -= 0.58F + step * 0.10F;
                this.rightArm.zRot += 0.12F + breath * 0.15F;
                this.head.xRot += breath * 0.035F;
            }
            case WORK -> {
                // Controlled short work stroke synchronized locally; do not
                // turn an iron pickaxe into an anatomically impossible spin.
                this.rightArm.xRot -= 0.55F + step * 0.36F;
                this.leftArm.xRot -= 0.12F;
                this.head.xRot += 0.10F;
            }
            case CARRY -> {
                // Holding hands nearer the body signals weight WITHOUT
                // pretending there is a rendered non-existent chest/item.
                this.leftArm.xRot -= 0.40F;
                this.rightArm.xRot -= 0.40F;
                this.leftArm.zRot -= 0.07F;
                this.rightArm.zRot += 0.07F;
                this.body.xRot += 0.05F;
            }
            case GUARD -> {
                // Alert/chest square, weapon-ready right hand.
                this.rightArm.xRot -= 0.42F;
                this.leftArm.xRot -= 0.20F;
                this.head.xRot -= 0.04F;
            }
            case AFRAID -> {
                // Flinch and tense shoulders; face/player awareness remains
                // vanilla. Fear never changes actual movement hitboxes.
                this.body.xRot += 0.16F;
                this.head.xRot += 0.10F;
                this.leftArm.xRot -= 0.27F;
                this.rightArm.xRot -= 0.27F;
            }
            case REST -> {
                this.head.xRot += 0.19F;
                this.body.xRot += 0.08F;
                this.leftArm.xRot += 0.10F;
                this.rightArm.xRot += 0.10F;
            }
            default -> { }
        }
        if (ModList.get().isLoaded("playeranimator"))
            KosmXNpcAnimation.apply(npc, this, ageInTicks - (float)Math.floor(ageInTicks));

        // Vanilla PlayerModel copies these BEFORE returning from its own
        // setupAnim, therefore we MUST copy again after applying our pose.
        // This is key for a true 64x64 second layer to follow limb pivots.
        this.hat.copyFrom(this.head);
        this.jacket.copyFrom(this.body);
        this.leftSleeve.copyFrom(this.leftArm);
        this.rightSleeve.copyFrom(this.rightArm);
        this.leftPants.copyFrom(this.leftLeg);
        this.rightPants.copyFrom(this.rightLeg);
    }
}
