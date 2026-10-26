package sa.urtv.home;

/**
 * «حماية العائلة (DNS)»: Android's Private DNS pointed at AdGuard Family, and put back exactly as it was. Pure logic (no Android
 * imports) so tests/family_dns_test.py can run it with a fake {@link Ports}; FamilyDnsAndroid is the real one.
 *
 * Rules, each one a way this could leave a TV without internet or with a lost setting:
 *  - nothing changes until HOST:PORT answers, and the two original values are recorded BEFORE the first write;
 *  - a record already there is never overwritten (it would then hold our own values, and "off" would keep them);
 *  - "was not set" (null) is not the same as "could not be read" (UNREADABLE): an unreadable value is never recorded;
 *  - "off" puts back exactly what was recorded; a null original is deleted, not written as a default;
 *  - after the change the internet must still work within INTERNET_MS, else the originals go back at once.
 */
public final class FamilyDns {
    public static final String HOST = "family.adguard-dns.com";
    public static final int PORT = 853;
    public static final int PROBE_MS = 5000;
    public static final int INTERNET_MS = 20000;
    public static final String KEY_MODE = "private_dns_mode";
    public static final String KEY_SPEC = "private_dns_specifier";
    public static final String MODE_ON = "hostname";
    /** getGlobal answer for "the value exists but could not be read" (null means "not set"). */
    public static final String UNREADABLE = "\u0000unreadable";

    /** The originals; a null field = it was not set. */
    public static final class Record {
        public final String mode, specifier;
        public Record(String mode, String specifier) { this.mode = mode; this.specifier = specifier; }
    }

    public interface Ports {
        boolean canWriteSecure();
        /** TCP connect to host:port within timeoutMs. */
        boolean probe(String host, int port, int timeoutMs);
        /** The value, null if not set, UNREADABLE if it could not be read. */
        String getGlobal(String key);
        boolean putGlobal(String key, String value);
        boolean deleteGlobal(String key);
        /** Resolve and reach connectivitycheck.gstatic.com within totalMs. */
        boolean internetWorks(int totalMs);
        /** null = nothing recorded. */
        Record loadRecord();
        boolean saveRecord(Record r);
        void clearRecord();
    }

    public enum Result { ENABLED, ALREADY_ON_BY_OTHER, NEEDS_GRANT, NO_ROUTE, UNREADABLE_VALUE, NOT_RECORDED, NOT_TAKEN, REVERTED, RESTORED, NOTHING_TO_RESTORE, RESTORE_FAILED }
    public enum State { ON, OFF, UNKNOWN }

    public static final class Status {
        public final State state;
        public final String by;   // "us" (we recorded originals), "other" (on, but not set by us), "none"
        Status(State s, String by) { this.state = s; this.by = by; }
        @Override public String toString() { return state.name().toLowerCase() + " (" + by + ")"; }
    }

    private final Ports p;
    public FamilyDns(Ports ports) { p = ports; }

    public Status status() {
        String mode = p.getGlobal(KEY_MODE), spec = p.getGlobal(KEY_SPEC);
        if (UNREADABLE.equals(mode) || UNREADABLE.equals(spec)) return new Status(State.UNKNOWN, p.loadRecord() != null ? "us" : "none");
        boolean on = MODE_ON.equals(mode) && HOST.equals(spec);
        boolean ours = p.loadRecord() != null;
        return on ? new Status(State.ON, ours ? "us" : "other") : new Status(State.OFF, "none");
    }

    public Result enable() {
        if (!p.canWriteSecure()) return Result.NEEDS_GRANT;
        if (!p.probe(HOST, PORT, PROBE_MS)) return Result.NO_ROUTE;
        Record rec = p.loadRecord();
        boolean madeRecord = false;
        if (rec == null) {
            String mode = p.getGlobal(KEY_MODE), spec = p.getGlobal(KEY_SPEC);
            if (UNREADABLE.equals(mode) || UNREADABLE.equals(spec)) return Result.UNREADABLE_VALUE;
            if (MODE_ON.equals(mode) && HOST.equals(spec)) return Result.ALREADY_ON_BY_OTHER;   // nothing of ours to put back
            rec = new Record(mode, spec);
            if (!p.saveRecord(rec)) return Result.NOT_RECORDED;
            madeRecord = true;
        }
        boolean wrote = p.putGlobal(KEY_SPEC, HOST) && p.putGlobal(KEY_MODE, MODE_ON);
        if (!wrote || !MODE_ON.equals(p.getGlobal(KEY_MODE)) || !HOST.equals(p.getGlobal(KEY_SPEC))) {
            if (restore(rec)) p.clearRecord();
            return Result.NOT_TAKEN;
        }
        if (!p.internetWorks(INTERNET_MS)) {
            if (restore(rec)) p.clearRecord();
            return Result.REVERTED;
        }
        return Result.ENABLED;
    }

    public Result disable() {
        Record rec = p.loadRecord();
        if (rec == null) return Result.NOTHING_TO_RESTORE;
        if (!restore(rec)) return Result.RESTORE_FAILED;   // the record stays: "off" can be tried again
        p.clearRecord();
        return Result.RESTORED;
    }

    /** Exactly the recorded values, mode first (the specifier alone does nothing); true only if read back equal. */
    private boolean restore(Record r) {
        boolean ok = put(KEY_MODE, r.mode);
        ok &= put(KEY_SPEC, r.specifier);
        return ok && same(p.getGlobal(KEY_MODE), r.mode) && same(p.getGlobal(KEY_SPEC), r.specifier);
    }

    private boolean put(String key, String v) { return v == null ? p.deleteGlobal(key) : p.putGlobal(key, v); }
    private static boolean same(String a, String b) { return a == null ? b == null : a.equals(b); }

    /** Plain Arabic for the person (the settings row). */
    public static String arabic(Result r) {
        switch (r) {
            case ENABLED: return "حماية العائلة مفعّلة";
            case ALREADY_ON_BY_OTHER: return "الحماية شغّالة من قبل بإعداد ثاني، ما غيّرنا شي";
            case NEEDS_GRANT: return "يحتاج تفعيل من الكمبيوتر";
            case NO_ROUTE: return "ما قدرنا نوصل لخدمة الحماية، ما تغيّر شي";
            case UNREADABLE_VALUE: return "ما قدرنا نقرأ إعداد التلفزيون الحالي، ما تغيّر شي";
            case NOT_RECORDED: return "ما قدرنا نحفظ الإعداد القديم، ما تغيّر شي";
            case NOT_TAKEN: return "الإعداد ما انحفظ فرجّعنا القديم";
            case REVERTED: return "الإنترنت وقف فرجّعنا الإعداد القديم";
            case RESTORED: return "حماية العائلة متوقفة، رجع الإعداد القديم";
            case NOTHING_TO_RESTORE: return "حماية العائلة متوقفة";
            default: return "ما قدرنا نرجّع الإعداد القديم، جرّب مرة ثانية";
        }
    }

    public static String arabic(Status s) {
        if (s.state == State.UNKNOWN) return "غير معروفة";
        return s.state == State.ON ? "مفعّلة" : "متوقفة";
    }
}
