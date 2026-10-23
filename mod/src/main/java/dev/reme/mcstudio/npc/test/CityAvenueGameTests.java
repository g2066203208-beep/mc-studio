package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import dev.reme.mcstudio.npc.life.CityDistrictDemo;
import dev.reme.mcstudio.npc.life.CityDistrictExpansion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** City expansion safety tested against real vanilla blocks, not mock maps.
 * A full three-section success needs a larger isolated world/GUI acceptance. */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CityAvenueGameTests {
    private CityAvenueGameTests() {}

    @GameTest(template="empty",timeoutTicks=160)
    public static void avenue_does_not_overwrite_one_obstructed_side_lane(GameTestHelper h) {
        ServerLevel world=h.getLevel();
        BlockPos center=h.absolutePos(new BlockPos(23,4,23));
        for(int x=-20;x<=20;x++)for(int z=-20;z<=20;z++){
            world.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
            for(int y=0;y<=10;y++)
                world.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
        }
        CityDistrictDemo.Result city=CityDistrictDemo.build(world,center,null);
        if(!city.success()||city.citizens()!=15)
            throw new AssertionError("Native existing city block could not build");
        BlockPos seam=center.east(21).south(2);
        for(int side=-2;side<=2;side++){
            BlockPos lane=center.east(21).south(side);
            world.setBlock(lane.below(),Blocks.STONE.defaultBlockState(),3);
            world.setBlock(lane,Blocks.AIR.defaultBlockState(),3);
            world.setBlock(lane.above(),Blocks.AIR.defaultBlockState(),3);
        }
        world.setBlock(seam,Blocks.DIAMOND_BLOCK.defaultBlockState(),3);
        int before=world.getEntitiesOfClass(PlayerNpcEntity.class,
            new AABB(center).inflate(25)).size();
        String rejection=CityDistrictExpansion.surveyAvenue(world,center,
            Direction.EAST,1);
        CityDistrictDemo.Result refused=CityDistrictExpansion.growAvenue(
            world,center,Direction.EAST,1,null);
        int after=world.getEntitiesOfClass(PlayerNpcEntity.class,
            new AABB(center).inflate(25)).size();
        if(!rejection.contains("道路接口") || refused.success()
            || refused.blocks()!=0 || refused.citizens()!=0
            || !world.getBlockState(seam).is(Blocks.DIAMOND_BLOCK)
            || !world.getBlockState(center).is(Blocks.GRAY_CONCRETE)
            || before!=after)
            throw new AssertionError("Five-wide city road preflight damaged prior housing or spawned bogus residents: "+rejection);
        System.out.println("REME_NPC_AVENUE_PROTECTED fiveWide=true"
            +" unmodifiedDiamond=true unchangedResidentCount="+before);
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=80)
    public static void never_allocate_unbounded_six_block_avenue(GameTestHelper h) {
        ServerLevel world=h.getLevel();
        BlockPos center=h.absolutePos(new BlockPos(5,4,5));
        if(CityDistrictExpansion.surveyAvenue(world,center,Direction.EAST,4)
            .startsWith("通过")
            ||CityDistrictExpansion.growAvenue(world,center,Direction.EAST,4,null).success())
            throw new AssertionError("City growth must cap parcels at three and preserve the world");
        h.succeed();
    }
}
