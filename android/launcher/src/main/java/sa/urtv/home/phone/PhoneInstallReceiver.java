package sa.urtv.home.phone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** The answer from Android's installer for «تحديث تلفزيونك» (not exported: only reached through the app's own PendingIntent). */
public class PhoneInstallReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent != null && PhoneUpdater.ACTION_RESULT.equals(intent.getAction())) PhoneUpdater.onResult(context.getApplicationContext(), intent);
    }
}
