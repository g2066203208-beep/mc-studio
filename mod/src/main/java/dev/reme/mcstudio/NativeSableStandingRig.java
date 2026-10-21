package dev.reme.mcstudio;

import com.mojang.logging.LogUtils;
import java.lang.reflect.InvocationTargetException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CommonLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.slf4j.Logger;

/**
 * Boss P0: a REAL Sable/Rapier rigid body with an elevated 3x3 torso and
 * four physical 2-block-long support legs, attached as one rigid assembly.
 *
 * No animation, hidden wheels, anti-gravity, invisible anchoring, fake
 * support springs, setPos, or manual Y clamping. When feet touch ordinary
 * Minecraft ground, Rapier's block contacts carry gravity/load.
 *
 * This is deliberately NOT yet a four-joint walker. The next stage will
 * separate each lower leg into its own sublevel with rotary constraints.
 * Model blocks are vanilla so no externally licensed assets are bundled.
 *
 * Reflection keeps the baseline viewer/observer NeoForge mod buildable when
 * Sable is absent; calling spawn REQUIRES the real Sable API at runtime.
 */
public final class NativeSableStandingRig {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String CONTAINER = "dev.ryanhcode.sable.api.sublevel.SubLevelContainer";
    private static final String POSE = "dev.ryanhcode.sable.companion.math.Pose3d";

    private NativeSableStandingRig() {}

    public record Rig(Object sublevel, CommonLevelAccessor blocks, Vector3d spawn,
                      int bodyCount, int footCount) {
        public Vector3d position() {
            try {
                Object pose = sublevel.getClass().getMethod("logicalPose").invoke(sublevel);
                return new Vector3d((Vector3dc) pose.getClass().getMethod("position").invoke(pose));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot read real Sable rigid-body pose", e);
            }
        }
        public Vector3d velocity() {
            try {
                Class<?> handleClass=Class.forName("dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle");
                // Physics world owns the velocity; never extrapolate from visual animation.
                for (Method m : handleClass.getMethods()) {
                    if (m.getName().equals("of") && m.getParameterCount()==1 &&
                        m.getParameterTypes()[0].isInstance(sublevel)) {
                        Object handle=m.invoke(null,sublevel);
                        if (handle == null) throw new IllegalStateException("Sable handle not ready");
                        Object result=handle.getClass().getMethod("getLinearVelocity",Vector3d.class)
                                .invoke(handle,new Vector3d());
                        return new Vector3d((Vector3dc)result);
                    }
                }
                throw new NoSuchMethodException("RigidBodyHandle.of(ServerSubLevel)");
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot read real Sable/Rapier body velocity", e);
            }
        }
        public void removeFeet() {
            for(int x : new int[]{0,2})for(int z : new int[]{0,2})
                for(int y=0;y<=1;y++) {
                    if (!blocks.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3))
                        throw new IllegalStateException("Failed removing support leg");
                }
        }
    }

    public static Rig spawn(ServerLevel level, Vector3d worldOrigin) {
        if (!level.isLoaded(BlockPos.containing(worldOrigin.x(),worldOrigin.y(),worldOrigin.z()))) {
            throw new IllegalStateException("Boss rig requires a loaded real world chunk");
        }
        try {
            Class<?> containerClass=Class.forName(CONTAINER);
            Class<?> poseClass=Class.forName(POSE);
            Class<?> holderClass=Class.forName("dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder");
            // Important: SubLevelContainer.getContainer() is overloaded for
            // ClientLevel. Reflecting getMethod() on that class causes
            // NeoForge RuntimeDistCleaner to load ClientLevel and FAIL on a
            // dedicated server. Use its server-safe injected holder interface.
            Object container=holderClass.getMethod("sable$getPlotContainer").invoke(level);
            if (container==null)throw new IllegalStateException("Sable SubLevelContainer is unavailable");
            Object pose=poseClass.getConstructor().newInstance();
            ((Vector3d)poseClass.getMethod("position").invoke(pose)).set(worldOrigin);
            // findVirtual resolves only the precise method signature, without
            // enumerating other client-only Sable overloads.
            Class<?> sublevelClass=Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
            MethodHandle allocate=MethodHandles.publicLookup().findVirtual(containerClass,
                    "allocateNewSubLevel",MethodType.methodType(sublevelClass,poseClass));
            Object sublevel=allocate.invoke(container,pose);
            if(sublevel==null)throw new IllegalStateException("Sable failed to allocate a real sublevel");
            Object plot=sublevel.getClass().getMethod("getPlot").invoke(sublevel);
            ChunkPos center=(ChunkPos)plot.getClass().getMethod("getCenterChunk").invoke(plot);
            plot.getClass().getMethod("newEmptyChunk",ChunkPos.class).invoke(plot,center);
            CommonLevelAccessor blocks=(CommonLevelAccessor)
                    plot.getClass().getMethod("getEmbeddedLevelAccessor").invoke(plot);

            // Four legs have actual block collision hulls, and connect
            // structurally to the elevated torso at y=2.
            int legs=0,body=0;
            for (int x : new int[]{0,2})for (int z : new int[]{0,2}) {
                put(blocks,new BlockPos(x,0,z),Blocks.IRON_BLOCK.defaultBlockState()); // foot sole
                put(blocks,new BlockPos(x,1,z),Blocks.DEEPSLATE_BRICKS.defaultBlockState()); // load column
                legs+=2;
            }
            for(int x=0;x<3;x++)for(int z=0;z<3;z++){
                BlockState state=x==1&&z==0?Blocks.REDSTONE_LAMP.defaultBlockState():
                        (x==1&&z==1?Blocks.COPPER_BLOCK.defaultBlockState():
                          Blocks.CUT_COPPER.defaultBlockState());
                put(blocks,new BlockPos(x,2,z),state);
                body++;
            }
            sublevel.getClass().getMethod("updateLastPose").invoke(sublevel);
            Rig rig=new Rig(sublevel,blocks,new Vector3d(worldOrigin),body,legs);
            LOG.info("RPMS_BOSS_P0_SPAWN feet=4 footColliders={} torsoBlocks={} realSubLevel={} at={}",
                    legs,body,sublevel.getClass().getName(),worldOrigin);
            return rig;
        } catch (InvocationTargetException e){
            throw new IllegalStateException("Sable rigid-body creation failed: "+e.getCause(),e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Required Sable API missing; use official Sable 2.0.6",e);
        } catch (Throwable e) {
            if(e instanceof Error err)throw err;
            throw new IllegalStateException("Sable dedicated-server sublevel allocation failed",e);
        }
    }

    private static void put(CommonLevelAccessor world,BlockPos pos,BlockState state) {
        if(!world.setBlock(pos,state,3))throw new IllegalStateException("Cannot place boss rig "+pos);
    }
}
