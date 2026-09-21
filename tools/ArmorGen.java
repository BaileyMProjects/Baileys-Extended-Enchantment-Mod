import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;

/**
 * Re-skins the corrupted armour to deepslate, and animates its corruption.
 *
 * <p>⚠️ <b>THIS TRANSFORMS BAILEY'S TEXTURES. IT DOES NOT REDRAW THEM — ON PURPOSE.</b>
 *
 * <p>On 2026-09-21 I redrew these armour icons from scratch three times and every attempt came
 * out worse than the originals: a helmet that read as a ring, a chestplate that read as a crate,
 * boots that read as two squares. They were reverted. The diagnosis was that "bland and boring"
 * meant the icons lacked DEPTH, not that the shapes were wrong — the silhouettes were always
 * good, and they are the part I cannot do.
 *
 * <p>So this reads the existing PNG and changes only what is safe to change:
 * <ul>
 *   <li><b>Silhouette</b> — untouched. Every transparent pixel stays transparent.
 *   <li><b>Shading structure</b> — untouched. Relative luminance is preserved, so wherever
 *       Bailey put a highlight there is still a highlight.
 *   <li><b>Hue</b> — remapped from teal to deepslate, matching the GUI panels exactly.
 *   <li><b>Corruption</b> — his brightest accents become sculk that PULSES.
 * </ul>
 *
 * <p>⚠️ This is not the lazy re-hue that got the scarlet crystal rejected. That one copied
 * another item's silhouette and recoloured it, producing two items that looked the same. This
 * keeps one item's own silhouette and changes its material, which is the opposite operation.
 */
public class ArmorGen {

    static final int FRAMES = 8;
    static final int FRAMETIME = 5;

    /**
     * The deepslate ramp, darkest to lightest — the same values as the GUI panels, so a player
     * wearing the set and looking at the anvil sees one material, not two.
     */
    static final int[] RAMP = {
            0xFF0E1014, 0xFF1A1E24, 0xFF23272E, 0xFF343A42,
            0xFF474E59, 0xFF5A626E, 0xFF737E8D
    };

    /** Sculk, dim to hot. The animated part. */
    static final int GLOW_DIM = 0xFF14565A;
    static final int GLOW_HOT = 0xFF7DF5E6;

    /**
     * Anything brighter than this in the source is treated as corruption rather than plate.
     *
     * ⚠️ Tuned to the actual source palette, not guessed: the armour's plate tones run from
     * luminance ~18 to ~101, and its accent colour #5FD6B4 sits at ~180. 140 separates them
     * cleanly with room on both sides.
     */
    static final int GLOW_LUMA = 140;

    public static void main(String[] args) throws Exception {
        String items = args[0];
        String armor = args[1];

        // Item icons live on an atlas, so .mcmeta animation works on them.
        for (String p : new String[]{"corrupted_helmet", "corrupted_chestplate",
                                     "corrupted_leggings", "corrupted_boots"}) {
            convert(items + "/" + p + ".png", items + "/" + p + ".png", true);
        }

        // ⚠️ WORN ARMOUR CANNOT ANIMATE FROM A .mcmeta. Layer textures are bound directly
        // through RenderType.armorCutoutNoCull and are not atlas sprites, so the animation
        // ticker never touches them. They get the recolour only; animating worn armour needs
        // a custom render layer cycling textures per tick. Not built.
        convert(armor + "/corrupted_layer_1.png", armor + "/corrupted_layer_1.png", false);
        convert(armor + "/corrupted_layer_2.png", armor + "/corrupted_layer_2.png", false);
    }

    static void convert(String in, String out, boolean animate) throws Exception {
        BufferedImage src = ImageIO.read(new File(in));
        int w = src.getWidth();
        // Re-running the generator must not stack frames on frames, so only the FIRST frame of
        // an already-animated source is read back in.
        int h = Math.min(src.getHeight(), animate ? w : src.getHeight());

        // Pass one: find the plate's luminance range so the ramp can use its full contrast.
        // A fixed mapping would leave everything in the bottom third, because the source is
        // uniformly dark.
        int lo = 255, hi = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = src.getRGB(x, y);
                if ((argb >>> 24) == 0) continue;
                int l = luma(argb);
                if (l >= GLOW_LUMA) continue;
                lo = Math.min(lo, l);
                hi = Math.max(hi, l);
            }
        }
        if (hi <= lo) { lo = 0; hi = 255; }

        int frames = animate ? FRAMES : 1;
        BufferedImage dst = new BufferedImage(w, h * frames, BufferedImage.TYPE_INT_ARGB);

        for (int f = 0; f < frames; f++) {
            double t = frames == 1 ? 0.55 : (1 - Math.cos(2 * Math.PI * f / frames)) / 2.0;
            int glow = lerp(GLOW_DIM, GLOW_HOT, t);

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int argb = src.getRGB(x, y);
                    if ((argb >>> 24) == 0) continue;           // silhouette preserved exactly

                    int l = luma(argb);
                    if (l >= GLOW_LUMA) {
                        dst.setRGB(x, f * h + y, glow);          // his accents become the pulse
                    } else {
                        double n = (double) (l - lo) / (hi - lo);
                        int idx = (int) Math.round(n * (RAMP.length - 1));
                        dst.setRGB(x, f * h + y, RAMP[Math.max(0, Math.min(RAMP.length - 1, idx))]);
                    }
                }
            }
        }

        ImageIO.write(dst, "png", new File(out));
        if (animate) {
            try (FileWriter fw = new FileWriter(out + ".mcmeta")) {
                fw.write("{\n  \"animation\": {\n    \"frametime\": " + FRAMETIME + "\n  }\n}\n");
            }
        }
        System.out.println("wrote " + out + "  " + w + "x" + (h * frames)
                + (animate ? "  (+ .mcmeta)" : "  (static -- worn layers cannot animate)"));
    }

    /** Perceptual luminance. Straight averaging would wash out the teal's green channel. */
    static int luma(int argb) {
        int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        return (int) Math.round(0.2126 * r + 0.7152 * g + 0.0722 * b);
    }

    static int lerp(int a, int b, double t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000
                | ((int) Math.round(ar + (br - ar) * t) << 16)
                | ((int) Math.round(ag + (bg - ag) * t) << 8)
                | (int) Math.round(ab + (bb - ab) * t);
    }
}
