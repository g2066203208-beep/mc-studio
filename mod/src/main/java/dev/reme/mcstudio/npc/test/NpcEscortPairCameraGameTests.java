package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.NpcCameraController;
import dev.reme.mcstudio.npc.NpcRegistry;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native Minecraft geometry tests; only GUI footage can validate aesthetics. */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcEscortPairCameraGameTests {
    private NpcEscortPairCameraGameTests() {}

    @GameTest(template="empty", timeoutTicks=100)
    public static void escort_lens_aims_at_both_real_actor_positions(
            GameTestHelper helper) {
        Vec3 prisonerEye = new Vec3(4.5, 4.6, 8.5);
        Vec3 officerEye = new Vec3(10.5, 4.6, 8.5);
        Vec3 midpoint = prisonerEye.add(officerEye).scale(0.5);
        var lens = NpcCameraController.selectPairView(
            prisonerEye, officerEye, 0, p -> true, p -> true);
        if (lens == null) throw new AssertionError("No escort pair camera");
        Vec3 cameraEye = new Vec3(lens.x(), lens.y(), lens.z());
        if (cameraEye.distanceTo(midpoint) < 4.5)
            throw new AssertionError("Two actors cropped by single-actor closeup");
        double expectedYaw = Math.toDegrees(Math.atan2(
            midpoint.z - lens.z(), midpoint.x - lens.x())) - 90.0;
        double wrapped = Math.IEEEremainder(lens.yaw() - expectedYaw, 360.0);
        if (Math.abs(wrapped) > 0.02)
            throw new AssertionError("Camera looks at only one actor, not midpoint");
        helper.succeed();
    }

    @GameTest(template="empty", timeoutTicks=100)
    public static void spectator_eye_clear_but_body_in_stone_is_rejected(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos stone = helper.absolutePos(new BlockPos(3, 3, 3));
        level.setBlock(stone, Blocks.STONE.defaultBlockState(), 2);
        Vec3 lensEye = new Vec3(stone.getX() + 0.5,
            stone.getY() + 1.62, stone.getZ() + 0.5);
        if (!level.getBlockState(BlockPos.containing(lensEye))
                .getCollisionShape(level, BlockPos.containing(lensEye)).isEmpty())
            throw new AssertionError("Test precondition: lens eye must be in air");
        java.util.function.Predicate<Vec3> collisionFree = point -> {
            BlockPos at = BlockPos.containing(point);
            return level.hasChunkAt(at)
                && level.getBlockState(at).getCollisionShape(level, at).isEmpty();
        };
        if (NpcCameraController.spectatorBodyClear(lensEye, 1.62, collisionFree))
            throw new AssertionError("Eye-only check put spectator feet inside stone");
        level.setBlock(stone, Blocks.AIR.defaultBlockState(), 2);
        if (!NpcCameraController.spectatorBodyClear(lensEye, 1.62, collisionFree))
            throw new AssertionError("Unobstructed spectator body was rejected");
        helper.succeed();
    }

    @GameTest(template="empty", timeoutTicks=100)
    public static void solid_wall_between_escort_actors_never_fakes_joint_view(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos wall = helper.absolutePos(new BlockPos(12, 3, 12));
        // A genuine five-block-tall opaque Minecraft wall separates the
        // officer and detainee. No lens can have line of sight to BOTH.
        for (int z = -12; z <= 12; z++)
            for (int y = -1; y <= 3; y++)
                level.setBlock(wall.offset(0, y, z),
                    Blocks.STONE.defaultBlockState(), 2);
        PlayerNpcEntity actor = NpcRegistry.PLAYER_NPC.get().create(level);
        if (actor == null) throw new AssertionError("NPC registry unavailable");
        Vec3 prisonerEye = Vec3.atCenterOf(wall.west(3)).add(0, 0.12, 0);
        Vec3 officerEye = Vec3.atCenterOf(wall.east(3)).add(0, 0.12, 0);
        var lens = NpcCameraController.selectPairView(
            prisonerEye, officerEye, 0,
            p -> {
                BlockPos cell = BlockPos.containing(p);
                return level.hasChunkAt(cell)
                    && level.getBlockState(cell)
                        .getCollisionShape(level, cell).isEmpty();
            },
            p -> level.clip(new ClipContext(prisonerEye, p,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, actor))
                    .getType() == HitResult.Type.MISS
                && level.clip(new ClipContext(officerEye, p,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, actor))
                    .getType() == HitResult.Type.MISS);
        if (lens != null)
            throw new AssertionError("Camera claims both separated actors visible through stone");
        helper.succeed();
    }
}
