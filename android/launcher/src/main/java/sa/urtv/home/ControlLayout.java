package sa.urtv.home;

/**
 * The size of «مركز التحكم»'s tiles for this screen, with no Android in it (tests/ControlLayoutRun.java runs it). The panel never
 * asks for more than {@link #MAX_W} of the width and {@link #MAX_H} of the height: the tiles shrink (and the labels go compact) before
 * the last row can be cut, and the panel is told its own height so the caller can scroll what still does not fit.
 */
final class ControlLayout {
    private ControlLayout() {}

    static final float MAX_W = 0.82f, MAX_H = 0.90f;
    /** dp: the panel's own padding on the sides, the gap between tiles, and what the title, its notice line and the padding take above and below the rows */
    static final int SIDE_PAD = 36, GAP = 10, CHROME = 100;
    /** dp: a tile at its best, and the least that still reads (icon, name, one line) */
    static final int TILE_W = 170, TILE_H = 116, MIN_W = 80, MIN_H = 72;
    /** dp: below these a tile is «compact» (smaller icon and text, one line under the name) */
    static final int COMFY_W = 112, COMFY_H = 108;

    static final class Plan {
        final int cols, rows, tileW, tileH, panelW, panelH;   // pixels
        final boolean compact;
        /** the tile is below MIN_H: the name and the icon only, no line under it */
        final boolean tiny;

        Plan(int cols, int rows, int tileW, int tileH, int panelW, int panelH, boolean compact, boolean tiny) {
            this.cols = cols;
            this.rows = rows;
            this.tileW = tileW;
            this.tileH = tileH;
            this.panelW = panelW;
            this.panelH = panelH;
            this.compact = compact;
            this.tiny = tiny;
        }
    }

    private static int px(float dp, float density) { return Math.round(dp * density); }

    /** screenW and screenH in pixels, density = pixels per dp, n = how many tiles. 4 columns when they fit, else 3, else 2. */
    static Plan plan(int screenW, int screenH, float density, int n) {
        if (density <= 0) density = 1;
        if (n < 1) n = 1;
        float wDp = screenW / density, hDp = screenH / density;
        float availW = wDp * MAX_W - SIDE_PAD, availH = hDp * MAX_H - CHROME;
        Plan first = null, valid = null;
        for (int cols = 4; cols >= 2; cols--) {
            int rows = (n + cols - 1) / cols, c = Math.min(cols, n);
            float w = Math.min(TILE_W, (availW - GAP * (c - 1)) / c);
            float h = Math.min(TILE_H, (availH - GAP * (rows - 1)) / rows);
            boolean fits = w >= MIN_W && h >= MIN_H;
            boolean comfy = w >= COMFY_W && h >= COMFY_H;
            Plan p = build(cols, rows, c, w, h, density, !comfy);
            if (fits && comfy) return p;
            if (fits && valid == null) valid = p;
            if (first == null) first = p;
        }
        if (valid != null) return valid;
        // nothing reaches the least: the smallest tiles on the fewest rows, the caller scrolls the rest
        int rows = (n + 3) / 4, c = Math.min(4, n);
        float w = Math.max(MIN_W * 0.75f, Math.min(TILE_W, (availW - GAP * (c - 1)) / c));
        float h = Math.max(MIN_H * 0.7f, Math.min(TILE_H, (availH - GAP * (rows - 1)) / rows));
        return build(4, rows, c, w, h, density, true);
    }

    private static Plan build(int cols, int rows, int c, float wDp, float hDp, float density, boolean compact) {
        int tw = px(wDp, density), th = px(hDp, density);
        boolean tiny = hDp < MIN_H;
        int panelW = tw * c + px(GAP, density) * (c - 1) + px(SIDE_PAD, density);
        int rowsH = th * rows + px(GAP, density) * (rows - 1);
        int panelH = rowsH + px(CHROME, density);
        return new Plan(cols, rows, tw, th, panelW, panelH, compact, tiny);
    }

    /** the panel's own limit in pixels, for a caller that scrolls (the height the screen allows it) */
    static int maxHeight(int screenH) { return Math.round(screenH * MAX_H); }
}
