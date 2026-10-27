package sa.urtv.home;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.tv.TvContract;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads the rows for the home screen from Android TV's own lists (the way talkies does, MIT: THIRD_PARTY_NOTICES.md): «أكمل المشاهدة»
 * from Watch Next, and the rows the apps publish (preview channels). Needs READ_TV_LISTINGS, which the owner grants in «صفوف القنوات»;
 * without it nothing is read and the home has no rows, as before. The artwork is kept small on disk (files/tvrows), each address
 * once. Blocking (the provider and the artwork): call it off the main thread. TvRowsPick decides the order and the words.
 */
final class TvRows {
    static final String PERMISSION = "android.permission.READ_TV_LISTINGS";
    static final String EPG = "com.android.providers.tv.permission.READ_EPG_DATA";   // some TVs want it too to show other apps' rows
    private static final int MAX_ITEMS = 15, MAX_WATCH_NEXT = 16, ART_W = 400;   // a card is about a fifth of the screen

    /** One card: what it says, its picture, and the link it opens (into its own app only, see {@link #open}). */
    static final class Item {
        String title = "", meta = "", pkg, link;
        File art;
        Bitmap picture;   // decoded off the main thread, small (RGB_565)
        float progress = -1f;
    }

    /** A row ready for the screen. */
    static final class Row {
        String key, title;
        final List<Item> items = new ArrayList<>();
    }

    private TvRows() { }

    static boolean allowed(Context c) { return c.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED; }

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("tvrows", Context.MODE_PRIVATE); }

    /** Every row there is (for «صفوف القنوات»), arranged and marked shown or not, without their cards. */
    static List<TvRowsPick.Row> arrange(Context c) {
        if (!allowed(c)) return new ArrayList<>();
        SharedPreferences p = prefs(c);
        return TvRowsPick.arrange(found(c), watchNext(c, false).size(), p.getString("order", null), p.getString("shown", null));
    }

    /** The owner's choice, kept. */
    static void save(Context c, List<TvRowsPick.Row> rows) {
        String[] s = TvRowsPick.save(rows);
        prefs(c).edit().putString("order", s[0]).putString("shown", s[1]).apply();
    }

    /** The shown rows with their cards, in the owner's order; empty without the permission. */
    static List<Row> load(Context c) {
        List<Row> out = new ArrayList<>();
        if (!allowed(c)) return out;
        File dir = new File(c.getFilesDir(), "tvrows");
        Set<String> keep = new HashSet<>();
        List<Item> next = watchNext(c, true);
        SharedPreferences p = prefs(c);
        List<TvRowsPick.Found> found = found(c);
        for (TvRowsPick.Row r : TvRowsPick.arrange(found, next.size(), p.getString("order", null), p.getString("shown", null))) {
            if (!r.shown) continue;
            Row row = new Row();
            row.key = r.key;
            row.title = r.title;
            if (TvRowsPick.WATCH_NEXT.equals(r.key)) row.items.addAll(next);
            else readItems(c, r, idOf(found, r.key), row);
            for (Item it : row.items) {
                if (it.art == null) continue;
                keep.add(it.art.getName());
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inPreferredConfig = Bitmap.Config.RGB_565;   // half the memory; a card has no transparency to keep
                it.picture = BitmapFactory.decodeFile(it.art.getPath(), o);
            }
            if (!row.items.isEmpty()) out.add(row);
        }
        for (Item it : next) if (it.art != null) keep.add(it.art.getName());
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) if (!keep.contains(f.getName())) f.delete();
        return out;
    }

    private static final java.util.Map<String, Long> IDS = new java.util.HashMap<>();

    private static long idOf(List<TvRowsPick.Found> unused, String key) {
        synchronized (IDS) { Long id = IDS.get(key); return id == null ? -1 : id; }
    }

    /** The preview channels with something in them, from apps that are on the TV. */
    private static List<TvRowsPick.Found> found(Context c) {
        List<TvRowsPick.Found> out = new ArrayList<>();
        PackageManager pm = c.getPackageManager();
        String[] cols = {TvContract.Channels._ID, TvContract.Channels.COLUMN_PACKAGE_NAME, TvContract.Channels.COLUMN_DISPLAY_NAME,
                TvContract.Channels.COLUMN_TYPE, TvContract.Channels.COLUMN_BROWSABLE};
        java.util.Map<String, Integer> best = new java.util.HashMap<>();
        try (Cursor cur = c.getContentResolver().query(TvContract.Channels.CONTENT_URI, cols, null, null, null)) {
            while (cur != null && cur.moveToNext()) {
                if (!TvContract.Channels.TYPE_PREVIEW.equals(cur.getString(3))) continue;
                String pkg = cur.getString(1), name = cur.getString(2);
                if (pkg == null || name == null || pm.getLeanbackLaunchIntentForPackage(pkg) == null) continue;
                long id = cur.getLong(0);
                int count = count(c, id);
                if (count == 0) continue;
                String key = pkg + "|" + name;
                Integer had = best.get(key);
                if (had == null || count > had) { best.put(key, count); synchronized (IDS) { IDS.put(key, id); } }
                out.add(new TvRowsPick.Found(pkg, name, label(pm, pkg), count, !cur.isNull(4) && cur.getInt(4) == 1));
            }
        } catch (RuntimeException e) {
            // no permission, or the TV has no TV provider: no rows
        }
        return out;
    }

    private static int count(Context c, long id) {
        try (Cursor cur = c.getContentResolver().query(TvContract.buildPreviewProgramsUriForChannel(id),
                new String[]{TvContract.PreviewPrograms._ID}, null, null, null)) {
            return cur == null ? 0 : cur.getCount();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static void readItems(Context c, TvRowsPick.Row r, long id, Row row) {
        if (id < 0) return;
        boolean latin = Digits.latin();
        String app = label(c.getPackageManager(), r.pkg);
        String[] cols = {TvContract.PreviewPrograms.COLUMN_TITLE, TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION,
                TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI, TvContract.PreviewPrograms.COLUMN_THUMBNAIL_URI,
                TvContract.PreviewPrograms.COLUMN_INTENT_URI, TvContract.PreviewPrograms.COLUMN_BROWSABLE,
                TvContract.PreviewPrograms.COLUMN_SEASON_DISPLAY_NUMBER, TvContract.PreviewPrograms.COLUMN_EPISODE_DISPLAY_NUMBER,
                TvContract.PreviewPrograms.COLUMN_EPISODE_TITLE};
        File dir = new File(c.getFilesDir(), "tvrows");
        try (Cursor cur = c.getContentResolver().query(TvContract.buildPreviewProgramsUriForChannel(id), cols, null, null, null)) {
            while (cur != null && cur.moveToNext() && row.items.size() < MAX_ITEMS) {
                if (!cur.isNull(5) && cur.getInt(5) == 0) continue;
                Item it = new Item();
                it.pkg = r.pkg;
                it.title = str(cur, 0);
                it.meta = TvRowsPick.meta(str(cur, 6), str(cur, 7), str(cur, 8), it.title, str(cur, 1), r.title.equals(app) ? "" : app, latin);
                it.link = str(cur, 4).isEmpty() ? null : str(cur, 4);
                String art = !str(cur, 3).isEmpty() ? str(cur, 3) : str(cur, 2);
                it.art = art.isEmpty() ? null : cached(c, dir, art);
                row.items.add(it);
            }
        } catch (RuntimeException e) {
            // the row went while it was read: what was read stays
        }
    }

    /** «أكمل المشاهدة»: continue and next-episode entries, newest first (TvRowsPick.watchNextShown); art only when asked. */
    private static List<Item> watchNext(Context c, boolean withArt) {
        List<Item> out = new ArrayList<>();
        PackageManager pm = c.getPackageManager();
        boolean latin = Digits.latin();
        File dir = new File(c.getFilesDir(), "tvrows");
        String[] cols = {TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME, TvContract.WatchNextPrograms.COLUMN_TITLE,
                TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE, TvContract.WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER,
                TvContract.WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER, TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE,
                TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS, TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS,
                TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI, TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI,
                TvContract.WatchNextPrograms.COLUMN_INTENT_URI, TvContract.WatchNextPrograms.COLUMN_BROWSABLE};
        try (Cursor cur = c.getContentResolver().query(TvContract.WatchNextPrograms.CONTENT_URI, cols, null, null,
                TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS + " DESC")) {
            while (cur != null && cur.moveToNext() && out.size() < MAX_WATCH_NEXT) {
                String pkg = cur.getString(0);
                int type = cur.isNull(5) ? -1 : cur.getInt(5);
                boolean browsable = cur.isNull(11) || cur.getInt(11) != 0;
                if (!TvRowsPick.watchNextShown(type, browsable, pkg != null && pm.getLeanbackLaunchIntentForPackage(pkg) != null)) continue;
                Item it = new Item();
                it.pkg = pkg;
                it.title = str(cur, 1);
                it.meta = TvRowsPick.meta(str(cur, 3), str(cur, 4), str(cur, 2), it.title, "", label(pm, pkg), latin);
                it.progress = TvRowsPick.progress(cur.isNull(6) ? 0 : cur.getLong(6), cur.isNull(7) ? 0 : cur.getLong(7));
                it.link = str(cur, 10).isEmpty() ? null : str(cur, 10);
                if (withArt) {
                    String art = !str(cur, 8).isEmpty() ? str(cur, 8) : str(cur, 9);
                    it.art = art.isEmpty() ? null : cached(c, dir, art);
                }
                out.add(it);
            }
        } catch (RuntimeException e) {
            // no permission, or no TV provider: an empty «أكمل المشاهدة» (not shown)
        }
        return out;
    }

    /**
     * Opens a card: its link when it goes into the app that published the card (TvRowsPick.linkAllowed), else that app itself.
     * True when something was started.
     */
    static boolean open(Context c, Item it) {
        if (it.pkg == null) return false;
        try {
            if (it.link != null) {
                Intent i = Intent.parseUri(it.link, Intent.URI_INTENT_SCHEME);
                String comp = i.getComponent() == null ? null : i.getComponent().getPackageName();
                if (TvRowsPick.linkAllowed(it.pkg, i.getPackage(), comp)) {
                    i.setSelector(null);
                    if (comp == null) i.setPackage(it.pkg);   // the link opens inside its own app, never something else that answers it
                    c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    return true;
                }
            }
        } catch (Exception e) {
            // a bad or refused link: the app itself instead
        }
        Intent app = c.getPackageManager().getLeanbackLaunchIntentForPackage(it.pkg);
        if (app == null) return false;
        try {
            c.startActivity(app.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** The picture as a small JPEG (PNG when it has transparency) on disk, at most ART_W wide; each address once. */
    private static File cached(Context c, File dir, String uri) {
        String base = Integer.toHexString(uri.hashCode()) + "_" + ART_W;
        File jpg = new File(dir, base + ".jpg"), png = new File(dir, base + ".png");
        if (jpg.isFile()) return jpg;
        if (png.isFile()) return png;
        if (!dir.isDirectory() && !dir.mkdirs()) return null;
        try (InputStream in = open(c.getContentResolver(), uri)) {
            if (in == null) return null;
            byte[] bytes = Bounded.readAll(in, Bounded.IMAGE_MAX);   // a picture, not whatever the address sends
            Bitmap b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (b == null) return null;
            if (b.getWidth() > ART_W) b = Bitmap.createScaledBitmap(b, ART_W, Math.max(1, b.getHeight() * ART_W / b.getWidth()), true);
            boolean alpha = b.hasAlpha();
            File done = alpha ? png : jpg, part = new File(dir, done.getName() + ".part");
            try (OutputStream out = new FileOutputStream(part)) {
                b.compress(alpha ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, 85, out);
            }
            return part.renameTo(done) ? done : null;
        } catch (Exception e) {
            return null;   // no picture: the card shows its title on its own colour
        }
    }

    private static InputStream open(ContentResolver cr, String uri) throws Exception {
        if (uri.startsWith("https://") || uri.startsWith("http://")) {
            HttpURLConnection h = (HttpURLConnection) new URL(uri).openConnection();
            h.setConnectTimeout(12_000);
            h.setReadTimeout(15_000);
            return h.getInputStream();
        }
        if (uri.startsWith("content://") || uri.startsWith("android.resource://")) return cr.openInputStream(Uri.parse(uri));
        return null;   // a file:// or anything else: not ours to read
    }

    private static String str(Cursor c, int i) {
        String s = c.isNull(i) ? null : c.getString(i);
        return s == null || "null".equals(s) ? "" : s.trim();
    }

    static String label(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString().trim();
        } catch (Exception e) {
            return pkg;
        }
    }
}
