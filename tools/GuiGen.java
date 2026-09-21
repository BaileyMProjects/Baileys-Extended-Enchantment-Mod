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

    /**
     * Occupancy mask. Bailey, 2026-09-21: "go closer to what you initially had where the
     * corruption spread across the screen but be careful of the placement. If thats hard maybe
     * just compensate by having a lot of corruption."
     *
     * The first version spread nicely and grew over the hotbar and over the Secret warning
     * text. The second version could not overlap anything but was confined to the border.
     * A mask gets both: every slot, field and text band marks itself forbidden, and the sculk
     * is then scattered across the WHOLE panel at high density with placement checked per
     * pixel. Overlap becomes impossible rather than avoided by hand, so density is free.
     */
    static boolean[] blocked = new boolean[W * H];
    static boolean maskOn = false;

    static void forbid(int x, int y, int w, int h) {
        for (int j = y - 1; j < y + h + 1; j++)
            for (int i = x - 1; i < x + w + 1; i++)
                if (i >= 0 && j >= 0 && i < W && j < H) blocked[j * W + i] = true;
    }

    static void clearMask() { blocked = new boolean[W * H]; }

    public static void main(String[] args) throws Exception {
        String dir = args[0];
        table(dir + "/ancient_enchanting_table.png");
        forge(dir + "/ancient_anvil_forge.png");
        ultimate(dir + "/ancient_anvil_ultimate.png");
    }

    // ---- primitives ------------------------------------------------------------------
    static void px(int x, int y, int c) {
        if (maskOn && x >= 0 && y >= 0 && x < W && y < H && blocked[y * W + x]) return;
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
        forbid(x, y, w, h);
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

    /**
     * Sculk scattered over the whole panel, not just its border.
     *
     * Every pixel is mask-checked, so a blob may sit right up against a slot and stop dead at
     * its edge — which is what growth around an obstacle actually looks like, and is why the
     * density can be this high without hurting readability.
     */
    static void sculkSpread(long seed) {
        Random r = new Random(seed * 7919L);
        for (int i = 0; i < 90; i++) {
            int cx = 2 + r.nextInt(PW - 4);
            int cy = 2 + r.nextInt(PH - 4);
            // Bias toward the edges: the middle of the panel is where the eye works, so mass
            // collects at the rim and only tendrils reach inward.
            double edgeness = Math.min(Math.min(cx, PW - cx), Math.min(cy, PH - cy)) / 40.0;
            int spread = 5 + r.nextInt(9);
            int density = (int) ((70 + r.nextInt(110)) * (1.3 - Math.min(edgeness, 1.0)));
            sculkPatch(cx, cy, spread, density, seed + 500 + i);
        }
        // Long veins wandering across the panel, broken wherever they meet content.
        for (int i = 0; i < 14; i++) {
            int x = 2 + r.nextInt(PW - 4), y = 2 + r.nextInt(PH - 4);
            boolean horiz = r.nextBoolean();
            vein(x, y, 20 + r.nextInt(40), horiz ? 1 : 0, horiz ? 0 : 1, seed + 900 + i);
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
        clearMask();
        maskOn = false;
    }

    /**
     * Text bands the screens draw over the panel. These are not visible in the texture, so
     * nothing else records them — but sculk grown underneath a label makes the label
     * unreadable, which is the exact complaint that started this.
     */
    static void forbidCommonText() {
        // ⚠️ Reserve what the text ACTUALLY covers, not the whole row. Blocking full-width
        // bands walled off the entire top half of the panel and left the corruption nowhere
        // to grow but the frame — which is the problem this was meant to solve.
        forbid(6, 4, 122, 13);        // title, y6
        forbid(154, 4, 18, 14);       // the "?" info icon, top-right
        forbid(6, 16, 134, 13);       // hint / cost row, y18
        forbid(6, 70, 108, 12);       // "Inventory" label, y72
    }

    static void shell(long seed) {
        panel(0, 0, PW, PH);
        mottle(2, 2, PW - 4, PH - 4, seed);

        // Corruption. The mask guarantees it cannot reach a slot, a field or a label, so it
        // is scattered across the WHOLE panel at high density rather than hugging the frame.
        clip(1, 1, PW - 2, PH - 2);
        maskOn = true;
        sculkBorder(seed);
        sculkSpread(seed);
        maskOn = false;
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

    /** Reserve a slot's footprint without drawing it yet. */
    static void forbidSlot(int itemX, int itemY) { forbid(itemX - 1, itemY - 1, 18, 18); }

    // ⚠️ ORDER MATTERS IN ALL THREE OF THESE, AND IT IS NOT OBVIOUS.
    //
    //   1. reserve every slot, field and text band with forbid()
    //   2. shell() — paints the panel, then grows corruption against that mask
    //   3. draw the slots and the arrow for real
    //
    // Drawing the slots in step 1 does not work: shell() paints the panel background over the
    // whole area and erases them. Reserving is not drawing.

    /** Slots (15,47) item and (35,47) shards; three offer rows at x60, y14/33/52. */
    static void table(String path) throws Exception {
        begin();
        forbidSlot(15, 47);
        forbidSlot(35, 47);
        forbid(58, 12, 112, 61);      // the offer column
        forbidCommonText();

        shell(1001);

        slot(15, 47);
        slot(35, 47);
        recess(58, 12, 112, 61);
        save(path);
    }

    /** Forge tab: inputs (27,47) and (76,47), result (134,47), rename field 59..169 x 20..36. */
    static void forge(String path) throws Exception {
        begin();
        forbidSlot(27, 47);
        forbidSlot(76, 47);
        forbidSlot(134, 47);
        forbid(58, 19, 112, 18);      // rename field
        forbid(100, 45, 22, 15);      // the arrow
        forbid(94, 66, 78, 15);       // the forge cost readout, drawn at y69
        forbidCommonText();

        shell(2002);

        recess(58, 19, 112, 18);
        slot(27, 47);
        slot(76, 47);
        arrow(103, 47);
        slot(134, 47);
        save(path);
    }

    /** Ultimate tab: (8,48) template, (26,48) gear, (44,48) crystal, result (98,48). */
    static void ultimate(String path) throws Exception {
        begin();
        forbidSlot(8, 48);
        forbidSlot(26, 48);
        forbidSlot(44, 48);
        forbidSlot(98, 48);
        forbid(67, 46, 22, 15);       // the arrow
        forbid(6, 27, PW - 12, 20);   // the whole Secret control block, y29..45
        forbid(112, 46, 48, 20);      // cycle button and the n/n indicator
        forbidCommonText();

        shell(3003);

        slot(8, 48);
        slot(26, 48);
        slot(44, 48);
        arrow(70, 48);
        slot(98, 48);
        save(path);
    }
}
