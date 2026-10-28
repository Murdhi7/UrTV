package sa.urtv.home;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** For tests/tv_rows_test.py: TvRowsPick on a plain JVM. Prints "name = value" lines; a row list as "key:shown,key:shown". */
public final class TvRowsRun {
    static final StringBuilder out = new StringBuilder();

    static void put(String k, Object v) { out.append(k).append(" = ").append(v).append('\n'); }

    static String rows(List<TvRowsPick.Row> rs) {
        List<String> s = new ArrayList<>();
        for (TvRowsPick.Row r : rs) s.add(r.key + ":" + (r.shown ? 1 : 0));
        return String.join(",", s);
    }

    static TvRowsPick.Found f(String pkg, String name, String app, int n, boolean browsable) { return new TvRowsPick.Found(pkg, name, app, n, browsable); }

    public static void main(String[] args) {
        put("title_same", TvRowsPick.title("Netflix", "Netflix"));
        put("title_two", TvRowsPick.title("YouTube", "Recommended for you"));
        put("title_inside", TvRowsPick.title("Prime Video", "Prime"));
        put("title_noapp", TvRowsPick.title("", "Top Picks"));

        List<TvRowsPick.Found> found = Arrays.asList(
            f("com.netflix.ninja", "Netflix", "Netflix", 12, true),
            f("com.google.android.youtube.tv", "Recommended for you", "YouTube", 20, true),
            f("com.google.android.youtube.tv", "Trending", "YouTube", 8, false),
            f("com.apple.atve", "Up Next", "Apple TV", 3, false),
            f("com.apple.atve", "Up Next", "Apple TV", 9, false),          // the same row twice: the fuller one
            f("com.empty.app", "Nothing", "Empty", 0, true));               // nothing in it: not a row
        put("default", rows(TvRowsPick.arrange(found, 4, null, null)));
        put("default_no_watchnext", rows(TvRowsPick.arrange(found, 0, null, null)));
        List<TvRowsPick.Found> none = Arrays.asList(f("a.one", "Row A", "One", 2, false), f("a.one", "Row B", "One", 3, false), f("b.two", "Row C", "Two", 1, false));
        put("default_none_browsable", rows(TvRowsPick.arrange(none, 0, null, null)));
        for (TvRowsPick.Row r : TvRowsPick.arrange(found, 4, null, null)) if (r.key.equals("com.apple.atve|Up Next")) put("duplicate_count", r.count);
        put("watchnext_title", TvRowsPick.arrange(found, 4, null, null).get(0).title);

        String order = "com.apple.atve|Up Next\ngone.app|Old\nwatchnext\ncom.netflix.ninja|Netflix\n", shown = "com.apple.atve|Up Next\ncom.netflix.ninja|Netflix\n";
        put("saved", rows(TvRowsPick.arrange(found, 4, order, shown)));
        List<TvRowsPick.Row> rs = TvRowsPick.arrange(found, 4, order, shown);
        String[] saved = TvRowsPick.save(rs);
        put("roundtrip", rows(TvRowsPick.arrange(found, 4, saved[0], saved[1])).equals(rows(rs)));
        put("saved_nothing_shown", rows(TvRowsPick.arrange(found, 4, order, "")));
        put("move_up", TvRowsPick.move(rs, 1, -1) + " " + rows(rs));
        put("move_top_edge", TvRowsPick.move(rs, 0, -1));
        put("move_bottom_edge", TvRowsPick.move(rs, rs.size() - 1, 1));

        put("wn_continue", TvRowsPick.watchNextShown(0, true, true));
        put("wn_next", TvRowsPick.watchNextShown(1, true, true));
        put("wn_new", TvRowsPick.watchNextShown(2, true, true));
        put("wn_watchlist", TvRowsPick.watchNextShown(3, true, true));
        put("wn_hidden", TvRowsPick.watchNextShown(0, false, true));
        put("wn_no_app", TvRowsPick.watchNextShown(0, true, false));

        put("meta_episode", TvRowsPick.meta("2", "1", "Pilot", "Show", "", "Netflix", false));
        put("meta_latin", TvRowsPick.meta("2", "10", "", "Show", "", "Netflix", true));
        put("meta_eptitle", TvRowsPick.meta("", "", "The Long Night", "Show", "", "", false));
        put("meta_same_title", TvRowsPick.meta("", "", "Show", "Show", "desc here", "", false));
        put("meta_nulls", TvRowsPick.meta("null", null, "null", "Show", "null", "", false));

        put("link_package", TvRowsPick.linkAllowed("com.netflix.ninja", "com.netflix.ninja", null));
        put("link_component", TvRowsPick.linkAllowed("com.netflix.ninja", null, "com.netflix.ninja"));
        put("link_other_component", TvRowsPick.linkAllowed("com.netflix.ninja", null, "com.evil.app"));
        put("link_other_package", TvRowsPick.linkAllowed("com.netflix.ninja", "com.evil.app", null));
        put("link_mixed", TvRowsPick.linkAllowed("com.netflix.ninja", "com.evil.app", "com.netflix.ninja"));
        put("link_none", TvRowsPick.linkAllowed("com.netflix.ninja", null, null));
        put("link_no_row", TvRowsPick.linkAllowed(null, "x", "x"));

        // a huge picture (an 8000×6000 JPEG, 190 MB at full size) is decoded at 1/8 and then fitted to 400×400; a tall poster too
        int s = TvRowsPick.sampleSize(8000, 6000, 400, 400);
        put("sample_huge", s + " " + (8000 / s) + "x" + (6000 / s) + " " + ((8000L / s) * (6000 / s) * 4 / 1024) + "KB");
        int[] f = TvRowsPick.fitted(8000 / s, 6000 / s, 400, 400);
        put("fit_huge", f[0] + "x" + f[1]);
        int t = TvRowsPick.sampleSize(1000, 30000, 400, 400);
        put("sample_tall", t + " " + (1000 / t) + "x" + (30000 / t));
        put("fit_tall", String.join("x", String.valueOf(TvRowsPick.fitted(1000 / t, 30000 / t, 400, 400)[0]), String.valueOf(TvRowsPick.fitted(1000 / t, 30000 / t, 400, 400)[1])));
        put("sample_small", TvRowsPick.sampleSize(320, 180, 400, 400) + " " + TvRowsPick.fitted(320, 180, 400, 400)[0]);
        put("sample_none", TvRowsPick.sampleSize(0, 0, 400, 400) + " " + TvRowsPick.sampleSize(-1, 50, 400, 400));
        put("provider_own", TvRowsPick.pictureProviderAllowed("com.netflix.ninja", "com.netflix.ninja"));
        put("provider_other", TvRowsPick.pictureProviderAllowed("com.netflix.ninja", "com.android.providers.media"));
        put("provider_none", TvRowsPick.pictureProviderAllowed("com.netflix.ninja", null));
        put("sig_same", TvRowsPick.signature(Arrays.asList(new String[]{"a", "t1"}, new String[]{"b"})).equals(TvRowsPick.signature(Arrays.asList(new String[]{"a", "t1"}, new String[]{"b"}))));
        put("sig_diff", TvRowsPick.signature(Arrays.asList(new String[]{"a", "t1"}, new String[]{"b"})).equals(TvRowsPick.signature(Arrays.asList(new String[]{"a"}, new String[]{"t1", "b"}))));

        put("progress_half", TvRowsPick.progress(30, 60));
        put("progress_over", TvRowsPick.progress(90, 60));
        put("progress_unknown", TvRowsPick.progress(0, 60) + " " + TvRowsPick.progress(10, 0));
        put("needs_unchosen", TvRowsPick.needsChannels(null));
        put("needs_only_wn", TvRowsPick.needsChannels("watchnext\n"));
        put("needs_nothing", TvRowsPick.needsChannels(""));
        put("needs_other", TvRowsPick.needsChannels("watchnext\ncom.netflix.ninja|Netflix\n"));
        System.out.print(out);
    }
}
