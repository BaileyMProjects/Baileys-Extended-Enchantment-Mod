package com.bailey.enchantments.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/**
 * A buffer source that darkens every vertex written through it toward shadow-blue-black.
 *
 * <p>Every entity renderer — vanilla, GeckoLib, anything — ends up calling
 * {@code vertex(...).color(r, g, b, a)} on whatever consumer it was handed. Wrapping the
 * consumer and multiplying inside {@code color} recolours the whole mob without knowing what
 * it is or what texture it uses. It is a transform, not a repaint: detail survives as shading.
 *
 * <p>⚠️ Only the primitive methods are delegated. The composite {@code vertex(matrix, ...)} and
 * {@code putBulkData} defaults are left alone <b>on purpose</b>, so they decompose into calls on
 * this wrapper and pass through {@link Consumer#color}. Delegating them would bypass the tint.
 */
public final class ShadowTintBuffer implements MultiBufferSource {
    /** Multipliers. Black with a blue bias; the blue is what reads as "shadow" not "dark". */
    private static final float R = 0.16F, G = 0.20F, B = 0.42F;

    private final MultiBufferSource delegate;

    public ShadowTintBuffer(MultiBufferSource delegate) {
        this.delegate = delegate;
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        return new Consumer(delegate.getBuffer(type));
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
            out.color((int) (r * R), (int) (g * G), (int) (b * B), a);
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
            out.uv2(u, v);
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
            out.defaultColor((int) (r * R), (int) (g * G), (int) (b * B), a);
        }

        @Override
        public void unsetDefaultColor() {
            out.unsetDefaultColor();
        }
    }
}
