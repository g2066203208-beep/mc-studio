package dev.reme.mcstudio.climb;

import dev.reme.mcstudio.StudioMod;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Rotate the *rendered body* toward the real grabbed face, never the camera.
 * The player can still look anywhere; camera yaw/pitch never drives the arms.
 * Works for remote observers using server-synchronized contact directions.
 */
@EventBusSubscriber(modid = StudioMod.MOD_ID, value = Dist.CLIENT)
public final class PeakSurfaceRender {
    private static final Map<Player, float[]> PREVIOUS = new WeakHashMap<>();
    private PeakSurfaceRender() {}

    @SubscribeEvent
    public static void before(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        PeakVisuals.Pose pose = PeakVisuals.pose(player);
        if (pose == null || !pose.climbing() || !Float.isFinite(pose.bodyYaw())
                || pose.normal().lengthSqr() < 0.5) return;
        PREVIOUS.put(player, new float[]{player.yBodyRot, player.yBodyRotO});
        // A collision face decides where the torso and arms point; never read
        // player.getYRot() here. Head look orientation remains vanilla.
        float yaw = ClimbSurfaceMath.facingYaw(pose.normal(), pose.bodyYaw());
        player.yBodyRot = yaw;
        player.yBodyRotO = yaw;
    }

    @SubscribeEvent
    public static void after(RenderPlayerEvent.Post event) {
        Player player = event.getEntity();
        float[] before = PREVIOUS.remove(player);
        if (before != null) {
            player.yBodyRot = before[0];
            player.yBodyRotO = before[1];
        }
    }
}
