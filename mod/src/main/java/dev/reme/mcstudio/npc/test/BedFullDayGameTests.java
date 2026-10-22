package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.NpcRegistry;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import dev.reme.mcstudio.npc.life.LivingPlanner;
import dev.reme.mcstudio.npc.life.SocietyRules;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Actual NeoForge GameTestServer runs a 24,000-tick resident life cycle.
 * This does NOT speed-run the planner by calling tick manually and it does not
 * pretend to benchmark a real 200-citizen FPS workload.
 */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BedFullDayGameTests {
    private BedFullDayGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 25200)
    public static void one_real_resident_actually_sleeps_and_wakes_over_a_whole_day(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(6, 9, 6));
        for (int x = -2; x <= 8; x++)
            for (int z = -2; z <= 8; z++) {
                BlockPos floor = origin.offset(x, -1, z);
                level.setBlock(floor, Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(floor.above(), Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(floor.above(2), Blocks.AIR.defaultBlockState(), 2);
            }
        BlockPos head = origin.offset(2, 0, 2);
        BlockPos foot = head.relative(Direction.SOUTH);
        level.setBlock(head, Blocks.RED_BED.defaultBlockState()
            .setValue(BedBlock.FACING, Direction.NORTH)
            .setValue(BedBlock.PART, BedPart.HEAD), 2);
        level.setBlock(foot, Blocks.RED_BED.defaultBlockState()
            .setValue(BedBlock.FACING, Direction.NORTH)
            .setValue(BedBlock.PART, BedPart.FOOT), 2);
        PlayerNpcEntity npc = NpcRegistry.PLAYER_NPC.get().create(level);
        if (npc == null) throw new AssertionError("No registered living NPC");
        npc.moveTo(origin.getX() + 4.5, origin.getY(), origin.getZ() + 2.5, 0, 0);
        npc.setHomePos(origin.offset(3, 0, 2));
        npc.setWorkPos(origin.offset(3, 0, 2));
        npc.setProfession(PlayerNpcEntity.Profession.RESIDENT);
        npc.setMode(PlayerNpcEntity.Mode.LIVE);
        npc.setRoamingRadius(16);
        npc.setIdentity(SocietyRules.civilian("bed_full_day_isolated"));
        npc.backpack().addItem(new ItemStack(Items.BREAD, 32));
        if (!level.addFreshEntity(npc)) throw new AssertionError("Could not create real resident");

        AtomicInteger enteredBed = new AtomicInteger();
        AtomicInteger leftBed = new AtomicInteger();
        AtomicInteger samplesInBed = new AtomicInteger();
        AtomicInteger checks = new AtomicInteger();
        AtomicInteger dayActivities = new AtomicInteger();
        final boolean[] lastSleeping = {false};
        final long wallStart = System.nanoTime();

        // 120 actual scheduled checks over 24k server ticks, never simulated
        // ticks or fabricated movement. Observe native world bed occupancy.
        for (int age = 200; age <= 23800; age += 200) {
            helper.runAfterDelay(age, () -> {
                if (!npc.isAlive() || npc.isRemoved())
                    helper.fail("Resident died/disappeared mid-24k day", origin);
                boolean asleep = npc.isSleeping();
                if (asleep && !lastSleeping[0]) enteredBed.incrementAndGet();
                if (!asleep && lastSleeping[0]) leftBed.incrementAndGet();
                lastSleeping[0] = asleep;
                if (asleep) {
                    samplesInBed.incrementAndGet();
                    if (!level.getBlockState(head).getValue(BedBlock.OCCUPIED))
                        helper.fail("Native entity sleeping without actual bed OCCUPIED state", head);
                } else if (npc.intent() != LivingPlanner.Intent.SLEEP) {
                    dayActivities.incrementAndGet();
                }
                checks.incrementAndGet();
            });
        }

        helper.runAfterDelay(24000, () -> {
            double wallSeconds = (System.nanoTime() - wallStart) / 1_000_000_000.0;
            System.out.println("REME_NPC_REAL_BED_DAYSOAK ticks=24000"
                + " samples=" + checks.get()
                + " sleepEntries=" + enteredBed.get()
                + " wakeExits=" + leftBed.get()
                + " sleepingSamples=" + samplesInBed.get()
                + " wakingActivities=" + dayActivities.get()
                + " hunger=" + npc.hunger() + " energy=" + npc.energy()
                + " wallSeconds=" + String.format(java.util.Locale.ROOT, "%.2f", wallSeconds)
                + " (game ticks, NOT 24 hours of real-world soak)");
            if (!npc.isAlive() || npc.isRemoved())
                helper.fail("NPC dead/removed after full game day", origin);
            else if (enteredBed.get() == 0 || leftBed.get() == 0)
                helper.fail("One full actual game day lacked native bed sleep/wake transitions", head);
            else if (samplesInBed.get() == 0 || dayActivities.get() == 0)
                helper.fail("One full game day lacked observable sleep or waking life", head);
            else helper.succeed();
        });
    }
}
