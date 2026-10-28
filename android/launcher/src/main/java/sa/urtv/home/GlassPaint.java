package sa.urtv.home;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * The glass look drawn (GlassStyle's colours): the page, panels, tiles, dialogs. Flat fills only: the page's photo is the one small
 * backdrop GlassSource keeps, drawn scaled, never blurred here (no live blur: tests/glass_test.py looks for one).
 *
 * <p>This file is the SAME in both apps (only the package line differs): tests/glass_test.py fails when the two copies differ.
 */
public final class GlassPaint {
    private GlassPaint() { }

    /** The page: the backdrop under the veil it needs, or the look's solid colour while there is none. */
    public static Drawable page(Context c, Bitmap backdrop, GlassStyle.Look l, float scrim) {
        if (backdrop == null) return new ColorDrawable(l.base);
        BitmapDrawable photo = new BitmapDrawable(c.getResources(), backdrop);   // the same bitmap, not a copy
        photo.setFilterBitmap(true);
        photo.setGravity(Gravity.FILL);
        return new LayerDrawable(new Drawable[] {photo, new ColorDrawable(GlassStyle.scrimColor(l, scrim))});
    }

    /** A rounded fill, with a thin line when {@code line} is not 0. */
    public static GradientDrawable fill(Context c, int color, float radiusDp, int line) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(c, radiusDp));
        d.setColor(color);
        if (line != 0) d.setStroke(Math.max(1, dp(c, 1)), line);
        return d;
    }

    /** A panel: a list of rows, a tile at rest. */
    public static GradientDrawable panel(Context c, GlassStyle.Look l, float radiusDp) {
        return fill(c, l.panel, radiusDp, l.line);
    }

    /** A tile or a row: the panel at rest, the solid focus fill while it has the focus. */
    public static Drawable tile(Context c, GlassStyle.Look l, float radiusDp) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[] {android.R.attr.state_focused}, fill(c, l.focus, radiusDp, 0));
        s.addState(new int[] {}, panel(c, l, radiusDp));
        return s;
    }

    /** A row inside a panel: nothing at rest (the panel is under it), the focus fill while focused. */
    public static Drawable row(Context c, GlassStyle.Look l, float radiusDp) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[] {android.R.attr.state_focused}, fill(c, l.focus, radiusDp, 0));
        s.addState(new int[] {}, new ColorDrawable(0));
        return s;
    }

    /** A dialog's own panel. */
    public static GradientDrawable dialog(Context c, GlassStyle.Look l) {
        return fill(c, l.dialog, 22, l.line);
    }

    /** The hint line's pill at the top of a screen. */
    public static GradientDrawable hint(Context c, GlassStyle.Look l) {
        return fill(c, l.hint, 18, l.line);
    }

    /** the tag of an ImageView that is one of our line icons: it takes the card's focus colours (an app's icon keeps its own) */
    public static final String ICON = "glass:icon";

    /** a colour at rest, another while focused or selected (a list's row) */
    public static ColorStateList focusColors(int rest, int focused) {
        return new ColorStateList(new int[][] {{android.R.attr.state_focused}, {android.R.attr.state_selected}, {}}, new int[] {focused, focused, rest});
    }

    /**
     * A focusable card in the glass look: the panel at rest, the solid focus fill while focused ({@link #tile}), and every word and icon
     * in it in the focus colours then (they take the card's state: duplicateParentState). A word in the sub colour becomes onFocusSub;
     * an icon tagged {@link #ICON} the accent, then the focus ink.
     */
    public static void card(View card, GlassStyle.Look l, float radiusDp) {
        card.setBackground(tile(card.getContext(), l, radiusDp));
        if (card instanceof TextView) {
            TextView t = (TextView) card;
            int rest = t.getCurrentTextColor();
            t.setTextColor(focusColors(rest, rest == l.sub ? l.onFocusSub : l.onFocus));
        } else if (card instanceof ViewGroup) {
            follow((ViewGroup) card, l);
        }
    }

    /** The words and icons under {@code g} take its focus: the focus colours while it has it. */
    public static void follow(ViewGroup g, GlassStyle.Look l) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View v = g.getChildAt(i);
            v.setDuplicateParentStateEnabled(true);
            if (v instanceof TextView) {
                int rest = ((TextView) v).getCurrentTextColor();
                ((TextView) v).setTextColor(focusColors(rest, rest == l.sub ? l.onFocusSub : l.onFocus));
            } else if (v instanceof ImageView && ICON.equals(v.getTag())) {   // our own line icons only, never an app's icon
                ImageView iv = (ImageView) v;
                if (iv.getDrawable() != null) iv.getDrawable().mutate().setTintList(focusColors(l.accent, l.onFocus));
            } else if (v instanceof ViewGroup) {
                follow((ViewGroup) v, l);
            }
        }
    }

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
