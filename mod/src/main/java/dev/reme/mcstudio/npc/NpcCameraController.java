package dev.reme.mcstudio.npc;

import java.util.Comparator;
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

    @Nullable public static PlayerNpcEntity find(ServerPlayer player, String role) {
        String tag = switch (role) {
            case "shoplifter" -> "reme_city_shoplifter";
            case "clerk" -> "reme_city_clerk";
            case "guard" -> "reme_city_guard";
            case "park" -> "reme_city_park";
            case "office" -> "reme_city_office";
            case "farmer" -> "reme_city_farmer";
            case "baker" -> "reme_city_baker";
            case "courier" -> "reme_city_courier";
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
            moveChase(player, npc);
        }
        return true;
    }

    public static void stop(ServerPlayer player) {
        ACTIVE.remove(player.getUUID());
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

    private static void moveChase(ServerPlayer player, PlayerNpcEntity target) {
        ServerLevel level = player.serverLevel();
        CameraPose desired = behind(target.position(), target.getYRot(),
            target.getEyePosition(), 4.25);
        Vec3 eye = target.getEyePosition();
        Vec3 camera = new Vec3(desired.x, desired.y, desired.z);
        // Collision-aware spectator framing: keep the camera on the target
        // side of actual blocks, not behind the back wall of a convenience shop.
        var hit = level.clip(new ClipContext(eye,camera,
            ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        if (hit.getType() == HitResult.Type.BLOCK)
            camera = eye.add(hit.getLocation().subtract(eye).scale(.85));
        BlockPos cameraCell = BlockPos.containing(camera);
        if (!level.hasChunkAt(cameraCell)) return;
        double dx = eye.x-camera.x, dy = eye.y-camera.y, dz=eye.z-camera.z;
        float yaw=(float)(Math.toDegrees(Math.atan2(dz,dx))-90);
        float pitch=(float)-Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz)));
        player.connection.teleport(camera.x,camera.y,camera.z,yaw,pitch);
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
        } else if (player.tickCount % 3 == 0) {
            moveChase(player,target);
        }
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) stop(player);
    }
}
