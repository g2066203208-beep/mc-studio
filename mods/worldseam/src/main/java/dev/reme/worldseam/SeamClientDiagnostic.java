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
        if (mc.level==null || mc.player==null || mc.options.hideGui ||
            !SeamStackClient.enabled()) return;
        // Do not cover the player's sky/terrain with a development overlay.
        // Show in F3 debug mode, or in explicitly enabled CI E2E screenshots.
        if (!Boolean.getBoolean("worldseam.e2e")
            && !mc.getDebugOverlay().showDebugScreen()) return;
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
            "界缝 | 当前维度: %s | Y=%.2f",dimension,mc.player.getY());
        String link=String.format(java.util.Locale.ROOT,
            "连接面 Y=%.0f  →  目标: %s Y=%.0f (%s)",
            currentSeam,closest.target(),otherSeam,
            closest.exit()==SeamGraph.EdgeSide.BOTTOM?"下方":"上方");
        String cache="已接收区块: "+SeamClientWorldPool.receivedChunks()+
            " | 远端缓存: "+SeamClientWorldPool.remoteWindowChunks()+
            "/25 | 远端模型: "+SeamNativeGeometryClient.visibleBlockCount()+
            " | 待重建段: "+SeamNativeGeometryClient.pendingRebuilds()+
            " | 本帧: "+SeamNativeGeometryClient.lastFrameDrawn()+
            " | 预算: "+SeamNativeGeometryClient.currentModelBudget()+
            " | CPU P95: "+String.format(java.util.Locale.ROOT,"%.1fms",SeamNativeGeometryClient.p95ModelCpuMs())+
            " | 片段: "+SeamNativeGeometryClient.visibleSlices()+"/"+
            (SeamNativeGeometryClient.visibleSlices()+SeamNativeGeometryClient.culledSlices())+
            " | 重建P95: "+String.format(java.util.Locale.ROOT,"%.1fms",SeamNativeGeometryClient.p95RebuildCpuMs())+
            " | 专用缓冲: "+(SeamNativeGeometryClient.ownBuffersActive()?"活动":"未分配")+
            " | 提交: "+SeamNativeGeometryClient.bufferFlushes()+
            " | 世界池: "+SeamClientWorldPool.dedicatedRendererActive()+
            " (新建"+SeamClientWorldPool.dedicatedRendererCreated()+
            "/释放"+SeamClientWorldPool.dedicatedRendererReleased()+")";
        String rawStatus=SeamClientWorldPool.remoteStatus();
        String detail=rawStatus.contains("FAILED")?"目标维度初始化失败，请看日志":
            rawStatus.contains("NO TARGET")?"等待目标维度区块":
            "正在同步目标维度地形（优先 3×3，然后扩展 5×5）";
        if (SeamClientWorldPool.receivedChunks()==0 && !rawStatus.contains("FAILED"))
            detail+=" | 请检查 /worldseam enable";
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
