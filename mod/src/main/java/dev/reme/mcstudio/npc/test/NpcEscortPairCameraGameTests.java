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
    public static void pair_camera_uses_high_vantage_only_when_normal_view_blocked(
            GameTestHelper helper) {
        Vec3 detainee = new Vec3(4.5, 5.5, 4.5);
        Vec3 officer = new Vec3(6.5, 5.5, 4.5);
        double midpointY = detainee.add(officer).scale(0.5).y;
        // Ordinary shots remain first choice; elevated cinematic framing
        // must not replace an already unobstructed street-level view.
        var ordinary = NpcCameraController.selectPairView(
            detainee, officer, 0, point -> true, point -> true);
        if (ordinary == null || ordinary.y() - midpointY > 0.5)
            throw new AssertionError("Valid normal escort framing was displaced");
        // Simulate a low, obstructed camera band. The final decision is
        // still gated by the real-world body and ray predicates at runtime.
        var elevated = NpcCameraController.selectPairView(
            detainee, officer, 0,
            point -> point.y > midpointY + 0.8, point -> true);
        if (elevated == null || elevated.y() - midpointY < 0.8)
            throw new AssertionError("Escort camera failed to try higher clear lens");
        // No candidate may bypass the collision or line-of-sight predicates:
        // this must fall back to genuine entity POV instead of wallhacking.
        if (NpcCameraController.selectPairView(detainee, officer, 0,
                point -> false, point -> true) != null
            || NpcCameraController.selectPairView(detainee, officer, 0,
                point -> true, point -> false) != null)
            throw new AssertionError("Camera ignored physical occlusion");
        helper.succeed();
    }

    /** An obstructed rear eye-level shot must NOT cause an elevated shot
     * when a clear street-level side shot exists. The old radius/yaw/height
     * loop incorrectly picked the elevated rear candidate first.
     */
    @GameTest(template="empty", timeoutTicks=100)
    public static void pair_camera_prefers_clear_side_street_level_over_high_rear(
            GameTestHelper helper) {
        Vec3 detainee = new Vec3(4.5, 5.5, 4.5);
        Vec3 officer = new Vec3(6.5, 5.5, 4.5);
        Vec3 mid = detainee.add(officer).scale(0.5);
        var lens = NpcCameraController.selectPairView(
            detainee, officer, 0,
            p -> (p.y > mid.y + 0.8
                    && Math.abs(p.x - mid.x) < 0.1
                    && p.z < mid.z - 4.0)
                || (Math.abs(p.y - (mid.y + 0.32)) < 0.01
                    && p.x > mid.x + 4.0),
            p -> true);
        if (lens == null || lens.y() - mid.y > 0.8
            || lens.x() <= mid.x + 4.0)
            throw new AssertionError(
                "High rear lens displaced a clear street-level side view");
        helper.succeed();
    }

    /** Reproduces a narrow real-world corridor: all coarse 0/60/115/180
     * candidate azimuths are obstructed, but the side-on 90-degree lens
     * sees BOTH real actors. A POV fallback here fills the entire screen
     * with one detainee's face instead of filming the escorted pair.
     */
    @GameTest(template="empty", timeoutTicks=100)
    public static void pair_camera_finds_narrow_side_corridor_lens(
            GameTestHelper helper) {
        Vec3 detainee = new Vec3(4.5, 5.5, 4.5);
        Vec3 officer = new Vec3(6.5, 5.5, 4.5);
        Vec3 mid = detainee.add(officer).scale(0.5);
        var lens = NpcCameraController.selectPairView(
            detainee, officer, 0,
            p -> Math.abs(p.z - mid.z) < 0.02
                && p.x > mid.x + 4.0
                && Math.abs(p.y - mid.y - 0.32) < 0.02,
            p -> true);
        if (lens == null || Math.abs(lens.z() - mid.z) > 0.02
            || lens.x() <= mid.x + 4.0)
            throw new AssertionError("Narrow 90-degree pair view was missed");
        // An occluded target is never a valid shot, regardless of angle.
        if (NpcCameraController.selectPairView(detainee, officer, 0,
                p -> true, p -> false) != null)
            throw new AssertionError("Pair camera bypassed sightline validation");
        helper.succeed();
    }

    /** A single citizen in a tight market aisle may only have a 30-degree
     * opening. This must remain a third-person real-world lens, not POV.
     */
    @GameTest(template="empty", timeoutTicks=100)
    public static void chase_camera_finds_narrow_thirty_degree_lens(
            GameTestHelper helper) {
        Vec3 eye = new Vec3(5.5, 5.5, 5.5);
        var lens = NpcCameraController.selectClearView(eye, 0,
            p -> p.x > eye.x + 1.4 && p.x < eye.x + 1.8
                && p.z < eye.z - 2.7 && p.z > eye.z - 3.0
                && Math.abs(p.y - eye.y - 0.32) < 0.02,
            p -> true);
        if (lens == null)
            throw new AssertionError("Clear 30-degree market aisle lens missed");
        helper.succeed();
    }

    /** A hidden guard must not force a face-filling POV when the real
     * prisoner still has a collision-free, visible third-person lens.
     * A genuine two-actor view always wins if both are visible.
     */
    @GameTest(template="empty", timeoutTicks=100)
    public static void blocked_officer_uses_safe_prisoner_only_camera(
            GameTestHelper helper) {
        Vec3 prisoner = new Vec3(4.5, 5.5, 4.5);
        Vec3 officer = new Vec3(10.5, 5.5, 4.5);
        var solo = NpcCameraController.selectEscortView(prisoner, officer, 0,
            p -> true, p -> true, p -> false);
        if (solo == null || solo.sharedLens()
            || new Vec3(solo.pose().x(), solo.pose().y(), solo.pose().z())
                .distanceTo(prisoner) < 1.8)
            throw new AssertionError("Occluded officer forced a closeup despite clear prisoner lens");
        var paired = NpcCameraController.selectEscortView(prisoner, officer, 0,
            p -> true, p -> true, p -> true);
        if (paired == null || !paired.sharedLens())
            throw new AssertionError("Clear two-actor view lost priority");
        if (NpcCameraController.selectEscortView(prisoner, officer, 0,
                p -> true, p -> false, p -> true) != null
            || NpcCameraController.selectEscortView(prisoner, officer, 0,
                p -> false, p -> true, p -> true) != null)
            throw new AssertionError("Camera saw through an occlusion or solid body");
        helper.succeed();
    }

    /** Real native stone partition: two loaded Minecraft NPCs on opposite
     * sides cannot share a clear raycast. The prisoner-side spectator must
     * remain in an actual free body volume and never look through the wall.
     */
    @GameTest(template="empty", timeoutTicks=100)
    public static void real_stone_wall_blocks_pair_but_allows_safe_solo(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(10, 2, 10));
        for (int dx = -4; dx <= 12; dx++)
            for (int dz = -10; dz <= 10; dz++) {
                BlockPos tile = base.offset(dx, 0, dz);
                level.setBlock(tile.below(), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int dy = 0; dy < 5; dy++)
                    level.setBlock(tile.above(dy), Blocks.AIR.defaultBlockState(), 2);
            }
        for (int dz = -9; dz <= 9; dz++)
            for (int dy = 0; dy < 5; dy++)
                level.setBlock(base.offset(3, dy, dz),
                    Blocks.STONE.defaultBlockState(), 2);
        PlayerNpcEntity prisoner = NpcRegistry.PLAYER_NPC.get().create(level);
        PlayerNpcEntity officer = NpcRegistry.PLAYER_NPC.get().create(level);
        if (prisoner == null || officer == null)
            throw new AssertionError("Real NPC entity registration missing");
        prisoner.moveTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0, 0);
        officer.moveTo(base.getX() + 6.5, base.getY(), base.getZ() + 0.5, 0, 0);
        if (!level.addFreshEntity(prisoner) || !level.addFreshEntity(officer))
            throw new AssertionError("Could not spawn real custody pair around stone wall");
        Vec3 first = prisoner.getEyePosition();
        Vec3 second = officer.getEyePosition();
        java.util.function.Predicate<Vec3> clear = p ->
            NpcCameraController.spectatorBodyClearInWorld(level, p, 1.62, 0.6, 1.8);
        java.util.function.Predicate<Vec3> seesFirst = p ->
            level.clip(new ClipContext(first, p, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, prisoner)).getType() == HitResult.Type.MISS;
        java.util.function.Predicate<Vec3> seesSecond = p ->
            level.clip(new ClipContext(second, p, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, officer)).getType() == HitResult.Type.MISS;
        var shot = NpcCameraController.selectEscortView(first, second, 0,
            clear, seesFirst, seesSecond);
        if (shot == null || shot.sharedLens())
            throw new AssertionError("Real stone wall was ignored or safe solo view missed");
        Vec3 lens = new Vec3(shot.pose().x(), shot.pose().y(), shot.pose().z());
        if (!clear.test(lens) || !seesFirst.test(lens) || seesSecond.test(lens))
            throw new AssertionError("Solo spectator intersected a real wall or saw officer through stone");
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
    public static void iron_bars_clearance_uses_real_partial_voxel_shape(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos bars = helper.absolutePos(new BlockPos(3, 3, 3));
        level.setBlock(bars, Blocks.IRON_BARS.defaultBlockState(), 2);
        // The body is inside the iron-bar BLOCK but stays in its real empty
        // corner, away from the thin central collision cross. An old
        // whole-block-is-solid check wrongly forced spectator POV here.
        Vec3 lensEye = new Vec3(bars.getX() + 0.08,
            bars.getY() + 1.72, bars.getZ() + 0.08);
        if (!NpcCameraController.spectatorBodyClearInWorld(
                level, lensEye, 1.62, 0.6, 1.8))
            throw new AssertionError("Clear corner beside iron bars rejected");
        // Replacing only the material with full stone must reject the exact
        // same body volume; this must never permit filming through walls.
        level.setBlock(bars, Blocks.STONE.defaultBlockState(), 2);
        if (NpcCameraController.spectatorBodyClearInWorld(
                level, lensEye, 1.62, 0.6, 1.8))
            throw new AssertionError("Solid stone intersects spectator body");
        if (NpcCameraController.spectatorBodyClearInWorld(
                level, new Vec3(Double.NaN, lensEye.y, lensEye.z),
                1.62, 0.6, 1.8))
            throw new AssertionError("Non-finite camera coordinate accepted");
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

    /** Keep a safe moving lens through turns; reject stale/blocked positions. */
    @GameTest(template="empty", timeoutTicks=100)
    public static void safe_chase_lens_tracks_walking_actor_without_orbit_cut(
            GameTestHelper helper) {
        Vec3 oldEye = new Vec3(5.5, 5.5, 5.5);
        Vec3 newEye = oldEye.add(0.45, 0, 0.65);
        var previous = NpcCameraController.selectClearView(oldEye, 0,
            p -> true, p -> true);
        if (previous == null) throw new AssertionError("No initial camera");
        var retained = NpcCameraController.retainSafeShot(previous,
            oldEye, newEye, p -> true, p -> true);
        if (retained == null) throw new AssertionError("Safe walking lens discarded");
        Vec3 oldRelative = new Vec3(previous.x(), previous.y(), previous.z())
            .subtract(oldEye);
        Vec3 newRelative = new Vec3(retained.x(), retained.y(), retained.z())
            .subtract(newEye);
        if (oldRelative.distanceToSqr(newRelative) > 0.000001)
            throw new AssertionError("Camera jumped to a new orbit on actor turn");
        if (NpcCameraController.retainSafeShot(previous,
                oldEye, newEye, p -> false, p -> true) != null
            || NpcCameraController.retainSafeShot(previous,
                oldEye, newEye, p -> true, p -> false) != null
            || NpcCameraController.retainSafeShot(previous,
                oldEye, oldEye.add(5, 0, 0), p -> true, p -> true) != null)
            throw new AssertionError("Unsafe or stale camera was retained");
        helper.succeed();
    }

    /** A real stone block must invalidate a previously safe moving camera. */
    @GameTest(template="empty", timeoutTicks=100)
    public static void stable_chase_lens_rechecks_real_stone_collision(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 3, 8));
        for (int dx = -6; dx <= 6; dx++)
            for (int dz = -8; dz <= 6; dz++) {
                BlockPos floor = base.offset(dx, -1, dz);
                level.setBlock(floor, Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int dy = 0; dy < 5; dy++)
                    level.setBlock(floor.above(dy + 1), Blocks.AIR.defaultBlockState(), 2);
            }
        Vec3 eye = new Vec3(base.getX() + 0.5, base.getY() + 1.62,
            base.getZ() + 0.5);
        var previous = NpcCameraController.selectClearView(eye, 0,
            p -> true, p -> true);
        if (previous == null) throw new AssertionError("No initial street lens");
        Vec3 next = eye.add(0.25, 0, 0.25);
        java.util.function.Predicate<Vec3> realBody = p ->
            NpcCameraController.spectatorBodyClearInWorld(
                level, p, 1.62, 0.6, 1.8);
        var safe = NpcCameraController.retainSafeShot(previous, eye, next,
            realBody, p -> true);
        if (safe == null) throw new AssertionError("Open street lens rejected");
        BlockPos obstruction = BlockPos.containing(
            new Vec3(safe.x(), safe.y() - 1.0, safe.z()));
        level.setBlock(obstruction, Blocks.STONE.defaultBlockState(), 2);
        if (NpcCameraController.retainSafeShot(previous, eye, next,
                realBody, p -> true) != null)
            throw new AssertionError("Old camera passed through a real stone block");
        helper.succeed();
    }

    /** Real GUI regression: spectator mode has no entity collision, so the
     *  previous "block-clear" orbit could park INSIDE a moving NPC's head.
     *  Use a genuinely spawned citizen and its vanilla bounding box; a
     *  different, still clear third-person angle must be selected instead.
     */
    @GameTest(template="empty", timeoutTicks=100)
    public static void real_npc_body_blocks_face_filling_spectator_lens(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 3, 8));
        Vec3 targetEye = new Vec3(origin.getX() + 0.5,
            origin.getY() + 1.62, origin.getZ() + 0.5);
        var first = NpcCameraController.selectClearView(
            targetEye, 0, p -> true, p -> true);
        if (first == null) throw new AssertionError("Missing baseline chase lens");
        PlayerNpcEntity bystander = NpcRegistry.PLAYER_NPC.get().create(level);
        if (bystander == null) throw new AssertionError("NPC registry unavailable");
        bystander.moveTo(first.x(), first.y() - bystander.getEyeHeight(),
            first.z(), 0, 0);
        if (!level.addFreshEntity(bystander))
            throw new AssertionError("Could not spawn real camera-occluding NPC");
        var nearby = level.getEntitiesOfClass(PlayerNpcEntity.class,
            bystander.getBoundingBox().inflate(12), PlayerNpcEntity::isAlive);
        Vec3 firstEye = new Vec3(first.x(), first.y(), first.z());
        if (NpcCameraController.lensClearOfNpcBodies(firstEye, nearby, 0.75))
            throw new AssertionError("Spectator lens accepted NPC face collision");
        var recovered = NpcCameraController.selectClearView(
            targetEye, 0,
            p -> NpcCameraController.lensClearOfNpcBodies(p, nearby, 0.75),
            p -> true);
        if (recovered == null)
            throw new AssertionError("Safe alternative third-person lens missed");
        Vec3 recoveredEye = new Vec3(recovered.x(), recovered.y(), recovered.z());
        if (recoveredEye.distanceToSqr(firstEye) < 0.1
            || !NpcCameraController.lensClearOfNpcBodies(recoveredEye, nearby, 0.75))
            throw new AssertionError("Camera still clips the real bystander");
        if (NpcCameraController.lensClearOfNpcBodies(
                new Vec3(Double.NaN, 2, 3), nearby, 0.75))
            throw new AssertionError("Non-finite camera lens accepted");
        helper.succeed();
    }

    /** A clear camera EYE is insufficient when its torso intersects a real
     * citizen standing just to the side. Spectator collision must use the
     * entire player volume, including the feet, before accepting a lens.
     */
    @GameTest(template="empty", timeoutTicks=100)
    public static void real_citizen_blocks_spectator_torso_even_when_eye_clear(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 3, 8));
        Vec3 eye = new Vec3(origin.getX() + 0.5,
            origin.getY() + 1.62, origin.getZ() + 0.5);
        PlayerNpcEntity bystander = NpcRegistry.PLAYER_NPC.get().create(level);
        if (bystander == null)
            throw new AssertionError("Real citizen entity registration missing");
        bystander.moveTo(eye.x + 0.7, eye.y - 1.62, eye.z, 0, 0);
        if (!level.addFreshEntity(bystander))
            throw new AssertionError("Could not spawn real bystander");
        var citizens = level.getEntitiesOfClass(PlayerNpcEntity.class,
            bystander.getBoundingBox().inflate(4), PlayerNpcEntity::isAlive);
        // The bystander's expanded box misses the lens eye itself, but
        // intersects the spectator's 0.6-block-wide torso.
        if (!NpcCameraController.lensClearOfNpcBodies(eye, citizens, 0.2))
            throw new AssertionError("Test precondition: camera eye is occluded");
        if (NpcCameraController.spectatorBodyClearOfNpcBodies(
                eye, 1.62, 0.6, 1.8, citizens, 0.2))
            throw new AssertionError("Camera torso clipped a real citizen");
        bystander.moveTo(eye.x + 4, eye.y - 1.62, eye.z, 0, 0);
        if (!NpcCameraController.spectatorBodyClearOfNpcBodies(
                eye, 1.62, 0.6, 1.8, citizens, 0.2))
            throw new AssertionError("Unobstructed spectator body was rejected");
        if (NpcCameraController.spectatorBodyClearOfNpcBodies(
                eye, 1.62, Double.NaN, 1.8, citizens, 0.2)
            || NpcCameraController.spectatorBodyClearOfNpcBodies(
                eye, 1.62, 0.6, 1.8, citizens, -1.0))
            throw new AssertionError("Invalid spectator geometry was accepted");
        helper.succeed();
    }
}
