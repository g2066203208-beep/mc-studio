package dev.reme.worldseam;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Integration gate against the actual released Create 6.0.10,
 * Sable 2.0.6 and Create: Aeronautics 1.3.2 JARs.
 *
 * Tests authentic Create kinetic RPM + Aeronautics generated thrust AFTER
 * assembly into the live Sable ServerSubLevel. No fake actors, no applied
 * WorldSeam force, no debug teleport and no standalone-stub mod.
 *
 * It does NOT certify two-chart Rapier contacts or a pilot flying.
 */
@GameTestHolder(WorldSeamMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SeamRealAeroDriveGameTests {
    private static final org.slf4j.Logger LOG = LogUtils.getLogger();
    private SeamRealAeroDriveGameTests() {}

    @GameTest(template = "empty",
              batch = "worldseam.real_aeronautics_native_power",
              timeoutTicks = 260)
    public static void assembledSmartPropellerMustSpinFromRealCreateMotor(
            GameTestHelper helper) {
        if (!Boolean.getBoolean("worldseam.requireExternalMods")) {
            helper.succeed();
            return;
        }
        if (!ModList.get().isLoaded("create") ||
            !ModList.get().isLoaded("sable")) {
            helper.fail("Released Create/Sable jars are mandatory for this gate");
            return;
        }

        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(4, 1, 4));
        int x = center.getX(), z = center.getZ();
        BlockPos motorPos = new BlockPos(x, -54, z);
        BlockPos rotorPos = motorPos.above();
        final Object liveShip;
        final UUID shipId;
        try {
            var motor = BuiltInRegistries.BLOCK.get(
                ResourceLocation.parse("create:creative_motor"));
            var propeller = BuiltInRegistries.BLOCK.get(
                ResourceLocation.parse("aeronautics:smart_propeller"));
            if (motor == Blocks.AIR || propeller == Blocks.AIR)
                throw new IllegalStateException(
                    "Genuine released motor and smart propeller are absent");

            // OFFICIAL SmartPropellerBlock source declares a HORIZONTAL_AXIS
            // X/Z hinge, while its real kinetic axle is vertical Y and
            // CEILING=false exposes a shaft DOWN toward this motor.
            BlockState motorState = motor.defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.UP);
            BlockState propState = propeller.defaultBlockState();
            if (!propState.hasProperty(BlockStateProperties.HORIZONTAL_AXIS))
                throw new IllegalStateException(
                    "Smart propeller horizontal hinge property absent");
            Object nativeAxle = propeller.getClass().getMethod(
                "getRotationAxis", BlockState.class).invoke(propeller, propState);
            Object downward = propeller.getClass().getMethod(
                "hasShaftTowards", net.minecraft.world.level.LevelReader.class,
                BlockPos.class, BlockState.class, Direction.class)
                .invoke(propeller, level, rotorPos, propState, Direction.DOWN);
            if (nativeAxle != Direction.Axis.Y || !Boolean.TRUE.equals(downward))
                throw new IllegalStateException(
                    "Rotor is not coupled to motor: axle=" + nativeAxle +
                    " downward=" + downward);

            // Disposable GameTest world, genuine source Level block states.
            // A rigid body with enough inertial mass to be meaningful.
            var blocks = new ArrayList<BlockPos>();
            for (int y = -55; y <= -54; y++)
                for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos at = new BlockPos(x + dx, y, z + dz);
                        if (!level.setBlock(at,
                            Blocks.IRON_BLOCK.defaultBlockState(), 3))
                            throw new IllegalStateException("Fixture voxel failed " + at);
                        blocks.add(at);
                    }
            level.setBlock(motorPos, motorState, 3);
            level.setBlock(rotorPos, propState, 3);
            blocks.add(rotorPos);
            if (level.getBlockState(motorPos).getBlock() != motor ||
                level.getBlockState(rotorPos).getBlock() != propeller)
                throw new IllegalStateException("Motor/rotor did not stage");

            Class<?> boxType = Class.forName(
                "dev.ryanhcode.sable.companion.math.BoundingBox3ic");
            Object bounds = Class.forName(
                "dev.ryanhcode.sable.companion.math.BoundingBox3i")
                .getConstructor(int.class, int.class, int.class,
                    int.class, int.class, int.class)
                .newInstance(x-1, -55, z-1, x+1, -53, z+1);
            liveShip = Class.forName(
                "dev.ryanhcode.sable.api.SubLevelAssemblyHelper")
                .getMethod("assembleBlocks", ServerLevel.class,
                    BlockPos.class, Iterable.class, boxType)
                .invoke(null, level, motorPos, blocks, bounds);
            if (liveShip == null)
                throw new IllegalStateException("Released Sable returned null");
            shipId = (UUID)liveShip.getClass().getMethod("getUniqueId")
                .invoke(liveShip);
            LOG.info("WORLDSEAM_REAL_AERO_NATIVE_POWER_ASSEMBLED: uuid={} nativeVoxels={} motor={} rotor={} axle=Y externalForce=false",
                shipId,blocks.size(),motorPos,rotorPos);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            helper.fail("Real Create/Aeronautics/Sable power fixture failed: "+e);
            return;
        }

        final long begun = level.getGameTime();
        helper.succeedWhen(() -> {
            SeamAeronauticsEvidence.Observation power =
                SeamAeronauticsEvidence.inspect(liveShip);
            if (power.powered()) {
                LOG.info("WORLDSEAM_REAL_AERO_NATIVE_POWER_PASS: uuid={} liveActors={} spinningActors={} observedThrust={} actualPowertrain=true externalForce=false ticks={}",
                    shipId,power.aeronauticsPropellerActors(),
                    power.spinningPropellers(),
                    power.totalAbsoluteThrust(),level.getGameTime()-begun);
                return;
            }
            // Don't mistake an assembled Sable raft for a motor-driven ship.
            // Running until success/timeout exercises actual BE tick logic.
            helper.assertTrue(false,
                "Aero propeller has no authentic Create kinetic RPM/thrust "+
                "(elapsedTicks="+(level.getGameTime()-begun)+"): "+
                power.diagnostic());
        });
    }
}
