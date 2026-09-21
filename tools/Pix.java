import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

/**
 * Texture inspection helper. Not shipped — a scratchpad tool so the art can be looked at
 * properly instead of squinted at.
 *
 *   zoom  <in.png> <out.png> <factor>   nearest-neighbour upscale, for viewing
 *   grid  <in.png>                      per-pixel character map + palette listing
 *   sheet <out.png> <in.png>...         tile several textures side by side, zoomed
 */
public class Pix {

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "zoom"  -> zoom(args[1], args[2], Integer.parseInt(args[3]));
            case "grid"  -> grid(args[1]);
            case "sheet" -> sheet(args);
            default -> System.out.println("unknown command");
        }
    }

    static BufferedImage read(String p) throws Exception {
        BufferedImage src = ImageIO.read(new File(p));
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        out.getGraphics().drawImage(src, 0, 0, null);
        return out;
    }

    static void zoom(String in, String out, int f) throws Exception {
        BufferedImage s = read(in);
        BufferedImage d = new BufferedImage(s.getWidth() * f, s.getHeight() * f, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < d.getHeight(); y++)
            for (int x = 0; x < d.getWidth(); x++)
                d.setRGB(x, y, s.getRGB(x / f, y / f));
        ImageIO.write(d, "png", new File(out));
        System.out.println("wrote " + out + "  " + d.getWidth() + "x" + d.getHeight());
    }

    /** A character per pixel, plus the palette sorted by frequency. */
    static void grid(String in) throws Exception {
        BufferedImage s = read(in);
        LinkedHashMap<Integer, Integer> counts = new LinkedHashMap<>();
        for (int y = 0; y < s.getHeight(); y++)
            for (int x = 0; x < s.getWidth(); x++)
                counts.merge(s.getRGB(x, y), 1, Integer::sum);

        List<Integer> palette = new ArrayList<>(counts.keySet());
        palette.sort((a, b) -> counts.get(b) - counts.get(a));

        String glyphs = ".:-=+*#%@abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        Map<Integer, Character> ch = new HashMap<>();
        for (int i = 0; i < palette.size(); i++) {
            int argb = palette.get(i);
            ch.put(argb, (argb >>> 24) == 0 ? ' ' : glyphs.charAt(Math.min(i, glyphs.length() - 1)));
        }

        System.out.println(s.getWidth() + "x" + s.getHeight() + "   " + palette.size() + " colours");
        System.out.println();
        for (int y = 0; y < s.getHeight(); y++) {
            StringBuilder sb = new StringBuilder();
            for (int x = 0; x < s.getWidth(); x++) sb.append(ch.get(s.getRGB(x, y)));
            System.out.printf("%3d |%s|%n", y, sb);
        }
        System.out.println();
        for (int argb : palette) {
            int a = argb >>> 24;
            System.out.printf("  %c  #%06X  alpha %3d  x%d%n",
                    ch.get(argb), argb & 0xFFFFFF, a, counts.get(argb));
        }
    }

    static void sheet(String[] args) throws Exception {
        int f = 8, pad = 4;
        List<BufferedImage> imgs = new ArrayList<>();
        for (int i = 2; i < args.length; i++) imgs.add(read(args[i]));
        int h = 0, w = pad;
        for (BufferedImage b : imgs) { h = Math.max(h, b.getHeight() * f); w += b.getWidth() * f + pad; }
        BufferedImage d = new BufferedImage(w, h + pad * 2, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < d.getHeight(); y++)
            for (int x = 0; x < d.getWidth(); x++) d.setRGB(x, y, 0xFF202020);
        int ox = pad;
        for (BufferedImage b : imgs) {
            for (int y = 0; y < b.getHeight() * f; y++)
                for (int x = 0; x < b.getWidth() * f; x++) {
                    int px = b.getRGB(x / f, y / f);
                    if ((px >>> 24) != 0) d.setRGB(ox + x, pad + y, px);
                }
            ox += b.getWidth() * f + pad;
        }
        ImageIO.write(d, "png", new File(args[1]));
        System.out.println("wrote " + args[1] + "  " + d.getWidth() + "x" + d.getHeight());
    }
}
