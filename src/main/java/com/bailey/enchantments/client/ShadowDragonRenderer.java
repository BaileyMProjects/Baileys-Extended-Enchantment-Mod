package com.bailey.enchantments.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;

/**
 * The vanilla dragon renderer, drawn through the shadow tint. The dragon is not a
 * {@code LivingEntityRenderer}, so {@link ShadowRenderClient}'s event never sees her; the tint
 * is applied here instead.
 */
public class ShadowDragonRenderer extends EnderDragonRenderer {
    public ShadowDragonRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(EnderDragon dragon, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        super.render(dragon, yaw, partialTick, pose, new ShadowTintBuffer(buffers), light);
    }
}
