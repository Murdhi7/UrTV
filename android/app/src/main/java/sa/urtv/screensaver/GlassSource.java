package sa.urtv.screensaver;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.View;

import java.io.InputStream;
import java.util.Calendar;

/**
 * The glass look's backdrop for this app's screens (the settings): the home app makes it once per photo and lends it through its
 * GlassProvider (signature permission sa.urtv.home.READ_GLASS). Read once per photo into one bitmap for the whole process; an
 * older home app, or none yet, gives the look's solid colour.
 */
final class GlassSource {
    private GlassSource() { }

    static final Uri BACKDROP = Uri.parse("content://sa.urtv.home.glass/backdrop");

    private static Bitmap cached;            // the one bitmap of this process
    private static long cachedStamp = -1;
    private static float scrimDark = 0.95f, scrimLight = 0.95f;
    private static int homeLight = -1;      // what the home shows: 1 light, 0 dark, -1 not known

    /** The home's answer: the stamp of its backdrop and the veils (one small query); the bitmap read again only when the stamp moved. */
    private static synchronized void refresh(Context c) {
        long stamp = 0;
        try (Cursor q = c.getContentResolver().query(BACKDROP, null, null, null, null)) {
            if (q != null && q.moveToFirst()) {
                stamp = q.getLong(0);
                scrimDark = q.getFloat(1);
                scrimLight = q.getFloat(2);
                homeLight = q.getInt(3);
            }
        } catch (RuntimeException e) {   // no home app, an older one without the provider, or no permission
            stamp = 0;
        }
        if (stamp == cachedStamp) return;
        Bitmap bm = null;
        if (stamp != 0) {
            try (InputStream in = c.getContentResolver().openInputStream(BACKDROP)) {
                bm = in == null ? null : BitmapFactory.decodeStream(in);
            } catch (java.io.IOException | RuntimeException ignored) { }
        }
        cached = bm;
        cachedStamp = bm == null ? 0 : stamp;
    }

    /** Light now? «المظهر» as kept here; automatic follows what the home shows, else the clock (6:00 to 18:30). */
    static boolean light(Context c) {
        String mode = new Prefs(c).appearance();
        if ("light".equals(mode)) return true;
        if (!"auto".equals(mode)) return false;
        if (homeLight >= 0) return homeLight == 1;
        Calendar now = Calendar.getInstance();
        int m = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        return m >= 6 * 60 && m < 18 * 60 + 30;
    }

    /** A screen's page: the backdrop under its veil, in the look of now. Returns that look, for the screen's own colours. */
    static GlassStyle.Look page(Activity a, View root) {
        refresh(a);
        boolean light = light(a);
        GlassStyle.Look l = GlassStyle.of(light);
        Bitmap bm;
        float scrim;
        synchronized (GlassSource.class) { bm = cached; scrim = light ? scrimLight : scrimDark; }
        root.setBackground(GlassPaint.page(a, bm, l, scrim));
        return l;
    }

    /** The look of now, for a dialog or a row drawn after the page. */
    static GlassStyle.Look look(Context c) { return GlassStyle.of(light(c)); }
}
