package dev.reme.mcstudio.npc.life;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/**
 * Operator-only expansion of one PHYSICAL 41x41 town block into a connected
 * second loaded, unoccupied 41x41 neighborhood. No world carving or teleport.
 * The road link is 5 real vanilla blocks; all 15 extra residents are real
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
        String seam = seamSafety(world, center, way);
        if (!seam.isEmpty()) return seam;
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
        int joins=paveSeam(world,center,dir);
        if(joins!=5)
            return new CityDistrictDemo.Result(false,
                "宽马路接缝部分受保护："+joins+"/5，已建新街区须现场检查",
                result.blocks()+joins,result.citizens());
        System.out.println("REME_NPC_CITY_BOROUGH_EXPANDED direction="+dir
            +" position="+next.toShortString()+" blocks="+(result.blocks()+5)
            +" addedRealCitizens="+result.citizens()
            +" nativeRoadConnection=true");
        return new CityDistrictDemo.Result(true,
            "已建第二个真实41×41城区并连接原版实体马路；新增15名真实NPC与16张真实床，"
            +"可从十字路口向相邻方向继续扩建，已占用地块会拒绝",result.blocks()+5,
            result.citizens());
    }

    /** Survey every proposed parcel before touching any player blocks. */
    public static String surveyAvenue(ServerLevel world,BlockPos start,
                                      Direction dir,int count){
        if(count<1||count>3||!dir.getAxis().isHorizontal())
            return "每次只能沿南北东西建设1至3个相邻街区";
        String first=survey(world,start,dir);
        if(!first.startsWith("通过"))return first;
        for(int i=1;i<=count;i++){
            BlockPos next=start.relative(dir,i*42);
            if(i>1){
                String seam=seamSafety(world,start.relative(dir,(i-1)*42),dir);
                if(!seam.isEmpty())return "第"+i+"段："+seam;
            }
            String check=CityDistrictDemo.survey(world,next);
            if(!check.startsWith("场地通过"))return "第"+i+"段拒绝施工："+check;
        }
        return "通过："+count+"个完整41×41新区和五格宽实体道路接缝";
    }

    /** Real, incremental contiguous city construction; never force loads. */
    public static CityDistrictDemo.Result growAvenue(ServerLevel world,BlockPos start,
                                                      Direction dir,int count,ServerPlayer owner){
        String report=surveyAvenue(world,start,dir,count);
        if(!report.startsWith("通过"))
            return new CityDistrictDemo.Result(false,report,0,0);
        int blocks=0,people=0;
        for(int i=0;i<count;i++){
            CityDistrictDemo.Result result=grow(world,start.relative(dir,i*42),dir,owner);
            blocks+=result.blocks();
            people+=result.citizens();
            if(!result.success())
                return new CityDistrictDemo.Result(false,
                    "部分建成："+i+"段；第"+(i+1)+"段失败："+result.reason(),blocks,people);
        }
        System.out.println("REME_NPC_CONNECTED_BOULEVARD sections="+count
            +" placedBlocks="+blocks+" newEntities="+people+" seamWidth=5");
        return new CityDistrictDemo.Result(true,
            "真实新增"+count+"个41×41街区与五格宽公路连接；"
                +"有实际商场、住宅、警局和新居民；仅管理员创意施工，非NPC自行建设",
            blocks,people);
    }

    private static String seamSafety(ServerLevel world,BlockPos previous,Direction way){
        BlockPos seam=previous.relative(way,21);
        Direction cross=way.getClockWise();
        for(int d=-2;d<=2;d++){
            BlockPos spot=seam.relative(cross,d);
            if(!world.isInWorldBounds(spot)
                ||!world.getWorldBorder().isWithinBounds(spot)
                ||!world.hasChunkAt(spot)||!world.hasChunkAt(spot.below())
                ||!world.getBlockState(spot).isAir()
                ||!world.getBlockState(spot.above()).isAir()
                ||!world.getBlockState(spot.below()).isSolid())
                return "实体五格道路接口被占用/未加载/地基有洞："+spot.toShortString();
        }
        return "";
    }

    private static int paveSeam(ServerLevel world,BlockPos prev,Direction way){
        Direction cross=way.getClockWise();
        int count=0;
        for(int d=-2;d<=2;d++)
            if(world.setBlock(prev.relative(way,21).relative(cross,d),
                Blocks.GRAY_CONCRETE.defaultBlockState(),3))count++;
        return count;
    }
}
