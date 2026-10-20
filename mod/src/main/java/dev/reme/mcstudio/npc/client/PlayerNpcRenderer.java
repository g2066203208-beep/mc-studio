package dev.reme.mcstudio.npc.client;

import dev.reme.mcstudio.npc.PlayerNpcEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

public final class PlayerNpcRenderer extends MobRenderer<PlayerNpcEntity, PlayerModel<PlayerNpcEntity>> {
    private static final ResourceLocation TEXTURE =
        ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");

    public PlayerNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.45F);
        addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(PlayerNpcEntity entity) {
        return TEXTURE;
    }
}
