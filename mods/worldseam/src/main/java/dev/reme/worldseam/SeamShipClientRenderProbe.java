package dev.reme.worldseam;

import java.lang.reflect.Method;

/**
 * CI-only, CLIENT-THREAD Sable render readiness probe against released 2.x.
 *
 * A nonnull ClientSubLevel#getRenderData() is not proof that one Minecraft
 * chunk section was compiled: this exact mistake produced successful UUID
 * telemetry but a real 2026-10-23 video without aircraft pixels.
 *
 * This class merely checks native Sable data, with no fake mesh, renderer
 * hacks, level swaps, or GPU copies. The actual raw video pixel gate is still
 * mandatory, since compiled != visible to the current camera.
 */
public final class SeamShipClientRenderProbe {
    private SeamShipClientRenderProbe() {}

    public record Snapshot(int allocatedSections,int compiledSections,
                           String renderer,String diagnostic) {
        public boolean nativeGeometryReady() {
            return allocatedSections>0 && compiledSections>0;
        }
    }

    public static Snapshot inspect(Object actualSableClientSubLevel) {
        if(actualSableClientSubLevel==null)
            return new Snapshot(0,0,"none","missing native Sable sublevel");
        try {
            Object render=actualSableClientSubLevel.getClass()
                .getMethod("getRenderData").invoke(actualSableClientSubLevel);
            if(render==null)
                return new Snapshot(0,0,"none","Sable render data absent");
            String type=render.getClass().getName();
            int count=((Number)render.getClass()
                .getMethod("getVisibleSectionCount").invoke(render)).intValue();
            if(count<=0)
                return new Snapshot(Math.max(0,count),0,type,
                    "Sable render sections not allocated");
            Object plot=actualSableClientSubLevel.getClass()
                .getMethod("getPlot").invoke(actualSableClientSubLevel);
            if(plot==null)
                return new Snapshot(count,0,type,"Sable client plot absent");
            Object bounds=plot.getClass().getMethod("getBoundingBox").invoke(plot);
            if(bounds==null)
                return new Snapshot(count,0,type,"Sable plot bounds absent");

            Class<?> boxType=Class.forName(
                "dev.ryanhcode.sable.companion.math.BoundingBox3ic");
            int minX=num(boxType.getMethod("minX"),bounds)>>4;
            int minY=num(boxType.getMethod("minY"),bounds)>>4;
            int minZ=num(boxType.getMethod("minZ"),bounds)>>4;
            int maxX=num(boxType.getMethod("maxX"),bounds)>>4;
            int maxY=num(boxType.getMethod("maxY"),bounds)>>4;
            int maxZ=num(boxType.getMethod("maxZ"),bounds)>>4;
            long volume=((long)maxX-minX+1)*((long)maxY-minY+1)
                *((long)maxZ-minZ+1);
            if(volume<=0||volume>1024)
                return new Snapshot(count,0,type,
                    "Unsafe or empty Sable section grid bounds "+volume);
            Method compiled=render.getClass().getMethod(
                "isSectionCompiled",int.class,int.class,int.class);
            int ready=0;
            for(int x=minX;x<=maxX;x++)
                for(int y=minY;y<=maxY;y++)
                    for(int z=minZ;z<=maxZ;z++)
                        if(Boolean.TRUE.equals(compiled.invoke(render,x,y,z)))
                            ready++;
            return new Snapshot(count,ready,type,
                ready==0?"Sable native sections allocated but not compiled":
                         "native section compilation complete in at least one section");
        } catch(ReflectiveOperationException|RuntimeException|LinkageError ex) {
            return new Snapshot(0,0,"unresolved",
                "Authentic Sable client renderer API unresolved: "+ex);
        }
    }

    private static int num(Method method,Object object)
            throws ReflectiveOperationException {
        return ((Number)method.invoke(object)).intValue();
    }
}
