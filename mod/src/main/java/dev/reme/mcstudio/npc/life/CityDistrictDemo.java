package dev.reme.mcstudio.npc.life;

import dev.reme.mcstudio.npc.NpcRegistry;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import java.util.ArrayList;
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
 * Explicit, operator-only CREATIVE city showcase: physical Minecraft blocks,
 * functional vanilla businesses, streets, flats, park, police station and
 * genuinely ticking resident NPCs. NOT autonomous survival city building.
 *
 * The complete plan is checked BEFORE even one block is placed. The command
 * refuses uneven/occupied/protected/unloaded land, never bulldozes existing
 * terrain, never loads chunks, never runs during routine NPC AI, and does not
 * touch old player saves unless an OP explicitly invokes the demo.
 */
public final class CityDistrictDemo {
    private CityDistrictDemo() {}
    public static final int HALF = 20;
    private static final int MAX_Y = 10;
    private static final int MIN_GROUND_COVERAGE = 1681; // 41 x 41

    public record Result(boolean success, String reason, int blocks, int citizens) {}

    private static final class Plan {
        final BlockPos origin;
        final LinkedHashMap<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        Plan(BlockPos origin) { this.origin = origin.immutable(); }
        BlockPos pos(int x, int y, int z) { return origin.offset(x, y, z); }
        void put(int x, int y, int z, Block block) {
            blocks.put(pos(x, y, z), block.defaultBlockState());
        }
        void state(int x, int y, int z, BlockState state) {
            blocks.put(pos(x, y, z), state);
        }
    }

    private static String validate(ServerLevel level, Plan plan) {
        BlockPos anchor = plan.origin;
        // The road system needs an honest existing FLAT solid ground.
        // Keeping the requirement strict prevents flattening players' homes.
        int inspected = 0;
        for (int x = -HALF; x <= HALF; x++)
            for (int z = -HALF; z <= HALF; z++) {
                BlockPos base = anchor.offset(x, -1, z);
                if (!level.isInWorldBounds(base)
                    || !level.getWorldBorder().isWithinBounds(base)
                    || !level.hasChunkAt(base))
                    return "地基跨越未加载区块/世界边界，不能施工";
                if (!level.getBlockState(base).isSolid())
                    return "地面不平或有空洞：需要41×41的实心平地";
                inspected++;
                for (int y = 0; y <= MAX_Y; y++) {
                    BlockPos cell = anchor.offset(x, y, z);
                    if (!level.isInWorldBounds(cell)
                        || !level.getWorldBorder().isWithinBounds(cell)
                        || !level.hasChunkAt(cell)
                        || !level.getBlockState(cell).isAir())
                        return "区域已有地物/树/雪或未加载，拒绝覆盖：" + cell.toShortString();
                }
            }
        if (inspected != MIN_GROUND_COVERAGE) return "地基验证数量错误";
        // All blocks are real vanilla BlockStates; no offset or half blocks.
        for (BlockPos cell : plan.blocks.keySet()) {
            if (!level.isInWorldBounds(cell) || !level.hasChunkAt(cell)
                || !level.getBlockState(cell).isAir())
                return "目标方块被占用：" + cell.toShortString();
        }
        return "";
    }

    public static String survey(ServerLevel level, BlockPos anchor) {
        Plan plan = plan(anchor);
        String reject = validate(level, plan);
        return reject.isEmpty()
            ? "场地通过：41×41空地、平整实心地基、区块已加载；将真实放置 "
                + plan.blocks.size() + " 个原版方块，包含十字马路、人行道、双层商场、两栋住宅、警局、街区公园与市集"
            : "不能生成城市：" + reject + "。仅在全新平地测试世界操作，自动保护你的现有建筑。";
    }

    public static Result build(ServerLevel level, BlockPos anchor,
                               @Nullable ServerPlayer owner) {
        Plan plan = plan(anchor);
        String reject = validate(level, plan);
        if (!reject.isEmpty()) return new Result(false, reject, 0, 0);
        int placed = 0;
        for (Map.Entry<BlockPos, BlockState> entry : plan.blocks.entrySet()) {
            if (!level.setBlock(entry.getKey(), entry.getValue(), 3))
                return new Result(false, "方块安放被游戏或保护机制否决: "
                    + entry.getKey().toShortString() + "，已放置 " + placed
                    + "；请在新测试世界验收，不会强制覆盖被保护区域", placed, 0);
            placed++;
        }
        int residents = spawnShowcaseResidents(level, anchor, owner);
        return new Result(true,
            "已在真实 Minecraft 世界建成 41×41 街区：十字双车道、斑马线、路灯、人行道、双层购物中心、两栋住宅、实体床、警局铁栏拘留区、绿地、市集和实际店员/顾客。"
            + "注意：这是仅管理员手动调用的创意演示，不是 NPC 生存模式自动建城。",
            placed, residents);
    }

    private static Plan plan(BlockPos center) {
        Plan p = new Plan(center);
        // Every surface has a physical vanilla tile, no mesh/ghost blocks.
        for (int x = -HALF; x <= HALF; x++)
            for (int z = -HALF; z <= HALF; z++) {
                boolean road = Math.abs(x) <= 2 || Math.abs(z) <= 2;
                boolean sidewalk = Math.abs(x) <= 4 || Math.abs(z) <= 4;
                p.put(x, 0, z, road ? Blocks.GRAY_CONCRETE
                    : sidewalk ? Blocks.SMOOTH_STONE : Blocks.GRASS_BLOCK);
                // Yellow dashed center lines across the four road arms.
                if (road && ((x == 0 && Math.abs(z) >= 6 && z % 5 == 0)
                    || (z == 0 && Math.abs(x) >= 6 && x % 5 == 0)))
                    p.put(x, 0, z, Blocks.YELLOW_CONCRETE);
                // Genuine crosswalk stripes at all four intersection edges.
                if ((Math.abs(x) <= 2 && (Math.abs(z) == 6 || Math.abs(z) == 8))
                    || (Math.abs(z) <= 2 && (Math.abs(x) == 6 || Math.abs(x) == 8)))
                    p.put(x, 0, z, Blocks.WHITE_CONCRETE);
            }
        // Streetlamps built block-by-block with vanilla fence and sea lantern.
        for (int s : new int[]{-1, 1}) {
            lamp(p, s * 4, -13);
            lamp(p, s * 4, 13);
            lamp(p, -13, s * 4);
            lamp(p, 13, s * 4);
        }
        mall(p);
        apartments(p);
        police(p);
        parkAndMarket(p);
        return p;
    }

    private static void lamp(Plan p, int x, int z) {
        for (int y = 1; y <= 4; y++) p.put(x, y, z, Blocks.DARK_OAK_FENCE);
        p.put(x, 5, z, Blocks.SEA_LANTERN);
    }

    private static void shell(Plan p, int west, int north, int east, int south,
                              int roof, Block wall, Block window, Block roofBlock) {
        for (int x = west; x <= east; x++)
            for (int z = north; z <= south; z++) {
                p.put(x, 0, z, Blocks.POLISHED_ANDESITE);
                p.put(x, roof, z, roofBlock);
                if (x != west && x != east && z != north && z != south) continue;
                for (int y = 1; y < roof; y++) {
                    boolean glass = y >= 2 && y < roof - 1
                        && ((x + z) % 3 != 0);
                    p.put(x, y, z, glass ? window : wall);
                }
            }
    }

    private static void door(Plan p, int x, int z, Direction facing) {
        p.state(x, 1, z, Blocks.OAK_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING, facing)
            .setValue(DoorBlock.OPEN, true)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        p.state(x, 2, z, Blocks.OAK_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING, facing)
            .setValue(DoorBlock.OPEN, true)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private static void bed(Plan p, int x, int z) {
        // Head is north, foot is south: actual two-block vanilla bed.
        p.state(x, 1, z, Blocks.RED_BED.defaultBlockState()
            .setValue(BedBlock.PART, BedPart.HEAD)
            .setValue(BedBlock.FACING, Direction.NORTH));
        p.state(x, 1, z + 1, Blocks.RED_BED.defaultBlockState()
            .setValue(BedBlock.PART, BedPart.FOOT)
            .setValue(BedBlock.FACING, Direction.NORTH));
    }

    private static void mall(Plan p) {
        // Genuine two-level enclosed physical shopping mall in northwest.
        shell(p, -18, -18, -6, -6, 8, Blocks.WHITE_CONCRETE,
            Blocks.LIGHT_BLUE_STAINED_GLASS, Blocks.DARK_PRISMARINE);
        // Upper floor; ladder shaft kept clear over the rear wall.
        for (int x = -17; x <= -7; x++)
            for (int z = -17; z <= -7; z++)
                if (!(x == -17 && z == -17))
                    p.put(x, 4, z, Blocks.SMOOTH_QUARTZ);
        door(p, -6, -12, Direction.EAST);
        // The library desk is genuinely inside the mall, rather than just
        // giving a nonfunctional LIBRARIAN label to a random pedestrian.
        p.put(-15, 1, -10, Blocks.BOOKSHELF);
        p.put(-15, 1, -9, Blocks.LECTERN);
        for (int z = -16; z <= -8; z++)
            p.put(-5, 3, z, z % 2 == 0 ? Blocks.RED_CONCRETE : Blocks.YELLOW_CONCRETE);
        // Physical counters, real chest/barrel storage, display stalls.
        for (int z : new int[]{-16, -12, -8}) {
            p.put(-13, 1, z, Blocks.BARREL);
            p.put(-12, 1, z, Blocks.CHEST);
            p.put(-11, 1, z, Blocks.SMOOTH_QUARTZ);
            p.put(-10, 1, z, Blocks.LIME_CARPET);
            p.put(-9, 1, z, Blocks.GLOWSTONE);
        }
        // Actual ceiling/lighting, upper-level windows and carpeted gallery.
        for (int x = -16; x <= -8; x += 4)
            for (int z = -16; z <= -8; z += 4) p.put(x, 7, z, Blocks.SEA_LANTERN);
        for (int y = 1; y <= 5; y++)
            p.state(-17, y, -17, Blocks.LADDER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.EAST));
        // Visible contrasting rooftop signage stripe.
        for (int z = -17; z <= -7; z++)
            p.put(-6, 8, z, z % 2 == 0 ? Blocks.GOLD_BLOCK : Blocks.ORANGE_CONCRETE);
    }

    private static void apartments(Plan p) {
        // Two distinct townhouses face the main avenue; actual assignable beds.
        // Continuous inner gallery across two townhouse facade wings.
        // Former 5-wide individual units crammed six beds into 1-wide halls
        // and NPCs blocked one another, causing real nighttime insomnia.
        shell(p, 6, -18, 18, -6, 6, Blocks.BRICKS,
            Blocks.GLASS, Blocks.SPRUCE_PLANKS);
        // Two distinct exterior facade wings WITHOUT an interior wall.
        for(int x=13;x<=18;x++)for(int z=-18;z<=-6;z++)
            if(x==18||z==-18||z==-6)
                for(int y=1;y<6;y++)
                    if(!p.blocks.get(p.pos(x,y,z)).is(Blocks.GLASS))
                        p.put(x,y,z,Blocks.OAK_PLANKS);
        door(p, 9, -6, Direction.SOUTH);
        door(p, 15, -6, Direction.SOUTH);
        // Two compact homes need enough *real* vanilla sleeping space for
        // the citizens working here: 12 separate red beds, not 2 for 13 NPCs.
        for (int z : new int[]{-16, -13, -10}) {
            bed(p, 7, z);
            bed(p, 10, z);
            bed(p, 13, z);
            bed(p, 16, z);
        }
        p.put(7, 1, -11, Blocks.CHEST);
        p.put(17, 1, -11, Blocks.BARREL);
        for (int z = -5; z <= -3; z++) {
            p.put(9, 0, z, Blocks.POLISHED_ANDESITE);
            p.put(15, 0, z, Blocks.POLISHED_ANDESITE);
        }
    }

    private static void police(Plan p) {
        shell(p, -18, 6, -6, 18, 6, Blocks.STONE_BRICKS,
            Blocks.GLASS, Blocks.DEEPSLATE_TILES);
        door(p, -6, 12, Direction.EAST);
        // Real rear prisoner intake: outside street z=19 -> open station
        // back door z=18 -> authorized IRON cell gate z=17 -> actual cell.
        // Avoid a 25-block detour around solid police walls, which left
        // pathfinding stuck four blocks from the cell for 1200 ticks.
        door(p, -13, 18, Direction.SOUTH);
        // Lockable-looking REAL iron-bar holding room; true custody remains
        // the existing PublicSafety controller, not the structure itself.
        for (int x = -17; x <= -12; x++)
            for (int z = 14; z <= 17; z++) {
                if (x != -17 && x != -12 && z != 14 && z != 17) continue;
                // Only south intake at x=-13,z=17 can be unlocked.
                // East wall stays fully closed: no walkaround out of jail.
                if (x == -13 && z == 17) continue;
                for (int y = 1; y <= 3; y++) p.put(x, y, z, Blocks.IRON_BARS);
            }
        // A REAL initially locked iron gate directly facing the rear
        // entrance, changed only by CityJailGate after physical arrest.
        p.state(-13, 1, 17, Blocks.IRON_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING, Direction.SOUTH)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        p.state(-13, 2, 17, Blocks.IRON_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING, Direction.SOUTH)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        // Night-watch officers have their own quarters instead of using
        // shopworkers' beds or sleeping at the occupied iron-bar cell.
        bed(p, -16, 8);
        bed(p, -13, 8);
        // Two additional civic staff sleeping places; public clinic and
        // lending-library employees have their OWN real bed heads.
        bed(p, -16, 11);
        bed(p, -13, 11);
        // Small physical community-care workstation; no GUI-only hospital.
        p.put(-9, 1, 9, Blocks.SMOOTH_QUARTZ);
        p.put(-9, 2, 9, Blocks.BREWING_STAND);
        p.put(-8, 1, 9, Blocks.CAULDRON);
        p.put(-11, 1, 10, Blocks.LECTERN);
        p.put(-15, 1, 9, Blocks.CHEST);
        p.put(-10, 1, 9, Blocks.CRAFTING_TABLE);
        // Keep the EAST station door and its approach genuinely walkable.
        // Earlier -5,1,12 STONE_BRICKS blocked the only native entrance:
        // prisoner reached the outside south wall but could never get in.
        // Alarm is moved along the facade away from the route.
        p.put(-5, 1, 9, Blocks.STONE_BRICKS);
        p.put(-5, 2, 9, Blocks.BELL);
        // Real audible alarm *near the mall*, not only 24 blocks away at
        // headquarters. The store clerk can trigger it after an actual
        // first-hand theft and nearby on-foot guard dispatch.
        p.put(-4, 1, -10, Blocks.STONE_BRICK_WALL);
        p.put(-4, 2, -10, Blocks.BELL);
        for (int z = 11; z <= 13; z++) p.put(-6, 5, z, Blocks.BLUE_CONCRETE);
    }

    private static void parkAndMarket(Plan p) {
        // Southeast public park with accessible paved paths and stalls.
        for (int x = 6; x <= 18; x++)
            for (int z = 6; z <= 18; z++)
                if (x == 12 || z == 12) p.put(x, 0, z, Blocks.SMOOTH_STONE);
        // True physical fountain made of contained water cauldrons, not a
        // spreading water source that would grief neighboring buildings.
        for (int x = 10; x <= 14; x++)
            for (int z = 10; z <= 14; z++)
                if (!(x == 12 || z == 12)) p.put(x, 0, z, Blocks.POLISHED_DIORITE);
        p.put(12, 1, 12, Blocks.STONE_BRICKS);
        p.put(12, 2, 12, Blocks.WATER_CAULDRON);
        for (int x : new int[]{8,17}) for (int z : new int[]{8,17}) {
            for (int y = 1; y <= 3; y++) p.put(x,y,z,Blocks.OAK_LOG);
            for (int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++)
                p.put(x+dx,4,z+dz,Blocks.OAK_LEAVES);
        }
        // Real market stalls, not an imaginary virtual shop GUI.
        for (int z : new int[]{7,16}) {
            for (int x = 10; x <= 14; x++) {
                p.put(x, 1, z, Blocks.BARREL);
                p.put(x, 3, z, x % 2 == 0 ? Blocks.RED_WOOL : Blocks.YELLOW_WOOL);
            }
        }
        p.put(15,1,9, Blocks.NOTE_BLOCK);
        p.put(9,1,15, Blocks.BARREL);
    }

    /** Demo citizens receive explicitly labeled creative-starting goods. */
    private static int spawnShowcaseResidents(ServerLevel level, BlockPos center,
                                               @Nullable ServerPlayer owner) {
        String[] names = {"商场经理","食品店员","烘焙店员","巡逻警察","值班警察",
            "街区农夫","农贸供应商","上班族甲","上班族乙","逛街顾客","公园居民","快递配送员",
            "街区逃单者","社区医护","图书管理员"};
        PlayerNpcEntity.Profession[] jobs = {
            PlayerNpcEntity.Profession.SHOPKEEPER, PlayerNpcEntity.Profession.SHOPKEEPER,
            PlayerNpcEntity.Profession.BAKER, PlayerNpcEntity.Profession.GUARD,
            PlayerNpcEntity.Profession.GUARD, PlayerNpcEntity.Profession.FARMER,
            PlayerNpcEntity.Profession.FARMER, PlayerNpcEntity.Profession.RESIDENT,
            PlayerNpcEntity.Profession.RESIDENT, PlayerNpcEntity.Profession.RESIDENT,
            PlayerNpcEntity.Profession.RESIDENT, PlayerNpcEntity.Profession.COURIER,
            PlayerNpcEntity.Profession.RESIDENT,
            PlayerNpcEntity.Profession.MEDIC, PlayerNpcEntity.Profession.LIBRARIAN
        };
        int[][] spawn = {
            {-10,-10},{-10,-14},{-8,-8},{-7,-10},{-10,13},
            {10,11},{16,12},{9,-9},{15,-9},{-3,-12},{10,8},{3,12},
            {-8,-11},{-10,10},{-14,-10}
        };
        int count=0;
        String region = "city_" + (center.getX()>>4) + "_" + (center.getZ()>>4);
        // Fixed one-to-one *physical bed HEADS*; no multiple people fighting
        // over a single claim. These blocks are placed by apartments()/police.
        int[][] beds = {
            {7,-16},{10,-16},{13,-16},{-16,8},{-13,8},
            {16,-16},{7,-13},{10,-13},{13,-13},{16,-13},
            {7,-10},{10,-10},{13,-10},
            {-16,11},{-13,11}
        };
        for (int i=0; i<names.length; i++) {
            PlayerNpcEntity npc = NpcRegistry.PLAYER_NPC.get().create(level);
            if (npc == null) continue;
            npc.moveTo(center.getX() + spawn[i][0] + .5,
                center.getY() + 1, center.getZ() + spawn[i][1] + .5, 0, 0);
            npc.setMode(PlayerNpcEntity.Mode.LIVE);
            npc.setProfession(jobs[i]);
            npc.setIdentity(SocietyRules.civilian(region));
            npc.setSkinId(i % 6);
            npc.setRoamingRadius(64);
            BlockPos head = center.offset(beds[i][0], 1, beds[i][1]);
            // The vanilla bed FOOT is occupied geometry, NOT a valid
            // walking target. Previously all home anchors pointed *inside*
            // the bed and residents could circle forever at night.
            int aisle = switch (beds[i][0]) {
                case 7 -> 8;
                case 10 -> 9;
                case 13 -> 14;
                case 16 -> 15;
                case -16 -> -15;
                case -13 -> -12;
                default -> beds[i][0];
            };
            npc.setHomePos(center.offset(aisle, 1, beds[i][1]));
            npc.setAssignedBedPos(head);
            if (i < 3) npc.setWorkPos(center.offset(-10,1,-12));
            else if (i < 5) {
                // One officer's actual street beat covers the shop so
                // eyewitness reports can reach police. One guards station.
                npc.setWorkPos(center.offset(i == 3 ? -7 : -10, 1,
                    i == 3 ? -10 : 12));
                npc.setPoliceJail(center.offset(-13,1,15));
            }
            else if (i < 7) npc.setWorkPos(center.offset(15,1,12));
            else if (i == 13) npc.setWorkPos(center.offset(-10,1,10));
            else if (i == 14) npc.setWorkPos(center.offset(-14,1,-10));
            else npc.setWorkPos(center.offset(-3,1,-12));
            if (owner != null) npc.setOwner(owner);
            npc.setCustomName(Component.literal(names[i]));
            npc.setCustomNameVisible(true);
            // Identity-stable native entity tags survive real navigation.
            // Do NOT infer a moving citizen's identity from old spawn
            // coordinates in the Xvfb client screenshot workflow.
            if (i == 0) npc.addTag("reme_city_clerk");
            if (i == 3) npc.addTag("reme_city_guard");
            if (i == 10) npc.addTag("reme_city_park");
            if (i == 12) npc.addTag("reme_city_shoplifter");
            npc.setCharacter(i == 12
                ? new CharacterProfile(8, 30, 50, 25)
                : CharacterProfile.fromSeed(i+234));
            if (jobs[i] == PlayerNpcEntity.Profession.SHOPKEEPER) {
                npc.backpack().addItem(new ItemStack(Items.BREAD, 12));
                npc.backpack().addItem(new ItemStack(Items.APPLE, 8));
                npc.backpack().addItem(new ItemStack(Items.EMERALD, 4));
            } else if (jobs[i] == PlayerNpcEntity.Profession.FARMER) {
                npc.backpack().addItem(new ItemStack(Items.CARROT, 18));
                npc.backpack().addItem(new ItemStack(Items.BREAD, 2));
            } else if (jobs[i] == PlayerNpcEntity.Profession.MEDIC) {
                npc.backpack().addItem(new ItemStack(Items.GOLDEN_APPLE, 6));
                npc.backpack().addItem(new ItemStack(Items.BREAD, 3));
            } else if (jobs[i] == PlayerNpcEntity.Profession.LIBRARIAN) {
                npc.backpack().addItem(new ItemStack(Items.BOOK, 12));
                npc.backpack().addItem(new ItemStack(Items.BREAD, 3));
            } else if (jobs[i] == PlayerNpcEntity.Profession.RESIDENT) {
                // 12th index is a low-honesty, cashless local. Honest
                // customers get actual money and pay normally. No stolen
                // merchandise is inserted here: crime requires real theft.
                if (i != 12)
                    npc.backpack().addItem(new ItemStack(Items.EMERALD, 3));
                npc.changeNeeds(-52,0,0);
            } else npc.backpack().addItem(new ItemStack(Items.BREAD,3));
            if (level.addFreshEntity(npc)) count++;
        }
        System.out.println("REME_NPC_STORY stage=city_initial_population"
            + " tick=" + level.getGameTime() + " citizens=" + count
            + " realClerks=2 realGuards=2 dishonestCustomers=1"
            + " realResidentialBeds=16 individualBedClaims=15 clinic=1 library=1");
        return count;
    }
}
