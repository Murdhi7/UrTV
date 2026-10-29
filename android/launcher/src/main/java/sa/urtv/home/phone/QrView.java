package sa.urtv.home.phone;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Draws a QrCode black on white with the four-module quiet zone around it, as large as the view allows (a square). */
public final class QrView extends View {
    private QrCode code;
    private final Paint paint = new Paint();

    public QrView(Context c) {
        super(c);
        paint.setAntiAlias(false);
    }

    public void setCode(QrCode q) {
        code = q;
        invalidate();
    }

    @Override protected void onMeasure(int w, int h) {
        int side = Math.min(MeasureSpec.getSize(w), MeasureSpec.getSize(h) > 0 && MeasureSpec.getMode(h) != MeasureSpec.UNSPECIFIED ? MeasureSpec.getSize(h) : MeasureSpec.getSize(w));
        setMeasuredDimension(side, side);
    }

    @Override protected void onDraw(Canvas canvas) {
        paint.setColor(0xFFFFFFFF);
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        if (code == null) return;
        int n = code.size + 8;   // four modules of white on each side
        float cell = Math.min(getWidth(), getHeight()) / (float) n;
        paint.setColor(0xFF000000);
        for (int y = 0; y < code.size; y++) {
            for (int x = 0; x < code.size; x++) {
                if (code.dark(x, y)) canvas.drawRect((x + 4) * cell, (y + 4) * cell, (x + 5) * cell + 0.5f, (y + 5) * cell + 0.5f, paint);
            }
        }
    }
}
