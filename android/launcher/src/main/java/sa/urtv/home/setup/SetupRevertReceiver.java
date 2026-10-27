package sa.urtv.home.setup;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** The safety switch fired: if a home switch was left half done and Home is not ours, the stock home is put back (see {@link SetupEngine#recoverIfCutOff}). */
public class SetupRevertReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        final Context app = context.getApplicationContext();
        final PendingResult pending = goAsync();
        new Thread(new Runnable() {
            @Override public void run() {
                try (SetupSession s = SetupSession.open(app, 0, null, new SetupEngine.Out() { @Override public void line(String text) { } })) {
                    s.engine.recoverIfCutOff();
                } catch (Exception e) {
                    // the TV did not answer: the next time the setup screen opens it offers «تراجع»
                } finally {
                    pending.finish();
                }
            }
        }, "setup-revert").start();
    }
}
