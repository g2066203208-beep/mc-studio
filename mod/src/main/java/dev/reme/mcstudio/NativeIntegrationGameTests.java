package dev.reme.mcstudio;

import java.lang.reflect.Method;
import java.util.Collection;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * Native runtime probes running INSIDE NeoForge's dedicated GameTestServer with
 * the officially released mods actually loaded. These deliberately do not claim
 * stress, wireless signal propagation, Sable constraints, or aircraft physics.
 *
 * These tests fail if the required official Mods are absent. This prevents a
 * vanilla-only JVM from being mistaken for a Create/Aeronautics runtime test.
 */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NativeIntegrationGameTests {
    private static final Logger LOG = LogUtils.getLogger();
    private NativeIntegrationGameTests() {}

    private static void requireMods() {
        for (String id : new String[]{"create", "simulated", "aeronautics"}) {
            if (!ModList.get().isLoaded(id)) {
                throw new AssertionError("Native validation requires loaded NeoForge Mod: " + id);
            }
        }
    }

    private static Block block(String id) {
        ResourceLocation name = ResourceLocation.parse(id);
        if (!BuiltInRegistries.BLOCK.containsKey(name)) {
            throw new AssertionError("Official block is not in live Minecraft registry: " + id);
        }
        Block result = BuiltInRegistries.BLOCK.get(name);
        LOG.info("RPMS_NATIVE_REGISTRY block={} implementation={}", id, result.getClass().getName());
        return result;
    }

    private static BlockEntity placeEntity(GameTestHelper helper, String id, BlockPos pos) {
        Block b = block(id);
        helper.setBlock(pos, b);
        helper.assertBlockPresent(b, pos);
        BlockEntity entity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (entity == null) {
            throw new AssertionError("Expected a real native BlockEntity after placing " + id);
        }
        LOG.info("RPMS_NATIVE_BLOCK_ENTITY id={} class={} properties={}", id,
                entity.getClass().getName(), helper.getBlockState(pos).getValues());
        return entity;
    }

    @GameTest(template = "rpms_empty", timeoutTicks = 120)
    public static void officialRegistriesAreLoaded(GameTestHelper helper) {
        requireMods();
        for (String id : new String[]{
                "create:shaft", "create:cogwheel", "create:large_cogwheel",
                "create:creative_motor", "create:redstone_link",
                "simulated:swivel_bearing", "simulated:gimbal_sensor",
                "aeronautics:andesite_propeller"
        }) block(id);
        LOG.info("RPMS_NATIVE_PASS officialRegistriesAreLoaded");
        helper.succeed();
    }

    @GameTest(template = "rpms_empty", timeoutTicks = 120)
    public static void createKineticBlockEntitiesExistAndTick(GameTestHelper helper) {
        requireMods();
        BlockPos motor = new BlockPos(1, 1, 1);
        BlockPos shaft = new BlockPos(2, 1, 1);
        // The motor's real Create shaft connector only emits along FACING.
        // A default NORTH motor beside a default Y shaft is NOT a valid
        // mechanical network: that setup yielded -16 RPM at the motor and
        // 0 RPM at the shaft during our first real run. Use aligned blocks.
        BlockState motorState = block("create:creative_motor").defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.EAST);
        BlockState shaftState = block("create:shaft").defaultBlockState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.X);
        helper.setBlock(motor, motorState);
        helper.setBlock(shaft, shaftState);
        helper.assertBlockPresent(block("create:creative_motor"), motor);
        helper.assertBlockPresent(block("create:shaft"), shaft);
        helper.runAfterDelay(30, () -> {
            BlockEntity liveMotor = helper.getLevel().getBlockEntity(helper.absolutePos(motor));
            BlockEntity liveShaft = helper.getLevel().getBlockEntity(helper.absolutePos(shaft));
            if (liveMotor == null || liveShaft == null) throw new AssertionError("Kinetic BE missing after server ticks");
            double motorSpeed = nativeNumber(liveMotor, "getSpeed");
            double shaftSpeed = nativeNumber(liveShaft, "getSpeed");
            LOG.info("RPMS_NATIVE_KINETIC motorSpeed={} shaftSpeed={} motorFacing={} shaftAxis={}",
                    motorSpeed, shaftSpeed, motorState.getValue(BlockStateProperties.FACING),
                    shaftState.getValue(BlockStateProperties.AXIS));
            if (Math.abs(motorSpeed) < 0.1 || Math.abs(shaftSpeed) < 0.1) {
                throw new AssertionError("Create kinetic power did not propagate into adjacent correctly aligned shaft");
            }
            if (Math.abs(Math.abs(shaftSpeed) - Math.abs(motorSpeed)) > 0.01) {
                throw new AssertionError("Incorrect native 1:1 shaft transmission: motor=" + motorSpeed + " shaft=" + shaftSpeed);
            }
            LOG.info("RPMS_NATIVE_PASS createKineticBlockEntitiesExistAndTick (aligned motor actually drives shaft)");
            helper.succeed();
        });
    }

    @GameTest(template = "rpms_empty", timeoutTicks = 120)
    public static void swivelBearingAndSensorRunInServer(GameTestHelper helper) {
        requireMods();
        BlockPos bearing = new BlockPos(1, 1, 1);
        BlockPos sensor = new BlockPos(3, 1, 1);
        placeEntity(helper, "simulated:swivel_bearing", bearing);
        placeEntity(helper, "simulated:gimbal_sensor", sensor);
        helper.runAfterDelay(20, () -> {
            if (helper.getLevel().getBlockEntity(helper.absolutePos(bearing)) == null) {
                throw new AssertionError("Swivel bearing BlockEntity lost while ticking");
            }
            LOG.info("RPMS_NATIVE_PASS swivelBearingAndSensorRunInServer (not proof of Sable constraint)");
            helper.succeed();
        });
    }

    @GameTest(template = "rpms_empty", timeoutTicks = 120)
    public static void wirelessRedstoneLinkHasNativeEntity(GameTestHelper helper) {
        requireMods();
        placeEntity(helper, "create:redstone_link", new BlockPos(1, 1, 1));
        LOG.info("RPMS_NATIVE_PASS wirelessRedstoneLinkHasNativeEntity (NOT signal propagation)");
        helper.succeed();
    }

    @GameTest(template = "rpms_empty", timeoutTicks = 120)
    public static void wirelessSignalActuallyPropagates(GameTestHelper helper) {
        requireMods();
        // Two actual Create Redstone Link blocks, both with an identical
        // native EMPTY/EMPTY frequency. No fake numeric signal is assigned to
        // the receiver. The source BE sends into Create's own wireless network.
        BlockPos senderPos = new BlockPos(1, 1, 1);
        BlockPos receiverPos = new BlockPos(3, 1, 1);
        helper.setBlock(senderPos.below(), Blocks.STONE);
        helper.setBlock(receiverPos.below(), Blocks.STONE);
        Block link = block("create:redstone_link");
        BooleanProperty receiving = (BooleanProperty) link.getStateDefinition().getProperty("receiver");
        if (receiving == null) throw new AssertionError("Official Redstone Link 'receiver' state missing");
        BlockState senderState = link.defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.UP)
                .setValue(receiving, false);
        BlockState receiverState = link.defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.UP)
                .setValue(receiving, true);
        helper.setBlock(senderPos, senderState);
        helper.setBlock(receiverPos, receiverState);
        helper.assertBlockPresent(link, senderPos);
        helper.assertBlockPresent(link, receiverPos);
        helper.runAfterDelay(25, () -> {
            BlockEntity sender = helper.getLevel().getBlockEntity(helper.absolutePos(senderPos));
            BlockEntity receiver = helper.getLevel().getBlockEntity(helper.absolutePos(receiverPos));
            if (sender == null || receiver == null) throw new AssertionError("Native Redstone Link entity missing");
            // Real Create links are paired with two actual item frequencies;
            // the empty initial pair is NOT a reliable functional test.
            nativeSetWirelessFrequency(sender, true, new ItemStack(Items.REDSTONE));
            nativeSetWirelessFrequency(sender, false, new ItemStack(Items.IRON_INGOT));
            nativeSetWirelessFrequency(receiver, true, new ItemStack(Items.REDSTONE));
            nativeSetWirelessFrequency(receiver, false, new ItemStack(Items.IRON_INGOT));
            LOG.info("RPMS_NATIVE_LINK_MODES tx={} rx={}", nativeLinkState(sender), nativeLinkState(receiver));
            nativeTransmit(sender, 15);
            helper.runAfterDelay(5, () -> {
                BlockEntity receivedEntity = helper.getLevel().getBlockEntity(helper.absolutePos(receiverPos));
                if (receivedEntity == null) throw new AssertionError("Native Redstone receiver entity missing");
                double got = nativeNumber(receivedEntity, "getReceivedSignal");
                LOG.info("RPMS_NATIVE_WIRELESS expected=15 received={} frequency=official_default", got);
                if (got != 15) throw new AssertionError("Create wireless signal did not actually propagate: " + got);
                nativeTransmit(sender, 0);
                helper.runAfterDelay(5, () -> {
                    BlockEntity receiverAfter = helper.getLevel().getBlockEntity(helper.absolutePos(receiverPos));
                    double off = nativeNumber(receiverAfter, "getReceivedSignal");
                    LOG.info("RPMS_NATIVE_WIRELESS expected=0 received={}", off);
                    if (off != 0) throw new AssertionError("Native Create wireless power did not turn off: " + off);
                    LOG.info("RPMS_NATIVE_PASS wirelessSignalActuallyPropagates");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "rpms_empty", timeoutTicks = 120)
    public static void kineticNetworkDisconnectionStopsShaft(GameTestHelper helper) {
        requireMods();
        BlockPos motor = new BlockPos(1, 1, 1);
        BlockPos middle = new BlockPos(2, 1, 1);
        BlockPos end = new BlockPos(3, 1, 1);
        BlockState motorState = block("create:creative_motor").defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.EAST);
        BlockState shaftState = block("create:shaft").defaultBlockState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.X);
        helper.setBlock(motor, motorState);
        helper.setBlock(middle, shaftState);
        helper.setBlock(end, shaftState);
        helper.runAfterDelay(30, () -> {
            BlockEntity last = helper.getLevel().getBlockEntity(helper.absolutePos(end));
            if (last == null) throw new AssertionError("Downstream kinetic entity missing");
            double running = nativeNumber(last, "getSpeed");
            LOG.info("RPMS_NATIVE_DISCONNECT phase=connected downstreamRPM={}", running);
            if (Math.abs(running) < 0.1) throw new AssertionError("Create did not transmit torque to second shaft");
            helper.setBlock(middle, Blocks.AIR);
            helper.runAfterDelay(20, () -> {
                BlockEntity disconnected = helper.getLevel().getBlockEntity(helper.absolutePos(end));
                if (disconnected == null) throw new AssertionError("Downstream shaft destroyed unexpectedly");
                double stopped = nativeNumber(disconnected, "getSpeed");
                LOG.info("RPMS_NATIVE_DISCONNECT phase=removed_middle downstreamRPM={}", stopped);
                if (Math.abs(stopped) > 0.01) {
                    throw new AssertionError("Real Create network did not stop after disconnected shaft: " + stopped);
                }
                LOG.info("RPMS_NATIVE_PASS kineticNetworkDisconnectionStopsShaft");
                helper.succeed();
            });
        });
    }

    private static Object nativeLink(Object blockEntity) {
        try {
            // SmartBlockEntity's public behaviour list allows us to use the
            // official LinkBehaviour without statically depending on Create.
            Object all = blockEntity.getClass().getMethod("getAllBehaviours").invoke(blockEntity);
            if (all instanceof Collection<?> behaviours) {
                for (Object b : behaviours) {
                    if (b.getClass().getSimpleName().equals("LinkBehaviour")) return b;
                }
            }
            throw new AssertionError("Official Create LinkBehaviour was not initialized");
        } catch (ReflectiveOperationException | SecurityException e) {
            throw new AssertionError("Could not inspect native Create wireless behaviours", e);
        }
    }

    private static void nativeSetWirelessFrequency(Object be, boolean first, ItemStack stack) {
        Object link = nativeLink(be);
        try {
            link.getClass().getMethod("setFrequency", boolean.class, ItemStack.class).invoke(link, first, stack);
        } catch (ReflectiveOperationException | SecurityException e) {
            throw new AssertionError("Cannot configure native Create wireless link frequency", e);
        }
    }

    private static String nativeLinkState(Object be) {
        Object link = nativeLink(be);
        try {
            Object listening = link.getClass().getMethod("isListening").invoke(link);
            Object alive = link.getClass().getMethod("isAlive").invoke(link);
            Object key = link.getClass().getMethod("getNetworkKey").invoke(link);
            return "listening=" + listening + ",alive=" + alive + ",frequency=" + key;
        } catch (ReflectiveOperationException | SecurityException e) {
            return "unreadable:" + e.getClass().getSimpleName();
        }
    }

    private static void nativeTransmit(Object sender, int strength) {
        try {
            sender.getClass().getMethod("transmit", int.class).invoke(sender, strength);
        } catch (ReflectiveOperationException | SecurityException e) {
            throw new AssertionError("Cannot drive the official Create Redstone Link transmitter: " + e, e);
        }
    }

    private static double nativeNumber(Object receiver, String methodName) {
        try {
            Method method = receiver.getClass().getMethod(methodName);
            Object value = method.invoke(receiver);
            if (value instanceof Number n) return n.doubleValue();
            throw new AssertionError("Expected numerical Create runtime value for " + methodName);
        } catch (ReflectiveOperationException | SecurityException e) {
            throw new AssertionError("Unable to read native Create runtime speed: " + e, e);
        }
    }
}
