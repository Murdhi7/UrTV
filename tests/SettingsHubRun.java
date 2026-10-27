package sa.urtv.screensaver;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * For tests/settings_hub_test.py: SettingsHub and FocusKey on a plain JVM. Reads a file of lines "row<TAB>label<TAB>header" or
 * "header<TAB>text" (what the test found in SettingsActivity) and prints one line each: "key<TAB>category" for a row named in
 * SettingsHub.ROWS (or "key<TAB>MISSING"), "text<TAB>category" for a header (or MISSING); then the grid's ways: "next i n dir = j".
 */
public final class SettingsHubRun {
    public static void main(String[] args) throws Exception {
        StringBuilder out = new StringBuilder();
        for (String line : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            String[] f = line.split("\t", -1);
            if (f[0].equals("row")) {
                String key = FocusKey.key(f[1]);
                boolean named = false;
                for (String[] r : SettingsHub.ROWS) named |= r[0].equals(key);
                out.append(key).append('\t').append(named ? SettingsHub.of(key, null) : "MISSING").append('\t')
                   .append(SettingsHub.of(key, SettingsHub.ofHeader(f[2]))).append('\n');
            } else if (f[0].equals("header")) {
                String c = SettingsHub.ofHeader(f[1]);
                out.append(f[1]).append('\t').append(c == null ? "MISSING" : c).append('\n');
            }
        }
        int n = SettingsHub.CATEGORIES.length;
        for (int i = 0; i < n; i++) for (int dir = 0; dir < 4; dir++)
            out.append("next ").append(i).append(' ').append(n).append(' ').append(dir).append(" = ").append(SettingsHub.next(i, n, dir)).append('\n');
        out.append("next 6 7 1 = ").append(SettingsHub.next(6, 7, 1)).append('\n').append("next 6 7 2 = ").append(SettingsHub.next(6, 7, 2)).append('\n');
        for (String[] c : SettingsHub.CATEGORIES) out.append("category ").append(c[0]).append('\t').append(c[1]).append('\t').append(SettingsHub.index(c[0])).append('\n');
        System.out.print(out);
    }
}
