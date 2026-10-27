package sa.urtv.home;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

/**
 * For tests/input_names_test.py: InputNames.java on tests/input_names_cases.tsv. A line is
 *   clean   RAW                    -> the name to keep, "" for none, NULL when it can't be kept (\n in RAW is a newline, \t a tab)
 *   label   SAVED  OWN             -> the name shown (SAVED "-" = nothing saved)
 *   id      ID                     -> ok | no
 *   roundtrip  STORED  ID  NAME    -> keep NAME for ID in STORED (\n for a line break), print it again as one line with | for line breaks
 */
public final class InputNamesRun {
    public static void main(String[] args) throws IOException {
        StringBuilder out = new StringBuilder();
        for (String line : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t", -1);
            switch (f[0]) {
                case "clean": {
                    String r = InputNames.clean(f[1].replace("\\n", "\n").replace("\\t", "\t").replace("\\u0085", "\u0085"));
                    out.append(r == null ? "NULL" : r);
                    break;
                }
                case "label": out.append(InputNames.label(f[1].equals("-") ? null : f[1], f[2])); break;
                case "id": out.append(InputNames.validId(f[1]) ? "ok" : "no"); break;
                default: {
                    Map<String, String> m = InputNames.parse(f[1].replace("\\n", "\n"));
                    m = InputNames.with(m, f[2], f[3]);
                    out.append(InputNames.serialize(m).replace("\n", ";"));
                }
            }
            out.append('\n');
        }
        System.out.write(out.toString().getBytes(StandardCharsets.UTF_8));
        System.out.flush();
    }
}
