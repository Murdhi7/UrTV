package sa.urtv.home.phone;

import java.util.List;
import java.util.Map;

/**
 * What the phone may do on the TV, implemented by PhoneActions (Android) and by a stand-in in the tests. Everything arrives already checked
 * by Schema. A method answers with a Result: ok with a short word the page explains, or a fixed failure code; never text from the request.
 */
public interface Actions {
    final class Result {
        public final boolean ok;
        public final String code;
        private Result(boolean ok, String code) { this.ok = ok; this.code = code; }
        public static Result ok(String code) { return new Result(true, code); }
        public static Result fail(String code) { return new Result(false, code); }
    }

    /** A remote key (Schema.KEYS). */
    Result key(String key);

    /** Typed text: "typed" (into a field on the TV), "task" (added to «المهام»), "copied" (no field was open: put on the TV's clipboard). */
    Result text(String text);

    /** What the page shows: settings and player state. Nothing secret. */
    Map<String, Object> state();

    /** The settings of Schema "set", through the same code as the TV's own settings. */
    Result set(Map<String, Object> values);

    /** The Quran player: Schema "quran". */
    Result quran(Map<String, Object> values);

    /** Built-in city names that match the words, at most twenty. */
    List<String> cities(String query);

    /** «تحديث تلفزيونك»: op check or install; a map with a status word the page explains. */
    Map<String, Object> update(String op);
}
