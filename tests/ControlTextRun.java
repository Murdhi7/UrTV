package sa.urtv.home;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * For tests/control_center_test.py: ControlText.java on tests/control_center_cases.tsv. A line is
 *   freed      BEFORE AFTER TRIED          -> «حرّر الذاكرة»'s answer
 *   mbps       VALUE                       -> the speed test's answer
 *   signal     RSSI                        -> the bars (0..4) of a dBm
 *   network    wired(0|1) wifi(0|1) SSID RSSI -> the network tile's two lines, joined with «|»
 *   output     TYPE,TYPE,...               -> the sound output tile's line
 *   killable   PKG system(0|1) keyboard(0|1) -> true or false
 */
public final class ControlTextRun {
    public static void main(String[] args) throws IOException {
        StringBuilder out = new StringBuilder();
        for (String line : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t", -1);
            switch (f[0]) {
                case "freed": out.append(ControlText.freed(Long.parseLong(f[1]), Long.parseLong(f[2]), Integer.parseInt(f[3]))); break;
                case "mbps": out.append(ControlText.mbps(Double.parseDouble(f[1]))); break;
                case "signal": out.append(ControlText.signalLevel(Integer.parseInt(f[1]))); break;
                case "network": {
                    boolean wired = "1".equals(f[1]), wifi = "1".equals(f[2]);
                    out.append(ControlText.network(wired, wifi, f[3].equals("null") ? null : f[3])).append('|')
                            .append(ControlText.networkDetail(wired, wifi, Integer.parseInt(f[4])));
                    break;
                }
                case "output": {
                    String[] t = f[1].isEmpty() ? new String[0] : f[1].split(",");
                    int[] types = new int[t.length];
                    for (int i = 0; i < t.length; i++) types[i] = Integer.parseInt(t[i]);
                    out.append(ControlText.outputs(types));
                    break;
                }
                default: out.append(ControlText.killable(f[1], "1".equals(f[2]), "1".equals(f[3])));
            }
            out.append('\n');
        }
        System.out.write(out.toString().getBytes(StandardCharsets.UTF_8));
        System.out.flush();
    }
}
