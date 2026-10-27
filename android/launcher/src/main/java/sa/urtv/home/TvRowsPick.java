package sa.urtv.home;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The channel rows under the favourites (the idea and the defaults from talkies, MIT: THIRD_PARTY_NOTICES.md): which of the rows the
 * apps publish to Android TV («Netflix», «Recommended for you»…) and «أكمل المشاهدة» are shown, in the owner's order; the line under
 * a card; which Watch Next entries count; and which links a card may open. No Android in here: tests/TvRowsRun.java runs it.
 */
final class TvRowsPick {
    private TvRowsPick() { }

    static final String WATCH_NEXT = "watchnext";
    static final String WATCH_NEXT_TITLE = "أكمل المشاهدة";

    /** A row an app published (TvContract.Channels, TYPE_PREVIEW), with something in it. key: "package|name". */
    static final class Found {
        final String key, pkg, name, app;
        final int count;
        final boolean browsable;   // the app asked Android TV to show it on the home screen
        Found(String pkg, String name, String app, int count, boolean browsable) {
            this.key = pkg + "|" + name; this.pkg = pkg; this.name = name; this.app = app; this.count = count; this.browsable = browsable;
        }
    }

    /** A row as the owner arranges it. pkg is null for «أكمل المشاهدة». */
    static final class Row {
        final String key, pkg, title;
        final int count;
        boolean shown;
        Row(String key, String pkg, String title, int count) { this.key = key; this.pkg = pkg; this.title = title; this.count = count; }
    }

    /** The app's name alone when the row is named after it («Netflix»), else «YouTube · Recommended for you». */
    static String title(String app, String name) {
        String a = app == null ? "" : app.trim(), n = name == null ? "" : name.trim();
        if (a.isEmpty()) return n;
        if (n.isEmpty() || n.equalsIgnoreCase(a) || a.toLowerCase(Locale.ROOT).contains(n.toLowerCase(Locale.ROOT))) return a;
        return a + " · " + n;
    }

    /**
     * Every row there is, in the owner's order, each shown or not. An app that published one row twice keeps the fuller one. Rows not
     * placed yet go after, by title. Until the owner has chosen (savedShown null): «أكمل المشاهدة» and the rows their apps asked to
     * show; if no app asked, each app's first row. savedOrder / savedShown: the keys, one a line (as {@link #save} writes them).
     */
    static List<Row> arrange(List<Found> found, int watchNextCount, String savedOrder, String savedShown) {
        Map<String, Row> all = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        Set<String> browsable = new HashSet<>();
        if (watchNextCount > 0) all.put(WATCH_NEXT, new Row(WATCH_NEXT, null, WATCH_NEXT_TITLE, watchNextCount));
        for (Found f : found) {
            if (f.count <= 0 || f.pkg == null || f.name == null) continue;
            Integer had = counts.get(f.key);
            if (had != null && had >= f.count) continue;
            counts.put(f.key, f.count);
            all.put(f.key, new Row(f.key, f.pkg, title(f.app, f.name), f.count));
            if (f.browsable) browsable.add(f.key);
        }
        Set<String> shown;
        if (savedShown != null) {
            shown = new HashSet<>(lines(savedShown));
        } else {
            shown = new HashSet<>();
            shown.add(WATCH_NEXT);
            shown.addAll(browsable);
            if (browsable.isEmpty()) {
                Set<String> apps = new HashSet<>();
                for (Row r : all.values()) if (r.pkg != null && apps.add(r.pkg)) shown.add(r.key);
            }
        }
        List<Row> out = new ArrayList<>();
        if (savedOrder != null) for (String k : lines(savedOrder)) { Row r = all.remove(k); if (r != null) out.add(r); }
        List<Row> rest = new ArrayList<>(all.values());
        Row wn = null;
        for (Row r : rest) if (WATCH_NEXT.equals(r.key)) wn = r;
        if (wn != null) { rest.remove(wn); out.add(0, wn); }   // never placed: «أكمل المشاهدة» first, as the home screens do
        Collections.sort(rest, (a, b) -> a.title.compareToIgnoreCase(b.title));
        out.addAll(rest);
        for (Row r : out) r.shown = shown.contains(r.key);
        return out;
    }

    /** {order, shown}: what {@link #arrange} reads back. */
    static String[] save(List<Row> rows) {
        StringBuilder order = new StringBuilder(), shown = new StringBuilder();
        for (Row r : rows) {
            order.append(r.key).append('\n');
            if (r.shown) shown.append(r.key).append('\n');
        }
        return new String[]{order.toString(), shown.toString()};
    }

    /** Moves row {@code i} one place up (-1) or down (+1); false at an edge. */
    static boolean move(List<Row> rows, int i, int d) {
        int j = i + d;
        if (i < 0 || i >= rows.size() || j < 0 || j >= rows.size()) return false;
        Collections.swap(rows, i, j);
        return true;
    }

    private static List<String> lines(String s) {
        List<String> out = new ArrayList<>();
        for (String l : Arrays.asList(s.split("\n"))) if (!l.trim().isEmpty()) out.add(l.trim());
        return out;
    }

    // android.media.tv.TvContract.WatchNextPrograms' types: what one is in the middle of (0), its next episode (1); a new
    // season (2) and a watchlist (3) are the app's promotion, not something watched
    static final int WATCH_NEXT_TYPE_CONTINUE = 0, WATCH_NEXT_TYPE_NEXT = 1;

    /** A Watch Next entry shown in «أكمل المشاهدة»: continue or next episode, browsable, from an app that is on the TV. */
    static boolean watchNextShown(int type, boolean browsable, boolean appThere) {
        return (type == WATCH_NEXT_TYPE_CONTINUE || type == WATCH_NEXT_TYPE_NEXT) && browsable && appThere;
    }

    /** The line under a card: «الموسم ٢ · الحلقة ١ · Netflix», or the episode's own name, or the description; digits as the setting says. */
    static String meta(String season, String episode, String episodeTitle, String title, String description, String app, boolean latin) {
        StringBuilder m = new StringBuilder();
        if (!blank(season)) m.append("الموسم ").append(DigitsText.convert(season.trim(), latin));
        if (!blank(episode)) m.append(m.length() > 0 ? " · " : "").append("الحلقة ").append(DigitsText.convert(episode.trim(), latin));
        if (m.length() == 0 && !blank(episodeTitle) && !episodeTitle.trim().equals(title == null ? "" : title.trim())) m.append(episodeTitle.trim());
        if (m.length() == 0 && !blank(description)) m.append(description.trim());
        if (!blank(app)) m.append(m.length() > 0 ? " · " : "").append(app.trim());
        return m.toString();
    }

    /**
     * Whether a card may open its link: only into the app that published it (the link's package or component names that app),
     * never another one, and never a link that names none (it would open whatever answers it).
     */
    static boolean linkAllowed(String rowPkg, String linkPkg, String componentPkg) {
        if (rowPkg == null || rowPkg.isEmpty()) return false;
        if (componentPkg != null) return rowPkg.equals(componentPkg) && (linkPkg == null || rowPkg.equals(linkPkg));
        return rowPkg.equals(linkPkg);
    }

    /** How far in, 0 to 1, from the position and the length; -1 when not known. */
    static float progress(long position, long duration) {
        return position > 0 && duration > 0 ? Math.min(1f, position / (float) duration) : -1f;
    }

    private static boolean blank(String s) { return s == null || s.trim().isEmpty() || "null".equals(s.trim()); }
}
