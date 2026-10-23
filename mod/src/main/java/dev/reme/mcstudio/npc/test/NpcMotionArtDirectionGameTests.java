package dev.reme.mcstudio.npc.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.reme.mcstudio.StudioMod;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A STYLE REGRESSION gate, not a claim of Action & Stuff-level animation.
 * Every carefully keyed movement must remain paced, loop seamlessly and use
 * vanilla Minecraft six-bone rotations inside non-contorted limits.
 * Actual rendered feel and weight must separately pass native GUI review.
 */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcMotionArtDirectionGameTests {
    private NpcMotionArtDirectionGameTests(){}
    private static final Set<String> CURATED = Set.of(
        "idle","walk","run","flee","chase","strike","hit",
        "stunned","crouch","greet","look_around","eating","afraid","sitting");
    @GameTest(template="empty",timeoutTicks=100)
    public static void hand_authored_motion_has_timing_variety_safe_joints_and_loop_seams(
        GameTestHelper helper) {
        int actualKeys=0, fullCycles=0;
        for(String action:CURATED){
            String file="assets/reme_studio/player_animations/actor_"+action+".json";
            var stream=NpcMotionArtDirectionGameTests.class.getClassLoader()
                .getResourceAsStream(file);
            if(stream==null)throw new AssertionError("Missing native KosmX motion "+file);
            JsonObject root=JsonParser.parseReader(
                new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject data=root.getAsJsonObject("emote");
            var moves=data.getAsJsonArray("moves");
            int duration=data.get("endTick").getAsInt();
            if(duration<16||duration>70)
                throw new AssertionError("Out-of-style rushed/frozen clip "+file);
            var byTime=new java.util.TreeMap<Integer,Map<String,JsonObject>>();
            for(var item:moves){
                JsonObject key=item.getAsJsonObject();
                int time=key.get("tick").getAsInt();
                if(time<0||time>duration)
                    throw new AssertionError("Keyframe outside clip duration "+file);
                for(var prop:key.entrySet()){
                    String bone=prop.getKey();
                    if(bone.equals("tick")||bone.equals("easing"))continue;
                    if(!Set.of("head","torso","rightArm","leftArm","rightLeg","leftLeg")
                            .contains(bone))
                        throw new AssertionError("Non-vanilla player bone "+bone);
                    JsonObject angles=prop.getValue().getAsJsonObject();
                    for(var axis:angles.entrySet()){
                        float v=axis.getValue().getAsFloat();
                        if(Float.isNaN(v)||Float.isInfinite(v)
                            ||Math.abs(v)>2.3F)
                            throw new AssertionError("Uncomfortable contorted limb "+action
                                +" "+bone+"."+axis.getKey()+"="+v);
                    }
                    byTime.computeIfAbsent(time,unused->new HashMap<>())
                        .put(bone,angles);
                    actualKeys++;
                }
            }
            if(byTime.size()<5)
                throw new AssertionError("Only stiff keyframe poses, no designed timing: "+action);
            if(!byTime.containsKey(0)||!byTime.containsKey(duration))
                throw new AssertionError("Clip cannot blend back to stable neutral "+action);
            boolean loop=data.get("isLoop").getAsBoolean();
            if(loop){
                Map<String,JsonObject> first=byTime.firstEntry().getValue();
                Map<String,JsonObject> last=byTime.lastEntry().getValue();
                if(!first.equals(last))
                    throw new AssertionError("Loop snaps on wraparound "+action);
                fullCycles++;
            } else if(!Set.of("strike","hit","greet").contains(action))
                throw new AssertionError("Persistent physical state incorrectly stops "+action);
        }
        if(actualKeys<450||fullCycles<10)
            throw new AssertionError("Too few curated movement keyframes: "+actualKeys);
        System.out.println("REME_NPC_MOTION_ART_DIRECTION curated="+CURATED.size()
            +" authoredBoneKeys="+actualKeys
            +" seamlessLoops="+fullCycles+" vanillaSixBoneLimits=true");
        helper.succeed();
    }
}
