package sa.urtv.home;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * «أكمل المشاهدة»: the one question the row needs, Android's own «Allow» for the TV's Watch Next list (READ_TV_LISTINGS; the idea from
 * talkies, MIT: THIRD_PARTY_NOTICES.md). Opened by the screensaver's settings row when it is turned on; closes by itself when that is
 * allowed already, or once it is. Behind «قفل الإعدادات» like «ترتيب المفضلة». `am start -n sa.urtv.home/.WatchNextActivity`.
 */
public class WatchNextActivity extends Activity {
    private static final int GOLD = 0xFFD6AF6B, TEXT = 0xFFF6EFE0, DIM = 0xFFC6BBA4;
    private static final int ASK = 7;

    private View screen, allow;
    private boolean verified;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Digits.init(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF0A2016, 0xFF04120C, 0xFF020806}));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text("أكمل المشاهدة", 28, "Medium", TEXT), right(dp(10)));
        col.addView(text("تحت المفضلة: ما وقفته في نصه والحلقة الجاية، من تطبيقاتك. تحتاج الواجهة إذن قراءة هذه القائمة من التلفزيون مرة "
            + "واحدة، ولا يُرسل شيء خارج التلفزيون.", 15, "Regular", DIM), right(dp(26)));
        allow = card("السماح", v -> requestPermissions(new String[]{TvRows.PERMISSION, TvRows.EPG}, ASK));
        col.addView(allow, new LinearLayout.LayoutParams(dp(220), -2, Gravity.RIGHT));
        root.addView(col, new FrameLayout.LayoutParams(dp(640), -2, Gravity.CENTER));
        setContentView(root);
        screen = root;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (TvRows.allowed(this)) { finish(); return; }   // allowed already: nothing to ask
        if (verified) { allow.requestFocus(); return; }
        String[] targets = ParentalPolicy.targets(ParentalPolicy.ARRANGE_SCREEN, null);
        if (!Parental.needsPin(this, targets)) { verified = true; allow.requestFocus(); return; }
        screen.setVisibility(View.INVISIBLE);
        Parental.guard(this, Parental.adhanFirst(this), "أكمل المشاهدة", targets, () -> {
            verified = true;
            screen.setVisibility(View.VISIBLE);
            allow.requestFocus();
        }, () -> finish());
    }

    @Override
    protected void onPause() {
        super.onPause();
        PinPrompt.dismiss(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        verified = false;
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        if (code == ASK && TvRows.allowed(this)) finish();   // the home shows the row when it is back in front
    }

    private View card(String title, View.OnClickListener click) {
        TextView c = text(title, 18, "Medium", TEXT);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(16), dp(12), dp(16), dp(12));
        c.setFocusable(true);
        c.setClickable(true);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        c.setBackground(bg);
        View.OnFocusChangeListener paint = (v, has) -> {
            bg.setColor(has ? 0x33D6AF6B : 0x14FFFFFF);
            bg.setStroke(dp(2), has ? GOLD : 0x1FD6AF6B);
        };
        c.setOnFocusChangeListener(paint);
        paint.onFocusChange(c, false);
        c.setOnClickListener(click);
        return c;
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
