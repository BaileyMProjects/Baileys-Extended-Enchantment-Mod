import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;

/**
 * The black ribbon that coils around a sword carrying Resonance.
 *
 * <p>⚠️ <b>This is a TEXTURE, not a particle effect, and that is the whole point.</b>
 * Bailey, 2026-09-21: <i>"no I do not want an actual particle. I mean a drawn on aura on the
 * icon that translates to the hand because its apart of the icon. Same way the reflection
 * animation on the scarlet crystal translates to the hand."</i>
 *
 * <p>He is right, and it is the better design. A drawn overlay appears in the inventory, in the
 * hand, on the ground, in item frames and in another player's hand — everywhere the item is
 * drawn — with no render code at all. The particle version I built first only existed in the
 * world and needed a tick handler to do it.
 *
 * <p>It is applied as <b>layer1</b> of a model whose layer0 is the vanilla sword texture, so no
 * vanilla art is copied into this mod — only referenced.
 *
 * <h2>The shape</h2>
 * A vanilla sword icon runs on the diagonal, hilt at bottom-left, tip at top-right. The ribbon
 * is a sine wave travelling along that axis, offset perpendicular to it — which is what a helix
 * around the blade looks like once flattened to two dimensions. Two strands in antiphase, so
 * one crosses in front while the other is behind, and the wave slides along the blade each
 * frame so it reads as circulating rather than wobbling.
 */
public class AuraGen {

    static final int SIZE = 16;
    static final int FRAMES = 12;
    static final int FRAMETIME = 2;

    /** Hilt and tip, in texture pixels. Matches where a vanilla sword actually sits. */
    static final double AX = 2.5, AY = 13.5;
    static final double BX = 13.5, BY = 2.5;

    /** How far the ribbon swings off the blade axis at its widest. */
    static final double AMPLITUDE = 4.3;

    /** Coils along the blade. Two and a half reads as a wrap; more reads as a scribble. */
    // ⚠️ 1.5, not 2.5. At 16px a tighter coil bunches in the middle and reads as a scribble
    // rather than as something wrapped around a blade — there is simply not enough room.
    static final double COILS = 1.5;

    public static void main(String[] args) throws Exception {
        String out = args[0] + "/resonance_aura.png";
        BufferedImage img = new BufferedImage(SIZE, SIZE * FRAMES, BufferedImage.TYPE_INT_ARGB);

        double dx = BX - AX, dy = BY - AY;
        double len = Math.sqrt(dx * dx + dy * dy);
        double ux = dx / len, uy = dy / len;      // along the blade
        double nx = -uy, ny = ux;                  // perpendicular to it

        for (int f = 0; f < FRAMES; f++) {
            double phase = (Math.PI * 2 / FRAMES) * f;

            for (int strand = 0; strand < 2; strand++) {
                double strandPhase = phase + strand * Math.PI;

                // Walked in small steps rather than per-pixel so the curve stays connected;
                // a coarse step leaves a dotted line, which reads as particles, not a ribbon.
                for (double s = 0.02; s <= 0.98; s += 0.004) {
                    double wave = Math.sin(s * Math.PI * 2 * COILS + strandPhase);

                    // Taper at both ends so the ribbon fades out rather than stopping dead at
                    // the hilt and the tip.
                    // Square-rooted so the ribbon stays wide most of the way and only pinches
                    // right at the hilt and the tip. A plain sine tapers far too early and
                    // leaves the effect huddled in the centre.
                    double taper = Math.sqrt(Math.sin(s * Math.PI));
                    double off = wave * AMPLITUDE * taper;

                    double px = AX + ux * (len * s) + nx * off;
                    double py = AY + uy * (len * s) + ny * off;

                    // The strand is "behind" the blade on half of each coil. Behind reads as
                    // fainter, and that alternation is what sells it as going AROUND the sword
                    // rather than lying flat on top of it.
                    boolean front = wave < 0;
                    int alpha = (int) ((front ? 240 : 95) * taper);
                    if (alpha < 12) continue;

                    plot(img, px, py, f * SIZE, alpha, front);
                }
            }
        }

        ImageIO.write(img, "png", new File(out));
        try (FileWriter fw = new FileWriter(out + ".mcmeta")) {
            fw.write("{\n  \"animation\": {\n    \"frametime\": " + FRAMETIME + "\n  }\n}\n");
        }
        System.out.println("wrote " + out + "  " + SIZE + "x" + (SIZE * FRAMES) + "  (+ .mcmeta)");
    }

    /**
     * ⚠️ Keeps the STRONGEST pixel rather than blending, because the curve is walked in
     * overlapping steps — averaging would smear the ribbon into a grey haze.
     */
    static void plot(BufferedImage img, double px, double py, int rowOffset, int alpha, boolean front) {
        int x = (int) Math.floor(px), y = (int) Math.floor(py);
        if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) return;

        // Near-black with the faintest red bias, so it belongs to the Secret tier without
        // reading as a red aura. Bailey asked for black.
        int rgb = front ? 0x0B0407 : 0x140A0E;
        int argb = (alpha << 24) | rgb;

        int existing = img.getRGB(x, rowOffset + y);
        if ((existing >>> 24) >= alpha) return;
        img.setRGB(x, rowOffset + y, argb);
    }
}
