package dev.reme.mcstudio.npc.client;

import dev.reme.mcstudio.npc.PlayerNpcEntity;
import dev.reme.mcstudio.npc.NpcPose;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

public final class PlayerNpcRenderer extends MobRenderer<PlayerNpcEntity, PlayerModel<PlayerNpcEntity>> {
    // A victim must ACTUALLY leave the standing silhouette. Only applies to
    // server-synced STUNNED state, not a fake stun on any criminal without hits.
    private final Map<PlayerNpcEntity,Integer> realKnockdownStart = new WeakHashMap<>();
    private static final ResourceLocation[] SKINS = {
        ResourceLocation.fromNamespaceAndPath("reme_studio", "textures/entity/npc/green_farmer.png"),
        ResourceLocation.fromNamespaceAndPath("reme_studio", "textures/entity/npc/field_explorer.png"),
        ResourceLocation.fromNamespaceAndPath("reme_studio", "textures/entity/npc/formal_trader.png"),
        ResourceLocation.fromNamespaceAndPath("reme_studio", "textures/entity/npc/blue_farmer.png"),
        ResourceLocation.fromNamespaceAndPath("reme_studio", "textures/entity/npc/teal_explorer.png"),
        ResourceLocation.fromNamespaceAndPath("reme_studio", "textures/entity/npc/brown_trader.png")
    };

    public PlayerNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new LivingNpcPlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.45F);
        addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public void render(PlayerNpcEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int light) {
        if (entity.visualPose() == NpcPose.STUNNED) {
            // Whole-body root rotation makes the square vanilla silhouette
            // physically FALL rather than simply folding a torso while its
            // feet and model remain vertical. 11 tick ease-out impact.
            int start = realKnockdownStart.computeIfAbsent(
                entity, ignored -> entity.tickCount);
            float p = Math.clamp((entity.tickCount - start + partialTick) / 11.0F,
                0.0F, 1.0F);
            float eased = p * p * (3.0F - 2.0F * p);
            poseStack.pushPose();
            poseStack.mulPose(Axis.XP.rotationDegrees(82.0F * eased));
            super.render(entity, entityYaw, partialTick, poseStack, buffers, light);
            poseStack.popPose();
        } else {
            realKnockdownStart.remove(entity);
            super.render(entity, entityYaw, partialTick, poseStack, buffers, light);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(PlayerNpcEntity entity) {
        return SKINS[Math.clamp(entity.skinId(), 0, SKINS.length - 1)];
    }
}
