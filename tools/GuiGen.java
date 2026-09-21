import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/**
 * The three container GUI sheets.
 *
 * Construction is vanilla's: a flat base, a 1px light bevel top/left, a 1px dark bevel
 * bottom/right, and slot wells with that bevel INVERTED so they read as recessed. That
 * structure is what makes a panel feel like Minecraft. Only the palette is ours — deepslate
 * instead of #C6C6C6 — because these are ancient corrupted blocks, and because the screens
 * already draw their text white-on-dark.
 *
 * Every slot well is placed from the coordinates in the Menu classes (slot position minus 1,
 * 18x18). If a slot ever moves, it moves here too.
 */
public class GuiGen {

    static final int W = 256, H = 256;      // sheet
    static final int PW = 176, PH = 166;    // panel

    // ---- palette ---------------------------------------------------------------------
    static final int OUTLINE   = 0xFF0E1014;
    static final int BEVEL_HI  = 0xFF737E8D;
    static final int BEVEL_LO  = 0xFF23272E;
    static final int BASE      = 0xFF474E59;
    static final int BASE_HI   = 0xFF4E5661;
    static final int BASE_LO   = 0xFF40464F;

    static final int WELL_IN   = 0xFF272C33;
    static final int WELL_DK   = 0xFF14171B;
    static final int WELL_LT   = 0xFF737E8D;

    static final int SCULK_DK  = 0xFF123E3F;
    static final int SCULK_MD  = 0xFF1A6664;
    static final int SCULK_LT  = 0xFF2A9A90;
    static final int SCULK_GLO = 0xFF4FE3D2;
    static final int SCULK_HOT = 0xFF7DF5E6;

    static BufferedImage img;

    /**
     * Drawing clip. Only 0,0..PW,PH is ever blitted to the screen, so corruption allowed to
     * wander past the panel edge would render as a patch sliced off mid-growth. Clipping it
     * to the panel interior makes it stop at the frame instead.
     */
    static int clipX = 0, clipY = 0, clipW = W, clipH = H;

    static void clip(int x, int y, int w, int h) { clipX = x; clipY = y; clipW = w; clipH = h; }
    static void noClip() { clip(0, 0, W, H); }

    public static void main(String[] args) throws Exception {
        String dir = args[0];
        table(dir + "/ancient_enchanting_table.png");
        forge(dir + "/ancient_anvil_forge.png");
        ultimate(dir + "/ancient_anvil_ultimate.png");
    }

    // ---- primitives ------------------------------------------------------------------
    static void px(int x, int y, int c) {
        if (x < clipX || y < clipY || x >= clipX + clipW || y >= clipY + clipH) return;
        if (x < 0 || y < 0 || x >= W || y >= H) return;
        if ((c >>> 24) == 0xFF) { img.setRGB(x, y, c); return; }
        int d = img.getRGB(x, y);
        int a = c >>> 24;
        int r = (((c >> 16) & 255) * a + ((d >> 16) & 255) * (255 - a)) / 255;
        int g = (((c >> 8) & 255) * a + ((d >> 8) & 255) * (255 - a)) / 255;
        int b = ((c & 255) * a + (d & 255) * (255 - a)) / 255;
        img.setRGB(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
    }

    static void rect(int x, int y, int w, int h, int c) {
        for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) px(x + i, y + j, c);
    }

    /** Raised panel: outline, light bevel top/left, dark bevel bottom/right. */
    static void panel(int x, int y, int w, int h) {
        rect(x, y, w, h, BASE);
        rect(x, y, w, 1, OUTLINE);
        rect(x, y + h - 1, w, 1, OUTLINE);
        rect(x, y, 1, h, OUTLINE);
        rect(x + w - 1, y, 1, h, OUTLINE);
        rect(x + 1, y + 1, w - 2, 1, BEVEL_HI);
        rect(x + 1, y + 1, 1, h - 2, BEVEL_HI);
        rect(x + 1, y + h - 2, w - 2, 1, BEVEL_LO);
        rect(x + w - 2, y + 1, 1, h - 2, BEVEL_LO);
    }

    /** Recessed area: bevel inverted. Used for slot wells and sunken fields. */
    static void recess(int x, int y, int w, int h) {
        rect(x, y, w, h, WELL_IN);
        rect(x, y, w, 1, WELL_DK);
        rect(x, y, 1, h, WELL_DK);
        rect(x, y + h - 1, w, 1, WELL_LT);
        rect(x + w - 1, y, 1, h, WELL_LT);
        px(x, y + h - 1, WELL_DK);
        px(x + w - 1, y, WELL_DK);
    }

    /** A standard 18x18 slot, given the SLOT ITEM position from the Menu class. */
    static void slot(int itemX, int itemY) { recess(itemX - 1, itemY - 1, 18, 18); }

    static void arrow(int x, int y) {
        int sh = BEVEL_LO, lt = BEVEL_HI;
        rect(x, y + 3, 12, 5, sh);
        rect(x + 1, y + 4, 10, 3, lt);
        for (int i = 0; i < 6; i++) {
            rect(x + 10 + i / 2, y + i, 1, 1, sh);
            rect(x + 10 + i / 2, y + 10 - i, 1, 1, sh);
        }
        rect(x + 11, y + 2, 1, 7, sh);
        rect(x + 12, y + 3, 1, 5, sh);
        rect(x + 13, y + 4, 1, 3, sh);
        rect(x + 14, y + 5, 1, 1, sh);
        rect(x + 11, y + 4, 1, 3, lt);
        rect(x + 12, y + 5, 1, 1, lt);
    }

    // ---- corruption ------------------------------------------------------------------
    /**
     * Sculk creeping in from a corner. Grown outward from a seed point so it reads as
     * organic rather than as a stamped decal, and kept to the panel margins so it never
     * fights a slot for attention.
     */
    static void sculkPatch(int cx, int cy, int spread, int density, long seed) {
        Random r = new Random(seed);
        for (int i = 0; i < density; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = Math.pow(r.nextDouble(), 0.6) * spread;
            int x = cx + (int) (Math.cos(a) * d);
            int y = cy + (int) (Math.sin(a) * d * 0.8);
            double t = d / spread;
            int c = t < 0.45 ? SCULK_MD : t < 0.75 ? SCULK_DK : (r.nextInt(3) == 0 ? SCULK_DK : -1);
            if (c == -1) continue;
            px(x, y, c);
            if (r.nextInt(4) == 0) px(x + 1, y, c);
            if (r.nextInt(5) == 0) px(x, y + 1, c);
        }
        // a few lit nodes near the dense core
        for (int i = 0; i < density / 14; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = r.nextDouble() * spread * 0.45;
            int x = cx + (int) (Math.cos(a) * d), y = cy + (int) (Math.sin(a) * d * 0.8);
            px(x, y, r.nextInt(3) == 0 ? SCULK_HOT : SCULK_GLO);
            px(x + 1, y, SCULK_LT);
            px(x, y + 1, SCULK_LT);
        }
    }

    /**
     * Corruption grown around the whole perimeter rather than blobbed into two corners.
     *
     * ⚠️ IT MUST NOT CROSS INTO CONTENT. Bailey, 2026-09-21: "the corruption on the left needs a
     * change in position as it overlaps with stuff on the screen ... i think it should spread
     * across the menu a bit more." Both at once, so the sculk now runs the full border inside a
     * strict margin instead of sitting in two clumps that collided with the hotbar and with the
     * Secret warning text.
     *
     * The safe margins are dictated by the layout, not chosen:
     *   left   x1..5    slot wells start at x=7
     *   right  x170..174 the last inventory well ends at x=168
     *   top    y1..5    the title sits at y=6
     *   bottom y160..164 the hotbar wells end at y=159
     *
     * Depth varies along each edge from summed sines, so the growth breathes in and out
     * instead of reading as a ruled stripe.
     */
    static void sculkBorder(long seed) {
        Random r = new Random(seed);
        edge(seed, 1, PW - 2, 5, true, false, r);    // top
        edge(seed + 31, 1, PW - 2, 4, true, true, r);  // bottom
        edge(seed + 61, 1, PH - 2, 5, false, false, r); // left
        edge(seed + 91, 1, PH - 2, 4, false, true, r);  // right
    }

    private static void edge(long seed, int from, int to, int maxDepth,
                             boolean horizontal, boolean far, Random r) {
        double p1 = (seed % 97) * 0.13, p2 = (seed % 53) * 0.31;
        for (int p = from; p < to; p++) {
            double n = Math.sin(p * 0.11 + p1) * 0.5 + Math.sin(p * 0.043 + p2) * 0.5;
            // Power curve, not linear: the growth should be absent in places and thick in
            // others. A linear map averages mid-depth everywhere and reads as a ruled stripe.
            int depth = (int) Math.round(Math.pow(n * 0.5 + 0.5, 1.9) * (maxDepth + 1));
            if (depth > maxDepth) depth = maxDepth;
            if (depth <= 0) continue;
            for (int d = 0; d < depth; d++) {
                int a = horizontal ? p : (far ? PW - 2 - d : 1 + d);
                int b = horizontal ? (far ? PH - 2 - d : 1 + d) : p;
                int c = d == 0 ? SCULK_MD : d < depth - 1 ? SCULK_DK : (r.nextInt(2) == 0 ? SCULK_DK : -1);
                if (c != -1) px(a, b, c);
            }
            // lit nodes only where the growth is thickest, so the glow follows the mass
            if (depth >= maxDepth - 1 && r.nextInt(7) == 0) {
                int a = horizontal ? p : (far ? PW - 3 : 2);
                int b = horizontal ? (far ? PH - 3 : 2) : p;
                px(a, b, r.nextInt(3) == 0 ? SCULK_HOT : SCULK_GLO);
            }
        }
    }

    /** A vein running along an edge, thinning as it goes. */
    static void vein(int x, int y, int len, int dx, int dy, long seed) {
        Random r = new Random(seed);
        int cx = x, cy = y;
        for (int i = 0; i < len; i++) {
            double t = 1.0 - (double) i / len;
            px(cx, cy, t > 0.5 ? SCULK_MD : SCULK_DK);
            if (r.nextInt(6) == 0) px(cx, cy + 1, SCULK_DK);
            if (r.nextInt(14) == 0) px(cx, cy - 1, SCULK_LT);
            cx += dx; cy += dy;
            if (r.nextInt(3) == 0) cy += r.nextInt(3) - 1;
        }
    }

    /**
     * Stone grain. Deliberately sparse and clustered into short horizontal runs — single
     * scattered pixels at high density read as dither noise, not as rock.
     */
    static void mottle(int x, int y, int w, int h, long seed) {
        Random r = new Random(seed);
        for (int i = 0; i < (w * h) / 55; i++) {
            int sx = x + r.nextInt(w), sy = y + r.nextInt(h);
            int c = r.nextBoolean() ? BASE_HI : BASE_LO;
            int run = 1 + r.nextInt(3);
            for (int k = 0; k < run; k++) px(sx + k, sy, c);
        }
    }

    // ---- the shared shell ------------------------------------------------------------
    static void begin() {
        img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        rect(0, 0, W, H, 0x00000000);
    }

    static void shell(long seed) {
        panel(0, 0, PW, PH);
        mottle(2, 2, PW - 4, PH - 4, seed);

        clip(1, 1, PW - 2, PH - 2);
        // Corruption. It enters from the corners and creeps along the edges, never across
        // the middle band where the slots and labels live — the panel has to stay readable,
        // so the sculk frames the work area rather than competing with it.
        sculkBorder(seed);
        noClip();

        // the divider above the player inventory, so the two halves read as separate
        rect(7, 69, PW - 14, 1, BEVEL_LO);
        rect(7, 70, PW - 14, 1, BEVEL_HI);

        playerInventory();
    }

    static void playerInventory() {
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                slot(8 + col * 18, 84 + row * 18);
        for (int col = 0; col < 9; col++) slot(8 + col * 18, 142);
    }

    static void save(String path) throws Exception {
        ImageIO.write(img, "png", new File(path));
        System.out.println("wrote " + path);
    }

    // ---- the three screens -----------------------------------------------------------

    /** Slots (15,47) item and (35,47) lapis-equivalent; three offer rows at x60 y14/33/52. */
    static void table(String path) throws Exception {
        begin();
        shell(1001);
        slot(15, 47);
        slot(35, 47);
        // the offer column sits behind code-drawn rows; give it a frame so it reads as sunken
        recess(58, 12, 112, 61);
        save(path);
    }

    /** Forge tab: inputs (27,47) and (76,47), result (134,47), name field 59..169 x 20..36. */
    static void forge(String path) throws Exception {
        begin();
        shell(2002);
        recess(58, 19, 112, 18);      // rename field
        slot(27, 47);
        slot(76, 47);
        arrow(103, 47);
        slot(134, 47);
        save(path);
    }

    /** Ultimate tab: (8,48) template, (26,48) gear, (44,48) crystal, result (98,48). */
    static void ultimate(String path) throws Exception {
        begin();
        shell(3003);
        slot(8, 48);
        slot(26, 48);
        slot(44, 48);
        arrow(70, 48);
        slot(98, 48);
        save(path);
    }
}
