package dev.reme.mcstudio.worldstack;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Optional integration with Immersive Portals (NeoForge).
 *
 * Uses the upstream dimension-stack implementation rather than duplicating
 * portal geometry, chunk synchronization or teleportation. Reflection avoids
 * shipping/repackaging upstream classes and allows the studio base mod to
 * load when Immersive Portals is absent.
 *
 * Reviewed against:
 * UpperMoon0/ImmersivePortals @ ad2e7ee5d73e4ff78bd0befade4f797265e65939
 * iPortalTeam/ImmersivePortalsModForNeo @ aede93a4865fe4aab5dd2781fb38ab3e5cecd63b
 */
public final class WorldStackBridge {
    private static final String PREFIX = "qouteall.imm_ptl.";
    private static final String STACK_MANAGEMENT = PREFIX + "peripheral.dim_stack.DimStackManagement";
    private static final String STACK_INFO = PREFIX + "peripheral.dim_stack.DimStackInfo";
    private static final String STACK_ENTRY = PREFIX + "peripheral.dim_stack.DimStackEntry";
    private static final String GLOBAL_STORAGE = PREFIX + "core.portal.global_portals.GlobalPortalStorage";
    private static final String VERTICAL_PORTAL = PREFIX + "core.portal.global_portals.VerticalConnectingPortal";

    public enum InstallResult {
        INSTALLED,
        ALREADY_PRESENT,
        FOREIGN_GLOBAL_PORTALS,
        MISSING_DIMENSION,
        UNEXPECTED_PORTAL_COUNT
    }

    public record Status(
        boolean dependencyPresent, int endFloor, int overworldConnections,
        int netherCeiling, boolean directionsVerified
    ) {
        public boolean installed() {
            return directionsVerified && endFloor == 1
                && overworldConnections == 2 && netherCeiling == 1;
        }
    }

    private WorldStackBridge() {}

    public static boolean dependencyPresent() {
        ModList mods = ModList.get();
        return mods != null && (mods.isLoaded("immersive_portals_core") || mods.isLoaded("imm_ptl"));
    }

    public static Status status(MinecraftServer server) throws ReflectiveOperationException {
        if (!dependencyPresent()) {
            return new Status(false, 0, 0, 0, false);
        }
        return new Status(
            true,
            countVertical(server.getLevel(Level.END)),
            countVertical(server.getLevel(Level.OVERWORLD)),
            countVertical(server.getLevel(Level.NETHER)),
            hasDirectedPortal(server.getLevel(Level.END), Level.OVERWORLD, true)
                && hasDirectedPortal(server.getLevel(Level.OVERWORLD), Level.END, false)
                && hasDirectedPortal(server.getLevel(Level.OVERWORLD), Level.NETHER, true)
                && hasDirectedPortal(server.getLevel(Level.NETHER), Level.OVERWORLD, false)
        );
    }

    private static int countVertical(ServerLevel level) throws ReflectiveOperationException {
        if (level == null) {
            return 0;
        }
        Class<?> portalType = Class.forName(VERTICAL_PORTAL);
        return (int) globalPortals(level).stream().filter(portalType::isInstance).count();
    }

    /**
     * Counting portal surfaces alone is insufficient: an unrelated dimension
     * stack can have the same 1/2/1 count but point to different destinations.
     * Upstream calls positive-Y normals "floor", negative-Y "ceil".
     */
    private static boolean hasDirectedPortal(
        ServerLevel from, ResourceKey<Level> destination, boolean floor
    ) throws ReflectiveOperationException {
        if (from == null) {
            return false;
        }
        Class<?> portalType = Class.forName(VERTICAL_PORTAL);
        for (Object portal : globalPortals(from)) {
            if (!portalType.isInstance(portal)) {
                continue;
            }
            Object dest = portal.getClass().getMethod("getDestDim").invoke(portal);
            Vec3 normal = (Vec3) portal.getClass().getMethod("getNormal").invoke(portal);
            if (destination.equals(dest) && (floor ? normal.y > 0 : normal.y < 0)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> globalPortals(ServerLevel level) throws ReflectiveOperationException {
        Class<?> storage = Class.forName(GLOBAL_STORAGE);
        Method method = storage.getMethod("getGlobalPortals", Level.class);
        return (List<Object>) method.invoke(null, level);
    }

    /**
     * Explicit, permission-gated operation. Do not overwrite existing stack
     * portals or any unrelated global portal network.
     */
    public static InstallResult install(MinecraftServer server) throws ReflectiveOperationException {
        if (!dependencyPresent()) {
            throw new IllegalStateException("Immersive Portals is not installed");
        }
        WorldStackLayout.validate(WorldStackLayout.DEFAULT);
        for (WorldStackLayout.Layer layer : WorldStackLayout.DEFAULT) {
            if (server.getLevel(key(layer.dimensionId())) == null) {
                return InstallResult.MISSING_DIMENSION;
            }
        }

        Class<?> management = Class.forName(STACK_MANAGEMENT);
        boolean alreadyStacked = (boolean) management
            .getMethod("hasDimStackPortal", MinecraftServer.class).invoke(null, server);
        if (alreadyStacked) {
            return InstallResult.ALREADY_PRESENT;
        }
        // Existing portals are user data; never clear them as a side effect.
        for (ServerLevel level : server.getAllLevels()) {
            if (!globalPortals(level).isEmpty()) {
                return InstallResult.FOREIGN_GLOBAL_PORTALS;
            }
        }

        Class<?> infoType = Class.forName(STACK_INFO);
        Class<?> entryType = Class.forName(STACK_ENTRY);
        Object info = infoType.getConstructor().newInstance();
        @SuppressWarnings("unchecked")
        List<Object> entries = (List<Object>) infoType.getField("entries").get(info);
        Constructor<?> entryCtor = entryType.getConstructor(ResourceKey.class);
        Field scale = entryType.getField("scale");
        Field replacement = entryType.getField("bedrockReplacementStr");
        Field previous = entryType.getField("connectsPrevious");
        Field next = entryType.getField("connectsNext");
        for (WorldStackLayout.Layer layer : WorldStackLayout.DEFAULT) {
            Object entry = entryCtor.newInstance(key(layer.dimensionId()));
            scale.setDouble(entry, layer.scale());
            replacement.set(entry, layer.bedrockReplacement());
            previous.setBoolean(entry, true);
            next.setBoolean(entry, true);
            entries.add(entry);
        }
        infoType.getField("loop").setBoolean(info, false);
        infoType.getField("gravityTransform").setBoolean(info, false);

        // Deliberately do not call DimStackManagement's private clear/update.
        // apply() persists global portals and bedrock rules using upstream code.
        try {
            infoType.getMethod("apply", MinecraftServer.class).invoke(info, server);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw e;
        }

        return status(server).installed()
            ? InstallResult.INSTALLED
            : InstallResult.UNEXPECTED_PORTAL_COUNT;
    }

    private static ResourceKey<Level> key(String id) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(id));
    }
}
