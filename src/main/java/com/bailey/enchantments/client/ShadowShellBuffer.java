package com.bailey.enchantments.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * The shell: a second pass over the same model, slightly larger, painted solid light blue and
 * blended over the world as a halo.
 *
 * <h2>Why a second pass exists at all</h2>
 * The main pass writes a vertex colour, which the shader <b>multiplies</b> with the texture — so
 * an accent can never be brighter than the texel underneath it, and on a dark mob it barely
 * shows. This pass escapes that: it draws through an emissive translucent render type at full
 * lightmap with a fixed colour, so the halo's brightness owes nothing to the texture.
 *
 * <h2>⚠️ Every render type collapses into one</h2>
 * Whatever the renderer asks for — body, armour layer, eyes — it gets the same emissive
 * translucent buffer built from the <b>body</b> texture. The shell is a silhouette, not a
 * likeness, and the only thing the texture still decides is the alpha cutout, so the shape is
 * right even where the UVs are not. It also means no reflection is needed to dig a texture out
 * of a {@code RenderType}, which is the alternative.
 *
 * <p>🔶 A model whose texture is largely transparent in the region a layer samples will show
 * holes in its halo. Accepted: a ghost with a gap is better than the reflection it would take
 * to avoid it.
 */
public final class ShadowShellBuffer implements MultiBufferSource {

    /** The halo colour. */
    public static final int R = 138, G = 200, B = 255;

    /** How solid the halo is. Low: it is a glow, not a coat of paint. */
    public static final int ALPHA = 90;

    private final MultiBufferSource delegate;
    private final RenderType shell;

    public ShadowShellBuffer(MultiBufferSource delegate, ResourceLocation texture) {
        this.delegate = delegate;
        this.shell = RenderType.entityTranslucentEmissive(texture);
    }

    @Override
    public VertexConsumer getBuffer(RenderType ignored) {
        return new Consumer(delegate.getBuffer(shell));
    }

    private static final class Consumer implements VertexConsumer {
        private final VertexConsumer out;

        Consumer(VertexConsumer out) {
            this.out = out;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            out.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            // The incoming colour is discarded: the halo is one colour by definition. Alpha is
            // scaled by whatever the renderer asked for, so a layer already fading out fades out.
            out.color(R, G, B, ALPHA * a / 255);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            out.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            out.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            // Full bright, always. The halo must glow in a cave as brightly as at noon.
            out.uv2(LightTexture.FULL_BRIGHT & 0xFFFF, (LightTexture.FULL_BRIGHT >> 16) & 0xFFFF);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            out.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            out.endVertex();
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
            out.defaultColor(R, G, B, ALPHA * a / 255);
        }

        @Override
        public void unsetDefaultColor() {
            out.unsetDefaultColor();
        }
    }
}
