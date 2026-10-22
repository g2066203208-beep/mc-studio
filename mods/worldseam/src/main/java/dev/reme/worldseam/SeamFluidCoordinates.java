package dev.reme.worldseam;

/**
 * Shader-independent native fluid geometry coordinate mapping.
 * Pure Java so client dependencies and OpenGL are NOT needed in tests.
 */
public final class SeamFluidCoordinates {
    private SeamFluidCoordinates() {}
    public record Position(float x,float y,float z) {}
    public static Position shifted(float nativeX,float nativeY,float nativeZ,
                                   float dx,float dy,float dz) {
        return new Position(nativeX+dx,nativeY+dy,nativeZ+dz);
    }
}
