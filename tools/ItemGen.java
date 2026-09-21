import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The crystal item textures and their empty-slot ghosts.
 *
 * Shapes are authored as explicit character maps — one glyph per pixel, one colour per glyph.
 * NOT procedural, and specifically not a re-hue of another texture: the scarlet crystal was
 * rejected on 2026-09-21 precisely because it was the dark crystal's silhouette in red.
 *
 * The two crystals now have deliberately different SHAPES so they are never confusable:
 *   dark crystal    a squat, wide, faceted gem
 *   scarlet crystal a tall, spiky, three-shard cluster
 *
 * Glyph 'g' is a GLOW pixel: its colour is interpolated per animation frame between a dim and
 * a hot value, so the crystal pulses from within. Everything else is constant across frames.
 */
public class ItemGen {

    static final int FRAMES = 8;
    static final int FRAMETIME = 4;

    // ---- palettes --------------------------------------------------------------------
    // Corrupted/sculk: the blue-cyan lighting the Deep Dark set runs on.
    static final Map<Character, Integer> DARK = pal(
            '#', 0xFF06181A, 'a', 0xFF0A272A, 'b', 0xFF0D3236, 'c', 0xFF14565A,
            'd', 0xFF1E8A88, 'e', 0xFF2FB9AE, 'f', 0xFF6FEFE0);
    static final int DARK_GLOW_DIM = 0xFF1E8A88, DARK_GLOW_HOT = 0xFFAFFFF6;

    // Scarlet stays RED, deliberately. The Secret tier is red and not blue by design — the
    // corrupted family gets the cyan lighting, this one keeps its own identity.
    static final Map<Character, Integer> SCARLET = pal(
            '#', 0xFF2A0610, 'a', 0xFF5E0F1C, 'b', 0xFF8E1526, 'c', 0xFFC01F33,
            'd', 0xFFE84257, 'e', 0xFFFF7D8C, 'f', 0xFFFFD9DE);
    static final int SCAR_GLOW_DIM = 0xFFC01F33, SCAR_GLOW_HOT = 0xFFFFE8EC;

    // ---- shapes ----------------------------------------------------------------------
    /** Squat, wide, brilliant-cut gem. Nothing like a shard cluster at a glance. */
    static final String[] DARK_CRYSTAL = {
            "................",
            "................",
            "......####......",
            "....##bccb##....",
            "...#bcdeedcb#...",
            "..#bcdeffedcb#..",
            "..#bcdeggedcb#..",
            "..#bcdeggedcb#..",
            "...#bcdeedcb#...",
            "....#bcddcb#....",
            ".....#bccb#.....",
            "......#bb#......",
            ".......##.......",
            "................",
            "................",
            "................"
    };

    /** Tall three-shard cluster, tapered, lit from upper-left. */
    static final String[] SCARLET_CRYSTAL = {
            "................",
            ".........#......",
            "........#e#.....",
            "........#dg#....",
            ".......#cdg#....",
            ".......#cdgf#...",
            ".......#bcge#...",
            "....#.#bbcge#...",
            "...#g##abcge#...",
            "...#cg#abcge#...",
            "..#bcg#abcge#...",
            "..#abc#aabcg#...",
            "..#abc##aabc#...",
            "...#ab#.#ab#....",
            "....###..###....",
            "................"
    };

    // Shared with the GUI panels so the armour and the shelf read as the same material as the
    // menus. Bailey, 2026-09-21: "I want the armor to have a closer look to what you made the
    // UI's look like."
    static final Map<Character, Integer> CORRUPT = pal(
            '#', 0xFF0E1014,   // outline, same as the panel outline
            'a', 0xFF23272E,   // deepest plate
            'b', 0xFF343A42,   // shadow plate
            'c', 0xFF474E59,   // base plate  — the GUI panel colour exactly
            'd', 0xFF5A626E,   // lit plate
            'e', 0xFF737E8D,   // highlight   — the GUI bevel colour exactly
            'k', 0xFF0D3236,   // sculk, deep
            'K', 0xFF14565A,   // sculk, mid
            'L', 0xFF1E8A88,   // sculk, lit
            'x', 0xFF14171B,   // gap / recess
            'p', 0xFF3E444D,   // plank
            'r', 0xFF3A2B4A,   // book spine, violet
            's', 0xFF1E4A46,   // book spine, teal
            't', 0xFF4A3527,   // book spine, tan
            'u', 0xFF2E3A52,   // book spine, slate blue
            'W', 0xFF3D434B,   // shelf frame, lit
            'w', 0xFF2B2F35);  // shelf frame, shadow
    static final int CORRUPT_GLOW_DIM = 0xFF14565A, CORRUPT_GLOW_HOT = 0xFF7DF5E6;

    /**
     * The corrupted bookshelf — the only thing that powers the Ancient Enchanting Table, and
     * previously a flat brown-grey block. Bailey: "the bookshelves look bad and need a complete
     * redesign."
     *
     * Vanilla's bookshelf silhouette is kept (frame top and bottom, spines between, a plank
     * across the middle) so it still reads instantly as a bookshelf — then sculk eats into it
     * from the top-right and bottom-left, with the glow sitting in the thickest growth.
     */
    static final String[] BOOKSHELF = {
            "WWWWWWWWWWWKKggK",
            "wwwwwwwwwwKKkkKK",
            "wrrxssxttxuKkkKx",
            "wrrxssxttxuuxkKx",
            "wrrxssxttxuuxrKx",
            "wrrxssxttxuuxrrx",
            "wrrxssxttxuuxrrx",
            "wppppppppppppppw",
            "wsxttxuurxssxttw",
            "wsxttxuurxssxttw",
            "KKxttxuurxssxttw",
            "gKKxtxuurxssxttw",
            "KggKxxuurxssxttw",
            "kKgKKxuurxssxttw",
            "wkKKkwwwwwwwwwww",
            "WwkkwWWWWWWWWWWW"
    };

    // ⚠️ THE ARMOUR ICONS ARE NOT GENERATED, AND THAT IS A DELIBERATE RETREAT.
    //
    // Three attempts on 2026-09-21 all came out WORSE than the hand-drawn originals: a helmet
    // that read as a ring, then as an arch; a chestplate that read as an hourglass, then as a
    // crate; boots that read as a bracket, then as two squares. Reverted.
    //
    // The misdiagnosis is the useful part. "Bland and boring" meant the icons lacked DEPTH —
    // they are near-flat fills with almost no value range. It did NOT mean the shapes were
    // wrong. The originals have good silhouettes and read instantly as helmet, chestplate,
    // boots. I redrew the silhouettes, which was the one part that did not need changing.
    //
    // If this is picked up again: KEEP the original shapes, and only add shading, sculk
    // detail and glow pixels on top of them. Do not re-cut the outlines.

    public static void main(String[] args) throws Exception {
        String items = args[0];      // .../textures/item
        String slots = args[1];      // .../textures/gui/sprites/container/slot
        String blocks = args.length > 2 ? args[2] : null;

        animated(items + "/dark_crystal.png", DARK_CRYSTAL, DARK, DARK_GLOW_DIM, DARK_GLOW_HOT);
        animated(items + "/scarlet_crystal.png", SCARLET_CRYSTAL, SCARLET, SCAR_GLOW_DIM, SCAR_GLOW_HOT);

        // Ghosts stay STATIC. They are placeholders behind an empty slot; a pulsing hint
        // would pull the eye to the one thing in the menu that is not there yet.
        ghost(items + "/empty_slot_dark_crystal.png", DARK_CRYSTAL);
        ghost(items + "/empty_slot_scarlet_crystal.png", SCARLET_CRYSTAL);
        ghost(slots + "/dark_crystal.png", DARK_CRYSTAL);

        if (blocks != null) {
            animated(blocks + "/corrupted_bookshelf.png", BOOKSHELF, CORRUPT, CORRUPT_GLOW_DIM, CORRUPT_GLOW_HOT);
        }
    }

    // ---- writers ---------------------------------------------------------------------

    /** A vertical strip of FRAMES frames plus the .mcmeta Minecraft needs to animate it. */
    static void animated(String path, String[] map, Map<Character, Integer> pal,
                         int glowDim, int glowHot) throws Exception {
        int w = map[0].length(), h = map.length;
        BufferedImage img = new BufferedImage(w, h * FRAMES, BufferedImage.TYPE_INT_ARGB);

        for (int f = 0; f < FRAMES; f++) {
            // Smooth round trip, so frame 7 leads back into frame 0 with no visible jump.
            double t = (1 - Math.cos(2 * Math.PI * f / FRAMES)) / 2.0;
            int glow = lerp(glowDim, glowHot, t);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    char c = map[y].charAt(x);
                    if (c == '.') continue;
                    int argb = c == 'g' ? glow : pal.get(c);
                    img.setRGB(x, f * h + y, argb);
                }
            }
        }
        ImageIO.write(img, "png", new File(path));
        try (FileWriter fw = new FileWriter(path + ".mcmeta")) {
            fw.write("{\n  \"animation\": {\n    \"frametime\": " + FRAMETIME + "\n  }\n}\n");
        }
        System.out.println("wrote " + path + "  " + w + "x" + (h * FRAMES) + "  (+ .mcmeta)");
    }

    /** Flat, desaturated silhouette of the same shape — the slot hint. */
    static void ghost(String path, String[] map) throws Exception {
        int w = map[0].length(), h = map.length;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                char c = map[y].charAt(x);
                if (c == '.') continue;
                // Outline darker than the body so the shape still reads without colour.
                img.setRGB(x, y, c == '#' ? 0xFF1B1F24 : 0xFF3A424C);
            }
        }
        ImageIO.write(img, "png", new File(path));
        System.out.println("wrote " + path + "  ghost");
    }

    // ---- helpers ---------------------------------------------------------------------
    static int lerp(int a, int b, double t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        int r = (int) Math.round(ar + (br - ar) * t);
        int g = (int) Math.round(ag + (bg - ag) * t);
        int bl = (int) Math.round(ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    static Map<Character, Integer> pal(Object... kv) {
        LinkedHashMap<Character, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((Character) kv[i], (Integer) kv[i + 1]);
        return m;
    }
}
