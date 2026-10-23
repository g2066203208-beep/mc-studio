package dev.reme.mcstudio.npc.client;

import dev.kosmx.playerAnim.api.IPlayable;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import dev.reme.mcstudio.npc.NpcPose;
import dev.reme.mcstudio.npc.SharedHumanoidMotion;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;

/**
 * Actual KosmX PlayerAnimationAccess adapter for REAL Minecraft players,
 * including other clients' visible networked vanilla actions.
 * Uses the SAME twenty-two JSON keyframe resources as KosmXNpcAnimation.
 * Idle/walk/run/attack/hurt/crouch/jump/sitting are shared by default.
 * Explicit social/carry/handhold animations need real gameplay action
 * dispatch to this public adapter; merely registering them does not make
 * two human players spontaneously hold hands.
 */
public final class KosmXPlayerAnimation {
    private KosmXPlayerAnimation() {}
    private static final int LAYER_PRIORITY=45;
    private static final Map<AbstractClientPlayer, Playback> TRACKS=new WeakHashMap<>();
    private static final class Playback {
        final ModifierLayer<IAnimation> layer=new ModifierLayer<>();
        NpcPose pose=NpcPose.IDLE;
        NpcPose requested=null;
        long requestedUntil=0L;
    }

    /** Called only on CLIENT ticks, never a server-side classloading path. */
    public static void sync(ClientLevel level) {
        long tick=level.getGameTime();
        for (AbstractClientPlayer player:level.players()) {
            Playback t=TRACKS.get(player);
            if (t==null) {
                t=new Playback();
                try {
                    PlayerAnimationAccess.getPlayerAnimLayer(player)
                        .addAnimLayer(LAYER_PRIORITY,t.layer);
                } catch (IllegalArgumentException missingMixin) {
                    // If the optional PlayerAnimator mixin fails, do not crash
                    // the actual client's world or erase vanilla animations.
                    continue;
                }
                TRACKS.put(player,t);
            }
            NpcPose pose=t.requested!=null && tick<t.requestedUntil
                ?t.requested:SharedHumanoidMotion.observePlayer(player);
            if (pose == t.pose && t.layer.getAnimation()!=null
                && t.layer.getAnimation().isActive()) continue;
            IPlayable clip=PlayerAnimationRegistry.getAnimation(
                SharedHumanoidMotion.animationId(pose));
            if (clip!=null) {
                int blend = switch(pose) {
                    case HIT, STRIKE, STUNNED -> 2;
                    case IDLE, REST, SITTING, SAD -> 7;
                    default -> 4;
                };
                t.layer.replaceAnimationWithFade(
                    AbstractFadeModifier.standardFadeIn(blend,Ease.INOUTSINE),
                    clip.playAnimation(),false);
                t.pose=pose;
            } else {
                t.layer.setAnimation(null);
                t.pose=NpcPose.IDLE;
            }
        }
    }

    /** Same authored actions available to a real player and an NPC.
     * The caller is responsible for validating a REAL action and syncing
     * it across multiplayer clients; this client method itself is visual.
     */
    public static void play(AbstractClientPlayer player,NpcPose action,int ticks) {
        if (!SharedHumanoidMotion.authoredClip(action)) return;
        Playback t=TRACKS.get(player);
        if (t==null) return;
        t.requested=action;
        t.requestedUntil=player.level().getGameTime()+Math.clamp(ticks,1,200);
        t.pose=NpcPose.IDLE; // force next client tick to swap the layer
    }
}
