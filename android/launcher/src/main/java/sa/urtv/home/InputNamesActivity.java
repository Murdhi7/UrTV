package sa.urtv.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvInputInfo;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * «أسماء المصادر»: the inputs the TV reports, each with the name shown in the sources list. OK on one opens the name editor on the TV's
 * keyboard; an empty name brings the TV's own back. The names stay on this TV (InputNames). No list dialogs, like DockActivity: rows are
 * built right-aligned by hand. Opened from the screensaver's settings, or `am start -n sa.urtv.home/.InputNamesActivity`; behind
 * «قفل الإعدادات» as «ترتيب المفضلة» is.
 */
public class InputNamesActivity extends Activity {
    private static final int GOLD = 0xFFD6AF6B, INK = 0xFF04120C, TEXT = 0xFFF6EFE0, DIM = 0xFFC6BBA4;

    private LinearLayout list;
    private View screen;
    private boolean verified;
    private AlertDialog editor;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Digits.init(this);
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
        col.addView(text("أسماء المصادر", 26, "Medium", TEXT, false), right(0));
        col.addView(text("اختيار على مصدر لتغيير اسمه في قائمة «المصادر» (مثل PS5). اترك الاسم فارغاً لرجوع اسم التلفزيون.", 14, "Regular", DIM, false), right(dp(24)));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        col.addView(list, new LinearLayout.LayoutParams(-1, -2));
        scroll.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);   // pinned like DockActivity: a TV set to Arabic does not mirror the rows under the D-pad
        setContentView(scroll);
        screen = scroll;
        render(0);
    }

    /** «قفل الإعدادات»: with it on, this screen opens only after the PIN, whoever opens it (same target as «ترتيب المفضلة»). */
    @Override
    protected void onResume() {
        super.onResume();
        if (verified) return;
        String[] targets = ParentalPolicy.targets(ParentalPolicy.ARRANGE_SCREEN, null);
        if (!Parental.needsPin(this, targets)) {
            verified = true;
            return;
        }
        screen.setVisibility(View.INVISIBLE);
        Parental.guard(this, Parental.adhanFirst(this), "أسماء المصادر", targets, () -> {
            verified = true;
            screen.setVisibility(View.VISIBLE);
        }, () -> finish());
    }

    @Override
    protected void onPause() {
        super.onPause();
        PinPrompt.dismiss(this);
        if (editor != null) editor.dismiss();
    }

    @Override
    protected void onStop() {
        super.onStop();
        verified = false;
    }

    private void render(int focus) {
        list.removeAllViews();
        List<TvInputInfo> inputs = InputLabels.reported(this);
        Map<String, String> given = InputLabels.given(this);
        List<View> cards = new ArrayList<>();
        for (TvInputInfo t : inputs) {
            View c = card(InputLabels.own(this, t), given.get(t.getId()), v -> edit(t));
            cards.add(c);
            list.addView(c, rowParams());
        }
        if (cards.isEmpty()) {
            list.addView(text("التلفزيون ما أبلغ عن مصادر (HDMI أو غيرها).", 15, "Medium", DIM, false), right(0));
            return;
        }
        for (int i = 0; i < cards.size(); i++) {
            cards.get(i).setNextFocusUpId(cards.get(Math.max(0, i - 1)).getId());
            cards.get(i).setNextFocusDownId(cards.get(Math.min(cards.size() - 1, i + 1)).getId());
        }
        View f = cards.get(Math.max(0, Math.min(focus, cards.size() - 1)));
        f.post(f::requestFocus);
    }

    /** [ the TV's name (dim) ]   [ the name shown ] — right-aligned by hand; a Latin name keeps its own order. */
    private View card(String own, String saved, View.OnClickListener click) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        c.setPadding(dp(16), dp(12), dp(16), dp(12));
        c.setFocusable(true);
        c.setClickable(true);
        c.setId(View.generateViewId());
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        c.setBackground(bg);
        boolean named = saved != null && !saved.isEmpty();
        final TextView tv = text(named ? own : "", 13, "Regular", DIM, true);
        tv.setSingleLine(true);
        c.addView(tv, new LinearLayout.LayoutParams(-2, -2));
        c.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        final TextView shown = text(InputNames.label(saved, own), 17, "Medium", named ? GOLD : TEXT, true);
        shown.setSingleLine(true);
        c.addView(shown, new LinearLayout.LayoutParams(-2, -2));
        View.OnFocusChangeListener paint = (v, has) -> {
            bg.setColor(has ? 0x33D6AF6B : 0x14FFFFFF);
            bg.setStroke(dp(2), has ? GOLD : 0x1FD6AF6B);
        };
        c.setOnFocusChangeListener(paint);
        paint.onFocusChange(c, false);
        c.setOnClickListener(click);
        return c;
    }

    /** The editor: the TV's keyboard in a small box; «حفظ» keeps it, empty brings the TV's own name back. */
    private void edit(TvInputInfo t) {
        final String id = t.getId(), own = InputLabels.own(this, t);
        String saved = InputLabels.given(this).get(id);
        final EditText in = new EditText(this);
        in.setSingleLine(true);
        in.setInputType(InputType.TYPE_CLASS_TEXT);
        in.setHint(own);
        in.setText(saved == null ? "" : saved);
        in.setSelection(in.getText().length());
        in.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        editor = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("اسم المصدر: " + own)
            .setView(in)
            .setPositiveButton("حفظ", null)   // set below: a name that can't be kept leaves the box open
            .setNegativeButton("إلغاء", null)
            .create();
        editor.setOnDismissListener(d -> editor = null);
        editor.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        editor.show();
        editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = InputNames.clean(in.getText().toString());
            if (name == null) { toast("الاسم حتى " + Digits.of(String.valueOf(InputNames.MAX_LEN)) + " حرفاً في سطر واحد"); return; }
            InputLabels.set(this, id, name);
            editor.dismiss();
            toast(name.isEmpty() ? "رجع اسم التلفزيون" : "تم حفظ الاسم");
            render(indexOf(id));
        });
        in.requestFocus();
    }

    private int indexOf(String id) {
        List<TvInputInfo> inputs = InputLabels.reported(this);
        for (int i = 0; i < inputs.size(); i++) if (id.equals(inputs.get(i).getId())) return i;
        return 0;
    }

    // ---------------------------------------------------------------- helpers

    private TextView text(String s, float sp, String weight, int color, boolean ownDirection) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.RIGHT);
        if (ownDirection) t.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);   // «BN Sport» reads left to right inside the Arabic row
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
