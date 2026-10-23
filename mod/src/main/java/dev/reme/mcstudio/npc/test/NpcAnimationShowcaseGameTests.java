package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.NpcAnimationShowcase;
import dev.reme.mcstudio.npc.NpcPose;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import java.util.EnumSet;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** True Minecraft blocks, native registered entities and synced pose bits.
 * Rendering MUST separately pass the REAL GUI/X11 test, not this server test.
 */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcAnimationShowcaseGameTests {
    private NpcAnimationShowcaseGameTests() {}

    @GameTest(template="empty",timeoutTicks=130)
    public static void stage_contains_thirty_distinct_real_npcs_with_live_synced_player_poses(
        GameTestHelper h) {
        ServerLevel level=h.getLevel();
        BlockPos center=h.absolutePos(new BlockPos(23,4,23));
        for(int x=-12;x<=12;x++)for(int z=-10;z<=10;z++){
            BlockPos tile=center.offset(x,0,z);
            level.setBlock(tile.below(),Blocks.STONE.defaultBlockState(),2);
            for(int y=0;y<=3;y++)level.setBlock(tile.above(y),Blocks.AIR.defaultBlockState(),2);
        }
        var created=NpcAnimationShowcase.create(level,center,null);
        if(!created.success() || created.actors()!=30)
            throw new AssertionError("No actual complete 30 actor stage: "+created);
        var duplicates=NpcAnimationShowcase.create(level,center,null);
        if(duplicates.success()||duplicates.actors()!=0)
            throw new AssertionError("Second operator stage duplicated existing actors");
        h.runAfterDelay(60,()->{
            var citizens=level.getEntitiesOfClass(PlayerNpcEntity.class,
                new AABB(center).inflate(12,3,10),
                npc->npc.isAlive() && npc.getTags().contains(NpcAnimationShowcase.TAG));
            var poses=EnumSet.noneOf(NpcPose.class);
            var uuids=new HashSet<java.util.UUID>();
            for(PlayerNpcEntity npc:citizens) {
                if(!npc.showcasingAnimation(level.getGameTime())
                    ||!npc.isAnimationShowcase()
                    ||npc.mode()!=PlayerNpcEntity.Mode.STAY
                    ||!uuids.add(npc.getUUID())
                    ||npc.blockPosition().getY()!=center.getY())
                    throw new AssertionError("Actor failed real entity staging "+npc.getUUID());
                poses.add(npc.visualPose());
            }
            if(citizens.size()!=30||poses.size()!=30||uuids.size()!=30)
                h.fail("Hard animation stage acceptance requires 30 real synced resident poses",center);
            else {
                System.out.println("REME_NPC_NATIVE_ANIMATION_STAGE_TEST realActors="
                    +citizens.size()+" uniqueUUIDs="+uuids.size()
                    +" syncedActionStates="+poses.size()+" unchangedWorldBlocks=true");
                h.succeed();
            }
        });
    }

    @GameTest(template="empty",timeoutTicks=90)
    public static void existing_house_blocks_prevent_stage_without_changing_world(GameTestHelper h){
        ServerLevel level=h.getLevel();
        BlockPos center=h.absolutePos(new BlockPos(23,4,23));
        for(int x=-12;x<=12;x++)for(int z=-10;z<=10;z++){
            BlockPos spot=center.offset(x,0,z);
            level.setBlock(spot.below(),Blocks.STONE.defaultBlockState(),2);
            for(int y=0;y<=2;y++)level.setBlock(spot.above(y),Blocks.AIR.defaultBlockState(),2);
        }
        BlockPos protectedBlock=center.offset(-10,0,-8);
        level.setBlock(protectedBlock,Blocks.DIAMOND_BLOCK.defaultBlockState(),3);
        var refused=NpcAnimationShowcase.create(level,center,null);
        if(refused.success() || refused.actors()!=0
            ||!level.getBlockState(protectedBlock).is(Blocks.DIAMOND_BLOCK)
            ||!level.getEntitiesOfClass(PlayerNpcEntity.class,
                new AABB(center).inflate(14,3,12),
                npc->npc.getTags().contains(NpcAnimationShowcase.TAG)).isEmpty())
            throw new AssertionError("Showcase overwritten player's existing block or spawned partial cast");
        h.succeed();
    }
}
