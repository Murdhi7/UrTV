package sa.urtv.home.setup;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * The in-app safety switch of the home switch: an alarm that fires {@link SetupRevertReceiver} after a few minutes, which puts the stock home back
 * unless the switch was confirmed (disarm). It lives in this app, not in a background job started over ADB (some TVs end those with the session).
 */
final class SetupDeadman implements SetupEngine.Deadman {
    private final Context context;

    SetupDeadman(Context c) { context = c.getApplicationContext(); }

    private PendingIntent intent() {
        Intent i = new Intent(context, SetupRevertReceiver.class);
        return PendingIntent.getBroadcast(context, 71, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override public void arm(int seconds) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long at = System.currentTimeMillis() + seconds * 1000L;
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent());
        else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent());
    }

    @Override public void disarm() {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(intent());
    }
}
