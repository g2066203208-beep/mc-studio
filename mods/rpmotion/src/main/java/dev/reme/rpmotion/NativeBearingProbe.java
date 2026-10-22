package dev.reme.rpmotion;

import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Observes the ORIGINAL Simulated bearing BE, not any custom block. */
public final class NativeBearingProbe {
    private NativeBearingProbe() {}
    private static final String NATIVE = "dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity";
    public record Status(boolean installed, boolean assembled, UUID subLevelId, double targetDeg, BlockPos platePos) {
        public boolean attached() { return installed && assembled && subLevelId != null && platePos != null; }
    }
    public static Status read(ServerLevel level, BlockPos pos) {
        Object be = level.getBlockEntity(pos);
        if (be == null || !be.getClass().getName().equals(NATIVE))
            return new Status(false,false,null,0,null);
        try {
            Class<?> cls = be.getClass();
            return new Status(true,(boolean)cls.getMethod("isAssembled").invoke(be),
                (UUID)cls.getMethod("getSubLevelID").invoke(be),
                (double)cls.getMethod("getTargetAngleDegrees").invoke(be),
                (BlockPos)cls.getMethod("getPlatePos").invoke(be));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("ORIGINAL Simulated 1.3.2 bearing API not compatible",exception);
        }
    }
    public static void assembleNative(ServerLevel level, BlockPos pos) {
        Object be=level.getBlockEntity(pos);
        if(be==null || !be.getClass().getName().equals(NATIVE))
            throw new IllegalStateException("No original Simulated swivel bearing at "+pos);
        try {
            be.getClass().getMethod("assemble").invoke(be);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("The installed genuine Aeronautics bearing could not assemble",e);
        }
    }
}
