package dev.reme.worldseam;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Minecraft's AABB lives in NeoForge's real game runtime, NOT on the isolated
 * pure-JUnit test classpath. Exercise the two-chart geometry in GameTestServer.
 */
@GameTestHolder(WorldSeamMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SeamGeometryGameTests {
    private SeamGeometryGameTests() {}

    private static boolean near(double a,double b) {
        return Math.abs(a-b)<1.e-7;
    }

    @GameTest(template="empty",batch="worldseam.geometry",timeoutTicks=100)
    public static void realAabbSplitOverworldNether(GameTestHelper h) {
        var box=new AABB(-0.3,127.875,-0.3,0.3,129.675,0.3);
        var s=SeamBodySplit.atTargetBoundary(box,SeamGraph.EdgeSide.TOP,128,-64);
        if(s.targetPart()==null||s.sourcePart()==null ||
           !near(s.targetPart().minY,127.875) ||
           !near(s.targetPart().maxY,128) ||
           !near(s.sourcePart().minY,-64) ||
           !near(s.sourcePart().maxY,-62.325)) {
            h.fail("Real AABB Nether logical roof failed to split into Overworld floor");
            return;
        }
        var reverse=SeamBodySplit.atTargetBoundary(
            new AABB(-.3,-64.5,-.3,.3,-62.7,.3),
            SeamGraph.EdgeSide.BOTTOM,-64,128);
        if(reverse.targetPart()==null||reverse.sourcePart()==null||
           !near(reverse.targetPart().minY,-64)||
           !near(reverse.sourcePart().minY,127.5)||
           !near(reverse.sourcePart().maxY,128)) {
            h.fail("Real AABB inverse clip of Overworld floor vs Nether roof failed");
            return;
        }
        var no=SeamBodySplit.atTargetBoundary(
            new AABB(0,-63.8,0,1,-62,1),SeamGraph.EdgeSide.BOTTOM,-64,128);
        if(no.sourcePart()!=null||no.targetPart()==null) {
            h.fail("Non-straddling AABB falsely acquired an image");
            return;
        }
        var custom=SeamBodySplit.atTargetBoundary(
            new AABB(-11,204,-16,-10,211,-15),
            SeamGraph.EdgeSide.TOP,209,37);
        if(custom.targetPart()==null||custom.sourcePart()==null||
           !near(custom.targetPart().maxY,209)||
           !near(custom.sourcePart().minY,37)||
           !near(custom.sourcePart().maxY,39)) {
            h.fail("Custom Y, unaligned 1:1 seam clip failed");
            return;
        }
        com.mojang.logging.LogUtils.getLogger().info(
            "WORLDSEAM_GEOMETRY_GAME_TEST_PASS: real AABBs clipped at both logical planes and arbitrary Y");
        h.succeed();
    }

    @GameTest(template="empty",batch="worldseam.geometry",timeoutTicks=100)
    public static void real64WideOneShipTwoChartProjection(GameTestHelper h) {
        var down=SeamStack.defaults().from("minecraft:overworld").stream()
            .filter(l->l.exit()==SeamGraph.EdgeSide.BOTTOM).findFirst().orElseThrow();
        var up=SeamStack.defaults().from("minecraft:the_nether").stream()
            .filter(l->l.exit()==SeamGraph.EdgeSide.TOP).findFirst().orElseThrow();
        var large=new AABB(-32,-70,-8,32,-57,8);
        var split=SeamShipChartBounds.project(large,down,-64,128);
        if(!split.straddles() || split.homePart()==null ||
           split.neighboringPart()==null ||
           !near(split.homePart().minY,-64) ||
           !near(split.homePart().maxY,-57) ||
           !near(split.neighboringPart().minY,122) ||
           !near(split.neighboringPart().maxY,128) ||
           !near(split.neighboringPart().minX,-32) ||
           !near(split.neighboringPart().maxX,32)) {
            h.fail("64-block physical ship bounds did not clip exactly into two chart hulls");
            return;
        }
        var body=new AABB(10,-67,10,18,-59,18);
        var forward=SeamShipChartBounds.project(body,down,-64,128);
        var backward=SeamShipChartBounds.project(body.move(0,192,0),up,128,-64);
        if(!forward.straddles()||!backward.straddles()||
           !forward.homePart().equals(backward.neighboringPart())||
           !forward.neighboringPart().equals(backward.homePart())) {
            h.fail("Single physical ship's two chart projections are not inverse");
            return;
        }
        var allHome=SeamShipChartBounds.project(
            new AABB(0,-60,0,2,-56,2),down,-64,128);
        if(allHome.straddles()||allHome.homePart()==null||
           allHome.neighboringPart()!=null) {
            h.fail("Single-chart hull falsely generated a second-image collider");
            return;
        }
        com.mojang.logging.LogUtils.getLogger().info(
            "WORLDSEAM_SHIP_CHART_GAME_TEST_PASS: native MC AABB 64-wide hull, inverse chart planes, no cloned body");
        h.succeed();
    }
}
