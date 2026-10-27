package sa.urtv.home;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

/**
 * One read of the screensaver's ConfigProvider (same signing key) for everything the home screen's minute tick needs: the
 * city's zone, the greeting, the bar before the adhan, the prayer times' place. Each of those used to ask the provider (a
 * cross-process query) for itself, eight times a minute.
 */
final class ConfigSnapshot {
    static final Uri URI = Uri.parse("content://sa.urtv.screensaver.config");

    /** "city=..&lat=..&lon=..&tz=..&..." as the page reads it; null when the screensaver is missing or does not answer */
    final String fragment;
    /** an older screensaver does not say: on, the default */
    final boolean greetingOn, adhanBarOn, listenPillOn;
    /** «المظهر»: auto, light or dark (Look.mode); an older screensaver does not say: dark, as the home always was */
    private final String appearance;

    private ConfigSnapshot(String fragment, boolean greetingOn, boolean adhanBarOn, boolean listenPillOn, String appearance) {
        this.fragment = fragment;
        this.greetingOn = greetingOn;
        this.adhanBarOn = adhanBarOn;
        this.listenPillOn = listenPillOn;
        this.appearance = Look.mode(appearance);
    }

    String appearance() { return appearance; }

    static ConfigSnapshot read(Context c) {
        String fragment = null;
        boolean greeting = true, bar = true, listenPill = true;
        String appearance = null;
        try (Cursor cur = c.getContentResolver().query(URI, null, null, null, null)) {
            if (cur != null && cur.moveToFirst()) {
                fragment = cur.getString(0);
                int g = cur.getColumnIndex("greeting");
                if (g >= 0 && "0".equals(cur.getString(g))) greeting = false;
                int b = cur.getColumnIndex("adhan_bar");
                if (b >= 0 && "0".equals(cur.getString(b))) bar = false;
                int l = cur.getColumnIndex("listen_pill");   // an older screensaver does not say: the button is shown, as it always was
                if (l >= 0 && "0".equals(cur.getString(l))) listenPill = false;
                int a = cur.getColumnIndex("appearance");
                if (a >= 0) appearance = cur.getString(a);
            }
        } catch (RuntimeException ignored) {
            // the screensaver is missing or signed with another key: the defaults (Riyadh, on)
        }
        return new ConfigSnapshot(fragment, greeting, bar, listenPill, appearance);
    }

    /** {city, zone id}; null when the screensaver gave nothing: the TV's own zone then */
    String[] cityZone() { return fragment == null ? null : ClockZone.parse(fragment); }

    /** «&clock=24» is in the fragment only while the setting is on; an older screensaver says nothing: the 12-hour clock */
    boolean clock24() { return fragment != null && (fragment.contains("&clock=24") || fragment.startsWith("clock=24")); }

    boolean latin() { return fragment != null && (fragment.contains("&digits=latin") || fragment.startsWith("digits=latin")); }
}
