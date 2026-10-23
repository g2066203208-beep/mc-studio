package dev.reme.mcstudio.npc.life;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/**
 * Operator-only expansion of one PHYSICAL 41x41 town block into a connected
 * second loaded, unoccupied 41x41 neighborhood. No world carving or teleport.
 * The road link is 1 real vanilla block; all 15 extra residents are real
 * entities with independent, dedicated original vanilla beds.
 * Builders, zoning and multi-tile mass transit are NOT simulated here.
 */
public final class CityDistrictExpansion {
    private CityDistrictExpansion() {}

    public static String survey(ServerLevel world, BlockPos center, Direction way) {
        if (!way.getAxis().isHorizontal())
            return "城市只能沿南北东西道路扩展";
        if (!world.hasChunkAt(center)
            || !world.getBlockState(center).is(Blocks.GRAY_CONCRETE))
            return "请站在已经生成的城市十字路口中心操作，而不是任意野外空地";
        BlockPos last = center.relative(way, 20);
        if (!world.hasChunkAt(last)
            || !(world.getBlockState(last).is(Blocks.GRAY_CONCRETE)
                || world.getBlockState(last).is(Blocks.YELLOW_CONCRETE)
                || world.getBlockState(last).is(Blocks.WHITE_CONCRETE)))
            return "原城区边界没有实体道路，不允许建造孤岛";
        BlockPos bridge = center.relative(way,21);
        if (!world.hasChunkAt(bridge) || !world.hasChunkAt(bridge.below())
            || !world.getBlockState(bridge).isAir()
            || !world.getBlockState(bridge.below()).isSolid())
            return "连接主街的实体道路走廊被占用/不是实心平地";
        BlockPos next = center.relative(way, 42);
        String report = CityDistrictDemo.survey(world,next);
        if (report.startsWith("不能生成城市"))
            return "新城区拒绝施工：" + report;
        return "通过：现有城区、实体接缝和邻接41×41新地块均安全。"
            + "下一段街道将与原路网通过原版灰色混凝土连通。" ;
    }

    public static CityDistrictDemo.Result grow(ServerLevel world, BlockPos center,
                                                Direction dir, ServerPlayer owner) {
        String report=survey(world,center,dir);
        if (!report.startsWith("通过"))
            return new CityDistrictDemo.Result(false,report,0,0);
        BlockPos next=center.relative(dir,42);
        CityDistrictDemo.Result result=CityDistrictDemo.build(world,next,owner);
        if (!result.success())return result;
        BlockPos join=center.relative(dir,21);
        // Preflight already validated the corridor before any construction.
        // No attempt to modify existing player blocks.
        if(!world.setBlock(join,Blocks.GRAY_CONCRETE.defaultBlockState(),3))
            return new CityDistrictDemo.Result(false,
                "新街区已建好，但连接路口受插件保护，人工检查路段",result.blocks(),result.citizens());
        System.out.println("REME_NPC_CITY_BOROUGH_EXPANDED direction="+dir
            +" position="+next.toShortString()+" blocks="+(result.blocks()+1)
            +" addedRealCitizens="+result.citizens()
            +" nativeRoadConnection=true");
        return new CityDistrictDemo.Result(true,
            "已建第二个真实41×41城区并连接原版实体马路；新增15名真实NPC与16张真实床，"
            +"可从十字路口向相邻方向继续扩建，已占用地块会拒绝",result.blocks()+1,
            result.citizens());
    }
}
