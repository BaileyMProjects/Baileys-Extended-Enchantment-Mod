package com.bailey.enchantments.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/**
 * A buffer source that repaints every vertex written through it into the shadow palette.
 *
 * <p>Every entity renderer — vanilla, GeckoLib, anything — ends up calling
 * {@code vertex(...).color(r, g, b, a)} on whatever consumer it was handed. Wrapping the
 * consumer and rewriting inside {@code color} recolours the whole mob without knowing what it
 * is or what texture it uses.
 *
 * <h2>⚠️ A luminance REMAP, not a multiply</h2>
 * Bailey, 2026-09-22: <i>"replace some of the bolder colours with just shades of light blue and
 * have the rest really dark blue and close to black. Because everything just looked really
 * dark."</i> He is right, and the reason is arithmetic: multiplying every channel toward dark
 * takes an already-dark texture (a zombie, a Warden) to near-black mush with no shape left.
 *
 * <p>So the pixel's <b>brightness</b> is measured and used to pick a colour along a ramp from
 * near-black navy to light blue. A dark pixel stays dark; a bright pixel becomes <i>bright light
 * blue</i> rather than dark blue. Contrast is pushed a little first, so the midtones separate
 * instead of all landing in the middle of the ramp. The result reads as a shadow at a glance and
 * still has a face.
 */
public final class ShadowTintBuffer implements MultiBufferSource {

    /** The dark end of the ramp: deep navy, nearly black. */
    private static final float DARK_R = 0.020F, DARK_G = 0.035F, DARK_B = 0.094F;

    /** The light end: the pale blue everything bright becomes. */
    private static final float LIGHT_R = 0.541F, LIGHT_G = 0.784F, LIGHT_B = 1.000F;

    /**
     * Brightness below this is floored to the dark end, above it stretched over the ramp.
     * ⚠️ Without the stretch a mob whose texture lives between 20% and 60% brightness — which is
     * most of them — occupies only the middle of the ramp and comes out uniformly murky.
     */
    private static final float FLOOR = 0.10F;
    private static final float CEIL = 0.72F;

    private final MultiBufferSource delegate;

    public ShadowTintBuffer(MultiBufferSource delegate) {
        this.delegate = delegate;
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        return new Consumer(delegate.getBuffer(type));
    }

    /** Perceived brightness, 0..1, of an 0..255 colour. */
    private static float luminance(int r, int g, int b) {
        return (0.2126F * r + 0.7152F * g + 0.0722F * b) / 255.0F;
    }

    private static int ramp(float t, float dark, float light) {
        return (int) ((dark + (light - dark) * t) * 255.0F);
    }

    private static final class Consumer implements VertexConsumer {
        private final VertexConsumer out;

        Consumer(VertexConsumer out) {
            this.out = out;
        }

        private VertexConsumer shade(int r, int g, int b, int a) {
            float lum = luminance(r, g, b);
            float t = (lum - FLOOR) / (CEIL - FLOOR);
            t = t < 0 ? 0 : t > 1 ? 1 : t;
            // Ease toward the light end, so highlights read as highlights rather than as
            // "slightly less dark". Cheap curve, no pow().
            t = t * t * (3 - 2 * t);
            out.color(ramp(t, DARK_R, LIGHT_R), ramp(t, DARK_G, LIGHT_G), ramp(t, DARK_B, LIGHT_B), a);
            return this;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            out.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            return shade(r, g, b, a);
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
            float lum = luminance(r, g, b);
            float t = (lum - FLOOR) / (CEIL - FLOOR);
            t = t < 0 ? 0 : t > 1 ? 1 : t;
            t = t * t * (3 - 2 * t);
            out.defaultColor(ramp(t, DARK_R, LIGHT_R), ramp(t, DARK_G, LIGHT_G), ramp(t, DARK_B, LIGHT_B), a);
        }

        @Override
        public void unsetDefaultColor() {
            out.unsetDefaultColor();
        }
    }
}
