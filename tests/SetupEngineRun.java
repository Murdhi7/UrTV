package sa.urtv.home.setup;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Runs SetupEngine against a fake TV (packages, settings, the HOME role) on a plain JVM. Prints one line per scenario for tests/setup_engine_test.py. */
public class SetupEngineRun {
    /** A fake Android TV: the commands the engine sends, with the answers the real ones give. */
    static final class FakeTv implements SetupEngine.Shell {
        final Set<String> installed = new LinkedHashSet<>(), off = new LinkedHashSet<>();
        final Map<String, String> settings = new HashMap<>(), appops = new HashMap<>();
        String roleHolder = "com.google.android.apps.tv.launcherx";
        String stockPkg = "com.google.android.apps.tv.launcherx";
        String webview = "Current WebView package (name, version): (com.google.android.webview, 120.0.6099.144)";
        String sdk = "34";
        boolean silent, dashFailsMarker, wizardAnswersHome, wizardOwnsWifi, nokia, refuseSwitch, noRoleManager, emptyResolve;
        final List<String> log = new ArrayList<>();

        String home() {
            if (emptyResolve) return "";
            String h = roleHolder;
            if (h.equals("sa.urtv.home") && !off.contains("sa.urtv.home")) return refuseSwitch ? stockPkg + "/.home.HomeActivity" : "sa.urtv.home/.MainActivity";
            if (off.contains(h)) return "com.google.android.tungsten.setupwraith/.RecoveryActivity";
            return h + "/.home.HomeActivity";
        }

        public AdbClient.Result run(String command) {
            log.add(command);
            String body = command.contains("\necho URTV_RC=") ? command.substring(0, command.indexOf("\necho URTV_RC=")) : command;
            if (silent) return new AdbClient.Result("", 0);
            String out = answer(body);
            return new AdbClient.Result(out, 0);
        }

        String answer(String c) {
            if (c.startsWith("getprop ro.build.version.sdk")) return sdk + "\n";
            if (c.equals("pm list features")) return "feature:android.hardware.touchscreen\nfeature:android.software.leanback\n";
            if (c.equals("pm list packages; echo END_OF_LIST")) {
                StringBuilder b = new StringBuilder("package:android\n");
                for (String p : installed) b.append("package:").append(p).append('\n');
                return b.append("END_OF_LIST\n").toString();
            }
            if (c.equals("pm list packages -d; echo END_OF_LIST")) {
                StringBuilder b = new StringBuilder();
                for (String p : off) b.append("package:").append(p).append('\n');
                return dashFailsMarker ? b.toString() : b.append("END_OF_LIST\n").toString();
            }
            if (c.startsWith("pm disable-user --user 0 ")) {
                String p = c.substring(c.lastIndexOf(' ') + 1);
                if (nokia && p.equals(stockPkg)) return "java.lang.SecurityException: This command is illegal\n";
                if (installed.contains(p) && !p.equals("sa.urtv.home")) off.add(p);
                return "Package " + p + " new state: disabled-user\n";
            }
            if (c.startsWith("pm enable ")) { String p = c.substring(c.lastIndexOf(' ') + 1); off.remove(p); return "Package " + p + " new state: enabled\n"; }
            if (c.startsWith("pm grant ") || c.startsWith("appops set sa.urtv.home SCHEDULE")) return "";
            if (c.startsWith("appops get sa.urtv.home REQUEST_INSTALL_PACKAGES")) return appops.containsKey("REQUEST_INSTALL_PACKAGES") ? "REQUEST_INSTALL_PACKAGES: " + appops.get("REQUEST_INSTALL_PACKAGES") + "\n" : "No operations.\n";
            if (c.startsWith("appops set sa.urtv.home REQUEST_INSTALL_PACKAGES ")) { appops.put("REQUEST_INSTALL_PACKAGES", c.substring(c.lastIndexOf(' ') + 1)); return ""; }
            if (c.startsWith("dumpsys webviewupdate")) return webview + "\n";
            if (c.startsWith("settings get ")) { String[] w = c.split(" "); return settings.getOrDefault(w[2] + "/" + w[3], "null") + "\n"; }
            if (c.startsWith("settings put ")) { String[] w = c.split(" "); settings.put(w[2] + "/" + w[3], w[4]); return ""; }
            if (c.startsWith("settings delete ")) { String[] w = c.split(" "); settings.remove(w[2] + "/" + w[3]); return ""; }
            if (c.startsWith("cmd role get-role-holders")) return noRoleManager ? "" : roleHolder + "\n";
            if (c.startsWith("cmd role add-role-holder")) { roleHolder = c.substring(c.lastIndexOf(' ') + 1); return ""; }
            if (c.startsWith("cmd package set-home-activity")) return "";
            if (c.startsWith("cmd package resolve-activity")) return home() + "\n";
            if (c.startsWith("cmd package query-activities")) return "2 activities found:\n  Activity #0:\n    priority=0\n    " + stockPkg + "/.home.HomeActivity\n"
                + (wizardAnswersHome ? "  Activity #1:\n    com.google.android.tungsten.setupwraith/.RecoveryActivity\n" : "");
            if (c.startsWith("dumpsys wifi")) return wizardOwnsWifi ? "  cname=com.google.android.tungsten.setupwraith\n" : "  cname=android.uid.system\n";
            if (c.startsWith("dumpsys activity")) return "  mResumedActivity: ActivityRecord{abc u0 " + home() + " t5}\n";
            if (c.startsWith("input keyevent") || c.startsWith("am force-stop") || c.startsWith("cmd notification")) return "";
            return "unknown command: " + c + "\n";
        }

        int count(String prefix) { int n = 0; for (String l : log) if (l.startsWith(prefix)) n++; return n; }
    }

    static final class Mem implements SetupEngine.Store {
        final Map<String, String> files = new HashMap<>();
        public String get(String name) { return files.get(name); }
        public void put(String name, String text) { files.put(name, text); }
    }

    static final class Dead implements SetupEngine.Deadman {
        int armed = 0, disarmed = 0;
        public void arm(int seconds) { armed++; }
        public void disarm() { disarmed++; }
    }

    static FakeTv tv() {
        FakeTv t = new FakeTv();
        t.installed.addAll(Arrays.asList(SetupLists.SAFE));
        t.installed.addAll(Arrays.asList("com.google.android.apps.tv.launcherx", "com.android.vending", "com.tcl.airplay2", "sa.urtv.home", "sa.urtv.screensaver",
            "com.google.android.tungsten.setupwraith", "com.google.android.leanbacklauncher.recommendations", "com.tcl.tv.tclhome_passive"));
        t.off.add("com.tcl.bi");            // already off before UrTV: never ours
        t.settings.put("secure/screensaver_components", "com.google.android.apps.tv.dreamx/.service.Dream");
        t.settings.put("secure/screensaver_enabled", "1");
        return t;
    }

    static SetupEngine engine(FakeTv t, Mem m, Dead d, List<String> said) {
        return new SetupEngine(t, m, said::add, d, ms -> { }, "2026-10-27 12:00:00");
    }

    static String snapshot(FakeTv t) {
        return new TreeSet<>(t.off) + "|" + roleHolderOf(t) + "|" + new TreeSet<>(t.settings.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(java.util.stream.Collectors.toList()))
            + "|" + (t.appops.getOrDefault("REQUEST_INSTALL_PACKAGES", "default").equals("default") ? "" : t.appops);   // Android's own default is the same as never set
    }

    static String roleHolderOf(FakeTv t) { return t.roleHolder; }

    static void line(String s) { System.out.println(s); }

    public static void main(String[] a) throws Exception {
        // A: a healthy TV, the whole run, then the undo puts everything back
        FakeTv t = tv(); Mem m = new Mem(); Dead d = new Dead(); List<String> said = new ArrayList<>();
        String before = snapshot(t);
        SetupEngine e = engine(t, m, d, said);
        SetupEngine.Result pre = e.precheck(), gr = e.grants(), de = e.debloat(), ss = e.screensaver(), ho = e.home();
        line("A_steps " + pre.status + " " + gr.status + " " + de.status + " " + ss.status + " " + ho.status);
        TreeSet<String> ledger = new TreeSet<>(SetupEngine.ledgerNames(m.get("disabled-by-us.txt")));
        line("A_ledger " + String.join(",", ledger));
        line("A_journal " + m.get("home-switch.json").replace("\n", "|").replaceAll("\"written\": \"[^\"]*\"", "\"written\": \"W\""));
        line("A_home " + t.roleHolder + " " + t.home() + " armed=" + d.armed + " disarmed=" + d.disarmed + " helpersOff=" + (t.off.contains("com.tcl.tv.tclhome_passive") && t.off.contains("com.google.android.leanbacklauncher.recommendations")));
        line("A_after_off_stock " + t.off.contains("com.google.android.apps.tv.launcherx"));
        line("A_screensaver " + t.settings.get("secure/screensaver_components"));
        SetupEngine.Result un = e.undo();
        line("A_undo " + un.status + " " + t.roleHolder + " " + t.home() + " " + t.settings.get("secure/screensaver_components") + " ledger=[" + m.get("disabled-by-us.txt").trim() + "]");
        line("A_same " + (snapshot(t).equals(before)) + " " + new TreeSet<>(t.off));
        line("A_journal_state " + e.journal("state"));
        // A2: running it twice changes nothing more (the ledger keeps once each; Home is already ours)
        FakeTv t2 = tv(); Mem m2 = new Mem(); Dead d2 = new Dead(); List<String> said2 = new ArrayList<>();
        SetupEngine e2 = engine(t2, m2, d2, said2);
        e2.precheck(); e2.grants(); e2.debloat(); e2.screensaver(); e2.home();
        String ledger1 = m2.get("disabled-by-us.txt");
        e2.precheck(); e2.grants(); e2.debloat(); e2.screensaver(); SetupEngine.Result again = e2.home();
        line("A2 " + ledger1.equals(m2.get("disabled-by-us.txt")) + " " + again.status + " " + again.message.startsWith("تلفزيونك هو الشاشة الرئيسية أصلاً"));
        // B: the TV does not end the list of disabled packages: nothing is turned off, nothing remembered
        FakeTv tb = tv(); tb.dashFailsMarker = true; Mem mb = new Mem();
        SetupEngine.Result rb = engine(tb, mb, new Dead(), new ArrayList<>()).debloat();
        line("B " + rb.status + " disables=" + tb.count("pm disable-user") + " ledger=" + mb.get("disabled-by-us.txt"));
        // C: a TV that answers nothing at all: nothing counts as done, nothing is changed
        FakeTv tc = tv(); tc.silent = true; Mem mc = new Mem(); Dead dc = new Dead();
        SetupEngine ec = engine(tc, mc, dc, new ArrayList<>());
        line("C " + ec.precheck().status + " " + ec.debloat().status + " " + ec.screensaver().status + " " + ec.home().status + " " + ec.undo().status
            + " changes=" + (tc.count("pm disable-user") + tc.count("settings put") + tc.count("cmd role add") + tc.count("pm enable")) + " files=" + mc.files.keySet()
            + " armed=" + dc.armed);
        // D: Home does not open ours: the stock home is put back, the safety switch is disarmed, the journal says restored
        FakeTv td = tv(); td.refuseSwitch = true; Mem md = new Mem(); Dead dd = new Dead();
        SetupEngine ed = engine(td, md, dd, new ArrayList<>());
        SetupEngine.Result rd = ed.home();
        line("D " + rd.status + " " + td.roleHolder + " stockOff=" + td.off.contains(td.stockPkg) + " wizOff=" + td.off.contains("com.google.android.tungsten.setupwraith")
            + " armed=" + dd.armed + " disarmed=" + dd.disarmed + " state=" + ed.journal("state"));
        // E: a box that answers «illegal» to disabling its own home (Nokia): skipped, as before
        FakeTv te = tv(); te.nokia = true; Mem me = new Mem(); Dead de2 = new Dead();
        SetupEngine ee = engine(te, me, de2, new ArrayList<>());
        SetupEngine.Result re = ee.home();
        line("E " + re.status + " " + te.roleHolder + " stockOff=" + te.off.contains(te.stockPkg) + " state=" + ee.journal("state") + " disarmed=" + de2.disarmed);
        // F: the WebView is too old, or unknown: the screensaver is left alone
        FakeTv tf = tv(); tf.webview = "Current WebView package (name, version): (com.google.android.webview, 66.0.3359.158)"; Mem mf = new Mem();
        SetupEngine.Result rf = engine(tf, mf, new Dead(), new ArrayList<>()).screensaver();
        FakeTv tg = tv(); tg.webview = ""; SetupEngine.Result rg = engine(tg, new Mem(), new Dead(), new ArrayList<>()).screensaver();
        FakeTv th = tv(); th.installed.remove("sa.urtv.screensaver"); SetupEngine.Result rh = engine(th, new Mem(), new Dead(), new ArrayList<>()).screensaver();
        line("F " + rf.status + " " + rg.status + " " + rh.status + " puts=" + (tf.count("settings put") + tg.count("settings put") + th.count("settings put")));
        // G: the wizard answers Home and owns the saved Wi-Fi: it is left on
        FakeTv tw = tv(); tw.wizardAnswersHome = true; tw.wizardOwnsWifi = true;
        SetupEngine.Result rw = engine(tw, new Mem(), new Dead(), new ArrayList<>()).home();
        line("G " + rw.status + " wizardDisabled=" + tw.count("pm disable-user --user 0 com.google.android.tungsten.setupwraith"));
        FakeTv tx = tv(); tx.wizardAnswersHome = true; tx.wizardOwnsWifi = false;
        engine(tx, new Mem(), new Dead(), new ArrayList<>()).home();
        line("G2 wizardDisabled=" + tx.count("pm disable-user --user 0 com.google.android.tungsten.setupwraith"));
        // H: a switch cut off in the middle (the journal says switching, Home is the stock one): recovered; ours is home: left alone
        FakeTv tr = tv(); Mem mr = new Mem(); Dead dr = new Dead();
        SetupEngine er = engine(tr, mr, dr, new ArrayList<>());
        tr.settings.put("secure/x", "1");
        er.home();      // a good switch first
        tr.roleHolder = tr.stockPkg; tr.off.remove(tr.stockPkg);                   // ... then pretend the TV went back to the stock home with the journal at «switching»
        mr.put("home-switch.json", mr.get("home-switch.json").replaceAll("(?m)^  \"state\": \".*\",", "  \"state\": \"switching\","));
        SetupEngine.Result rr = er.recoverIfCutOff();
        line("H " + (rr == null ? "null" : rr.status) + " " + er.journal("state") + " " + tr.roleHolder);
        FakeTv ts = tv(); Mem ms = new Mem(); SetupEngine es = engine(ts, ms, new Dead(), new ArrayList<>());
        line("H2 " + (es.recoverIfCutOff() == null));
        // I: no role holder named (an older Android): the app that answers Home is the «before» only when it is the stock home; nothing readable stops the switch
        FakeTv ti = tv(); ti.noRoleManager = true; Mem mi = new Mem();
        engine(ti, mi, new Dead(), new ArrayList<>()).home();
        FakeTv tj = tv(); tj.noRoleManager = true; tj.emptyResolve = true; Mem mj = new Mem();
        SetupEngine.Result rj = engine(tj, mj, new Dead(), new ArrayList<>()).home();
        line("I " + new SetupEngine(ti, mi, s -> { }, new Dead(), ms2 -> { }, "x").journal("role_holder_before") + " " + rj.status + " changes=" + (tj.count("cmd role add") + tj.count("pm disable-user")) + " files=" + mj.files.keySet());
        // J: the lists: the safe list never holds a package of keep.txt or core.txt
        int bad = 0;
        for (String p : SetupEngine.filterSafe(SetupLists.SAFE)) {
            for (String k : SetupLists.KEEP) if (k.equals(p)) bad++;
            for (String re2 : SetupLists.CORE) if (p.matches(re2)) bad++;
        }
        line("J " + bad + " " + SetupEngine.filterSafe(SetupLists.SAFE).size() + " " + SetupLists.SAFE.length);
        // K: parsing
        line("K " + (SetupEngine.packagesOf("package:a\npackage:b\n") == null) + " " + (SetupEngine.packagesOf("package:a\r\nEND_OF_LIST\r\n").size()) + " " + (SetupEngine.packagesOf("") == null)
            + " " + SetupEngine.webviewMajor("Current WebView package (name, version): (com.google.android.webview, 120.0.6099.144)") + " " + SetupEngine.webviewMajor("nothing") + " " + SetupEngine.ledgerNames("a.b\n# no\na.b\n\nc d\ne.f\r\n"));
    }
}
