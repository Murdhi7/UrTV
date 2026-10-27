package sa.urtv.home;

/**
 * The words of the home screen's greeting and its weather line. No Android in here, so
 * tests/greeting_test.py runs it on tests/greeting_cases.tsv.
 */
final class GreetingText {
    static final int MORNING = 0, DAY = 1, EVENING = 2, NIGHT = 3;

    private GreetingText() { }

    /** Fajr to Dhuhr, Dhuhr to Asr, Asr to Isha, and the night from Isha to the next Fajr. Times in millis. */
    static int part(long now, long fajr, long dhuhr, long asr, long isha) {
        if (now < fajr) return NIGHT;       // still the night before
        if (now < dhuhr) return MORNING;
        if (now < asr) return DAY;
        if (now < isha) return EVENING;
        return NIGHT;
    }

    static String greeting(int part) {
        switch (part) {
            case MORNING: return "صباح الخير";
            case DAY: return "طاب يومك";
            case EVENING: return "مساء الخير";
            default: return "تصبح على خير";
        }
    }

    /** Open-Meteo's WMO weather code, as a short phrase that goes before «في المدينة». */
    static String sky(int code) {
        switch (code) {
            case 0: return "سماء صافية";
            case 1: return "سماء صافية غالبًا";
            case 2: return "غائم جزئيًا";
            case 3: return "سماء غائمة";
            case 45: case 48: return "ضباب";
            case 51: case 53: case 55: case 56: case 57: return "رذاذ";
            case 61: case 63: case 80: case 81: return "أمطار";
            case 65: case 82: return "أمطار غزيرة";
            case 66: case 67: return "مطر متجمد";
            case 71: case 73: case 75: case 77: case 85: case 86: return "ثلوج";
            case 95: return "عواصف رعدية";
            case 96: case 99: return "عواصف رعدية وبرد";
            default: return "";
        }
    }

    /** PM10 or dust in µg/m³ from which the air is «غبار», and «غبار كثيف»: the page's thresholds (told beside its condition word) */
    static final double DUST = 150, HEAVY_DUST = 400;

    /**
     * The dust level to tell, given the visibility in metres (negative: not known). The air-quality model can put thousands of µg/m³
     * of dust over Riyadh for days under a clear sky with 20 km of visibility (issue #29): at 10 km or more no dust is told, under
     * 3 km it may be «كثيف», in between at most «غبار». Unknown visibility: the level as it is (the page does the same: dustWord).
     */
    static double seenDust(double dust, double vis) {
        if (vis < 0) return dust;
        if (vis >= 10000) return 0;
        return vis < 3000 ? dust : Math.min(dust, HEAVY_DUST - 1);
    }

    /** The sky's word, and beside it (not instead of it) «وغبار» / «وغبار كثيف» when the dust level (the larger of Open-Meteo's pm10 and dust) says so. */
    static String skyWord(int code, double dust) {
        String sky = sky(code);
        String d = dust >= HEAVY_DUST ? "غبار كثيف" : dust >= DUST ? "غبار" : "";
        return d.isEmpty() ? sky : sky.isEmpty() ? d : sky + " و" + d;
    }

    /** «سماء صافية في الرياض، ٣١°» (no «·» beside a digit: the TV's font draws it as ٠); a code we have no word for gives just «الرياض، ٣١°»; dust (0 when unknown) is told beside the sky: «سماء صافية وغبار في الرياض، ٣٥°». */
    static String weather(int code, double dust, String city, double temp, boolean latin) {
        String sky = skyWord(code, dust);
        String place = sky.isEmpty() ? city : sky + " في " + city;
        return place + "، " + digits(Math.round(temp) + "°", latin);
    }

    /** «الغروب ٥:٥٧ م» from "17:57" (on a 24-hour clock «الغروب ١٧:٥٧»). */
    static String sun(String label, String hhmm, boolean latin, boolean clock24) {
        return label + " " + ClockText.time(hhmm, clock24, latin);
    }

    /** Arabic-Indic digits (as the page's #digits=hindi), or the Latin ones left alone. */
    static String digits(String s, boolean latin) {
        return DigitsText.convert(s, latin);
    }
}
