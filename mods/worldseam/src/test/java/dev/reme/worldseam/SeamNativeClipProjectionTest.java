package dev.reme.worldseam;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeamNativeClipProjectionTest {
    private Matrix4f projection() {
        return new Matrix4f().perspective((float)Math.toRadians(70),16f/9f,.05f,500f);
    }
    private float near(Matrix4f p,Matrix4f v,float y) {
        Vector4f q=new Matrix4f(p).mul(v).transform(new Vector4f(0,y,0,1));
        return q.z+q.w;
    }
    @Test void downwardSeamRejectsNearSideGeometry() {
        Matrix4f v=new Matrix4f().rotateX((float)Math.PI/2);
        Matrix4f p=SeamNativeClipProjection.beyondSeam(projection(),v,-2,true);
        assertNotNull(p);
        assertTrue(near(p,v,-1)<0);
        assertEquals(0,near(p,v,-2),.0002);
        assertTrue(near(p,v,-4)>0);
    }
    @Test void upwardSeamRejectsNearSideGeometry() {
        Matrix4f v=new Matrix4f().rotateX(-(float)Math.PI/2);
        Matrix4f p=SeamNativeClipProjection.beyondSeam(projection(),v,2,false);
        assertNotNull(p);
        assertTrue(near(p,v,1)<0);
        assertEquals(0,near(p,v,2),.0002);
        assertTrue(near(p,v,4)>0);
    }
    @Test void obliqueDepthReconstructsOriginalGeometry() {
        Matrix4f v=new Matrix4f().rotateX((float)Math.PI/2);
        Matrix4f p=SeamNativeClipProjection.beyondSeam(projection(),v,-2,true);
        assertNotNull(p);
        Vector4f original=new Vector4f(.3f,-7f,-.4f,1);
        Vector4f ndc=new Matrix4f(p).mul(v).transform(new Vector4f(original));
        ndc.div(ndc.w);
        Vector4f recovered=new Matrix4f(p).mul(v).invert()
            .transform(new Vector4f(ndc.x,ndc.y,ndc.z,1));
        recovered.div(recovered.w);
        assertEquals(original.x,recovered.x,.002);
        assertEquals(original.y,recovered.y,.002);
        assertEquals(original.z,recovered.z,.002);
    }
    @Test void subBlockSeamCameraBandCannotSafelyUseUnclippedProjection() {
        // At the join itself the oblique near plane is ill-conditioned.
        // Production must SKIP the foreign pass, not fall back to vanilla
        // projection and let wrong-side blocks occlude real far-side ones.
        Matrix4f downView=new Matrix4f().rotateX((float)Math.PI/2);
        Matrix4f upView=new Matrix4f().rotateX(-(float)Math.PI/2);
        assertNull(SeamNativeClipProjection.beyondSeam(projection(),downView,-.01f,true));
        assertNull(SeamNativeClipProjection.beyondSeam(projection(),upView,.01f,false));
    }
    @Test void nearSeamAndWrongSideAreRejected() {
        assertNull(SeamNativeClipProjection.beyondSeam(projection(),new Matrix4f(),0,true));
        assertNull(SeamNativeClipProjection.beyondSeam(projection(),new Matrix4f(),Float.NaN,false));
        assertNull(SeamNativeClipProjection.beyondSeam(projection(),new Matrix4f(),2,true));
    }
}
