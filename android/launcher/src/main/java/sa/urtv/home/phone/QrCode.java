package sa.urtv.home.phone;

import java.nio.charset.StandardCharsets;

/**
 * A QR code encoder, written for this app (nothing is copied from another library): byte mode, versions 1 to 10, error correction level M,
 * the mask with the lowest penalty. Enough for the pairing address, which is about 65 characters (version 5 at level M, 37 modules wide).
 * No Android in it; QrView draws the modules.
 */
public final class QrCode {
    public static final int MAX_VERSION = 10;

    public final int version, size, mask;
    private final boolean[][] dark;

    // per version 1..10, level M: error-correction codewords per block, and the number of blocks
    private static final int[] EC_PER_BLOCK = {10, 16, 26, 18, 24, 16, 18, 22, 22, 26};
    private static final int[] BLOCKS = {1, 1, 1, 2, 2, 4, 4, 4, 5, 5};

    private QrCode(int version, boolean[][] dark, int mask) {
        this.version = version;
        this.dark = dark;
        this.size = dark.length;
        this.mask = mask;
    }

    public boolean dark(int x, int y) { return x >= 0 && y >= 0 && x < size && y < size && dark[y][x]; }

    /** The text as UTF-8 in the smallest version that holds it. */
    public static QrCode encode(String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        for (int v = 1; v <= MAX_VERSION; v++) {
            if (4 + (v < 10 ? 8 : 16) + data.length * 8 <= dataCodewords(v) * 8) return build(data, v);
        }
        throw new IllegalArgumentException("too long for a QR code of version " + MAX_VERSION);
    }

    private static int rawModules(int v) {
        int r = (16 * v + 128) * v + 64;
        if (v >= 2) {
            int n = v / 7 + 2;
            r -= (25 * n - 10) * n - 55;
            if (v >= 7) r -= 36;
        }
        return r;
    }

    private static int dataCodewords(int v) {
        return rawModules(v) / 8 - EC_PER_BLOCK[v - 1] * BLOCKS[v - 1];
    }

    private static QrCode build(byte[] data, int v) {
        int cap = dataCodewords(v);
        // the bits: mode 0100, the count, the bytes, up to four zeros, padding to a byte, then 0xEC 0x11 ...
        BitBuffer bb = new BitBuffer();
        bb.add(4, 4);
        bb.add(data.length, v < 10 ? 8 : 16);
        for (byte b : data) bb.add(b & 0xff, 8);
        bb.add(0, Math.min(4, cap * 8 - bb.len));
        bb.add(0, (8 - bb.len % 8) % 8);
        for (int pad = 0xEC; bb.len < cap * 8; pad ^= 0xEC ^ 0x11) bb.add(pad, 8);
        byte[] codewords = new byte[cap];
        for (int i = 0; i < cap; i++) codewords[i] = (byte) bb.bytes(i);
        byte[] all = interleave(codewords, v);

        int size = v * 4 + 17;
        boolean[][] fn = new boolean[size][size];
        boolean[][] mod = new boolean[size][size];
        functionPatterns(mod, fn, v, size);
        place(mod, fn, all, size);
        int best = 0;
        long bestScore = Long.MAX_VALUE;
        for (int m = 0; m < 8; m++) {
            applyMask(mod, fn, m, size);
            formatBits(mod, fn, m, size);
            long score = penalty(mod, size);
            if (score < bestScore) { bestScore = score; best = m; }
            applyMask(mod, fn, m, size);   // XOR again: undone
        }
        applyMask(mod, fn, best, size);
        formatBits(mod, fn, best, size);
        return new QrCode(v, mod, best);
    }

    private static final class BitBuffer {
        final boolean[] bits = new boolean[4096];
        int len;
        void add(int value, int n) {
            for (int i = n - 1; i >= 0; i--) bits[len++] = ((value >>> i) & 1) != 0;
        }
        int bytes(int i) {
            int v = 0;
            for (int k = 0; k < 8; k++) v = (v << 1) | (bits[i * 8 + k] ? 1 : 0);
            return v;
        }
    }

    // ---- error correction

    private static byte[] interleave(byte[] data, int v) {
        int blocks = BLOCKS[v - 1], ecLen = EC_PER_BLOCK[v - 1];
        int raw = rawModules(v) / 8;
        int shortBlocks = blocks - raw % blocks, shortLen = raw / blocks - ecLen;
        byte[][] dataBlocks = new byte[blocks][];
        byte[][] ecBlocks = new byte[blocks][];
        byte[] gen = generator(ecLen);
        int k = 0;
        for (int i = 0; i < blocks; i++) {
            int len = shortLen + (i < shortBlocks ? 0 : 1);
            dataBlocks[i] = java.util.Arrays.copyOfRange(data, k, k + len);
            k += len;
            ecBlocks[i] = remainder(dataBlocks[i], gen);
        }
        byte[] out = new byte[raw];
        int o = 0;
        for (int i = 0; i <= shortLen; i++) for (int j = 0; j < blocks; j++) if (i < dataBlocks[j].length) out[o++] = dataBlocks[j][i];
        for (int i = 0; i < ecLen; i++) for (int j = 0; j < blocks; j++) out[o++] = ecBlocks[j][i];
        return out;
    }

    static int mul(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z & 0xff;
    }

    static byte[] generator(int degree) {
        byte[] g = new byte[degree];
        g[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < degree; j++) {
                g[j] = (byte) mul(g[j] & 0xff, root);
                if (j + 1 < degree) g[j] ^= g[j + 1];
            }
            root = mul(root, 2);
        }
        return g;
    }

    static byte[] remainder(byte[] data, byte[] gen) {
        byte[] r = new byte[gen.length];
        for (byte b : data) {
            int f = (b ^ r[0]) & 0xff;
            System.arraycopy(r, 1, r, 0, r.length - 1);
            r[r.length - 1] = 0;
            for (int i = 0; i < r.length; i++) r[i] ^= mul(gen[i] & 0xff, f);
        }
        return r;
    }

    // ---- the picture

    private static void set(boolean[][] mod, boolean[][] fn, int x, int y, boolean d) {
        mod[y][x] = d;
        fn[y][x] = true;
    }

    private static int[] alignment(int v, int size) {
        if (v == 1) return new int[0];
        int n = v / 7 + 2;
        int step = (v * 4 + n * 2 + 1) / (n * 2 - 2) * 2;
        int[] p = new int[n];
        p[0] = 6;
        for (int i = n - 1, pos = size - 7; i >= 1; i--, pos -= step) p[i] = pos;
        return p;
    }

    private static void functionPatterns(boolean[][] mod, boolean[][] fn, int v, int size) {
        for (int i = 0; i < size; i++) {   // the timing lines
            set(mod, fn, 6, i, i % 2 == 0);
            set(mod, fn, i, 6, i % 2 == 0);
        }
        finder(mod, fn, 3, 3, size);
        finder(mod, fn, size - 4, 3, size);
        finder(mod, fn, 3, size - 4, size);
        int[] a = alignment(v, size);
        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < a.length; j++) {
                if ((i == 0 && j == 0) || (i == 0 && j == a.length - 1) || (i == a.length - 1 && j == 0)) continue;
                for (int dy = -2; dy <= 2; dy++) for (int dx = -2; dx <= 2; dx++) set(mod, fn, a[i] + dx, a[j] + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
            }
        }
        // format and version areas are reserved (drawn later)
        for (int i = 0; i < 9; i++) { fn[8][i] = true; fn[i][8] = true; }
        for (int i = 0; i < 8; i++) { fn[8][size - 1 - i] = true; fn[size - 1 - i][8] = true; }
        set(mod, fn, 8, size - 8, true);   // the dark module
        if (v >= 7) {
            int rem = v;
            for (int i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
            int bits = v << 12 | rem;
            for (int i = 0; i < 18; i++) {
                boolean b = ((bits >>> i) & 1) != 0;
                int p = size - 11 + i % 3, q = i / 3;
                set(mod, fn, p, q, b);
                set(mod, fn, q, p, b);
            }
        }
    }

    private static void finder(boolean[][] mod, boolean[][] fn, int cx, int cy, int size) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int x = cx + dx, y = cy + dy;
                if (x < 0 || y < 0 || x >= size || y >= size) continue;
                int d = Math.max(Math.abs(dx), Math.abs(dy));
                set(mod, fn, x, y, d != 2 && d != 4);
            }
        }
    }

    private static void place(boolean[][] mod, boolean[][] fn, byte[] data, int size) {
        int i = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5;
            for (int vert = 0; vert < size; vert++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean up = ((right + 1) & 2) == 0;
                    int y = up ? size - 1 - vert : vert;
                    if (!fn[y][x] && i < data.length * 8) {
                        mod[y][x] = ((data[i >>> 3] >>> (7 - (i & 7))) & 1) != 0;
                        i++;
                    }
                }
            }
        }
    }

    static boolean masked(int m, int x, int y) {
        switch (m) {
            case 0: return (x + y) % 2 == 0;
            case 1: return y % 2 == 0;
            case 2: return x % 3 == 0;
            case 3: return (x + y) % 3 == 0;
            case 4: return (x / 3 + y / 2) % 2 == 0;
            case 5: return x * y % 2 + x * y % 3 == 0;
            case 6: return (x * y % 2 + x * y % 3) % 2 == 0;
            default: return ((x + y) % 2 + x * y % 3) % 2 == 0;
        }
    }

    private static void applyMask(boolean[][] mod, boolean[][] fn, int m, int size) {
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) if (!fn[y][x] && masked(m, x, y)) mod[y][x] = !mod[y][x];
    }

    /** The 15 format bits: level M (00), the mask, a BCH code, XORed with 101010000010010. */
    static int formatValue(int m) {
        int data = m;
        int rem = data;
        for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        return (data << 10 | rem) ^ 0x5412;
    }

    private static void formatBits(boolean[][] mod, boolean[][] fn, int m, int size) {
        int bits = formatValue(m);
        for (int i = 0; i <= 5; i++) mod[i][8] = bit(bits, i);
        mod[7][8] = bit(bits, 6);
        mod[8][8] = bit(bits, 7);
        mod[8][7] = bit(bits, 8);
        for (int i = 9; i < 15; i++) mod[8][14 - i] = bit(bits, i);
        for (int i = 0; i < 8; i++) mod[8][size - 1 - i] = bit(bits, i);
        for (int i = 8; i < 15; i++) mod[size - 15 + i][8] = bit(bits, i);
        mod[size - 8][8] = true;
    }

    private static boolean bit(int v, int i) { return ((v >>> i) & 1) != 0; }

    // ---- choosing the mask

    private static long penalty(boolean[][] m, int n) {
        long score = 0;
        for (int pass = 0; pass < 2; pass++) {   // rows, then columns: runs of five or more, and the 1:1:3:1:1 look-alike of a finder
            for (int a = 0; a < n; a++) {
                int run = 1;
                int pat = 0;
                for (int b = 0; b < n; b++) {
                    boolean cur = pass == 0 ? m[a][b] : m[b][a];
                    if (b > 0) {
                        boolean prev = pass == 0 ? m[a][b - 1] : m[b - 1][a];
                        if (cur == prev) { run++; if (run == 5) score += 3; else if (run > 5) score++; } else run = 1;
                    }
                    pat = ((pat << 1) | (cur ? 1 : 0)) & 0x7ff;
                    if (b >= 10 && (pat == 0x05D || pat == 0x5D0)) score += 40;
                }
            }
        }
        for (int y = 0; y + 1 < n; y++) for (int x = 0; x + 1 < n; x++) {
            boolean c = m[y][x];
            if (c == m[y][x + 1] && c == m[y + 1][x] && c == m[y + 1][x + 1]) score += 3;
        }
        long dark = 0;
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) if (m[y][x]) dark++;
        long total = (long) n * n;
        long k = (Math.abs(dark * 20 - total * 10) + total - 1) / total - 1;
        return score + Math.max(0, k) * 10;
    }
}
