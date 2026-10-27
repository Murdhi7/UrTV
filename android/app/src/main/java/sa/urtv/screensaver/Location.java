package sa.urtv.screensaver;

import java.util.ArrayList;
import java.util.List;

/**
 * The settings' «الموقع» group without Android, so tests/PrayerMethodRun.java runs it: the country (a filter for the city list and
 * the search, told by the city's zone, never saved on its own) and the first-run note while the city is still the install's Riyadh.
 */
final class Location {
    /**
     * The countries of the city list (Cities.REGIONS), in its order: Open-Meteo's country code, the name, then the country's zones (the ones
     * Cities.java gives its cities; Palestine has two, and Morocco's search may answer Africa/El_Aaiun). The city search
     * (PrayerMethod.SEARCH_COUNTRIES) takes all of them but Palestine (see there); tests/PrayerMethodRun.java keeps the two lists in step.
     */
    static final String[][] COUNTRIES = {
        {"SA", "السعودية", "Asia/Riyadh"}, {"BH", "البحرين", "Asia/Bahrain"}, {"KW", "الكويت", "Asia/Kuwait"},
        {"QA", "قطر", "Asia/Qatar"}, {"AE", "الإمارات", "Asia/Dubai"}, {"OM", "سلطنة عُمان", "Asia/Muscat"},
        {"EG", "مصر", "Africa/Cairo"}, {"JO", "الأردن", "Asia/Amman"}, {"SY", "سوريا", "Asia/Damascus"},
        {"LB", "لبنان", "Asia/Beirut"}, {"PS", "فلسطين", "Asia/Hebron", "Asia/Gaza"},
        {"MA", "المغرب", "Africa/Casablanca", "Africa/El_Aaiun"}, {"DZ", "الجزائر", "Africa/Algiers"}, {"TN", "تونس", "Africa/Tunis"},
    };
    /** the screensaver's first starts that show the note */
    static final int NOTE_STARTS = 3;
    static final String HELP = "المدينة تحدد الوقت والطقس؛ طريقة الحساب تحدد مواقيت الصلاة";

    private Location() { }

    /** The country of a city's zone, as an index of COUNTRIES; -1 for a place of one's own elsewhere. */
    static int countryOf(String tz) {
        for (int i = 0; i < COUNTRIES.length; i++) if (inCountry(i, tz)) return i;
        return -1;
    }

    private static boolean inCountry(int country, String tz) {
        for (int k = 2; k < COUNTRIES[country].length; k++) if (COUNTRIES[country][k].equals(tz)) return true;
        return false;
    }

    /** May the city search be asked for places in that country? (All but Palestine: PrayerMethod.SEARCH_COUNTRIES.) */
    static boolean searchable(int country) { return PrayerMethod.searchable(COUNTRIES[country][0]); }

    static String countryLabel(String tz) {
        int i = countryOf(tz);
        return i < 0 ? "مكان من اختيارك" : COUNTRIES[i][1];
    }

    /** The regions of the city list in that country: Saudi Arabia's thirteen, or the one region that is the country (by its first city's zone). */
    static Cities.Region[] regions(int country) {
        List<Cities.Region> out = new ArrayList<>();
        for (Cities.Region r : Cities.REGIONS) {
            String tz = r.cities.length == 0 ? "" : r.cities[0].length > 3 ? r.cities[0][3] : "Asia/Riyadh";
            if (inCountry(country, tz)) out.add(r);
        }
        return out.toArray(new Cities.Region[0]);
    }

    /**
     * Is the note due? Only on a TV that got this app fresh (never on one that was already installed and updated: stable first), while no
     * city was ever set (from the settings or the computer), for the first three starts. The one place to change who sees it.
     */
    static boolean noteDue(boolean freshInstall, boolean chosen, int shown) { return freshInstall && !chosen && shown < NOTE_STARTS; }

    /** «المدينة: الرياض. لتغييرها: الإعدادات ثم الموقع», on the screensaver and in the settings' top row. Words, not «←»: Thmanyah has
     *  no arrows (a fallback font would draw one), and «‹›» point one way or the other as the renderer mirrors them or not. */
    static String note(String city) { return "المدينة: " + city + ". لتغييرها: الإعدادات ثم الموقع"; }
}
