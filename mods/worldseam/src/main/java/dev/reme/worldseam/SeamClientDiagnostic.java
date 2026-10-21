package dev.reme.worldseam;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Permanent near-seam diagnostic; no more invisible failures. */
public final class SeamClientDiagnostic {
    private SeamClientDiagnostic() {}
    public static void install() { NeoForge.EVENT_BUS.addListener(SeamClientDiagnostic::draw); }

    private static void draw(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level==null || mc.player==null || mc.options.hideGui) return;
        String dimension=mc.level.dimension().location().toString();
        SeamGraph.Link closest=null;
        double distance=Double.POSITIVE_INFINITY;
        for (var link:SeamStackClient.from(dimension)) {
            double bound=SeamStackClient.boundary(dimension,link.exit(),mc.level.getMinBuildHeight(),
                mc.level.getMaxBuildHeight());
            double dist=Math.abs(mc.player.getY()-bound);
            if (dist<distance) { closest=link;distance=dist; }
        }
        if (closest==null || distance>96) return;
        var g=e.getGuiGraphics();
        // Explicit source/target labels: earlier HUD printed ONLY the
        // target ("minecraft:the_nether"), which appeared to claim the
        // player was already IN the Nether while still in Overworld.
        double currentSeam=SeamStackClient.boundary(dimension,closest.exit(),
            mc.level.getMinBuildHeight(),mc.level.getMaxBuildHeight());
        int otherMin=SeamStackClient.minY(closest.target());
        int otherVanillaTop=SeamStackClient.maxY(closest.target());
        int otherTop=SeamStackClient.boundary(closest.target(),SeamGraph.EdgeSide.TOP,
            otherMin,otherVanillaTop);
        double otherSeam=SeamStackClient.boundary(closest.target(),closest.entry(),otherMin,otherVanillaTop);
        String here=String.format(java.util.Locale.ROOT,
            "WorldSeam CURRENT: %s Y=%.2f",dimension,mc.player.getY());
        String link=String.format(java.util.Locale.ROOT,
            "EDGE: source Y=%.0f  ->  TARGET: %s Y=%.0f (%s)",
            currentSeam,closest.target(),otherSeam,
            closest.exit()==SeamGraph.EdgeSide.BOTTOM?"below":"above");
        String cache="native packets: "+SeamClientWorldPool.receivedChunks()+
            " | cached 3x3: "+SeamClientWorldPool.nearbyChunks()+
            "/9 | geometry: "+SeamNativeGeometryClient.visibleBlockCount()+
            " | drawn: "+SeamNativeGeometryClient.lastFrameDrawn();
        String detail=SeamClientWorldPool.remoteStatus();
        if (SeamClientWorldPool.receivedChunks()==0 && !detail.contains("FAILED"))
            detail+=" | no packets: ensure /worldseam enable";
        int wide=Math.max(Math.max(mc.font.width(here),mc.font.width(link)),
            Math.max(mc.font.width(cache),mc.font.width(detail)));
        g.fill(5,5,Math.max(390,wide+16),62,0x9A101820);
        g.drawString(mc.font,here,10,10,0xE8E8E8,true);
        g.drawString(mc.font,link,10,23,0xDDDDEE,true);
        g.drawString(mc.font,cache,10,36,
            SeamNativeGeometryClient.lastFrameDrawn()>0 ? 0x66FF99 : 0xFFDD77,true);
        g.drawString(mc.font,detail,10,49,0xFFAACC,true);
    }
}
