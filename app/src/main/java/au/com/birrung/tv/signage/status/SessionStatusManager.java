package au.com.birrung.tv.signage.status;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * What the rest of the app talks to.
 *
 * Signing in or out is recorded locally first and pushed to the server afterwards, so a
 * sign-out performed with no internet is never lost: it is delivered the moment the
 * connection returns, and always before the next sign-in is allowed.
 */
public final class SessionStatusManager {

    private static final String TAG = "SessionStatus";

    private static SessionStatusManager instance;

    private final StatusSyncQueue queue;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private SessionStatusManager(Context ctx) {
        SqliteStatusStore store = new SqliteStatusStore(ctx.getApplicationContext());
        this.queue = new StatusSyncQueue(store, new StatusApi());
    }

    public static synchronized SessionStatusManager get(Context ctx) {
        if (instance == null) instance = new SessionStatusManager(ctx);
        return instance;
    }

    /** Queues a sign-in and tries to deliver it. Never blocks the caller. */
    public void recordLogin(final String sessionId) {
        worker.execute(() -> {
            queue.recordLogin(sessionId);
            queue.syncPending();
        });
    }

    /**
     * Queues a sign-out and tries to deliver it. Never blocks the caller: the device must
     * log out instantly whether or not the server can be reached.
     */
    public void recordLogout(final String sessionId) {
        worker.execute(() -> {
            queue.recordLogout(sessionId);
            queue.syncPending();
        });
    }

    /** Delivers anything outstanding. Safe to call often -- overlapping passes are ignored. */
    public void syncNow() {
        worker.execute(queue::syncPending);
    }

    /**
     * Decides whether a sign-in may go ahead, delivering any outstanding sign-out first.
     *
     * @param allowed  run on the main thread when the server is up to date
     * @param blocked  run on the main thread when a sign-out could not be delivered; the
     *                 login request must not be sent, or the server will reject it as
     *                 "already logged in"
     */
    public void whenSafeToLogin(final Runnable allowed, final Runnable blocked) {
        worker.execute(() -> {
            boolean ok;
            try {
                ok = queue.canLoginNow();
            } catch (Throwable t) {
                Log.e(TAG, "Could not check for a pending sign out: " + t.getMessage());
                ok = true;   // never let a local fault lock the installer out of the app
            }
            final boolean result = ok;
            main.post(() -> {
                if (result) allowed.run();
                else {
                    Log.e(TAG, "Login held back: a previous sign out has not reached the server");
                    blocked.run();
                }
            });
        });
    }
}
