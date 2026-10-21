package dev.reme.mcstudio;

import java.lang.reflect.Method;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
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
        BlockEntity motorEntity = placeEntity(helper, "create:creative_motor", motor);
        BlockEntity shaftEntity = placeEntity(helper, "create:shaft", shaft);
        helper.runAfterDelay(30, () -> {
            // Genuine BlockEntity classes are loaded, instantiated and still
            // active after server ticks. Speed is READ, not faked by JS.
            BlockEntity liveMotor = helper.getLevel().getBlockEntity(helper.absolutePos(motor));
            BlockEntity liveShaft = helper.getLevel().getBlockEntity(helper.absolutePos(shaft));
            if (liveMotor == null || liveShaft == null) throw new AssertionError("Native kinetic BE disappeared after ticking");
            LOG.info("RPMS_NATIVE_KINETIC motor={} shaft={} speedProbe={} shaftSpeedProbe={}",
                    liveMotor.getClass().getName(), liveShaft.getClass().getName(),
                    readNumber(liveMotor, "getSpeed"), readNumber(liveShaft, "getSpeed"));
            LOG.info("RPMS_NATIVE_PASS createKineticBlockEntitiesExistAndTick (not proof of full stress network)");
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

    private static String readNumber(Object receiver, String methodName) {
        try {
            Method method = receiver.getClass().getMethod(methodName);
            Object value = method.invoke(receiver);
            return String.valueOf(value);
        } catch (ReflectiveOperationException | SecurityException e) {
            return "UNSUPPORTED:" + e.getClass().getSimpleName();
        }
    }
}
