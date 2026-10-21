package dev.reme.worldseam;

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
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A separate remote-section scene. It NEVER alters mc.level or tricks
 * Minecraft into believing the other dimension is the active ClientLevel.
 * This is the groundwork for native section meshes / custom LevelRenderer.
 *
 * One packet recompiles one section's visible block-model surface.
 * Current renderer is still the slower vanilla renderSingleBlock path.
 */
public final class SeamSectionClient {
    private static final SeamSectionCache CACHE = new SeamSectionCache();
    private record Cell(int x, int y, int z, int state, int light) {}
    private static final Map<SeamSectionCache.Key, List<Cell>> geometry =
        new java.util.HashMap<>();
    private static long lastReceive = -1;
    private static final long TIMEOUT_TICKS = 100;
    private static final int MAX_RENDER_MODELS = 1800;

    private SeamSectionClient() {}

    public static void install() {
        NeoForge.EVENT_BUS.addListener(SeamSectionClient::render);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> clear());
    }

    public static void accept(SeamSectionPayload payload) {
        var frame = payload.frame();
        if (!CACHE.apply(frame)) return;
        if (frame.clear()) geometry.clear();
        else if (frame.key() != null) {
            geometry.keySet().retainAll(CACHE.snapshot().keySet());
            geometry.put(frame.key(), buildSurface(frame));
        }
        Minecraft mc = Minecraft.getInstance();
        lastReceive = mc.level != null ? mc.level.getGameTime() : -1;
    }

    private static List<Cell> buildSurface(SeamSectionCache.Frame frame) {
        var e = frame.content();
        List<Cell> mesh = new ArrayList<>(1024);
        int[] state = new int[4096];
        for (int i = 0; i < state.length; i++) state[i] = e.stateAt(i);

        for (int y=0; y<16; y++) for (int z=0; z<16; z++) for(int x=0; x<16; x++) {
            int offset = SeamSectionCodec.index(x,y,z);
            BlockState block = Block.stateById(state[offset]);
            if (block.isAir() || block.getRenderShape() != RenderShape.MODEL) continue;
            if (isInteriorOpaque(x,y,z,state) && block.canOcclude()) continue;
            int worldY = (frame.key().sectionY() << 4) + y;
            int visibleY = frame.sourceBoundary() + worldY - frame.targetBoundary();
            // Existing near-block preview fills the first 12 blocks.
            if (Math.abs(visibleY - frame.sourceBoundary()) < 12) continue;
            mesh.add(new Cell((frame.key().chunkX()<<4)+x,
                visibleY, (frame.key().chunkZ()<<4)+z, state[offset], e.lightAt(offset)));
        }
        return List.copyOf(mesh);
    }

    private static boolean isInteriorOpaque(int x, int y, int z, int[] states) {
        if (x==0 || y==0 || z==0 || x==15 || y==15 || z==15) return false;
        int index=SeamSectionCodec.index(x,y,z);
        for (int delta : new int[]{-1,1,-16,16,-256,256}) {
            if (!Block.stateById(states[index+delta]).canOcclude()) return false;
        }
        return true;
    }

    private static void render(RenderLevelStageEvent event) {
        if (event.getStage()!=RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        Minecraft mc=Minecraft.getInstance();
        if (mc.level==null || !CACHE.isFor(mc.level.dimension().location().toString())
            || (lastReceive >= 0 && mc.level.getGameTime() - lastReceive > TIMEOUT_TICKS)) {
            clear();
            return;
        }
        if (geometry.isEmpty() || event.getPoseStack()==null) return;
        Vec3 camera=event.getCamera().getPosition();
        PoseStack pose=event.getPoseStack();
        MultiBufferSource.BufferSource buffers=mc.renderBuffers().bufferSource();
        int rendered=0;
        for (List<Cell> section:geometry.values()) {
            for (Cell cell:section) {
                double dx=cell.x()+.5-camera.x;
                double dy=cell.y()+.5-camera.y;
                double dz=cell.z()+.5-camera.z;
                if (dx*dx+dy*dy+dz*dz>96*96) continue;
                if (!event.getFrustum().isVisible(new AABB(cell.x(),cell.y(),cell.z(),
                    cell.x()+1,cell.y()+1,cell.z()+1))) continue;
                pose.pushPose();
                pose.translate(cell.x()-camera.x,cell.y()-camera.y,cell.z()-camera.z);
                mc.getBlockRenderer().renderSingleBlock(
                    Block.stateById(cell.state()),pose,buffers,
                    LightTexture.pack(cell.light()&15,cell.light()>>>4),
                    OverlayTexture.NO_OVERLAY
                );
                pose.popPose();
                rendered++;
                if (rendered>=MAX_RENDER_MODELS) break;
            }
            if (rendered>=MAX_RENDER_MODELS) break;
        }
        if (rendered>0) buffers.endBatch();
    }

    private static void clear() {
        CACHE.reset();
        geometry.clear();
        lastReceive=-1;
    }
}
