package sa.urtv.home.phone;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

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
 * «تحديث تلفزيونك» from the phone. check: reads version.txt of the apk-latest release (the one optimize/install.sh uses). install: downloads
 * the newest APKs from the same release, refuses any that is not the same app, not newer, or signed with another certificate than the
 * installed one (UpdateRules.verify), and hands the ones that pass, one after the other, to Android's own installer: its confirm screen opens
 * on the TV and the owner confirms with the remote. Nothing is installed silently, and nothing is uninstalled. The approach (download, check
 * the signer, PackageInstaller) is from itsAbdulelah's pull request #10.
 */
final class PhoneUpdater {
    static final String ACTION_RESULT = "sa.urtv.home.PHONE_INSTALL_RESULT";

    private static final long STUCK_MS = 10 * 60_000L;   // an install screen nobody answers for ten minutes no longer blocks a new try
    private static String state = "idle", error = "", detail = "";
    private static int build;
    private static long since;
    private static final List<File> queue = new ArrayList<>();
    private static final List<String> queuePackages = new ArrayList<>();

    private PhoneUpdater() { }

    static synchronized Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", state);
        if (!error.isEmpty()) m.put("reason", error);
        if (!detail.isEmpty()) m.put("detail", detail);
        if (build > 0) m.put("build", (long) build);
        return m;
    }

    private static synchronized void set(String s, String why, String d) {
        state = s;
        error = why;
        detail = d;
        since = System.currentTimeMillis();
    }

    private static boolean busy() {
        return (state.equals("checking") || state.equals("downloading") || state.equals("waiting_owner")) && System.currentTimeMillis() - since < STUCK_MS;
    }

    // ---- check

    static Map<String, Object> check(final Context c) {
        synchronized (PhoneUpdater.class) {
            if (busy()) return status();
            set("checking", "", "");
        }
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    int newest = UpdateRules.buildOf(readText(UpdateRules.VERSION_URL));
                    if (newest < 0) { set("error", "offline", ""); return; }
                    synchronized (PhoneUpdater.class) { build = newest; }
                    long mine = installedCode(c, c.getPackageName());
                    set(newest > mine ? "available" : "uptodate", "", "");
                } catch (Exception e) {
                    set("error", "offline", "");
                }
            }
        }, "phone-update-check").start();
        return status();
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

    // ---- install

    static Map<String, Object> install(final Context c) {
        synchronized (PhoneUpdater.class) {
            if (busy()) return status();
            PackageManager pm = c.getPackageManager();
            if (Build.VERSION.SDK_INT >= 26 && !pm.canRequestPackageInstalls()) {
                // the TV's own screen where the owner allows this app to install updates
                try {
                    c.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + c.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (RuntimeException ignored) { }
                set("error", "need_permission", "");
                return status();
            }
            set("downloading", "", "");
        }
        new Thread(new Runnable() {
            @Override public void run() { download(c); }
        }, "phone-update").start();
        return status();
    }

    private static void download(Context c) {
        File dir = new File(c.getFilesDir(), "phone-update");
        if (!dir.isDirectory() && !dir.mkdirs()) { set("error", "storage", ""); return; }
        List<File> ready = new ArrayList<>();
        List<String> packages = new ArrayList<>();
        try {
            for (String[] apk : UpdateRules.APKS) {
                String pkg = apk[0];
                if (installedCode(c, pkg) < 0) continue;   // not installed on this TV: nothing to update
                File f = new File(dir, apk[1]);
                if (!fetch(UpdateRules.BASE + apk[1], f)) { clean(dir); set("error", "offline", ""); return; }
                String why = verify(c, pkg, f);
                if ("not_newer".equals(why)) { f.delete(); continue; }
                if (why != null) { clean(dir); set("error", why, ""); return; }   // one that fails stops the whole update
                ready.add(f);
                packages.add(pkg);
            }
        } catch (Exception e) {
            clean(dir);
            set("error", "offline", "");
            return;
        }
        if (ready.isEmpty()) { clean(dir); set("uptodate", "", ""); return; }
        synchronized (PhoneUpdater.class) {
            queue.clear();
            queue.addAll(ready);
            queuePackages.clear();
            queuePackages.addAll(packages);
        }
        installNext(c);
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
    static long installedCode(Context c, String pkg) {
        try {
            return code(c.getPackageManager().getPackageInfo(pkg, 0));
        } catch (PackageManager.NameNotFoundException e) {
            return -1;
        }
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
            if (queue.isEmpty()) { set("done", "", ""); return; }
            file = queue.remove(0);
            pkg = queuePackages.remove(0);
            set("waiting_owner", "", pkg);   // the TV shows Android's install screen: the owner confirms with the remote
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
                Intent result = new Intent(c, PhoneInstallReceiver.class).setAction(ACTION_RESULT);
                int pi = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                session.commit(PendingIntent.getBroadcast(c, id, result, pi).getIntentSender());
            } finally {
                session.close();
            }
        } catch (Exception e) {
            synchronized (PhoneUpdater.class) { queue.clear(); queuePackages.clear(); }
            set("error", "installer", "");
        }
    }

    /** The installer's answer (PhoneInstallReceiver): the confirm screen is needed, it went through, or it failed. */
    static void onResult(Context c, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Integer.MIN_VALUE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm == null) { set("error", "installer", ""); return; }
            try {
                c.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (RuntimeException e) {
                set("error", "installer", "");
            }
        } else if (status == PackageInstaller.STATUS_SUCCESS) {
            installNext(c);
        } else {
            synchronized (PhoneUpdater.class) { queue.clear(); queuePackages.clear(); }
            set("error", status == PackageInstaller.STATUS_FAILURE_ABORTED ? "declined" : "installer", "");
        }
    }
}
