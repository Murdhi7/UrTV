package sa.urtv.home;

import android.content.Context;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** The TV's inputs and the name shown for each: the one the owner gave it (InputNames), else the TV's own. One place for the popup, «أسماء المصادر» and ADB. */
final class InputLabels {
    private static final String KEY = "input_names";

    private InputLabels() {}

    /** The inputs the sources popup lists (HDMI, AV...), in the TV's own names' order. */
    static List<TvInputInfo> reported(Context c) {
        TvInputManager tim = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
        List<TvInputInfo> inputs = new ArrayList<>();
        if (tim != null) for (TvInputInfo t : tim.getTvInputList()) {
            if (t.isPassthroughInput() && !t.isHidden(c)) inputs.add(t);
        }
        Collections.sort(inputs, (x, y) -> own(c, x).compareToIgnoreCase(own(c, y)));
        return inputs;
    }

    /** The TV's own name for it. */
    static String own(Context c, TvInputInfo t) {
        CharSequence s = t.loadCustomLabel(c);
        if (s == null || s.length() == 0) s = t.loadLabel(c);
        return String.valueOf(s);
    }

    static String label(Context c, TvInputInfo t, Map<String, String> given) {
        return InputNames.label(given.get(t.getId()), own(c, t));
    }

    static Map<String, String> given(Context c) {
        return InputNames.parse(Dock.prefs(c).getString(KEY, ""));
    }

    static void set(Context c, String id, String name) {
        Dock.prefs(c).edit().putString(KEY, InputNames.serialize(InputNames.with(given(c), id, name))).apply();
    }
}
