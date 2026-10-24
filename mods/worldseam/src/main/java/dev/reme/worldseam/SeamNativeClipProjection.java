package dev.reme.worldseam;

import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Physical target-chart clipping BEFORE the native depth test.
 * Null means the oblique plane cannot safely be applied in this camera.
 * The compositor must reconstruct target depth with this same projection.
 */
public final class SeamNativeClipProjection {
    private SeamNativeClipProjection() {}
    public static Matrix4f beyondSeam(Matrix4f projection, Matrix4f view,
                                      float seamCameraY, boolean below) {
        if (projection == null || view == null ||
            !Float.isFinite(seamCameraY) || Math.abs(seamCameraY) < .025f)
            return null;
        float side = below ? -1f : 1f;
        Vector4f plane = new Vector4f(0,side,0,-side*seamCameraY);
        if (plane.w >= -.025f) return null;
        new Matrix4f(view).invert().transpose().transform(plane);
        float norm=(float)Math.sqrt(plane.x*plane.x+plane.y*plane.y+plane.z*plane.z);
        if(!Float.isFinite(norm)||norm<1e-6f)return null;
        plane.div(norm);
        Vector4f q=new Vector4f(plane.x>=0?1:-1,plane.y>=0?1:-1,1,1);
        new Matrix4f(projection).invert().transform(q);
        float denominator=plane.dot(q);
        if(!Float.isFinite(denominator)||denominator<=1e-5f)return null;
        plane.mul(2f/denominator);
        Matrix4f result=new Matrix4f(projection);
        // JOML mCR is column C / row R; replace the third projection row.
        result.m02(plane.x-projection.m03());
        result.m12(plane.y-projection.m13());
        result.m22(plane.z-projection.m23());
        result.m32(plane.w-projection.m33());
        return Float.isFinite(result.m02()) && Float.isFinite(result.m12()) &&
            Float.isFinite(result.m22()) && Float.isFinite(result.m32()) ? result : null;
    }
}
