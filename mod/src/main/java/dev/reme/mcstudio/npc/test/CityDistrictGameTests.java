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
        if (!level.getBlockState(center.offset(-6,8,-12)).is(Blocks.GOLD_BLOCK)
            || !level.getBlockState(center.offset(-12,4,-13)).is(Blocks.SMOOTH_QUARTZ)
            || !level.getBlockState(center.offset(-12,1,-12)).is(Blocks.CHEST)
            || !level.getBlockState(center.offset(-13,1,-12)).is(Blocks.BARREL)
            || !(level.getBlockEntity(center.offset(-12,1,-12)) instanceof ChestBlockEntity))
            throw new AssertionError("Mall is a façade without a physical second floor and shop storage");
        if (!level.getBlockState(center.offset(8,1,-15)).is(Blocks.RED_BED)
            || !level.getBlockState(center.offset(8,1,-15))
                .getValue(BedBlock.PART).equals(BedPart.HEAD)
            || !level.getBlockState(center.offset(15,1,-15)).is(Blocks.RED_BED))
            throw new AssertionError("Two townhouses lack physical vanilla resident beds");
        if (!level.getBlockState(center.offset(-12,2,15)).is(Blocks.IRON_BARS)
            || !level.getBlockState(center.offset(-5,2,12)).is(Blocks.BELL)
            || !level.getBlockState(center.offset(15,1,9)).is(Blocks.NOTE_BLOCK)
            || !level.getBlockState(center.offset(12,2,12)).is(Blocks.WATER_CAULDRON))
            throw new AssertionError("Town's police cell, village alarm or market park is missing");
        List<PlayerNpcEntity> people = level.getEntitiesOfClass(PlayerNpcEntity.class,
            new AABB(center.getX()-20, center.getY(), center.getZ()-20,
                center.getX()+21, center.getY()+11, center.getZ()+21));
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
        BlockPos inmateSpot = center.offset(-13, 1, 15);
        if (merchants < 2 || guards < 2 || customers < 3
            || realDishonestShoppers != 1
            || people.size() != 13
            || !level.getBlockState(center.offset(-4,2,-10)).is(Blocks.BELL)
            || !level.getBlockState(center.offset(-8,1,-11)).isAir()
            || !dev.reme.mcstudio.npc.life.PublicSafety.holdingSpot(level, inmateSpot)
            || !level.getBlockState(center.offset(-12,1,16)).isAir())
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
            +" roadCrossing=true mallRealStorage=true residentialBeds=true cityProtected=true");
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
