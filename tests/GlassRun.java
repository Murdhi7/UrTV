package sa.urtv.home;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * For tests/glass_test.py: GlassStyle over every photo we ship (the bundled backgrounds and the hosted photos), made into the backdrop
 * the TV makes (1/8, blurred), and over plain white, black and grey. One line per photo and look:
 * "photo NAME LOOK alpha=A worst=W" (W: the lowest contrast of any text under the veil it gets), then "solid LOOK worst=W".
 * Args: the photo files or folders.
 */
public final class GlassRun {
    public static void main(String[] args) throws Exception {
        List<File> files = new ArrayList<>();
        for (String a : args) {
            File f = new File(a);
            if (f.isDirectory()) {
                File[] in = f.listFiles((d, n) -> n.toLowerCase().matches(".*\\.(jpe?g|png|webp)$"));
                if (in != null) { Arrays.sort(in); files.addAll(Arrays.asList(in)); }
            } else files.add(f);
        }
        StringBuilder out = new StringBuilder();
        for (File f : files) {
            BufferedImage im = ImageIO.read(f);
            if (im == null) { out.append("skip ").append(f.getName()).append('\n'); continue; }
            int w = im.getWidth(), h = im.getHeight();
            int[] src = im.getRGB(0, 0, w, h, null, 0, w);
            line(out, "photo " + f.getName(), GlassStyle.blur(GlassStyle.downscale(src, w, h), GlassStyle.W, GlassStyle.H));
        }
        for (int c : new int[] {0xFFFFFFFF, 0xFF000000, 0xFF808080, 0xFFFF0000, 0xFF0000FF, 0xFFFFFF00}) {
            int[] flat = new int[GlassStyle.W * GlassStyle.H];
            Arrays.fill(flat, c);
            line(out, String.format("plain %06X", c & 0xFFFFFF), flat);
        }
        for (boolean light : new boolean[] {false, true})
            out.append("solid ").append(light ? "light" : "dark").append(String.format(" worst=%.2f", GlassStyle.worstSolid(GlassStyle.of(light)))).append('\n');
        System.out.print(out);
    }

    private static void line(StringBuilder out, String name, int[] px) {
        for (boolean light : new boolean[] {false, true}) {
            GlassStyle.Look l = GlassStyle.of(light);
            float a = GlassStyle.scrimAlpha(px, l);
            double worst = Double.MAX_VALUE;
            for (int p : px) worst = Math.min(worst, GlassStyle.worst(l, a, p));   // every pixel, not only the bounds
            out.append(name).append(light ? " light" : " dark").append(String.format(" alpha=%.2f worst=%.2f", a, worst)).append('\n');
        }
    }
}
