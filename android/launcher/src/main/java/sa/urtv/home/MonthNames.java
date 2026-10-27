package sa.urtv.home;

/**
 * The Gregorian month in the home screen's date line, as the screensaver's setting says: «أكتوبر» as always, or with months=levant
 * in its fragment the Levant's «تشرين الأول» (the page's LEVANT_MONTHS; tests/month_names_test.py keeps the two tables equal). Plain
 * Java: tests/MonthNamesRun.java runs it.
 */
final class MonthNames {
    private MonthNames() { }

    static final String[][] LEVANT = {
        {"يناير", "كانون الثاني"}, {"فبراير", "شباط"}, {"مارس", "آذار"}, {"أبريل", "نيسان"}, {"مايو", "أيار"}, {"يونيو", "حزيران"},
        {"يوليو", "تموز"}, {"أغسطس", "آب"}, {"سبتمبر", "أيلول"}, {"أكتوبر", "تشرين الأول"}, {"نوفمبر", "تشرين الثاني"}, {"ديسمبر", "كانون الأول"},
    };

    /** Other spellings a TV's Android may use for the same months (its locale data, not ours): each is the Levant's name too. */
    static final String[][] VARIANTS = {{"إبريل", "نيسان"}, {"ابريل", "نيسان"}, {"يونيه", "حزيران"}, {"يوليه", "تموز"}, {"اغسطس", "آب"}};

    /** Does the screensaver's fragment ask for the Levant's names (months=levant)? */
    static boolean levant(String fragment) {
        if (fragment == null) return false;
        for (String part : fragment.split("&")) if (part.equals("months=levant")) return true;
        return false;
    }

    /** The text with each Gregorian month's name in the Levant's, when asked; else as it is. */
    static String of(String text, boolean levant) {
        if (!levant || text == null) return text;
        for (String[] m : LEVANT) text = text.replace(m[0], m[1]);
        for (String[] m : VARIANTS) text = text.replace(m[0], m[1]);
        return text;
    }
}
