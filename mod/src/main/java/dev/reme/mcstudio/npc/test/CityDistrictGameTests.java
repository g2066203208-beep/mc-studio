package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import dev.reme.mcstudio.npc.life.CityDistrictDemo;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * REAL game-server city validation: materialized 41x41 block-for-block
 * vanilla structures with actual roads, mall, stores, residences, beds,
 * district law station, park and individually ticking NPC entities.
 * Creative OP-demo materialization is NOT a survival construction claim.
 */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CityDistrictGameTests {
    private CityDistrictGameTests() {}

    private static void prepare(ServerLevel world, BlockPos center) {
        for (int x = -20; x <= 20; x++)
            for (int z = -20; z <= 20; z++) {
                BlockPos ground = center.offset(x, -1, z);
                world.setBlock(ground, Blocks.STONE.defaultBlockState(), 2);
                for (int y = 0; y <= 10; y++)
                    world.setBlock(center.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
    }

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void real_city_streets_mall_homes_police_and_people_exist_as_blocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(23, 4, 23));
        prepare(level, center);
        if (!CityDistrictDemo.survey(level, center).contains("场地通过"))
            throw new AssertionError("A clean real-world flat site failed preflight");
        CityDistrictDemo.Result outcome = CityDistrictDemo.build(level, center, null);
        if (!outcome.success() || outcome.blocks() < 2500 || outcome.citizens() < 10)
            throw new AssertionError("Real city did not build enough physical blocks/residents: " + outcome);
        // Roads are truly in Minecraft's block palette and form a continuous
        // X/Z intersection with real crosswalk stripes and pavements.
        for (int i = -20; i <= 20; i++) {
            if (!(level.getBlockState(center.offset(i,0,0)).is(Blocks.GRAY_CONCRETE)
                || level.getBlockState(center.offset(i,0,0)).is(Blocks.YELLOW_CONCRETE)
                || level.getBlockState(center.offset(i,0,0)).is(Blocks.WHITE_CONCRETE))
                || !(level.getBlockState(center.offset(0,0,i)).is(Blocks.GRAY_CONCRETE)
                || level.getBlockState(center.offset(0,0,i)).is(Blocks.YELLOW_CONCRETE)
                || level.getBlockState(center.offset(0,0,i)).is(Blocks.WHITE_CONCRETE)))
                throw new AssertionError("Main 41-block cross-town paved roads have a physical gap at " + i);
        }
        if (!level.getBlockState(center.offset(0,0,6)).is(Blocks.WHITE_CONCRETE)
            || !level.getBlockState(center.offset(-4,5,-13)).is(Blocks.SEA_LANTERN))
            throw new AssertionError("Town has no real zebra crossing or glowing streetlight");
        // Hard physical exit checks: walls may not trap real workers inside
        // one-tile mall doors or private apartments during the night shift.
        for(int z : new int[]{-13,-11,11,13})
            for(int y=1;y<=2;y++)
                if(!level.getBlockState(center.offset(-6,y,z)).isAir())
                    throw new AssertionError("Mall/police side pedestrian arch is solid "+z+" y="+y);
        for(int x : new int[]{8,10,14,16})
            for(int y=1;y<=2;y++)
                if(!level.getBlockState(center.offset(x,y,-6)).isAir())
                    throw new AssertionError("Real apartment pedestrian exit blocked at x="+x);
        for(var doorPos : new BlockPos[]{
            center.offset(-6,1,-12),center.offset(-6,1,12),
            center.offset(9,1,-6),center.offset(15,1,-6)})
            if(!level.getBlockState(doorPos).is(Blocks.OAK_DOOR)
                || !level.getBlockState(doorPos).getValue(
                    net.minecraft.world.level.block.DoorBlock.OPEN))
                throw new AssertionError("Native openable door replaced by an arcade: "+doorPos);
        if (!level.getBlockState(center.offset(-6,8,-12)).is(Blocks.GOLD_BLOCK)
            || !level.getBlockState(center.offset(-12,4,-13)).is(Blocks.SMOOTH_QUARTZ)
            || !level.getBlockState(center.offset(-12,1,-12)).is(Blocks.CHEST)
            || !level.getBlockState(center.offset(-13,1,-12)).is(Blocks.BARREL)
            || !(level.getBlockEntity(center.offset(-12,1,-12)) instanceof ChestBlockEntity))
            throw new AssertionError("Mall is a façade without a physical second floor and shop storage");
        if (!level.getBlockState(center.offset(7,1,-16)).is(Blocks.RED_BED)
            || !level.getBlockState(center.offset(7,1,-16))
                .getValue(BedBlock.PART).equals(BedPart.HEAD)
            || !level.getBlockState(center.offset(13,1,-16)).is(Blocks.RED_BED)
            || !level.getBlockState(center.offset(-16,1,8)).is(Blocks.RED_BED))
            throw new AssertionError("City housing and guard quarters lack real beds");
        if (!level.getBlockState(center.offset(-12,2,15)).is(Blocks.IRON_BARS)
            || !level.getBlockState(center.offset(-5,2,9)).is(Blocks.BELL)
            || !level.getBlockState(center.offset(-5,1,12)).isAir()
            || !level.getBlockState(center.offset(-5,2,12)).isAir()
            || !level.getBlockState(center.offset(15,1,9)).is(Blocks.NOTE_BLOCK)
            || !level.getBlockState(center.offset(12,2,12)).is(Blocks.WATER_CAULDRON))
            throw new AssertionError("Town's police cell, village alarm or market park is missing");
        List<PlayerNpcEntity> people = level.getEntitiesOfClass(PlayerNpcEntity.class,
            new AABB(center.getX()-20, center.getY(), center.getZ()-20,
                center.getX()+21, center.getY()+11, center.getZ()+21));
        PlayerNpcEntity storeClerk = people.stream().filter(p ->
                p.getTags().contains("reme_city_clerk")).findFirst().orElseThrow();
        var exitPath = storeClerk.getNavigation().createPath(center.offset(-5,1,-12),0);
        if(exitPath==null || !exitPath.canReach())
            throw new AssertionError("Mall merchant cannot walk out of the actual mall door");
        // A source/target that exists does NOT pass if vanilla A* ends on the
        // wrong side of a solid wall; the path must reach the real city sidewalk.
        System.out.println("REME_NPC_CITY_REAL_ENTRANCE verifiedStoreClerkUUID="
            +storeClerk.getUUID()+" mallExitReachable=true nativeDoors=4 pedestrianArches=8");
        long merchants = people.stream().filter(p -> p.profession()
            == PlayerNpcEntity.Profession.SHOPKEEPER
            && p.backpack().countItem(Items.BREAD) > 0).count();
        long guards = people.stream().filter(p -> p.profession()
            == PlayerNpcEntity.Profession.GUARD && p.policeJail() != null).count();
        long customers = people.stream().filter(p -> p.profession()
            == PlayerNpcEntity.Profession.RESIDENT
            && p.backpack().countItem(Items.EMERALD) > 0).count();
        long realDishonestShoppers = people.stream().filter(p ->
            p.profession() == PlayerNpcEntity.Profession.RESIDENT
            && p.character().honesty() <= 20).count();
        for (String track : new String[]{"reme_city_clerk","reme_city_guard",
                                       "reme_city_park","reme_city_shoplifter"}) {
            long tracked = people.stream().filter(p -> p.getTags().contains(track)).count();
            if (tracked != 1)
                throw new AssertionError("Real entity identity not stable for camera: "
                    + track + "=" + tracked);
        }
        // A generated city with 13 people and only 2 beds is NOT a liveable
        // city. Count *actual vanilla HEAD blocks and distinct saved claims*.
        java.util.Set<BlockPos> bedClaims = new java.util.HashSet<>();
        for (PlayerNpcEntity resident : people) {
            BlockPos claim = resident.assignedBedPos();
            if (claim == null || !bedClaims.add(claim)
                || !level.getBlockState(claim).is(Blocks.RED_BED)
                || level.getBlockState(claim).getValue(BedBlock.PART) != BedPart.HEAD
                || resident.homePos().distSqr(claim) > 6)
                throw new AssertionError("Citizen lacks unique reachable physical bed: "
                    + resident.getName().getString() + " claim=" + claim);
        }
        if (bedClaims.size() != 15)
            throw new AssertionError("Not all 15 NPCs own a separate real bed");
        if (!level.getBlockState(center.offset(-15,1,-10)).is(Blocks.BOOKSHELF)
            || !level.getBlockState(center.offset(-15,1,-9)).is(Blocks.LECTERN)
            || !level.getBlockState(center.offset(-9,2,9)).is(Blocks.BREWING_STAND)
            || people.stream().noneMatch(p -> p.profession()
                == PlayerNpcEntity.Profession.MEDIC)
            || people.stream().noneMatch(p -> p.profession()
                == PlayerNpcEntity.Profession.LIBRARIAN))
            throw new AssertionError("Town claims professions without real library / medical facility");
        BlockPos inmateSpot = center.offset(-13, 1, 15);
        if (merchants < 2 || guards < 2 || customers < 3
            || realDishonestShoppers != 1
            || people.size() != 15
            || !level.getBlockState(center.offset(-4,2,-10)).is(Blocks.BELL)
            || !level.getBlockState(center.offset(-8,1,-11)).isAir()
            || !dev.reme.mcstudio.npc.life.PublicSafety.holdingSpot(level, inmateSpot)
            || !dev.reme.mcstudio.npc.life.CityJailGate.isGate(level, inmateSpot)
            || dev.reme.mcstudio.npc.life.CityJailGate.isOpen(level, inmateSpot)
            || !level.getBlockState(center.offset(-12,1,16)).is(Blocks.IRON_BARS)
            || !level.getBlockState(center.offset(-13,1,18)).is(Blocks.OAK_DOOR)
            || !level.getBlockState(center.offset(-13,1,18))
                .getValue(net.minecraft.world.level.block.DoorBlock.OPEN))
            throw new AssertionError("City lacks actual shops, 13 safe citizens, cashless shoplifter,"
                + " physically accessible jail or genuine shop alarm: merchants=" + merchants
                + " guards=" + guards + " payingResidents=" + customers
                + " dishonest=" + realDishonestShoppers + " total=" + people.size());
        // Once created, a city cannot silently overprint an existing world.
        CityDistrictDemo.Result refused = CityDistrictDemo.build(level, center, null);
        if (refused.success() || refused.blocks() != 0 || refused.citizens() != 0)
            throw new AssertionError("City duplicated already placed world or NPCs");
        System.out.println("REME_NPC_ACTUAL_CITY_BLOCKS="+outcome.blocks()
            +" citizens="+outcome.citizens()+" shops="+merchants
            +" police="+guards+" customers="+customers
            +" cityWidth=41"
            +" roadCrossing=true mallRealStorage=true residentialBeds=16"
            +" distinctBedClaims="+bedClaims.size()+" clinicAndLibrary=true cityProtected=true");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 90)
    public static void cannot_bulldoze_existing_house_terrain_snow_or_unloaded_cells(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(23,4,23));
        prepare(level, center);
        BlockPos existing = center.offset(-7, 3, -7);
        level.setBlock(existing, Blocks.DIAMOND_BLOCK.defaultBlockState(), 2);
        CityDistrictDemo.Result attempt = CityDistrictDemo.build(level, center, null);
        if (attempt.success() || attempt.blocks()!=0
            || !level.getBlockState(existing).is(Blocks.DIAMOND_BLOCK)
            || !level.getBlockState(center).isAir())
            throw new AssertionError("City demo bulldozed an existing player building");
        helper.succeed();
    }
}
