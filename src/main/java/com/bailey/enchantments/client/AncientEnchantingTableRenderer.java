package com.bailey.enchantments.client;

import com.bailey.enchantments.block.AncientEnchantingTableBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.model.BookModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class AncientEnchantingTableRenderer implements BlockEntityRenderer<AncientEnchantingTableBlockEntity> {
    @SuppressWarnings("deprecation")
    private static final Material BOOK_MATERIAL = new Material(
            TextureAtlas.LOCATION_BLOCKS,
            new ResourceLocation("entity/enchanting_table_book"));

    private final BookModel bookModel;

    public AncientEnchantingTableRenderer(BlockEntityRendererProvider.Context context) {
        this.bookModel = new BookModel(context.bakeLayer(ModelLayers.BOOK));
    }

    @Override
    public void render(AncientEnchantingTableBlockEntity entity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        float flip = Mth.lerp(partialTick, entity.oFlip, entity.flip);
        float open = Mth.lerp(partialTick, entity.oOpen, entity.open);
        float f = entity.rot - entity.oRot;
        while (f >= (float) Math.PI) f -= (float) (Math.PI * 2);
        while (f < (float) -Math.PI) f += (float) (Math.PI * 2);
        float yRot = entity.oRot + f * partialTick;

        float f1 = Mth.frac(flip + 0.25F) * 1.6F - 0.3F;
        float f2 = Mth.frac(flip + 0.75F) * 1.6F - 0.3F;
        float pageLeft = Mth.clamp(f1, 0.0F, 1.0F);
        float pageRight = Mth.clamp(f2, 0.0F, 1.0F);

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.75D, 0.5D);
        poseStack.translate(0.0F, 0.1F + Mth.sin(entity.time * 0.1F) * 0.01F, 0.0F);
        poseStack.mulPose(Axis.YP.rotation(-yRot));
        poseStack.mulPose(Axis.ZP.rotationDegrees(80.0F));

        this.bookModel.setupAnim(entity.time, pageLeft, pageRight, open);
        VertexConsumer vc = BOOK_MATERIAL.buffer(buffer, RenderType::entitySolid);
        this.bookModel.renderToBuffer(poseStack, vc, packedLight, OverlayTexture.NO_OVERLAY,
                1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();
    }
}
