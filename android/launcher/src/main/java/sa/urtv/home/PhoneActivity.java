package sa.urtv.home;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import sa.urtv.home.phone.PhoneControl;
import sa.urtv.home.phone.PhoneHost;
import sa.urtv.home.phone.Pairing;
import sa.urtv.home.phone.PrivateScreen;
import sa.urtv.home.phone.QrCode;
import sa.urtv.home.phone.QrView;
import sa.urtv.home.phone.Sessions;

/**
 * «تحكم من الجوال»: the owner's switch and the screen for it. Off, it only offers «تشغيل»; nothing listens. On, it shows the QR code for the
 * pairing address (and the address with a 6-digit code under it), asks «جوال جديد يطلب التحكم» with سماح / رفض, lists «الجوالات المتصلة» with
 * إلغاء for each, and has «إيقاف التحكم من الجوال». Leaving this screen with no phone connected turns it off again. No dialogs, like
 * DockActivity: rows are built right-aligned by hand. Opened from the screensaver's settings, or `am start -n sa.urtv.home/.PhoneActivity`.
 * It is a PrivateScreen: keys sent from a phone never reach it, so a phone can't press سماح for another one.
 */
public class PhoneActivity extends Activity implements PrivateScreen {
    private static final int GOLD = 0xFFD6AF6B, INK = 0xFF04120C, TEXT = 0xFFF6EFE0, DIM = 0xFFC6BBA4, RED = 0xFFEE8B70;

    private PhoneControl control;
    private LinearLayout col;
    private TextView countdown;
    private String shownToken = "";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            update();
            main.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Digits.init(this);
        control = PhoneHost.get(this);
        control.setPinGate(new sa.urtv.home.phone.PinStoreGate(Parental.pin(this), System::currentTimeMillis));   // with a family PIN, the phone's changes ask for it (same lockout as the TV's keypad)
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF0A2016, 0xFF04120C, 0xFF020806}));
        scroll.setVerticalScrollBarEnabled(false);
        FrameLayout frame = new FrameLayout(this);
        scroll.addView(frame);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, dp(28), 0, dp(40));
        frame.addView(col, new FrameLayout.LayoutParams(dp(640), -2, Gravity.CENTER_HORIZONTAL));
        scroll.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);   // its buttons and D-pad ids are right-to-left: pinned, so a TV set to English does not mirror them
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        PhoneHost.setUi(new Runnable() {
            @Override public void run() { runOnUiThread(new Runnable() { @Override public void run() { render(); } }); }
        });
        render();
        main.postDelayed(tick, 1000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        main.removeCallbacks(tick);
        PhoneHost.setUi(null);
        // the code is shown only here: leaving with nobody connected turns it off, so nothing keeps listening for a code nobody can see
        if (control.isOn() && control.phones().isEmpty()) {
            control.withdrawOffer();
            control.stop("owner");
        }
    }

    // ---------------------------------------------------------------- screen

    private void render() {
        col.removeAllViews();
        shownToken = "";
        col.addView(text("تحكم من الجوال", 26, "Medium", TEXT, Gravity.RIGHT), right(0));
        View focus;
        if (!control.isOn()) {
            col.addView(text("يشغّل هذا الخيار خادمًا صغيرًا يعمل على شبكة الواي فاي في بيتك فقط. لا يعمل شيء حتى تضغط «تشغيل»، ويتوقف وحده عندما ينتهي آخر جوال.",
                14, "Regular", DIM, Gravity.RIGHT), right(dp(8)));
            col.addView(text("تمسح رمز الاستجابة السريعة بالجوال، ثم تضغط «سماح» هنا بالريموت.", 14, "Regular", DIM, Gravity.RIGHT), right(dp(20)));
            String why = control.lastStop();
            if (!why.isEmpty()) col.addView(text(stopText(why), 14, "Medium", GOLD, Gravity.RIGHT), right(dp(14)));
            focus = button("تشغيل", GOLD, new View.OnClickListener() {
                @Override public void onClick(View v) { turnOn(); }
            });
            col.addView(focus, button());
        } else {
            focus = renderOn();
        }
        if (focus != null) {
            final View f = focus;
            f.post(new Runnable() { @Override public void run() { f.requestFocus(); } });
        }
    }

    private View renderOn() {
        PhoneControl.Pending pending = control.pending();
        List<Sessions.Info> phones = control.phones();
        View focus = null;
        if (pending != null && pending.waiting()) {
            col.addView(text("جوال جديد يطلب التحكم", 22, "Medium", GOLD, Gravity.RIGHT), right(dp(4)));
            col.addView(text("العنوان: " + ltr(pending.ip) + ". اضغط «سماح» فقط إذا كان هذا جوالك.", 15, "Regular", TEXT, Gravity.RIGHT), right(dp(14)));
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            View deny = button("رفض", RED, new View.OnClickListener() {
                @Override public void onClick(View v) { control.deny(); }
            });
            View allow = button("سماح", GOLD, new View.OnClickListener() {
                @Override public void onClick(View v) { control.approve(); }
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(52), 1f);
            p.setMarginEnd(dp(10));   // the screen is pinned right-to-left: the end is the left edge of the right-hand button, between the two
            row.addView(allow, p);   // right-hand button first: the screen reads right to left
            row.addView(deny, new LinearLayout.LayoutParams(0, dp(52), 1f));
            col.addView(row, button());
            focus = deny;   // the safe answer is the one under a stray press
            deny.setNextFocusRightId(allow.getId());
            allow.setNextFocusLeftId(deny.getId());
        } else {
            Pairing.Offer o = control.offer();
            if (o == null) {
                col.addView(text(control.lastStop().isEmpty() ? "لا يوجد رمز جاهز." : stopText(control.lastStop()), 16, "Medium", GOLD, Gravity.RIGHT), right(dp(14)));
            } else {
                shownToken = o.token;
                col.addView(text("امسح الرمز بكاميرا الجوال", 18, "Medium", TEXT, Gravity.RIGHT), right(dp(10)));
                QrView qr = new QrView(this);
                qr.setCode(QrCode.encode(control.pairUrl(o)));
                LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(dp(300), dp(300));
                qp.gravity = Gravity.CENTER_HORIZONTAL;
                qp.bottomMargin = dp(12);
                col.addView(qr, qp);
                col.addView(text("أو افتح هذا العنوان في متصفح الجوال:", 14, "Regular", DIM, Gravity.RIGHT), right(dp(2)));
                col.addView(text(ltr(control.address() + "/p"), 20, "Medium", GOLD, Gravity.RIGHT), right(dp(6)));
                col.addView(text("واكتب الرمز: " + ltr(o.code), 20, "Medium", TEXT, Gravity.RIGHT), right(dp(4)));
                countdown = text("", 13, "Regular", DIM, Gravity.RIGHT);
                col.addView(countdown, right(dp(14)));
                update();
            }
        }

        col.addView(text("الجوالات المتصلة", 16, "Medium", GOLD, Gravity.RIGHT), right(dp(8)));
        if (phones.isEmpty()) col.addView(text("لا يوجد جوال متصل.", 14, "Regular", DIM, Gravity.RIGHT), right(dp(10)));
        int n = 0;
        for (final Sessions.Info s : phones) {
            n++;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            View cancel = button("إلغاء", RED, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    control.revoke(s.id);
                    toast("أُلغي اتصال الجوال");
                }
            });
            row.addView(cancel, new LinearLayout.LayoutParams(dp(110), dp(46)));
            TextView label = text("جوال " + Digits.of(String.valueOf(n)) + "، " + ltr(s.ip), 16, "Medium", TEXT, Gravity.RIGHT);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.rightMargin = dp(4);
            row.addView(label, lp);
            col.addView(row, rowParams());
            if (focus == null) focus = cancel;
        }
        View stop = button("إيقاف التحكم من الجوال", RED, new View.OnClickListener() {
            @Override public void onClick(View v) {
                control.withdrawOffer();
                control.stop("owner");
            }
        });
        LinearLayout.LayoutParams sp = button();
        sp.topMargin = dp(18);
        col.addView(stop, sp);
        if (focus == null) focus = stop;
        col.addView(text("الجلسة تنتهي بعد " + Digits.of("30") + " دقيقة بلا استخدام أو " + Digits.of("4") + " ساعات كحد أقصى، ولا يُحفظ شيء بعد إعادة التشغيل.", 12, "Regular", DIM, Gravity.RIGHT), right(dp(6)));
        return focus;
    }

    /** Once a second: the countdown of the code, and a new code when it ran out. */
    private void update() {
        if (!control.isOn()) return;
        Pairing.Offer o = control.offer();
        if (o == null) return;
        if (!shownToken.isEmpty() && !o.token.equals(shownToken) && control.pending() == null) { render(); return; }
        if (countdown != null) {
            long left = Math.max(0, (o.expiresAt - System.currentTimeMillis()) / 1000);
            countdown.setText("ينتهي هذا الرمز بعد " + Digits.of(String.format(java.util.Locale.ROOT, "%d:%02d", left / 60, left % 60)) + " ويتجدد وحده، ويصلح لجوال واحد مرة واحدة.");
        }
    }

    private void turnOn() {
        PhoneControl.Start r = PhoneHost.turnOn(this);
        if (r == PhoneControl.Start.NO_WIFI) toast("اتصل بشبكة واي فاي أولًا: هذا الخيار يعمل على الواي فاي فقط");
        else if (r == PhoneControl.Start.FAILED) toast("تعذّر التشغيل، حاول من جديد");
        render();
    }

    private static String stopText(String why) {
        switch (why) {
            case "locked": return "توقف التحكم من الجوال لأن الرمز أُدخل خطأ خمس مرات.";
            case "ended": return "توقف التحكم من الجوال لأن آخر جوال انتهت جلسته.";
            case "unused": return "توقف التحكم من الجوال لأنه لم يتصل أي جوال.";
            default: return "";
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Latin text (an address, a code) inside an Arabic line: kept in its own direction. */
    private static String ltr(String s) { return "⁦" + s + "⁩"; }

    private LinearLayout.LayoutParams button() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(52));
        p.bottomMargin = dp(8);
        return p;
    }

    private View button(String label, final int accent, View.OnClickListener click) {
        final TextView p = text(label, 17, "Medium", TEXT, Gravity.CENTER);
        p.setFocusable(true);
        p.setClickable(true);
        p.setId(View.generateViewId());
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        p.setBackground(bg);
        View.OnFocusChangeListener paint = new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                bg.setColor(has ? accent : 0x14FFFFFF);
                bg.setStroke(dp(2), has ? accent : 0x1FD6AF6B);
                p.setTextColor(has ? INK : TEXT);
            }
        };
        p.setOnFocusChangeListener(paint);
        paint.onFocusChange(p, false);
        p.setOnClickListener(click);
        return p;
    }

    private TextView text(String s, float sp, String weight, int color, int gravity) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(gravity);
        try { t.setTypeface(Typeface.createFromAsset(getAssets(), "fonts/Thmanyah-" + weight + ".otf")); }
        catch (RuntimeException ignored) { }
        return t;
    }

    private LinearLayout.LayoutParams right(int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = bottom;
        return p;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(8);
        return p;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
