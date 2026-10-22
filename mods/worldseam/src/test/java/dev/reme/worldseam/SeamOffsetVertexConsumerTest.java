package dev.reme.worldseam;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeamOffsetVertexConsumerTest {
    private static class Recording implements VertexConsumer {
        float x,y,z,u,v;
        int light,rgba;
        @Override public VertexConsumer addVertex(float a,float b,float c) {
            x=a;y=b;z=c;return this;
        }
        @Override public VertexConsumer setColor(int r,int g,int b,int a) {
            rgba=a;return this;
        }
        @Override public VertexConsumer setUv(float uu,float vv) {
            u=uu;v=vv;return this;
        }
        @Override public VertexConsumer setUv1(int a,int b){return this;}
        @Override public VertexConsumer setUv2(int a,int b){
            light=(b<<16)|a;return this;
        }
        @Override public VertexConsumer setNormal(float a,float b,float c){return this;}
    }
    @Test void liquidVerticesUseExactTranslatedCameraAndDimensionOffset() {
        Recording rec=new Recording();
        var shifted=new SeamOffsetVertexConsumer(rec,-12.5f,-192.5f,8.5f);
        shifted.addVertex(13,128,9).setColor(255,0,0,230)
            .setUv(0.5f,0.75f).setUv2(12,15);
        assertEquals(0.5f,rec.x,1e-6f);
        assertEquals(-64.5f,rec.y,1e-6f);
        assertEquals(17.5f,rec.z,1e-6f);
        assertEquals(0.5f,rec.u,1e-6f);
        assertEquals(0.75f,rec.v,1e-6f);
        assertEquals(230,rec.rgba);
        assertEquals((15<<16)|12,rec.light);
    }
    @Test void separateVerticesDoNotAccumulateOffset() {
        Recording rec=new Recording();
        var shifted=new SeamOffsetVertexConsumer(rec,2,3,4);
        shifted.addVertex(0,0,0);
        assertEquals(3,rec.y);
        shifted.addVertex(1,1,1);
        assertEquals(4,rec.y);
        assertEquals(3,rec.x);
    }
}
