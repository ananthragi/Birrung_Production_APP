package au.com.birrung.tv.signage.status;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pushes queued sign-in/sign-out events to the server, oldest first.
 *
 * Plain Java on purpose -- no Android types -- so the ordering, retry and gating rules can be
 * tested on the JVM against fakes.
 */
public class StatusSyncQueue {

    /** Grows the wait between attempts for a record that keeps failing. */
    private static final long BASE_BACKOFF_MS = 30_000L;
    private static final long MAX_BACKOFF_MS = 30 * 60_000L;

    /** Synced records are kept this long for diagnosis, then removed. */
    static final long KEEP_SYNCED_MS = 7L * 24 * 60 * 60 * 1000;

    /** Only one pass at a time: connectivity changes, resume and login can all fire together. */
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final StatusStore store;
    private final StatusTransport transport;
    private final Clock clock;

    /** Lets tests move time without sleeping. */
    public interface Clock { long now(); }

    public StatusSyncQueue(StatusStore store, StatusTransport transport) {
        this(store, transport, System::currentTimeMillis);
    }

    public StatusSyncQueue(StatusStore store, StatusTransport transport, Clock clock) {
        this.store = store;
        this.transport = transport;
        this.clock = clock;
    }

    public void recordLogin(String sessionId)  { record(sessionId, SessionStatusEvent.STATUS_LOGIN); }
    public void recordLogout(String sessionId) { record(sessionId, SessionStatusEvent.STATUS_LOGOUT); }

    private void record(String sessionId, int status) {
        // Always queued BEFORE the network is touched, so nothing is lost if the call fails,
        // the connection is down, or the process dies on the next line.
        store.insertPending(sessionId, status, clock.now());
    }

    public boolean hasPendingLogout() { return store.hasPendingLogout(); }

    /**
     * Sends every pending event in order.
     *
     * Stops at the first failure rather than skipping past it: the server must see what
     * happened in the order it happened, so a stuck logout must not be overtaken by a later
     * login. Returns true when the queue is empty at the end of the pass.
     */
    public boolean syncPending() {
        if (!running.compareAndSet(false, true)) return false;   // a pass is already under way
        try {
            List<SessionStatusEvent> pending = store.pendingOldestFirst();
            for (SessionStatusEvent e : pending) {
                if (!isDue(e)) return false;        // still backing off; keep the order intact
                StatusTransport.Result r = transport.send(e.sessionId, e.status);
                if (!r.success) {
                    store.markAttemptFailed(e.id, r.error);
                    return false;                   // stop the pass, preserve ordering
                }
                store.markSynced(e.id, clock.now());
            }
            store.deleteSyncedBefore(clock.now() - KEEP_SYNCED_MS);
            return true;
        } finally {
            running.set(false);
        }
    }

    /**
     * True when enough time has passed since the last failed ATTEMPT on this record.
     * Measuring from creation instead would let an old record become permanently "due"
     * however often it had just failed.
     */
    private boolean isDue(SessionStatusEvent e) {
        if (e.retryCount <= 0 || e.lastAttemptAt <= 0) return true;
        long wait = Math.min(MAX_BACKOFF_MS, BASE_BACKOFF_MS * (1L << Math.min(e.retryCount - 1, 10)));
        return clock.now() - e.lastAttemptAt >= wait;
    }

    /**
     * Whether a login may proceed. A sign-out that has not reached the server must be
     * delivered first, or the server still believes the old session is live and rejects the
     * new login with "already logged in".
     */
    public boolean canLoginNow() {
        if (!store.hasPendingLogout()) return true;
        syncPending();
        return !store.hasPendingLogout();
    }
}
