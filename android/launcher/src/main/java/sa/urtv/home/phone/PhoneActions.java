package sa.urtv.home.phone;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import sa.urtv.home.tilawah.PhoneBridge;

/**
 * What the phone does on the TV, on this TV's own screens and through the same code as the TV's settings:
 *  - keys: delivered to our own screen that is in front (Android allows an app to send keys to its own windows only); volume, mute, play/pause
 *    and home go through the system's own calls. Up/down/left/right/OK/back while another app is in front are refused ("other_app").
 *  - text: into the focused text field of our own screen, or as a task when «المهام» is open; otherwise copied to the TV's clipboard.
 *  - settings: the city and the home cards through the screensaver app's provider (SaverReceiver.set), the adhan and the Quran player
 *    through tilawah.PhoneBridge (the same code as optimize/music.sh).
 */
final class PhoneActions implements Actions {
    private static final Uri SAVER = Uri.parse("content://sa.urtv.screensaver.config");

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());

    PhoneActions(Context app) { this.app = app.getApplicationContext(); }

    // ---- keys

    @Override public Result key(String key) {
        AudioManager audio = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        switch (key) {
            case "vol_up": audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); return Result.ok("done");
            case "vol_down": audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); return Result.ok("done");
            case "mute": audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI); return Result.ok("done");
            case "home":
                try {
                    app.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    return Result.ok("done");
                } catch (RuntimeException e) {
                    return Result.fail("failed");
                }
            case "play_pause":
                if (PhoneBridge.pauseOurs(app)) return Result.ok("done");
                audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
                audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
                return Result.ok("sent");   // to whatever app is playing; the system decides
            default:
                break;
        }
        final int code;
        switch (key) {
            case "up": code = KeyEvent.KEYCODE_DPAD_UP; break;
            case "down": code = KeyEvent.KEYCODE_DPAD_DOWN; break;
            case "left": code = KeyEvent.KEYCODE_DPAD_LEFT; break;
            case "right": code = KeyEvent.KEYCODE_DPAD_RIGHT; break;
            case "ok": code = KeyEvent.KEYCODE_DPAD_CENTER; break;
            case "back": code = KeyEvent.KEYCODE_BACK; break;
            default: return Result.fail("failed");
        }
        final Activity a = Foreground.current();
        if (a == null) return Result.fail("other_app");   // no screen of ours in front: Android lets an app send keys only to its own windows
        if (a instanceof PrivateScreen) return Result.fail("private_screen");
        main.post(new Runnable() {
            @Override public void run() {
                long t = SystemClock.uptimeMillis();
                a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
                a.dispatchKeyEvent(new KeyEvent(t, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0));
            }
        });
        return Result.ok("done");
    }

    // ---- text

    @Override public Result text(final String text) {
        FutureTask<String> job = new FutureTask<>(new java.util.concurrent.Callable<String>() {
            @Override public String call() {
                Activity a = Foreground.current();
                if (a != null && !(a instanceof PrivateScreen)) {
                    if (a instanceof TextTarget && ((TextTarget) a).acceptText(text)) return "task";
                    View f = a.getCurrentFocus();
                    if (f instanceof EditText) {
                        EditText e = (EditText) f;
                        Editable ed = e.getText();
                        int s = Math.max(0, e.getSelectionStart()), t = Math.max(0, e.getSelectionEnd());
                        ed.replace(Math.min(s, t), Math.max(s, t), text);
                        return "typed";
                    }
                }
                try {
                    ClipboardManager cm = (ClipboardManager) app.getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("urtv", text));
                    return "copied";
                } catch (RuntimeException e) {
                    return null;
                }
            }
        });
        main.post(job);
        try {
            String r = job.get(2, TimeUnit.SECONDS);
            return r == null ? Result.fail("failed") : Result.ok(r);
        } catch (Exception e) {
            return Result.fail("failed");
        }
    }

    // ---- state and settings

    private Bundle saver(String method, String arg, Bundle extras) {
        try {
            return app.getContentResolver().call(SAVER, method, arg, extras);
        } catch (RuntimeException e) {
            return null;   // the screensaver app is missing, older, or not signed with our key
        }
    }

    @Override public Map<String, Object> state() {
        Map<String, Object> m = new LinkedHashMap<>();
        Bundle b = saver("phone_state", null, null);
        m.put("settings", b != null);
        if (b != null) {
            m.put("city", b.getString("city", ""));
            m.put("home_cards", b.getStringArrayList("home_cards") == null ? new ArrayList<String>() : b.getStringArrayList("home_cards"));
            List<Object> panels = new ArrayList<>();
            List<String> codes = b.getStringArrayList("panels"), names = b.getStringArrayList("panel_names");
            for (int i = 0; codes != null && names != null && i < codes.size() && i < names.size(); i++) {
                if ("none".equals(codes.get(i))) continue;
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("id", codes.get(i));
                p.put("name", names.get(i));
                panels.add(p);
            }
            m.put("panels", panels);
        }
        m.putAll(PhoneBridge.state(app));
        m.put("foreground", Foreground.current() != null ? "own" : "other");
        m.put("update", PhoneUpdater.status());
        return m;
    }

    @Override public Result set(Map<String, Object> v) {
        Bundle extras = new Bundle();
        boolean city = false;
        if (v.containsKey("city")) { extras.putString("city", (String) v.get("city")); city = true; }
        if (v.containsKey("home_cards")) {
            StringBuilder cards = new StringBuilder();
            for (Object x : (List<?>) v.get("home_cards")) cards.append(cards.length() > 0 ? "," : "").append((String) x);
            extras.putString("home_panel", cards.length() == 0 ? "none" : cards.toString());
        }
        if (!extras.isEmpty()) {
            Bundle r = saver("phone_set", null, extras);
            if (r == null) return Result.fail("saver_missing");
            if (!r.getBoolean("ok")) return Result.fail("refused");
            if (city) PhoneBridge.rescheduleAdhan(app);
        }
        String why = PhoneBridge.adhan(app, v);
        return why == null ? Result.ok("saved") : Result.fail(why);
    }

    @Override public Result quran(Map<String, Object> v) {
        String why = PhoneBridge.quran(app, v);
        return why == null ? Result.ok("sent") : Result.fail(why);
    }

    @Override public List<String> cities(String query) {
        Bundle r = saver("phone_cities", query, null);
        List<String> out = r == null ? null : r.getStringArrayList("cities");
        return out == null ? new ArrayList<String>() : out;
    }

    @Override public Map<String, Object> update(String op) {
        return "install".equals(op) ? PhoneUpdater.install(app, true) : PhoneUpdater.checkVersion(app);   // «افحص التحديث» reads version.txt only; the files come with «ثبّت»
    }
}
