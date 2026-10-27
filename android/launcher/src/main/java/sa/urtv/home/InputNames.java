package sa.urtv.home;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The names the owner gave the TV's inputs (HDMI 1 -> «PS5»), with no Android in them (tests/InputNamesRun.java runs them).
 * Kept on this TV only, keyed by the input's id, as "id|name" lines in the home screen's preferences. A name for an input the
 * TV no longer reports stays in the file, harmlessly. With nothing saved every input shows the TV's own name, as always.
 */
final class InputNames {
    static final int MAX_LEN = 20;   // characters (code points), counted the way the owner reads them

    private InputNames() {}

    /**
     * The name to keep, or null when it can't be: trimmed, at most MAX_LEN characters, one line, no control characters.
     * Empty (or only spaces) is "": it brings the TV's own name back.
     */
    static String clean(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.codePointCount(0, s.length()) > MAX_LEN) return null;
        for (int i = 0; i < s.length(); i++) if (Character.isISOControl(s.charAt(i))) return null;
        return s;
    }

    /** What the row shows: the saved name when there is one, else the TV's own. */
    static String label(String saved, String own) {
        return saved == null || saved.isEmpty() ? own : saved;
    }

    /** An input's id as the TV writes it (com.vendor/.Service/HDMI12345): safe to keep and to send to the shell in single quotes. */
    static boolean validId(String id) {
        return id != null && !id.isEmpty() && id.length() <= 200 && id.matches("[A-Za-z0-9._/%:$-]+");
    }

    /** The preferences' text as a map: lines "id|name"; a line with no bar or no id is ignored. */
    static Map<String, String> parse(String stored) {
        Map<String, String> m = new LinkedHashMap<>();
        if (stored == null) return m;
        for (String row : stored.split("\n")) {
            int bar = row.indexOf('|');
            if (bar > 0 && bar < row.length() - 1) m.put(row.substring(0, bar), row.substring(bar + 1));
        }
        return m;
    }

    static String serialize(Map<String, String> m) {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : m.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) continue;   // an empty name is no name
            b.append(e.getKey()).append('|').append(e.getValue()).append('\n');
        }
        return b.toString();
    }

    /** The map after naming one input: an empty name takes it out; every other saved name stays, shown or not. */
    static Map<String, String> with(Map<String, String> m, String id, String name) {
        Map<String, String> out = new LinkedHashMap<>(m);
        if (name.isEmpty()) out.remove(id); else out.put(id, name);
        return out;
    }
}
