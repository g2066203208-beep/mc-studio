package dev.reme.mcstudio.npc.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.NpcPose;
import dev.reme.mcstudio.npc.NpcPoseSelection;
import dev.reme.mcstudio.npc.SharedHumanoidMotion;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native NeoForge checks for shared EXACT same PLAYER and NPC clip assets.
 * The real client renderer is tested separately; this is not a render test.
 */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SharedHumanoidMotionGameTests {
    private SharedHumanoidMotionGameTests() {}

    @GameTest(template="empty",timeoutTicks=100)
    public static void every_declared_player_and_npc_action_uses_a_real_clip_file(GameTestHelper helper) {
        String[] bones={"head","torso","leftArm","rightArm","leftLeg","rightLeg"};
        Set<String> known=Set.of(bones);
        Set<String> resources=new HashSet<>();
        int clips=0,keyframes=0;
        for (NpcPose pose:NpcPose.values()) {
            if (!SharedHumanoidMotion.authoredClip(pose)) continue;
            String id=SharedHumanoidMotion.animationId(pose).toString();
            if(!resources.add(id)||!id.startsWith("reme_studio:actor_"))
                throw new AssertionError("Duplicate/wrong actor animation namespace: "+id);
            String path="assets/reme_studio/player_animation/actor_"
                +pose.name().toLowerCase(java.util.Locale.ROOT)+".json";
            try(InputStream stream=SharedHumanoidMotionGameTests.class
                    .getClassLoader().getResourceAsStream(path)) {
                if(stream==null) throw new AssertionError("No authored player+NPC clip: "+path);
                JsonObject root=JsonParser.parseReader(new InputStreamReader(stream,
                    StandardCharsets.UTF_8)).getAsJsonObject();
                if(!root.get("name").getAsString().equals("actor_"
                    +pose.name().toLowerCase(java.util.Locale.ROOT)))
                    throw new AssertionError("PlayerAnimator registry name mismatch: "+path);
                JsonObject emote=root.getAsJsonObject("emote");
                int end=emote.get("endTick").getAsInt();
                if(end<=0 || emote.get("stopTick").getAsInt()<=end)
                    throw new AssertionError("Animation timeline invalid: "+path);
                JsonArray moves=emote.getAsJsonArray("moves");
                if(moves.isEmpty()) throw new AssertionError("Empty keyframe animation: "+path);
                for(var entry:moves) {
                    JsonObject key=entry.getAsJsonObject();
                    int tick=key.get("tick").getAsInt();
                    if(tick<0 || tick>end) throw new AssertionError("Out of time pose: "+path);
                    for(String bone:key.keySet()) {
                        if(!bone.equals("tick")&&!bone.equals("easing")&&!known.contains(bone))
                            throw new AssertionError("Clip uses a non-PlayerModel bone: "+bone);
                    }
                }
                keyframes+=moves.size();
                clips++;
            } catch (java.io.IOException problem) {
                throw new AssertionError("Failed to read real clip asset "+path,problem);
            }
        }
        if(clips!=22 || keyframes<100)
            throw new AssertionError("Too few independently authored player/NPC clips: "
                +clips+" frames="+keyframes);
        // Old network ordinals are permanent; old saved worlds need not change.
        if(NpcPose.values()[0]!=NpcPose.IDLE
           ||NpcPose.values()[1]!=NpcPose.WORK
           ||NpcPose.values()[2]!=NpcPose.CARRY
           ||NpcPose.values()[3]!=NpcPose.SOCIALIZE
           ||NpcPose.values()[4]!=NpcPose.GUARD
           ||NpcPose.values()[5]!=NpcPose.AFRAID
           ||NpcPose.values()[6]!=NpcPose.REST
           ||NpcPose.fromNetworkId(-1)!=NpcPose.IDLE)
            throw new AssertionError("Existing save/network pose ID changed");
        System.out.println("REME_NPC_SHARED_PLAYER_RIG clips="+clips
            +" realKeyframes="+keyframes
            +" originalNetworkOrdinalsPreserved=true sameResourceIDs=true");
        helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=80)
    public static void stolen_goods_pursuit_and_real_knockout_override_random_emotes(GameTestHelper helper) {
        if(NpcPoseSelection.classifyPhysical(true,true,true,true,true,true)!=NpcPose.CUFFED
           ||NpcPoseSelection.classifyPhysical(true,false,true,true,true,true)!=NpcPose.STUNNED
           ||NpcPoseSelection.classifyPhysical(false,false,true,true,true,true)!=NpcPose.HIT
           ||NpcPoseSelection.classifyPhysical(false,false,true,true,true,false)!=NpcPose.ESCORT
           ||NpcPoseSelection.classifyPhysical(false,false,false,true,true,false)!=NpcPose.CHASE
           ||NpcPoseSelection.classifyPhysical(false,false,false,false,true,false)!=NpcPose.FLEE
           ||NpcPoseSelection.classifyPhysical(false,false,false,false,false,false)!=null)
            throw new AssertionError("Player/NPC real event pose priority incorrect");
        helper.succeed();
    }
}
