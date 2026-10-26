package sa.urtv.home;

import java.util.HashMap;
import java.util.Map;

/** For tests/family_dns_test.py: FamilyDns with a fake TV, one answer per line. */
public class FamilyDnsRun {
    static final class Fake implements FamilyDns.Ports {
        boolean grant = true, route = true, internet = true, saveOk = true, ignorePuts = false, validated = true;
        int sdk = 29;
        long checking;
        long checkingAtFirstPut = -1;
        final Map<String, String> g = new HashMap<>();
        final java.util.Set<String> unreadable = new java.util.HashSet<>();
        FamilyDns.Record rec;
        int writes;
        public boolean canWriteSecure() { return grant; }
        public boolean probe(String h, int p, int t) { return route && h.equals(FamilyDns.HOST) && p == 853; }
        public String getGlobal(String k) { return unreadable.contains(k) ? FamilyDns.UNREADABLE : g.get(k); }
        public boolean putGlobal(String k, String v) { writes++; if (checkingAtFirstPut < 0) checkingAtFirstPut = checking; if (!ignorePuts) g.put(k, v); return true; }
        public boolean deleteGlobal(String k) { writes++; g.remove(k); return true; }
        public int sdkInt() { return sdk; }
        public boolean privateDnsValidated(String h, int ms) { return validated && h.equals(FamilyDns.HOST) && ms == 12000; }
        public boolean internetWorks(int ms) { return internet && ms == 8000; }
        public long checkingSince() { return checking; }
        public boolean markChecking() { checking = 111; return true; }
        public void clearChecking() { checking = 0; }
        public FamilyDns.Record loadRecord() { return rec; }
        public boolean saveRecord(FamilyDns.Record r) { if (saveOk) rec = r; return saveOk; }
        public void clearRecord() { rec = null; }
        String state() { return g.get("private_dns_mode") + "/" + g.get("private_dns_specifier"); }
    }

    static Fake tv(String mode, String spec) {
        Fake f = new Fake();
        if (mode != null) f.g.put("private_dns_mode", mode);
        if (spec != null) f.g.put("private_dns_specifier", spec);
        return f;
    }

    public static void main(String[] a) {
        // 0 constants in one place
        System.out.println(FamilyDns.HOST + " " + FamilyDns.PORT);
        // 1 no grant
        Fake f = tv("opportunistic", null); f.grant = false;
        System.out.println(new FamilyDns(f).enable() + " " + f.writes + " " + f.state() + " " + (f.rec == null));
        // 2 no route: nothing changed, nothing recorded
        f = tv("opportunistic", null); f.route = false;
        System.out.println(new FamilyDns(f).enable() + " " + f.writes + " " + f.state() + " " + (f.rec == null));
        // 3 enable then exact restore (both originals set)
        f = tv("hostname", "dns.example");
        FamilyDns.Result r1 = new FamilyDns(f).enable();
        String on = f.state();
        FamilyDns.Result r2 = new FamilyDns(f).disable();
        System.out.println(r1 + " " + on + " " + r2 + " " + f.state() + " " + (f.rec == null));
        // 4 enable then restore where both were not set: they are deleted
        f = tv(null, null);
        new FamilyDns(f).enable();
        String on2 = f.state();
        new FamilyDns(f).disable();
        System.out.println(on2 + " " + f.g.containsKey("private_dns_mode") + " " + f.g.containsKey("private_dns_specifier") + " " + (f.rec == null));
        // 5 internet breaks: originals back at once (mode set, specifier null -> deleted)
        f = tv("off", null); f.internet = false;
        FamilyDns.Result r5 = new FamilyDns(f).enable();
        System.out.println(r5 + " " + f.state() + " " + f.g.containsKey("private_dns_specifier") + " " + (f.rec == null));
        // 6 second enable keeps the first record
        f = tv("opportunistic", "x.example");
        new FamilyDns(f).enable();
        FamilyDns.Result r6 = new FamilyDns(f).enable();
        System.out.println(r6 + " " + f.rec.mode + " " + f.rec.specifier);
        new FamilyDns(f).disable();
        System.out.println(f.state());
        // 7 unreadable value: not recorded as null, nothing changed
        f = tv("opportunistic", null); f.unreadable.add("private_dns_specifier");
        FamilyDns.Result r7 = new FamilyDns(f).enable();
        System.out.println(r7 + " " + f.writes + " " + (f.rec == null));
        // 8 value not taken (the TV ignores the write): originals put back, record cleared
        f = tv("opportunistic", "keep.example"); f.ignorePuts = true;
        FamilyDns.Result r8 = new FamilyDns(f).enable();
        System.out.println(r8 + " " + f.state() + " " + (f.rec == null));
        // 9 disable without a record changes nothing
        f = tv("hostname", "other.example");
        FamilyDns.Result r9 = new FamilyDns(f).disable();
        System.out.println(r9 + " " + f.writes + " " + f.state());
        // 10 status
        f = tv("opportunistic", null);
        FamilyDns d = new FamilyDns(f);
        String s1 = d.status().toString();
        d.enable();
        String s2 = d.status().toString();
        d.disable();
        f.unreadable.add("private_dns_mode");
        String s3 = d.status().toString();
        Fake o = tv("hostname", FamilyDns.HOST);
        System.out.println(s1 + " | " + s2 + " | " + s3 + " | " + new FamilyDns(o).status() + " | " + new FamilyDns(o).enable() + " " + o.writes + " " + (o.rec == null));
        // 11 record cannot be saved: nothing changed
        f = tv("opportunistic", null); f.saveOk = false;
        System.out.println(new FamilyDns(f).enable() + " " + f.writes);
        // 12 Arabic copy
        System.out.println(FamilyDns.arabic(FamilyDns.Result.NO_ROUTE) + " | " + FamilyDns.arabic(FamilyDns.Result.REVERTED) + " | " + FamilyDns.arabic(FamilyDns.Result.NEEDS_GRANT));
    
        // 13 Private DNS below API 28: UNSUPPORTED, nothing written, never ON
        f = tv("hostname", FamilyDns.HOST); f.sdk = 27;
        Fake f27 = tv("opportunistic", null); f27.sdk = 27;
        System.out.println(new FamilyDns(f27).enable() + " " + f27.writes + " " + (f27.rec == null) + " " + new FamilyDns(f27).supported()
            + " " + new FamilyDns(f).status() + " " + new FamilyDns(tv(null, null)).supported());
        // 14 Private DNS never validates (cached DNS would still answer): REVERTED, originals back, flag and record cleared
        f = tv("opportunistic", "x.example"); f.validated = false;
        FamilyDns.Result r14 = new FamilyDns(f).enable();
        System.out.println(r14 + " " + f.state() + " " + (f.rec == null) + " " + f.checking);
        // 15 the flag is saved before the first write and cleared after ENABLED
        f = tv("opportunistic", null);
        FamilyDns.Result r15 = new FamilyDns(f).enable();
        System.out.println(r15 + " " + f.checkingAtFirstPut + " " + f.checking + " " + (f.rec != null));
        // 16 recover: a cut-short check (flag set, record, our values on) goes back; no flag = nothing; flag without record = flag cleared
        f = tv("hostname", FamilyDns.HOST); f.rec = new FamilyDns.Record("opportunistic", null); f.checking = 5;
        FamilyDns.Result r16 = new FamilyDns(f).recover();
        Fake nf = tv("hostname", FamilyDns.HOST); nf.rec = new FamilyDns.Record("off", null);
        FamilyDns.Result r16b = new FamilyDns(nf).recover();
        Fake of = tv("hostname", "o.example"); of.checking = 5;
        FamilyDns.Result r16c = new FamilyDns(of).recover();
        System.out.println(r16 + " " + f.state() + " " + f.g.containsKey("private_dns_specifier") + " " + f.checking + " " + (f.rec == null)
            + " | " + r16b + " " + nf.writes + " | " + r16c + " " + of.checking + " " + of.writes);
        // 17 off when the value is no longer ours: nothing written, record dropped
        f = tv("opportunistic", "x.example");
        new FamilyDns(f).enable();
        f.g.put("private_dns_specifier", "mine.example");
        int w = f.writes;
        FamilyDns.Result r17 = new FamilyDns(f).disable();
        System.out.println(r17 + " " + (f.writes - w) + " " + f.state() + " " + (f.rec == null));
        // 18 Arabic copy for the new results
        System.out.println(FamilyDns.arabic(FamilyDns.Result.UNSUPPORTED) + " | " + FamilyDns.arabic(FamilyDns.Result.CHANGED_BY_SOMEONE_ELSE));
    }
}
