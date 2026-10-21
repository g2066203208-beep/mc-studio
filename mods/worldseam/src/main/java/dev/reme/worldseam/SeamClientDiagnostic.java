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
        for (var link:SeamGraph.from(dimension)) {
            double bound=SeamGraph.boundaryY(link.exit(),mc.level.getMinBuildHeight(),
                SeamBounds.top(dimension,mc.level.getMaxBuildHeight()));
            double dist=Math.abs(mc.player.getY()-bound);
            if (dist<distance) { closest=link;distance=dist; }
        }
        if (closest==null || distance>96) return;
        var g=e.getGuiGraphics();
        String info="WorldSeam | "+closest.target()+" | seam "+
            (closest.exit()==SeamGraph.EdgeSide.TOP?"TOP":"BOTTOM")+
            " | distance "+String.format(java.util.Locale.ROOT,"%.1f",distance);
        String cache="native packets: "+SeamClientWorldPool.receivedChunks()+
            " | cached 3x3: "+SeamClientWorldPool.nearbyChunks()+
            "/9 | geometry: "+SeamNativeGeometryClient.visibleBlockCount()+
            " | drawn: "+SeamNativeGeometryClient.lastFrameDrawn();
        g.fill(5,5,Math.max(360,mc.font.width(cache)+16),36,0x9A101820);
        g.drawString(mc.font,info,10,10,0xE8E8E8,true);
        g.drawString(mc.font,cache,10,23,
            SeamNativeGeometryClient.lastFrameDrawn()>0 ? 0x66FF99 : 0xFFDD77,true);
    }
}
