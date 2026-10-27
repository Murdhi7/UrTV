package sa.urtv.home;

// Adapted from talkies (https://github.com/rajupp/talkies, MIT, (c) 2026 TechPP) HomeActivity.java (its quick-settings panel: free memory,
// network, speed test, sound output), see THIRD_PARTY_NOTICES.md. The panel itself is written for this home screen.

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * «مركز التحكم»: the panel the home screen's round pill opens: the sleep timer, turn the TV off now, free memory, the network, the speed
 * test, the sound output, the screensaver and the TV's settings, as big tiles you walk with the D-pad (OK acts, Back closes, the focus goes
 * back to the pill). Opening it is behind the family lock's «@center»; the tiles that open something keep their own locks (the sleep
 * timer's, the Wi-Fi's, the screensaver's, the TV's settings') through the host.
 *
 * Nothing runs until a tile is pressed: no network traffic (the speed test starts on its OK only and is cancelled when the panel closes)
 * and no background service. The logic that can be tested without Android is ControlText and SpeedTest.
 */
final class ControlCenter {
    /** What the panel asks of the home screen: its locks, its fonts, and the two popups it already has. */
    interface Host {
        void launchGuarded(int path, Intent i, String name);
        void openSaver();           // the screensaver pill's OK, locked by «@saver»
        void openSaverSettings();   // its long OK
        void openSleep(View anchor);   // «مؤقت النوم» popup, locked by «@sleep»
        boolean saverInstalled();
        Typeface font(String family);
    }

    private static final int COLUMNS = 4;
    private static final int PANEL = 0xF0242428, FOCUSED = 0xF2FFFFFF, TILE = 0x33FFFFFF;

    private final Activity act;
    private final Host host;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final float density;
    private PopupWindow pw;
    private SpeedTest speed;      // the one running now, or null
    private boolean freeing;      // a «حرّر الذاكرة» is in progress

    ControlCenter(Activity act, Host host) {
        this.act = act;
        this.host = host;
        density = act.getResources().getDisplayMetrics().density;
    }

    boolean showing() { return pw != null && pw.isShowing(); }

    void dismiss() { if (pw != null) pw.dismiss(); }

    /** Opens the panel over the home screen; the focus returns to the anchor (the pill) when it closes. */
    void open(View anchor) {
        if (showing()) return;
        List<Tile> tiles = new ArrayList<>();

        tiles.add(tile("مؤقت النوم", sleepLine(), R.drawable.ic_sleep, () -> { dismiss(); host.openSleep(anchor); }));
        Tile off = tile("أطفئ الآن", SleepService.enabled() ? "تُطفأ الشاشة فورًا" : "الخدمة مطفأة: تظهر شاشة التوقف بدل الإطفاء. مطوّلًا: تفعيلها",
                R.drawable.ic_power, () -> { dismiss(); SleepTimer.turnOff(act); });
        off.setOnLongClickListener(v -> {   // a long OK: where the sleep service is turned on, behind the same lock as the sleep timer's own line
            dismiss();
            host.launchGuarded(ParentalPolicy.SLEEP_ACCESSIBILITY, new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), "إعدادات التلفزيون");
            return true;
        });
        tiles.add(off);
        Tile memory = tile("حرّر الذاكرة", available(), R.drawable.ic_memory, null);
        memory.action = () -> freeMemory(memory);
        tiles.add(memory);
        Tile net = tile("الشبكة", "", R.drawable.ic_wifi, () -> { dismiss(); host.launchGuarded(ParentalPolicy.PILL_WIFI, new Intent(Settings.ACTION_WIFI_SETTINGS), "واي فاي"); });
        networkLines(net);
        tiles.add(net);
        Tile test = tile("اختبار السرعة", "اضغط OK للبدء", R.drawable.ic_speed, null);
        test.action = () -> speedTest(test);
        tiles.add(test);
        tiles.add(tile("مخرج الصوت", outputLine(), R.drawable.ic_sound, () -> { dismiss(); host.launchGuarded(ParentalPolicy.PILL_TV_SETTINGS, soundIntent(), "إعدادات الصوت"); }));
        if (host.saverInstalled()) {
            Tile saver = tile("شاشة التوقف", "مطوّلًا: الإعدادات", R.drawable.ic_screensaver, () -> { dismiss(); host.openSaver(); });
            saver.setOnLongClickListener(v -> { dismiss(); host.openSaverSettings(); return true; });
            tiles.add(saver);
        }
        tiles.add(tile("الإعدادات", "إعدادات التلفزيون", R.drawable.ic_settings,
                () -> { dismiss(); host.launchGuarded(ParentalPolicy.PILL_TV_SETTINGS, new Intent(Settings.ACTION_SETTINGS), "إعدادات التلفزيون"); }));

        LinearLayout panel = new LinearLayout(act);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);   // the home is built in code: the popup's own window has no direction either
        panel.setPadding(dp(18), dp(14), dp(18), dp(18));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(22));
        bg.setColor(PANEL);
        bg.setStroke(dp(1), 0x26FFFFFF);
        panel.setBackground(bg);

        TextView title = new TextView(act);
        title.setText(ControlText.NAME);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(host.font("sans-serif-medium"));
        title.setGravity(Gravity.RIGHT);
        panel.addView(title, new LinearLayout.LayoutParams(-1, -2));
        ((LinearLayout.LayoutParams) title.getLayoutParams()).bottomMargin = dp(12);

        int screen = act.getResources().getDisplayMetrics().widthPixels;
        int tileW = Math.min(dp(170), (int) ((screen * 0.82f - dp(36) - dp(10) * (COLUMNS - 1)) / COLUMNS));
        int rowsCount = (tiles.size() + COLUMNS - 1) / COLUMNS;
        for (int r = 0; r < rowsCount; r++) {
            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < COLUMNS && r * COLUMNS + c < tiles.size(); c++) {
                Tile t = tiles.get(r * COLUMNS + c);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileW, dp(104));
                if (c > 0) lp.setMarginStart(dp(10));
                row.addView(t, lp);
            }
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-2, -2);
            if (r > 0) rlp.topMargin = dp(10);
            panel.addView(row, rlp);
        }
        // D-pad: right and left along a row (the row reads from the right: the first tile is the rightmost), up and down by row, no wrapping
        for (int i = 0; i < tiles.size(); i++) {
            Tile t = tiles.get(i);
            int col = i % COLUMNS;
            t.setNextFocusRightId(col == 0 ? t.getId() : tiles.get(i - 1).getId());
            t.setNextFocusLeftId(col == COLUMNS - 1 || i == tiles.size() - 1 ? t.getId() : tiles.get(i + 1).getId());
            t.setNextFocusUpId(i < COLUMNS ? t.getId() : tiles.get(i - COLUMNS).getId());
            t.setNextFocusDownId(i + COLUMNS < tiles.size() ? tiles.get(i + COLUMNS).getId()
                    : i / COLUMNS < (tiles.size() - 1) / COLUMNS ? tiles.get(tiles.size() - 1).getId() : t.getId());
        }

        pw = new PopupWindow(panel, -2, -2, true);
        pw.setOutsideTouchable(true);
        pw.setElevation(dp(24));
        pw.setOnDismissListener(() -> {
            if (speed != null) speed.cancel();   // nothing keeps running behind a closed panel
            speed = null;
            pw = null;
            anchor.requestFocus();
        });
        pw.showAtLocation(anchor.getRootView(), Gravity.CENTER, 0, 0);
        tiles.get(0).requestFocus();
    }

    // ---------------------------------------------------------------- tiles

    private Tile tile(String title, String value, int icon, Runnable action) {
        Tile t = new Tile(act, title, value, icon);
        t.action = action;
        t.setOnClickListener(v -> { if (t.action != null) t.action.run(); });
        return t;
    }

    /** One tile: an icon, its name and a line under it (the state, or the result); white while it has the focus. */
    private final class Tile extends LinearLayout {
        Runnable action;
        private final TextView name, value;
        private final ImageView glyph;
        private final GradientDrawable fill = new GradientDrawable();

        Tile(Context c, String title, String line, int icon) {
            super(c);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER);
            setFocusable(true);
            setClickable(true);
            setId(View.generateViewId());
            fill.setCornerRadius(dp(14));
            fill.setColor(TILE);
            setBackground(fill);
            setPadding(dp(8), dp(8), dp(8), dp(8));
            glyph = new ImageView(c);
            glyph.setImageDrawable(c.getDrawable(icon).mutate());
            addView(glyph, new LayoutParams(dp(30), dp(30)));
            name = new TextView(c);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            name.setTypeface(host.font("sans-serif-medium"));
            name.setSingleLine(true);
            name.setGravity(Gravity.CENTER);
            name.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
            addView(name, new LayoutParams(-1, -2));
            ((LayoutParams) name.getLayoutParams()).topMargin = dp(6);
            value = new TextView(c);
            value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            value.setTypeface(host.font("sans-serif"));
            value.setMaxLines(2);
            value.setEllipsize(android.text.TextUtils.TruncateAt.END);
            value.setGravity(Gravity.CENTER);
            value.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
            addView(value, new LayoutParams(-1, -2));
            name.setText(title);
            value.setText(line);
            setContentDescription(title);
            paint();
        }

        /** the line as given: a network name may hold digits, so the numbers of the other lines go through Digits.of where they are made */
        void setValue(String line) { value.setText(line); }

        @Override protected void onFocusChanged(boolean gained, int direction, android.graphics.Rect previouslyFocusedRect) {
            super.onFocusChanged(gained, direction, previouslyFocusedRect);
            paint();
        }

        private void paint() {
            boolean f = hasFocus();
            fill.setColor(f ? FOCUSED : TILE);
            name.setTextColor(f ? 0xFF111111 : Color.WHITE);
            value.setTextColor(f ? 0xFF333333 : 0xCCFFFFFF);
            glyph.getDrawable().setTint(f ? 0xFF111111 : Color.WHITE);
        }
    }

    // ---------------------------------------------------------------- what the tiles say

    private String sleepLine() {
        int left = SleepTimer.minutesLeft(act);
        return Digits.of(left > 0 ? "يعمل: باقي " + left + " د" : "١٥ إلى ٩٠ دقيقة");
    }

    private long availableBytes() {
        ActivityManager am = (ActivityManager) act.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return 0;
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.availMem;
    }

    private String available() { return Digits.of(ControlText.available(availableBytes())); }

    /** the network by name, its signal when it is Wi-Fi, as the TV reports them; read now, once (no polling) */
    private void networkLines(Tile t) {
        boolean wired = false, wifi = false;
        String ssid = null;
        int rssi = 0;
        try {
            android.net.ConnectivityManager cm = act.getSystemService(android.net.ConnectivityManager.class);
            android.net.NetworkCapabilities nc = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
            wired = nc != null && nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET);
            wifi = nc != null && !wired && nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI);
            if (wifi) {
                WifiManager wm = (WifiManager) act.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null && wm.getConnectionInfo() != null) {
                    ssid = wm.getConnectionInfo().getSSID();
                    rssi = wm.getConnectionInfo().getRssi();
                }
            }
        } catch (SecurityException ignored) { }
        t.setValue(ControlText.network(wired, wifi, ssid) + "\n" + Digits.of(ControlText.networkDetail(wired, wifi, rssi)));
    }

    private String outputLine() {
        try {
            AudioManager am = (AudioManager) act.getSystemService(Context.AUDIO_SERVICE);
            AudioDeviceInfo[] d = am == null ? new AudioDeviceInfo[0] : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            int[] types = new int[d.length];
            for (int i = 0; i < d.length; i++) types[i] = d[i].getType();
            return ControlText.outputs(types);
        } catch (RuntimeException e) {
            return ControlText.outputs(new int[0]);
        }
    }

    /** the sound settings if this TV has them (the public action), else the TV's settings: never an intent that does not resolve */
    private Intent soundIntent() {
        Intent sound = new Intent(Settings.ACTION_SOUND_SETTINGS);
        return act.getPackageManager().resolveActivity(sound, 0) != null ? sound : new Intent(Settings.ACTION_SETTINGS);
    }

    // ---------------------------------------------------------------- «حرّر الذاكرة»

    /**
     * Asks the system to stop the background processes of the other apps the person installed (never ours, a system app or a keyboard),
     * then says how much the available memory grew. killBackgroundProcesses stops only what is in the background and the system may start
     * things again, so the number is what the TV reports, rounded, and «لم يتحرر شيء» when it is nothing.
     */
    private void freeMemory(Tile t) {
        if (freeing) return;
        freeing = true;
        t.setValue("جارٍ التحرير…");
        final Context app = act.getApplicationContext();
        new Thread(() -> {
            ActivityManager am = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
            long before = availableBytes();
            int tried = 0;
            if (am != null) for (String pkg : candidates(app)) {
                try { am.killBackgroundProcesses(pkg); tried++; } catch (RuntimeException ignored) { }
            }
            try { Thread.sleep(1200); } catch (InterruptedException ignored) { }   // the system takes a moment to give it back
            long after = availableBytes();
            String said = Digits.of(ControlText.freed(before, after, tried));
            ui.post(() -> {
                freeing = false;
                if (showing()) t.setValue(said + "\n" + available());
            });
        }, "free-memory").start();
    }

    /** the apps «حرّر الذاكرة» may ask the system to stop: the launchable apps (Dock.catalogue, what the home lists) that ControlText.killable allows */
    private List<String> candidates(Context c) {
        PackageManager pm = c.getPackageManager();
        Set<String> keyboards = new HashSet<>();
        try {
            InputMethodManager imm = (InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) for (InputMethodInfo i : imm.getInputMethodList()) keyboards.add(i.getPackageName());
        } catch (RuntimeException ignored) { }
        List<String> out = new ArrayList<>();
        for (ResolveInfo ri : Dock.catalogue(c)) {
            ApplicationInfo ai = ri.activityInfo.applicationInfo;
            String pkg = ri.activityInfo.packageName;
            boolean system = (ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
            if (ControlText.killable(pkg, system, keyboards.contains(pkg)) && !out.contains(pkg)) out.add(pkg);
        }
        return out;
    }

    // ---------------------------------------------------------------- «اختبار السرعة»

    /** OK starts a download of a few MB off the main thread; OK while it runs stops it. Closing the panel stops it too. */
    private void speedTest(Tile t) {
        if (speed != null) {
            speed.cancel();
            speed = null;
            t.setValue("أُلغي · اضغط OK للبدء");
            return;
        }
        final SpeedTest test = new SpeedTest();
        speed = test;
        t.setValue("جارٍ القياس…");
        new Thread(() -> {
            String said;
            try {
                said = Digits.of(ControlText.mbps(test.downloadMbps()));
            } catch (java.io.IOException | RuntimeException e) {
                said = test.cancelled() ? null : "تعذّر القياس: تأكد من الشبكة";
            }
            final String result = said;
            ui.post(() -> {
                if (speed != test || !showing()) return;   // cancelled, or the panel closed meanwhile
                speed = null;
                t.setValue(result + "\nاضغط OK للإعادة");
            });
        }, "speed-test").start();
    }

    private int dp(float v) { return Math.round(v * density); }
}
