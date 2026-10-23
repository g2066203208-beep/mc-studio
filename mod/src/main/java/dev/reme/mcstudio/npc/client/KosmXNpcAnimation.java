package dev.reme.mcstudio.npc.client;

import dev.kosmx.playerAnim.api.IPlayable;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import dev.reme.mcstudio.npc.NpcPose;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import dev.reme.mcstudio.npc.SharedHumanoidMotion;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * PlayerAnimator's original 1.21.1 KEYFRAME evaluator applied to the NPC's
 * faithful six-part PlayerModel. The same ResourceLocation is played by real
 * humans using PlayerAnimationAccess; NOT an imitation NPC-only asset.
 * Never loads its optional client class when playeranimator is absent.
 */
public final class KosmXNpcAnimation {
    private KosmXNpcAnimation() {}
    private static final Map<PlayerNpcEntity, Playback> TRACKS = new WeakHashMap<>();
    private static final class Playback {
        NpcPose pose;
        IAnimation active;
        int tick;
        Playback(NpcPose pose, IAnimation active, int tick) {
            this.pose = pose;
            this.active = active;
            this.tick = tick;
        }
    }
    public static void apply(PlayerNpcEntity npc, PlayerModel<PlayerNpcEntity> model,
                             float tickDelta) {
        NpcPose pose=npc.visualPose();
        if (!SharedHumanoidMotion.authoredClip(pose)) pose=NpcPose.IDLE;
        Playback track=TRACKS.get(npc);
        if (track == null || track.pose!=pose
            || track.active == null || !track.active.isActive()) {
            IPlayable clip=PlayerAnimationRegistry.getAnimation(
                SharedHumanoidMotion.animationId(pose));
            if (clip == null) {
                TRACKS.remove(npc);
                return;
            }
            track=new Playback(pose, clip.playAnimation(), npc.tickCount);
            TRACKS.put(npc,track);
        }
        // Sample with Minecraft entity ticks rather than rendered frame count.
        // Cap catch-up when a world resumes after heavy lag or chunk unload.
        int elapsed=Math.clamp(npc.tickCount-track.tick,0,8);
        for (int i=0;i<elapsed;i++) track.active.tick();
        track.tick=npc.tickCount;
        track.active.setupAnim(tickDelta);
        rotation(model.head,"head",track.active,tickDelta);
        rotation(model.body,"torso",track.active,tickDelta);
        rotation(model.rightArm,"rightArm",track.active,tickDelta);
        rotation(model.leftArm,"leftArm",track.active,tickDelta);
        rotation(model.rightLeg,"rightLeg",track.active,tickDelta);
        rotation(model.leftLeg,"leftLeg",track.active,tickDelta);
    }
    private static void rotation(ModelPart part,String bone,IAnimation playerClip,float delta) {
        Vec3f start=new Vec3f(part.xRot,part.yRot,part.zRot);
        Vec3f value=playerClip.get3DTransform(bone,TransformType.ROTATION,delta,start);
        part.xRot=value.getX();
        part.yRot=value.getY();
        part.zRot=value.getZ();
    }
}
