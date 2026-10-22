package dev.reme.worldseam;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Runtime API contract: only the special CI job loads the THREE RELEASED
 * third-party mods. Standalone WorldSeam testing stays independent.
 *
 * This is an optional-dependency load/linkage test; it does NOT certify
 * Sable shared-Rapier impulse solving or a real assembled airship straddle.
 */
@GameTestHolder(WorldSeamMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SeamExternalModGameTests {
    private SeamExternalModGameTests() {}

    @GameTest(template="empty",batch="worldseam.real_external_mods",timeoutTicks=160)
    public static void loadRealSableCreateAndAeronautics(GameTestHelper helper) {
        if(!Boolean.getBoolean("worldseam.requireExternalMods")) {
            helper.succeed();
            return;
        }
        ModList mods=ModList.get();
        if(!mods.isLoaded("sable") || !mods.isLoaded("create")) {
            helper.fail("REAL MOD INTEGRATION FAIL: missing installed Sable or Create 6");
            return;
        }
        boolean aeronauticsPresent=mods.getMods().stream().anyMatch(info->
            info.getModId().toLowerCase(java.util.Locale.ROOT).contains("aero") ||
            info.getModId().toLowerCase(java.util.Locale.ROOT).contains("simulated") ||
            info.getDisplayName().toLowerCase(java.util.Locale.ROOT).contains("aeronautics"));
        if(!aeronauticsPresent) {
            helper.fail("REAL MOD INTEGRATION FAIL: Aeronautics bundled jar not loaded");
            return;
        }
        var server=helper.getLevel().getServer();
        SeamSableObserver.tick(server,10,true);
        var report=SeamSableObserver.snapshot();
        if(!report.installed() || !report.compatible()) {
            helper.fail("REAL MOD INTEGRATION FAIL: Sable API mismatch: "+
                SeamSableObserver.problem());
            return;
        }
        com.mojang.logging.LogUtils.getLogger().info(
            "WORLDSEAM_REAL_EXTERNAL_MODS_PASS: verified released Sable/Create/Aeronautics loaded; Sable sublevel reflection compatible, knownActiveShips={}, straddledShips={}, physicsBridgeActive={}",
            report.liveBodies(),report.straddlingBodies(),report.physicsBridgeActive());
        // The absence of assembled ships must never be reported as "ship
        // physics compatibility passed". It proves ONLY actual API loading.
        helper.succeed();
    }
}
