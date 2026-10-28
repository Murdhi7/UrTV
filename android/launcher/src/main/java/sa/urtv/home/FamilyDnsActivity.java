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

/**
 * «حماية العائلة (DNS)»: one switch, with the remote. On sets Android's Private DNS to AdGuard Family after checking the service can
 * be reached. The old setting goes back by itself only during the 20-second check after switching on (if Private DNS does not
 * validate or the internet stops); after that the person turns it off from the row, and it works offline (see FamilyDns). Without
 * the permission the switch says «يحتاج تفعيل من الكمبيوتر» and does nothing; on Android below 9 (API 28) it says it is unsupported;
 * when Private DNS already points at our host without our record (set from the computer) the switch does nothing either.
 * Exported, but guarded by the signature permission sa.urtv.home.FAMILY_DNS (the screensaver has the same key). Opened from the screensaver's settings (الشاشة الرئيسية ← حماية العائلة)
 * or `optimize/dock.sh familydns`. (Not `am start` from the shell: the shell lacks the permission.) The page polls once a second only while a check is running and the screen is
 * in front.
 */
public class FamilyDnsActivity extends Activity {
    private static final int GOLD = 0xFFD6AF6B, INK = 0xFF04120C, TEXT = 0xFFF6EFE0, DIM = 0xFFC6BBA4;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = new Runnable() { public void run() { refresh(); } };
    private TextView state, message, button, hint;
    private String last = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0xFF0A2016, 0xFF04120C, 0xFF020806}));
        scroll.setVerticalScrollBarEnabled(false);
        FrameLayout frame = new FrameLayout(this);
        scroll.addView(frame);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, dp(36), 0, dp(48));
        frame.addView(col, new FrameLayout.LayoutParams(dp(600), -2, Gravity.CENTER_HORIZONTAL));

        col.addView(text("حماية العائلة (DNS)", 26, "Medium", TEXT), right(0));
        col.addView(text("تحجب المواقع غير المناسبة للعائلة على كل التلفزيون. قبل التفعيل نتأكد أن الخدمة تشتغل، وإذا وقف الإنترنت "
            + "نرجّع الإعداد القديم تلقائياً.", 14, "Regular", DIM), right(dp(14)));
        state = text("", 17, "Medium", GOLD);
        col.addView(state, right(dp(18)));

        button = text("", 18, "Medium", INK);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(16), dp(14), dp(16), dp(14));
        button.setFocusable(true);
        button.setClickable(true);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(GOLD);
        button.setBackground(bg);
        button.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { toggle(); } });
        col.addView(button, right(dp(16)));

        message = text("", 14, "Regular", DIM);
        col.addView(message, right(dp(10)));
        hint = text("يرجع الإعداد القديم تلقائياً فقط أثناء الفحص (٢٠ ثانية). بعدها توقفها من هنا، وتشتغل بدون إنترنت.", 13, "Regular", DIM);
        col.addView(hint, right(0));
        setContentView(scroll);
        button.requestFocus();
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    @Override protected void onPause() {
        super.onPause();
        handler.removeCallbacks(poll);
    }

    private void toggle() {
        if (!FamilyDnsAndroid.supported(this) || !new FamilyDnsAndroid(this).canWriteSecure() || FamilyDnsAndroid.busy()) return;
        FamilyDns.Status now = FamilyDnsAndroid.status(this);
        if (now.state == FamilyDns.State.ON && "other".equals(now.by)) return;   // set from the computer: no second record
        final boolean enable = now.state != FamilyDns.State.ON;
        FamilyDnsAndroid.run(this, enable, new FamilyDnsAndroid.Done() {
            public void done(final FamilyDns.Result r) {
                runOnUiThread(new Runnable() { public void run() { last = FamilyDns.arabic(r); refresh(); } });
            }
        });
        refresh();
    }

    private void refresh() {
        handler.removeCallbacks(poll);
        if (!FamilyDnsAndroid.supported(this)) {
            state.setText("غير مدعومة في هذا التلفزيون (أندرويد قديم)");
            button.setText("غير متاح");
            button.setAlpha(.5f);
            message.setText("");
            return;
        }
        if (!new FamilyDnsAndroid(this).canWriteSecure()) {
            state.setText("يحتاج تفعيل من الكمبيوتر");
            button.setText("غير متاح");
            button.setAlpha(.5f);
            message.setText("اطلب من وكيلك تشغيل: optimize/dock.sh familydns grant");
            return;
        }
        button.setAlpha(1f);
        if (FamilyDnsAndroid.busy()) {   // a check takes up to about 40 seconds
            state.setText("جارٍ الفحص…");
            button.setText("انتظر");
            message.setText("");
            handler.postDelayed(poll, 1000);
            return;
        }
        FamilyDns.Status s = FamilyDnsAndroid.status(this);
        if (s.state == FamilyDns.State.ON && "other".equals(s.by)) {
            state.setText("مفعّلة من الكمبيوتر، أوقفها من هناك");
            button.setText("غير متاح");
            button.setAlpha(.5f);
            message.setText(last);
            return;
        }
        state.setText("الحالة: " + FamilyDns.arabic(s));
        button.setText(s.state == FamilyDns.State.ON ? "إيقاف الحماية" : "تفعيل الحماية");
        message.setText(last);
    }

    private TextView text(String s, float sp, String weight, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.RIGHT);
        try { t.setTypeface(Typeface.createFromAsset(getAssets(), "fonts/Thmanyah-" + weight + ".otf")); }
        catch (RuntimeException ignored) { }
        return t;
    }

    private LinearLayout.LayoutParams right(int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = bottom;
        return p;
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
