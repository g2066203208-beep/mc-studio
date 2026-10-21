package dev.reme.mcstudio;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * Read-only inspection of the ACTUAL loaded Sable physics system, per-dimension
 * gravity and drag. Values are from Sable's DimensionPhysicsData, not guessed
 * from Minecraft's generic gravity or invented JavaScript physics.
 *
 * This does NOT create a moving sub-level or demonstrate physical forces;
 * those require additional GameTests with assembled bodies.
 */
public final class NativeSableTelemetry {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String SYSTEM = "dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem";
    private static final String DIMENSION = "dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData";

    private NativeSableTelemetry() {}

    public static JsonObject read(ServerLevel level) {
        JsonObject report = new JsonObject();
        report.addProperty("source", "native_sable_server_runtime");
        report.addProperty("dimension", level.dimension().location().toString());
        try {
            Class<?> physicsClass = Class.forName(SYSTEM);
            Object system = physicsClass.getMethod("get", Level.class).invoke(null, level);
            if (system == null) throw new IllegalStateException("No Sable physics world for dimension");
            Object pipeline = physicsClass.getMethod("getPipeline").invoke(system);
            if (pipeline == null) throw new IllegalStateException("Sable physics pipeline not initialized");
            report.addProperty("pipelineClass", pipeline.getClass().getName());
            report.addProperty("paused", (Boolean) physicsClass.getMethod("getPaused").invoke(system));
            Class<?> data = Class.forName(DIMENSION);
            Object gravity = data.getMethod("getGravity", Level.class).invoke(null, level);
            JsonArray vector = new JsonArray();
            for (String axis : new String[]{"x", "y", "z"}) {
                Number value = (Number) gravity.getClass().getMethod(axis).invoke(gravity);
                if (!Double.isFinite(value.doubleValue())) throw new IllegalStateException("Nonfinite gravity");
                vector.add(value.doubleValue());
            }
            report.add("gravity", vector);
            Number drag = (Number) data.getMethod("getUniversalDrag", ServerLevel.class).invoke(null, level);
            if (!Double.isFinite(drag.doubleValue()) || drag.doubleValue() < 0) {
                throw new IllegalStateException("Invalid Sable universal drag");
            }
            report.addProperty("universalDrag", drag.doubleValue());
            report.addProperty("available", true);
            report.addProperty("verifiedMeaning", "Native Sable physics pipeline is initialized; current configured gravity and global drag were read from real game runtime. No free-fall or aerodynamic flight test has yet run.");
        } catch (ReflectiveOperationException | RuntimeException ex) {
            report.addProperty("available", false);
            report.addProperty("error", ex.getClass().getSimpleName() + ": " + ex.getMessage());
            LOG.warn("RPMS_SABLE_TELEMETRY_UNAVAILABLE dimension={} reason={}", level.dimension().location(), ex.toString());
        }
        return report;
    }
}
