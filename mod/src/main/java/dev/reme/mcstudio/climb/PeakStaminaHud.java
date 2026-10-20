package dev.reme.mcstudio.climb;

import dev.reme.mcstudio.StudioMod;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** PEAK-style compact bar, only drawn while climbing or refilling stamina. */
@EventBusSubscriber(modid = StudioMod.MOD_ID, value = Dist.CLIENT)
public final class PeakStaminaHud {
    private PeakStaminaHud() {}

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        ClimbState s = ClimbState.of(mc.player);
        if (s.stamina >= 99.99f && !s.peakHeld) return;
        int width = 84, height = 5;
        int x = event.getGuiGraphics().guiWidth() / 2 - width / 2;
        int y = event.getGuiGraphics().guiHeight() - 60;
        int filled = Math.min(width - 2, Math.max(0,
                Math.round((width - 2) * s.stamina / PeakGripRules.MAX_STAMINA)));
        int color = s.stamina < 22 ? 0xFFE27562 : 0xFFEDC978;
        event.getGuiGraphics().fill(x, y, x + width, y + height, 0xAA17212C);
        event.getGuiGraphics().fill(x + 1, y + 1, x + 1 + filled, y + height - 1, color);
    }
}
