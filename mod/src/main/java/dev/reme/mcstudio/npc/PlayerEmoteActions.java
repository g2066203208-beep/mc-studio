package dev.reme.mcstudio.npc;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import dev.reme.mcstudio.npc.net.ActorEmotePacket;

/** Legitimate player-invoked gestures, broadcast to the SAME shared player
 * action clips used by NPCs. Only voluntary cosmetic gestures are allowed;
 * NO fake knocked-out/arrest/fleeing actor state or speed advantage by emote.
 */
public final class PlayerEmoteActions {
    private PlayerEmoteActions() {}
    private static final int TICKS=35;
    private static final Map<UUID,Long> NEXT=new HashMap<>();
    public static NpcPose choose(String text) {
        if(text==null || text.length()>32) return null;
        try {
            NpcPose pose=NpcPose.valueOf(text.toUpperCase(Locale.ROOT));
            return permitted(pose)?pose:null;
        } catch(IllegalArgumentException invalid){ return null; }
    }
    public static boolean permitted(NpcPose pose) {
        return switch(pose) {
            case GREET, SOCIALIZE, LAUGH, SAD, LOOK_AROUND, LEAN,
                 WAKE, SIT_DOWN, REST -> true;
            default -> false;
        };
    }
    public static int run(CommandSourceStack source,String name)
        throws CommandSyntaxException {
        ServerPlayer actor=source.getPlayerOrException();
        NpcPose pose=choose(name);
        if(pose==null){
            source.sendFailure(Component.literal(
                "只允许无犯罪伪装的动作：greet/socialize/laugh/sad/look_around/lean/wake/sit_down/rest"));
            return 0;
        }
        long now=actor.server.getTickCount();
        long next=NEXT.getOrDefault(actor.getUUID(),Long.MIN_VALUE);
        if(now<next){
            source.sendFailure(Component.literal("动作仍在冷却中，不能刷屏"));
            return 0;
        }
        if(NEXT.size()>128)NEXT.clear(); // Bounded, never retain unbounded offline UUIDs
        NEXT.put(actor.getUUID(),now+20L);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(actor,
            new ActorEmotePacket(actor.getUUID().toString(),pose.ordinal(),TICKS));
        source.sendSuccess(()->Component.literal(
            "你已向附近实际在线玩家播放与 NPC 共用的动作："+pose),false);
        return 1;
    }
}
