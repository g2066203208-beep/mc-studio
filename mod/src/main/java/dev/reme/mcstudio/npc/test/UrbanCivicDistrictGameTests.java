package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import dev.reme.mcstudio.npc.life.UrbanCivicDistrict;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Native 1.21.1 GameTestServer: FOUR physically different populated districts,
 * no images and no virtual schematic. Tests natural walkable footprints.
 */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UrbanCivicDistrictGameTests {
    private UrbanCivicDistrictGameTests() {}

    private static BlockPos clean(ServerLevel level,GameTestHelper helper) {
        BlockPos center=helper.absolutePos(new BlockPos(23,4,23));
        for(int x=-20;x<=20;x++)for(int z=-20;z<=20;z++){
            BlockPos base=center.offset(x,-1,z);
            level.setBlock(base,Blocks.STONE.defaultBlockState(),2);
            for(int y=0;y<=11;y++)
                level.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
        }
        return center;
    }
    private static void verify(GameTestHelper helper,UrbanCivicDistrict.Zone zone) {
        ServerLevel world=helper.getLevel();
        BlockPos c=clean(world,helper);
        UrbanCivicDistrict.Result result=UrbanCivicDistrict.buildGameTest(world,c,zone);
        if(!result.success()||result.blocks()<2100||result.citizens()!=4)
            throw new AssertionError("Native city zone missing buildings or workers: "+result);
        List<PlayerNpcEntity> people=world.getEntitiesOfClass(
            PlayerNpcEntity.class,new AABB(c.getX()-20,c.getY(),
                c.getZ()-20,c.getX()+21,c.getY()+11,c.getZ()+21));
        long employed=people.stream().filter(p->p.workPos()!=null
            && p.assignedBedPos()!=null && p.isAlive()).count();
        if(people.size()!=4||employed!=4)
            throw new AssertionError("No 4 real living staffed city residents: "+people.size());
        for(int i=0;i<4;i++){
            BlockPos head=c.offset(8+i*3,1,14);
            if(!world.getBlockState(head).is(Blocks.BLUE_BED)
                ||world.getBlockState(head).getValue(BedBlock.PART)!=BedPart.HEAD
                ||!world.getBlockState(head.south()).is(Blocks.BLUE_BED))
                throw new AssertionError("Missing unique native employee bed "+head);
        }
        for(int step=-20;step<=20;step++){
            if(!road(world.getBlockState(c.offset(step,0,0)))
                ||!road(world.getBlockState(c.offset(0,0,step))))
                throw new AssertionError("City district cross streets physically broken at "+step);
        }
        if(!world.getBlockState(c.offset(0,0,6)).is(Blocks.WHITE_CONCRETE)
            ||!world.getBlockState(c.offset(4,5,-16)).is(Blocks.SEA_LANTERN)
            ||!world.getBlockState(c.offset(-6,1,-12)).is(Blocks.OAK_DOOR)
            ||!world.getBlockState(c.offset(-6,1,-12))
                .getValue(DoorBlock.OPEN)
            ||!world.getBlockState(c.offset(6,1,-12)).is(Blocks.OAK_DOOR))
            throw new AssertionError("Zone lacks actual crosswalk, streetlights or walkable public entrance");
        // Real safe sidewalk from central junction to BOTH block entries:
        // y+1/2 must be empty for an actual 1.8-block Minecraft NPC body.
        for(int side:new int[]{-1,1})
            for(int x=0;x<=5;x++){
                BlockPos aisle=c.offset(side*x,1,-12);
                if(!world.getBlockState(aisle).isAir()
                    ||!world.getBlockState(aisle.above()).isAir()
                    ||!world.getBlockState(aisle.below()).isSolid())
                    throw new AssertionError("Public building front has an obstructed ground walkway: "+aisle);
            }
        switch(zone){
            case HOSPITAL -> {
                if(!world.getBlockState(c.offset(-6,6,-12)).is(Blocks.RED_CONCRETE)
                    ||!world.getBlockState(c.offset(-10,1,-10)).is(Blocks.BREWING_STAND)
                    ||!world.getBlockState(c.offset(-16,1,-16)).is(Blocks.BLUE_BED)
                    ||!world.getBlockState(c.offset(9,1,-11)).is(Blocks.CHEST))
                    throw new AssertionError("Hospital needs actual wards, inventory clinic and pharmacy");
            }
            case EDUCATION -> {
                if(!world.getBlockState(c.offset(-9,1,-10)).is(Blocks.LECTERN)
                    ||!world.getBlockState(c.offset(12,1,-10)).is(Blocks.LECTERN)
                    ||!world.getBlockState(c.offset(8,1,-16)).is(Blocks.BOOKSHELF)
                    ||!world.getBlockState(c.offset(-12,0,7)).is(Blocks.WHITE_CONCRETE))
                    throw new AssertionError("School lacks classrooms, real library or physical sports field");
            }
            case GOVERNMENT -> {
                if(!world.getBlockState(c.offset(-11,1,-10)).is(Blocks.LECTERN)
                    ||!world.getBlockState(c.offset(11,1,-10)).is(Blocks.BARREL)
                    ||!world.getBlockState(c.offset(12,1,8)).is(Blocks.BELL)
                    ||!world.getBlockState(c.offset(-12,2,12)).is(Blocks.WATER_CAULDRON))
                    throw new AssertionError("Government lacks town hall desk, post office, hotel or plaza");
            }
            case TRANSPORT -> {
                if(!world.getBlockState(c.offset(-14,1,-13)).is(Blocks.RAIL)
                    ||!world.getBlockState(c.offset(-10,1,-9)).is(Blocks.ANVIL)
                    ||!world.getBlockState(c.offset(11,1,-12)).is(Blocks.BLAST_FURNACE)
                    ||!world.getBlockState(c.offset(-17,0,10)).is(Blocks.WHITE_CONCRETE))
                    throw new AssertionError("Transport lacks physical showroom, workshop or parking lanes");
            }
        }
        // No repeated /npc zone duplication, replacement of existing world.
        UrbanCivicDistrict.Result repeat=UrbanCivicDistrict.buildGameTest(world,c,zone);
        if(repeat.success()||repeat.citizens()!=0||repeat.blocks()!=0)
            throw new AssertionError("City district overwrote old player building or spawned duplicate workers");
        System.out.println("REME_NPC_PROFESSIONAL_ZONE type="+zone.command
            +" actualBlocks="+result.blocks()
            +" citizens="+result.citizens()
            +" nativeBeds=4 realCrossroads=true actualEntrance=true"
            +" stockDuplicated=false");
        helper.succeed();
    }
    private static boolean road(net.minecraft.world.level.block.state.BlockState s){
        return s.is(Blocks.GRAY_CONCRETE)||s.is(Blocks.YELLOW_CONCRETE)
            ||s.is(Blocks.WHITE_CONCRETE);
    }
    @GameTest(template="empty",timeoutTicks=220)
    public static void real_west_hospital_pharmacy_ward_medic_zone(GameTestHelper h){
        verify(h,UrbanCivicDistrict.Zone.HOSPITAL);
    }
    @GameTest(template="empty",timeoutTicks=220)
    public static void real_north_school_library_classroom_campus(GameTestHelper h){
        verify(h,UrbanCivicDistrict.Zone.EDUCATION);
    }
    @GameTest(template="empty",timeoutTicks=220)
    public static void real_south_cityhall_hotel_post_office_plaza(GameTestHelper h){
        verify(h,UrbanCivicDistrict.Zone.GOVERNMENT);
    }
    @GameTest(template="empty",timeoutTicks=220)
    public static void real_east_vehicle_showroom_garage_parking_streets(GameTestHelper h){
        verify(h,UrbanCivicDistrict.Zone.TRANSPORT);
    }
    @GameTest(template="empty",timeoutTicks=120)
    public static void refused_city_cannot_remove_old_walls_or_mine_player_blocks(GameTestHelper h){
        ServerLevel world=h.getLevel();
        BlockPos c=clean(world,h);
        BlockPos existing=c.offset(-10,2,-10);
        world.setBlock(existing,Blocks.DIAMOND_BLOCK.defaultBlockState(),3);
        UrbanCivicDistrict.Result failure=UrbanCivicDistrict.buildGameTest(
            world,c,UrbanCivicDistrict.Zone.HOSPITAL);
        if(failure.success()||failure.blocks()!=0||failure.citizens()!=0
            ||!world.getBlockState(existing).is(Blocks.DIAMOND_BLOCK)
            ||!world.getBlockState(c).isAir())
            throw new AssertionError("Urban district griefed existing old-building terrain");
        h.succeed();
    }
}
