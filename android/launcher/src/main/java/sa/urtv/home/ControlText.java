package sa.urtv.home;

import java.util.Locale;

/**
 * What «مركز التحكم» says and decides, with no Android in it (tests/ControlTextRun.java runs it). Numbers are written with western
 * digits here and the screen passes them through Digits.of, like every other number of the home screen.
 */
final class ControlText {
    private ControlText() {}

    /** The panel's name: on the pill, over the panel, and in the family lock's list. */
    static final String NAME = "مركز التحكم";

    /** whole megabytes, rounded to the nearest; never below 0 (the system may take memory back while we look) */
    static long megabytes(long bytes) { return bytes <= 0 ? 0 : (bytes + 524_288L) / 1_048_576L; }

    /**
     * What «حرّر الذاكرة» says: how much the available memory grew, as the system reports it (MemoryInfo before and after), plainly
     * when nothing was freed. tried = how many apps were asked to stop.
     */
    static String freed(long beforeBytes, long afterBytes, int tried) {
        long mb = megabytes(afterBytes - beforeBytes);
        if (tried == 0) return "لا تطبيقات في الخلفية لإغلاقها";
        if (mb == 0) return "لم يتحرر شيء: الذاكرة مرتاحة";
        return "تحرر " + mb + " ميجابايت";
    }

    /** the memory the system has free now, for the tile's second line */
    static String available(long bytes) { return "المتاح " + megabytes(bytes) + " ميجابايت"; }

    /** Mbps for the person: one decimal below 100, whole above; «٫» is the decimal mark (Digits.of turns it into «.» for latin digits) */
    static String mbps(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v) || v < 0) v = 0;
        String n = v < 100 ? String.format(Locale.ROOT, "%.1f", v) : String.format(Locale.ROOT, "%.0f", v);
        return n.replace('.', '٫') + " ميجابت/ث";
    }

    /** the Wi-Fi's signal as 0..4 bars from its dBm; below -100 is 0, from -55 up is 4 (the system's own five-step scale) */
    static int signalLevel(int rssi) {
        if (rssi <= -100) return 0;
        if (rssi >= -55) return 4;
        return (int) Math.round((rssi + 100) * 4.0 / 45);
    }

    static String signal(int level) {
        switch (Math.max(0, Math.min(4, level))) {
            case 0: return "إشارة ضعيفة جدًا";
            case 1: return "إشارة ضعيفة";
            case 2: return "إشارة متوسطة";
            case 3: return "إشارة جيدة";
            default: return "إشارة ممتازة";
        }
    }

    /** the first line of the network tile: the cable, the Wi-Fi by name, or not connected (the same words as PillText.network) */
    static String network(boolean wired, boolean wifi, String ssid) { return PillText.network(wired, wifi, ssid); }

    /** the second line: the signal when it is Wi-Fi and the TV says it (rssi 0 or more = unknown), else nothing */
    static String networkDetail(boolean wired, boolean wifi, int rssi) {
        if (wired) return "اتصال ثابت";
        if (!wifi) return "افتح إعدادات الشبكة";
        return rssi >= 0 ? "افتح إعدادات الشبكة" : signal(signalLevel(rssi));
    }

    // The values of android.media.AudioDeviceInfo.TYPE_*, so the names can be tested without Android.
    static final int BUILTIN_SPEAKER = 2, WIRED_HEADSET = 3, WIRED_HEADPHONES = 4, LINE_ANALOG = 5, LINE_DIGITAL = 6, BLUETOOTH_SCO = 7,
            BLUETOOTH_A2DP = 8, HDMI = 9, HDMI_ARC = 10, USB_DEVICE = 11, USB_ACCESSORY = 12, USB_HEADSET = 22, HDMI_EARC = 29;

    /** the name of an output the TV reports, or null for one the person would not call an output (telephony, the bus, and the like) */
    static String outputName(int type) {
        switch (type) {
            case BUILTIN_SPEAKER: return "سماعات التلفزيون";
            case WIRED_HEADSET:
            case WIRED_HEADPHONES: return "سماعة بسلك";
            case LINE_ANALOG:
            case LINE_DIGITAL: return "مخرج صوت خارجي";
            case BLUETOOTH_SCO:
            case BLUETOOTH_A2DP: return "بلوتوث";
            case HDMI: return "HDMI";
            case HDMI_ARC:
            case HDMI_EARC: return "HDMI ARC (مضخم أو ساوند بار)";
            case USB_DEVICE:
            case USB_ACCESSORY:
            case USB_HEADSET: return "USB";
            default: return null;
        }
    }

    /** several outputs may be reported: the ones that have a name, once each, joined; none = the TV did not say */
    static String outputs(int[] types) {
        StringBuilder b = new StringBuilder();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (int t : types) {
            String n = outputName(t);
            if (n != null && seen.add(n)) b.append(b.length() == 0 ? "" : "، ").append(n);
        }
        return b.length() == 0 ? "لا يُعرف" : b.toString();
    }

    // ---- «حرّر الذاكرة»: which apps may be asked to stop

    /**
     * An app «حرّر الذاكرة» may ask the system to stop: not ours (the home, the screensaver, the adhan live there), not a system app, not
     * a keyboard. The system itself only stops what is in the background; this decides who is asked at all.
     */
    static boolean killable(String pkg, boolean system, boolean keyboard) {
        if (pkg == null || pkg.isEmpty() || system || keyboard) return false;
        if (pkg.equals("android") || pkg.startsWith("sa.urtv.") || pkg.startsWith("com.android.") || pkg.startsWith("android.")) return false;
        String p = pkg.toLowerCase(Locale.ROOT);
        return !(p.contains("inputmethod") || p.contains("keyboard") || p.contains("setupwraith") || p.contains("tvlauncher") || p.contains("leanbacklauncher"));
    }
}
