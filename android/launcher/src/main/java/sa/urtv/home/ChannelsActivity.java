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
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * «صفوف القنوات»: which rows show under the favourites and in what order («أكمل المشاهدة» and the rows the apps publish to Android TV;
 * the idea from talkies, MIT: THIRD_PARTY_NOTICES.md). OK on a row shows or hides it, ▲ and ▼ beside it move it. The first time it
 * asks Android for the TV listings (READ_TV_LISTINGS): without it the home has no rows, as before. Built like «ترتيب المفضلة»:
 * right-aligned by hand on a left-to-right screen, behind the same «قفل الإعدادات». Opened from the screensaver's settings, or
 * `am start -n sa.urtv.home/.ChannelsActivity`.
 */
public class ChannelsActivity extends Activity {
    private static final int GOLD = 0xFFD6AF6B, TEXT = 0xFFF6EFE0, DIM = 0xFFC6BBA4;
    private static final int ASK = 7;

    private LinearLayout list;
    private View screen;
    private boolean verified, asked;
    private final List<TvRowsPick.Row> rows = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Digits.init(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF0A2016, 0xFF04120C, 0xFF020806}));
        scroll.setVerticalScrollBarEnabled(false);
        FrameLayout frame = new FrameLayout(this);
        scroll.addView(frame);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, dp(36), 0, dp(48));
        frame.addView(col, new FrameLayout.LayoutParams(dp(640), -2, Gravity.CENTER_HORIZONTAL));
        col.addView(text("صفوف القنوات", 26, "Medium", TEXT), right(0));
        col.addView(text("صفوف تحت المفضلة في الشاشة الرئيسية: «أكمل المشاهدة» وما تنشره التطبيقات. اختيار على صف يظهره أو يخفيه، "
            + "والسهمان بجانبه يرتّبانه.", 14, "Regular", DIM), right(dp(24)));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        col.addView(list, new LinearLayout.LayoutParams(-1, -2));
        scroll.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);   // the rows' D-pad ids are left to right: pinned, as in «ترتيب المفضلة»
        setContentView(scroll);
        screen = scroll;
    }

    /** «قفل الإعدادات»: with it on, this screen opens only after the PIN, as «ترتيب المفضلة». */
    @Override
    protected void onResume() {
        super.onResume();
        if (verified) { render(0); return; }
        String[] targets = ParentalPolicy.targets(ParentalPolicy.ARRANGE_SCREEN, null);
        if (!Parental.needsPin(this, targets)) {
            verified = true;
            render(0);
            return;
        }
        screen.setVisibility(View.INVISIBLE);
        Parental.guard(this, Parental.adhanFirst(this), "صفوف القنوات", targets, () -> {
            verified = true;
            screen.setVisibility(View.VISIBLE);
            render(0);
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
        if (code == ASK) render(0);
    }

    private void render(int focus) {
        list.removeAllViews();
        if (!TvRows.allowed(this)) {
            list.addView(text("لعرض الصفوف تحتاج الواجهة إذن قراءة قوائم التلفزيون. لا يُرسل شيء خارج التلفزيون.", 15, "Regular", TEXT), right(dp(14)));
            View allow = card("السماح", null, v -> { asked = true; requestPermissions(new String[]{TvRows.PERMISSION, TvRows.EPG}, ASK); });
            list.addView(allow, rowParams());
            if (asked) list.addView(text("إذا ما ظهرت نافذة السماح، اطلب من وكيلك يعطي الإذن للواجهة.", 13, "Regular", DIM), right(dp(8)));
            allow.post(allow::requestFocus);
            return;
        }
        rows.clear();
        new Thread(() -> {   // the TV's lists: off the main thread
            List<TvRowsPick.Row> got = TvRows.arrange(this);
            runOnUiThread(() -> { if (!isDestroyed()) { rows.clear(); rows.addAll(got); draw(focus); } });
        }).start();
    }

    private void draw(int focus) {
        list.removeAllViews();
        if (rows.isEmpty()) {
            list.addView(text("ما فيه صفوف الآن: التطبيقات تنشرها لما تستخدمها، و«أكمل المشاهدة» يظهر لما توقف شيء في نصه.", 15, "Regular", TEXT), right(0));
            return;
        }
        List<View> cards = new ArrayList<>(), ups = new ArrayList<>(), downs = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            final int at = i;
            TvRowsPick.Row r = rows.get(i);
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            View down = pill("▼", v -> move(at, 1)), up = pill("▲", v -> move(at, -1));
            line.addView(down, new LinearLayout.LayoutParams(dp(56), -1));
            LinearLayout.LayoutParams upP = new LinearLayout.LayoutParams(dp(56), -1);
            upP.leftMargin = dp(8);
            line.addView(up, upP);
            View c = card(r.title, (r.shown ? "ظاهر" : "مخفي") + " · " + ar(r.count), v -> {
                r.shown = !r.shown;
                TvRows.save(this, rows);
                draw(at);
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f);
            cp.leftMargin = dp(10);
            line.addView(c, cp);
            c.setNextFocusLeftId(up.getId());
            up.setNextFocusRightId(c.getId());
            up.setNextFocusLeftId(down.getId());
            down.setNextFocusRightId(up.getId());
            cards.add(c); ups.add(up); downs.add(down);
            list.addView(line, rowParams());
        }
        for (List<View> colViews : java.util.Arrays.asList(cards, ups, downs))   // ▲▼ of the remote stay in their column
            for (int i = 0; i < colViews.size(); i++) {
                colViews.get(i).setNextFocusUpId(colViews.get(Math.max(0, i - 1)).getId());
                colViews.get(i).setNextFocusDownId(colViews.get(Math.min(colViews.size() - 1, i + 1)).getId());
            }
        View f = cards.get(Math.max(0, Math.min(focus, cards.size() - 1)));
        f.post(f::requestFocus);
    }

    private void move(int i, int d) {
        if (!TvRowsPick.move(rows, i, d)) return;
        TvRows.save(this, rows);
        draw(i + d);
    }

    // ---------------------------------------------------------------- views, as «ترتيب المفضلة»

    /** [  title  ] with its state under it, right-aligned; gold when focused. */
    private View card(String title, String state, View.OnClickListener click) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.RIGHT);
        c.setPadding(dp(16), dp(10), dp(16), dp(10));
        c.setFocusable(true);
        c.setClickable(true);
        c.setId(View.generateViewId());
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        c.setBackground(bg);
        final TextView name = text(title, 17, "Medium", TEXT);
        name.setSingleLine(true);
        c.addView(name, new LinearLayout.LayoutParams(-1, -2));
        if (state != null) c.addView(text(state, 13, "Regular", DIM), new LinearLayout.LayoutParams(-1, -2));
        View.OnFocusChangeListener paint = (v, has) -> {
            bg.setColor(has ? 0x33D6AF6B : 0x14FFFFFF);
            bg.setStroke(dp(2), has ? GOLD : 0x1FD6AF6B);
        };
        c.setOnFocusChangeListener(paint);
        paint.onFocusChange(c, false);
        c.setOnClickListener(click);
        return c;
    }

    private View pill(String label, View.OnClickListener click) {
        final TextView p = text(label, 15, "Medium", DIM);
        p.setGravity(Gravity.CENTER);
        p.setFocusable(true);
        p.setClickable(true);
        p.setId(View.generateViewId());
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        p.setBackground(bg);
        View.OnFocusChangeListener paint = (v, has) -> {
            bg.setColor(has ? 0x33D6AF6B : 0x0DFFFFFF);
            bg.setStroke(dp(2), has ? GOLD : 0x14FFFFFF);
            p.setTextColor(has ? TEXT : DIM);
        };
        p.setOnFocusChangeListener(paint);
        paint.onFocusChange(p, false);
        p.setOnClickListener(click);
        return p;
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

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(8);
        return p;
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private static String ar(int n) { return Digits.of(String.valueOf(n)); }
}
