package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.NpcRegistry;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import dev.reme.mcstudio.npc.life.LivingPlanner;
import dev.reme.mcstudio.npc.life.ResidentBedLife;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Regression for the watch-officer bedtime oscillation. A real registered
 * guard must retain the physical bed journey after a needs update crosses
 * the watch-energy threshold, but abandon it on a new daytime work intent.
 * This tests the routing ownership gate, not an instant or fake bed arrival;
 * the 24,000-tick city test separately verifies actual native sleep.
 */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GuardBedtimeCommitmentGameTests {
    private GuardBedtimeCommitmentGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void guard_does_not_abandon_real_bed_route_on_energy_recovery(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(6, 4, 6));
        for (int x = -2; x <= 8; x++) {
            for (int z = -2; z <= 8; z++) {
                BlockPos floor = origin.offset(x, -1, z);
                level.setBlock(floor, Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(floor.above(), Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(floor.above(2), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        BlockPos head = origin.offset(2, 0, 2);
        BlockPos foot = head.relative(Direction.SOUTH);
        level.setBlock(head, Blocks.RED_BED.defaultBlockState()
            .setValue(BedBlock.FACING, Direction.NORTH)
            .setValue(BedBlock.PART, BedPart.HEAD), 2);
        level.setBlock(foot, Blocks.RED_BED.defaultBlockState()
            .setValue(BedBlock.FACING, Direction.NORTH)
            .setValue(BedBlock.PART, BedPart.FOOT), 2);

        PlayerNpcEntity guard = NpcRegistry.PLAYER_NPC.get().create(level);
        if (guard == null) throw new AssertionError("Registered NPC missing");
        guard.moveTo(origin.getX() + 7.5, origin.getY(),
            origin.getZ() + 2.5, 0, 0);
        guard.setHomePos(origin.offset(3, 0, 2));
        guard.setWorkPos(origin.offset(3, 0, 2));
        guard.setAssignedBedPos(head);
        guard.setProfession(PlayerNpcEntity.Profession.GUARD);
        guard.setMode(PlayerNpcEntity.Mode.LIVE);
        guard.setRoamingRadius(16);
        guard.setDecision(new LivingPlanner.Decision(
            LivingPlanner.Intent.SLEEP, 1000, "night watch recovery"));
        if (!level.addFreshEntity(guard))
            throw new AssertionError("Real guard entity could not spawn");

        // Begin below the normal watch threshold, several blocks from bed.
        guard.changeNeeds(0, -100, 0);
        Vec3 start = guard.position();
        if (!ResidentBedLife.tick(guard, level, level.getGameTime()))
            throw new AssertionError("Tired guard did not begin real bedtime navigation");
        if (guard.isSleeping())
            throw new AssertionError("Guard slept without physically reaching bedside");

        // The old implementation immediately dropped the bed journey at 35
        // energy and allowed patrol to override the active home route.
        guard.changeNeeds(0, 100, 0);
        if (!ResidentBedLife.tick(guard, level, level.getGameTime()))
            throw new AssertionError("Guard abandoned bedtime when energy recovered");
        if (guard.isSleeping() || guard.position().distanceToSqr(start) > 0.01)
            throw new AssertionError("Bedtime commitment must not teleport a guard");

        // A real change of intent must release transient commitment.
        guard.setDecision(new LivingPlanner.Decision(
            LivingPlanner.Intent.WORK, 1000, "morning duty"));
        if (ResidentBedLife.tick(guard, level, level.getGameTime()))
            throw new AssertionError("Old bedtime survived a new work intent");
        guard.setDecision(new LivingPlanner.Decision(
            LivingPlanner.Intent.SLEEP, 1000, "not yet tired"));
        if (ResidentBedLife.tick(guard, level, level.getGameTime()))
            throw new AssertionError("Stale bedtime commitment survived work transition");
        helper.succeed();
    }
}
