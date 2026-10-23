package dev.reme.mcstudio.npc.life;

import dev.reme.mcstudio.npc.NpcRegistry;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Four specialized 41x41 satellites connected to the original crossroads:
 * WEST public hospital, NORTH education campus, SOUTH government/hotel,
 * EAST garage/parking/transit. Vanilla blocks, walkable entrances, beds,
 * materials and REAL resident entities. Operator-only creative showcases:
 * not resource-free autonomous construction by civilians.
 *
 * Bounded memory (one 41x41x11 plan); safe unloaded/protected checks; exactly
 * one district per command; NO terrain clearing, NO chunk ticket creation.
 */
public final class UrbanCivicDistrict {
    private UrbanCivicDistrict() {}
    public enum Zone {
        HOSPITAL(Direction.WEST, "hospital", "医院/药房/急诊/员工宿舍"),
        EDUCATION(Direction.NORTH, "education", "学校/图书馆/操场/宿舍"),
        GOVERNMENT(Direction.SOUTH, "government", "市政府/酒店/邮政/广场"),
        TRANSPORT(Direction.EAST, "transport", "车店/停车场/车站/修理车间");
        public final Direction direction;
        public final String command;
        public final String facilities;
        Zone(Direction direction,String command,String facilities) {
            this.direction=direction; this.command=command; this.facilities=facilities;
        }
        public static Zone from(String name) {
            for (Zone z:values()) if(z.command.equals(name))return z;
            return null;
        }
    }
    public record Result(boolean success,String message,int blocks,int citizens) {}

    private static final class Plan {
        final BlockPos center;
        final LinkedHashMap<BlockPos,BlockState> cells=new LinkedHashMap<>();
        Plan(BlockPos center) { this.center=center.immutable(); }
        BlockPos at(int x,int y,int z) { return center.offset(x,y,z); }
        void put(int x,int y,int z,Block block) { cells.put(at(x,y,z),block.defaultBlockState()); }
        void set(int x,int y,int z,BlockState state) { cells.put(at(x,y,z),state); }
    }
    private static final int HALF=20, OFFSET=42, ROOF=10;

    private static Plan plan(BlockPos target, Zone zone) {
        Plan p=new Plan(target);
        // Continuous 5-wide crossed local streets, contrasting footpaths,
        // corners striped for pedestrians instead of empty grey pavement.
        for(int x=-HALF;x<=HALF;x++)for(int z=-HALF;z<=HALF;z++){
            boolean roadway=Math.abs(x)<=2||Math.abs(z)<=2;
            boolean walking=Math.abs(x)<=4||Math.abs(z)<=4;
            p.put(x,0,z,roadway?Blocks.GRAY_CONCRETE:
                walking?Blocks.SMOOTH_STONE:Blocks.GRASS_BLOCK);
            if((x==0 && Math.abs(z)>=9 && z%6==0)
                ||(z==0 && Math.abs(x)>=9 && x%6==0))
                p.put(x,0,z,Blocks.YELLOW_CONCRETE);
            if((Math.abs(x)<=2&&(Math.abs(z)==6||Math.abs(z)==8))
                ||(Math.abs(z)<=2&&(Math.abs(x)==6||Math.abs(x)==8)))
                p.put(x,0,z,Blocks.WHITE_CONCRETE);
        }
        for(int x:new int[]{-4,4})for(int z:new int[]{-16,16})streetlight(p,x,z);
        for(int z:new int[]{-4,4})for(int x:new int[]{-16,16})streetlight(p,x,z);
        // Walkable short curb paths lead from EVERY public entry to sidewalk.
        for(int z:new int[]{-12,12}) {
            for(int x=-6;x<=-5;x++) p.put(x,0,z,Blocks.SMOOTH_STONE);
            for(int x=5;x<=6;x++) p.put(x,0,z,Blocks.SMOOTH_STONE);
        }
        switch(zone){
            case HOSPITAL -> hospital(p);
            case EDUCATION -> education(p);
            case GOVERNMENT -> government(p);
            case TRANSPORT -> transport(p);
        }
        staffLodging(p,zone);
        return p;
    }

    private static void streetlight(Plan p,int x,int z){
        for(int y=1;y<=4;y++)p.put(x,y,z,Blocks.IRON_BARS);
        p.put(x,5,z,Blocks.SEA_LANTERN);
    }
    /** Two-story open interior with actual flooring and lighting; door
     * states are paired, already open, never a solid block at the entrance. */
    private static void building(Plan p,int west,int north,int east,int south,
                                 int top, Block material,Block roof,boolean upstairs){
        for(int x=west;x<=east;x++)for(int z=north;z<=south;z++){
            p.put(x,0,z,Blocks.POLISHED_ANDESITE);
            p.put(x,top,z,roof);
            if(upstairs&&x>west&&x<east&&z>north&&z<south
                && !(x==west+1&&z==north+1))p.put(x,4,z,Blocks.SMOOTH_QUARTZ);
            boolean boundary=x==west||x==east||z==north||z==south;
            if(!boundary)continue;
            for(int y=1;y<top;y++){
                boolean glass=y>=2&&(y==2||y>=5&&y<=6)
                    && ((x+z)%3!=0);
                p.put(x,y,z,glass?Blocks.LIGHT_BLUE_STAINED_GLASS:material);
            }
        }
        // Use a physical open double-height door aimed toward central street.
        int doorZ=(north+south)/2;
        boolean western=west<0;
        int doorX=western?east:west;
        Direction facing=western?Direction.EAST:Direction.WEST;
        p.set(doorX,1,doorZ,Blocks.OAK_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING,facing)
            .setValue(DoorBlock.OPEN,true)
            .setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER));
        p.set(doorX,2,doorZ,Blocks.OAK_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING,facing)
            .setValue(DoorBlock.OPEN,true)
            .setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER));
        // Shaft with original vanilla ladder allows second-floor access.
        if(upstairs)
            for(int y=1;y<top;y++)p.set(west+1,y,north+1,
                Blocks.LADDER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LadderBlock.FACING,Direction.EAST));
        for(int x=west+3;x<east-1;x+=4)
            for(int z=north+3;z<south-1;z+=4)
                p.put(x,top-1,z,Blocks.SEA_LANTERN);
    }
    private static void bed(Plan p,int x,int z) {
        p.set(x,1,z,Blocks.BLUE_BED.defaultBlockState()
            .setValue(BedBlock.FACING,Direction.NORTH)
            .setValue(BedBlock.PART,BedPart.HEAD));
        p.set(x,1,z+1,Blocks.BLUE_BED.defaultBlockState()
            .setValue(BedBlock.FACING,Direction.NORTH)
            .setValue(BedBlock.PART,BedPart.FOOT));
    }
    private static void staffLodging(Plan p,Zone z) {
        // Every new human/NPC employee gets an ACTUAL assigned bed head.
        // This common south-east staff hostel is a staffed government hotel
        // in the civic district, otherwise staff housing.
        building(p,6,6,18,18,z==Zone.GOVERNMENT?10:6,
            z==Zone.TRANSPORT?Blocks.TERRACOTTA:Blocks.OAK_PLANKS,
            Blocks.DEEPSLATE_TILES,z==Zone.GOVERNMENT);
        for(int x:new int[]{8,11,14,17}) bed(p,x,14);
        p.put(7,1,9,Blocks.CHEST);
        p.put(9,1,9,Blocks.CRAFTING_TABLE);
        p.put(12,1,9,Blocks.BARREL);
        if(z==Zone.GOVERNMENT) {
            p.put(10,1,8,Blocks.LECTERN); // hotel front desk
            p.put(12,1,8,Blocks.BELL);
            for(int x=8;x<=16;x++)p.put(x,0,7,Blocks.YELLOW_CARPET);
        }
    }
    private static void hospital(Plan p) {
        building(p,-18,-18,-6,-6,10,Blocks.WHITE_CONCRETE,
            Blocks.CYAN_CONCRETE,true);
        // Red cross over street-facing clinical front, two real medical beds,
        // medicine brewing equipment; not invented heal GUI.
        for(int y=5;y<=8;y++)p.put(-6,y,-12,Blocks.RED_CONCRETE);
        for(int z=-14;z<=-10;z++)p.put(-6,6,z,Blocks.RED_CONCRETE);
        bed(p,-16,-16);
        bed(p,-12,-16);
        p.put(-10,1,-10,Blocks.BREWING_STAND);
        p.put(-9,1,-10,Blocks.CAULDRON);
        p.put(-14,1,-9,Blocks.CHEST);
        building(p,6,-18,18,-6,7,Blocks.SMOOTH_QUARTZ,
            Blocks.CYAN_TERRACOTTA,false);
        p.put(8,1,-11,Blocks.BREWING_STAND);
        p.put(9,1,-11,Blocks.CHEST);
        p.put(12,1,-11,Blocks.BARREL);
        p.put(16,1,-12,Blocks.LECTERN);
        building(p,-18,6,-6,18,5,Blocks.WHITE_CONCRETE,
            Blocks.RED_CONCRETE,false);
        p.put(-14,1,9,Blocks.CHEST);
        p.put(-12,1,9,Blocks.CRAFTING_TABLE);
        for(int x=-16;x<=-8;x+=3)p.put(x,0,16,Blocks.WHITE_CONCRETE);
    }
    private static void education(Plan p){
        building(p,-18,-18,-6,-6,8,Blocks.BRICKS,
            Blocks.RED_NETHER_BRICKS,true);
        for(int x:new int[]{-16,-13,-10})for(int z:new int[]{-15,-11})
            p.put(x,1,z,Blocks.OAK_STAIRS);
        p.put(-9,1,-10,Blocks.LECTERN);
        p.put(-8,1,-10,Blocks.BOOKSHELF);
        p.put(-7,1,-10,Blocks.BELL);
        building(p,6,-18,18,-6,7,Blocks.SMOOTH_QUARTZ,
            Blocks.DARK_PRISMARINE,false);
        for(int x=8;x<=16;x+=2)for(int z=-16;z<=-8;z+=3)
            p.put(x,1,z,Blocks.BOOKSHELF);
        p.put(12,1,-10,Blocks.LECTERN);
        // School sports field; physical ground, fences and goals.
        for(int x=-18;x<=-6;x++)for(int z=6;z<=18;z++)
            p.put(x,0,z,Blocks.GREEN_CONCRETE);
        for(int x=-17;x<=-7;x++)for(int z:new int[]{7,17})
            p.put(x,0,z,Blocks.WHITE_CONCRETE);
        for(int z=7;z<=17;z++)for(int x:new int[]{-17,-7})
            p.put(x,0,z,Blocks.WHITE_CONCRETE);
        for(int z=10;z<=14;z+=4){
            p.put(-17,1,z,Blocks.IRON_BARS);
            p.put(-7,1,z,Blocks.IRON_BARS);
        }
        p.put(-12,1,12,Blocks.NOTE_BLOCK);
    }
    private static void government(Plan p){
        building(p,-18,-18,-6,-6,10,Blocks.STONE_BRICKS,
            Blocks.COPPER_BLOCK,true);
        for(int x=-17;x<=-7;x+=3)p.put(x,9,-12,Blocks.GOLD_BLOCK);
        p.put(-11,1,-10,Blocks.LECTERN);
        p.put(-13,1,-10,Blocks.CHEST);
        p.put(-15,1,-10,Blocks.BOOKSHELF);
        p.put(-9,1,-10,Blocks.BELL);
        building(p,6,-18,18,-6,7,Blocks.BRICKS,
            Blocks.DEEPSLATE_TILES,false);
        for(int z=-16;z<=-8;z+=2){
            p.put(11,1,z,Blocks.BARREL);
            p.put(14,1,z,Blocks.CHEST);
        }
        p.put(9,1,-10,Blocks.LECTERN);
        // South-west civic square: gathering place and water-cauldron,
        // no expensive continuous liquid updates.
        for(int x=-18;x<=-6;x++)for(int z=6;z<=18;z++)
            p.put(x,0,z,Blocks.SMOOTH_STONE);
        p.put(-12,1,12,Blocks.POLISHED_DIORITE);
        p.put(-12,2,12,Blocks.WATER_CAULDRON);
        p.put(-15,1,9,Blocks.BELL);
        p.put(-10,1,9,Blocks.LECTERN);
        for(int z=7;z<=17;z+=4)p.put(-17,1,z,Blocks.FLOWER_POT);
    }
    private static void transport(Plan p){
        building(p,-18,-18,-6,-6,8,Blocks.BLUE_CONCRETE,
            Blocks.GRAY_CONCRETE,true);
        p.put(-15,1,-13,Blocks.RAIL);
        p.put(-14,1,-13,Blocks.RAIL);
        p.put(-13,1,-13,Blocks.RAIL);
        p.put(-11,1,-9,Blocks.CRAFTING_TABLE);
        p.put(-10,1,-9,Blocks.ANVIL);
        p.put(-9,1,-9,Blocks.CHEST);
        building(p,6,-18,18,-6,7,Blocks.LIGHT_GRAY_CONCRETE,
            Blocks.BLACK_CONCRETE,false);
        p.put(11,1,-12,Blocks.BLAST_FURNACE);
        p.put(12,1,-12,Blocks.ANVIL);
        p.put(13,1,-12,Blocks.CHEST);
        // Real 13x13 parking lot with 4 marked stalls + sidewalk, NOT cars.
        for(int x=-18;x<=-6;x++)for(int z=6;z<=18;z++)
            p.put(x,0,z,Blocks.GRAY_CONCRETE);
        for(int x:new int[]{-17,-14,-11,-8})
            for(int z=8;z<=16;z++)
                p.put(x,0,z,Blocks.WHITE_CONCRETE);
        for(int z=8;z<=16;z+=4)
            p.put(-9,1,z,Blocks.OAK_FENCE);
        p.put(-15,1,17,Blocks.SEA_LANTERN);
        p.put(-8,1,17,Blocks.BARREL); // fare/check-in booth
    }

    private static String check(ServerLevel level,Plan plan) {
        for(int x=-HALF;x<=HALF;x++)for(int z=-HALF;z<=HALF;z++){
            BlockPos bottom=plan.at(x,-1,z);
            if(!level.isInWorldBounds(bottom)
                || !level.getWorldBorder().isWithinBounds(bottom)
                || !level.hasChunkAt(bottom)
                || !level.getBlockState(bottom).isSolid())
                return "新区需要已经加载、41×41整块平整实心地基：" + bottom.toShortString();
            for(int y=0;y<=ROOF+1;y++){
                BlockPos spot=plan.at(x,y,z);
                if(!level.isInWorldBounds(spot)
                    ||!level.getWorldBorder().isWithinBounds(spot)
                    ||!level.hasChunkAt(spot)
                    ||!level.getBlockState(spot).isAir())
                    return "新区存在旧建筑、雪层、树或未加载区块，绝不覆盖：" + spot.toShortString();
            }
        }
        for(BlockPos b:plan.cells.keySet())
            if(!level.isInWorldBounds(b)||!level.hasChunkAt(b)
                ||!level.getBlockState(b).isAir())
                return "建筑材料位置被保护或占用：" + b.toShortString();
        return "";
    }
    private static String bridgeCheck(ServerLevel world,BlockPos original,Direction dir){
        BlockPos site=original.relative(dir,21);
        BlockPos verge=original.relative(dir,20);
        if(!world.hasChunkAt(original)||!world.getBlockState(original).is(Blocks.GRAY_CONCRETE)
            ||!world.hasChunkAt(verge)
            ||!(world.getBlockState(verge).is(Blocks.GRAY_CONCRETE)
                ||world.getBlockState(verge).is(Blocks.YELLOW_CONCRETE)
                ||world.getBlockState(verge).is(Blocks.WHITE_CONCRETE)))
            return "必须站在现有中心城区实体十字路口中心；原有马路不能被覆盖";
        if(!world.hasChunkAt(site)||!world.hasChunkAt(site.below())
            ||!world.getBlockState(site).isAir()
            ||!world.getBlockState(site.below()).isSolid())
            return "新旧城区之间的实体道路接口被占用或地基有空洞";
        return "";
    }

    public static String survey(ServerLevel level,BlockPos original,Zone zone){
        String bridge=bridgeCheck(level,original,zone.direction);
        if(!bridge.isEmpty())return bridge;
        BlockPos next=original.relative(zone.direction,OFFSET);
        String fail=check(level,plan(next,zone));
        return fail.isEmpty()
            ? "可以扩建 " + zone.facilities + "：新区41×41地块及旧路道路接口全部已加载、平整且空闲"
            : "拒绝扩建 " + zone.facilities + "：" + fail;
    }

    public static Result build(ServerLevel level,BlockPos original,Zone zone,
                               @Nullable ServerPlayer player){
        String bridge=bridgeCheck(level,original,zone.direction);
        if(!bridge.isEmpty())return new Result(false,bridge,0,0);
        BlockPos next=original.relative(zone.direction,OFFSET);
        Plan plan=plan(next,zone);
        String fail=check(level,plan);
        if(!fail.isEmpty())return new Result(false,fail,0,0);
        // Only the OP-requested preflighted zone is built. Up to ~4k vanilla
        // block updates, not a continuously running NPC AI world-edit loop.
        int count=0;
        for(Map.Entry<BlockPos,BlockState> e:plan.cells.entrySet()){
            if(!level.setBlock(e.getKey(),e.getValue(),3))
                return new Result(false,"保护机制拒绝方块："+e.getKey().toShortString()
                    +"；请检查部分已施工街区并备份",count,0);
            count++;
        }
        BlockPos road=original.relative(zone.direction,21);
        if(!level.setBlock(road,Blocks.GRAY_CONCRETE.defaultBlockState(),3))
            return new Result(false,"新区存在，但旧城道路衔接被保护插件拒绝",count,0);
        int people=spawn(level,next,zone,player,original);
        System.out.println("REME_NPC_URBAN_DISTRICT zone="+zone.command+" realBlocks="+(count+1)
            +" realPeople="+people+" roadConnected=true noChunkTickets=true");
        return new Result(true,zone.facilities+" 建成：原版实体道路、可进入的建筑、岗位和床位。"
            +"仅为管理员演示，不等于自动生成真实社会或汽车",count+1,people);
    }

    /** Same full preflight and physical building algorithm, but this native
     * GameTest variant operates in a 41x41 isolated arena without an existing
     * main city; never registered as a user command or a normal AI action. */
    public static Result buildGameTest(ServerLevel level,BlockPos center,Zone zone) {
        Plan p=plan(center,zone);
        String fail=check(level,p);
        if(!fail.isEmpty())return new Result(false,fail,0,0);
        int placed=0;
        for(Map.Entry<BlockPos,BlockState> e:p.cells.entrySet()){
            if(!level.setBlock(e.getKey(),e.getValue(),3))
                return new Result(false,"GameTest materialization rejected",placed,0);
            placed++;
        }
        return new Result(true,"Native isolated zone "+zone,placed,0);
    }

    private static int spawn(ServerLevel level,BlockPos city,Zone zone,
                             @Nullable ServerPlayer player,BlockPos original){
        String region="city_"+(original.getX()>>4)+"_"+(original.getZ()>>4);
        PlayerNpcEntity.Profession[] jobs=switch(zone){
            case HOSPITAL -> new PlayerNpcEntity.Profession[]{
                PlayerNpcEntity.Profession.MEDIC,PlayerNpcEntity.Profession.MEDIC,
                PlayerNpcEntity.Profession.MEDIC,PlayerNpcEntity.Profession.RESIDENT};
            case EDUCATION -> new PlayerNpcEntity.Profession[]{
                PlayerNpcEntity.Profession.TEACHER,PlayerNpcEntity.Profession.LIBRARIAN,
                PlayerNpcEntity.Profession.RESIDENT,PlayerNpcEntity.Profession.RESIDENT};
            case GOVERNMENT -> new PlayerNpcEntity.Profession[]{
                PlayerNpcEntity.Profession.CIVIC_CLERK,PlayerNpcEntity.Profession.HOTELIER,
                PlayerNpcEntity.Profession.COURIER,PlayerNpcEntity.Profession.RESIDENT};
            case TRANSPORT -> new PlayerNpcEntity.Profession[]{
                PlayerNpcEntity.Profession.MECHANIC,PlayerNpcEntity.Profession.SHOPKEEPER,
                PlayerNpcEntity.Profession.CIVIC_CLERK,PlayerNpcEntity.Profession.RESIDENT};
        };
        String[] names=switch(zone){
            case HOSPITAL->new String[]{"急诊医生","护士","药房医师","就诊居民"};
            case EDUCATION->new String[]{"学校教师","图书馆员","在校学生","运动学生"};
            case GOVERNMENT->new String[]{"市政办事员","酒店前台","邮政配送员","旅店客人"};
            case TRANSPORT->new String[]{"车店技师","车店柜员","停车场管理员","公共交通乘客"};
        };
        int[][] spawnAt={{-12,-12},{12,-12},{-12,12},{11,10}};
        int[][] jobAt={{-11,-10},{12,-10},{-12,11},{10,10}};
        int count=0;
        for(int i=0;i<4;i++){
            PlayerNpcEntity npc=NpcRegistry.PLAYER_NPC.get().create(level);
            if(npc==null)continue;
            BlockPos at=city.offset(spawnAt[i][0],1,spawnAt[i][1]);
            npc.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);
            npc.setMode(PlayerNpcEntity.Mode.LIVE);
            npc.setIdentity(SocietyRules.civilian(region));
            npc.setProfession(jobs[i]);
            npc.setRoamingRadius(30); // bounded to local district; no chunk tickets
            npc.setSkinId((i+zone.ordinal()*4)%6);
            // Every assigned bed is an actual two-block, non-overlapping head.
            int x=8+i*3;
            BlockPos head=city.offset(x,1,14);
            npc.setAssignedBedPos(head);
            npc.setHomePos(city.offset(x,1,13)); // walkable aisle, not bed FOOT
            npc.setWorkPos(city.offset(jobAt[i][0],1,jobAt[i][1]));
            npc.setCustomName(Component.literal(names[i]));
            npc.setCustomNameVisible(true);
            npc.addTag("reme_urban_"+zone.command+"_"+i);
            npc.setCharacter(CharacterProfile.fromSeed(2000+zone.ordinal()*13+i));
            if(jobs[i]==PlayerNpcEntity.Profession.MEDIC)
                npc.backpack().addItem(new ItemStack(Items.GOLDEN_APPLE,4));
            else if(jobs[i]==PlayerNpcEntity.Profession.LIBRARIAN)
                npc.backpack().addItem(new ItemStack(Items.BOOK,8));
            else if(jobs[i]==PlayerNpcEntity.Profession.TEACHER)
                npc.backpack().addItem(new ItemStack(Items.BOOK,8));
            else if(jobs[i]==PlayerNpcEntity.Profession.HOTELIER)
                npc.backpack().addItem(new ItemStack(Items.BREAD,6));
            else if(jobs[i]==PlayerNpcEntity.Profession.CIVIC_CLERK)
                npc.backpack().addItem(new ItemStack(Items.PAPER,12));
            else if(jobs[i]==PlayerNpcEntity.Profession.MECHANIC)
                npc.backpack().addItem(new ItemStack(Items.IRON_INGOT,4));
            else if(jobs[i]==PlayerNpcEntity.Profession.SHOPKEEPER)
                npc.backpack().addItem(new ItemStack(Items.BREAD,8));
            else if(jobs[i]==PlayerNpcEntity.Profession.RESIDENT)
                npc.backpack().addItem(new ItemStack(Items.EMERALD,3));
            if(npc.backpack().countItem(Items.BREAD)==0
                && jobs[i]!=PlayerNpcEntity.Profession.RESIDENT)
                npc.backpack().addItem(new ItemStack(Items.BREAD,3));
            if(player!=null)npc.setOwner(player);
            if(level.addFreshEntity(npc))count++;
        }
        return count;
    }
}
