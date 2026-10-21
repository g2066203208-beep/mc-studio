package dev.reme.mcstudio.npc.client;

import dev.reme.mcstudio.npc.PlayerNpcEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

public final class PlayerNpcRenderer extends MobRenderer<PlayerNpcEntity, PlayerModel<PlayerNpcEntity>> {
    private static final ResourceLocation[] SKINS = {
        ResourceLocation.fromNamespaceAndPath("reme_studio", "textures/entity/npc/green_farmer.png"),
        ResourceLocation.fromNamespaceAndPath("reme_studio", "textures/entity/npc/field_explorer.png"),
        ResourceLocation.fromNamespaceAndPath("reme_studio", "textures/entity/npc/formal_trader.png"),
        ResourceLocation.fromNamespaceAndPath("reme_studio", "textures/entity/npc/blue_farmer.png"),
        ResourceLocation.fromNamespaceAndPath("reme_studio", "textures/entity/npc/teal_explorer.png"),
        ResourceLocation.fromNamespaceAndPath("reme_studio", "textures/entity/npc/brown_trader.png")
    };

    public PlayerNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.45F);
        addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(PlayerNpcEntity entity) {
        return SKINS[Math.clamp(entity.skinId(), 0, SKINS.length - 1)];
    }
}
