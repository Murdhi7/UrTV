package sa.urtv.screensaver;

/** For tests/clock_setting_test.py: the screensaver's side of «ساعة ٢٤» (ClockSetting.java) on a plain JVM. */
public final class ClockSettingRun {
    static int failed;

    static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "PASS " : "FAIL ") + name + (ok ? "" : "  -> " + detail));
        if (!ok) failed++;
    }

    public static void main(String[] args) {
        check("receiver: on and off are accepted", ClockSetting.valid("on") && ClockSetting.valid("off"), "");
        check("receiver: anything else is refused (24, ON, yes, 1, true, empty, null)",
                !ClockSetting.valid("24") && !ClockSetting.valid("ON") && !ClockSetting.valid("yes") && !ClockSetting.valid("1")
                && !ClockSetting.valid("true") && !ClockSetting.valid("") && !ClockSetting.valid(null), "");
        check("fragment: nothing while off, so the page and the URL are as they were", ClockSetting.fragment(false).isEmpty(), ClockSetting.fragment(false));
        check("fragment: &clock=24 while on", ClockSetting.fragment(true).equals("&clock=24"), ClockSetting.fragment(true));
        check("show: on and off", ClockSetting.describe(true).equals("on") && ClockSetting.describe(false).equals("off"), "");
        if (failed > 0) { System.out.println(failed + " failed"); System.exit(1); }
    }
}
