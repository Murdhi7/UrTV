package sa.urtv.home;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Types the phone remote's text into ANOTHER app: the text field of the app in front is found and set through this accessibility service. A
 * service of its own, apart from the sleep timer's (SleepService, which reads nothing): this one can read the screen, so the owner turns it
 * on on its own (the TV's accessibility settings, or optimize/dock.sh type-setup). It listens to no events; it looks at the screen only at the
 * moment a phone sends text, only to find the field, and nothing it sees is kept, logged or sent back (the phone gets one fixed word).
 */
public class TypeService extends AccessibilityService {
    private static volatile TypeService running;

    @Override protected void onServiceConnected() { running = this; }
    @Override public boolean onUnbind(android.content.Intent i) { running = null; return super.onUnbind(i); }
    @Override public void onAccessibilityEvent(AccessibilityEvent e) { }
    @Override public void onInterrupt() { }

    /** Is the service on? (the owner or ADB enabled it, and Android has started it) */
    static boolean enabled() { return running != null; }

    /** The words typeText answers with; the phone gets nothing else of what it saw. */
    static final String TYPED = "typed_other", TYPED_NO_SEARCH = "typed_other_nosearch", OFF = "service_off", LOCKED = "locked", REFUSED = "refused",
        NO_FIELD = "no_field", PASSWORD = "password_field", FAILED = "failed";

    /** One node of the real screen, for TypeInto. A budget keeps a huge tree from holding the call up. */
    private static final class Wrapped implements TypeInto.Node {
        final AccessibilityNodeInfo info;
        private final int[] budget;
        private final int depth;
        Wrapped(AccessibilityNodeInfo info, int[] budget, int depth) { this.info = info; this.budget = budget; this.depth = depth; }
        @Override public boolean editable() { return info.isEditable(); }
        @Override public boolean focused() { return info.isFocused(); }
        @Override public boolean password() { return info.isPassword(); }
        @Override public boolean visible() { return info.isVisibleToUser(); }
        @Override public List<TypeInto.Node> children() {
            List<TypeInto.Node> out = new ArrayList<>();
            if (depth >= 40) return out;
            for (int i = 0; i < info.getChildCount() && budget[0] > 0; i++) {
                AccessibilityNodeInfo c = info.getChild(i);
                if (c == null) continue;
                budget[0]--;
                out.add(new Wrapped(c, budget, depth + 1));
            }
            return out;
        }
    }

    /**
     * Types the phone's text into the text field of the app in front: the focused editable field, else the first one on screen. Only when the service is
     * on, and never into an app the home screen would ask the PIN for (or, in kids mode, hide), nor into a password field. "append" adds to what the
     * field holds, otherwise the text replaces it; "search" then presses the keyboard's action (Android 11 and later). Answers one of the words above.
     */
    static String typeText(Context c, String text, boolean append, boolean search) {
        TypeService s = running;
        AccessibilityNodeInfo root = s == null ? null : s.getRootInActiveWindow();
        String pkg = root == null || root.getPackageName() == null ? null : root.getPackageName().toString();
        ParentalPolicy.State st = Parental.state(c);
        boolean ours = pkg != null && (pkg.equals(c.getPackageName()) || pkg.equals("sa.urtv.screensaver"));
        boolean systemScreen = pkg != null && ((Parental.needsPin(c, ParentalPolicy.TV_SETTINGS) && pkg.equals(packageOf(c, Settings.ACTION_SETTINGS)))
            || (Parental.needsPin(c, ParentalPolicy.WIFI) && pkg.equals(packageOf(c, Settings.ACTION_WIFI_SETTINGS))));
        TypeInto.Refusal r = TypeInto.allowed(s != null, pkg, ours, pkg != null && Parental.needsPin(c, pkg), pkg != null && !ParentalPolicy.visible(st, pkg), systemScreen);
        if (r == TypeInto.Refusal.SERVICE_OFF) return OFF;
        if (r == TypeInto.Refusal.LOCKED) return LOCKED;
        if (r == TypeInto.Refusal.REFUSED) return REFUSED;
        TypeInto.Node picked = TypeInto.pick(new Wrapped(root, new int[] {500}, 0));
        r = TypeInto.fieldProblem(picked);
        if (r == TypeInto.Refusal.NO_FIELD) return NO_FIELD;
        if (r == TypeInto.Refusal.PASSWORD_FIELD) return PASSWORD;
        AccessibilityNodeInfo field = ((Wrapped) picked).info;
        CharSequence now = Build.VERSION.SDK_INT >= 26 && field.isShowingHintText() ? null : field.getText();
        String out = TypeInto.compose(now == null ? null : now.toString(), text, append);
        if (out == null) return FAILED;
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, out);
        if (!field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return FAILED;
        if (!search) return TYPED;
        if (Build.VERSION.SDK_INT < 30) return TYPED_NO_SEARCH;
        return field.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId()) ? TYPED : TYPED_NO_SEARCH;
    }

    /** The package an intent opens, or null. */
    private static String packageOf(Context c, String action) {
        ResolveInfo ri = c.getPackageManager().resolveActivity(new Intent(action), 0);
        return ri == null || ri.activityInfo == null ? null : ri.activityInfo.packageName;
    }
}
