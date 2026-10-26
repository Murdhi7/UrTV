package sa.urtv.home;

/**
 * The master PIN and its wrong tries, over any small key-value store (Parental gives it the SharedPreferences; the test a map). Only the
 * PBKDF2 record is kept (PinCrypto). Five wrong tries in a row lock it for 1 minute, the next five for 5, the next five and every five
 * after for 15; a right PIN starts over. While it is locked a try is turned away without being looked at, so guessing during the wait
 * learns nothing. The wait is measured on the TV's clock: a clock set back is capped at the longest wait. No Android in here.
 */
final class PinStore {
    interface Backing {
        String get(String key);
        void put(String key, String value);
        void remove(String key);
    }

    static final int TRIES = 5;
    static final long[] WAIT_MS = {60_000L, 5 * 60_000L, 15 * 60_000L};
    private static final String RECORD = "pin", LOCK = "pinlock";   // LOCK is "wrong tries in a row,unlock time" in one value

    static final int OK = 0, WRONG = 1, LOCKED = 2, NOT_SET = 3;

    /** kind: one of the four above; waitMs: how long until the next try (LOCKED, or WRONG on the try that locked it); triesLeft: before the lock (WRONG) */
    static final class Result {
        final int kind;
        final long waitMs;
        final int triesLeft;
        Result(int kind, long waitMs, int triesLeft) { this.kind = kind; this.waitMs = waitMs; this.triesLeft = triesLeft; }
    }

    private final Backing store;

    PinStore(Backing store) { this.store = store; }

    synchronized boolean isSet() { return store.get(RECORD) != null; }

    /** A new PIN; the tries start over. */
    synchronized void set(String pin) {
        String record = PinCrypto.create(pin);
        store.put(RECORD, record);
        store.remove(LOCK);
    }

    /** No PIN, no tries. */
    synchronized void clear() {
        store.remove(RECORD);
        store.remove(LOCK);
    }

    /** How long until a PIN is looked at again, 0 when it is now. */
    synchronized long waitLeft(long now) {
        long until = state()[1];
        if (until <= now) return 0;
        return Math.min(until - now, WAIT_MS[WAIT_MS.length - 1]);
    }

    /** Wrong tries in a row so far. */
    synchronized int fails() { return (int) state()[0]; }

    synchronized Result verify(String pin, long now) {
        String record = store.get(RECORD);
        if (record == null) return new Result(NOT_SET, 0, 0);
        long wait = waitLeft(now);
        if (wait > 0) return new Result(LOCKED, wait, 0);
        if (PinCrypto.verify(pin, record)) {
            store.remove(LOCK);
            return new Result(OK, 0, TRIES);
        }
        int fails = (int) state()[0] + 1;
        long lock = waitFor(fails);
        store.put(LOCK, fails + "," + (lock > 0 ? now + lock : 0));
        return new Result(WRONG, lock, lock > 0 ? 0 : TRIES - fails % TRIES);
    }

    /** The wait the nth wrong try in a row brings: none until the 5th, then 1, 5 and 15 minutes, 15 from then on. */
    static long waitFor(int fails) {
        if (fails <= 0 || fails % TRIES != 0) return 0;
        return WAIT_MS[Math.min(fails / TRIES, WAIT_MS.length) - 1];
    }

    private long[] state() {
        String s = store.get(LOCK);
        if (s == null) return new long[] {0, 0};
        try {
            String[] p = s.split(",");
            return new long[] {Math.max(0, Long.parseLong(p[0])), Math.max(0, Long.parseLong(p[1]))};
        } catch (RuntimeException e) {
            return new long[] {0, 0};
        }
    }
}
