package sa.urtv.home.phone;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * «تحديث تلفزيوني», the one engine: the TV's own screen (UpdateActivity) and the phone page's button call the same code. check: reads
 * version.txt of the apk-latest release (the one optimize/install.sh uses) and decides (UpdateFlow); when there is a newer version and the
 * permission is given, it downloads the APKs and refuses any that is not the same app, not newer, or signed with another certificate than the
 * installed one (UpdateRules.verify), so «available» means every check passed. install: hands the files, the screensaver app first and the
 * launcher last (UpdateOrder), one after the other to Android's own installer (PackageInstaller sessions, their status callbacks): its confirm
 * screen opens on the TV and the owner confirms with the remote. Before the launcher is replaced (this process dies with it) the «update in
 * progress» record is written (UpdateRecords), and the next start of the launcher reads it. Nothing is installed silently, nothing is
 * uninstalled. The approach (download, check the signer, PackageInstaller) is from itsAbdulelah's pull request #10.
 */
public final class PhoneUpdater {
    public static final String ACTION_RESULT = "sa.urtv.home.PHONE_INSTALL_RESULT";
    private static final String TAG = "UrTVUpdate";

    private static final long STUCK_MS = 10 * 60_000L;   // an install screen nobody answers for ten minutes no longer blocks a new try
    private static String state = "idle", error = "", detail = "";
    private static int build;
    private static long since;
    private static final List<File> ready = new ArrayList<>();           // downloaded and checked, in install order
    private static final List<String> readyPackages = new ArrayList<>();
    private static final List<File> queue = new ArrayList<>();           // still to hand to the installer
    private static final List<String> queuePackages = new ArrayList<>();
    private static String installing = "";                              // the package whose session is open now
    private static Intent confirm;                                        // the installer's confirm screen, until its answer
    private static boolean confirmShown, visible;
    private static Runnable listener;

    private PhoneUpdater() { }

    public static synchronized Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", state);
        if (!error.isEmpty()) m.put("reason", error);
        if (!detail.isEmpty()) m.put("detail", detail);
        if (build > 0) m.put("build", (long) build);
        return m;
    }

    /** The screen showing the update wants to hear about changes (any thread); null to stop. */
    public static synchronized void setListener(Runnable r) { listener = r; }

    /** The update screen is in front: the installer's confirm screen may be opened from it. */
    public static synchronized void setVisible(boolean v) { visible = v; }

    private static void set(String s, String why, String d) {
        Runnable r;
        synchronized (PhoneUpdater.class) {
            state = s;
            error = why;
            detail = d;
            since = System.currentTimeMillis();
            r = listener;
        }
        if (r != null) r.run();
    }

    private static boolean busy() {
        return (state.equals("checking") || state.equals("downloading") || state.equals("waiting_owner")) && System.currentTimeMillis() - since < STUCK_MS;
    }

    /** The word for the state the screen shows. */
    public static synchronized boolean isBusy() { return busy(); }

    // ---- check

    public static Map<String, Object> check(final Context c) {
        synchronized (PhoneUpdater.class) {
            if (busy()) return status();
            set("checking", "", "");
        }
        final Context app = c.getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    prepare(app);
                } catch (Exception e) {
                    Log.w(TAG, "check failed: " + e);
                    set("error", "offline", "");
                }
            }
        }, "tv-update-check").start();
        return status();
    }

    private static void prepare(Context c) throws Exception {
        clearReady();
        String text;
        try {
            text = readText(UpdateRules.VERSION_URL);
        } catch (java.io.IOException e) {
            text = "";
        }
        int newest = UpdateRules.buildOf(text);
        synchronized (PhoneUpdater.class) { if (newest > 0) build = newest; }
        UpdateFlow.Verdict v = UpdateFlow.first(newest, lowestInstalled(c), canInstall(c));
        switch (v.result) {
            case OFFLINE: set("error", "offline", ""); return;
            case UP_TO_DATE: set("uptodate", "", ""); return;
            case NEED_PERMISSION: set("error", "need_permission", ""); return;
            default: break;
        }
        // newer, and allowed: what is installed here, how big the files are, is there room, then the files themselves
        List<String> have = new ArrayList<>();
        for (String[] apk : UpdateRules.APKS) if (installedCode(c, apk[0]) >= 0) have.add(apk[0]);
        List<String> order = UpdateOrder.order(have);
        long[] sizes = new long[order.size()];
        for (int i = 0; i < sizes.length; i++) {
            long n = sizeOf(UpdateRules.BASE + fileOf(order.get(i)));
            sizes[i] = n > 0 ? n : UpdateRules.MAX_APK_BYTES;   // unknown: the most a file may be
        }
        File dir = new File(c.getFilesDir(), "phone-update");
        if (!dir.isDirectory() && !dir.mkdirs()) { cannot("storage"); return; }
        clean(dir);
        v = UpdateFlow.room(wifiOrEthernet(c), dir.getUsableSpace(), sizes);
        if (v.result == UpdateFlow.Result.CANNOT) { cannot(v.reason); return; }

        set("downloading", "", "");
        Map<String, String> verdicts = new LinkedHashMap<>();
        List<File> files = new ArrayList<>();
        List<String> pkgs = new ArrayList<>();
        for (String pkg : order) {
            File f = new File(dir, fileOf(pkg));
            if (!fetch(UpdateRules.BASE + fileOf(pkg), f)) { clean(dir); set("error", "offline", ""); return; }
            String why = verify(c, pkg, f);
            verdicts.put(pkg, why);
            if (why == null) { files.add(f); pkgs.add(pkg); }
        }
        v = UpdateFlow.files(verdicts);
        if (v.result != UpdateFlow.Result.AVAILABLE) { clean(dir); cannot(v.reason); return; }
        synchronized (PhoneUpdater.class) {
            ready.clear();
            ready.addAll(files);
            readyPackages.clear();
            readyPackages.addAll(pkgs);
        }
        set("available", "", "");
    }

    /** The TV can't update itself: the screen shows the message about the AI assistant. The reason goes to the log. */
    private static void cannot(String why) {
        Log.w(TAG, "cannot update: " + why);
        set("error", "cannot", why);
    }

    private static String fileOf(String pkg) {
        for (String[] apk : UpdateRules.APKS) if (apk[0].equals(pkg)) return apk[1];
        return "";
    }

    private static synchronized void clearReady() {
        ready.clear();
        readyPackages.clear();
    }

    private static String readText(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(10000);
        con.setReadTimeout(10000);
        try {
            if (con.getResponseCode() != HttpURLConnection.HTTP_OK) return "";
            InputStream in = con.getInputStream();
            try {
                byte[] buf = new byte[64];   // "3.51\n": anything longer is not it
                int n = 0, r;
                while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
                return new String(buf, 0, n, "UTF-8");
            } finally {
                in.close();
            }
        } finally {
            con.disconnect();
        }
    }

    /** The size of a file without downloading it: the first byte only, and the total from Content-Range. -1 when the server doesn't say. */
    private static long sizeOf(String url) {
        if (!url.startsWith(UpdateRules.BASE)) return -1;
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(10000);
            con.setReadTimeout(10000);
            con.setRequestProperty("Range", "bytes=0-0");
            try {
                int code = con.getResponseCode();
                if (code == HttpURLConnection.HTTP_PARTIAL) {
                    String range = con.getHeaderField("Content-Range");   // "bytes 0-0/12345678"
                    if (range != null && range.matches("bytes 0-0/[0-9]{1,12}")) return Long.parseLong(range.substring(range.indexOf('/') + 1));
                    return -1;
                }
                return code == HttpURLConnection.HTTP_OK ? con.getContentLengthLong() : -1;
            } finally {
                con.disconnect();
            }
        } catch (Exception e) {
            return -1;
        }
    }

    // ---- install

    /** From the TV's own screen or from the phone page (fromPhone: the TV is brought to the update screen, where the owner confirms). */
    public static Map<String, Object> install(final Context c, boolean fromPhone) {
        final Context app = c.getApplicationContext();
        synchronized (PhoneUpdater.class) {
            if (busy()) return status();
            if (!state.equals("available") || ready.isEmpty()) return status();   // the checks come first: nothing is installed that wasn't checked
            if (!canInstall(app)) {
                set("error", "need_permission", "");
                return status();
            }
            queue.clear();
            queuePackages.clear();
            for (String pkg : UpdateOrder.order(readyPackages)) {
                queue.add(ready.get(readyPackages.indexOf(pkg)));
                queuePackages.add(pkg);
            }
            confirm = null;
            confirmShown = false;
            set("waiting_owner", "", "");
        }
        if (fromPhone) {
            try {
                app.startActivity(new Intent().setClassName(app.getPackageName(), "sa.urtv.home.UpdateActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (RuntimeException ignored) { }
        }
        new Thread(new Runnable() {
            @Override public void run() { installNext(app); }
        }, "tv-update").start();
        return status();
    }

    /** The TV's screen where the owner allows this app to install updates; false when it can't be opened. */
    public static boolean openPermissionScreen(Context c) {
        try {
            c.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + c.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean canInstall(Context c) {
        return Build.VERSION.SDK_INT < 26 || c.getPackageManager().canRequestPackageInstalls();
    }

    /** The permission screen could not be opened: the same message as any other way the TV can't update itself. */
    public static void permissionScreenFailed() { cannot("permission_screen"); }

    /** Wi-Fi or Ethernet with internet, not mobile data and not nothing. */
    private static boolean wifiOrEthernet(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities nc = cm.getNetworkCapabilities(n);
                if (nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) return true;
            }
        } catch (RuntimeException ignored) { }
        return false;
    }

    private static void clean(File dir) {
        File[] all = dir.listFiles();
        if (all != null) for (File f : all) f.delete();
    }

    private static boolean fetch(String url, File out) throws Exception {
        if (!url.startsWith(UpdateRules.BASE)) return false;
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(10000);
        con.setReadTimeout(30000);
        try {
            if (con.getResponseCode() != HttpURLConnection.HTTP_OK) return false;
            long len = con.getContentLength();
            if (len > UpdateRules.MAX_APK_BYTES) return false;
            long total = 0;
            InputStream in = con.getInputStream();
            OutputStream o = new FileOutputStream(out);
            try {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > UpdateRules.MAX_APK_BYTES) return false;
                    o.write(buf, 0, n);
                }
            } finally {
                o.close();
                in.close();
            }
            return total > 0;
        } finally {
            con.disconnect();
        }
    }

    // ---- the check of the file against the installed app

    private static int flags() {
        return Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES | PackageManager.GET_SIGNATURES : PackageManager.GET_SIGNATURES;
    }

    private static long code(PackageInfo i) {
        return Build.VERSION.SDK_INT >= 28 ? i.getLongVersionCode() : i.versionCode;
    }

    /** The installed version code of a package, or -1 when it isn't installed. */
    public static long installedCode(Context c, String pkg) {
        try {
            return code(c.getPackageManager().getPackageInfo(pkg, 0));
        } catch (PackageManager.NameNotFoundException e) {
            return -1;
        }
    }

    /** The version name of this app ("3.51"), for the screen. */
    public static String installedName(Context c) {
        try {
            String n = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
            return n == null ? "" : n;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    /** The lowest build among our apps that are installed: the TV is up to date only when all of them are. */
    private static long lowestInstalled(Context c) {
        long low = Long.MAX_VALUE;
        for (String[] apk : UpdateRules.APKS) {
            long k = installedCode(c, apk[0]);
            if (k >= 0 && k < low) low = k;
        }
        return low == Long.MAX_VALUE ? 0 : low;
    }

    private static Set<String> signers(PackageInfo i) throws Exception {
        Signature[] sigs = null;
        if (Build.VERSION.SDK_INT >= 28 && i.signingInfo != null) sigs = i.signingInfo.getApkContentsSigners();
        if ((sigs == null || sigs.length == 0) && i.signatures != null) sigs = i.signatures;
        Set<String> out = new HashSet<>();
        if (sigs != null) for (Signature s : sigs) out.add(UpdateRules.hex(MessageDigest.getInstance("SHA-256").digest(s.toByteArray())));
        return out;
    }

    /** Null when the file may be installed; else a word from UpdateRules.verify, or "unreadable". */
    private static String verify(Context c, String pkg, File f) {
        try {
            PackageManager pm = c.getPackageManager();
            PackageInfo archive = pm.getPackageArchiveInfo(f.getAbsolutePath(), flags());
            if (archive == null || archive.packageName == null) return "unreadable";
            PackageInfo installed = pm.getPackageInfo(pkg, flags());
            return UpdateRules.verify(pkg, archive.packageName, code(installed), code(archive), signers(installed), signers(archive));
        } catch (Exception e) {
            return "unreadable";
        }
    }

    // ---- Android's installer, one app at a time

    private static void installNext(Context c) {
        File file;
        String pkg;
        synchronized (PhoneUpdater.class) {
            if (queue.isEmpty()) {
                if (installing.isEmpty()) return;   // a late answer in a restarted process: the launcher's start reads the record and tells the owner
                installing = "";
                set("done", "", "");
                return;
            }
            file = queue.remove(0);
            pkg = queuePackages.remove(0);
            installing = pkg;
            confirm = null;
            confirmShown = false;
            set("waiting_owner", "", pkg);   // the TV shows Android's install screen: the owner confirms with the remote
        }
        // the file is checked again right before its turn: it is what gets installed
        String why = verify(c, pkg, file);
        if (why != null) { fail(c, "failed", pkg, why); return; }
        UpdateStore store = new UpdateStore(c);
        if (UpdateOrder.replacesUs(pkg)) {
            // this process dies with the launcher: the next start of the launcher reads this and tells the owner how it went
            if (!UpdateRecords.begin(store, build, pkg)) { fail(c, "failed", pkg, "record"); return; }
        }
        try {
            PackageInstaller installer = c.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(pkg);
            int id = installer.createSession(params);
            PackageInstaller.Session session = installer.openSession(id);
            try {
                OutputStream out = session.openWrite("base.apk", 0, file.length());
                InputStream in = new FileInputStream(file);
                try {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    session.fsync(out);
                } finally {
                    in.close();
                    out.close();
                }
                Intent result = new Intent(c, PhoneInstallReceiver.class).setAction(ACTION_RESULT).putExtra("pkg", pkg);
                int pi = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                session.commit(PendingIntent.getBroadcast(c, id, result, pi).getIntentSender());
            } finally {
                session.close();
            }
        } catch (Exception e) {
            Log.w(TAG, "session failed: " + e);
            fail(c, "failed", pkg, "installer");
        }
    }

    /** A failure ends the update: nothing more is handed to the installer, and the record (if one was written) is removed, since the screen says it now. */
    private static void fail(Context c, String reason, String pkg, String why) {
        new UpdateStore(c).clear();
        Log.w(TAG, "update stopped at " + pkg + ": " + why);
        synchronized (PhoneUpdater.class) {
            queue.clear();
            queuePackages.clear();
            installing = "";
            confirm = null;
        }
        set("error", reason, pkg);
    }

    /** The installer's answer (PhoneInstallReceiver): the confirm screen is needed, it went through, or it failed. */
    static void onResult(Context c, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Integer.MIN_VALUE);
        String pkg = intent.getStringExtra("pkg");
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent screen = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (screen == null) { fail(c, "failed", pkg == null ? "" : pkg, "installer"); return; }
            boolean front;
            synchronized (PhoneUpdater.class) {
                confirm = screen;
                confirmShown = false;
                front = visible;
            }
            if (front) {
                set("waiting_owner", "", pkg == null ? "" : pkg);   // the update screen opens it (UpdateActivity), from the front
            } else {
                showConfirm(c);
            }
        } else if (status == PackageInstaller.STATUS_SUCCESS) {
            installNext(c);
        } else {
            String why = UpdateFlow.failure(status);
            if (UpdateFlow.isDeclined(why)) {
                new UpdateStore(c).clear();
                synchronized (PhoneUpdater.class) { queue.clear(); queuePackages.clear(); installing = ""; confirm = null; }
                set("error", "declined", pkg == null ? "" : pkg);
            } else {
                fail(c, "failed", pkg == null ? "" : pkg, why == null ? "failed" : why);
            }
        }
    }

    /** Opens the installer's confirm screen (from the screen in front, or from the background where Android allows it). */
    public static synchronized boolean showConfirm(Context c) {
        if (confirm == null) return false;
        try {
            c.startActivity(new Intent(confirm).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            confirmShown = true;
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "confirm screen: " + e);
            return false;
        }
    }

    /** True when the installer's confirm screen is waiting and nobody has opened it yet. */
    public static synchronized boolean confirmWaiting() { return confirm != null && !confirmShown; }

    /** True when there is a confirm screen to open again (the owner left it, or Android didn't show it). */
    public static synchronized boolean confirmKnown() { return confirm != null; }

    /** Which app the installer is working on now ("" when none). */
    public static synchronized String installingNow() { return installing; }

    /** Back to the first screen after a result has been read. */
    public static synchronized void reset() {
        if (busy()) return;
        state = "idle";
        error = "";
        detail = "";
    }
}
