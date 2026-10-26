package sa.urtv.home;

import java.util.HashMap;
import java.util.Map;

/** For tests/family_dns_test.py: FamilyDns with a fake TV, one answer per line. */
public class FamilyDnsRun {
    static final class Fake implements FamilyDns.Ports {
        boolean grant = true, route = true, internet = true, saveOk = true, ignorePuts = false;
        final Map<String, String> g = new HashMap<>();
        final java.util.Set<String> unreadable = new java.util.HashSet<>();
        FamilyDns.Record rec;
        int writes;
        public boolean canWriteSecure() { return grant; }
        public boolean probe(String h, int p, int t) { return route && h.equals(FamilyDns.HOST) && p == 853; }
        public String getGlobal(String k) { return unreadable.contains(k) ? FamilyDns.UNREADABLE : g.get(k); }
        public boolean putGlobal(String k, String v) { writes++; if (!ignorePuts) g.put(k, v); return true; }
        public boolean deleteGlobal(String k) { writes++; g.remove(k); return true; }
        public boolean internetWorks(int ms) { return internet && ms == 20000; }
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
    }
}
