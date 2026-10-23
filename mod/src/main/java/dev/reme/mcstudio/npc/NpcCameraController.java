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
        double[] radii = {3.3, 2.6, 1.85};
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
                    // Aim at upper chest, not the ground. Actor stays in frame.
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

    /** Never teleport the NPC or force-load a chunk to make a pretty shot.
     * If no valid lens exists, use genuine entity POV until space reopens.
     */
    private static boolean moveChase(ServerPlayer player, PlayerNpcEntity target) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = target.getEyePosition();
        CameraPose chosen = selectClearView(eye, target.getYRot(),
            lens -> {
                // A camera INSIDE stone, glass, iron bars or a low ceiling is
                // not a valid view, even when its center ray is unobstructed.
                for (double ox : new double[]{-.19, .0, .19})
                    for (double oz : new double[]{-.19, .0, .19}) {
                        BlockPos block = BlockPos.containing(
                            lens.add(ox, 0, oz));
                        if (!level.hasChunkAt(block)
                            || !level.getBlockState(block)
                                .getCollisionShape(level, block).isEmpty())
                            return false;
                    }
                return true;
            },
            lens -> level.clip(new ClipContext(
                eye, lens, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, target)).getType()
                == HitResult.Type.MISS);
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
