package com.bailey.enchantments.client;

import com.bailey.enchantments.shadow.ShadowPlayer;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * A player shadow drawn with the player model, the dead player's skin, and their armour. The
 * shadow tint is applied on top by {@link ShadowRenderClient} like for every other shadow.
 */
public class ShadowPlayerRenderer extends HumanoidMobRenderer<ShadowPlayer, PlayerModel<ShadowPlayer>> {
    private final PlayerModel<ShadowPlayer> wide;
    private final PlayerModel<ShadowPlayer> slim;

    public ShadowPlayerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.wide = this.model;
        this.slim = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                ctx.getModelManager()));
    }

    @Override
    public void render(ShadowPlayer entity, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        this.model = ShadowSkins.slim(entity) ? slim : wide;
        this.model.setAllVisible(true);
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ShadowPlayer entity) {
        return ShadowSkins.skin(entity);
    }
}
