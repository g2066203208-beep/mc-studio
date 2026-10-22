package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.NpcRegistry;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import dev.reme.mcstudio.npc.life.LivingPlanner;
import dev.reme.mcstudio.npc.life.SocietyRules;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Watch ONE UUID, not a population average. Real vanilla world/entity ticks,
 * 24 hourly audit records, actual ripe wheat, real meals and the night routine.
 *
 * Opt-in only: the 54 standard PR GameTests must not incur 24,000+ world ticks.
 * The CI runs fast 24k-tick behavior evidence and separately can require
 * 20 actual wall-clock minutes, which are NOT the same measurement.
 */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcSingleDayGameTests {
    private NpcSingleDayGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 160000)
    public static void watch_same_real_farmer_from_sunrise_through_next_sunrise(
            GameTestHelper helper) {
        if (!Boolean.getBoolean("reme.npc.singleDay")) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(21, 9, 21));
        // This is a disposable isolated GameTest arena, not a user save.
        // Keep every position within its 48x24x48 NBT footprint.
        for (int dx = -13; dx <= 13; dx++)
            for (int dz = -13; dz <= 13; dz++) {
                BlockPos feet = center.offset(dx, 0, dz);
                level.setBlock(feet.below(), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(feet, Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(feet.above(), Blocks.AIR.defaultBlockState(), 3);
            }
        // A real irrigated wheat patch at work, not a fake instant-harvest
        // planner counter. NPC work can act on the vanilla crop blocks.
        BlockPos field = center.east(4);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos crop = field.offset(dx, 0, dz);
                level.setBlock(crop.below(), Blocks.FARMLAND.defaultBlockState()
                    .setValue(FarmBlock.MOISTURE, 7), 3);
                level.setBlock(crop, Blocks.WHEAT.defaultBlockState()
                    .setValue(CropBlock.AGE, 7), 3);
            }
        // Lit open-air test area avoids night monster interference with the
        // tracked citizen's actual schedule.
        for (int x : new int[] {-11, 11}) for (int z : new int[] {-11, 11})
            level.setBlock(center.offset(x, 0, z),
                Blocks.TORCH.defaultBlockState(), 3);

        PlayerNpcEntity farmer = NpcRegistry.PLAYER_NPC.get().create(level);
        if (farmer == null) throw new AssertionError("Missing NeoForge resident registry");
        farmer.moveTo(center.getX() + 0.5, center.getY(),
            center.getZ() + 0.5, 0, 0);
        farmer.setCustomName(net.minecraft.network.chat.Component.literal("DayWatchFarmer"));
        farmer.setHomePos(center);
        farmer.setWorkPos(center);
        farmer.setRoamingRadius(16);
        farmer.setIdentity(SocietyRules.civilian("day_watch_test"));
        farmer.setProfession(PlayerNpcEntity.Profession.FARMER);
        farmer.setMode(PlayerNpcEntity.Mode.LIVE);
        // 10 actual bread keep the resident fed for a whole day, but eating
        // still consumes the real 36-slot backpack food on the server.
        farmer.backpack().addItem(new ItemStack(Items.BREAD, 10));
        farmer.backpack().addItem(new ItemStack(Items.WHEAT_SEEDS, 12));
        if (!level.addFreshEntity(farmer))
            throw new AssertionError("Could not add the observed actual NPC");

        level.setDayTime(0L); // 06:00 Minecraft local clock
        final UUID subjectId = farmer.getUUID();
        final long startGame = level.getGameTime();
        final long startWall = System.nanoTime();
        final long requiredWall = Math.max(0L,
            Math.min(2400L, Long.getLong("reme.npc.singleDay.minWallSeconds", 0L)))
            * 1_000_000_000L;

        final int[] nextHour = {0};
        final int[] eatTicks = {0}, workTicks = {0}, restTicks = {0};
        final int[] harvestedTicks = {0}, navigationTicks = {0};
        final int[] hungerMin = {farmer.hunger()}, hungerMax = {farmer.hunger()};
        final int[] lastBread = {farmer.backpack().countItem(Items.BREAD)};
        final int[] eaten = {0};
        final Set<BlockPos> positions = new HashSet<>();
        final int[] hourlyChecks = {0};
        final long[] lastMinuteReport = {startWall};

        System.out.println("REME_DAYWATCH_BEGIN uuid=" + subjectId
            + " name=DayWatchFarmer profession=FARMER realItemsBread=10"
            + " realCrops=9 gameDayTicks=24000 minRealWallSeconds="
            + (requiredWall / 1_000_000_000L));
        helper.onEachTick(() -> {
            long elapsed = level.getGameTime() - startGame;
            long wall = System.nanoTime() - startWall;
            if (!farmer.isAlive() || farmer.isRemoved()
                || !farmer.getUUID().equals(subjectId)) {
                helper.fail("DayWatch farmer dead, removed, or UUID changed", center);
                return;
            }
            String action = farmer.lastAction();
            if (farmer.intent() == LivingPlanner.Intent.EAT
                || action.startsWith("ate an item")) eatTicks[0]++;
            if (farmer.intent() == LivingPlanner.Intent.WORK) workTicks[0]++;
            if (farmer.intent() == LivingPlanner.Intent.SLEEP) restTicks[0]++;
            if (action.startsWith("harvested and replanted")) harvestedTicks[0]++;
            if (!farmer.getNavigation().isDone()) navigationTicks[0]++;
            hungerMin[0] = Math.min(hungerMin[0], farmer.hunger());
            hungerMax[0] = Math.max(hungerMax[0], farmer.hunger());
            int bread = farmer.backpack().countItem(Items.BREAD);
            if (bread < lastBread[0]) eaten[0] += lastBread[0] - bread;
            lastBread[0] = bread;

            // Exactly one record for each of the 24 Minecraft-hour
            // boundaries, plus a last sunrise result below.
            if (nextHour[0] < 24 && elapsed >= 1000L * nextHour[0]) {
                int hour = nextHour[0]++;
                BlockPos at = farmer.blockPosition();
                positions.add(at.immutable());
                hourlyChecks[0]++;
                int ripe = 0, growing = 0;
                for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++) {
                        var state = level.getBlockState(field.offset(dx, 0, dz));
                        if (state.is(Blocks.WHEAT)) {
                            if (state.getValue(CropBlock.AGE) == 7) ripe++;
                            else growing++;
                        }
                    }
                System.out.println(String.format(Locale.ROOT,
                    "REME_DAYWATCH_HOUR hour=%02d mcClock=%05d gameTicks=%d"
                        + " uuid=%s pos=%s intent=%s hunger=%d energy=%d social=%d"
                        + " bread=%d wheat=%d seeds=%d ripe=%d growing=%d"
                        + " moving=%s action=%s",
                    hour, Math.floorMod(level.getDayTime(), 24000L),
                    elapsed, subjectId, at.toShortString(), farmer.intent(),
                    farmer.hunger(), farmer.energy(), farmer.socialNeed(), bread,
                    farmer.backpack().countItem(Items.WHEAT),
                    farmer.backpack().countItem(Items.WHEAT_SEEDS), ripe, growing,
                    !farmer.getNavigation().isDone(), action.replace('\n', ' ')));
            }
            if (wall - lastMinuteReport[0] >= 60_000_000_000L) {
                lastMinuteReport[0] = wall;
                System.out.println("REME_DAYWATCH_WALL elapsedSeconds="
                    + (wall / 1_000_000_000L)
                    + " gameTicks=" + elapsed + " sameUUID=" + subjectId);
            }
            if (elapsed < 24000L || wall < requiredWall) return;

            String result = String.format(Locale.ROOT,
                "REME_DAYWATCH_RESULT uuid=%s ticks=%d realSeconds=%.1f"
                    + " hourlySamples=%d distinctPositions=%d eatIntentTicks=%d"
                    + " workIntentTicks=%d sleepIntentTicks=%d harvestActionTicks=%d"
                    + " navigationTicks=%d breadConsumed=%d hungerMin=%d"
                    + " hungerMax=%d alive=%s completedDay=%s",
                subjectId, elapsed, wall / 1e9, hourlyChecks[0],
                positions.size(), eatTicks[0], workTicks[0], restTicks[0],
                harvestedTicks[0], navigationTicks[0], eaten[0],
                hungerMin[0], hungerMax[0], farmer.isAlive(),
                elapsed >= 24000);
            System.out.println(result);
            if (hourlyChecks[0] != 24 || positions.size() < 2
                || eatTicks[0] == 0 || workTicks[0] == 0
                || restTicks[0] == 0 || eaten[0] == 0
                || harvestedTicks[0] == 0)
                helper.fail("One-day observed citizen did not eat/work/rest/"
                    + "harvest/move across 24 hourly logs; inspect REME_DAYWATCH_HOUR", center);
            else helper.succeed();
        });
    }
}
