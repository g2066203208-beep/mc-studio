package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.NpcPose;
import dev.reme.mcstudio.npc.PlayerEmoteActions;
import dev.reme.mcstudio.npc.SharedHumanoidMotion;
import dev.reme.mcstudio.npc.net.ActorEmotePacket;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlayerEmoteGameTests {
    private PlayerEmoteGameTests(){}
    @GameTest(template="empty",timeoutTicks=80)
    public static void real_player_and_npc_own_same_non_fake_emote_action_ids(GameTestHelper h){
        for(String ok:new String[]{"greet","laugh","sad","socialize","lean",
            "wake","rest","sit_down","look_around"}){
            NpcPose selected=PlayerEmoteActions.choose(ok);
            if(selected==null || !SharedHumanoidMotion.authoredClip(selected)
                || !SharedHumanoidMotion.animationId(selected).getPath().startsWith("actor_")
                || !PlayerEmoteActions.permitted(selected))
                throw new AssertionError("Human/NPC gesture contract missing "+ok);
        }
        for(String forbidden:new String[]{"cuffed","stunned","flee","chase",
            "hit","attack","back_carry","hand_hold","running","bad_pose"}){
            if(PlayerEmoteActions.choose(forbidden)!=null)
                throw new AssertionError("Players could forge physical crime/carry actions: "+forbidden);
        }
        ActorEmotePacket packet=new ActorEmotePacket(
            "00000000-0000-0000-0000-000000000001",NpcPose.GREET.ordinal(),35);
        if(packet.poseId()!=NpcPose.GREET.ordinal()||packet.duration()!=35
            ||!packet.type().id().getNamespace().equals("reme_studio"))
            throw new AssertionError("Actor emote network ID or packet incorrect");
        h.succeed();
    }
}
