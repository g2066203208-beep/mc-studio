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
    private record Follow(UUID target, Mode mode) {}
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
            unobstructed, new double[]{3.3, 2.6, 1.85});
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
        return selectClearViewWithRadii(midpoint, targetYaw, clearLens,
            seesBoth, new double[]{far, far * 0.82, far * 0.65});
    }

    @Nullable
    private static CameraPose selectClearViewWithRadii(
            Vec3 targetEye, float targetYaw, Predicate<Vec3> clearLens,
            Predicate<Vec3> unobstructed, double[] radii) {
        double[] yawOffsets = {0, 60, -60, 115, -115, 180};
        double[] heightOffsets = {0.32, 0.05, -0.20};
        for (double radius : radii) {
            for (double offset : yawOffsets) {
                double angle = Math.toRadians(targetYaw + offset);
                for (double height : heightOffsets) {
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
        Predicate<Vec3> clearLens = lens -> spectatorBodyClearInWorld(
            level, lens, player.getEyeHeight(),
            player.getBbWidth(), player.getBbHeight());
        Predicate<Vec3> targetVisible = lens -> level.clip(new ClipContext(
            eye, lens, ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, target)).getType() == HitResult.Type.MISS;
        CameraPose chosen;
        if (partner != null) {
            Vec3 partnerEye = partner.getEyePosition();
            chosen = selectPairView(eye, partnerEye, target.getYRot(),
                clearLens, lens -> targetVisible.test(lens)
                    && level.clip(new ClipContext(partnerEye, lens,
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                        partner)).getType() == HitResult.Type.MISS);
        } else {
            chosen = selectClearView(eye, target.getYRot(),
                clearLens, targetVisible);
        }
        if (partner != null && player.tickCount % 40 == 0) {
            com.mojang.logging.LogUtils.getLogger().info(
                "REME_NPC_CAMERA_PAIR_VIEW observer={} target={} partner={} "
                    + "actorDistance={} sharedLens={} lensEye={}",
                player.getUUID(), target.getUUID(), partner.getUUID(),
                target.distanceTo(partner), chosen != null,
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
