package dev.reme.mcstudio.seam;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Map;

/**
 * Renders real remote models and remote sky/block light from incremental
 * caches. Occlusion + frustum rejection make most solid interior cells free.
 * Still a near-field LOD, not the final full multi-world chunk renderer.
 */
public final class SeamPreviewClient {
    private static final SeamFrameState CACHE = new SeamFrameState();
    private static long lastReceivedTick = -1;
    private static final int EXPIRY_TICKS = 80;
    private static final double MAX_RENDER_DISTANCE_SQ = 48 * 48;

    private SeamPreviewClient() {}

    public static void install() {
        NeoForge.EVENT_BUS.addListener(SeamPreviewClient::render);
    }

    public static void accept(SeamPreviewPayload message) {
        // Called on the client main thread via context.enqueueWork.
        if (CACHE.accept(message.frame())) {
            Minecraft mc = Minecraft.getInstance();
            lastReceivedTick = mc.level != null ? mc.level.getGameTime() : -1;
        }
    }

    private static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || event.getPoseStack() == null) {
            CACHE.clear();
            lastReceivedTick = -1;
            return;
        }
        if (!mc.level.dimension().location().toString().equals(CACHE.dimension())
            || (lastReceivedTick >= 0
                && mc.level.getGameTime() - lastReceivedTick > EXPIRY_TICKS)) {
            CACHE.clear();
            lastReceivedTick = -1;
            return;
        }
        Map<SeamFrameState.Position, SeamFrameState.Visual> cells = CACHE.blocks();
        if (cells.isEmpty()) return;

        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        boolean anyDrawn = false;
        for (var entry : cells.entrySet()) {
            var p = entry.getKey();
            var visual = entry.getValue();
            double dx = p.x() + .5 - camera.x;
            double dy = p.y() + .5 - camera.y;
            double dz = p.z() + .5 - camera.z;
            if (dx*dx + dy*dy + dz*dz > MAX_RENDER_DISTANCE_SQ) continue;
            if (!event.getFrustum().isVisible(new AABB(
                p.x(), p.y(), p.z(), p.x()+1, p.y()+1, p.z()+1
            ))) continue;
            if (isBuried(p, cells)) continue;
            BlockState state = Block.stateById(visual.stateId());
            if (state.getRenderShape() != RenderShape.MODEL) continue;

            pose.pushPose();
            pose.translate(p.x() - camera.x, p.y() - camera.y, p.z() - camera.z);
            int light = LightTexture.pack(visual.light() & 15, visual.light() >>> 4);
            mc.getBlockRenderer().renderSingleBlock(
                state, pose, buffers, light, OverlayTexture.NO_OVERLAY
            );
            pose.popPose();
            anyDrawn = true;
        }
        if (anyDrawn) buffers.endBatch();
    }

    private static boolean isBuried(
        SeamFrameState.Position p,
        Map<SeamFrameState.Position, SeamFrameState.Visual> cells
    ) {
        // Every neighbor must be a full opaque cube; glass/foliage/water cannot
        // hide a cell. This is conservative and avoids obvious internal models.
        int[][] offsets = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        for (int[] d : offsets) {
            var neighbor = cells.get(new SeamFrameState.Position(
                p.x()+d[0], p.y()+d[1], p.z()+d[2]
            ));
            if (neighbor == null) return false;
            BlockState state = Block.stateById(neighbor.stateId());
            if (!state.canOcclude() || !state.isSolidRender(
                net.minecraft.client.Minecraft.getInstance().level,
                new net.minecraft.core.BlockPos(p.x()+d[0],p.y()+d[1],p.z()+d[2])
            )) return false;
        }
        return true;
    }
}
