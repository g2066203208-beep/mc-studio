package dev.reme.mcstudio.npc;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Native Java Edition possession camera + player-only cinematic chase.
 * Attached targets are actual UUIDs (not unstable coordinates or screenshots).
 *
 * This never moves the NPC, modifies entity AI or force-loads chunks.
 * A player must deliberately enter spectator mode; camera automatically
 * disengages when the target is gone, spectator ends or the player leaves.
 */
public final class NpcCameraController {
    private NpcCameraController() {}
    public enum Mode { POV, CHASE }
    public record CameraPose(double x, double y, double z, float yaw, float pitch) {}
    /** Whether a physically clear shot includes both linked custody actors. */
    public record EscortView(CameraPose pose, boolean sharedLens) {}
    private record Follow(UUID target, Mode mode) {}
    /** Last collision-checked spectator lens, anchored to the filmed actor(s).
     *  Retaining a safe relative lens avoids 180-degree camera cuts whenever
     *  an NPC turns while walking. No NPC position is ever changed.
     */
    private record StableShot(Vec3 anchor, CameraPose pose,
                              @Nullable UUID partnerId, boolean sharedLens) {}
    private static final Map<UUID, StableShot> STABLE_SHOTS = new HashMap<>();
    private static final Map<UUID, Follow> ACTIVE = new HashMap<>();
    // A cramped corridor may offer no collision-free third-person lens.
    // Native spectator POV is the honest fallback, never a wall-clipping shot.
    private static final Set<UUID> POV_FALLBACK = new HashSet<>();

    @Nullable public static PlayerNpcEntity find(ServerPlayer player, String role) {
        String tag = switch (role) {
            case "shoplifter" -> "reme_city_shoplifter";
            case "clerk" -> "reme_city_clerk";
            case "guard" -> "reme_city_guard";
            case "park" -> "reme_city_park";
            case "nearest" -> null;
            default -> "";
        };
        if ("".equals(tag)) return null;
        return player.serverLevel().getEntitiesOfClass(
            PlayerNpcEntity.class, player.getBoundingBox().inflate(64),
            npc -> npc.isAlive() && (tag == null || npc.getTags().contains(tag)))
            .stream().min(Comparator.comparingDouble(player::distanceToSqr))
            .orElse(null);
    }

    public static boolean start(ServerPlayer player, PlayerNpcEntity npc, Mode mode) {
        if (!player.gameMode.getGameModeForPlayer().equals(GameType.SPECTATOR)
            || npc == null || !npc.isAlive() || npc.level() != player.level()
            || !player.serverLevel().hasChunkAt(npc.blockPosition())) return false;
        stop(player);
        ACTIVE.put(player.getUUID(), new Follow(npc.getUUID(), mode));
        if (mode == Mode.POV) {
            // Minecraft's native /spectate entity mode: the server sends the
            // proper camera packet, and the client follows entity movement.
            player.setCamera(npc);
        } else {
            // A genuine third-person camera 4 blocks behind the *same* NPC.
            // Only the spectator is moved; inmate/guard never teleported.
            player.setCamera(player);
            if (!moveChase(player, npc)) player.setCamera(npc);
        }
        return true;
    }

    public static void stop(ServerPlayer player) {
        ACTIVE.remove(player.getUUID());
        STABLE_SHOTS.remove(player.getUUID());
        POV_FALLBACK.remove(player.getUUID());
        if (player.getCamera() != player) player.setCamera(player);
    }

    public static String status(ServerPlayer player) {
        Follow f = ACTIVE.get(player.getUUID());
        if (f == null) return "NPC camera OFF";
        return "NPC camera " + f.mode + " target=" + f.target;
    }

    /** Geometric follow view; chooses a physical angle based on actual yaw. */
    public static CameraPose behind(Vec3 target, float targetYaw,
                                     Vec3 targetEye, double radius) {
        double yawRad = Math.toRadians(targetYaw);
        double x = target.x + Math.sin(yawRad) * radius;
        double z = target.z - Math.cos(yawRad) * radius;
        double y = target.y + 2.6;
        double dx = targetEye.x - x, dz = targetEye.z - z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float lookYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
        float lookPitch = (float) -Math.toDegrees(Math.atan2(targetEye.y - y, horizontal));
        return new CameraPose(x,y,z,lookYaw,lookPitch);
    }

    /** A lens is a WORLD EYE point, not a spectator's feet. The original
     *  client camera used the lens as /tp feet and therefore rendered from
     *  1.62 blocks too high, clipping ceilings and staring at shop floors.
     *  Candidate orbits are deterministic: do not randomly jump cameras.
     *  The predicates are injectable for actual native GameTest coverage.
     */
    @Nullable
    public static CameraPose selectClearView(Vec3 targetEye, float targetYaw,
                                             Predicate<Vec3> clearLens,
                                             Predicate<Vec3> unobstructed) {
        return selectClearViewWithRadii(targetEye, targetYaw, clearLens,
            unobstructed, new double[]{3.3, 2.6, 1.85},
            new double[]{0.32, 0.05, -0.20});
    }

    /** Frame two ACTUAL mutually linked escort actors rather than filming
     * only the prisoner or pointing into a nearby wall. The midpoint is a
     * camera focus, never a teleport destination for either NPC.
     * Both sight lines must be physically clear in the loaded Minecraft world.
     */
    @Nullable
    public static CameraPose selectPairView(Vec3 firstEye, Vec3 secondEye,
                                            float targetYaw,
                                            Predicate<Vec3> clearLens,
                                            Predicate<Vec3> seesBoth) {
        Vec3 midpoint = firstEye.add(secondEye).scale(0.5);
        double distance = firstEye.distanceTo(secondEye);
        double far = Math.max(4.5, Math.min(9.0, 4.0 + distance * 0.8));
        // Preserve the natural eye-level view first. Only when all such
        // physically valid shots fail, try a higher spectator vantage above
        // short market stalls/road furniture. Native body collision and BOTH
        // actor sight lines remain mandatory; this never moves an NPC.
        return selectClearViewWithRadii(midpoint, targetYaw, clearLens,
            seesBoth, new double[]{far, far * 0.82, far * 0.65},
            new double[]{0.32, 0.05, -0.20, 1.10, 1.75});
    }

    /**
     * A narrow real street or jail corridor can hide the officer behind a
     * wall even when the prisoner is clearly visible from a safe third-person
     * position. Never claim a shared lens without both native raycasts, but
     * prefer a genuine target-only exterior view over face-filling entity POV.
     * This changes only the spectator camera, never either NPC's position.
     */
    @Nullable
    public static EscortView selectEscortView(
            Vec3 prisonerEye, Vec3 officerEye, float targetYaw,
            Predicate<Vec3> clearLens, Predicate<Vec3> seesPrisoner,
            Predicate<Vec3> seesOfficer) {
        CameraPose paired = selectPairView(prisonerEye, officerEye,
            targetYaw, clearLens,
            lens -> seesPrisoner.test(lens) && seesOfficer.test(lens));
        if (paired != null) return new EscortView(paired, true);
        CameraPose solo = selectClearView(prisonerEye, targetYaw,
            clearLens, seesPrisoner);
        return solo == null ? null : new EscortView(solo, false);
    }

    @Nullable
    private static CameraPose selectClearViewWithRadii(
            Vec3 targetEye, float targetYaw, Predicate<Vec3> clearLens,
            Predicate<Vec3> unobstructed, double[] radii,
            double[] heightOffsets) {
        // Sweep the full street-level orbit before resorting to the NPC's face.
        // Narrow shop aisles and iron-bar corridors can have a clear 30° or
        // 90° lens even when all six coarse angles are obstructed. Every
        // candidate still must pass native spectator body collision and
        // both-actor line-of-sight checks; never see through real walls.
        double[] yawOffsets = {0, 30, -30, 60, -60, 90, -90,
                               115, -115, 150, -150, 180};
        // Try every street-level angle before ANY elevated angle. The old
        // radius -> yaw -> height order selected an elevated rear shot as
        // soon as the first rear low lens was blocked, even when a clear
        // eye-level SIDE view existed. This made indoor scenes unnecessarily
        // top-down and hid the two walking actors behind furniture.
        for (double height : heightOffsets) {
            for (double radius : radii) {
                for (double offset : yawOffsets) {
                    double angle = Math.toRadians(targetYaw + offset);
                    Vec3 lens = targetEye.add(
                        Math.sin(angle) * radius, height,
                        -Math.cos(angle) * radius);
                    if (!clearLens.test(lens) || !unobstructed.test(lens)) continue;
                    Vec3 focus = targetEye.add(0, -0.32, 0);
                    double dx = focus.x - lens.x;
                    double dy = focus.y - lens.y;
                    double dz = focus.z - lens.z;
                    float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
                    float pitch = (float) -Math.toDegrees(
                        Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
                    return new CameraPose(lens.x, lens.y, lens.z, yaw, pitch);
                }
            }
        }
        return null;
    }

    /** Translate an existing camera lens with its real actor(s), rather than
     *  instantly orbiting to the opposite side when a walking NPC turns.
     *  Revalidate the entire spectator body and all required actor raycasts
     *  against the CURRENT world. Unsafe, stale or distant shots are rejected.
     *  This is only a camera calculation; no entity is moved or force-loaded.
     */
    @Nullable
    public static CameraPose retainSafeShot(CameraPose previous, Vec3 oldAnchor,
            Vec3 currentAnchor, Predicate<Vec3> clearLens,
            Predicate<Vec3> visible) {
        if (previous == null || oldAnchor == null || currentAnchor == null
            || !Double.isFinite(previous.x()) || !Double.isFinite(previous.y())
            || !Double.isFinite(previous.z())
            || !Double.isFinite(oldAnchor.x) || !Double.isFinite(oldAnchor.y)
            || !Double.isFinite(oldAnchor.z)
            || !Double.isFinite(currentAnchor.x) || !Double.isFinite(currentAnchor.y)
            || !Double.isFinite(currentAnchor.z)) return null;
        Vec3 travel = currentAnchor.subtract(oldAnchor);
        // An actor teleported by another mod or a stale saved shot must not
        // drag the spectator across an unobserved area.
        if (travel.lengthSqr() > 16.0) return null;
        Vec3 lens = new Vec3(previous.x(), previous.y(), previous.z()).add(travel);
        double distanceSq = lens.distanceToSqr(currentAnchor);
        if (distanceSq < 4.0 || distanceSq > 121.0
            || !clearLens.test(lens) || !visible.test(lens)) return null;
        Vec3 focus = currentAnchor.add(0, -0.32, 0);
        double dx = focus.x - lens.x, dz = focus.z - lens.z;
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
        float pitch = (float) -Math.toDegrees(
            Math.atan2(focus.y - lens.y, Math.sqrt(dx * dx + dz * dz)));
        return new CameraPose(lens.x, lens.y, lens.z, yaw, pitch);
    }

    /** Vanilla server teleport positions the FEET, not the eye camera.
     * Never silently substitute eye coordinates for player feet.
     */
    public static Vec3 spectatorFeetForLens(CameraPose lens, float eyeHeight) {
        return new Vec3(lens.x(), lens.y() - eyeHeight, lens.z());
    }

    /** Reject a camera whose eye is clear but whose spectator body intersects
     * a real wall, door or floor. The original eye-only check could place the
     * spectator's FEET inside stone while its lens remained in open air.
     * Sample the body volume without loading any additional chunks.
     */
    public static boolean spectatorBodyClear(Vec3 lensEye, double eyeHeight,
                                             Predicate<Vec3> collisionFree) {
        if (!Double.isFinite(eyeHeight) || eyeHeight <= 0.0) return false;
        for (double height : new double[]{0.08, eyeHeight * 0.55, eyeHeight + 0.16})
            for (double ox : new double[]{-0.28, 0.0, 0.28})
                for (double oz : new double[]{-0.28, 0.0, 0.28})
                    if (!collisionFree.test(lensEye.add(ox, height - eyeHeight, oz)))
                        return false;
        return true;
    }

    /** Check the spectator's ACTUAL rectangular body against voxel collision
     * geometry, not merely whether any touched block has a nonempty shape.
     * Iron bars, open doors and fences occupy only part of a block; treating
     * their entire block as solid caused needless first-person fallback.
     * Reject unloaded chunks and solid intersections; never force-load them.
     */
    public static boolean spectatorBodyClearInWorld(ServerLevel level,
            Vec3 lensEye, double eyeHeight, double bodyWidth, double bodyHeight) {
        if (lensEye == null || !Double.isFinite(lensEye.x)
            || !Double.isFinite(lensEye.y) || !Double.isFinite(lensEye.z)
            || !Double.isFinite(eyeHeight) || eyeHeight <= 0.0
            || !Double.isFinite(bodyWidth) || bodyWidth <= 0.002
            || !Double.isFinite(bodyHeight) || bodyHeight <= 0.002)
            return false;
        double half = bodyWidth * 0.5;
        Vec3 feet = lensEye.add(0, -eyeHeight, 0);
        // A tiny epsilon avoids rejecting a legal face-to-face contact at
        // exact voxel boundaries; it never permits an actual overlap.
        net.minecraft.world.phys.AABB body = new net.minecraft.world.phys.AABB(
            feet.x - half + 0.001, feet.y + 0.001, feet.z - half + 0.001,
            feet.x + half - 0.001, feet.y + bodyHeight - 0.001,
            feet.z + half - 0.001);
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(body.minX, body.minY, body.minZ),
                BlockPos.containing(body.maxX, body.maxY, body.maxZ))) {
            if (!level.hasChunkAt(pos) || !level.isInWorldBounds(pos)
                || !level.getWorldBorder().isWithinBounds(pos)) return false;
            var shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            for (net.minecraft.world.phys.AABB piece : shape.toAabbs())
                if (body.intersects(piece.move(pos))) return false;
        }
        return true;
    }

    /** A spectator has no normal entity collision: a geometrically legal
     *  lens can sit INSIDE a moving citizen's head, producing the face-filling
     *  foreground seen in real GUI footage. Reject those candidate eyes using
     *  ACTUAL loaded NPC bounding boxes, without moving any NPC or chunk.
     *  The nearby list is collected once per camera update, not per orbit ray.
     */
    public static boolean lensClearOfNpcBodies(Vec3 lensEye,
            Iterable<PlayerNpcEntity> citizens, double clearance) {
        if (lensEye == null || !Double.isFinite(lensEye.x)
            || !Double.isFinite(lensEye.y) || !Double.isFinite(lensEye.z)
            || !Double.isFinite(clearance) || clearance < 0.0
            || citizens == null) return false;
        for (PlayerNpcEntity citizen : citizens)
            if (citizen != null && citizen.isAlive()
                && citizen.getBoundingBox().inflate(clearance).contains(lensEye))
                return false;
        return true;
    }

    /** Spectator eyes may be outside a citizen's head while the camera's
     *  FEET or torso still intersect that citizen. Native spectator mode
     *  ignores entity collisions, so validate the whole camera body against
     *  actual loaded NPC bounding boxes. No entity is moved or chunk loaded.
     */
    public static boolean spectatorBodyClearOfNpcBodies(Vec3 lensEye,
            double eyeHeight, double bodyWidth, double bodyHeight,
            Iterable<PlayerNpcEntity> citizens, double clearance) {
        if (lensEye == null || !Double.isFinite(lensEye.x)
            || !Double.isFinite(lensEye.y) || !Double.isFinite(lensEye.z)
            || !Double.isFinite(eyeHeight) || eyeHeight <= 0.0
            || !Double.isFinite(bodyWidth) || bodyWidth <= 0.0
            || !Double.isFinite(bodyHeight) || bodyHeight <= 0.0
            || !Double.isFinite(clearance) || clearance < 0.0
            || citizens == null) return false;
        double half = bodyWidth * 0.5;
        double feetY = lensEye.y - eyeHeight;
        net.minecraft.world.phys.AABB spectator = new net.minecraft.world.phys.AABB(
            lensEye.x - half, feetY, lensEye.z - half,
            lensEye.x + half, feetY + bodyHeight, lensEye.z + half);
        for (PlayerNpcEntity citizen : citizens)
            if (citizen != null && citizen.isAlive()
                && spectator.intersects(citizen.getBoundingBox().inflate(clearance)))
                return false;
        return true;
    }

    /** Never teleport the NPC or force-load a chunk to make a pretty shot.
     * If no valid lens exists, use genuine entity POV until space reopens.
     */
    private static boolean moveChase(ServerPlayer player, PlayerNpcEntity target) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = target.getEyePosition();
        // Follow either member of the REAL officer/prisoner pair. A nearby
        // unrelated guard is never accepted as a cinematic stand-in.
        UUID partnerId = target.custodyOfficer() != null
            ? target.custodyOfficer() : target.escortedPrisoner();
        Entity linked = partnerId == null ? null : level.getEntity(partnerId);
        PlayerNpcEntity partner = linked instanceof PlayerNpcEntity other
            && other.isAlive() && other.level() == level
            && target.distanceToSqr(other) <= 100.0
            && (target.custodyOfficer() != null
                ? target.getUUID().equals(other.escortedPrisoner())
                : target.getUUID().equals(other.custodyOfficer()))
            ? other : null;
        // Sample actual citizens once: spectator mode ignores their collision
        // boxes, so a block-clear lens can otherwise be INSIDE a bystander's
        // face. Never force-load distant chunks to clear the frame.
        var nearbyCitizens = level.getEntitiesOfClass(PlayerNpcEntity.class,
            target.getBoundingBox().inflate(18.0), PlayerNpcEntity::isAlive);
        Predicate<Vec3> clearLens = lens -> spectatorBodyClearInWorld(
            level, lens, player.getEyeHeight(),
            player.getBbWidth(), player.getBbHeight())
            && spectatorBodyClearOfNpcBodies(lens, player.getEyeHeight(),
                player.getBbWidth(), player.getBbHeight(), nearbyCitizens, 0.75);
        Predicate<Vec3> targetVisible = lens -> level.clip(new ClipContext(
            eye, lens, ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, target)).getType() == HitResult.Type.MISS;
        CameraPose chosen;
        boolean sharedLens = false;
        if (partner != null) {
            Vec3 partnerEye = partner.getEyePosition();
            EscortView view = selectEscortView(eye, partnerEye, target.getYRot(),
                clearLens, targetVisible,
                lens -> level.clip(new ClipContext(partnerEye, lens,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                    partner)).getType() == HitResult.Type.MISS);
            chosen = view == null ? null : view.pose();
            sharedLens = view != null && view.sharedLens();
        } else {
            chosen = selectClearView(eye, target.getYRot(),
                clearLens, targetVisible);
        }
        // Keep a safe previous camera-relative orbit through ordinary turns.
        // If a wall, iron door or partner occludes it, immediately use the
        // freshly raycast candidate above instead. Never trust stale geometry.
        UUID partnerIdForShot = partner == null ? null : partner.getUUID();
        Vec3 anchor = sharedLens
            ? eye.add(partner.getEyePosition()).scale(0.5) : eye;
        StableShot previous = STABLE_SHOTS.get(player.getUUID());
        if (chosen != null && previous != null
            && java.util.Objects.equals(previous.partnerId(), partnerIdForShot)
            && previous.sharedLens() == sharedLens) {
            Predicate<Vec3> currentSight = targetVisible;
            if (sharedLens) {
                Vec3 officerEye = partner.getEyePosition();
                currentSight = lens -> targetVisible.test(lens)
                    && level.clip(new ClipContext(officerEye, lens,
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                        partner)).getType() == HitResult.Type.MISS;
            }
            CameraPose retained = retainSafeShot(previous.pose(),
                previous.anchor(), anchor, clearLens, currentSight);
            if (retained != null) chosen = retained;
        }
        if (chosen == null) STABLE_SHOTS.remove(player.getUUID());
        else STABLE_SHOTS.put(player.getUUID(),
            new StableShot(anchor, chosen, partnerIdForShot, sharedLens));
        if (partner != null && player.tickCount % 40 == 0) {
            com.mojang.logging.LogUtils.getLogger().info(
                "REME_NPC_CAMERA_PAIR_VIEW observer={} target={} partner={} "
                    + "actorDistance={} sharedLens={} soloLens={} lensEye={}",
                player.getUUID(), target.getUUID(), partner.getUUID(),
                target.distanceTo(partner), sharedLens,
                chosen != null && !sharedLens,
                chosen == null ? "none" : new Vec3(chosen.x(), chosen.y(), chosen.z()));
        }
        if (chosen == null) {
            if (POV_FALLBACK.add(player.getUUID())) {
                com.mojang.logging.LogUtils.getLogger().info(
                    "REME_NPC_CAMERA_FALLBACK uuid={} target={} reason=no_clear_lens",
                    player.getUUID(), target.getUUID());
            }
            return false;
        }
        if (POV_FALLBACK.remove(player.getUUID())) {
            com.mojang.logging.LogUtils.getLogger().info(
                "REME_NPC_CAMERA_RECOVERED uuid={} target={} eye=({}, {}, {})",
                player.getUUID(), target.getUUID(),
                chosen.x(), chosen.y(), chosen.z());
        }
        if (player.getCamera() != player) player.setCamera(player);
        Vec3 feet = spectatorFeetForLens(chosen, player.getEyeHeight());
        player.connection.teleport(feet.x, feet.y, feet.z,
            chosen.yaw, chosen.pitch);
        return true;
    }

    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Follow f = ACTIVE.get(player.getUUID());
        if (f == null) return;
        if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
            stop(player);
            return;
        }
        Entity entity = player.serverLevel().getEntity(f.target);
        if (!(entity instanceof PlayerNpcEntity target) || !target.isAlive()
            || !player.serverLevel().hasChunkAt(target.blockPosition())) {
            stop(player);
            player.sendSystemMessage(Component.literal(
                "NPC camera stopped: citizen died, unloaded, or left this dimension"));
            return;
        }
        if (f.mode == Mode.POV) {
            // Reattach if the spectator pressed SHIFT (native detach).
            if (player.getCamera() != target) {
                ACTIVE.remove(player.getUUID());
            }
        } else if (player.tickCount % 2 == 0) {
            if (!moveChase(player,target) && player.getCamera() != target)
                player.setCamera(target);
        }
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) stop(player);
    }
}
