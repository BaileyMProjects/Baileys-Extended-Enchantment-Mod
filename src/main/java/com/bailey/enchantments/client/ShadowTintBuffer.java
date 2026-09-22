package com.bailey.enchantments.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Vector3f;

/**
 * Repaints every vertex written through it into the shadow palette: a near-black navy body with
 * light-blue accents, the way the shadows look in the show.
 *
 * <p>Every entity renderer — vanilla, GeckoLib, anything — ends up calling
 * {@code vertex(...).color(r, g, b, a)} on whatever consumer it was handed, so wrapping the
 * consumer recolours the whole mob without knowing what it is or what texture it uses.
 *
 * <h2>Why this is not a tint</h2>
 * Bailey, 2026-09-22: <i>"I wanted them to be really dark blue with light blue accents just like
 * the show and they are just basically tinted."</i> Right on both counts. A multiply darkens
 * everything equally; a smooth ramp shifts everything together. Neither produces a <b>dark body
 * with bright edges</b>, which is what the look actually is. Two things do:
 *
 * <ol>
 *   <li><b>A dark body.</b> The multiply is pushed to near-black navy, so the mob reads as a
 *       silhouette rather than as a recoloured mob.
 *   <li><b>Rim light.</b> Faces turning away from the camera light up bright blue and emit,
 *       which is the part that actually reads as "shadow". It needs the surface normal.
 * </ol>
 *
 * <h2>🔴 What is NOT possible here, and why</h2>
 * Per-pixel accents — bright eyes, highlights picked out of the texture — cannot be done from a
 * {@code VertexConsumer}. The vertex colour entity renderers pass is plain <b>white</b>; the
 * mob's colours live in the <b>texture</b>, sampled later in the shader, which multiplies the
 * two. So any colour written here is a uniform multiply over the whole texture, which is exactly
 * why earlier attempts read as "just tinted". Picking out parts of a texture needs a real
 * shader, or a hand-drawn texture per mob — neither of which survives contact with a modpack.
 *
 * <p>What does vary per vertex is the normal, so the rim is genuine. Minecraft entity models are
 * boxes, so a face's vertices share a normal and the rim lands <b>per face</b>: a faceted,
 * cel-shaded silhouette rather than a smooth gradient, which suits the look.
 *
 * <h2>⚠️ Why the vertex is buffered</h2>
 * The colour is written <b>before</b> the normal in the vertex stream, so the rim cannot be
 * computed when {@code color} is called. Each vertex's calls are therefore recorded and replayed
 * on {@code endVertex}, <b>in the order the renderer made them</b>, with only the colour
 * substituted. Replaying in the original order matters: a {@code RenderType}'s vertex format
 * fixes which elements appear and in what sequence, and inventing or reordering one corrupts the
 * buffer.
 */
public final class ShadowTintBuffer implements MultiBufferSource {

    // ---- palette ---------------------------------------------------------------------------

    /** The body. Very dark navy, near black. */
    private static final float DARK_R = 0.024F, DARK_G = 0.043F, DARK_B = 0.110F;

    /** The slightly lifted dark, so the body is not a flat void and still shows form. */
    private static final float MID_R = 0.055F, MID_G = 0.110F, MID_B = 0.255F;

    /** The accent. Light blue. */
    private static final float LIGHT_R = 0.541F, LIGHT_G = 0.800F, LIGHT_B = 1.000F;

    /** How much of the rim glows: higher is a thinner rim. */
    private static final float RIM_POWER = 3.0F;

    /** How strongly the rim pushes toward the accent colour, 0..1. */
    private static final float RIM_STRENGTH = 0.85F;

    private final MultiBufferSource delegate;

    public ShadowTintBuffer(MultiBufferSource delegate) {
        this.delegate = delegate;
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        return new Consumer(delegate.getBuffer(type));
    }

    /** The camera's facing, for the rim. Normals arrive already transformed into world space. */
    private static Vector3f viewVector() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null) return new Vector3f(0, 0, 1);
        return new Vector3f(mc.gameRenderer.getMainCamera().getLookVector()).normalize();
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    private static float smooth(float t) {
        return t * t * (3 - 2 * t);
    }

    private static final class Consumer implements VertexConsumer {
        /** The recorded calls of the vertex currently being built. */
        private enum Call { VERTEX, COLOR, UV, OVERLAY, UV2, NORMAL }

        private final VertexConsumer out;

        private final Call[] order = new Call[8];
        private int calls;

        private double vx, vy, vz;
        private int cr, cg, cb, ca;
        private float u, v;
        private int ou, ov;
        private int lu, lv;
        private float nx, ny, nz;
        private boolean hasNormal;

        Consumer(VertexConsumer out) {
            this.out = out;
        }

        private void record(Call call) {
            if (calls < order.length) order[calls++] = call;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            vx = x; vy = y; vz = z;
            record(Call.VERTEX);
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            cr = r; cg = g; cb = b; ca = a;
            record(Call.COLOR);
            return this;
        }

        @Override
        public VertexConsumer uv(float uu, float vv) {
            u = uu; v = vv;
            record(Call.UV);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int uu, int vv) {
            ou = uu; ov = vv;
            record(Call.OVERLAY);
            return this;
        }

        @Override
        public VertexConsumer uv2(int uu, int vv) {
            lu = uu; lv = vv;
            record(Call.UV2);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            nx = x; ny = y; nz = z;
            hasNormal = true;
            record(Call.NORMAL);
            return this;
        }

        @Override
        public void endVertex() {
            shade();
            for (int i = 0; i < calls; i++) {
                switch (order[i]) {
                    case VERTEX -> out.vertex(vx, vy, vz);
                    case COLOR -> out.color(cr, cg, cb, ca);
                    case UV -> out.uv(u, v);
                    case OVERLAY -> out.overlayCoords(ou, ov);
                    case UV2 -> out.uv2(lu, lv);
                    case NORMAL -> out.normal(nx, ny, nz);
                }
            }
            out.endVertex();
            calls = 0;
            hasNormal = false;
        }

        /**
         * Darken the body, and light the rim.
         *
         * <h3>⚠️ What the vertex colour actually is</h3>
         * It is <b>white</b>. Entity renderers pass {@code color(255, 255, 255, alpha)} and the
         * mob's real colours come from the <b>texture</b>, sampled later in the shader, which
         * multiplies the two. So there is nothing here to threshold: a per-pixel two-tone is
         * impossible from a {@code VertexConsumer}, and any single colour written here is a
         * uniform multiply over the whole texture — which is exactly why the old version read as
         * "basically tinted".
         *
         * <p>What <i>does</i> vary per vertex is the <b>normal</b>, so the rim is real. Minecraft
         * entity models are boxes, so every vertex of a face shares its normal and the rim lands
         * <b>per face</b>: faces turned away from the camera light up as a whole. That is a
         * faceted, cel-shaded silhouette rather than a smooth gradient — closer to the show than
         * a smooth rim would have been, as it happens.
         *
         * <p>The rim also drives the <b>lightmap</b> to full brightness, so lit faces glow in the
         * dark instead of merely being pale.
         */
        private void shade() {
            float rim = 0.0F;
            if (hasNormal) {
                Vector3f look = viewVector();
                float facing = Math.abs(nx * look.x() + ny * look.y() + nz * look.z());
                rim = (float) Math.pow(clamp01(1.0F - facing), RIM_POWER) * RIM_STRENGTH;
            }

            // A face square-on to the camera sits at MID, so the body is not a flat void and the
            // texture's own shading still shows through the multiply. Edge-on faces go to DARK
            // before the rim lifts them, which is what separates the silhouette from the body.
            float body = smooth(1.0F - rim);
            float r = DARK_R + (MID_R - DARK_R) * body;
            float g = DARK_G + (MID_G - DARK_G) * body;
            float b = DARK_B + (MID_B - DARK_B) * body;

            r += (LIGHT_R - r) * rim;
            g += (LIGHT_G - g) * rim;
            b += (LIGHT_B - b) * rim;

            cr = (int) (clamp01(r) * 255);
            cg = (int) (clamp01(g) * 255);
            cb = (int) (clamp01(b) * 255);
            // Alpha is left alone: the model's own transparency depends on it.

            // Lit faces emit, so the accent glows in a dark cave instead of merely being pale.
            // uv2 is the lightmap coordinate, 0..240 per channel (block light, sky light).
            if (rim > 0.15F) {
                int glow = (int) (rim * 240);
                lu = Math.max(lu, glow);
                lv = Math.max(lv, glow);
            }
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
            // Not recoloured: this sets a colour for vertices that never call color(), which the
            // entity renderers do not use. Passing it through unchanged is safer than guessing.
            out.defaultColor(r, g, b, a);
        }

        @Override
        public void unsetDefaultColor() {
            out.unsetDefaultColor();
        }
    }
}
