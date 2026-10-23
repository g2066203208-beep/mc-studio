package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.NpcCameraController;
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

/** Server-side geometry regressions for a genuine spectator camera.
 * Native GUI recording is still required for the final image-quality gate.
 */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcCameraFramingGameTests {
    private NpcCameraFramingGameTests() {}

    @GameTest(template="empty",timeoutTicks=100)
    public static void lens_world_eye_is_not_used_as_spectator_feet(
        GameTestHelper helper) {
        Vec3 eye = new Vec3(10.5, 3.62, 10.5);
        var lens = NpcCameraController.selectClearView(
            eye, 0, p -> true, p -> true);
        if (lens == null || lens.z() >= eye.z - 1.0
            || lens.y() < eye.y - 0.3 || lens.y() > eye.y + 0.5)
            throw new AssertionError("Camera must see the actual NPC at human eye height");
        float spectatorEyeHeight = 1.62F;
        Vec3 feet = NpcCameraController.spectatorFeetForLens(
            lens, spectatorEyeHeight);
        if (Math.abs((feet.y + spectatorEyeHeight) - lens.y()) > 0.0001
            || Math.abs(feet.y - lens.y()) < 1.0)
            throw new AssertionError("Spectator was teleported by eye, not feet: ceiling clip");
        helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void solid_shop_wall_forces_visible_side_orbit(
        GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(10,3,10));
        Vec3 eye = Vec3.atCenterOf(center).add(0,0.12,0);
        // Full-height real vanilla wall between a shopper and rear camera.
        for (int dx=-2;dx<=2;dx++)
            for (int dy=-1;dy<=3;dy++)
                level.setBlock(center.offset(dx,dy,-2),
                    Blocks.STONE.defaultBlockState(),2);
        var lens = NpcCameraController.selectClearView(eye,0,
            p -> {
                BlockPos cell=BlockPos.containing(p);
                return level.hasChunkAt(cell)
                    && level.getBlockState(cell)
                        .getCollisionShape(level,cell).isEmpty();
            },
            p -> level.clip(new ClipContext(eye,p,
                ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,null))
                .getType() == HitResult.Type.MISS);
        if(lens==null)
            throw new AssertionError("Camera failed to orbit around real shop wall");
        if(lens.z() < center.getZ()-2)
            throw new AssertionError("Camera still behind the opaque shop wall");
        BlockPos cell=BlockPos.containing(lens.x(),lens.y(),lens.z());
        if(!level.getBlockState(cell).getCollisionShape(level,cell).isEmpty())
            throw new AssertionError("Camera lens inside real Minecraft block");
        helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void completely_blocked_corridor_requires_native_pov_fallback(
        GameTestHelper helper) {
        var lens=NpcCameraController.selectClearView(
            new Vec3(0,2,0),90,p->false,p->true);
        if(lens!=null)
            throw new AssertionError("No line of sight must not create wallhack shot");
        helper.succeed();
    }
}
