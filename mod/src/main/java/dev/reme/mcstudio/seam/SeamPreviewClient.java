package dev.reme.mcstudio.seam;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;

/**
 * Original minimal cross-dimensional near-boundary visual proxy.
 *
 * Draws remote block models where the current world's height ends.
 * It is not yet the full chunk renderer: no remote fluids, lighting,
 * terrain far beyond sample radius, entities or remote block entities.
 */
public final class SeamPreviewClient {
    private static String sourceDimension = "";
    private static List<SeamPreviewPayload.BlockSample> blocks = List.of();

    private SeamPreviewClient() {}

    public static void install() {
        NeoForge.EVENT_BUS.addListener(SeamPreviewClient::render);
    }

    public static void accept(SeamPreviewPayload message) {
        sourceDimension = message.currentDimension();
        blocks = message.blocks();
    }

    private static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.dimension().location().toString().equals(sourceDimension)) {
            blocks = List.of();
            return;
        }
        if (blocks.isEmpty() || event.getPoseStack() == null) return;

        // Visible geometry is owned by the remote ServerLevel snapshot.
        // Using actual block models (not colored placeholder boxes).
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        for (SeamPreviewPayload.BlockSample sample : blocks) {
            BlockState state = Block.stateById(sample.blockStateId());
            if (state.getRenderShape() != RenderShape.MODEL) continue;
            pose.pushPose();
            pose.translate(sample.x() - camera.x,
                sample.y() - camera.y, sample.z() - camera.z);
            mc.getBlockRenderer().renderSingleBlock(
                state, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY
            );
            pose.popPose();
        }
        buffers.endBatch();
    }
}
