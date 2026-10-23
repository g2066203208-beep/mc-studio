package dev.reme.worldseam;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

/**
 * A genuine control-chain test using only released, unmodified Create,
 * Simulated (bundled in Aeronautics), Aeronautics and Sable block entities.
 *
 * Test ONLY the causal path throttle -> real redstone -> native Create
 * clutch -> Create kinetic network -> Aeronautics propeller actor *inside*
 * the same assembled Sable ship. No artificial Rapier impulses, no fake
 * physics body or player-control claim (no game-test client is seated).
 */
@GameTestHolder(WorldSeamMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SeamRealPilotThrottleGameTests {
    private static final org.slf4j.Logger LOG = LogUtils.getLogger();
    private SeamRealPilotThrottleGameTests() {}

    @GameTest(template="empty", batch="worldseam.real_pilot_throttle",
              timeoutTicks=280)
    public static void nativeThrottleMustCutAndRestoreRealAeronauticsRPM(
            GameTestHelper helper) {
        if (!Boolean.getBoolean("worldseam.requireExternalMods")) {
            helper.succeed();
            return;
        }

        final ServerLevel level=helper.getLevel();
        final BlockPos origin=helper.absolutePos(new BlockPos(4,1,4));
        final int x=origin.getX(),z=origin.getZ();
        final BlockPos motorPos=new BlockPos(x,-55,z);
        final BlockPos clutchPos=motorPos.above();
        final BlockPos propPos=clutchPos.above();
        final BlockPos throttlePos=clutchPos.east();
        final Object ship;
        final UUID uuid;
        try {
            Block motor=get("create:creative_motor");
            Block clutch=get("create:clutch");
            Block prop=get("aeronautics:smart_propeller");
            Block throttle=get("simulated:throttle_lever");
            BlockState motorState=motor.defaultBlockState().setValue(
                BlockStateProperties.FACING,Direction.UP);
            BlockState clutchState=clutch.defaultBlockState().setValue(
                BlockStateProperties.AXIS,Direction.Axis.Y);
            BlockState propState=prop.defaultBlockState();
            if (!propState.hasProperty(BlockStateProperties.HORIZONTAL_AXIS))
                throw new IllegalStateException("Genuine propeller hinge missing");

            var blocks=new ArrayList<BlockPos>();
            for(int y=-55;y<=-54;y++)
                for(int dx=-1;dx<=1;dx++)
                    for(int dz=-1;dz<=1;dz++) {
                        var bp=new BlockPos(x+dx,y,z+dz);
                        level.setBlock(bp,Blocks.IRON_BLOCK.defaultBlockState(),3);
                        blocks.add(bp);
                    }

            // Real block-entity positions are transferred to Sable's
            // plot by the released official SubLevelAssemblyHelper.
            level.setBlock(motorPos,motorState,3);
            level.setBlock(clutchPos,clutchState,3);
            level.setBlock(propPos,propState,3);
            level.setBlock(throttlePos,throttle.defaultBlockState(),3);
            blocks.add(propPos);
            if(level.getBlockState(propPos).getBlock()!=prop ||
               level.getBlockState(clutchPos).getBlock()!=clutch ||
               level.getBlockState(throttlePos).getBlock()!=throttle)
                throw new IllegalStateException("Native drivetrain staging failed");

            Class<?> boxType=Class.forName(
                "dev.ryanhcode.sable.companion.math.BoundingBox3ic");
            Object box=Class.forName(
                "dev.ryanhcode.sable.companion.math.BoundingBox3i")
                .getConstructor(int.class,int.class,int.class,
                    int.class,int.class,int.class)
                .newInstance(x-1,-55,z-1,x+1,-53,z+1);
            ship=Class.forName("dev.ryanhcode.sable.api.SubLevelAssemblyHelper")
                .getMethod("assembleBlocks",ServerLevel.class,BlockPos.class,
                    Iterable.class,boxType)
                .invoke(null,level,motorPos,blocks,box);
            if(ship==null)throw new IllegalStateException("Sable assembly null");
            uuid=(UUID)ship.getClass().getMethod("getUniqueId").invoke(ship);
            LOG.info("WORLDSEAM_REAL_PILOT_THROTTLE_ASSEMBLED: uuid={} motor={} clutch={} propeller={} realLever={}, externalForce=false",
                uuid,motorPos,clutchPos,propPos,throttlePos);
        } catch(ReflectiveOperationException | RuntimeException | LinkageError ex) {
            helper.fail("Real Sable native cockpit fixture failed: "+ex);
            return;
        }

        final int[] stage={0}; // 0 prop powered, 1 cut, 2 restored
        final long started=level.getGameTime();
        final BlockEntity[] lever={null};
        helper.succeedWhen(()->{
            try {
                SeamAeronauticsEvidence.Observation obs=
                    SeamAeronauticsEvidence.inspect(ship);
                if(stage[0]==0 && obs.powered()) {
                    lever[0]=findRealPlotLever(ship);
                    if(lever[0]==null)
                        throw new IllegalStateException(
                            "No authentic Simulated throttle BE in Sable plot chunks");
                    // This exercises the *real* Simulated block-entity API;
                    // setSignal triggers redstone neighbor update after two
                    // native ticks and the Create clutch must cut the RPM.
                    lever[0].getClass().getMethod("setSignal",int.class)
                        .invoke(lever[0],15);
                    stage[0]=1;
                    LOG.info("WORLDSEAM_REAL_PILOT_THROTTLE_SIGNAL_HIGH: uuid={} nativeRPMWasOn=true, signal=15",uuid);
                } else if(stage[0]==1 && !obs.powered()
                          && level.getGameTime()-started>=6) {
                    lever[0].getClass().getMethod("setSignal",int.class)
                        .invoke(lever[0],0);
                    stage[0]=2;
                    LOG.info("WORLDSEAM_REAL_PILOT_THROTTLE_CLUTCH_CUT: uuid={} noCreateRPM=true, signalRestored=0",uuid);
                } else if(stage[0]==2 && obs.powered()) {
                    LOG.info("WORLDSEAM_REAL_PILOT_THROTTLE_PASS: uuid={} genuinePropellers={} nativeThrust={} redstoneClutchCutAndRestored=true, externalForce=false, actualHumanPilotUnverified=true",
                        uuid,obs.spinningPropellers(),obs.totalAbsoluteThrust());
                    return;
                }
                helper.assertTrue(false,
                    "Waiting native throttle->clutch->RPM state="+stage[0]+
                    " ticks="+(level.getGameTime()-started)+
                    " diag="+obs.diagnostic());
            } catch(ReflectiveOperationException | RuntimeException ex) {
                helper.fail("Authentic throttle/drivetrain failed: "+ex);
            }
        });
    }

    private static Block get(String id) {
        Block found=BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
        if(found==Blocks.AIR)
            throw new IllegalStateException("Missing official block "+id);
        return found;
    }

    /**
     * Search the REAL moved Sable plot chunks, not the empty original
     * source Level voxels after assembly. PlotChunkHolder#getChunk is
     * public in Sable 2.0.6; this adapter never loads chunks itself.
     */
    private static BlockEntity findRealPlotLever(Object sub)
            throws ReflectiveOperationException {
        Object plot=sub.getClass().getMethod("getPlot").invoke(sub);
        Object obj=plot.getClass().getMethod("getLoadedChunks").invoke(plot);
        if(!(obj instanceof Collection<?> holders))return null;
        for(Object holder:holders) {
            Object raw=holder.getClass().getMethod("getChunk").invoke(holder);
            if(!(raw instanceof LevelChunk chunk))continue;
            for(BlockEntity entity:chunk.getBlockEntities().values()) {
                if(entity!=null && entity.getClass().getName().equals(
                    "dev.simulated_team.simulated.content.blocks.throttle_lever.ThrottleLeverBlockEntity"))
                    return entity;
            }
        }
        return null;
    }
}
