package sa.urtv.home;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Puts the TV to sleep for «مؤقت النوم». An app that is not part of the system cannot press the power key: PowerManager.goToSleep
 * needs a permission only the system holds, and DevicePolicyManager.lockNow needs a device admin that the TV does not offer. The one
 * way left that needs no root is an accessibility service: Android lets it ask for the lock-screen action, which turns the screen
 * off the way the power key does (API 28 and later). It listens to no events. Two more calls need it: Back (a global action), and typing a phone's
 * text into another app (typeText): the screen is looked at only at that moment, to find the field, and nothing of it is kept, logged or sent back.
 *
 * The person turns it on once in the TV's accessibility settings, or the agent does it over ADB (optimize/dock.sh sleep-setup).
 * Until then SleepTimer stops the sound and brings the screensaver up instead, and says so.
 */
public class SleepService extends AccessibilityService {
    private static volatile SleepService running;

    @Override protected void onServiceConnected() { running = this; }
    @Override public boolean onUnbind(android.content.Intent i) { running = null; return super.onUnbind(i); }
    @Override public void onAccessibilityEvent(AccessibilityEvent e) { }
    @Override public void onInterrupt() { }

    /** Is the service on? (the person or ADB enabled it, and Android has started it) */
    static boolean enabled() { return running != null; }

    /** The words typeText answers with; the phone gets nothing else of what it saw. */
    static final String TYPED = "typed_other", TYPED_NO_SEARCH = "typed_other_nosearch", OFF = "service_off", LOCKED = "locked",
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
        SleepService s = running;
        AccessibilityNodeInfo root = s == null ? null : s.getRootInActiveWindow();
        String pkg = root == null || root.getPackageName() == null ? null : root.getPackageName().toString();
        ParentalPolicy.State st = Parental.state(c);
        TypeInto.Refusal r = TypeInto.allowed(s != null, pkg != null && Parental.needsPin(c, pkg), pkg != null && !ParentalPolicy.visible(st, pkg));
        if (r == TypeInto.Refusal.SERVICE_OFF) return OFF;
        if (r == TypeInto.Refusal.LOCKED) return LOCKED;
        if (root == null) return NO_FIELD;
        TypeInto.Node picked = TypeInto.pick(new Wrapped(root, new int[] {3000}, 0));
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

    /** Presses Back for the phone remote (any app in front); false when the service is off. */
    static boolean back() {
        SleepService s = running;
        return s != null && s.performGlobalAction(GLOBAL_ACTION_BACK);
    }

    /** Turn the screen off now; false when the service is off or this Android is older than 9. */
    static boolean sleepNow() {
        SleepService s = running;
        return s != null && Build.VERSION.SDK_INT >= 28 && s.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }
}
