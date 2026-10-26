package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.NpcCameraController;
import dev.reme.mcstudio.npc.NpcRegistry;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Real vanilla stone-room camera regression. A GUI video must still judge
 * whether the resulting close shot is visually comfortable.
 */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcCompactJailCameraGameTests {
    private NpcCompactJailCameraGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void cramped_real_stone_room_allows_safe_compact_spectator(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(10, 2, 10));
        // Three traversable blocks across, surrounded by real four-high stone
        // walls. A normal 1.85-block orbit cannot fit the spectator's body.
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                level.setBlock(center.offset(dx, -1, dz),
                    Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int dy = 0; dy < 4; dy++)
                    level.setBlock(center.offset(dx, dy, dz),
                        Math.abs(dx) == 2 || Math.abs(dz) == 2
                            ? Blocks.STONE.defaultBlockState()
                            : Blocks.AIR.defaultBlockState(), 2);
            }
        PlayerNpcEntity citizen = NpcRegistry.PLAYER_NPC.get().create(level);
        if (citizen == null) throw new AssertionError("NPC type not registered");
        citizen.moveTo(center.getX() + 0.5, center.getY(),
            center.getZ() + 0.5, 0, 0);
        if (!level.addFreshEntity(citizen))
            throw new AssertionError("Could not spawn a real NPC in the room");
        Vec3 original = citizen.position();
        Vec3 eye = citizen.getEyePosition();
        Predicate<Vec3> blocksClear = lens ->
            NpcCameraController.spectatorBodyClearInWorld(
                level, lens, 1.62, 0.6, 1.8);
        Predicate<Vec3> strictClear = lens -> blocksClear.test(lens)
            && NpcCameraController.spectatorBodyClearOfNpcBodies(
                lens, 1.62, 0.6, 1.8, List.of(citizen), 0.75);
        Predicate<Vec3> compactClear = lens -> blocksClear.test(lens)
            && NpcCameraController.spectatorBodyClearOfNpcBodies(
                lens, 1.62, 0.6, 1.8, List.of(citizen), 0.20);
        Predicate<Vec3> visible = lens -> level.clip(new ClipContext(
            eye, lens, ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, citizen)).getType() == HitResult.Type.MISS;

        if (NpcCameraController.selectClearView(
                eye, 0, strictClear, visible) != null)
            throw new AssertionError(
                "Test fixture too wide: ordinary camera unexpectedly fits");
        var shot = NpcCameraController.selectCompactView(
            eye, 0, compactClear, visible);
        if (shot == null)
            throw new AssertionError(
                "No collision-free compact third-person view inside real room");
        Vec3 lens = new Vec3(shot.x(), shot.y(), shot.z());
        if (!compactClear.test(lens) || !visible.test(lens))
            throw new AssertionError("Compact camera intersects real blocks or NPC");
        if (lens.distanceTo(eye) < 1.3 || lens.distanceTo(eye) > 1.8)
            throw new AssertionError("Compact shot is too close or too distant");
        if (!citizen.position().equals(original))
            throw new AssertionError("Camera selection illegally moved NPC");
        if (NpcCameraController.selectCompactView(
                eye, 0, p -> false, visible) != null
            || NpcCameraController.selectCompactView(
                eye, 0, compactClear, p -> false) != null)
            throw new AssertionError("Compact camera bypassed physical safety");
        helper.succeed();
    }
}
