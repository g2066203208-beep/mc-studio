package dev.reme.worldseam;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Minecraft's LiquidBlockRenderer writes native WORLD-space XYZ directly
 * into a VertexConsumer (unlike block models, it takes no PoseStack).
 * Only the current renderer's caller decides the source->target Y offset.
 * This wrapper avoids touching the global model-view matrix, which can
 * invalidate shaders, player/entity rendering, and other installed mods.
 */
public final class SeamOffsetVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final float dx,dy,dz;

    public SeamOffsetVertexConsumer(VertexConsumer delegate,
                                    float dx,float dy,float dz) {
        this.delegate=delegate;
        this.dx=dx; this.dy=dy; this.dz=dz;
    }
    @Override public VertexConsumer addVertex(float x,float y,float z) {
        delegate.addVertex(x+dx,y+dy,z+dz);
        return this;
    }
    @Override public VertexConsumer setColor(int r,int g,int b,int a) {
        delegate.setColor(r,g,b,a);
        return this;
    }
    @Override public VertexConsumer setUv(float u,float v) {
        delegate.setUv(u,v);
        return this;
    }
    @Override public VertexConsumer setUv1(int u,int v) {
        delegate.setUv1(u,v);
        return this;
    }
    @Override public VertexConsumer setUv2(int u,int v) {
        delegate.setUv2(u,v);
        return this;
    }
    @Override public VertexConsumer setNormal(float x,float y,float z) {
        delegate.setNormal(x,y,z);
        return this;
    }
}
