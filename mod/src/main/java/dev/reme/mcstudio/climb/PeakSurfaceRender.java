package dev.reme.mcstudio.climb;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.reme.mcstudio.StudioMod;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Cosmetic-only render rig: root tilt matches wall vs ceiling without changing
 * physics hitboxes, player's aim or the real camera. Restores all state in Post.
 */
@EventBusSubscriber(modid = StudioMod.MOD_ID, value = Dist.CLIENT)
public final class PeakSurfaceRender {
    private record Saved(float previousYaw, float previousYawO, boolean pushed) {}
    private static final Map<Player, Saved> BEFORE = new WeakHashMap<>();
    private PeakSurfaceRender() {}

    @SubscribeEvent
    public static void before(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        PeakVisuals.Pose pose = PeakVisuals.pose(player);
        if (pose == null || !pose.climbing() || !Float.isFinite(pose.bodyYaw())
                || pose.normal().lengthSqr() < .5) return;
        Vec3 normal = pose.normal().normalize();
        float bodyYaw = ClimbSurfaceMath.facingYaw(normal, pose.bodyYaw());
        PoseStack stack = event.getPoseStack();
        stack.pushPose();
        // Keep the animated player slightly outside the face. The physical
        // contact-distance servo is responsible for primary separation.
        stack.translate(normal.x*.025, normal.y*.025, normal.z*.025);
        // Roof: REAL suspended hang. The legs and torso stay mostly VERTICAL
        // below two raised gripping hands; rotating the entire root 64 degrees
        // made the climber lie horizontally against the ceiling.
        float tilt = pose.action() == PeakAnimationPolicy.WALL_JUMP ? 0f
                : ClimbSurfaceMath.ceiling(normal) ? 0f
                : ClimbSurfaceMath.floor(normal) ? 0f : 5.5f;
        if (tilt != 0) {
            stack.translate(0, 1.16, 0);
            stack.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
            stack.mulPose(Axis.XP.rotationDegrees(tilt));
            stack.mulPose(Axis.YP.rotationDegrees(bodyYaw));
            stack.translate(0, -1.16, 0);
        }
        BEFORE.put(player, new Saved(player.yBodyRot, player.yBodyRotO, true));
        player.yBodyRot = bodyYaw;
        player.yBodyRotO = bodyYaw;
    }

    @SubscribeEvent
    public static void after(RenderPlayerEvent.Post event) {
        Player player = event.getEntity();
        Saved before = BEFORE.remove(player);
        if (before != null) {
            player.yBodyRot = before.previousYaw();
            player.yBodyRotO = before.previousYawO();
            if (before.pushed()) event.getPoseStack().popPose();
        }
    }
}
