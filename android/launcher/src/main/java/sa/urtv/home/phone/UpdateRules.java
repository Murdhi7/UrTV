package sa.urtv.home.phone;

import java.util.Set;

/**
 * «تحديث تلفزيونك», the rules (PhoneUpdater does the downloading and the installing): where the newest APKs are, and when a downloaded one
 * may be handed to Android's installer. It must be the same app, newer than the one installed, and signed with exactly the certificate(s)
 * of the installed one. A different signer is refused: such an APK could never replace the app anyway, and one that claimed to would be an attack.
 * The idea of downloading, verifying and opening Android's own install screen is from itsAbdulelah's pull request #10. No Android in it.
 */
public final class UpdateRules {
    /** The same release optimize/install.sh downloads from (APK_TAG defaults to apk-latest there too). */
    public static final String BASE = "https://github.com/tshi-dev/UrTV/releases/download/apk-latest/";
    public static final String VERSION_URL = BASE + "version.txt";
    /** package, file: the screensaver first, the home screen last (installing it restarts this app). */
    public static final String[][] APKS = {{"sa.urtv.screensaver", "urtv-screensaver.apk"}, {"sa.urtv.home", "urtv-home.apk"}};
    public static final long MAX_APK_BYTES = 80_000_000L;

    private UpdateRules() { }

    /** The build number in version.txt ("3.51" gives 51), or -1 for anything else. */
    public static int buildOf(String versionText) {
        if (versionText == null) return -1;
        String t = versionText.trim();
        if (!t.matches("[0-9]{1,3}\\.[0-9]{1,6}")) return -1;
        return Integer.parseInt(t.substring(t.indexOf('.') + 1));
    }

    /**
     * Null when the downloaded APK may be installed, else why not: "other_app" (not the package expected), "not_newer", "unsigned" (either
     * side has no certificate), "other_key" (the certificates differ). Signers are SHA-256 digests of the certificates, in hex.
     */
    public static String verify(String expectedPackage, String archivePackage, long installedCode, long archiveCode, Set<String> installedSigners, Set<String> archiveSigners) {
        if (expectedPackage == null || !expectedPackage.equals(archivePackage)) return "other_app";
        if (archiveCode <= installedCode) return "not_newer";
        if (installedSigners == null || archiveSigners == null || installedSigners.isEmpty() || archiveSigners.isEmpty()) return "unsigned";
        for (String d : installedSigners) if (!d.matches("[0-9a-f]{64}")) return "unsigned";
        for (String d : archiveSigners) if (!d.matches("[0-9a-f]{64}")) return "unsigned";
        if (!installedSigners.equals(archiveSigners)) return "other_key";
        return null;
    }

    public static String hex(byte[] bytes) {
        StringBuilder h = new StringBuilder();
        for (byte b : bytes) h.append(String.format("%02x", b & 0xff));
        return h.toString();
    }
}
